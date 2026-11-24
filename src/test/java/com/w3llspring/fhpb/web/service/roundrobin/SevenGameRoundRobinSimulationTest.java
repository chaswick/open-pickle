package com.w3llspring.fhpb.web.service.roundrobin;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.model.*;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinScheduler.MatchSpec;
import com.w3llspring.fhpb.web.service.scoring.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Realistic session budgets using the real season scorer, with no second rating model. */
class SevenGameRoundRobinSimulationTest {
  enum Profile {
    EQUAL,
    BANDS,
    NOISY,
    UNRATED,
    UNDERRATED
  }

  enum Mode {
    NORMAL,
    PERFORMANCE
  }

  enum Budget {
    SEVEN_ROUNDS,
    SEVEN_GAMES_EACH
  }

  record Fixture(List<Long> ids, Map<Long, Integer> points, Map<Long, Double> skill) {}

  record Run(
      Profile profile,
      int count,
      long seed,
      Budget budget,
      Mode mode,
      int rounds,
      int games,
      int minGames,
      int maxGames,
      double opponentGap,
      double teamGap,
      double courtSpan,
      double nearOpponentShare,
      double farGamesShare,
      double worstFarGamesShare,
      int longestFarStreak,
      double uniquePartners,
      double uniqueOpponents,
      double partnerRepeats,
      double opponentRepeats,
      int maxByeSpread,
      int consecutiveByes,
      int consecutivePartners) {}

  record PlayerRun(
      Profile profile,
      int count,
      long seed,
      Budget budget,
      Mode mode,
      long player,
      int startPoints,
      int endPoints,
      int games,
      int byes,
      int partners,
      int opponents,
      double opponentGap,
      double nearOpponentShare,
      double farGamesShare,
      int longestFarStreak) {}

  private static final List<Run> RUNS = new ArrayList<>();
  private static final List<PlayerRun> PLAYERS = new ArrayList<>();
  private static final long[] SEEDS = {7, 41, 101};

  static IntStream counts() {
    return IntStream.rangeClosed(4, 32);
  }

  @ParameterizedTest(name = "{0} players: seven-round sessions, five profiles, three seeds")
  @MethodSource("counts")
  void compareRealisticSessionBudgets(int count) {
    for (Profile profile : Profile.values())
      for (long seed : SEEDS)
        for (Mode mode : Mode.values())
          RUNS.add(
              simulate(
                  fixture(count, profile, seed), profile, seed, mode, Budget.SEVEN_ROUNDS, true));
  }

  @ParameterizedTest(name = "{0} players: enough rounds for seven actual games each")
  @MethodSource("counts")
  void sevenActualGamesEachIncludesTimeSpentOnByes(int count) {
    for (long seed : SEEDS)
      for (Mode mode : Mode.values()) {
        Run run =
            simulate(
                fixture(count, Profile.BANDS, seed),
                Profile.BANDS,
                seed,
                mode,
                Budget.SEVEN_GAMES_EACH,
                true);
        RUNS.add(run);
        assertThat(run.minGames()).isGreaterThanOrEqualTo(7);
        assertThat(run.maxGames() - run.minGames()).isLessThanOrEqualTo(1);
      }
  }

  @Test
  void performanceProvidesCloserIndividualOpponentsWithinSevenGames() {
    List<Run> normal = new ArrayList<>(), performance = new ArrayList<>();
    for (int count : new int[] {8, 12, 16, 24, 32})
      for (long seed : SEEDS) {
        Fixture f = fixture(count, Profile.BANDS, seed);
        normal.add(simulate(f, Profile.BANDS, seed, Mode.NORMAL, Budget.SEVEN_ROUNDS));
        performance.add(simulate(f, Profile.BANDS, seed, Mode.PERFORMANCE, Budget.SEVEN_ROUNDS));
      }
    assertThat(mean(performance, Run::opponentGap))
        .isLessThan(mean(normal, Run::opponentGap) * .85);
    assertThat(mean(performance, Run::courtSpan)).isLessThan(mean(normal, Run::courtSpan) * .85);
    assertThat(mean(performance, Run::farGamesShare)).isLessThan(mean(normal, Run::farGamesShare));
  }

  @Test
  void fixedSeedReproducesSchedulesSeasonUpdatesAndExperienceMetrics() {
    Fixture f = fixture(15, Profile.NOISY, 73);
    for (Mode mode : Mode.values())
      assertThat(simulate(f, Profile.NOISY, 73, mode, Budget.SEVEN_ROUNDS))
          .isEqualTo(simulate(f, Profile.NOISY, 73, mode, Budget.SEVEN_ROUNDS));
  }

