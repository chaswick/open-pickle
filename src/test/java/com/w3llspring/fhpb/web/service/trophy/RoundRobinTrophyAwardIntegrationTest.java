package com.w3llspring.fhpb.web.service.trophy;

import static org.assertj.core.api.Assertions.*;

import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "fhpb.trophies.round-robin-sweep-enabled=false",
      "spring.datasource.url=jdbc:h2:mem:rrtrophies;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
class RoundRobinTrophyAwardIntegrationTest {
  @Autowired RoundRobinTrophyAwardService service;
  @Autowired TrophyAwardService seasonsService;
  @Autowired RoundRobinRepository runs;
  @Autowired RoundRobinEntryRepository entries;
  @Autowired MatchRepository matches;
  @Autowired TrophyRepository trophies;
  @Autowired RoundRobinTrophyAwardRepository awards;
  @Autowired UserTrophyRepository collections;
  @Autowired UserRepository users;
  @Autowired LadderConfigRepository configs;
  @Autowired LadderSeasonRepository seasons;
  @Autowired PlatformTransactionManager transactions;
  @Autowired RoundRobinTrophyInvalidation changes;
  private List<User> people;
  private LadderConfig session;
  private LadderSeason season;
  private Map<String, Trophy> definitions;
  private int gameNumber;

  private <T> T tx(Supplier<T> work) {
    T result = new TransactionTemplate(transactions).execute(s -> work.get());
    changes.flush();
    return result;
  }

  private void edit(Runnable work) {
    tx(
        () -> {
          work.run();
          return null;
        });
  }

  @BeforeEach
  void setup() {
    tx(
        () -> {
          String suffix = UUID.randomUUID().toString().substring(0, 8);
          people = new ArrayList<>();
          for (int i = 0; i < 10; i++) {
            User user = new User();
            user.setEmail("rr-" + suffix + i + "@test.local");
            user.setNickName("P" + suffix + i);
            user.setPassword("unused");
            people.add(users.save(user));
          }
          LadderConfig league = new LadderConfig();
          league.setTitle("League " + suffix);
          league.setOwnerUserId(people.get(9).getId());
          league.setType(LadderConfig.Type.COMPETITION);
          league = configs.save(league);
          season = new LadderSeason();
          season.setLadderConfig(league);
          season.setName("Season " + suffix);
          season.setState(LadderSeason.State.ACTIVE);
          season.setStartDate(LocalDate.now().minusDays(1));
          season.setEndDate(LocalDate.now().plusDays(30));
          season = seasons.save(season);
          session = new LadderConfig();
          session.setType(LadderConfig.Type.SESSION);
          session.setSessionPlayMode(LadderConfig.SessionPlayMode.MATCHUPS);
          session.setTitle("Friends " + suffix);
          session.setOwnerUserId(people.get(9).getId());
          session.setTargetSeasonId(season.getId());
          session.setExpiresAt(Instant.now().plusSeconds(172800));
          session = configs.save(session);
          definitions = new LinkedHashMap<>();
          for (var d : RoundRobinTrophyTemplates.definitions()) {
            Trophy t = new Trophy();
            t.setSeason(season);
            t.setTitle(d.title());
            t.setGenerationSeed("round-robin-v1:" + d.slug());
            t.setSummary("Test");
            t.setSlug(suffix + "-" + d.slug());
            t.setUnlockCondition(d.condition());
            t.setUnlockExpression(d.expression());
            t.setRoundRobinTrophy(true);
            t.setRepeatable(true);
            t.setStatus(TrophyStatus.GENERATED);
            t.setRarity(TrophyRarity.valueOf(d.rarity()));
            t.setEvaluationScope(TrophyEvaluationScope.USER);
            definitions.put(d.slug(), trophies.save(t));
          }
          return null;
        });
  }

  private RoundRobin run(RoundRobin.Format format) {
    return tx(
        () -> {
          RoundRobin rr = new RoundRobin();
          rr.setSeason(season);
          rr.setSessionConfig(session);
          rr.setFormat(format);
          rr.setCreatedBy(people.get(9));
          rr.setSessionFlow(true);
          return runs.saveAndFlush(rr);
        });
  }

