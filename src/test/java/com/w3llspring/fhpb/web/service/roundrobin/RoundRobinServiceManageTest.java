package com.w3llspring.fhpb.web.service.roundrobin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoundRobinServiceManageTest {

  private static final LocalDate TODAY_UTC = LocalDate.now(ZoneOffset.UTC);

  @Mock private LadderMembershipRepository membershipRepo;
  @Mock private UserRepository userRepo;
  @Mock private LadderSeasonRepository seasonRepo;
  @Mock private LadderConfigRepository ladderConfigRepo;
  @Mock private MatchRepository matchRepo;
  @Mock private RoundRobinRepository rrRepo;
  @Mock private RoundRobinEntryRepository rrEntryRepo;
  @Mock private CourtNameService courtNameService;
  @Mock private RoundRobinScheduler roundRobinScheduler;

  private RoundRobinService service;

  @BeforeEach
  void setUp() {
    service =
        new RoundRobinService(
            membershipRepo,
            userRepo,
            seasonRepo,
            matchRepo,
            rrRepo,
            rrEntryRepo,
            courtNameService,
            roundRobinScheduler);
    ReflectionTestUtils.setField(service, "ladderConfigRepo", ladderConfigRepo);
    lenient()
        .when(courtNameService.gatherCourtNamesForUser(anyLong(), nullable(Long.class)))
        .thenReturn(java.util.Set.of());
  }

  @Test
  void createAndStart_rejectsWhenNoActiveSeason() {
    when(seasonRepo.findActive(77L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.createAndStart(77L, null, List.of(1L, 2L, 3L, 4L), 0, null))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("active season");

    verify(rrRepo, never()).save(any(RoundRobin.class));
    verify(roundRobinScheduler, never()).generateWithExplanation(anyList(), anyMap(), anyInt());
  }

  @Test
  void createAndStart_rejectsWhenActiveSeasonHasNotStartedYet() {
    LadderSeason season = new LadderSeason();
    LadderConfig config = new LadderConfig();
    config.setId(77L);
    season.setLadderConfig(config);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(TODAY_UTC.plusDays(1));
    season.setEndDate(TODAY_UTC.plusDays(30));

    when(seasonRepo.findActive(77L)).thenReturn(Optional.of(season));

    assertThatThrownBy(() -> service.createAndStart(77L, null, List.of(1L, 2L, 3L, 4L), 0, null))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("active season");

    verify(rrRepo, never()).save(any(RoundRobin.class));
    verify(roundRobinScheduler, never()).generateWithExplanation(anyList(), anyMap(), anyInt());
  }

  @Test
  void createAndStart_rejectsWhenActiveSeasonAlreadyEndedByDate() {
    LadderSeason season = new LadderSeason();
    LadderConfig config = new LadderConfig();
    config.setId(77L);
    season.setLadderConfig(config);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(TODAY_UTC.minusDays(30));
    season.setEndDate(TODAY_UTC.minusDays(1));

    when(seasonRepo.findActive(77L)).thenReturn(Optional.of(season));

    assertThatThrownBy(() -> service.createAndStart(77L, null, List.of(1L, 2L, 3L, 4L), 0, null))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("active season");

    verify(rrRepo, never()).save(any(RoundRobin.class));
    verify(roundRobinScheduler, never()).generateWithExplanation(anyList(), anyMap(), anyInt());
  }

  @Test
  void createAndStart_usesActiveSeason() throws Exception {
    LadderSeason season = new LadderSeason();
    LadderConfig config = new LadderConfig();
    config.setId(77L);
    season.setLadderConfig(config);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(TODAY_UTC.minusDays(1));
    season.setEndDate(TODAY_UTC.plusDays(30));

    when(seasonRepo.findActive(77L)).thenReturn(Optional.of(season));
    when(rrRepo.findBySeasonAndSessionConfigIsNull(season)).thenReturn(List.of());
    when(rrRepo.save(any(RoundRobin.class)))
        .thenAnswer(
            invocation -> {
              RoundRobin saved = invocation.getArgument(0);
              setId(saved, 1000L);
              return saved;
            });

    RoundRobinScheduler.GenerationResult generation = new RoundRobinScheduler.GenerationResult();
    generation.schedule = List.of();
    when(roundRobinScheduler.generateWithExplanation(anyList(), anyMap(), eq(0)))
        .thenReturn(generation);

    RoundRobin created = service.createAndStart(77L, null, List.of(11L, 12L, 13L, 14L), 0, null);

    assertThat(created).isNotNull();
    assertThat(created.getSeason()).isEqualTo(season);
    verify(rrRepo).save(any(RoundRobin.class));
    verify(roundRobinScheduler).generateWithExplanation(anyList(), anyMap(), eq(0));
  }

  @Test
  void createAndStart_sessionUsesTargetSeasonAndSessionOwner() throws Exception {
    LadderConfig sessionConfig = new LadderConfig();
    sessionConfig.setId(77L);
    sessionConfig.setType(LadderConfig.Type.SESSION);
    sessionConfig.setTargetSeasonId(501L);

    LadderSeason targetSeason = new LadderSeason();
    LadderConfig competitionConfig = new LadderConfig();
    competitionConfig.setId(900L);
    competitionConfig.setType(LadderConfig.Type.COMPETITION);
    targetSeason.setLadderConfig(competitionConfig);
    targetSeason.setState(LadderSeason.State.ACTIVE);
    targetSeason.setStartDate(TODAY_UTC.minusDays(1));
    targetSeason.setEndDate(TODAY_UTC.plusDays(30));
    setId(targetSeason, 501L);

    when(ladderConfigRepo.findById(77L)).thenReturn(Optional.of(sessionConfig));
    when(seasonRepo.findByIdWithLadderConfig(501L)).thenReturn(Optional.of(targetSeason));
    when(rrRepo.findBySessionConfig(sessionConfig)).thenReturn(List.of());
    when(rrRepo.save(any(RoundRobin.class)))
        .thenAnswer(
            invocation -> {
              RoundRobin saved = invocation.getArgument(0);
              setId(saved, 1001L);
              return saved;
            });

    RoundRobinScheduler.GenerationResult generation = new RoundRobinScheduler.GenerationResult();
    generation.schedule = List.of();
    when(roundRobinScheduler.generateWithExplanation(anyList(), anyMap(), eq(0)))
        .thenReturn(generation);

    RoundRobin created = service.createAndStart(77L, null, List.of(11L, 12L, 13L, 14L), 0, null);

    assertThat(created.getSeason()).isEqualTo(targetSeason);
    assertThat(created.getSessionConfig()).isEqualTo(sessionConfig);
    verify(rrRepo, atLeastOnce()).findBySessionConfig(sessionConfig);
  }

  @Test
  void createAndStart_fixedTeamsSchedulesTeamsAgainstEachOtherOnce() throws Exception {
    LadderSeason season = new LadderSeason();
    LadderConfig config = new LadderConfig();
    config.setId(77L);
    season.setLadderConfig(config);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(TODAY_UTC.minusDays(1));
    season.setEndDate(TODAY_UTC.plusDays(30));

    when(seasonRepo.findActive(77L)).thenReturn(Optional.of(season));
    when(rrRepo.findBySeasonAndSessionConfigIsNull(season)).thenReturn(List.of());
    when(rrRepo.save(any(RoundRobin.class)))
        .thenAnswer(
            invocation -> {
              RoundRobin saved = invocation.getArgument(0);
              setId(saved, 1002L);
              return saved;
            });
    when(rrEntryRepo.save(any(RoundRobinEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepo.findById(anyLong()))
        .thenAnswer(invocation -> Optional.of(userWithId(invocation.getArgument(0))));

    List<List<Long>> fixedTeams = List.of(List.of(11L, 12L), List.of(13L, 14L), List.of(15L, 16L));

    RoundRobin created =
        service.createAndStart(
            77L,
            null,
            List.of(11L, 12L, 13L, 14L, 15L, 16L),
            99,
            null,
            RoundRobin.Format.FIXED_TEAMS,
            fixedTeams);

    assertThat(created.getFormat()).isEqualTo(RoundRobin.Format.FIXED_TEAMS);
    verify(roundRobinScheduler, never()).generateWithExplanation(anyList(), anyMap(), anyInt());

    ArgumentCaptor<RoundRobinEntry> entryCaptor = ArgumentCaptor.forClass(RoundRobinEntry.class);
    verify(rrEntryRepo, times(6)).save(entryCaptor.capture());
    assertThat(entryCaptor.getAllValues())
        .extracting(RoundRobinEntry::getRoundNumber)
        .containsExactly(1, 1, 2, 2, 3, 3);
    assertThat(entryCaptor.getAllValues().stream().filter(RoundRobinEntry::isBye).count())
        .isEqualTo(3L);
    assertThat(
            entryCaptor.getAllValues().stream()
                .filter(entry -> !entry.isBye())
                .flatMap(
                    entry ->
                        java.util.stream.Stream.of(
                            teamKey(entry.getA1(), entry.getA2()),
                            teamKey(entry.getB1(), entry.getB2())))
                .distinct())
        .containsExactlyInAnyOrder("11-12", "13-14", "15-16");
  }

  @Test
  void createAndStart_fixedTeamsRejectsOddParticipantCount() {
    LadderSeason season = new LadderSeason();
    LadderConfig config = new LadderConfig();
    config.setId(77L);
    season.setLadderConfig(config);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(TODAY_UTC.minusDays(1));
    season.setEndDate(TODAY_UTC.plusDays(30));

    when(seasonRepo.findActive(77L)).thenReturn(Optional.of(season));

    assertThatThrownBy(
            () ->
                service.createAndStart(
                    77L,
                    null,
                    List.of(11L, 12L, 13L, 14L, 15L),
                    0,
                    null,
                    RoundRobin.Format.FIXED_TEAMS,
                    List.of(List.of(11L, 12L), List.of(13L, 14L), List.of(15L))))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("even number of players");

    verify(rrRepo, never()).save(any(RoundRobin.class));
  }

  @Test
  void listForLadderSeason_sessionUsesSessionOwnedRoundRobins() {
    LadderConfig sessionConfig = new LadderConfig();
    sessionConfig.setId(77L);
    sessionConfig.setType(LadderConfig.Type.SESSION);

    RoundRobin rr = new RoundRobin();
    when(ladderConfigRepo.findById(77L)).thenReturn(Optional.of(sessionConfig));
    when(rrRepo.findBySessionConfig(eq(sessionConfig), any()))
        .thenReturn(new PageImpl<>(List.of(rr), PageRequest.of(0, 10), 1));

    var page = service.listForLadderSeason(77L, 999L, 0, 10);

    assertThat(page.getContent()).containsExactly(rr);
    verify(rrRepo).findBySessionConfig(eq(sessionConfig), any());
    verify(rrRepo, never()).findBySeasonAndSessionConfigIsNull(any(), any());
  }

  @Test
  void findLoggedMatchForEntry_sessionUsesSourceSessionScope() throws Exception {
    LadderConfig sessionConfig = new LadderConfig();
    sessionConfig.setId(77L);
    sessionConfig.setType(LadderConfig.Type.SESSION);

    LadderSeason targetSeason = new LadderSeason();
    setId(targetSeason, 501L);

    RoundRobin rr = new RoundRobin();
    rr.setSessionConfig(sessionConfig);
    rr.setSeason(targetSeason);

    RoundRobinEntry entry = new RoundRobinEntry();
    entry.setRoundRobin(rr);
    User a1 = userWithId(11L);
    User b1 = userWithId(12L);
    entry.setA1(a1);
    entry.setB1(b1);

    Match linked = confirmedMatch(900L, a1, b1);
    when(matchRepo.findRecentPlayedMatchesForPlayersInSession(
            eq(77L), anyCollection(), any(), any()))
        .thenReturn(List.of(linked));

    Optional<Match> found =
        service.findLoggedMatchForEntry(rr, entry, java.time.Instant.parse("2026-03-16T10:00:00Z"));

    assertThat(found).contains(linked);
    verify(matchRepo)
        .findRecentPlayedMatchesForPlayersInSession(eq(77L), anyCollection(), any(), any());
    verify(matchRepo, never())
        .findRecentPlayedMatchesForPlayersInSeason(any(), anyCollection(), any(), any());
    verify(matchRepo, never()).findRecentPlayedMatchesForPlayers(anyCollection(), any(), any());
  }

  @Test
  void computeStandingsForSession_rollsUpConfirmedStampedMatches() throws Exception {
    LadderConfig sessionConfig = new LadderConfig();
    setId(sessionConfig, 555L);
    sessionConfig.setType(LadderConfig.Type.SESSION);

    User alpha = userWithId(11L);
    alpha.setNickName("Alpha");
    User beta = userWithId(12L);
    beta.setNickName("Beta");
    User gamma = userWithId(13L);
    gamma.setNickName("Gamma");
    User delta = userWithId(14L);
    delta.setNickName("Delta");

    when(courtNameService.gatherCourtNamesForUser(11L, 555L))
        .thenReturn(java.util.Set.of("Court11"));
    when(matchRepo.findConfirmedBySourceSessionConfigIdOrderByPlayedAtDescWithUsers(555L))
        .thenReturn(
            List.of(
                sessionMatch(9001L, sessionConfig, alpha, beta, gamma, null, 11, 7),
                sessionMatch(9002L, sessionConfig, alpha, null, delta, null, 11, 9)));

    List<RoundRobinStanding> standings = service.computeStandingsForSession(sessionConfig);

    assertThat(standings).hasSize(4);
    assertThat(standings)
        .extracting(RoundRobinStanding::getUserId)
        .containsExactly(11L, 12L, 14L, 13L);

    RoundRobinStanding alphaStanding = standings.get(0);
    assertThat(alphaStanding.getNickName()).isEqualTo("Court11-Alpha");
    assertThat(alphaStanding.getWins()).isEqualTo(2);
    assertThat(alphaStanding.getLosses()).isZero();
    assertThat(alphaStanding.getPointsFor()).isEqualTo(22);

    RoundRobinStanding betaStanding = standings.get(1);
    assertThat(betaStanding.getWins()).isEqualTo(1);
    assertThat(betaStanding.getLosses()).isZero();
    assertThat(betaStanding.getPointsFor()).isEqualTo(11);
  }

  @Test
  void computeStandingsForSession_returnsEmptyForNonSessionConfig() {
    LadderConfig standardConfig = new LadderConfig();
    standardConfig.setId(777L);
    standardConfig.setType(LadderConfig.Type.STANDARD);

    assertThat(service.computeStandingsForSession(standardConfig)).isEmpty();
    verify(matchRepo, never())
        .findConfirmedBySourceSessionConfigIdOrderByPlayedAtDescWithUsers(anyLong());
  }

  @Test
  void rebalanceFutureRounds_rejectsFixedTeamRoundRobin() throws Exception {
    RoundRobin rr = roundRobinWithSeason(88L, 77L);
    rr.setFormat(RoundRobin.Format.FIXED_TEAMS);
    when(rrRepo.findById(88L)).thenReturn(Optional.of(rr));

    assertThatThrownBy(() -> service.rebalanceFutureRounds(88L))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("Fixed-team round-robins");
  }

  @Test
  void endOpenRoundRobinsForSeason_endsOnlyOpenRoundRobins() throws Exception {
    LadderSeason season = new LadderSeason();
    LadderConfig config = new LadderConfig();
    config.setId(77L);
    season.setLadderConfig(config);

    RoundRobin open = new RoundRobin();
    setId(open, 2001L);
    open.setSeason(season);
    open.setCurrentRound(1);

    RoundRobin closed = new RoundRobin();
    setId(closed, 2002L);
    closed.setSeason(season);
    closed.setCurrentRound(3);

    when(rrRepo.findBySeason(season)).thenReturn(List.of(open, closed));
    when(rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(open))
        .thenReturn(List.of(entryForRound(open, 3001L, 1), entryForRound(open, 3002L, 2)));
    when(rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(closed))
        .thenReturn(List.of(entryForRound(closed, 3003L, 1), entryForRound(closed, 3004L, 2)));
    when(rrRepo.save(any(RoundRobin.class))).thenAnswer(invocation -> invocation.getArgument(0));

    int ended = service.endOpenRoundRobinsForSeason(season);

    assertThat(ended).isEqualTo(1);
    assertThat(open.getCurrentRound()).isEqualTo(3);
    assertThat(closed.getCurrentRound()).isEqualTo(3);
    verify(rrRepo).save(open);
    verify(rrRepo, never()).save(closed);
  }

  @Test
  void endOpenRoundRobinsForSession_endsOnlyOpenRoundRobins() throws Exception {
    LadderConfig sessionConfig = new LadderConfig();
    sessionConfig.setId(77L);
    sessionConfig.setType(LadderConfig.Type.SESSION);

    RoundRobin open = new RoundRobin();
    setId(open, 2101L);
    open.setSessionConfig(sessionConfig);
    open.setCurrentRound(1);

    RoundRobin closed = new RoundRobin();
    setId(closed, 2102L);
    closed.setSessionConfig(sessionConfig);
    closed.setCurrentRound(3);

    when(rrRepo.findBySessionConfig(sessionConfig)).thenReturn(List.of(open, closed));
    when(rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(open))
        .thenReturn(List.of(entryForRound(open, 3101L, 1), entryForRound(open, 3102L, 2)));
    when(rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(closed))
        .thenReturn(List.of(entryForRound(closed, 3103L, 1), entryForRound(closed, 3104L, 2)));
    when(rrRepo.save(any(RoundRobin.class))).thenAnswer(invocation -> invocation.getArgument(0));

    int ended = service.endOpenRoundRobinsForSession(sessionConfig);

    assertThat(ended).isEqualTo(1);
    assertThat(open.getCurrentRound()).isEqualTo(3);
    assertThat(closed.getCurrentRound()).isEqualTo(3);
    verify(rrRepo).save(open);
    verify(rrRepo, never()).save(closed);
  }

  @Test
  void updateEntryParticipants_reassignsPlayersAndClearsBye() throws Exception {
    RoundRobin rr = roundRobinWithSeason(1L, 77L);
    RoundRobinEntry entry = entryForRound(rr, 5L, 1);

    when(rrRepo.findById(1L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findById(5L)).thenReturn(Optional.of(entry));
    when(rrEntryRepo.findByRoundRobinAndRoundNumber(rr, 1)).thenReturn(java.util.List.of(entry));
    when(rrEntryRepo.save(any(RoundRobinEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    User a1 = userWithId(101L);
    User b1 = userWithId(201L);
    when(userRepo.findById(101L)).thenReturn(Optional.of(a1));
    when(userRepo.findById(201L)).thenReturn(Optional.of(b1));
    when(membershipRepo.findByLadderConfigIdAndUserId(77L, 101L))
        .thenReturn(Optional.of(activeMembership()));
    when(membershipRepo.findByLadderConfigIdAndUserId(77L, 201L))
        .thenReturn(Optional.of(activeMembership()));

    RoundRobinEntry result = service.updateEntryParticipants(1L, 5L, 101L, null, 201L, null);

    assertThat(result.getA1()).isEqualTo(a1);
    assertThat(result.getA2()).isNull();
    assertThat(result.getB1()).isEqualTo(b1);
    assertThat(result.getB2()).isNull();
    assertThat(result.isBye()).isFalse();
    assertThat(result.getMatchId()).isNull();
  }

  @Test
  void updateEntryParticipants_rejectsDuplicatePlayers() throws Exception {
    RoundRobin rr = roundRobinWithSeason(2L, 88L);
    RoundRobinEntry entry = entryForRound(rr, 6L, 1);

    when(rrRepo.findById(2L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findById(6L)).thenReturn(Optional.of(entry));
    assertThatThrownBy(() -> service.updateEntryParticipants(2L, 6L, 301L, null, 301L, null))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("player has been selected more than once");
  }

  @Test
  void updateEntryParticipants_retainsByePlayerAfterSubstitution() throws Exception {
    RoundRobin rr = roundRobinWithSeason(6L, 77L);
    RoundRobinEntry target = entryForRound(rr, 12L, 1);
    RoundRobinEntry byeEntry = entryForRound(rr, 13L, 1);
    byeEntry.setBye(true);
    User byePlayer = userWithId(801L);
    byeEntry.setA1(byePlayer);

    User opponent = userWithId(802L);

    when(rrRepo.findById(6L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findById(12L)).thenReturn(Optional.of(target));
    when(rrEntryRepo.findByRoundRobinAndRoundNumber(rr, 1))
        .thenReturn(java.util.List.of(target, byeEntry));
    when(rrEntryRepo.save(any(RoundRobinEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    when(userRepo.findById(801L)).thenReturn(Optional.of(byePlayer));
    when(userRepo.findById(802L)).thenReturn(Optional.of(opponent));
    when(membershipRepo.findByLadderConfigIdAndUserId(77L, 801L))
        .thenReturn(Optional.of(activeMembership()));
    when(membershipRepo.findByLadderConfigIdAndUserId(77L, 802L))
        .thenReturn(Optional.of(activeMembership()));

    service.updateEntryParticipants(6L, 12L, 801L, null, 802L, null);

    assertThat(byeEntry.getA1()).isEqualTo(byePlayer);
    verify(rrEntryRepo, never()).save(same(byeEntry));
  }

  @Test
  void updateEntryParticipants_rejectsWhenPlayerAlreadyInMatch() throws Exception {
    RoundRobin rr = roundRobinWithSeason(7L, 77L);
    RoundRobinEntry target = entryForRound(rr, 14L, 1);
    RoundRobinEntry otherMatch = entryForRound(rr, 15L, 1);
    User busyPlayer = userWithId(901L);
    otherMatch.setA1(busyPlayer);

    when(rrRepo.findById(7L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findById(14L)).thenReturn(Optional.of(target));
    when(rrEntryRepo.findByRoundRobinAndRoundNumber(rr, 1))
        .thenReturn(java.util.List.of(target, otherMatch));
    when(userRepo.findById(901L)).thenReturn(Optional.of(busyPlayer));
    when(userRepo.findById(902L)).thenReturn(Optional.of(userWithId(902L)));
    when(membershipRepo.findByLadderConfigIdAndUserId(77L, 901L))
        .thenReturn(Optional.of(activeMembership()));
    when(membershipRepo.findByLadderConfigIdAndUserId(77L, 902L))
        .thenReturn(Optional.of(activeMembership()));

    assertThatThrownBy(() -> service.updateEntryParticipants(7L, 14L, 901L, null, 902L, null))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("already in another matchup");
  }

  @Test
  void markEntryAsBye_setsByeAndPreservesPlayers() throws Exception {
    RoundRobin rr = roundRobinWithSeason(3L, 99L);
    RoundRobinEntry entry = entryForRound(rr, 7L, 2);
    entry.setA1(userWithId(401L));
    entry.setB1(userWithId(402L));

    when(rrRepo.findById(3L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findById(7L)).thenReturn(Optional.of(entry));
    when(rrEntryRepo.save(entry)).thenReturn(entry);

    RoundRobinEntry result = service.markEntryAsBye(3L, 7L);

    assertThat(result.isBye()).isTrue();
    assertThat(result.getMatchId()).isNull();
    assertThat(result.getA1()).isNotNull();
    assertThat(result.getB1()).isNotNull();
  }

  @Test
  void recordForfeit_createsConfirmedMatchWithWinner() throws Exception {
    RoundRobin rr = roundRobinWithSeason(4L, 55L);
    RoundRobinEntry entry = entryForRound(rr, 8L, 1);
    User a1 = userWithId(501L);
    User b1 = userWithId(601L);
    entry.setA1(a1);
    entry.setB1(b1);

    when(rrRepo.findById(4L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findById(8L)).thenReturn(Optional.of(entry));
    when(rrEntryRepo.save(entry)).thenReturn(entry);
    when(userRepo.findById(999L)).thenReturn(Optional.empty());
    when(matchRepo.save(any(Match.class)))
        .thenAnswer(
            invocation -> {
              Match match = invocation.getArgument(0);
              setId(match, 910L);
              return match;
            });

    RoundRobinEntry result =
        service.recordForfeit(4L, 8L, RoundRobinService.ForfeitWinner.TEAM_A, 15, 999L);

    assertThat(result.getMatchId()).isEqualTo(910L);
    assertThat(result.isBye()).isFalse();

    ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
    verify(matchRepo).save(captor.capture());
    Match saved = captor.getValue();
    assertThat(saved.getSeason()).isEqualTo(rr.getSeason());
    assertThat(saved.getScoreA()).isEqualTo(15);
    assertThat(saved.getScoreB()).isZero();
    assertThat(saved.getState()).isEqualTo(MatchState.CONFIRMED);
    assertThat(saved.isScoreEstimated()).isTrue();
  }

  @Test
  void recordForfeit_sessionOwnedStampsSourceSessionConfig() throws Exception {
    LadderConfig sessionConfig = new LadderConfig();
    sessionConfig.setId(88L);
    sessionConfig.setType(LadderConfig.Type.SESSION);

    RoundRobin rr = roundRobinWithSeason(15L, 55L);
    rr.setSessionConfig(sessionConfig);
    RoundRobinEntry entry = entryForRound(rr, 18L, 1);
    entry.setA1(userWithId(501L));
    entry.setB1(userWithId(601L));

    when(rrRepo.findById(15L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findById(18L)).thenReturn(Optional.of(entry));
    when(rrEntryRepo.save(entry)).thenReturn(entry);
    when(matchRepo.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.recordForfeit(15L, 18L, RoundRobinService.ForfeitWinner.TEAM_A, 11, null);

    ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
    verify(matchRepo).save(captor.capture());
    assertThat(captor.getValue().getSourceSessionConfig()).isEqualTo(sessionConfig);
  }

  @Test
  void createAdditionalEntry_persistsNewMatchup() throws Exception {
    RoundRobin rr = roundRobinWithSeason(5L, 44L);
    when(rrRepo.findById(5L)).thenReturn(Optional.of(rr));

    User a1 = userWithId(701L);
    User b1 = userWithId(702L);
    when(userRepo.findById(701L)).thenReturn(Optional.of(a1));
    when(userRepo.findById(702L)).thenReturn(Optional.of(b1));
    when(membershipRepo.findByLadderConfigIdAndUserId(44L, 701L))
        .thenReturn(Optional.of(activeMembership()));
    when(membershipRepo.findByLadderConfigIdAndUserId(44L, 702L))
        .thenReturn(Optional.of(activeMembership()));

    when(rrEntryRepo.findByRoundRobinAndRoundNumber(rr, 3)).thenReturn(java.util.List.of());
    when(rrEntryRepo.save(any(RoundRobinEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    RoundRobinEntry entry = service.createAdditionalEntry(5L, 3, 701L, null, 702L, null);

    assertThat(entry.getRoundRobin()).isEqualTo(rr);
    assertThat(entry.getRoundNumber()).isEqualTo(3);
    assertThat(entry.getA1()).isEqualTo(a1);
    assertThat(entry.getB1()).isEqualTo(b1);
    assertThat(entry.isBye()).isFalse();
  }

  @Test
  void createAdditionalEntry_keepsByePlayersVisible() throws Exception {
    RoundRobin rr = roundRobinWithSeason(8L, 55L);
    RoundRobinEntry byeEntry = entryForRound(rr, 16L, 2);
    byeEntry.setBye(true);
    User byePlayer = userWithId(1001L);
    byeEntry.setA1(byePlayer);

    User b1 = userWithId(1002L);

    when(rrRepo.findById(8L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findByRoundRobinAndRoundNumber(rr, 2)).thenReturn(java.util.List.of(byeEntry));
    when(rrEntryRepo.save(any(RoundRobinEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepo.findById(1001L)).thenReturn(Optional.of(byePlayer));
    when(userRepo.findById(1002L)).thenReturn(Optional.of(b1));
    when(membershipRepo.findByLadderConfigIdAndUserId(55L, 1001L))
        .thenReturn(Optional.of(activeMembership()));
    when(membershipRepo.findByLadderConfigIdAndUserId(55L, 1002L))
        .thenReturn(Optional.of(activeMembership()));

    RoundRobinEntry newEntry = service.createAdditionalEntry(8L, 2, 1001L, null, 1002L, null);

    assertThat(byeEntry.getA1()).isEqualTo(byePlayer);
    assertThat(newEntry.getA1()).isEqualTo(byePlayer);
    verify(rrEntryRepo, never()).save(same(byeEntry));
  }

  @Test
  void createAdditionalEntry_rejectsPlayerAlreadyScheduled() throws Exception {
    RoundRobin rr = roundRobinWithSeason(9L, 60L);
    RoundRobinEntry otherMatch = entryForRound(rr, 17L, 4);
    User busyPlayer = userWithId(1101L);
    otherMatch.setA1(busyPlayer);

    when(rrRepo.findById(9L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findByRoundRobinAndRoundNumber(rr, 4))
        .thenReturn(java.util.List.of(otherMatch));
    when(userRepo.findById(1101L)).thenReturn(Optional.of(busyPlayer));
    when(userRepo.findById(1102L)).thenReturn(Optional.of(userWithId(1102L)));
    when(membershipRepo.findByLadderConfigIdAndUserId(60L, 1101L))
        .thenReturn(Optional.of(activeMembership()));
    when(membershipRepo.findByLadderConfigIdAndUserId(60L, 1102L))
        .thenReturn(Optional.of(activeMembership()));

    assertThatThrownBy(() -> service.createAdditionalEntry(9L, 4, 1101L, null, 1102L, null))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("already in another matchup");
  }

  @Test
  void regenerateRoundWithForcedByes_rebalancesMatchesAndByes() throws Exception {
    RoundRobin rr = roundRobinWithSeason(10L, 70L);
    RoundRobinEntry match1 = entryForRound(rr, 21L, 1);
    RoundRobinEntry match2 = entryForRound(rr, 22L, 1);
    RoundRobinEntry byeEntry = entryForRound(rr, 23L, 1);
    byeEntry.setBye(true);

    match1.setA1(userWithId(101L));
    match1.setA2(userWithId(102L));
    match1.setB1(userWithId(103L));
    match1.setB2(userWithId(104L));

    match2.setA1(userWithId(105L));
    match2.setA2(userWithId(106L));
    match2.setB1(userWithId(107L));
    match2.setB2(userWithId(108L));

    java.util.List<RoundRobinEntry> entries =
        new java.util.ArrayList<>(List.of(match1, match2, byeEntry));

    when(rrRepo.findById(10L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findByRoundRobinAndRoundNumber(rr, 1)).thenReturn(entries);
    when(rrEntryRepo.save(any(RoundRobinEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepo.findAllById(any()))
        .thenAnswer(
            invocation -> {
              Iterable<Long> ids = invocation.getArgument(0);
              java.util.List<User> result = new java.util.ArrayList<>();
              for (Long id : ids) {
                User u = new User();
                u.setId(id);
                result.add(u);
              }
              return result;
            });

    java.util.List<User> byePlayers =
        service.regenerateRoundWithForcedByes(10L, 1, java.util.List.of(101L, 108L));

    assertThat(match1.isBye()).isFalse();
    assertThat(match1.getA1().getId()).isEqualTo(102L);
    assertThat(match1.getA2().getId()).isEqualTo(103L);
    assertThat(match1.getB1().getId()).isEqualTo(104L);
    assertThat(match1.getB2().getId()).isEqualTo(105L);

    assertThat(match2.isBye()).isTrue();
    assertThat(match2.getA1().getId()).isEqualTo(101L);
    assertThat(match2.getA2().getId()).isEqualTo(106L);

    assertThat(byeEntry.isBye()).isTrue();
    assertThat(byeEntry.getA1().getId()).isEqualTo(107L);
    assertThat(byeEntry.getA2().getId()).isEqualTo(108L);

    assertThat(byePlayers.stream().map(User::getId)).containsExactly(101L, 106L, 107L, 108L);
  }

  @Test
  void regenerateRoundWithForcedByes_rejectsWhenMatchLogged() throws Exception {
    RoundRobin rr = roundRobinWithSeason(11L, 80L);
    RoundRobinEntry entry = entryForRound(rr, 30L, 1);
    entry.setMatchId(999L);

    when(rrRepo.findById(11L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findByRoundRobinAndRoundNumber(rr, 1)).thenReturn(java.util.List.of(entry));

    assertThatThrownBy(() -> service.regenerateRoundWithForcedByes(11L, 1, List.of()))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("logged matches");
  }

  @Test
  void analyzeDeviations_summarizesParticipationImbalanceAcrossCompletedAndFutureRounds()
      throws Exception {
    RoundRobin rr = roundRobinWithSeason(12L, 90L);
    RoundRobinEntry match1 = entryForRound(rr, 40L, 1);
    match1.setA1(userWithId(301L));
    match1.setB1(userWithId(302L));
    match1.setMatchId(1001L);
    RoundRobinEntry match2 = entryForRound(rr, 41L, 1);
    match2.setA1(userWithId(301L));
    match2.setB1(userWithId(303L));
    match2.setMatchId(1002L);
    RoundRobinEntry future = entryForRound(rr, 42L, 2);
    future.setA1(userWithId(304L));
    future.setB1(userWithId(305L));

    when(rrRepo.findById(12L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr))
        .thenReturn(List.of(match1, match2, future));
    when(userRepo.findAllById(any()))
        .thenAnswer(
            invocation -> {
              Iterable<Long> ids = invocation.getArgument(0);
              java.util.List<User> users = new java.util.ArrayList<>();
              for (Long id : ids) {
                users.add(userWithId(id));
              }
              return users;
            });

    RoundRobinDeviationSummary summary = service.analyzeDeviations(12L);

    assertThat(summary.getParticipantCount()).isEqualTo(5);
    assertThat(summary.getCompletedRounds()).isEqualTo(1);
    assertThat(summary.getFutureRounds()).isEqualTo(1);
    assertThat(summary.hasImbalance()).isTrue();
    assertThat(summary.getMatchesByPlayer())
        .containsEntry("Player301", 2)
        .containsEntry("Player302", 1)
        .containsEntry("Player303", 1)
        .containsEntry("Player304", 0)
        .containsEntry("Player305", 0);
    assertThat(summary.getOverservedPlayers()).contains("Player301");
    assertThat(summary.getUnderservedPlayers()).contains("Player304", "Player305");
    assertThat(summary.getSuggestedFix()).contains("Rebalance Future Rounds");
  }

  @Test
  void analyzeDeviations_returnsCleanSummaryWhenCompletedParticipationIsBalanced()
      throws Exception {
    RoundRobin rr = roundRobinWithSeason(13L, 95L);
    RoundRobinEntry match1 = entryForRound(rr, 50L, 1);
    match1.setA1(userWithId(401L));
    match1.setB1(userWithId(402L));
    match1.setMatchId(2001L);
    RoundRobinEntry match2 = entryForRound(rr, 51L, 1);
    match2.setA1(userWithId(403L));
    match2.setB1(userWithId(404L));
    match2.setMatchId(2002L);

    when(rrRepo.findById(13L)).thenReturn(Optional.of(rr));
    when(rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr)).thenReturn(List.of(match1, match2));
    when(userRepo.findAllById(any()))
        .thenAnswer(
            invocation -> {
              Iterable<Long> ids = invocation.getArgument(0);
              java.util.List<User> users = new java.util.ArrayList<>();
              for (Long id : ids) {
                users.add(userWithId(id));
              }
              return users;
            });

    RoundRobinDeviationSummary summary = service.analyzeDeviations(13L);

    assertThat(summary.getParticipantCount()).isEqualTo(4);
    assertThat(summary.getMatchesByPlayer())
        .containsEntry("Player401", 1)
        .containsEntry("Player402", 1)
        .containsEntry("Player403", 1)
        .containsEntry("Player404", 1);
    assertThat(summary.hasImbalance()).isFalse();
    assertThat(summary.getUnderservedPlayers()).isEmpty();
    assertThat(summary.getOverservedPlayers()).isEmpty();
    assertThat(summary.getSuggestedFix()).isNull();
  }

  @Test
  void resolveCurrentRound_advancesToEarliestIncompleteRound() throws Exception {
    RoundRobin rr = roundRobinWithSeason(14L, 96L);
    rr.setCurrentRound(1);

    RoundRobinEntry round1 = entryForRound(rr, 60L, 1);
    round1.setA1(userWithId(501L));
    round1.setB1(userWithId(502L));
    round1.setMatchId(1001L);

    RoundRobinEntry round2 = entryForRound(rr, 61L, 2);
    round2.setA1(userWithId(503L));
    round2.setB1(userWithId(504L));
    round2.setMatchId(1002L);

    RoundRobinEntry round3 = entryForRound(rr, 62L, 3);
    round3.setA1(userWithId(505L));
    round3.setB1(userWithId(506L));

    when(rrEntryRepo.findByRoundRobinOrderByRoundNumberAscWithUsers(rr))
        .thenReturn(List.of(round1, round2, round3));
    when(matchRepo.findAllByIdInWithUsers(any()))
        .thenReturn(
            List.of(
                confirmedMatch(1001L, round1.getA1(), round1.getB1()),
                confirmedMatch(1002L, round2.getA1(), round2.getB1())));
    when(rrRepo.save(any(RoundRobin.class))).thenAnswer(invocation -> invocation.getArgument(0));

    int currentRound = service.resolveCurrentRound(rr);

    assertThat(currentRound).isEqualTo(3);
    assertThat(rr.getCurrentRound()).isEqualTo(3);
    verify(rrRepo).save(rr);
  }

  private RoundRobin roundRobinWithSeason(Long rrId, Long ladderId) throws Exception {
    RoundRobin rr = new RoundRobin();
    setId(rr, rrId);

    LadderSeason season = new LadderSeason();
    setId(season, ladderId * 10); // unique but arbitrary
    LadderConfig config = new LadderConfig();
    config.setId(ladderId);
    season.setLadderConfig(config);
    rr.setSeason(season);
    return rr;
  }

  private RoundRobinEntry entryForRound(RoundRobin rr, Long entryId, int round) throws Exception {
    RoundRobinEntry entry = new RoundRobinEntry();
    setId(entry, entryId);
    entry.setRoundRobin(rr);
    entry.setRoundNumber(round);
    entry.setBye(false);
    entry.setMatchId(null);
    return entry;
  }

  private User userWithId(Long id) throws Exception {
    User u = new User();
    setId(u, id);
    u.setNickName("Player" + id);
    return u;
  }

  private Match confirmedMatch(Long id, User a1, User b1) throws Exception {
    Match match = new Match();
    setId(match, id);
    match.setA1(a1);
    match.setB1(b1);
    match.setScoreA(11);
    match.setScoreB(7);
    match.setState(MatchState.CONFIRMED);
    return match;
  }

  private Match sessionMatch(
      Long id,
      LadderConfig sourceSession,
      User a1,
      User a2,
      User b1,
      User b2,
      int scoreA,
      int scoreB)
      throws Exception {
    Match match = new Match();
    setId(match, id);
    match.setSourceSessionConfig(sourceSession);
    match.setA1(a1);
    match.setA2(a2);
    match.setB1(b1);
    match.setB2(b2);
    match.setA1Guest(a1 == null);
    match.setA2Guest(a2 == null);
    match.setB1Guest(b1 == null);
    match.setB2Guest(b2 == null);
    match.setScoreA(scoreA);
    match.setScoreB(scoreB);
    match.setState(MatchState.CONFIRMED);
    return match;
  }

  private LadderMembership activeMembership() {
    LadderMembership membership = new LadderMembership();
    membership.setState(LadderMembership.State.ACTIVE);
    return membership;
  }

  private String teamKey(User first, User second) {
    java.util.List<Long> ids = new java.util.ArrayList<>();
    if (first != null && first.getId() != null) {
      ids.add(first.getId());
    }
    if (second != null && second.getId() != null) {
      ids.add(second.getId());
    }
    java.util.Collections.sort(ids);
    return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("-"));
  }

  private void setId(Object target, Long id) throws Exception {
    Field field = target.getClass().getDeclaredField("id");
    field.setAccessible(true);
    field.set(target, id);
  }
}
