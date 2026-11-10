package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.*;

import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
import com.w3llspring.fhpb.web.service.competition.*;
import com.w3llspring.fhpb.web.service.jobs.maintenance.SessionLadderCleanupJob;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional
class SessionSchedulingIntegrationTest {
  @Autowired LadderConfigService service;
  @Autowired GroupCreationService creation;
  @Autowired CompetitionSessionService summaries;
  @Autowired LadderConfigRepository configs;
  @Autowired LadderMembershipRepository memberships;
  @Autowired UserRepository users;
  @Autowired LadderSeasonRepository seasons;
  @Autowired SessionLadderCleanupJob cleanup;
  @Autowired com.w3llspring.fhpb.web.controller.competition.SessionMatchupController controller;
  @Autowired CompetitionSeasonService competitionSeasons;
  @Autowired SessionJoinRequestService joinRequests;
  @Autowired SessionJoinRequestRepository requestRows;
  @Autowired MatchRepository matches;
  @Autowired com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService roundRobins;
  private User owner;
  private LadderSeason season;

  @BeforeEach
  void setup() {
    owner = new User();
    owner.setEmail("schedule-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
    owner.setNickName("Organizer");
    owner.setPassword("unused");
    owner.setTimeZone("America/Los_Angeles");
    owner = users.saveAndFlush(owner);
    LadderConfig competition = new LadderConfig();
    competition.setOwnerUserId(owner.getId());
    competition.setTitle("Scheduling season");
    competition.setType(LadderConfig.Type.COMPETITION);
    configs.saveAndFlush(competition);
    season = new LadderSeason();
    season.setLadderConfig(competition);
    season.setName("Scheduling season");
    season.setStartDate(LocalDate.now().minusDays(1));
    season.setEndDate(LocalDate.now().plusDays(100));
    season.setState(LadderSeason.State.ACTIVE);
    season = seasons.saveAndFlush(season);
  }

  @Test
  void upcomingSignupsDoNotBlockNowAndNowDoesNotBlockAnotherUpcomingSession() {
    LocalDate date = LocalDate.now(ZoneId.of(owner.getTimeZone())).plusWeeks(1);
    var first = creation.createOrReuseSession(owner.getId(), null, season, date);
    assertThat(first.success()).isTrue();
    assertThat(service.findReusableSessionConfig(owner.getId())).isNull();
    assertThat(creation.canStartSessionNow(owner.getId())).isTrue();

    var current = creation.createOrReuseSession(owner.getId(), null, season);
    assertThat(current.success()).isTrue();
    assertThat(current.reusedExistingSession()).isFalse();
    assertThat(current.configId()).isNotEqualTo(first.configId());
    assertThat(service.findReusableSessionConfig(owner.getId()).getId())
        .isEqualTo(current.configId());

    var later = creation.createOrReuseSession(owner.getId(), null, season, date.plusDays(1));
    assertThat(later.success()).isTrue();
    assertThat(later.reusedExistingSession()).isFalse();
    var summary =
        summaries.summarizeSessions(
            memberships.findByUserIdAndState(owner.getId(), LadderMembership.State.ACTIVE),
            owner.getId(),
            null);
    assertThat(summary.sessionMemberships()).hasSize(3);
    assertThat(summary.selectedSessionMembership().getLadderConfig().getId())
        .isEqualTo(current.configId());
    assertThat(summary.hasOwnedCompetitionSession()).isTrue();
  }

  @Test
  void laterFormKeepsItsDateAndScoringPolicyWhileTheOrganizerHasACurrentSession() {
    service.createSessionConfig(owner.getId(), null, season);
    LocalDate date = LocalDate.now(ZoneId.of(owner.getTimeZone())).plusWeeks(1);
    var auth =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new CustomUserDetails(owner), null, java.util.List.of());
    var flash = new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
    String result =
        controller.create(
            auth,
            LadderConfig.SessionPlayMode.MATCHUPS,
            true,
            "LATER",
            date.toString(),
            "18:30",
            "  Amateur Bracket  ",
            flash);
    assertThat(result).startsWith("redirect:/groups/").endsWith("#matchups");
    LadderConfig planned =
        configs.findByOwnerUserId(owner.getId()).stream()
            .filter(LadderConfig::isUpcomingSession)
            .findFirst()
            .orElseThrow();
    assertThat(planned.getSessionScheduledDate()).isEqualTo(date);
    assertThat(planned.getTitle()).isEqualTo("Organizer's \"Amateur Bracket\" session");
    assertThat(planned.isOrganizerScored()).isTrue();
    assertThat(planned.getSessionStartedAt()).isNull();

    var missing = new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
    assertThat(
            controller.create(
                auth,
                LadderConfig.SessionPlayMode.OPEN_PLAY,
                false,
                "LATER",
                "",
                "18:30",
                "Amateur Bracket",
                missing))
        .isEqualTo("redirect:/groups/new-session");
    assertThat(missing.getFlashAttributes().get("sessionWhen")).isEqualTo("LATER");
    assertThat(missing.getFlashAttributes().get("sessionPlayMode"))
        .isEqualTo(LadderConfig.SessionPlayMode.OPEN_PLAY);
    assertThat(
            configs.countSessionsByTiming(
                owner.getId(),
                LadderConfig.Type.SESSION,
                LadderConfig.Status.ACTIVE,
                planned.getSessionScheduledAt().minusNanos(1),
                true))
        .isEqualTo(1);
    assertThat(
            configs.countSessionsByTiming(
                owner.getId(),
                LadderConfig.Type.SESSION,
                LadderConfig.Status.ACTIVE,
                planned.getSessionScheduledAt(),
                true))
        .isZero();
    assertThat(
            configs.countSessionsByTiming(
                owner.getId(),
                LadderConfig.Type.SESSION,
                LadderConfig.Status.ACTIVE,
                planned.getSessionScheduledAt(),
                false))
        .isEqualTo(1);
  }