  private Match game(
      RoundRobin rr, int a1, int a2, int b1, int b2, int scoreA, int scoreB, MatchState state) {
    return tx(
        () -> {
          Match m = new Match();
          m.setSeason(season);
          m.setSourceSessionConfig(session);
          m.setA1(people.get(a1));
          m.setA2(people.get(a2));
          m.setB1(people.get(b1));
          m.setB2(people.get(b2));
          m.setScoreA(scoreA);
          m.setScoreB(scoreB);
          m.setState(state);
          m.setLoggedBy(people.get(9));
          m.setPlayedAt(Instant.now().minusSeconds(300).plusSeconds(gameNumber++));
          m = matches.saveAndFlush(m);
          RoundRobinEntry e = new RoundRobinEntry();
          e.setRoundRobin(rr);
          e.setRoundNumber(gameNumber);
          e.setA1(people.get(a1));
          e.setA2(people.get(a2));
          e.setB1(people.get(b1));
          e.setB2(people.get(b2));
          e.setMatchId(m.getId());
          entries.saveAndFlush(e);
          return m;
        });
  }

  private void standardGames(RoundRobin rr) {
    game(rr, 0, 1, 2, 3, 11, 3, MatchState.CONFIRMED);
    game(rr, 0, 2, 1, 3, 11, 9, MatchState.CONFIRMED);
    game(rr, 0, 3, 1, 2, 11, 7, MatchState.CONFIRMED);
  }

  private List<Long> owners(RoundRobin rr, String slug) {
    return tx(
        () ->
            awards.findForRun(rr.getId()).stream()
                .filter(a -> a.getTrophy().getId().equals(definitions.get(slug).getId()))
                .map(a -> a.getUser().getId())
                .sorted()
                .toList());
  }

  private int count(String slug, int person) {
    return tx(
        () ->
            collections
                .findByUserAndTrophy(people.get(person), definitions.get(slug))
                .map(UserTrophy::getAwardCount)
                .orElse(0));
  }

  @Test
  void completedRunsAreProcessedOnceAndAwardPlacesAndParticipation() {
    RoundRobin rr = run(RoundRobin.Format.ROTATING_PARTNERS);
    standardGames(rr);
    edit(
        () -> {
          Trophy champion = trophies.findById(definitions.get("rr-champion").getId()).orElseThrow();
          champion.setDisplayOrder(99);
          trophies.save(champion);
          Trophy runnerUp =
              trophies.findById(definitions.get("rr-runner-up").getId()).orElseThrow();
          runnerUp.setDisplayOrder(-1);
          trophies.save(runnerUp);
        });
    assertThat(service.process(rr.getId())).isTrue();
    assertThat(owners(rr, "rr-champion")).containsExactly(people.get(0).getId());
    assertThat(owners(rr, "rr-runner-up")).containsExactly(people.get(1).getId());
    assertThat(owners(rr, "rr-wooden-spoon")).containsExactly(people.get(2).getId());
    assertThat(owners(rr, "rr-court-time"))
        .containsExactlyElementsOf(people.subList(0, 4).stream().map(User::getId).toList());
    var shown = service.awardsForRun(rr.getId());
    assertThat(shown)
        .extracting(RoundRobinTrophyAwardService.RunAward::place)
        .containsExactly(1, 2);
    assertThat(shown.getFirst().players())
        .containsExactly(
            new RoundRobinTrophyAwardService.RunAwardPlayer(
                people.get(0).getNickName(), people.get(0).getPublicCode()));
    assertThat(shown.getLast().players())
        .containsExactly(
            new RoundRobinTrophyAwardService.RunAwardPlayer(
                people.get(1).getNickName(), people.get(1).getPublicCode()));
    assertThat(shown.getFirst().imageUrl()).isEqualTo("/images/trophy/fallback.png");
    assertThat(service.process(rr.getId())).isFalse();
    assertThat(count("rr-court-time", 0)).isEqualTo(1);
    assertThat(runs.findById(rr.getId()).orElseThrow().getTrophiesProcessedAt()).isNotNull();
    assertThat(runs.findUnprocessedFinishedRuns(Instant.now(), PageRequest.of(0, 100)))
        .doesNotContain(rr.getId());
  }

