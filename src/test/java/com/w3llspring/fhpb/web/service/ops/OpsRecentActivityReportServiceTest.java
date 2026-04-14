package com.w3llspring.fhpb.web.service.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.config.OpsRecentActivityProperties;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderRatingChangeRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderRatingChange;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OpsRecentActivityReportServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-14T12:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private LadderConfigRepository ladderConfigRepository;
  @Mock private MatchRepository matchRepository;
  @Mock private LadderRatingChangeRepository ladderRatingChangeRepository;

  @TempDir Path tempDir;

  @Test
  void buildEmailReportIncludesDatabaseAndLogSummaries() throws Exception {
    OpsRecentActivityProperties properties = new OpsRecentActivityProperties();
    properties.setLookbackDays(1);
    properties.setActiveMinutes(60);
    properties.setMaxRows(5);
    properties.setMaxErrors(5);
    properties.setLogsDir(tempDir.toString());
    properties.setZone("UTC");
    properties.setSubjectPrefix("Ops: ");

    Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    OpsRecentActivityReportService service =
        new OpsRecentActivityReportService(
            clock,
            userRepository,
            ladderConfigRepository,
            matchRepository,
            ladderRatingChangeRepository,
            properties);

    User seenUser = user(7L, "RecentPlayer");
    seenUser.setLastSeenAt(NOW.minusSeconds(900));

    User newUser = user(8L, "NewPlayer");
    newUser.setRegisteredAt(NOW.minusSeconds(1800));

    LadderConfig recentSession = session(11L, "Morning Session", 41L);
    recentSession.setCreatedAt(NOW.minusSeconds(1200));
    recentSession.setExpiresAt(NOW.plusSeconds(7200));

    LadderConfig activeSession = session(12L, "Lunch Session", 42L);
    activeSession.setUpdatedAt(NOW.minusSeconds(300));
    activeSession.setExpiresAt(NOW.plusSeconds(5400));

    Match match = new Match();
    ReflectionTestUtils.setField(match, "id", 88L);
    match.setCreatedAt(NOW.minusSeconds(600));
    match.setLoggedBy(seenUser);
    match.setA1(seenUser);
    match.setB1(newUser);
    match.setScoreA(11);
    match.setScoreB(7);
    match.setState(MatchState.CONFIRMED);
    match.setSourceSessionConfig(activeSession);

    LadderSeason season = new LadderSeason();
    season.setLadderConfig(activeSession);
    LadderRatingChange change = new LadderRatingChange();
    change.setOccurredAt(NOW.minusSeconds(450));
    change.setUser(seenUser);
    change.setSeason(season);
    change.setRatingDelta(14);
    change.setRatingAfter(1514);
    change.setSummary("Won in straight games");

    when(userRepository.countByLastSeenAtGreaterThanEqual(any())).thenReturn(7L, 2L);
    when(userRepository.countByRegisteredAtGreaterThanEqual(any())).thenReturn(3L);
    when(ladderConfigRepository.countByTypeAndCreatedAtGreaterThanEqual(
            eq(LadderConfig.Type.SESSION), any()))
        .thenReturn(4L);
    when(ladderConfigRepository.countActiveByType(
            eq(LadderConfig.Type.SESSION), eq(LadderConfig.Status.ACTIVE), any()))
        .thenReturn(2L);
    when(matchRepository.countByCreatedAtInRange(any(), any())).thenReturn(5L, 1L);
    when(ladderRatingChangeRepository.countByOccurredAtGreaterThanEqual(any())).thenReturn(6L);

    when(userRepository.findByLastSeenAtGreaterThanEqualOrderByLastSeenAtDesc(
            any(), any(Pageable.class)))
        .thenReturn(List.of(seenUser));
    when(userRepository.findByRegisteredAtGreaterThanEqualOrderByRegisteredAtDesc(
            any(), any(Pageable.class)))
        .thenReturn(List.of(newUser));
    when(ladderConfigRepository.findByTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            eq(LadderConfig.Type.SESSION), any(), any(Pageable.class)))
        .thenReturn(List.of(recentSession));
    when(ladderConfigRepository.findActiveByTypeOrderByUpdatedAtDesc(
            eq(LadderConfig.Type.SESSION),
            eq(LadderConfig.Status.ACTIVE),
            any(),
            any(Pageable.class)))
        .thenReturn(List.of(activeSession));
    when(matchRepository.findByCreatedAtInRange(any(), any(), any(Pageable.class)))
        .thenReturn(List.of(match));
    when(ladderRatingChangeRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            any(), any(Pageable.class)))
        .thenReturn(List.of(change));

    writeLog(
        tempDir.resolve("application.log"),
        NOW.minusSeconds(300),
        "ERROR",
        "[main] com.example.App : Request failed with IllegalStateException");
    writeLog(
        tempDir.resolve("jobs.log"),
        NOW.minusSeconds(240),
        "ERROR",
        "[main] [ops-recent-activity-email] com.example.Job : Job failed hard");
    writeLog(
        tempDir.resolve("application.2026-04-12.0.log"),
        NOW.minusSeconds(172800),
        "ERROR",
        "[main] com.example.App : Old error outside lookback");

    OpsRecentActivityReportService.EmailReport report = service.buildEmailReport();

    assertThat(report.subject()).isEqualTo("Ops: Recent activity 2026-04-14");
    assertThat(report.text()).contains("users seen: 7 (2 in active window)");
    assertThat(report.text()).contains("users registered: 3");
    assertThat(report.text()).contains("sessions created: 4");
    assertThat(report.text()).contains("active sessions now: 2");
    assertThat(report.text()).contains("matches logged: 5 (1 in active window)");
    assertThat(report.text()).contains("ladder rating changes: 6");
    assertThat(report.text()).contains("Recent users seen");
    assertThat(report.text()).contains("RecentPlayer (#7)");
    assertThat(report.text()).contains("Recent new users");
    assertThat(report.text()).contains("NewPlayer (#8)");
    assertThat(report.text()).contains("Morning Session (#11, owner=41");
    assertThat(report.text()).contains("Lunch Session (#12, updated=");
    assertThat(report.text())
        .contains("match #88 | by RecentPlayer (#7) | RecentPlayer 11-7 NewPlayer");
    assertThat(report.text()).contains("Lunch Session | +14 => 1514 | Won in straight games");
    assertThat(report.text()).contains("application.log");
    assertThat(report.text()).contains("jobs.log");
    assertThat(report.text()).doesNotContain("Old error outside lookback");
    assertThat(report.html()).contains("<pre>").doesNotContain("Old error outside lookback");
  }

  private User user(Long id, String nickName) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickName);
    return user;
  }

  private LadderConfig session(Long id, String title, Long ownerUserId) {
    LadderConfig config = new LadderConfig();
    config.setId(id);
    config.setTitle(title);
    config.setOwnerUserId(ownerUserId);
    config.setType(LadderConfig.Type.SESSION);
    config.setStatus(LadderConfig.Status.ACTIVE);
    return config;
  }

  private void writeLog(Path path, Instant timestamp, String level, String body) throws Exception {
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            .withZone(ZoneId.systemDefault());
    Files.writeString(
        path, formatter.format(timestamp) + " " + level + " " + body + System.lineSeparator());
  }
}
