package com.w3llspring.fhpb.web.service;

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
import com.w3llspring.fhpb.web.service.meetups.DuplicateMeetupException;
import com.w3llspring.fhpb.web.service.meetups.MeetupCanceledEvent;
import com.w3llspring.fhpb.web.service.meetups.MeetupCreatedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LadderMeetupService {

  private static final int MAX_ACTIVE_PLANS_PER_LADDER_PER_USER = 3;

  public record MeetupRow(
      Long slotId,
      Long ladderId,
      String ladderTitle,
      Instant startsAt,
      String locationCode,
      int inCount,
      int maybeCount,
      int cantCount,
      List<String> inNames,
      List<String> maybeNames,
      LadderMeetupRsvp.Status myStatus,
      boolean canDelete) {}

  private final Clock clock;
  private final LadderMeetupSlotRepository slotRepo;
  private final LadderMeetupRsvpRepository rsvpRepo;
  private final LadderMembershipRepository membershipRepo;
  private final LadderConfigRepository ladderConfigRepo;
  private final UserRepository userRepo;
  private final ApplicationEventPublisher events;

  public LadderMeetupService(
      LadderMeetupSlotRepository slotRepo,
      LadderMeetupRsvpRepository rsvpRepo,
      LadderMembershipRepository membershipRepo,
      LadderConfigRepository ladderConfigRepo,
      UserRepository userRepo,
      ApplicationEventPublisher events) {
    this.clock = Clock.systemUTC();
    this.slotRepo = slotRepo;
    this.rsvpRepo = rsvpRepo;
    this.membershipRepo = membershipRepo;
    this.ladderConfigRepo = ladderConfigRepo;
    this.userRepo = userRepo;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public List<MeetupRow> upcomingForUser(Long userId, int limit) {
    Instant now = Instant.now(clock);
    Instant to = now.plus(14, ChronoUnit.DAYS);

    List<LadderMembership> memberships =
        membershipRepo.findByUserIdAndState(userId, LadderMembership.State.ACTIVE);
    List<Long> ladderIds =
        memberships.stream().map(m -> m.getLadderConfig().getId()).distinct().toList();
    if (ladderIds.isEmpty()) return List.of();

    List<LadderMeetupSlot> slots =
        new ArrayList<>(
            slotRepo.findUpcomingForLadders(ladderIds, now.minus(1, ChronoUnit.HOURS), to));
    if (slots.isEmpty()) return List.of();

    slots.sort(Comparator.comparing(LadderMeetupSlot::getStartsAt));
    if (limit > 0 && slots.size() > limit) {
      slots = slots.subList(0, limit);
    }

    List<Long> slotIds = slots.stream().map(LadderMeetupSlot::getId).toList();
    List<LadderMeetupRsvp> rsvps = rsvpRepo.findBySlotIdIn(slotIds);

    Map<Long, List<LadderMeetupRsvp>> rsvpsBySlot =
        rsvps.stream().collect(Collectors.groupingBy(r -> r.getSlot().getId()));

    Set<Long> userIds = rsvps.stream().map(LadderMeetupRsvp::getUserId).collect(Collectors.toSet());
    Map<Long, String> nickByUserId = new HashMap<>();
    if (!userIds.isEmpty()) {
      for (User u : userRepo.findAllById(userIds)) {
        nickByUserId.put(u.getId(), u.getNickName());
      }
    }

    Map<Long, LadderConfig> ladderById = new HashMap<>();
    for (LadderConfig cfg :
        ladderConfigRepo.findAllById(
            slots.stream().map(s -> s.getLadderConfig().getId()).collect(Collectors.toSet()))) {
      ladderById.put(cfg.getId(), cfg);
    }

    List<MeetupRow> out = new ArrayList<>();
    for (LadderMeetupSlot slot : slots) {
      List<LadderMeetupRsvp> slotRsvps = rsvpsBySlot.getOrDefault(slot.getId(), List.of());

      int inCount = 0;
      int maybeCount = 0;
      int cantCount = 0;
      List<String> inNames = new ArrayList<>();
      List<String> maybeNames = new ArrayList<>();
      LadderMeetupRsvp.Status myStatus = null;

      for (LadderMeetupRsvp r : slotRsvps) {
        if (Objects.equals(r.getUserId(), userId)) {
          myStatus = r.getStatus();
        }
        if (r.getStatus() == LadderMeetupRsvp.Status.IN) {
          inCount++;
          String nick = nickByUserId.get(r.getUserId());
          if (nick != null) inNames.add(nick);
        } else if (r.getStatus() == LadderMeetupRsvp.Status.MAYBE) {
          maybeCount++;
          String nick = nickByUserId.get(r.getUserId());
          if (nick != null) maybeNames.add(nick);
        } else {
          cantCount++;
        }
      }

      inNames.sort(String.CASE_INSENSITIVE_ORDER);
      maybeNames.sort(String.CASE_INSENSITIVE_ORDER);

      LadderConfig cfg = ladderById.get(slot.getLadderConfig().getId());
      String ladderTitle = (cfg != null) ? cfg.getTitle() : "Ladder";

      boolean canDelete = Objects.equals(slot.getCreatedByUserId(), userId);

      out.add(
          new MeetupRow(
              slot.getId(),
              slot.getLadderConfig().getId(),
              ladderTitle,
              slot.getStartsAt(),
              slot.getLocationCode(),
              inCount,
              maybeCount,
              cantCount,
              inNames,
              maybeNames,
              myStatus,
              canDelete));
    }

    out.sort(Comparator.comparing(MeetupRow::startsAt));
    return out;
  }

  @Transactional
  public Long createSlot(Long userId, Long ladderId, Instant startsAt) {
    requireActiveMembership(userId, ladderId);

    Instant now = Instant.now(clock);

    long existingActive =
        slotRepo.countActiveUpcomingByLadderAndCreator(
            ladderId, userId, now.minus(1, ChronoUnit.HOURS));
    if (existingActive >= MAX_ACTIVE_PLANS_PER_LADDER_PER_USER) {
      throw new IllegalArgumentException(
          "You can only have " + MAX_ACTIVE_PLANS_PER_LADDER_PER_USER + " active plans per ladder");
    }

    if (startsAt == null || startsAt.isBefore(now.minus(5, ChronoUnit.MINUTES))) {
      throw new IllegalArgumentException("Start time must be in the future");
    }
    if (startsAt.isAfter(now.plus(90, ChronoUnit.DAYS))) {
      throw new IllegalArgumentException("Start time is too far in the future");
    }

    long dup = slotRepo.countActiveByLadderAndStartsAt(ladderId, startsAt);
    if (dup > 0) {
      throw new DuplicateMeetupException("A Play Plan already exists for that ladder and time.");
    }

    LadderConfig ladder = ladderConfigRepo.findById(ladderId).orElseThrow();

    LadderMeetupSlot slot = new LadderMeetupSlot();
    slot.setLadderConfig(ladder);
    slot.setCreatedByUserId(userId);
    slot.setStartsAt(startsAt);
    slot.setCreatedAt(now);

    LadderMeetupSlot saved = slotRepo.save(slot);

    // Auto-RSVP creator as IN
    setRsvp(userId, saved.getId(), LadderMeetupRsvp.Status.IN);

    // Publish event so notifications (email/push) can be handled asynchronously after commit.
    events.publishEvent(
        new MeetupCreatedEvent(ladderId, userId, ladder.getTitle(), saved.getStartsAt()));

    return saved.getId();
  }

  @Transactional
  public void setRsvp(Long userId, Long slotId, LadderMeetupRsvp.Status status) {
    LadderMeetupSlot slot = slotRepo.findById(slotId).orElseThrow();
    Long ladderId = slot.getLadderConfig().getId();
    requireActiveMembership(userId, ladderId);

    var existingOpt = rsvpRepo.findBySlotIdAndUserId(slotId, userId);
    if (existingOpt.isPresent() && existingOpt.get().getStatus() == status) {
      rsvpRepo.delete(existingOpt.get());
      return;
    }

    LadderMeetupRsvp rsvp = existingOpt.orElseGet(LadderMeetupRsvp::new);
    rsvp.setSlot(slot);
    rsvp.setUserId(userId);
    rsvp.setStatus(status);
    rsvp.setUpdatedAt(Instant.now(clock));
    rsvpRepo.save(rsvp);
  }

  @Transactional
  public void deleteSlot(Long userId, Long slotId) {
    LadderMeetupSlot slot = slotRepo.findById(slotId).orElseThrow();
    Long ladderId = slot.getLadderConfig().getId();
    requireActiveMembership(userId, ladderId);

    if (!Objects.equals(slot.getCreatedByUserId(), userId)) {
      throw new IllegalArgumentException("Not allowed to delete this plan");
    }

    if (slot.getCanceledAt() != null) {
      return;
    }

    // Soft-cancel so other users don't see it, and future auditing is possible.
    Instant canceledAt = Instant.now(clock);
    slot.setCanceledAt(canceledAt);
    slotRepo.save(slot);

    String ladderTitle = null;
    Instant startsAt = null;
    try {
      LadderConfig ladder = slot.getLadderConfig();
      ladderTitle = ladder == null ? null : ladder.getTitle();
      startsAt = slot.getStartsAt();
    } catch (Exception ignored) {
    }

    events.publishEvent(new MeetupCanceledEvent(slotId, ladderId, userId, ladderTitle, startsAt));
  }

  @Transactional
  public void updateLocation(Long userId, Long slotId, String locationCode) {
    LadderMeetupSlot slot = slotRepo.findById(slotId).orElseThrow();
    Long ladderId = slot.getLadderConfig().getId();
    requireActiveMembership(userId, ladderId);

    if (slot.getCanceledAt() != null) {
      throw new IllegalArgumentException("Plan canceled");
    }
    if (!Objects.equals(slot.getCreatedByUserId(), userId)) {
      throw new IllegalArgumentException("Not allowed to update location");
    }

    String cleaned = locationCode == null ? null : locationCode.trim();
    if (cleaned == null || cleaned.isBlank()) {
      slot.setLocationCode(null);
      slotRepo.save(slot);
      return;
    }

    if (cleaned.length() != 2) {
      throw new IllegalArgumentException("Location must be exactly 2 characters");
    }
    // Keep it simple and predictable for display
    if (!cleaned.matches("[A-Za-z0-9]{2}")) {
      throw new IllegalArgumentException("Location must be 2 letters/numbers");
    }
    slot.setLocationCode(cleaned.toUpperCase());
    slotRepo.save(slot);
  }

  private void requireActiveMembership(Long userId, Long ladderId) {
    membershipRepo
        .findByLadderConfigIdAndUserId(ladderId, userId)
        .filter(m -> m.getState() == LadderMembership.State.ACTIVE)
        .orElseThrow(() -> new IllegalArgumentException("Not a ladder member"));
  }
}