  @Test
  void twoRunsInOneSessionHaveIndependentCountsAndInvalidation() {
    RoundRobin first = run(RoundRobin.Format.ROTATING_PARTNERS),
        second = run(RoundRobin.Format.ROTATING_PARTNERS);
    standardGames(first);
    standardGames(second);
    service.process(first.getId());
    service.process(second.getId());
    assertThat(count("rr-court-time", 0)).isEqualTo(2);
    Long matchId = entries.findByRoundRobinOrderByRoundNumberAsc(first).getFirst().getMatchId();
    edit(
        () -> {
          Match m = matches.findById(matchId).orElseThrow();
          m.setState(MatchState.FLAGGED);
          matches.saveAndFlush(m);
        });
    assertThat(runs.findById(first.getId()).orElseThrow().isTrophiesProcessed()).isFalse();
    assertThat(runs.findById(second.getId()).orElseThrow().isTrophiesProcessed()).isTrue();
    assertThat(count("rr-court-time", 0)).isEqualTo(1);
    assertThat(service.process(first.getId())).isFalse();
    edit(
        () -> {
          Match m = matches.findById(matchId).orElseThrow();
          m.setState(MatchState.CONFIRMED);
          matches.saveAndFlush(m);
        });
    assertThat(service.process(first.getId())).isTrue();
    assertThat(count("rr-court-time", 0)).isEqualTo(2);
  }

  @Test
  void pendingScoresBlockAwardsEvenWhenOrganizerScoringIsEnabledAndDoNotBlockOtherRuns() {
    edit(
        () -> {
          LadderConfig s = configs.findById(session.getId()).orElseThrow();
          s.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
          configs.save(s);
        });
    RoundRobin pending = run(RoundRobin.Format.ROTATING_PARTNERS);
    Match m = game(pending, 0, 1, 2, 3, 11, 9, MatchState.PROVISIONAL);
    RoundRobin ready = run(RoundRobin.Format.ROTATING_PARTNERS);
    standardGames(ready);
    assertThat(service.process(pending.getId())).isFalse();
    assertThat(count("rr-close-call", 0)).isZero();
    assertThat(runs.findUnprocessedFinishedRuns(Instant.now(), PageRequest.of(0, 1)))
        .containsExactly(ready.getId());
    edit(
        () -> {
          Match changed = matches.findById(m.getId()).orElseThrow();
          changed.setState(MatchState.CONFIRMED);
          matches.saveAndFlush(changed);
        });
    assertThat(service.process(pending.getId())).isTrue();
    assertThat(count("rr-close-call", 0)).isEqualTo(1);
  }

  @Test
  void correctionsAndNullificationsReconcileInsteadOfDuplicatingAwards() {
    RoundRobin rr = run(RoundRobin.Format.ROTATING_PARTNERS);
    standardGames(rr);
    service.process(rr.getId());
    Long id = entries.findByRoundRobinOrderByRoundNumberAsc(rr).get(1).getMatchId();
    edit(
        () -> {
          Match m = matches.findById(id).orElseThrow();
          m.setScoreA(1);
          m.setScoreB(11);
          matches.saveAndFlush(m);
        });
    assertThat(count("rr-close-call", 0)).isZero();
    assertThat(service.process(rr.getId())).isTrue();
    assertThat(count("rr-court-time", 0)).isEqualTo(1);
    assertThat(owners(rr, "rr-champion")).containsExactly(people.get(1).getId());
    edit(
        () -> {
          Match m = matches.findById(id).orElseThrow();
          m.setState(MatchState.NULLIFIED);
          matches.saveAndFlush(m);
        });
    assertThat(service.process(rr.getId())).isTrue();
    assertThat(count("rr-court-time", 0)).isZero();
    assertThat(owners(rr, "rr-close-call")).isEmpty();
  }

  @Test
  void fixedPartnersShareTeamPlacementsAndOneGameCanSettleASmallRun() {
    RoundRobin rr = run(RoundRobin.Format.FIXED_TEAMS);
    game(rr, 0, 1, 2, 3, 11, 9, MatchState.CONFIRMED);
    service.process(rr.getId());
    assertThat(owners(rr, "rr-champion"))
        .containsExactly(people.get(0).getId(), people.get(1).getId());
    assertThat(owners(rr, "rr-runner-up"))
        .containsExactly(people.get(2).getId(), people.get(3).getId());
    assertThat(owners(rr, "rr-wooden-spoon")).isEmpty();
  }

  @Test
  void tiesShareAPlaceWithoutUsingSeasonRatingOrAlphabeticalOrder() {
    RoundRobin rr = run(RoundRobin.Format.ROTATING_PARTNERS);
    game(rr, 0, 1, 2, 3, 11, 9, MatchState.CONFIRMED);
    service.process(rr.getId());
    assertThat(owners(rr, "rr-champion"))
        .containsExactly(people.get(0).getId(), people.get(1).getId());
    assertThat(owners(rr, "rr-runner-up"))
        .containsExactly(people.get(2).getId(), people.get(3).getId());
  }

