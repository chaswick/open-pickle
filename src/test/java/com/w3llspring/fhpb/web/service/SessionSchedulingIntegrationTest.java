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
            auth, LadderConfig.SessionPlayMode.MATCHUPS, true, "LATER", date.toString(), flash);
    assertThat(result).startsWith("redirect:/groups/").endsWith("#matchups");
    LadderConfig planned =
        configs.findByOwnerUserId(owner.getId()).stream()
            .filter(LadderConfig::isUpcomingSession)
            .findFirst()
            .orElseThrow();
    assertThat(planned.getSessionScheduledDate()).isEqualTo(date);
    assertThat(planned.isOrganizerScored()).isTrue();
    assertThat(planned.getSessionStartedAt()).isNull();

    var missing = new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
    assertThat(
            controller.create(
                auth, LadderConfig.SessionPlayMode.OPEN_PLAY, false, "LATER", "", missing))
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
  void plannedOpenPlayCannotLogEarlyAndFindsTheNewSeasonAfterARollover() {
    LocalDate date = LocalDate.now(ZoneId.of(owner.getTimeZone())).plusWeeks(1);
    LadderConfig planned = service.createSessionConfig(owner.getId(), null, season, date);
    var context =
        new com.w3llspring.fhpb.web.service.matchlog.MatchLogContextService(
            seasons, configs, memberships, null, null, null, ZoneId.of("America/New_York"));
    assertThat(
            context
                .resolveMatchLogContext(season.getId(), planned.getId(), Instant.now(), false)
                .getSeason())
        .isNull();
    season.setState(LadderSeason.State.ENDED);
    seasons.saveAndFlush(season);
    LadderSeason next = new LadderSeason();
    next.setLadderConfig(season.getLadderConfig());
    next.setName("Next season");
    next.setState(LadderSeason.State.ACTIVE);
    next.setStartDate(LocalDate.now().minusDays(1));
    next.setEndDate(LocalDate.now().plusDays(100));
    next = seasons.saveAndFlush(next);
    planned.setSessionScheduledAt(Instant.now().minusSeconds(86400));
    planned = configs.saveAndFlush(planned);
    assertThat(competitionSeasons.resolveTargetSeason(planned).getId()).isEqualTo(next.getId());
    assertThat(
            context
                .resolveMatchLogContext(season.getId(), planned.getId(), Instant.now(), false)
                .getSeason()
                .getId())
        .isEqualTo(next.getId());
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
    for (LocalDate invalid : new LocalDate[] {today.minusDays(1), today, today.plusDays(91)}) {
      assertThatThrownBy(() -> service.createSessionConfig(owner.getId(), null, season, invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("next 90 days");
    }
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