  private static Fixture fixture(int count, Profile profile, long seed) {
    List<Long> ids = LongStream.range(0, count).map(i -> 101 + 17 * i).boxed().toList();
    List<Long> assignment = new ArrayList<>(ids);
    Collections.shuffle(assignment, new Random(seed));
    Map<Long, Integer> points = new HashMap<>();
    Map<Long, Double> skill = new HashMap<>();
    Random noise = new Random(seed ^ 0x57111L);
    for (int i = 0; i < count; i++) {
      double value =
          profile == Profile.EQUAL
              ? 400
              : profile == Profile.BANDS
                  ? Math.min(3, i * 4 / count) * 300
                  : 800d * i / (count - 1);
      int estimate =
          profile == Profile.UNRATED
              ? 0
              : (int)
                  Math.round(
                      value / 7.5 + (profile == Profile.NOISY ? 20 * noise.nextGaussian() : 0));
      if (profile == Profile.UNDERRATED && i == count - 1) estimate = 0;
      points.put(assignment.get(i), estimate);
      skill.put(assignment.get(i), value);
    }
    return new Fixture(ids, points, skill);
  }

  private static Run simulate(Fixture f, Profile profile, long seed, Mode mode, Budget budget) {
    return simulate(f, profile, seed, mode, budget, false);
  }

