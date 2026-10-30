package com.w3llspring.fhpb.web.service.competition;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
import com.w3llspring.fhpb.web.model.RoundRobinEntry.Position;
import com.w3llspring.fhpb.web.service.*;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinModificationException;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class SessionMatchupServiceTest {
  private final LadderConfigRepository sessions = mock(LadderConfigRepository.class);
  private final LadderMembershipRepository memberships = mock(LadderMembershipRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final RoundRobinRepository schedules = mock(RoundRobinRepository.class);
  private final RoundRobinEntryRepository entries = mock(RoundRobinEntryRepository.class);
  private final RoundRobinService roundRobins = mock(RoundRobinService.class);
  private final CompetitionSeasonService seasons = mock(CompetitionSeasonService.class);
  private final MatchFactory factory = mock(MatchFactory.class);
  private final MatchValidationService validation = mock(MatchValidationService.class);
  private final UserMatchLogGateService gate = mock(UserMatchLogGateService.class);
  private final MatchLoggingQuotaService quota = mock(MatchLoggingQuotaService.class);
  private final CompetitionAutoModerationService moderation =
      mock(CompetitionAutoModerationService.class);
  private final LadderV2Service standings = mock(LadderV2Service.class);
  private final TrophyAwardService trophies = mock(TrophyAwardService.class);
  private SessionMatchupService service;
  private LadderConfig session;
  private RoundRobin rr;
  private User host;
  private final Map<Long, User> people = new LinkedHashMap<>();

  @BeforeEach
  void setup() {
    service =
        new SessionMatchupService(
            sessions,
            memberships,
            users,
            schedules,
            entries,
            roundRobins,
            seasons,
            factory,
            validation,
            gate,
            quota,
            moderation,
            standings,
            trophies,
            mock(com.w3llspring.fhpb.web.service.matchworkflow.MatchStateTransitionService.class),
            true);
    for (long id = 1; id <= 5; id++) people.put(id, user(id));
    host = people.get(1L);
    session = new LadderConfig();
    session.setId(7L);
    session.setOwnerUserId(1L);
    session.setType(LadderConfig.Type.SESSION);
    session.setSessionPlayMode(LadderConfig.SessionPlayMode.MATCHUPS);
    session.setExpiresAt(Instant.now().plusSeconds(3600));
    rr = new RoundRobin();
    ReflectionTestUtils.setField(rr, "id", 9L);
    rr.setSessionConfig(session);
    rr.setSessionFlow(true);
    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 11L);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now(ZoneOffset.UTC));
    season.setEndDate(LocalDate.now(ZoneOffset.UTC).plusDays(7));
    rr.setSeason(season);
    when(sessions.lockById(7L)).thenReturn(session);
    when(schedules.findByIdForUpdate(9L)).thenReturn(Optional.of(rr));
    when(schedules.findBySessionConfig(session)).thenReturn(List.of(rr));
    when(memberships.findByLadderConfigIdAndUserId(eq(7L), anyLong()))
        .thenAnswer(
            inv -> {
              long id = inv.getArgument(1);
              if (!people.containsKey(id)) return Optional.empty();
              LadderMembership m = new LadderMembership();
              m.setLadderConfig(session);
              m.setUserId(id);
              m.setState(LadderMembership.State.ACTIVE);
              return Optional.of(m);
            });
    when(users.findById(anyLong()))
        .thenAnswer(inv -> Optional.ofNullable(people.get(inv.getArgument(0))));
    when(roundRobins.listMembersForLadder(7L)).thenAnswer(inv -> new ArrayList<>(people.values()));
    when(roundRobins.loadMatchesWithParticipants(anyList())).thenReturn(Map.of());
  }

  @ParameterizedTest
  @CsvSource({
    "12, ROTATING_PARTNERS",
    "21, ROTATING_PARTNERS",
    "31, ROTATING_PARTNERS",
    "32, ROTATING_PARTNERS",
    "32, FIXED_TEAMS"
  })
  void startAutomaticallyUsesEveryCurrentMemberIncludingSomeoneWhoJustJoined(
      int count, RoundRobin.Format format) {
    when(schedules.findBySessionConfig(session)).thenReturn(List.of());
    for (long id = 6; id <= count; id++) people.put(id, user(id));
    when(seasons.resolveActiveCompetitionSeason()).thenReturn(rr.getSeason());
    List<Long> expected = new ArrayList<>(people.keySet());
    List<List<Long>> fixedTeams =
        format == RoundRobin.Format.FIXED_TEAMS
            ? java.util.stream.IntStream.range(0, count / 2)
                .mapToObj(i -> List.of(expected.get(i * 2), expected.get(i * 2 + 1)))
                .toList()
            : List.of();
    when(roundRobins.createAndStart(7L, null, expected, 6, 1L, format, fixedTeams)).thenReturn(rr);
    assertThat(service.start(7L, host, format, 6, fixedTeams)).isSameAs(rr);
    verify(roundRobins).createAndStart(7L, null, expected, 6, 1L, format, fixedTeams);
    assertThat(rr.isSessionFlow()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 48})
  void manuallyStartingAnUpcomingSessionBeginsItsPlayingWindowWithoutChangingThePlannedDate(
      int liveHours) {
    ReflectionTestUtils.setField(service, "liveSessionHours", liveHours);
    when(schedules.findBySessionConfig(session)).thenReturn(List.of());
    when(seasons.resolveActiveCompetitionSeason()).thenReturn(rr.getSeason());
    when(roundRobins.createAndStart(
            anyLong(), isNull(), anyList(), anyInt(), anyLong(), any(), anyList()))
        .thenReturn(rr);
    Instant planned = Instant.now().plusSeconds(7 * 86400L);
    session.setSessionScheduledAt(planned);
    session.setSessionTimeZone("America/New_York");
    session.setExpiresAt(planned.plusSeconds(48 * 3600L));
    assertThat(session.isUpcomingSession()).isTrue();
    Instant before = Instant.now();
    service.start(7L, host, RoundRobin.Format.ROTATING_PARTNERS, 6, List.of());
    assertThat(session.getSessionStartedAt()).isBetween(before, Instant.now());
    assertThat(session.getExpiresAt())
        .isEqualTo(session.getSessionStartedAt().plusSeconds(liveHours * 3600L));
    assertThat(session.getSessionScheduledAt()).isEqualTo(planned);
    assertThat(session.isUpcomingSession()).isFalse();
  }

  @Test
  void startRejectsMoreThanThirtyTwoPlayersBeforeGeneratingMatchups() {
    when(schedules.findBySessionConfig(session)).thenReturn(List.of());
    for (long id = 6; id <= 33; id++) people.put(id, user(id));
    assertThatThrownBy(
            () -> service.start(7L, host, RoundRobin.Format.ROTATING_PARTNERS, 6, List.of()))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("up to 32 players");
    verify(roundRobins, never())
        .createAndStart(
            anyLong(), nullable(String.class), anyList(), anyInt(), anyLong(), any(), anyList());
  }

  @Test
  void confirmationModeCannotChangeOnceTheFirstScheduleExists() {
    assertThatThrownBy(
            () -> service.start(7L, host, RoundRobin.Format.ROTATING_PARTNERS, 6, List.of(), true))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("fixed once matchups start");
    assertThat(session.isOrganizerScored()).isFalse();
    verify(roundRobins, never())
        .createAndStart(
            anyLong(), nullable(String.class), anyList(), anyInt(), anyLong(), any(), anyList());
  }

  @Test
  void lateJoinerIsAvailableWithoutChangingTheStartedRoster() {
    RoundRobinEntry entry = game(20, 1);
    when(entries.findByRoundRobinOrderByRoundNumberAscWithUsers(rr)).thenReturn(List.of(entry));
    var view = service.view(session, people.get(5L), Set.of());
    assertThat(view.roster()).hasSize(4);
    assertThat(view.available()).extracting(SessionMatchupService.Member::id).containsExactly(5L);
    assertThat(view.waiting()).isTrue();
    assertThat(view.next()).isNull();
    verify(entries, never()).saveAll(any());
  }

  @Test
  void guestReplacementPreservesPendingAndOutOfOrderRecordedGamesAndKeepsStableSlot() {
    RoundRobinEntry first = game(20, 1), unplayed = game(21, 2), playedAhead = game(22, 6);
    first.setMatchId(100L);
    playedAhead.setMatchId(101L);
    when(entries.findByRoundRobinOrderByRoundNumberAscWithUsers(rr))
        .thenReturn(List.of(first, unplayed, playedAhead));
    assertThat(service.replace(7L, 9L, 1L, null, "  Taylor  ", host)).isEqualTo(1);
    assertThat(first.getA1()).isSameAs(host);
    assertThat(playedAhead.getA1()).isSameAs(host);
    assertThat(unplayed.getA1()).isNull();
    assertThat(unplayed.getA2()).isSameAs(people.get(2L));
    assertThat(unplayed.getB1()).isSameAs(people.get(3L));
    assertThat(unplayed.getB2()).isSameAs(people.get(4L));
    assertThat(unplayed.guestName(Position.A1)).isEqualTo("Taylor");
    assertThat(unplayed.slotId(Position.A1)).isEqualTo(1L);
    var view = service.view(session, host, Set.of());
    assertThat(view.roster())
        .filteredOn(s -> s.id().equals(1L))
        .extracting(SessionMatchupService.Slot::name)
        .containsExactly("Taylor");
    assertThat(service.replace(7L, 9L, 1L, 5L, null, host)).isEqualTo(1);
    assertThat(unplayed.getA1()).isSameAs(people.get(5L));
    assertThat(unplayed.slotId(Position.A1)).isEqualTo(1L);
    assertThat(unplayed.guestName(Position.A1)).isNull();
    verify(users, never()).save(any());
  }

  @Test
  void replacementCannotReuseSomeoneAlreadyPlayingOrAnUnjoinedAccount() {
    when(entries.findByRoundRobinOrderByRoundNumberAscWithUsers(rr))
        .thenReturn(List.of(game(20, 1)));
    assertThatThrownBy(() -> service.replace(7L, 9L, 1L, 2L, null, host))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("already has a place");
    assertThatThrownBy(() -> service.replace(7L, 9L, 1L, 999L, null, host))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("has not joined");
  }

  @Test
  void onlyTheCreatorCanReplacePlayers() {
    assertThatThrownBy(() -> service.replace(7L, 9L, 1L, 5L, null, people.get(2L)))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode.value")
        .isEqualTo(403);
    verify(entries, never()).saveAll(any());
  }

  @Test
  void scoreEntryWorksForALaterRoundWithoutWaitingForOtherGamesOrConfirmations() {
    RoundRobinEntry entry = game(24, 4);
    when(entries.findByIdWithUsersForUpdate(24L)).thenReturn(Optional.of(entry));
    when(validation.validateScore(11, 8))
        .thenReturn(MatchValidationService.ScoreValidationResult.valid());
    when(gate.reserveMatchLogging(eq(1L), any(), eq(false)))
        .thenReturn(UserMatchLogGateService.MatchLogGateResult.allowed(host));
    when(quota.evaluate(host)).thenReturn(MatchLoggingQuotaService.QuotaStatus.allowed(100, 0));
    when(factory.createMatch(any(Match.class)))
        .thenAnswer(
            inv -> {
              Match m = inv.getArgument(0);
              ReflectionTestUtils.setField(m, "id", 200L);
              return m;
            });
    Match match = service.recordScore(7L, 9L, 24L, 11, 8, host);
    assertThat(entry.getMatchId()).isEqualTo(200L);
    assertThat(match.getScoreA()).isEqualTo(11);
    assertThat(match.getState()).isEqualTo(MatchState.PROVISIONAL);
    verify(roundRobins, never()).canAdvance(anyLong());
    verify(standings).applyMatch(match);
    verify(trophies).evaluateMatch(match);
  }

  @Test
  void organizerCanFinishAssignedGamesWithoutTheOrdinaryWeeklyQuota() {
    session.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    RoundRobinEntry entry = game(24, 4);
    when(entries.findByIdWithUsersForUpdate(24L)).thenReturn(Optional.of(entry));
    when(validation.validateScore(11, 8))
        .thenReturn(MatchValidationService.ScoreValidationResult.valid());
    when(gate.reserveMatchLogging(eq(1L), any(), eq(false)))
        .thenReturn(UserMatchLogGateService.MatchLogGateResult.allowed(host));
    when(factory.createMatch(any(Match.class)))
        .thenAnswer(
            inv -> {
              Match m = inv.getArgument(0);
              ReflectionTestUtils.setField(m, "id", 200L);
              m.setState(MatchState.CONFIRMED);
              return m;
            });
    Match saved = service.recordScore(7L, 9L, 24L, 11, 8, host);
    assertThat(saved.isOrganizerConfirmed()).isTrue();
    assertThat(entry.getMatchId()).isEqualTo(200L);
    verify(quota, never()).evaluate(any());
  }

  @Test
  void automaticConfirmationFailureDoesNotConsumeTheGameAssignment() {
    session.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    RoundRobinEntry entry = game(24, 4);
    when(entries.findByIdWithUsersForUpdate(24L)).thenReturn(Optional.of(entry));
    when(validation.validateScore(11, 8))
        .thenReturn(MatchValidationService.ScoreValidationResult.valid());
    when(gate.reserveMatchLogging(eq(1L), any(), eq(false)))
        .thenReturn(UserMatchLogGateService.MatchLogGateResult.allowed(host));
    when(factory.createMatch(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
    assertThatThrownBy(() -> service.recordScore(7L, 9L, 24L, 11, 8, host))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("could not be confirmed");
    assertThat(entry.getMatchId()).isNull();
    verify(entries, never()).save(any());
  }

  @Test
  void duplicateScoreAndScoresFromUnassignedLateJoinersAreRejected() {
    RoundRobinEntry entry = game(24, 4);
    when(entries.findByIdWithUsersForUpdate(24L)).thenReturn(Optional.of(entry));
    assertThatThrownBy(() -> service.recordScore(7L, 9L, 24L, 11, 8, people.get(5L)))
        .isInstanceOf(ResponseStatusException.class);
    entry.setMatchId(200L);
    assertThatThrownBy(() -> service.recordScore(7L, 9L, 24L, 11, 8, host))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("already been recorded");
    verify(factory, never()).createMatch(any(Match.class));
  }

  @Test
  void aScheduleFromAnotherSessionCannotBeChanged() {
    LadderConfig other = new LadderConfig();
    other.setId(8L);
    rr.setSessionConfig(other);
    assertThatThrownBy(() -> service.replace(7L, 9L, 1L, null, "Taylor", host))
        .isInstanceOf(ResponseStatusException.class);
    verify(entries, never()).saveAll(any());
  }

  private User user(long id) {
    User u = new User();
    ReflectionTestUtils.setField(u, "id", id);
    u.setNickName("Player" + id);
    return u;
  }

  private RoundRobinEntry game(long id, int round) {
    RoundRobinEntry e = new RoundRobinEntry();
    ReflectionTestUtils.setField(e, "id", id);
    e.setRoundRobin(rr);
    e.setRoundNumber(round);
    e.setA1(people.get(1L));
    e.setA2(people.get(2L));
    e.setB1(people.get(3L));
    e.setB2(people.get(4L));
    e.initializeSlots();
    return e;
  }
}
