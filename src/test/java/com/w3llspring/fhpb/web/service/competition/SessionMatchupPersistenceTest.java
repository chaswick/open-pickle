package com.w3llspring.fhpb.web.service.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
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

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SessionMatchupPersistenceTest {
  @Autowired private SessionMatchupService service;
  @Autowired private UserRepository users;
  @Autowired private LadderConfigRepository configs;
  @Autowired private LadderMembershipRepository memberships;
  @Autowired private LadderSeasonRepository seasons;
  @Autowired private RoundRobinRepository schedules;
  @Autowired private RoundRobinEntryRepository entries;
  @Autowired private MatchRepository matches;
  @Autowired private MatchConfirmationRepository confirmations;
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