  private static Run simulate(
      Fixture f, Profile profile, long seed, Mode mode, Budget budget, boolean recordPlayers) {
    int count = f.ids().size();
    int rounds =
        budget == Budget.SEVEN_ROUNDS ? 7 : (int) Math.ceil(7d * count / (4 * (count / 4)));
    var normal = new FairByeRoundRobinScheduler();
    normal.setSeedForTests(seed);
    var fixed =
        mode == Mode.NORMAL
            ? normal.generate(f.ids(), Map.of(), rounds)
            : List.<List<MatchSpec>>of();
    var performance = new PerformanceRoundRobinScheduler();
    var scoring = new MarginCurveV1LadderScoringAlgorithm();
    Map<Long, User> users = new HashMap<>();
    Map<Long, LadderStanding> standings = new HashMap<>();
    Map<Long, Integer> played = new HashMap<>(),
        byes = new HashMap<>(),
        far = new HashMap<>(),
        streak = new HashMap<>(),
        longest = new HashMap<>(),
        near = new HashMap<>();
    Map<Long, Double> gaps = new HashMap<>();
    Map<Long, Set<Long>> partners = new HashMap<>(), opponents = new HashMap<>();
    Map<Set<Long>, Integer> partnerCounts = new HashMap<>(), opponentCounts = new HashMap<>();
    for (Long id : f.ids()) {
      User u = new User();
      u.setId(id);
      u.setNickName("Player" + id);
      users.put(id, u);
      LadderStanding standing = new LadderStanding();
      standing.setUser(u);
      standing.setPoints(f.points().get(id));
      standings.put(id, standing);
      played.put(id, 0);
      byes.put(id, 0);
      far.put(id, 0);
      streak.put(id, 0);
      longest.put(id, 0);
      near.put(id, 0);
      gaps.put(id, 0d);
      partners.put(id, new HashSet<>());
      opponents.put(id, new HashSet<>());
    }
    List<List<MatchSpec>> history = new ArrayList<>();
    List<Match> results = new ArrayList<>();
    Set<Long> previousByes = Set.of();
    Set<Set<Long>> previousTeams = Set.of();
    int games = 0,
        repeatedPartners = 0,
        repeatedOpponents = 0,
        maxByeSpread = 0,
        consecutiveByes = 0,
        consecutivePartners = 0;
    double teamGap = 0, courtSpan = 0;
    for (int r = 0; r < rounds; r++) {
      Map<Long, Double> latest = new HashMap<>();
      standings.forEach((id, s) -> latest.put(id, (double) s.getPoints()));
      var round =
          mode == Mode.NORMAL
              ? fixed.get(r)
              : performance.nextRound(f.ids(), latest, history, seed);
      assertThat(
              round.stream()
                  .flatMap(g -> PerformanceRoundRobinScheduler.players(g).stream())
                  .toList())
          .as("%s %s %d players seed %d round %d", profile, mode, count, seed, r + 1)
          .containsExactlyInAnyOrderElementsOf(f.ids());
      assertThat(round.stream().filter(g -> !g.bye).count()).isEqualTo(count / 4);
      Set<Long> resting = new HashSet<>();
      Set<Set<Long>> teams = new HashSet<>();
      for (var g : round) {
        if (g.bye) {
          for (Long id : PerformanceRoundRobinScheduler.players(g)) {
            byes.merge(id, 1, Integer::sum);
            resting.add(id);
          }
          continue;
        }
        games++;
        var ids = PerformanceRoundRobinScheduler.players(g);
        assertThat(ids).hasSize(4).doesNotHaveDuplicates();
        var range = ids.stream().mapToDouble(f.skill()::get).summaryStatistics();
        courtSpan += range.getMax() - range.getMin();
        teamGap +=
            Math.abs(
                (f.skill().get(g.a1)
                        + f.skill().get(g.a2)
                        - f.skill().get(g.b1)
                        - f.skill().get(g.b2))
                    / 2);
        for (var team : List.of(List.of(g.a1, g.a2), List.of(g.b1, g.b2))) {
          var key = Set.copyOf(team);
          if (partnerCounts.containsKey(key)) repeatedPartners++;
          if (previousTeams.contains(key)) consecutivePartners++;
          partnerCounts.merge(key, 1, Integer::sum);
          teams.add(key);
          partners.get(team.get(0)).add(team.get(1));
          partners.get(team.get(1)).add(team.get(0));
        }
        for (Long a : List.of(g.a1, g.a2))
          for (Long b : List.of(g.b1, g.b2)) {
            var key = Set.of(a, b);
            if (opponentCounts.containsKey(key)) repeatedOpponents++;
            opponentCounts.merge(key, 1, Integer::sum);
            opponents.get(a).add(b);
            opponents.get(b).add(a);
          }
        for (Long id : ids) {
          played.merge(id, 1, Integer::sum);
          var faced = List.of(g.a1, g.a2).contains(id) ? List.of(g.b1, g.b2) : List.of(g.a1, g.a2);
          double gap =
              faced.stream()
                  .mapToDouble(other -> Math.abs(f.skill().get(id) - f.skill().get(other)))
                  .average()
                  .orElseThrow();
          gaps.merge(id, gap, Double::sum);
          for (Long other : faced)
            if (Math.abs(f.skill().get(id) - f.skill().get(other)) <= 200)
              near.merge(id, 1, Integer::sum);
          if (gap > 300) {
            far.merge(id, 1, Integer::sum);
            streak.merge(id, 1, Integer::sum);
          } else streak.put(id, 0);
          longest.put(id, Math.max(longest.get(id), streak.get(id)));
        }
        Match match = match(g, users, f.skill(), seed, r);
        match.setPlayedAt(Instant.parse("2026-01-01T12:00:00Z").plusSeconds(r * 900L + games));
        var adjustment =
            scoring.score(
                new LadderScoringRequest(
                    match, standings, Map.of(), Map.of(), 1, 1L, Map.of(), results));
        assertThat(adjustment.getAdjustments()).hasSize(4);
        adjustment
            .getAdjustments()
            .forEach(
                (u, delta) ->
                    standings
                        .get(u.getId())
                        .setPoints(standings.get(u.getId()).getPoints() + delta));
        results.addFirst(match);
      }
      assertThat(resting).hasSize(count % 4);
      for (Long id : resting) if (previousByes.contains(id)) consecutiveByes++;
      maxByeSpread =
          Math.max(maxByeSpread, Collections.max(byes.values()) - Collections.min(byes.values()));
      if (mode == Mode.PERFORMANCE) assertThat(maxByeSpread).isLessThanOrEqualTo(1);
      previousByes = resting;
      previousTeams = teams;
      history.add(round);
    }
    assertThat(Collections.max(byes.values()) - Collections.min(byes.values()))
        .isLessThanOrEqualTo(1);
    if (mode == Mode.PERFORMANCE) assertThat(consecutiveByes).isZero();
    if (mode == Mode.PERFORMANCE && count >= 8) assertThat(consecutivePartners).isZero();
    for (Long id : f.ids()) {
      assertThat(partners.get(id).size()).isGreaterThanOrEqualTo(Math.min(3, played.get(id)));
      if (recordPlayers)
        PLAYERS.add(
            new PlayerRun(
                profile,
                count,
                seed,
                budget,
                mode,
                id,
                f.points().get(id),
                standings.get(id).getPoints(),
                played.get(id),
                byes.get(id),
                partners.get(id).size(),
                opponents.get(id).size(),
                gaps.get(id) / played.get(id),
                near.get(id) / (2d * played.get(id)),
                far.get(id) / (double) played.get(id),
                longest.get(id)));
    }
    return new Run(
        profile,
        count,
        seed,
        budget,
        mode,
        rounds,
        games,
        Collections.min(played.values()),
        Collections.max(played.values()),
        gaps.values().stream().mapToDouble(Double::doubleValue).sum() / (4 * games),
        teamGap / games,
        courtSpan / games,
        near.values().stream().mapToInt(Integer::intValue).sum() / (8d * games),
        far.values().stream().mapToInt(Integer::intValue).sum() / (4d * games),
        f.ids().stream()
            .mapToDouble(id -> far.get(id) / (double) played.get(id))
            .max()
            .orElseThrow(),
        Collections.max(longest.values()),
        partners.values().stream().mapToInt(Set::size).average().orElseThrow(),
        opponents.values().stream().mapToInt(Set::size).average().orElseThrow(),
        repeatedPartners / (2d * games),
        repeatedOpponents / (4d * games),
        maxByeSpread,
        consecutiveByes,
        consecutivePartners);
  }

