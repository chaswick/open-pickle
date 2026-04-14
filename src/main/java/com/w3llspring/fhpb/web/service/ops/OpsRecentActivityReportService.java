package com.w3llspring.fhpb.web.service.ops;

import com.w3llspring.fhpb.web.config.OpsRecentActivityProperties;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderRatingChangeRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderRatingChange;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
public class OpsRecentActivityReportService {

  private static final Logger log = LoggerFactory.getLogger(OpsRecentActivityReportService.class);
  private static final DateTimeFormatter DISPLAY_TS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a z", Locale.US);
  private static final DateTimeFormatter SUBJECT_DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);
  private static final DateTimeFormatter LOG_TS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
  private static final Pattern LOG_HEADER_PATTERN =
      Pattern.compile(
          "^(?<ts>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(?<level>[A-Z]+)\\s+(?<body>.*)$");

  private final Clock clock;
  private final UserRepository userRepository;
  private final LadderConfigRepository ladderConfigRepository;
  private final MatchRepository matchRepository;
  private final LadderRatingChangeRepository ladderRatingChangeRepository;
  private final OpsRecentActivityProperties properties;

  @Autowired
  public OpsRecentActivityReportService(
      UserRepository userRepository,
      LadderConfigRepository ladderConfigRepository,
      MatchRepository matchRepository,
      LadderRatingChangeRepository ladderRatingChangeRepository,
      OpsRecentActivityProperties properties) {
    this(
        Clock.systemUTC(),
        userRepository,
        ladderConfigRepository,
        matchRepository,
        ladderRatingChangeRepository,
        properties);
  }

  OpsRecentActivityReportService(
      Clock clock,
      UserRepository userRepository,
      LadderConfigRepository ladderConfigRepository,
      MatchRepository matchRepository,
      LadderRatingChangeRepository ladderRatingChangeRepository,
      OpsRecentActivityProperties properties) {
    this.clock = clock;
    this.userRepository = userRepository;
    this.ladderConfigRepository = ladderConfigRepository;
    this.matchRepository = matchRepository;
    this.ladderRatingChangeRepository = ladderRatingChangeRepository;
    this.properties = properties;
  }

  public record EmailReport(String subject, String html, String text) {}

  @Transactional(readOnly = true)
  public EmailReport buildEmailReport() {
    Instant now = Instant.now(clock);
    Instant since = now.minus(Duration.ofDays(properties.getLookbackDays()));
    Instant activeCutoff = now.minus(Duration.ofMinutes(properties.getActiveMinutes()));
    int maxRows = properties.getMaxRows();
    StringBuilder body = new StringBuilder(4096);

    long usersSeen = userRepository.countByLastSeenAtGreaterThanEqual(since);
    long usersSeenRecently = userRepository.countByLastSeenAtGreaterThanEqual(activeCutoff);
    long usersRegistered = userRepository.countByRegisteredAtGreaterThanEqual(since);
    long sessionsCreated =
        ladderConfigRepository.countByTypeAndCreatedAtGreaterThanEqual(
            LadderConfig.Type.SESSION, since);
    long sessionsActiveNow =
        ladderConfigRepository.countActiveByType(
            LadderConfig.Type.SESSION, LadderConfig.Status.ACTIVE, now);
    long matchesLogged = matchRepository.countByCreatedAtInRange(since, now);
    long matchesLoggedRecently = matchRepository.countByCreatedAtInRange(activeCutoff, now);
    long ladderChanges = ladderRatingChangeRepository.countByOccurredAtGreaterThanEqual(since);

    List<User> recentUsers =
        userRepository.findByLastSeenAtGreaterThanEqualOrderByLastSeenAtDesc(
            since, PageRequest.of(0, maxRows));
    List<User> newUsers =
        userRepository.findByRegisteredAtGreaterThanEqualOrderByRegisteredAtDesc(
            since, PageRequest.of(0, maxRows));
    List<LadderConfig> recentSessions =
        ladderConfigRepository.findByTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            LadderConfig.Type.SESSION, since, PageRequest.of(0, maxRows));
    List<LadderConfig> activeSessions =
        ladderConfigRepository.findActiveByTypeOrderByUpdatedAtDesc(
            LadderConfig.Type.SESSION, LadderConfig.Status.ACTIVE, now, PageRequest.of(0, maxRows));
    List<Match> recentMatches =
        matchRepository.findByCreatedAtInRange(since, now, PageRequest.of(0, maxRows));
    List<LadderRatingChange> recentChanges =
        ladderRatingChangeRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            since, PageRequest.of(0, maxRows));
    List<LogHit> logHits = loadRecentLogHits(since);

    line(body, "Recent activity report");
    line(body, "Generated: " + formatTs(now));
    line(
        body,
        "Lookback: last " + properties.getLookbackDays() + " day(s) since " + formatTs(since));
    line(body, "Active window: last " + properties.getActiveMinutes() + " minute(s)");
    blank(body);

    line(body, "Summary");
    line(body, "- users seen: " + usersSeen + " (" + usersSeenRecently + " in active window)");
    line(body, "- users registered: " + usersRegistered);
    line(body, "- sessions created: " + sessionsCreated);
    line(body, "- active sessions now: " + sessionsActiveNow);
    line(
        body,
        "- matches logged: " + matchesLogged + " (" + matchesLoggedRecently + " in active window)");
    line(body, "- ladder rating changes: " + ladderChanges);
    line(body, "- recent log errors: " + logHits.size());
    blank(body);

    appendSection(body, "Recent users seen", recentUsers, this::formatRecentUserSeen);
    appendSection(body, "Recent new users", newUsers, this::formatNewUser);
    appendSection(body, "Recent sessions created", recentSessions, this::formatRecentSession);
    appendSection(body, "Active sessions now", activeSessions, this::formatActiveSession);
    appendSection(body, "Recent matches logged", recentMatches, this::formatRecentMatch);
    appendSection(body, "Recent ladder rating changes", recentChanges, this::formatRatingChange);
    appendSection(body, "Recent application/job log errors", logHits, this::formatLogHit);

    String subject =
        properties.getSubjectPrefix()
            + "Recent activity "
            + SUBJECT_DATE.format(now.atZone(properties.zoneId()));
    String text = body.toString().trim();
    String html =
        "<html><body style=\"font-family:Consolas,Monaco,'Courier New',monospace;\">"
            + "<pre>"
            + HtmlUtils.htmlEscape(text)
            + "</pre></body></html>";
    return new EmailReport(subject, html, text);
  }

  private <T> void appendSection(
      StringBuilder body,
      String title,
      List<T> items,
      java.util.function.Function<T, String> formatter) {
    line(body, title);
    if (items == null || items.isEmpty()) {
      line(body, "- none");
      blank(body);
      return;
    }
    for (T item : items) {
      line(body, "- " + formatter.apply(item));
    }
    blank(body);
  }

  private String formatRecentUserSeen(User user) {
    return formatTs(user.getLastSeenAt()) + " | " + userLabel(user);
  }

  private String formatNewUser(User user) {
    return formatTs(user.getRegisteredAt()) + " | " + userLabel(user);
  }

  private String formatRecentSession(LadderConfig config) {
    return formatTs(config.getCreatedAt())
        + " | "
        + config.getTitle()
        + " (#"
        + config.getId()
        + ", owner="
        + config.getOwnerUserId()
        + ", expires="
        + formatTs(config.getExpiresAt())
        + ")";
  }

  private String formatActiveSession(LadderConfig config) {
    return config.getTitle()
        + " (#"
        + config.getId()
        + ", updated="
        + formatTs(config.getUpdatedAt())
        + ", expires="
        + formatTs(config.getExpiresAt())
        + ")";
  }

  private String formatRecentMatch(Match match) {
    String sessionTitle =
        match.getSourceSessionConfig() != null ? match.getSourceSessionConfig().getTitle() : "n/a";
    return formatTs(match.getCreatedAt())
        + " | match #"
        + match.getId()
        + " | by "
        + userLabel(match.getLoggedBy())
        + " | "
        + matchLabel(match)
        + " | session="
        + sessionTitle
        + " | state="
        + match.getState();
  }

  private String formatRatingChange(LadderRatingChange change) {
    String ladderTitle =
        change.getSeason() != null
                && change.getSeason().getLadderConfig() != null
                && change.getSeason().getLadderConfig().getTitle() != null
            ? change.getSeason().getLadderConfig().getTitle()
            : "unknown ladder";
    return formatTs(change.getOccurredAt())
        + " | "
        + userLabel(change.getUser())
        + " | "
        + ladderTitle
        + " | "
        + signed(change.getRatingDelta())
        + " => "
        + change.getRatingAfter()
        + " | "
        + summarize(change.getSummary(), 120);
  }

  private String formatLogHit(LogHit hit) {
    return formatTs(hit.timestamp()) + " | " + hit.source() + " | " + hit.message();
  }

  private List<LogHit> loadRecentLogHits(Instant since) {
    Path logsDir = Path.of(properties.getLogsDir());
    if (!Files.isDirectory(logsDir)) {
      log.debug("Ops recent-activity logs directory not found: {}", logsDir.toAbsolutePath());
      return List.of();
    }

    try (Stream<Path> stream = Files.list(logsDir)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(this::isScannableLogFile)
          .flatMap(path -> scanLogFile(path, since).stream())
          .sorted(Comparator.comparing(LogHit::timestamp).reversed())
          .limit(properties.getMaxErrors())
          .toList();
    } catch (IOException ex) {
      log.warn("Ops recent-activity could not read logs from {}: {}", logsDir, ex.getMessage());
      return List.of();
    }
  }

  private boolean isScannableLogFile(Path path) {
    String fileName = path.getFileName().toString();
    if (!fileName.endsWith(".log")) {
      return false;
    }
    return fileName.startsWith("application") || fileName.startsWith("jobs");
  }

  private List<LogHit> scanLogFile(Path path, Instant since) {
    List<LogHit> hits = new ArrayList<>();
    try (Stream<String> lines = Files.lines(path)) {
      lines.forEach(
          line -> {
            Matcher matcher = LOG_HEADER_PATTERN.matcher(line);
            if (!matcher.matches()) {
              return;
            }
            if (!"ERROR".equals(matcher.group("level"))) {
              return;
            }
            Instant timestamp = parseLogTimestamp(matcher.group("ts"));
            if (timestamp == null || timestamp.isBefore(since)) {
              return;
            }
            hits.add(
                new LogHit(
                    path.getFileName().toString(),
                    timestamp,
                    summarize(matcher.group("body"), 220)));
          });
    } catch (IOException ex) {
      log.warn("Ops recent-activity could not scan log file {}: {}", path, ex.getMessage());
    }
    return hits;
  }

  private Instant parseLogTimestamp(String raw) {
    try {
      return LocalDateTime.parse(raw, LOG_TS).atZone(java.time.ZoneId.systemDefault()).toInstant();
    } catch (Exception ignored) {
      return null;
    }
  }

  private String formatTs(Instant instant) {
    if (instant == null) {
      return "n/a";
    }
    return DISPLAY_TS.format(instant.atZone(properties.zoneId()));
  }

  private String userLabel(User user) {
    if (user == null) {
      return "guest/unknown";
    }
    String nickName =
        user.getNickName() == null || user.getNickName().isBlank()
            ? "user"
            : user.getNickName().trim();
    return nickName + " (#" + user.getId() + ")";
  }

  private String matchLabel(Match match) {
    return playerLabel(match.getA1(), match.isA1Guest())
        + partnerLabel(match.getA2(), match.isA2Guest())
        + " "
        + match.getScoreA()
        + "-"
        + match.getScoreB()
        + " "
        + playerLabel(match.getB1(), match.isB1Guest())
        + partnerLabel(match.getB2(), match.isB2Guest());
  }

  private String playerLabel(User user, boolean guest) {
    if (guest) {
      return "Guest";
    }
    return user == null || user.getNickName() == null || user.getNickName().isBlank()
        ? "Unknown"
        : user.getNickName().trim();
  }

  private String partnerLabel(User user, boolean guest) {
    if (guest) {
      return " + Guest";
    }
    if (user == null || user.getNickName() == null || user.getNickName().isBlank()) {
      return "";
    }
    return " + " + user.getNickName().trim();
  }

  private String signed(int value) {
    return value >= 0 ? "+" + value : Integer.toString(value);
  }

  private String summarize(String value, int maxLen) {
    if (value == null || value.isBlank()) {
      return "n/a";
    }
    String normalized = value.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= maxLen) {
      return normalized;
    }
    return normalized.substring(0, Math.max(0, maxLen - 3)) + "...";
  }

  private void line(StringBuilder body, String value) {
    body.append(value).append(System.lineSeparator());
  }

  private void blank(StringBuilder body) {
    body.append(System.lineSeparator());
  }

  private record LogHit(String source, Instant timestamp, String message) {}
}