  @Test
  void timedSessionsPersistTheSelectedTimeAndExpireFromThatInstant() {
    ZoneId zone = ZoneId.of(owner.getTimeZone());
    LocalDate date = LocalDate.now(zone).plusDays(1);
    LadderConfig planned =
        service.createSessionConfig(owner.getId(), null, season, date, LocalTime.of(18, 30));
    assertThat(planned.getSessionScheduledAt())
        .isEqualTo(date.atTime(18, 30).atZone(zone).toInstant());
    assertThat(planned.getExpiresAt())
        .isEqualTo(planned.getSessionScheduledAt().plus(Duration.ofHours(48)));
    assertThat(planned.getSessionDateLabel()).contains("6:30 PM");
    assertThat(planned.isUpcomingSession(planned.getSessionScheduledAt().minusSeconds(1))).isTrue();
    assertThat(planned.isUpcomingSession(planned.getSessionScheduledAt())).isFalse();
  }

  @Test
  void laterRequiresATimeAndPreservesInputsOnValidationFailure() {
    var auth =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new CustomUserDetails(owner), null, java.util.List.of());
    var flash = new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
    String date = LocalDate.now(ZoneId.of(owner.getTimeZone())).plusDays(1).toString();
    assertThat(
            controller.create(
                auth,
                LadderConfig.SessionPlayMode.MATCHUPS,
                true,
                "LATER",
                date,
                "",
                "Amateur Bracket",
                flash))
        .isEqualTo("redirect:/groups/new-session");
    assertThat(flash.getFlashAttributes().get("sessionDate")).isEqualTo(date);
    assertThat(flash.getFlashAttributes().get("sessionTime")).isEqualTo("");
    assertThat(flash.getFlashAttributes().get("sessionName")).isEqualTo("Amateur Bracket");
    assertThat(configs.findByOwnerUserId(owner.getId())).noneMatch(LadderConfig::isSessionType);
  }

  @Test
  void blankNameKeepsTheDefaultAndReopeningDoesNotRenameTheCurrentSession() {
    var auth =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new CustomUserDetails(owner), null, java.util.List.of());
    var flash = new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
    String destination =
        controller.create(
            auth, LadderConfig.SessionPlayMode.OPEN_PLAY, false, "NOW", null, null, "   ", flash);
    LadderConfig current =
        configs.findByOwnerUserId(owner.getId()).stream()
            .filter(LadderConfig::isSessionType)
            .findFirst()
            .orElseThrow();
    assertThat(current.getTitle()).isEqualTo("Organizer's Session");
    assertThat(
            controller.create(
                auth,
                LadderConfig.SessionPlayMode.MATCHUPS,
                true,
                "NOW",
                null,
                null,
                "Different event",
                flash))
        .isEqualTo(destination);
    assertThat(current.getTitle()).isEqualTo("Organizer's Session");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"<b>Event</b>", "12345678901234567890123456789012345678901"})
  void invalidSessionNamesAreRejectedBeforeCreation(String name) {
    var auth =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new CustomUserDetails(owner), null, java.util.List.of());
    var flash = new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
    assertThat(
            controller.create(
                auth, LadderConfig.SessionPlayMode.MATCHUPS, true, "NOW", null, null, name, flash))
        .isEqualTo("redirect:/groups/new-session");
    assertThat(flash.getFlashAttributes().get("sessionName")).isEqualTo(name);
    assertThat(flash.getFlashAttributes().get("toastMessage").toString()).contains("Session name");
    assertThat(configs.findByOwnerUserId(owner.getId())).noneMatch(LadderConfig::isSessionType);
  }

  @Test
  void plannedOpenPlayCanLogEarlyAndFindsTheNewSeasonWithoutChangingItsSchedule() {
    LocalDate date = LocalDate.now(ZoneId.of(owner.getTimeZone())).plusWeeks(1);
    LadderConfig planned = service.createSessionConfig(owner.getId(), null, season, date);
    Instant scheduledAt = planned.getSessionScheduledAt();
    Instant expiresAt = planned.getExpiresAt();
    var context =
        new com.w3llspring.fhpb.web.service.matchlog.MatchLogContextService(
            seasons, configs, memberships, null, null, null, ZoneId.of("America/New_York"));
    assertThat(
            context
                .resolveMatchLogContext(season.getId(), planned.getId(), Instant.now(), false)
                .getSeason())
        .isEqualTo(season);
    season.setState(LadderSeason.State.ENDED);
    seasons.saveAndFlush(season);
    LadderSeason next = new LadderSeason();
    next.setLadderConfig(season.getLadderConfig());
    next.setName("Next season");
    next.setState(LadderSeason.State.ACTIVE);
    next.setStartDate(LocalDate.now().minusDays(1));
    next.setEndDate(LocalDate.now().plusDays(100));
    next = seasons.saveAndFlush(next);
    assertThat(competitionSeasons.resolveTargetSeason(planned).getId()).isEqualTo(next.getId());
    assertThat(
            context
                .resolveMatchLogContext(season.getId(), planned.getId(), Instant.now(), false)
                .getSeason()
                .getId())
        .isEqualTo(next.getId());
    assertThat(planned.isUpcomingSession()).isTrue();
    assertThat(planned.getSessionStartedAt()).isNull();
    assertThat(planned.getSessionScheduledAt()).isEqualTo(scheduledAt);
    assertThat(planned.getExpiresAt()).isEqualTo(expiresAt);
  }

  @Test
  void earlyLoggingStillRejectsUnstartedMatchupsAndEndedSessions() {
    LocalDate date = LocalDate.now(ZoneId.of(owner.getTimeZone())).plusWeeks(1);
    LadderConfig planned = service.createSessionConfig(owner.getId(), null, season, date);
    var context =
        new com.w3llspring.fhpb.web.service.matchlog.MatchLogContextService(
            seasons, configs, memberships, null, null, null, ZoneId.of("America/New_York"));
    planned.setSessionPlayMode(LadderConfig.SessionPlayMode.MATCHUPS);
    configs.saveAndFlush(planned);
    assertThat(
            context
                .resolveMatchLogContext(season.getId(), planned.getId(), Instant.now(), false)
                .getSeason())
        .isNull();
    planned.setSessionPlayMode(LadderConfig.SessionPlayMode.OPEN_PLAY);
    planned.setExpiresAt(Instant.now().minusSeconds(1));
    configs.saveAndFlush(planned);
    assertThat(
            context
                .resolveMatchLogContext(season.getId(), planned.getId(), Instant.now(), false)
                .getSeason())
        .isNull();
    planned.setExpiresAt(Instant.now().plusSeconds(3600));
    planned.setStatus(LadderConfig.Status.ARCHIVED);
    configs.saveAndFlush(planned);
    assertThat(
            context
                .resolveMatchLogContext(season.getId(), planned.getId(), Instant.now(), false)
                .getSeason())
        .isNull();
  }

  @Test
  void sessionTotalsIncludeConfirmedGamesFromBothSidesOfASeasonBoundary() {
    LadderConfig planned =
        service.createSessionConfig(
            owner.getId(),
            null,
            season,
            LocalDate.now(ZoneId.of(owner.getTimeZone())).plusWeeks(1));
    User opponent = new User();
    opponent.setEmail("opponent-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
    opponent.setNickName("Opponent");
    opponent.setPassword("unused");
    opponent = users.saveAndFlush(opponent);
    LadderSeason old = new LadderSeason();
    old.setLadderConfig(season.getLadderConfig());
    old.setName("Previous season");
    old.setState(LadderSeason.State.ENDED);
    old.setStartDate(LocalDate.now().minusWeeks(6));
    old.setEndDate(LocalDate.now().minusDays(2));
    old = seasons.saveAndFlush(old);
    for (int i = 0; i < 3; i++) {
      Match match = new Match();
      match.setSeason(i == 0 ? old : season);
      match.setPlayedAt(i == 0 ? Instant.now().minus(Duration.ofDays(3)) : Instant.now());
      match.setSourceSessionConfig(planned);
      match.setA1(owner);
      match.setB1(opponent);
      match.setScoreA(11);
      match.setScoreB(7);
      match.setState(i == 2 ? MatchState.PROVISIONAL : MatchState.CONFIRMED);
      matches.saveAndFlush(match);
    }
    var standings = roundRobins.computeStandingsForSession(planned);
    assertThat(standings).hasSize(2);
    assertThat(standings.getFirst().getUserId()).isEqualTo(owner.getId());
    assertThat(standings.getFirst().getWins()).isEqualTo(2);
    assertThat(standings.getFirst().getPointsFor()).isEqualTo(22);
  }

  @Test
  void advanceSignupsCanBeApprovedAfterTheFiveMinuteLiveRequestWindow() {
    LocalDate date = LocalDate.now(ZoneId.of(owner.getTimeZone())).plusWeeks(1);
    LadderConfig planned = service.createSessionConfig(owner.getId(), null, season, date);
    User player = new User();
    player.setEmail("join-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
    player.setNickName("Joining player");
    player.setPassword("unused");
    player = users.saveAndFlush(player);
    var submitted = joinRequests.submitByInvite(planned.getInviteCode(), player.getId());
    SessionJoinRequest request = requestRows.findById(submitted.requestId()).orElseThrow();
    assertThat(request.getExpiresAt()).isEqualTo(planned.getExpiresAt());
    request.setRequestedAt(Instant.now().minusSeconds(86400));
    requestRows.saveAndFlush(request);
    assertThat(joinRequests.approve(planned.getId(), request.getId(), owner.getId()).status())
        .isEqualTo(SessionJoinRequest.Status.APPROVED);
    assertThat(
            memberships
                .findByLadderConfigIdAndUserId(planned.getId(), player.getId())
                .orElseThrow()
                .getState())
        .isEqualTo(LadderMembership.State.ACTIVE);

    LadderConfig cancelled =
        service.createSessionConfig(owner.getId(), null, season, date.plusDays(1));
    var pending = joinRequests.submitByInvite(cancelled.getInviteCode(), player.getId());
    cancelled.setStatus(LadderConfig.Status.ARCHIVED);
    configs.saveAndFlush(cancelled);
    assertThat(joinRequests.getStatusForRequester(pending.requestId(), player.getId()).status())
        .isEqualTo(SessionJoinRequest.Status.EXPIRED);
  }

  @Test
  void upcomingCapacityLeavesTheCurrentSessionCapacityAvailable() {
    LocalDate date = LocalDate.now(ZoneId.of(owner.getTimeZone())).plusWeeks(1);
    for (int i = 0; i < service.allowedSessionsPerUser(); i++) {
      service.createSessionConfig(owner.getId(), null, season, date.plusDays(i));
    }
    assertThatThrownBy(
            () -> service.createSessionConfig(owner.getId(), null, season, date.plusDays(4)))
        .hasMessageContaining("upcoming sessions");
    assertThat(service.hasReachedSessionLimit(owner.getId())).isFalse();
    assertThat(service.createSessionConfig(owner.getId(), null, season).isUpcomingSession())
        .isFalse();
  }

  @Test
  void aShortLiveWindowStillLeavesTheWholePlannedDayAvailable() {
    int originalHours =
        (Integer)
            org.springframework.test.util.ReflectionTestUtils.getField(
                service, "defaultSessionLifetimeHours");
    org.springframework.test.util.ReflectionTestUtils.setField(
        service, "defaultSessionLifetimeHours", 8);
    try {
      owner.setTimeZone("America/New_York");
      users.saveAndFlush(owner);
      ZoneId zone = ZoneId.of(owner.getTimeZone());
      LocalDate date =
          LocalDate.now(zone)
              .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SUNDAY))
              .plusWeeks(1);
      LadderConfig planned = service.createSessionConfig(owner.getId(), null, season, date);
      Instant midnight = date.atStartOfDay(zone).toInstant();
      assertThat(planned.getSessionScheduledDate()).isEqualTo(date);
      assertThat(planned.getExpiresAt()).isEqualTo(midnight.plus(Duration.ofHours(48)));
      assertThat(planned.getExpiresAt()).isAfter(date.plusDays(1).atStartOfDay(zone).toInstant());

      Instant before = Instant.now();
      LadderConfig now = service.createSessionConfig(owner.getId(), null, season);
      assertThat(now.getExpiresAt())
          .isBetween(before.plus(Duration.ofHours(8)), Instant.now().plus(Duration.ofHours(8)));
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          service, "defaultSessionLifetimeHours", originalHours);
    }
  }

  @Test
  void scheduleKeepsItsOrganizerTimezoneAndArchivesAnUnstartedExpiredSession() {
    LocalDate date = LocalDate.now(ZoneId.of(owner.getTimeZone())).plusWeeks(1);
    LadderConfig planned = service.createSessionConfig(owner.getId(), null, season, date);
    Instant midnight = date.atStartOfDay(ZoneId.of("America/Los_Angeles")).toInstant();
    assertThat(planned.getSessionScheduledAt()).isEqualTo(midnight);
    assertThat(planned.getExpiresAt()).isEqualTo(midnight.plus(Duration.ofHours(48)));
    assertThat(planned.getSessionStartedAt()).isNull();
    owner.setTimeZone("Asia/Tokyo");
    users.saveAndFlush(owner);
    assertThat(planned.getSessionScheduledDate()).isEqualTo(date);
    assertThat(planned.getSessionTimeZone()).isEqualTo("America/Los_Angeles");
    planned.setExpiresAt(Instant.now().minusSeconds(1));
    configs.saveAndFlush(planned);
    cleanup.archiveExpiredSessions();
    assertThat(configs.findById(planned.getId()).orElseThrow().getStatus())
        .isEqualTo(LadderConfig.Status.ARCHIVED);
    assertThat(
            memberships.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                planned.getId(), LadderMembership.State.ACTIVE))
        .isEmpty();
  }

  @Test
  void midnightChangesClassificationWithoutStartingMatchupsAndHandlesDaylightSaving() {
    LadderConfig planned = new LadderConfig();
    planned.setType(LadderConfig.Type.SESSION);
    planned.setSessionTimeZone("America/New_York");
    for (LocalDate date : new LocalDate[] {LocalDate.of(2027, 3, 14), LocalDate.of(2027, 11, 7)}) {
      Instant midnight = date.atStartOfDay(ZoneId.of(planned.getSessionTimeZone())).toInstant();
      planned.setSessionScheduledAt(midnight);
      assertThat(planned.isUpcomingSession(midnight.minusNanos(1))).isTrue();
      assertThat(planned.isUpcomingSession(midnight)).isFalse();
      assertThat(planned.getSessionStartedAt()).isNull();
      assertThat(planned.getSessionScheduledDate()).isEqualTo(date);
    }
    planned.setSessionStartedAt(planned.getSessionScheduledAt().minusSeconds(3600));
    assertThat(planned.isUpcomingSession(planned.getSessionScheduledAt().minusSeconds(60)))
        .isFalse();
  }

  @Test
  void invalidDatesCannotCreateUnboundedOrPastSessions() {
    LocalDate today = LocalDate.now(ZoneId.of(owner.getTimeZone()));
    for (LocalDate invalid : new LocalDate[] {today.minusDays(1), today.plusDays(91)}) {
      assertThatThrownBy(() -> service.createSessionConfig(owner.getId(), null, season, invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("next 90 days");
    }
    assertThatThrownBy(
            () ->
                service.createSessionConfig(owner.getId(), null, season, today, LocalTime.MIDNIGHT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("future date and time");
    assertThat(
            configs.countSessionsByTiming(
                owner.getId(),
                LadderConfig.Type.SESSION,
                LadderConfig.Status.ACTIVE,
                Instant.now(),
                true))
        .isZero();
  }
}