  private static Match match(
      MatchSpec g, Map<Long, User> users, Map<Long, Double> skill, long seed, int round) {
    var a = Stream.of(g.a1, g.a2).sorted().toList();
    var b = Stream.of(g.b1, g.b2).sorted().toList();
    boolean swapped = a.getFirst() > b.getFirst();
    if (swapped) {
      var temp = a;
      a = b;
      b = temp;
    }
    double p =
        1
            / (1
                + Math.pow(
                    10,
                    (b.stream().mapToDouble(skill::get).average().orElseThrow()
                            - a.stream().mapToDouble(skill::get).average().orElseThrow())
                        / 1600));
    Random rng = new Random(Objects.hash(seed, round, a, b));
    int sa = 0, sb = 0;
    while ((Math.max(sa, sb) < 11 || Math.abs(sa - sb) < 2) && Math.max(sa, sb) < 35) {
      if (rng.nextDouble() < p) sa++;
      else sb++;
    }
    Match match = new Match();
    match.setA1(users.get(g.a1));
    match.setA2(users.get(g.a2));
    match.setB1(users.get(g.b1));
    match.setB2(users.get(g.b2));
    match.setScoreA(swapped ? sb : sa);
    match.setScoreB(swapped ? sa : sb);
    match.setState(MatchState.CONFIRMED);
    return match;
  }

  private static double mean(List<Run> runs, ToDoubleFunction<Run> metric) {
    return runs.stream().mapToDouble(metric).average().orElseThrow();
  }

  @AfterAll
  static void report() throws IOException {
    Path dir = Path.of("build/reports/roundrobin-seven-games");
    Files.createDirectories(dir);
    write(dir.resolve("runs.csv"), Run.class, RUNS);
    write(dir.resolve("players.csv"), PlayerRun.class, PLAYERS);
    for (Profile profile : Profile.values())
      for (Mode mode : Mode.values()) {
        var group =
            RUNS.stream()
                .filter(
                    r ->
                        r.profile() == profile
                            && r.mode() == mode
                            && r.count() >= 8
                            && r.budget() == Budget.SEVEN_ROUNDS)
                .toList();
        if (group.isEmpty()) continue;
        System.out.printf(
            Locale.ROOT,
            "SEVEN_GAMES %s %s runs=%d opponentGap=%.1f courtSpan=%.1f teamGap=%.1f near=%.1f%% farGames=%.1f%% worstPlayerFar=%.1f%% longestFar=%.1f uniquePartners=%.1f uniqueOpponents=%.1f repeatedPartners=%.1f%% repeatedOpponents=%.1f%%%n",
            profile,
            mode,
            group.size(),
            mean(group, Run::opponentGap),
            mean(group, Run::courtSpan),
            mean(group, Run::teamGap),
            100 * mean(group, Run::nearOpponentShare),
            100 * mean(group, Run::farGamesShare),
            100 * mean(group, Run::worstFarGamesShare),
            mean(group, Run::longestFarStreak),
            mean(group, Run::uniquePartners),
            mean(group, Run::uniqueOpponents),
            100 * mean(group, Run::partnerRepeats),
            100 * mean(group, Run::opponentRepeats));
      }
  }

  private static void write(Path path, Class<? extends Record> type, List<? extends Record> records)
      throws IOException {
    List<String> rows = new ArrayList<>();
    rows.add(
        Arrays.stream(type.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .collect(Collectors.joining(",")));
    for (Record record : records)
      rows.add(
          Arrays.stream(type.getRecordComponents())
              .map(
                  c -> {
                    try {
                      return String.valueOf(c.getAccessor().invoke(record));
                    } catch (ReflectiveOperationException ex) {
                      throw new IllegalStateException(ex);
                    }
                  })
              .collect(Collectors.joining(",")));
    Files.write(path, rows);
  }
}
