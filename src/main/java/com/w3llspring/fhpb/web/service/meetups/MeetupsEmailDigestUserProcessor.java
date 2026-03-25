package com.w3llspring.fhpb.web.service.meetups;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMeetupRsvpRepository;
import com.w3llspring.fhpb.web.db.LadderMeetupSlotRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMeetupRsvp;
import com.w3llspring.fhpb.web.model.LadderMeetupSlot;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.email.EmailService;
import jakarta.mail.MessagingException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetupsEmailDigestUserProcessor {

  private static final Logger log = LoggerFactory.getLogger(MeetupsEmailDigestUserProcessor.class);
  private static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("America/New_York");
  private static final DateTimeFormatter EMAIL_TIME_FORMAT =
      DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a z");

  public record MarkPendingResult(boolean optedIn, boolean hasEmail) {
    public static MarkPendingResult skipped() {
      return new MarkPendingResult(false, false);
    }
  }

  private final Clock clock;
  private final UserRepository userRepo;
  private final LadderMembershipRepository membershipRepo;
  private final LadderMeetupSlotRepository slotRepo;
  private final LadderMeetupRsvpRepository rsvpRepo;
  private final LadderConfigRepository ladderConfigRepo;
  private final EmailService emailService;
  private final MeetupsEmailConfig config;
  private final MeetupsEmailLinkSigner linkSigner;
  private final String supportEmail;

  @Value("${fhpb.public.base-url:}")
  private String publicBaseUrl;

  public MeetupsEmailDigestUserProcessor(
      UserRepository userRepo,
      LadderMembershipRepository membershipRepo,
      LadderMeetupSlotRepository slotRepo,
      LadderMeetupRsvpRepository rsvpRepo,
      LadderConfigRepository ladderConfigRepo,
      EmailService emailService,
      MeetupsEmailConfig config,
      MeetupsEmailLinkSigner linkSigner,
      @Value("${support.email:support@example.invalid}") String supportEmail) {
    this.clock = Clock.systemUTC();
    this.userRepo = userRepo;
    this.membershipRepo = membershipRepo;
    this.slotRepo = slotRepo;
    this.rsvpRepo = rsvpRepo;
    this.ladderConfigRepo = ladderConfigRepo;
    this.emailService = emailService;
    this.config = config;
    this.linkSigner = linkSigner;
    this.supportEmail =
        supportEmail == null || supportEmail.isBlank()
            ? "support@example.invalid"
            : supportEmail.trim();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordOptIn(Long userId, boolean optIn) {
    User user = userRepo.findByIdForUpdate(userId).orElseThrow();
    user.setMeetupsEmailOptIn(optIn);
    if (!optIn) {
      user.setMeetupsEmailPending(false);
    }
    userRepo.save(user);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public MarkPendingResult markPendingIfOptedIn(Long userId) {
    User user = userRepo.findByIdForUpdate(userId).orElse(null);
    if (user == null || !user.isMeetupsEmailOptIn()) {
      return MarkPendingResult.skipped();
    }
    user.setMeetupsEmailPending(true);
    userRepo.save(user);
    boolean hasEmail = user.getEmail() != null && !user.getEmail().isBlank();
    return new MarkPendingResult(true, hasEmail);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void trySendDigestIfAllowedNow(Long userId) {
    trySendDigest(userId, false);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void trySendPendingDigest(Long userId) {
    trySendDigest(userId, true);
  }

  private void trySendDigest(Long userId, boolean allowCooldownOverride) {
    User user = userRepo.findByIdForUpdate(userId).orElse(null);
    if (user == null || !user.isMeetupsEmailPending()) {
      return;
    }
    if (!user.isMeetupsEmailOptIn()) {
      user.setMeetupsEmailPending(false);
      userRepo.save(user);
      return;
    }

    Instant now = Instant.now(clock);
    boolean sendNow = canSendNow(user, now);

    if (!sendNow) {
      if (!allowCooldownOverride) {
        return;
      }
      Instant lastSentAt = user.getMeetupsEmailLastSentAt();
      if (lastSentAt == null) {
        return;
      }
      Instant cooldownEndsAt = lastSentAt.plus(config.cooldown());
      List<MeetupsEmailDigestRow> rows = buildDigestRows(user.getId());
      if (rows.isEmpty()) {
        user.setMeetupsEmailPending(false);
        userRepo.save(user);
        return;
      }
      Instant earliestStartsAt = rows.get(0).startsAt();
      if (!MeetupsEmailDigestService.shouldSendDuringCooldown(
          now, cooldownEndsAt, earliestStartsAt)) {
        return;
      }
      sendDigest(user, rows, now);
      return;
    }

    List<MeetupsEmailDigestRow> rows = buildDigestRows(user.getId());
    if (rows.isEmpty()) {
      user.setMeetupsEmailPending(false);
      userRepo.save(user);
      return;
    }

    sendDigest(user, rows, now);
  }

  private void sendDigest(User user, List<MeetupsEmailDigestRow> rows, Instant now) {
    ZoneId zone = resolveUserZone(user);
    String subject = buildSubject(rows, zone);
    String html;

    String unsubLink;
    try {
      String unsubNonce = UUID.randomUUID().toString().replace("-", "");
      Instant unsubExpires = Instant.now(clock).plus(30, ChronoUnit.DAYS);
      String unsubToken = linkSigner.signUnsubscribe(user.getId(), unsubExpires, unsubNonce);
      unsubLink = href("/meetups/unsubscribe?token=" + unsubToken);
    } catch (Exception ex) {
      log.warn("[meetups-email] unsubscribe link build failed userId={}", user.getId(), ex);
      return;
    }

    try {
      html = buildHtml(user, rows, unsubLink, zone);
    } catch (Exception ex) {
      log.warn(
          "[meetups-email] html build failed userId={} rows={}", user.getId(), rows.size(), ex);
      return;
    }

    try {
      Map<String, String> headers = new HashMap<>();
      headers.put(
          "List-Unsubscribe",
          "<" + unsubLink + ">, <mailto:" + supportEmail + "?subject=Unsubscribe>");
      headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
      headers.put("Precedence", "bulk");
      headers.put("Auto-Submitted", "auto-generated");
      headers.put("X-Auto-Response-Suppress", "OOF, DR, RN, NRN, O0F");

      emailService.sendHtml(user.getEmail(), subject, html, headers);
    } catch (MessagingException ex) {
      log.warn("[meetups-email] send failed userId={} subject={}", user.getId(), subject, ex);
      return;
    }

    log.info("[meetups-email] sent userId={} rows={}", user.getId(), rows.size());

    bumpDailyCounters(user, now);
    user.setMeetupsEmailPending(false);
    userRepo.save(user);
  }

  private boolean canSendNow(User user, Instant now) {
    if (user.getEmail() == null || user.getEmail().isBlank()) {
      return false;
    }

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate lastDay = user.getMeetupsEmailDailySentDay();
    int count =
        user.getMeetupsEmailDailySentCount() == null ? 0 : user.getMeetupsEmailDailySentCount();
    if (lastDay == null || !lastDay.equals(today)) {
      count = 0;
    }
    if (count >= config.maxPerDay()) {
      return false;
    }

    Instant lastSent = user.getMeetupsEmailLastSentAt();
    if (lastSent == null) {
      return true;
    }
    return !now.isBefore(lastSent.plus(config.cooldown()));
  }

  private void bumpDailyCounters(User user, Instant now) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate lastDay = user.getMeetupsEmailDailySentDay();
    int count =
        user.getMeetupsEmailDailySentCount() == null ? 0 : user.getMeetupsEmailDailySentCount();
    if (lastDay == null || !lastDay.equals(today)) {
      count = 0;
    }
    user.setMeetupsEmailDailySentDay(today);
    user.setMeetupsEmailDailySentCount(count + 1);
    user.setMeetupsEmailLastSentAt(now);
  }

  List<MeetupsEmailDigestRow> buildDigestRows(Long userId) {
    Instant now = Instant.now(clock);

    List<LadderMembership> memberships =
        membershipRepo.findByUserIdAndState(userId, LadderMembership.State.ACTIVE);
    List<Long> ladderIds =
        memberships.stream().map(m -> m.getLadderConfig().getId()).distinct().toList();
    if (ladderIds.isEmpty()) {
      return List.of();
    }

    List<LadderMeetupSlot> slots =
        slotRepo.findFutureForLadders(ladderIds, now.minus(1, ChronoUnit.HOURS));
    if (slots.isEmpty()) {
      return List.of();
    }

    List<Long> creatorIds =
        slots.stream()
            .map(LadderMeetupSlot::getCreatedByUserId)
            .filter(id -> id != null)
            .distinct()
            .toList();
    Map<Long, String> creatorNameById = new HashMap<>();
    if (!creatorIds.isEmpty()) {
      for (User user : userRepo.findAllById(creatorIds)) {
        if (user == null || user.getId() == null) {
          continue;
        }
        String name = user.getNickName();
        creatorNameById.put(user.getId(), name == null ? "" : name);
      }
    }

    List<Long> slotIds = slots.stream().map(LadderMeetupSlot::getId).toList();
    Set<Long> rsvped = rsvpRepo.findRsvpedSlotIds(userId, slotIds);

    Map<Long, LadderConfig> ladderById = new HashMap<>();
    for (LadderConfig cfg : ladderConfigRepo.findAllById(ladderIds)) {
      ladderById.put(cfg.getId(), cfg);
    }

    List<MeetupsEmailDigestRow> out = new ArrayList<>();
    for (LadderMeetupSlot slot : slots) {
      if (slot.getId() == null || rsvped.contains(slot.getId())) {
        continue;
      }

      LadderConfig cfg = ladderById.get(slot.getLadderConfig().getId());
      String ladderTitle = cfg != null ? cfg.getTitle() : "Ladder";
      String creatorName = creatorNameById.getOrDefault(slot.getCreatedByUserId(), "");
      out.add(
          new MeetupsEmailDigestRow(slot.getId(), ladderTitle, creatorName, slot.getStartsAt()));
    }

    out.sort(Comparator.comparing(MeetupsEmailDigestRow::startsAt));
    return out;
  }

  private String buildSubject(List<MeetupsEmailDigestRow> rows, ZoneId zone) {
    if (rows.isEmpty()) {
      return config.subjectPrefix() + "New";
    }

    Instant first =
        rows.stream()
            .map(MeetupsEmailDigestRow::startsAt)
            .filter(startsAt -> startsAt != null)
            .min(Comparator.naturalOrder())
            .orElse(null);
    String when = formatInstant(first, zone);

    String ladders =
        rows.stream()
            .map(MeetupsEmailDigestRow::ladderTitle)
            .filter(title -> title != null && !title.isBlank())
            .distinct()
            .limit(3)
            .collect(Collectors.joining(", "));
    String suffix;
    if (rows.size() == 1) {
      suffix = when;
    } else {
      suffix =
          rows.size()
              + " new"
              + (ladders.isBlank()
                  ? ""
                  : " across "
                      + rows.stream().map(MeetupsEmailDigestRow::ladderTitle).distinct().count()
                      + " ladders");
    }

    return config.subjectPrefix() + suffix;
  }

  private String buildHtml(
      User user, List<MeetupsEmailDigestRow> rows, String unsubscribeLink, ZoneId zone) {
    List<MeetupsEmailDigestRow> ordered = new ArrayList<>(rows);
    ordered.sort(Comparator.comparing(MeetupsEmailDigestRow::startsAt));

    StringBuilder sb = new StringBuilder();
    sb.append("<!DOCTYPE html>");
    sb.append(
        "<html><body style=\"margin:0; padding:0; background-color:#f4f4f4; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;\">");
    sb.append(
        "<div style=\"max-width: 600px; margin: 0 auto; background-color: #ffffff; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1);\">");

    sb.append("<div style=\"background-color: #0d6efd; padding: 20px; text-align: center;\">");
    sb.append(
      "<h1 style=\"margin: 0; color: #ffffff; font-size: 24px; font-weight: 700;\">Open-Pickle Play Plans</h1>");
    sb.append("</div>");

    sb.append("<div style=\"padding: 30px 20px;\">");
    sb.append("<p style=\"font-size: 16px; color: #333333; margin-top: 0;\">Hello ")
        .append(escape(user.getNickName()))
        .append(",</p>");
    sb.append(
        "<p style=\"font-size: 16px; color: #555555; line-height: 1.5;\">New games are available! RSVP with just one tap below:</p>");

    for (MeetupsEmailDigestRow row : ordered) {
      String when = formatInstant(row.startsAt(), zone);
      String ladder = row.ladderTitle() == null ? "Ladder" : row.ladderTitle();

      sb.append("<div style=\"margin-top: 18px;\">");
      sb.append(
          "<div style=\"background-color: #f8f9fa; border: 1px solid #e9ecef; border-radius: 8px; padding: 15px; margin-bottom: 15px;\">");
      sb.append(
              "<div style=\"font-size: 16px; font-weight: 600; color: #212529; margin-bottom: 4px;\">")
          .append(escape(when))
          .append("</div>");
      sb.append(
              "<div style=\"color: #0d6efd; font-size: 14px; margin-bottom: 6px; font-weight: 600;\">")
          .append(escape(ladder))
          .append("</div>");

      if (row.createdByName() != null && !row.createdByName().isBlank()) {
        sb.append("<div style=\"color: #6c757d; font-size: 14px; margin-bottom: 12px;\">Hosted by ")
            .append(escape(row.createdByName()))
            .append("</div>");
      }

      sb.append(buttonRow(user.getId(), row.slotId()));
      sb.append("</div>");
      sb.append("</div>");
    }
    sb.append("</div>");

    sb.append(
        "<div style=\"background-color: #f8f9fa; padding: 20px; text-align: center; border-top: 1px solid #e9ecef;\">");
    sb.append(
        "<p style=\"color: #6c757d; font-size: 12px; margin: 0;\">You are receiving this because you opted in to email notifications.</p>");
    sb.append("<div style=\"margin-top: 12px;\">");
    sb.append("<a href=\"")
        .append(escape(unsubscribeLink))
        .append(
            "\" style=\"display:inline-block; padding:10px 18px; border-radius:6px; background-color:#ffffff; border:1px solid #ced4da; color:#0d6efd; text-decoration:none; font-weight:600; font-size:12px;\">Unsubscribe</a>");
    sb.append("</div>");
    sb.append(
            "<p style=\"color: #6c757d; font-size: 11px; margin: 10px 0 0;\">If the button doesn't work, copy and paste this link: <a href=\"")
        .append(escape(unsubscribeLink))
        .append("\" style=\"color:#0d6efd; text-decoration:underline;\">unsubscribe</a></p>");
    sb.append(
      "<p style=\"color: #6c757d; font-size: 12px; margin: 5px 0 0;\">(c) Open-Pickle</p>");
    sb.append("</div>");

    sb.append("</div>");
    sb.append("</body></html>");
    return sb.toString();
  }

  private static ZoneId resolveUserZone(User user) {
    if (user == null) {
      return DEFAULT_TIMEZONE;
    }
    String tz = user.getTimeZone();
    if (tz == null || tz.isBlank()) {
      return DEFAULT_TIMEZONE;
    }
    try {
      return ZoneId.of(tz.trim());
    } catch (Exception ignored) {
      return DEFAULT_TIMEZONE;
    }
  }

  private static String formatInstant(Instant instant, ZoneId zone) {
    if (instant == null) {
      return "";
    }
    ZoneId resolvedZone = zone == null ? DEFAULT_TIMEZONE : zone;
    ZonedDateTime zdt = instant.truncatedTo(ChronoUnit.MINUTES).atZone(resolvedZone);
    return EMAIL_TIME_FORMAT.format(zdt);
  }

  private String buttonRow(Long userId, Long slotId) {
    Instant expires = Instant.now(clock).plus(7, ChronoUnit.DAYS);
    String nonce = UUID.randomUUID().toString().replace("-", "");

    String in = linkSigner.sign(userId, slotId, LadderMeetupRsvp.Status.IN, expires, nonce + "I");
    String maybe =
        linkSigner.sign(userId, slotId, LadderMeetupRsvp.Status.MAYBE, expires, nonce + "M");
    String cant =
        linkSigner.sign(userId, slotId, LadderMeetupRsvp.Status.CANT, expires, nonce + "C");

    return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:separate;border-spacing:8px 0;\">"
        + "<tr>"
        + "<td>"
        + linkButton(href("/meetups/email-rsvp?t=" + in), "I'm In")
        + "</td>"
        + "<td>"
        + linkButton(href("/meetups/email-rsvp?t=" + maybe), "Maybe")
        + "</td>"
        + "<td>"
        + linkButton(href("/meetups/email-rsvp?t=" + cant), "Can't")
        + "</td>"
        + "</tr>"
        + "</table>";
  }

  private String href(String path) {
    if (path == null) {
      return "";
    }
    String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
    if (base.isBlank()) {
      return path;
    }
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    String normalizedPath = path.startsWith("/") ? path : ("/" + path);
    return normalizedBase + normalizedPath;
  }

  private String linkButton(String href, String label) {
    String color = "#0d6efd";
    if ("I'm In".equals(label)) {
      color = "#198754";
    }
    if ("Can't".equals(label)) {
      color = "#dc3545";
    }

    return "<a href=\""
        + escape(href)
        + "\" style=\"display:inline-block; padding:10px 18px; border-radius:6px; background-color:"
        + color
        + "; color:#ffffff; text-decoration:none; font-weight:600; font-size:14px; text-align:center;\">"
        + escape(label)
        + "</a>";
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