  @Test
  void byesAndUnplayedAssignmentsDoNotBlockAnEndedRunOrAwardANoShow() {
    RoundRobin rr = run(RoundRobin.Format.ROTATING_PARTNERS);
    standardGames(rr);
    edit(
        () -> {
          RoundRobinEntry e = new RoundRobinEntry();
          e.setRoundRobin(rr);
          e.setRoundNumber(4);
          e.setA1(people.get(4));
          e.setBye(false);
          entries.save(e);
        });
    assertThat(service.process(rr.getId())).isFalse();
    edit(
        () -> {
          RoundRobin changed = runs.findById(rr.getId()).orElseThrow();
          changed.setEndedAt(Instant.now());
          runs.save(changed);
        });
    assertThat(service.process(rr.getId())).isTrue();
    assertThat(
            tx(() -> awards.findForRun(rr.getId()).stream().map(a -> a.getUser().getId()).toList()))
        .doesNotContain(people.get(4).getId());
  }

  @Test
  void anExpiredSessionClosesItsRunAndAnEmptyEndedRunEarnsNothing() {
    RoundRobin rr = run(RoundRobin.Format.ROTATING_PARTNERS);
    standardGames(rr);
    edit(
        () -> {
          RoundRobinEntry bye = new RoundRobinEntry();
          bye.setRoundRobin(rr);
          bye.setRoundNumber(4);
          bye.setBye(true);
          entries.save(bye);
          RoundRobinEntry unplayed = new RoundRobinEntry();
          unplayed.setRoundRobin(rr);
          unplayed.setRoundNumber(5);
          entries.save(unplayed);
          LadderConfig s = configs.findById(session.getId()).orElseThrow();
          s.setExpiresAt(Instant.now().minusSeconds(1));
          configs.save(s);
        });
    assertThat(service.process(rr.getId())).isTrue();
    assertThat(count("rr-court-time", 0)).isEqualTo(1);
    RoundRobin empty = run(RoundRobin.Format.ROTATING_PARTNERS);
    assertThat(service.process(empty.getId())).isTrue();
    assertThat(tx(() -> awards.findForRun(empty.getId()))).isEmpty();
  }

  @Test
  void courtCaptainCanBeEarnedByAnOrganizerWhoDoesNotPlayTheGames() {
    RoundRobin rr = run(RoundRobin.Format.ROTATING_PARTNERS);
    standardGames(rr);
    game(rr, 0, 1, 2, 3, 11, 8, MatchState.CONFIRMED);
    service.process(rr.getId());
    assertThat(owners(rr, "rr-court-captain")).containsExactly(people.get(9).getId());
    assertThat(count("rr-court-time", 9)).isZero();
  }

  @Test
  void partnersAreCountedWithinTheRunAndRegularSeasonAwardsStillUseRoundRobinMatches() {
    RoundRobin first = run(RoundRobin.Format.ROTATING_PARTNERS),
        second = run(RoundRobin.Format.ROTATING_PARTNERS);
    game(first, 0, 1, 5, 6, 11, 9, MatchState.CONFIRMED);
    game(first, 0, 2, 5, 6, 11, 8, MatchState.CONFIRMED);
    game(second, 0, 3, 5, 6, 11, 9, MatchState.CONFIRMED);
    game(second, 0, 4, 5, 6, 11, 8, MatchState.CONFIRMED);
    service.process(first.getId());
    service.process(second.getId());
    assertThat(count("rr-mix-master", 0)).isZero();
    Trophy regular =
        tx(
            () -> {
              Trophy t = new Trophy();
              t.setSeason(season);
              t.setSlug(UUID.randomUUID().toString());
              t.setTitle("Season partners");
              t.setSummary("Test");
              t.setRarity(TrophyRarity.COMMON);
              t.setStatus(TrophyStatus.GENERATED);
              t.setUnlockExpression("unique_partners_won_with >= 4");
              return trophies.saveAndFlush(t);
            });
    seasonsService.evaluateSeasonSweep(season);
    assertThat(collections.findByUserAndTrophy(people.get(0), regular)).isPresent();
    assertThat(count("rr-court-captain", 9)).isZero();
    assertThat(count("rr-court-time", 0)).isZero();
    assertThat(count("rr-close-call", 0)).isEqualTo(2);
    RoundRobin third = run(RoundRobin.Format.ROTATING_PARTNERS);
    for (int partner = 1; partner <= 4; partner++)
      game(third, 0, partner, 5, 6, 11, 9, MatchState.CONFIRMED);
    service.process(third.getId());
    assertThat(count("rr-mix-master", 0)).isEqualTo(1);
  }

