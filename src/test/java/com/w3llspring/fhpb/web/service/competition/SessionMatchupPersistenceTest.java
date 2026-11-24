package com.w3llspring.fhpb.web.service.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
import com.w3llspring.fhpb.web.service.SeasonStandingsRecalcQueue;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinModificationException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SessionMatchupPersistenceTest {
  // Rating updates change match versions independently of the score-persistence checks below.
  @MockitoBean private SeasonStandingsRecalcQueue standingsRecalcQueue;

  @Autowired private SessionMatchupService service;
  @Autowired private UserRepository users;
  @Autowired private LadderConfigRepository configs;
  @Autowired private LadderMembershipRepository memberships;
  @Autowired private LadderSeasonRepository seasons;
  @Autowired private RoundRobinRepository schedules;
  @Autowired private RoundRobinEntryRepository entries;
  @Autowired private MatchRepository matches;
  @Autowired private MatchConfirmationRepository confirmations;
  @Autowired private com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService roundRobins;
  @Autowired private LadderStandingRepository standingRepo;
  @Autowired private com.w3llspring.fhpb.web.service.MatchLoggingQuotaService quota;
  private User host;
  private LadderConfig session;
  private LadderSeason season;
  private RoundRobin rr;
  private RoundRobinEntry first;
  private RoundRobinEntry ahead;

  @BeforeEach
  void setup() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    List<User> players = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      User user = new User();
      user.setEmail("matchups-" + suffix + i + "@test.local");
      user.setNickName("Player" + suffix + i);
      user.setPassword("pw");
      players.add(users.save(user));
    }
    host = players.getFirst();
    LadderConfig league = new LadderConfig();
    league.setTitle("Scores " + suffix);
    league.setOwnerUserId(host.getId());
    league.setSecurityLevel(LadderSecurity.STANDARD);
    league = configs.save(league);
    season = new LadderSeason();
    season.setLadderConfig(league);
    season.setName("Season " + suffix);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now(ZoneOffset.UTC));
    season.setEndDate(LocalDate.now(ZoneOffset.UTC).plusDays(7));
    season = seasons.save(season);
    session = new LadderConfig();
    session.setTitle("Session " + suffix);
    session.setOwnerUserId(host.getId());
    session.setType(LadderConfig.Type.SESSION);
    session.setSessionPlayMode(LadderConfig.SessionPlayMode.MATCHUPS);
    session.setExpiresAt(Instant.now().plusSeconds(3600));
    session.setTargetSeasonId(season.getId());
    session = configs.save(session);
    for (User player : players) {
      LadderMembership membership = new LadderMembership();
      membership.setLadderConfig(session);
      membership.setUserId(player.getId());
      membership.setState(LadderMembership.State.ACTIVE);
      memberships.save(membership);
    }
    rr = new RoundRobin();
    rr.setSeason(season);
    rr.setSessionConfig(session);
    rr.setCreatedBy(host);
    rr.setSessionFlow(true);
    rr = schedules.save(rr);
    first = entry(1, players);
    ahead = entry(3, players);
  }

  @Test
  void organizerScoresAreConfirmedImmediatelyAndCorrectionsKeepTheAssignmentAndConfirmation() {
    session.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    configs.save(session);
    Match saved = service.recordScore(session.getId(), rr.getId(), ahead.getId(), 11, 7, host);
    assertThat(saved.isOrganizerConfirmed()).isTrue();
    assertThat(saved.getState()).isEqualTo(MatchState.CONFIRMED);
    assertThat(confirmations.findByMatch(saved))
        .hasSize(2)
        .allSatisfy(
            c -> {
              assertThat(c.getConfirmedAt()).isNotNull();
              assertThat(c.getMethod()).isEqualTo(MatchConfirmation.ConfirmationMethod.AUTO);
              assertThat(c.isCasualModeAutoConfirmed()).isFalse();
            });
    Match corrected =
        service.correctScore(
            session.getId(), rr.getId(), ahead.getId(), 11, 9, saved.getVersion(), host);
    assertThat(corrected.getScoreB()).isEqualTo(9);
    assertThat(corrected.getState()).isEqualTo(MatchState.CONFIRMED);
    assertThat(corrected.isOrganizerConfirmed()).isTrue();
    assertThat(entries.findById(ahead.getId()).orElseThrow().getMatchId()).isEqualTo(saved.getId());
    assertThat(matches.findById(saved.getId()).orElseThrow().getScoreB()).isEqualTo(9);
    assertThatThrownBy(
            () ->
                service.correctScore(
                    session.getId(), rr.getId(), ahead.getId(), 11, 5, saved.getVersion(), host))
        .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
  }

  @Test
  void aParticipantCannotSubmitAnAutoConfirmedScoreOrCorrectTheOrganizersResult() {
    session.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    configs.save(session);
    User participant = first.getA2();
    assertThatThrownBy(
            () ->
                service.recordScore(session.getId(), rr.getId(), first.getId(), 11, 7, participant))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    Match saved = service.recordScore(session.getId(), rr.getId(), first.getId(), 11, 7, host);
    assertThatThrownBy(
            () ->
                service.correctScore(
                    session.getId(),
                    rr.getId(),
                    first.getId(),
                    11,
                    5,
                    saved.getVersion(),
                    participant))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(
            new org.springframework.beans.BeanWrapperImpl(new Match())
                .isWritableProperty("organizerConfirmed"))
        .isFalse();
  }

  @Test
  void organizerCanRecordAndCorrectAGuestOnlyTeamWithoutAddingItToSeasonStandings() {
    session.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    configs.save(session);
    service.replace(session.getId(), rr.getId(), first.getB1().getId(), null, "Taylor", host);
    service.replace(session.getId(), rr.getId(), first.getB2().getId(), null, "Jordan", host);
    Match saved = service.recordScore(session.getId(), rr.getId(), ahead.getId(), 11, 7, host);
    assertThat(saved.getState()).isEqualTo(MatchState.CONFIRMED);
    assertThat(saved.isExcludeFromStandings()).isTrue();
    Match corrected =
        service.correctScore(
            session.getId(), rr.getId(), ahead.getId(), 11, 9, saved.getVersion(), host);
    assertThat(corrected.getState()).isEqualTo(MatchState.CONFIRMED);
    assertThat(corrected.getB1GuestName()).isEqualTo("Taylor");
    assertThat(corrected.isExcludeFromStandings()).isTrue();
  }

  @Test
  void organizerAssignmentsDoNotConsumeTheOrdinaryWeeklyLoggingQuota() {
    Match manual = service.recordScore(session.getId(), rr.getId(), first.getId(), 11, 7, host);
    assertThat(manual.isOrganizerConfirmed()).isFalse();
    session.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    configs.save(session);
    Match organized = service.recordScore(session.getId(), rr.getId(), ahead.getId(), 11, 8, host);
    assertThat(organized.isOrganizerConfirmed()).isTrue();
    assertThat(quota.evaluate(host).currentCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void concurrentScoreSubmissionsProduceOneMatchAndCanTargetALaterRound(boolean organizerScored)
      throws Exception {
    if (organizerScored) {
      session.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
      configs.save(session);
    }
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Callable<Boolean> submit =
          () -> {
            start.await();
            try {
              service.recordScore(session.getId(), rr.getId(), ahead.getId(), 11, 8, host);
              return true;
            } catch (RoundRobinModificationException ex) {
              assertThat(ex.getMessage()).contains("already been recorded");
              return false;
            }
          };
      Future<Boolean> a = pool.submit(submit), b = pool.submit(submit);
      start.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(matches.findBySeason(season)).hasSize(1);
      assertThat(entries.findById(first.getId()).orElseThrow().getMatchId()).isNull();
      assertThat(entries.findById(ahead.getId()).orElseThrow().getMatchId()).isNotNull();
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void namedGuestReplacementPersistsWithoutCreatingAnAccountOrChangingThePendingGame() {
    long usersBefore = users.count();
    Match recorded = service.recordScore(session.getId(), rr.getId(), first.getId(), 11, 6, host);
    assertThat(recorded.getState()).isEqualTo(MatchState.PROVISIONAL);
    service.replace(session.getId(), rr.getId(), host.getId(), null, "Taylor", host);
    Match guestGame = service.recordScore(session.getId(), rr.getId(), ahead.getId(), 11, 8, host);
    Match historical = matches.findById(recorded.getId()).orElseThrow();
    assertThat(historical.getA1().getId()).isEqualTo(host.getId());
    assertThat(historical.getA1GuestName()).isNull();
    assertThat(guestGame.getA1()).isNull();
    assertThat(guestGame.isA1Guest()).isTrue();
    assertThat(guestGame.getA1GuestName()).isEqualTo("Taylor");
    assertThat(users.count()).isEqualTo(usersBefore);
  }

  @Test
  void performanceRoundsWaitForConfirmationAndKeepReleasedPairingsStable() {
    rr.setFormat(RoundRobin.Format.PERFORMANCE);
    schedules.save(rr);
    entry(2, List.of(first.getA1(), first.getA2(), first.getB1(), first.getB2()));
    assertThat(service.view(session, host, Set.of()).rounds()).hasSize(1);
    assertThatThrownBy(
            () -> service.recordScore(session.getId(), rr.getId(), ahead.getId(), 11, 7, host))
        .isInstanceOf(RoundRobinModificationException.class)
        .hasMessageContaining("Confirm all scores");
    Match pending = service.recordScore(session.getId(), rr.getId(), first.getId(), 11, 7, host);
    assertThat(roundRobins.resolveCurrentRound(rr.getId())).isEqualTo(1);
    pending.setState(MatchState.CONFIRMED);
    matches.save(pending);
    assertThat(roundRobins.resolveCurrentRound(rr.getId())).isEqualTo(2);
    var released = entries.findByRoundRobinAndRoundNumberWithUsers(rr, 2).getFirst();
    Set<Long> originalTeam = Set.of(first.getA1().getId(), first.getA2().getId());
    Set<Long> newTeam = Set.of(released.getA1().getId(), released.getA2().getId());
    assertThat(newTeam).isNotEqualTo(originalTeam);
    assertThat(service.view(session, host, Set.of()).rounds().get(1).games()).hasSize(1);
    assertThat(roundRobins.resolveCurrentRound(rr.getId())).isEqualTo(2);
    var refreshed = entries.findByIdWithUsers(released.getId()).orElseThrow();
    assertThat(Set.of(refreshed.getA1().getId(), refreshed.getA2().getId())).isEqualTo(newTeam);
    assertThat(entries.findById(first.getId()).orElseThrow().getMatchId())
        .isEqualTo(pending.getId());
  }

  @Test
  void performanceCreationSnapshotsSeasonRatingsAndPreservesRequestedRounds() {
    roundRobins.endRoundRobinEarly(rr.getId());
    LadderStanding standing = new LadderStanding();
    standing.setSeason(season);
    standing.setUser(host);
    standing.setDisplayName(host.getNickName());
    standing.setPoints(250);
    standing.setRank(1);
    standingRepo.save(standing);
    List<Long> ids =
        List.of(
            first.getA1().getId(),
            first.getA2().getId(),
            first.getB1().getId(),
            first.getB2().getId());
    RoundRobin created =
        roundRobins.createAndStart(
            session.getId(), null, ids, 6, host.getId(), RoundRobin.Format.PERFORMANCE, List.of());
    assertThat(created.getSeedRatings()).containsEntry(host.getId(), 250).hasSize(4);
    assertThat(roundRobins.getSeasonDisplayRatings(created)).containsEntry(host.getId(), 1250);
    standing.setPoints(300);
    standingRepo.save(standing);
    assertThat(roundRobins.getSeasonDisplayRatings(created)).containsEntry(host.getId(), 1300);
    assertThat(roundRobins.getMaxRound(created.getId())).isEqualTo(6);
    assertThat(roundRobins.getEntriesForRound(created.getId(), 2)).isEmpty();
    assertThat(roundRobins.getEntriesForRound(created.getId(), 1)).hasSize(1);
    assertThat(roundRobins.canAdvance(created.getId())).isFalse();
    assertThat(roundRobins.findActiveSessionAssignment(session, host.getId())).isPresent();
    roundRobins.endRoundRobinEarly(created.getId());
    assertThat(roundRobins.getEntriesForRound(created.getId(), 2)).isEmpty();
    assertThat(service.view(session, host, Set.of()).rounds()).hasSize(1);
  }

  @Test
  void concurrentPerformanceRefreshesReleaseOnlyOneStableRound() throws Exception {
    rr.setFormat(RoundRobin.Format.PERFORMANCE);
    schedules.save(rr);
    entry(2, List.of(first.getA1(), first.getA2(), first.getB1(), first.getB2()));
    Match result = service.recordScore(session.getId(), rr.getId(), first.getId(), 11, 7, host);
    result.setState(MatchState.CONFIRMED);
    matches.save(result);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      CountDownLatch start = new CountDownLatch(1);
      Callable<Integer> refresh =
          () -> {
            start.await();
            return roundRobins.resolveCurrentRound(rr.getId());
          };
      Future<Integer> a = pool.submit(refresh), b = pool.submit(refresh);
      start.countDown();
      assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(2);
      assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(2);
    }
    assertThat(entries.findByRoundRobinAndRoundNumberWithUsers(rr, 2)).hasSize(1);
    assertThat(schedules.findById(rr.getId()).orElseThrow().getPreparedRound()).isEqualTo(2);
    assertThat(entries.findById(first.getId()).orElseThrow().getMatchId())
        .isEqualTo(result.getId());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void performanceUsesCurrentSeasonRatingsWithAStartingSnapshotFallback(
      boolean standingsAvailable) {
    roundRobins.endRoundRobinEarly(rr.getId());
    session.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    configs.save(session);
    List<User> roster =
        new ArrayList<>(List.of(first.getA1(), first.getA2(), first.getB1(), first.getB2()));
    for (int i = 0; i < 4; i++) {
      User player = new User();
      player.setEmail("rated-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
      player.setNickName("Rated" + i);
      player.setPassword("pw");
      player = users.save(player);
      roster.add(player);
      LadderMembership member = new LadderMembership();
      member.setLadderConfig(session);
      member.setUserId(player.getId());
      member.setState(LadderMembership.State.ACTIVE);
      memberships.save(member);
    }
    Map<Long, LadderStanding> rows = new HashMap<>();
    Map<Long, Integer> expected = new HashMap<>();
    for (int i = 0; i < roster.size(); i++) {
      User player = roster.get(i);
      LadderStanding row = new LadderStanding();
      row.setSeason(season);
      row.setUser(player);
      row.setDisplayName(player.getNickName());
      row.setRank(i + 1);
      row.setPoints(i < 4 ? 0 : 100);
      row = standingRepo.save(row);
      rows.put(player.getId(), row);
      expected.put(player.getId(), row.getPoints());
    }
    RoundRobin created =
        roundRobins.createAndStart(
            session.getId(),
            null,
            roster.stream().map(User::getId).toList(),
            7,
            host.getId(),
            RoundRobin.Format.PERFORMANCE,
            List.of());
    created.setSessionFlow(true);
    schedules.save(created);
    var opening = roundRobins.getEntriesForRound(created.getId(), 1);
    for (var game : opening)
      service.recordScore(session.getId(), created.getId(), game.getId(), 11, 7, host);
    if (standingsAvailable) {
      rows.get(roster.get(0).getId()).setPoints(100);
      rows.get(roster.get(4).getId()).setPoints(0);
      standingRepo.saveAll(rows.values());
      rows.forEach((id, row) -> expected.put(id, row.getPoints()));
    } else standingRepo.deleteAll(rows.values());
    var next = roundRobins.getEntriesForRound(created.getId(), 2);
    assertThat(next).hasSize(2);
    for (var game : next)
      assertThat(
              List.of(game.getA1(), game.getA2(), game.getB1(), game.getB2()).stream()
                  .map(u -> expected.get(u.getId()))
                  .distinct()
                  .toList())
          .hasSize(1);
    var released =
        next.stream()
            .map(
                e ->
                    List.of(
                        e.getA1().getId(), e.getA2().getId(), e.getB1().getId(), e.getB2().getId()))
            .toList();
    if (standingsAvailable) {
      rows.values().forEach(row -> row.setPoints(200 - row.getPoints()));
      standingRepo.saveAll(rows.values());
    }
    assertThat(
            roundRobins.getEntriesForRound(created.getId(), 2).stream()
                .map(
                    e ->
                        List.of(
                            e.getA1().getId(),
                            e.getA2().getId(),
                            e.getB1().getId(),
                            e.getB2().getId()))
                .toList())
        .isEqualTo(released);
    assertThat(created.getSeedRatings()).containsEntry(roster.get(0).getId(), 0);
  }

  @Test
  void fivePlayerPerformanceRunRotatesRestsAndCompletesOnlyAfterItsLastRound() {
    roundRobins.endRoundRobinEarly(rr.getId());
    session.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    configs.save(session);
    User fifth = new User();
    fifth.setEmail("fifth-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
    fifth.setNickName("Fifth");
    fifth.setPassword("pw");
    fifth = users.save(fifth);
    LadderMembership member = new LadderMembership();
    member.setLadderConfig(session);
    member.setUserId(fifth.getId());
    member.setState(LadderMembership.State.ACTIVE);
    memberships.save(member);
    List<Long> ids =
        List.of(
            first.getA1().getId(),
            first.getA2().getId(),
            first.getB1().getId(),
            first.getB2().getId(),
            fifth.getId());
    RoundRobin created =
        roundRobins.createAndStart(
            session.getId(), null, ids, 5, host.getId(), RoundRobin.Format.PERFORMANCE, List.of());
    created.setSessionFlow(true);
    schedules.save(created);
    Set<Long> rested = new HashSet<>();
    for (int r = 1; r <= 5; r++) {
      assertThat(roundRobins.resolveCurrentRound(created.getId())).isEqualTo(r);
      var round = roundRobins.getEntriesForRound(created.getId(), r);
      assertThat(round).hasSize(2);
      var bye = round.stream().filter(RoundRobinEntry::isBye).findFirst().orElseThrow();
      assertThat(rested.add(bye.getA1().getId())).isTrue();
      var game = round.stream().filter(e -> !e.isBye()).findFirst().orElseThrow();
      assertThat(
              service
                  .recordScore(session.getId(), created.getId(), game.getId(), 11, 7, host)
                  .getState())
          .isEqualTo(MatchState.CONFIRMED);
    }
    assertThat(rested).containsExactlyInAnyOrderElementsOf(ids);
    assertThat(roundRobins.resolveCurrentRound(created.getId())).isEqualTo(6);
    assertThat(service.view(session, host, Set.of()).complete()).isTrue();
  }

  private RoundRobinEntry entry(int round, List<User> players) {
    RoundRobinEntry e = new RoundRobinEntry();
    e.setRoundRobin(rr);
    e.setRoundNumber(round);
    e.setA1(players.get(0));
    e.setA2(players.get(1));
    e.setB1(players.get(2));
    e.setB2(players.get(3));
    return entries.save(e);
  }
}