  @Test
  void repeatedWorkersCannotMintTheSameRunTwice() throws Exception {
    RoundRobin rr = run(RoundRobin.Format.ROTATING_PARTNERS);
    standardGames(rr);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      var calls =
          pool.invokeAll(
              List.of(() -> service.process(rr.getId()), () -> service.process(rr.getId())));
      assertThat(
              calls.stream()
                  .filter(
                      f -> {
                        try {
                          return Boolean.TRUE.equals(f.get());
                        } catch (Exception e) {
                          throw new RuntimeException(e);
                        }
                      })
                  .count())
          .isEqualTo(1);
      assertThat(count("rr-court-time", 0)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void deletingARunRemovesOnlyItsContributionToTheTrophyCollection() {
    RoundRobin first = run(RoundRobin.Format.ROTATING_PARTNERS),
        second = run(RoundRobin.Format.ROTATING_PARTNERS);
    standardGames(first);
    standardGames(second);
    service.process(first.getId());
    service.process(second.getId());
    edit(
        () -> {
          RoundRobin rr = runs.findById(first.getId()).orElseThrow();
          entries.deleteAll(entries.findByRoundRobinOrderByRoundNumberAsc(rr));
          runs.delete(rr);
        });
    assertThat(count("rr-court-time", 0)).isEqualTo(1);
  }

  @Test
  void ratingRecalculationsDoNotInvalidateFinishedRunAwards() {
    RoundRobin rr = run(RoundRobin.Format.ROTATING_PARTNERS);
    standardGames(rr);
    service.process(rr.getId());
    Long id = entries.findByRoundRobinOrderByRoundNumberAsc(rr).getFirst().getMatchId();
    edit(
        () -> {
          Match match = matches.findById(id).orElseThrow();
          match.setA1Delta(123);
          matches.saveAndFlush(match);
        });
    assertThat(runs.findById(rr.getId()).orElseThrow().isTrophiesProcessed()).isTrue();
    assertThat(count("rr-court-time", 0)).isEqualTo(1);
  }

  @Test
  void aRolledBackScoreChangeDoesNotInvalidateCommittedAwards() {
    RoundRobin rr = run(RoundRobin.Format.ROTATING_PARTNERS);
    standardGames(rr);
    service.process(rr.getId());
    Long id = entries.findByRoundRobinOrderByRoundNumberAsc(rr).getFirst().getMatchId();
    new TransactionTemplate(transactions)
        .execute(
            status -> {
              Match m = matches.findById(id).orElseThrow();
              m.setState(MatchState.FLAGGED);
              matches.saveAndFlush(m);
              status.setRollbackOnly();
              return null;
            });
    assertThat(runs.findById(rr.getId()).orElseThrow().isTrophiesProcessed()).isTrue();
    assertThat(count("rr-court-time", 0)).isEqualTo(1);
  }

  @Test
  void rotatingReplacementsEarnOnlyTheirOwnRecordedGames() {
    RoundRobin rr = run(RoundRobin.Format.ROTATING_PARTNERS);
    game(rr, 0, 1, 2, 3, 11, 9, MatchState.CONFIRMED);
    for (int i = 0; i < 3; i++) {
      Match m = game(rr, 4, 1, 2, 3, 11, 9, MatchState.CONFIRMED);
      edit(
          () -> {
            RoundRobinEntry e = entries.findByMatchId(m.getId()).getFirst();
            e.replace(RoundRobinEntry.Position.A1, people.get(0).getId(), people.get(4), null);
            entries.save(e);
          });
    }
    service.process(rr.getId());
    assertThat(count("rr-court-time", 0)).isZero();
    assertThat(count("rr-court-time", 4)).isEqualTo(1);
    assertThat(owners(rr, "rr-champion")).containsExactly(people.get(1).getId());
  }

  @Test
  void fixedTeamReplacementsKeepTheTeamPlaceButDoNotInheritPartnerWins() {
    RoundRobin rr = run(RoundRobin.Format.FIXED_TEAMS);
    game(rr, 0, 1, 2, 3, 11, 9, MatchState.CONFIRMED);
    for (int i = 0; i < 2; i++) {
      Match m = game(rr, 4, 1, 2, 3, 11, 9, MatchState.CONFIRMED);
      edit(
          () -> {
            RoundRobinEntry e = entries.findByMatchId(m.getId()).getFirst();
            e.replace(RoundRobinEntry.Position.A1, people.get(0).getId(), people.get(4), null);
            entries.save(e);
          });
    }
    service.process(rr.getId());
    assertThat(owners(rr, "rr-champion"))
        .containsExactly(people.get(0).getId(), people.get(1).getId(), people.get(4).getId());
    assertThat(owners(rr, "rr-winning-pair"))
        .containsExactly(people.get(1).getId(), people.get(4).getId());
    assertThat(count("rr-court-time", 0)).isZero();
  }

  @Test
  void guestsCanCompeteAndSeasonExclusionDoesNotTurnTheirOpponentsIntoChampions() {
    RoundRobin rr = run(RoundRobin.Format.FIXED_TEAMS);
    Match recorded = game(rr, 0, 1, 2, 3, 9, 11, MatchState.CONFIRMED);
    edit(
        () -> {
          Match m = matches.findById(recorded.getId()).orElseThrow();
          m.setB1(null);
          m.setB2(null);
          m.setB1Guest(true);
          m.setB2Guest(true);
          m.setB1GuestName("Guest One");
          m.setB2GuestName("Guest Two");
          m.setExcludeFromStandings(true);
          matches.saveAndFlush(m);
          RoundRobinEntry e = entries.findByMatchId(m.getId()).getFirst();
          e.replace(RoundRobinEntry.Position.B1, people.get(2).getId(), null, "Guest One");
          e.replace(RoundRobinEntry.Position.B2, people.get(3).getId(), null, "Guest Two");
          entries.saveAndFlush(e);
        });
    service.process(rr.getId());
    assertThat(owners(rr, "rr-champion")).isEmpty();
    assertThat(owners(rr, "rr-runner-up"))
        .containsExactly(people.get(0).getId(), people.get(1).getId());
    assertThat(
            tx(() -> awards.findForRun(rr.getId()).stream().map(a -> a.getUser().getId()).toList()))
        .doesNotContain(people.get(2).getId(), people.get(3).getId());
  }

  @Test
  void concurrentScoreCommitsReleaseConnectionsBeforeInvalidatingAwards() throws Exception {
    RoundRobin rr = run(RoundRobin.Format.ROTATING_PARTNERS);
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < 12; i++) ids.add(game(rr, 0, 1, 2, 3, 11, 9, MatchState.CONFIRMED).getId());
    service.process(rr.getId());
    ExecutorService pool = Executors.newFixedThreadPool(12);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (Long id : ids)
        futures.add(
            pool.submit(
                () ->
                    new TransactionTemplate(transactions)
                        .execute(
                            status -> {
                              Match m = matches.findById(id).orElseThrow();
                              m.setScoreA(10);
                              matches.saveAndFlush(m);
                              return null;
                            })));
      for (Future<?> future : futures) future.get(15, TimeUnit.SECONDS);
      changes.flush();
      assertThat(runs.findById(rr.getId()).orElseThrow().isTrophiesProcessed()).isFalse();
      assertThat(service.process(rr.getId())).isTrue();
      assertThat(count("rr-court-time", 0)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void aNonRepeatableOptInTrophyStaysOwnedWhenOneOfItsQualifyingRunsChanges() {
    edit(
        () -> {
          Trophy t = trophies.findById(definitions.get("rr-close-call").getId()).orElseThrow();
          t.setRepeatable(false);
          trophies.save(t);
        });
    RoundRobin first = run(RoundRobin.Format.ROTATING_PARTNERS),
        second = run(RoundRobin.Format.ROTATING_PARTNERS);
    Match changed = game(first, 0, 1, 2, 3, 11, 9, MatchState.CONFIRMED);
    game(second, 0, 1, 2, 3, 11, 9, MatchState.CONFIRMED);
    service.process(first.getId());
    service.process(second.getId());
    assertThat(count("rr-close-call", 0)).isEqualTo(1);
    edit(
        () -> {
          Match m = matches.findById(changed.getId()).orElseThrow();
          m.setState(MatchState.NULLIFIED);
          matches.saveAndFlush(m);
        });
    assertThat(count("rr-close-call", 0)).isEqualTo(1);
    service.process(first.getId());
    assertThat(count("rr-close-call", 0)).isEqualTo(1);
  }
}
