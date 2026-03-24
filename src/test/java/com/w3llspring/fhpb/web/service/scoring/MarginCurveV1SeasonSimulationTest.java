package com.w3llspring.fhpb.web.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

class MarginCurveV1SeasonSimulationTest {
  private static final int BASELINE_RATING = 1000;
  private static final int DEFAULT_MATCH_COUNT = 200;
  private static final long DEFAULT_ROSTER_SEED = 20260311L;
  private static final long DEFAULT_RANDOM_MATCHMAKING_SEED = 20260312L;
  private static final long DEFAULT_GAMESMANSHIP_SEED = 20260313L;
  private static final long DEFAULT_REFUSAL_SEED = 20260314L;
  private static final long DEFAULT_REC_PLAY_SEED = 20260315L;
  private static final long DEFAULT_STREAK_GAMING_SEED = 20260316L;
  private static final Instant SEASON_START = Instant.parse("2026-01-01T12:00:00Z");

  private final MarginCurveV1LadderScoringAlgorithm algorithm =
      new MarginCurveV1LadderScoringAlgorithm();

  @Test
  void simulateSeasonWithRandomMatchmaking() {
    SimulationReport report =
        simulateSeason(
            Scenario.RANDOM_MATCHMAKING,
            resolveLong("fhpb.marginCurve.simulation.rosterSeed", DEFAULT_ROSTER_SEED),
            resolveLong("fhpb.marginCurve.simulation.randomSeed", DEFAULT_RANDOM_MATCHMAKING_SEED),
            resolveInt("fhpb.marginCurve.simulation.matches", DEFAULT_MATCH_COUNT));

    printReport(report);

    assertBroadBucketOrdering(report);
  }

  @Test
  void simulateSeasonWithGamesmanshipAndSelfSorting() {
    SimulationReport report =
        simulateSeason(
            Scenario.GAMESMANSHIP_AND_SELF_SORTING,
            resolveLong("fhpb.marginCurve.simulation.rosterSeed", DEFAULT_ROSTER_SEED),
            resolveLong("fhpb.marginCurve.simulation.gamesmanshipSeed", DEFAULT_GAMESMANSHIP_SEED),
            resolveInt("fhpb.marginCurve.simulation.matches", DEFAULT_MATCH_COUNT));

    printReport(report);

    assertBroadBucketOrdering(report);
  }

  @Test
  void simulateSeasonWithGamesmanshipAndBlowoutRefusal() {
    SimulationReport report =
        simulateSeason(
            Scenario.GAMESMANSHIP_WITH_BLOWOUT_REFUSAL,
            resolveLong("fhpb.marginCurve.simulation.rosterSeed", DEFAULT_ROSTER_SEED),
            resolveLong("fhpb.marginCurve.simulation.refusalSeed", DEFAULT_REFUSAL_SEED),
            resolveInt("fhpb.marginCurve.simulation.matches", DEFAULT_MATCH_COUNT));

    printReport(report);

    assertBroadBucketOrdering(report);
  }

  @Test
  void simulateSeasonWithRecPlaySkillAffinity() {
    SimulationReport report =
        simulateSeason(
            Scenario.REC_PLAY_SKILL_AFFINITY,
            resolveLong("fhpb.marginCurve.simulation.rosterSeed", DEFAULT_ROSTER_SEED),
            resolveLong("fhpb.marginCurve.simulation.recPlaySeed", DEFAULT_REC_PLAY_SEED),
            resolveInt("fhpb.marginCurve.simulation.matches", DEFAULT_MATCH_COUNT));

    printReport(report);

    assertBroadBucketOrdering(report);
  }

  @Test
  void simulateSeasonWithStreakGamingByBucket() {
    SimulationReport report =
        simulateSeason(
            Scenario.STREAK_GAMING_BY_BUCKET,
            resolveLong("fhpb.marginCurve.simulation.rosterSeed", DEFAULT_ROSTER_SEED),
            resolveLong("fhpb.marginCurve.simulation.streakSeed", DEFAULT_STREAK_GAMING_SEED),
            resolveInt("fhpb.marginCurve.simulation.matches", DEFAULT_MATCH_COUNT));

    printReport(report);

    assertBroadBucketOrdering(report);
  }

  private SimulationReport simulateSeason(
      Scenario scenario, long rosterSeed, long matchSeed, int matchCount) {
    List<SimPlayer> roster =
        prepareRosterForScenario(scenario, buildRoster(new Random(rosterSeed)));
    Random random = new Random(matchSeed);
    List<Match> history = new ArrayList<>();
    Map<Long, Integer> ratingPoints = new HashMap<>();
    Map<Long, Integer> matchesPlayed = new HashMap<>();
    Map<Long, Integer> wins = new HashMap<>();
    Map<Long, Integer> losses = new HashMap<>();

    for (SimPlayer player : roster) {
      ratingPoints.put(player.user().getId(), 0);
      matchesPlayed.put(player.user().getId(), 0);
      wins.put(player.user().getId(), 0);
      losses.put(player.user().getId(), 0);
    }

    for (int matchNumber = 0; matchNumber < matchCount; matchNumber++) {
      Instant playedAt = SEASON_START.plusSeconds(matchNumber * 60L * 60L * 8L);
      Lineup lineup = scenario.selectLineup(roster, ratingPoints, history, random);
      Scoreline scoreline = simulateScoreline(lineup, random);
      Match match = buildMatch(lineup, scoreline, playedAt);

      List<Match> visibleHistory =
          history.stream()
              .filter(
                  past -> !past.getPlayedAt().isBefore(playedAt.minus(algorithm.historyWindow())))
              .toList();
      LadderScoringResult result =
          algorithm.score(
              new LadderScoringRequest(
                  match, Map.of(), Map.of(), Map.of(), 1, null, Map.of(), visibleHistory));

      applyAdjustments(result, ratingPoints);
      updateResults(match, lineup, matchesPlayed, wins, losses);
      history.add(0, match);
    }

    List<StandingRow> standings = buildStandings(roster, ratingPoints, matchesPlayed, wins, losses);
    return new SimulationReport(
        scenario, rosterSeed, matchSeed, matchCount, standings, buildBucketSummary(standings));
  }

  private void applyAdjustments(LadderScoringResult result, Map<Long, Integer> ratingPoints) {
    for (Map.Entry<User, Integer> entry : result.getAdjustments().entrySet()) {
      User user = entry.getKey();
      if (user == null || user.getId() == null) {
        continue;
      }
      ratingPoints.merge(user.getId(), entry.getValue(), Integer::sum);
    }
  }

  private void updateResults(
      Match match,
      Lineup lineup,
      Map<Long, Integer> matchesPlayed,
      Map<Long, Integer> wins,
      Map<Long, Integer> losses) {
    List<SimPlayer> teamA = List.of(lineup.a1(), lineup.a2());
    List<SimPlayer> teamB = List.of(lineup.b1(), lineup.b2());
    boolean teamAWon = match.isTeamAWinner();

    for (SimPlayer player : teamA) {
      matchesPlayed.merge(player.user().getId(), 1, Integer::sum);
      if (teamAWon) {
        wins.merge(player.user().getId(), 1, Integer::sum);
      } else {
        losses.merge(player.user().getId(), 1, Integer::sum);
      }
    }
    for (SimPlayer player : teamB) {
      matchesPlayed.merge(player.user().getId(), 1, Integer::sum);
      if (teamAWon) {
        losses.merge(player.user().getId(), 1, Integer::sum);
      } else {
        wins.merge(player.user().getId(), 1, Integer::sum);
      }
    }
  }

  private List<StandingRow> buildStandings(
      List<SimPlayer> roster,
      Map<Long, Integer> ratingPoints,
      Map<Long, Integer> matchesPlayed,
      Map<Long, Integer> wins,
      Map<Long, Integer> losses) {
    List<StandingRow> rows = new ArrayList<>();
    for (SimPlayer player : roster) {
      long userId = player.user().getId();
      int points = ratingPoints.getOrDefault(userId, 0);
      rows.add(
          new StandingRow(
              player,
              BASELINE_RATING + points,
              points,
              matchesPlayed.getOrDefault(userId, 0),
              wins.getOrDefault(userId, 0),
              losses.getOrDefault(userId, 0)));
    }

    rows.sort(
        Comparator.comparingInt(StandingRow::rating)
            .reversed()
            .thenComparing(Comparator.comparingInt(StandingRow::wins).reversed())
            .thenComparing(row -> row.player().user().getNickName()));

    List<StandingRow> ranked = new ArrayList<>(rows.size());
    for (int i = 0; i < rows.size(); i++) {
      ranked.add(rows.get(i).withRank(i + 1));
    }
    return ranked;
  }

  private Map<SkillBucket, BucketSummary> buildBucketSummary(List<StandingRow> standings) {
    Map<SkillBucket, List<StandingRow>> byBucket = new EnumMap<>(SkillBucket.class);
    for (SkillBucket bucket : SkillBucket.values()) {
      byBucket.put(bucket, new ArrayList<>());
    }
    for (StandingRow row : standings) {
      byBucket.get(row.player().bucket()).add(row);
    }

    Map<SkillBucket, BucketSummary> summary = new EnumMap<>(SkillBucket.class);
    for (Map.Entry<SkillBucket, List<StandingRow>> entry : byBucket.entrySet()) {
      List<StandingRow> rows = entry.getValue();
      double averageRating =
          rows.stream().mapToInt(StandingRow::rating).average().orElse(BASELINE_RATING);
      double averageMatches =
          rows.stream().mapToInt(StandingRow::matchesPlayed).average().orElse(0.0d);
      summary.put(entry.getKey(), new BucketSummary(averageRating, averageMatches));
    }
    return summary;
  }

  private Scoreline simulateScoreline(Lineup lineup, Random random) {
    double teamASkill = lineup.a1().skill() + lineup.a2().skill();
    double teamBSkill = lineup.b1().skill() + lineup.b2().skill();
    double winProbabilityA = 1.0d / (1.0d + Math.exp(-3.0d * (teamASkill - teamBSkill)));
    boolean teamAWon = random.nextDouble() < winProbabilityA;
    double winnerConfidence = teamAWon ? winProbabilityA : (1.0d - winProbabilityA);

    int winnerScore = chooseWinnerScore(winnerConfidence, random);
    double dominance =
        Math.max(
            0.04d,
            Math.min(
                0.92d,
                0.06d
                    + Math.pow(Math.max(0.0d, (winnerConfidence - 0.5d) * 2.0d), 1.05d) * 0.68d
                    + (random.nextGaussian() * 0.06d)));
    int loserScore = (int) Math.round(winnerScore * (1.0d - dominance) / (1.0d + dominance));
    int maxLoserScore = winnerScore > 11 ? winnerScore - 2 : winnerScore - 1;
    loserScore = Math.max(0, Math.min(maxLoserScore, loserScore));

    if (teamAWon) {
      return new Scoreline(winnerScore, loserScore);
    }
    return new Scoreline(loserScore, winnerScore);
  }

  private int chooseWinnerScore(double winnerConfidence, Random random) {
    if (winnerConfidence < 0.58d && random.nextDouble() < 0.30d) {
      return 12 + random.nextInt(4);
    }
    if (random.nextDouble() < 0.10d) {
      return 9;
    }
    return 11;
  }

  private Match buildMatch(Lineup lineup, Scoreline scoreline, Instant playedAt) {
    Match match = new Match();
    match.setPlayedAt(playedAt);
    match.setState(MatchState.CONFIRMED);
    match.setA1(lineup.a1().user());
    match.setA2(lineup.a2().user());
    match.setB1(lineup.b1().user());
    match.setB2(lineup.b2().user());
    match.setScoreA(scoreline.scoreA());
    match.setScoreB(scoreline.scoreB());
    return match;
  }

  private List<SimPlayer> buildRoster(Random random) {
    List<SimPlayer> roster = new ArrayList<>();
    long nextId = 1L;
    nextId = addPlayers(roster, random, nextId, SkillBucket.HIGH, 4, "H");
    nextId = addPlayers(roster, random, nextId, SkillBucket.MEDIUM, 8, "M");
    addPlayers(roster, random, nextId, SkillBucket.LOW, 4, "L");
    return roster;
  }

  private List<SimPlayer> prepareRosterForScenario(Scenario scenario, List<SimPlayer> roster) {
    return switch (scenario) {
      case GAMESMANSHIP_AND_SELF_SORTING, GAMESMANSHIP_WITH_BLOWOUT_REFUSAL -> {
        markSharkHighPlayer(roster);
        yield roster;
      }
      case STREAK_GAMING_BY_BUCKET -> {
        markStreakGamers(roster);
        yield roster;
      }
      default -> roster;
    };
  }

  private long addPlayers(
      List<SimPlayer> roster,
      Random random,
      long startId,
      SkillBucket bucket,
      int count,
      String prefix) {
    long nextId = startId;
    for (int i = 1; i <= count; i++) {
      User user = new User();
      user.setId(nextId++);
      user.setNickName(prefix + i);
      user.setEmail(prefix.toLowerCase() + i + "@example.com");

      double jitter = (random.nextDouble() - 0.5d) * 0.10d;
      roster.add(
          new SimPlayer(
              user, bucket, randomActivity(random), bucket.baseSkill() + jitter, PlayStyle.NORMAL));
    }
    return nextId;
  }

  private void markSharkHighPlayer(List<SimPlayer> roster) {
    int sharkIndex = -1;
    double highestSkill = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < roster.size(); i++) {
      SimPlayer player = roster.get(i);
      if (player.bucket() == SkillBucket.HIGH && player.skill() > highestSkill) {
        highestSkill = player.skill();
        sharkIndex = i;
      }
    }
    if (sharkIndex < 0) {
      return;
    }

    SimPlayer shark = roster.get(sharkIndex);
    roster.set(
        sharkIndex,
        new SimPlayer(
            shark.user(), shark.bucket(), shark.activity(), shark.skill(), PlayStyle.SHARK));
  }

  private void markStreakGamers(List<SimPlayer> roster) {
    for (SkillBucket bucket : SkillBucket.values()) {
      int streakGamerIndex = -1;
      double highestSkill = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < roster.size(); i++) {
        SimPlayer player = roster.get(i);
        if (player.bucket() != bucket || player.skill() <= highestSkill) {
          continue;
        }
        highestSkill = player.skill();
        streakGamerIndex = i;
      }
      if (streakGamerIndex < 0) {
        continue;
      }

      SimPlayer streakGamer = roster.get(streakGamerIndex);
      roster.set(
          streakGamerIndex,
          new SimPlayer(
              streakGamer.user(),
              streakGamer.bucket(),
              streakGamer.activity(),
              streakGamer.skill(),
              PlayStyle.STREAK_CHASER));
    }
  }

  private ActivityProfile randomActivity(Random random) {
    double roll = random.nextDouble();
    if (roll < 0.35d) {
      return ActivityProfile.ACTIVE;
    }
    if (roll < 0.75d) {
      return ActivityProfile.SEMI_ACTIVE;
    }
    return ActivityProfile.RARE;
  }

  private void assertBroadBucketOrdering(SimulationReport report) {
    assertThat(report.standings()).hasSize(16);
    assertThat(report.averageRating(SkillBucket.HIGH))
        .isGreaterThan(report.averageRating(SkillBucket.MEDIUM));
    assertThat(report.averageRating(SkillBucket.MEDIUM))
        .isGreaterThan(report.averageRating(SkillBucket.LOW));
    assertThat(report.ratingSpread()).isGreaterThan(15);
  }

  private void printReport(SimulationReport report) {
    System.out.printf(
        "%n=== Margin Curve Simulation: %s | rosterSeed=%d | matchSeed=%d | matches=%d ===%n",
        report.scenario().label(), report.rosterSeed(), report.matchSeed(), report.matchCount());
    for (SkillBucket bucket : SkillBucket.values()) {
      BucketSummary summary = report.bucketSummary().get(bucket);
      System.out.printf(
          "%s avg rating %.1f | avg matches %.1f%n",
          bucket.name(), summary.averageRating(), summary.averageMatches());
    }
    for (StandingRow row : report.standings()) {
      String displayName =
          switch (row.player().style()) {
            case SHARK -> row.player().user().getNickName() + "*";
            case STREAK_CHASER -> row.player().user().getNickName() + "!";
            case NORMAL -> row.player().user().getNickName();
          };
      System.out.printf(
          "%2d. %-3s %-6s %-11s rating=%4d (%+d) record=%d-%d matches=%d skill=%.2f%n",
          row.rank(),
          displayName,
          row.player().bucket().name(),
          row.player().activity().name(),
          row.rating(),
          row.points(),
          row.wins(),
          row.losses(),
          row.matchesPlayed(),
          row.player().skill());
    }
  }

  private Set<Long> recentContacts(SimPlayer anchor, List<Match> history, int limit) {
    Set<Long> contacts = new HashSet<>();
    int countedMatches = 0;
    for (Match match : history) {
      if (countedMatches >= limit) {
        break;
      }
      if (!includesPlayer(match, anchor.user())) {
        continue;
      }
      countedMatches++;
      collectOtherPlayerIds(match, anchor.user(), contacts);
    }
    return contacts;
  }

  private boolean includesPlayer(Match match, User user) {
    if (match == null || user == null || user.getId() == null) {
      return false;
    }
    Long userId = user.getId();
    return sameUser(match.getA1(), userId)
        || sameUser(match.getA2(), userId)
        || sameUser(match.getB1(), userId)
        || sameUser(match.getB2(), userId);
  }

  private void collectOtherPlayerIds(Match match, User self, Set<Long> sink) {
    addOther(match.getA1(), self, sink);
    addOther(match.getA2(), self, sink);
    addOther(match.getB1(), self, sink);
    addOther(match.getB2(), self, sink);
  }

  private void addOther(User candidate, User self, Set<Long> sink) {
    if (candidate == null || candidate.getId() == null || self == null || self.getId() == null) {
      return;
    }
    if (!candidate.getId().equals(self.getId())) {
      sink.add(candidate.getId());
    }
  }

  private boolean sameUser(User candidate, Long userId) {
    return candidate != null && candidate.getId() != null && candidate.getId().equals(userId);
  }

  private SimPlayer weightedPick(
      List<SimPlayer> candidates, ToDoubleFunction<SimPlayer> weightFn, Random random) {
    double total = 0.0d;
    for (SimPlayer candidate : candidates) {
      total += Math.max(0.0d, weightFn.applyAsDouble(candidate));
    }
    if (total <= 0.0d) {
      return candidates.get(random.nextInt(candidates.size()));
    }

    double target = random.nextDouble() * total;
    double running = 0.0d;
    for (SimPlayer candidate : candidates) {
      running += Math.max(0.0d, weightFn.applyAsDouble(candidate));
      if (running >= target) {
        return candidate;
      }
    }
    return candidates.get(candidates.size() - 1);
  }

  private long resolveLong(String propertyName, long defaultValue) {
    String raw = System.getProperty(propertyName);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return Long.parseLong(raw.trim());
  }

  private int resolveInt(String propertyName, int defaultValue) {
    String raw = System.getProperty(propertyName);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return Integer.parseInt(raw.trim());
  }

  private enum Scenario {
    RANDOM_MATCHMAKING("Pure random matchmaking") {
      @Override
      Lineup selectLineup(
          List<SimPlayer> roster,
          Map<Long, Integer> ratingPoints,
          List<Match> history,
          Random random) {
        List<SimPlayer> pool = new ArrayList<>(roster);
        List<SimPlayer> selected = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
          SimPlayer picked = weightedPick(pool, player -> player.activity().weight(), random);
          selected.add(picked);
          pool.remove(picked);
        }
        Collections.shuffle(selected, random);
        return new Lineup(selected.get(0), selected.get(1), selected.get(2), selected.get(3));
      }
    },
    GAMESMANSHIP_AND_SELF_SORTING("Gamesmanship + self-sorting") {
      @Override
      Lineup selectLineup(
          List<SimPlayer> roster,
          Map<Long, Integer> ratingPoints,
          List<Match> history,
          Random random) {
        List<SimPlayer> pool = new ArrayList<>(roster);
        SimPlayer anchor =
            weightedPick(
                pool, player -> player.activity().weight() * initiatorBias(player), random);
        pool.remove(anchor);

        Set<Long> recentContacts = recentContacts(anchor, history, 5);
        SimPlayer partner =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * partnerPreference(anchor, candidate, recentContacts),
                random);
        pool.remove(partner);

        SimPlayer opponentOne =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * opponentPreference(anchor, candidate, recentContacts),
                random);
        pool.remove(opponentOne);

        SimPlayer opponentTwo =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * opponentPreference(anchor, candidate, recentContacts),
                random);
        return new Lineup(anchor, partner, opponentOne, opponentTwo);
      }

      private double initiatorBias(SimPlayer player) {
        double base =
            switch (player.bucket()) {
              case HIGH -> 1.15d;
              case MEDIUM -> 1.00d;
              case LOW -> 0.85d;
            };
        return player.style() == PlayStyle.SHARK ? base * 1.55d : base;
      }

      private double partnerPreference(
          SimPlayer anchor, SimPlayer candidate, Set<Long> recentContacts) {
        double weight =
            switch (anchor.bucket()) {
              case HIGH ->
                  switch (candidate.bucket()) {
                    case HIGH -> 1.60d;
                    case MEDIUM -> 1.15d;
                    case LOW -> 0.45d;
                  };
              case MEDIUM ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.95d;
                    case MEDIUM -> 1.40d;
                    case LOW -> 0.85d;
                  };
              case LOW ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.25d;
                    case MEDIUM -> 1.00d;
                    case LOW -> 1.55d;
                  };
            };
        if (anchor.style() == PlayStyle.SHARK) {
          weight *=
              switch (candidate.bucket()) {
                case HIGH -> 1.30d;
                case MEDIUM -> 1.05d;
                case LOW -> 0.30d;
              };
        }
        return weight * varietyWeight(candidate, recentContacts);
      }

      private double opponentPreference(
          SimPlayer anchor, SimPlayer candidate, Set<Long> recentContacts) {
        double weight =
            switch (anchor.bucket()) {
              case HIGH ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.30d;
                    case MEDIUM -> 1.05d;
                    case LOW -> 1.70d;
                  };
              case MEDIUM ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.40d;
                    case MEDIUM -> 1.00d;
                    case LOW -> 1.35d;
                  };
              case LOW ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.15d;
                    case MEDIUM -> 1.10d;
                    case LOW -> 1.45d;
                  };
            };
        if (anchor.style() == PlayStyle.SHARK) {
          weight *=
              switch (candidate.bucket()) {
                case HIGH -> 0.40d;
                case MEDIUM -> 1.10d;
                case LOW -> 1.75d;
              };
        }
        return weight * varietyWeight(candidate, recentContacts);
      }

      private double varietyWeight(SimPlayer candidate, Set<Long> recentContacts) {
        return recentContacts.contains(candidate.user().getId()) ? 0.60d : 1.30d;
      }
    },
    GAMESMANSHIP_WITH_BLOWOUT_REFUSAL("Gamesmanship + self-sorting + blowout refusal") {
      @Override
      Lineup selectLineup(
          List<SimPlayer> roster,
          Map<Long, Integer> ratingPoints,
          List<Match> history,
          Random random) {
        List<SimPlayer> pool = new ArrayList<>(roster);
        SimPlayer anchor =
            weightedPick(
                pool, player -> player.activity().weight() * initiatorBias(player), random);
        pool.remove(anchor);

        Set<Long> recentContacts = recentContacts(anchor, history, 5);
        SimPlayer partner =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * partnerPreference(anchor, candidate, recentContacts)
                        * refusalWeight(anchor, candidate, history),
                random);
        pool.remove(partner);

        SimPlayer opponentOne =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * opponentPreference(anchor, candidate, recentContacts)
                        * refusalWeight(anchor, candidate, history),
                random);
        pool.remove(opponentOne);

        SimPlayer opponentTwo =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * opponentPreference(anchor, candidate, recentContacts)
                        * refusalWeight(anchor, candidate, history),
                random);
        return new Lineup(anchor, partner, opponentOne, opponentTwo);
      }

      private double initiatorBias(SimPlayer player) {
        double base =
            switch (player.bucket()) {
              case HIGH -> 1.15d;
              case MEDIUM -> 1.00d;
              case LOW -> 0.85d;
            };
        return player.style() == PlayStyle.SHARK ? base * 1.55d : base;
      }

      private double partnerPreference(
          SimPlayer anchor, SimPlayer candidate, Set<Long> recentContacts) {
        double weight =
            switch (anchor.bucket()) {
              case HIGH ->
                  switch (candidate.bucket()) {
                    case HIGH -> 1.60d;
                    case MEDIUM -> 1.15d;
                    case LOW -> 0.45d;
                  };
              case MEDIUM ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.95d;
                    case MEDIUM -> 1.40d;
                    case LOW -> 0.85d;
                  };
              case LOW ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.25d;
                    case MEDIUM -> 1.00d;
                    case LOW -> 1.55d;
                  };
            };
        if (anchor.style() == PlayStyle.SHARK) {
          weight *=
              switch (candidate.bucket()) {
                case HIGH -> 1.30d;
                case MEDIUM -> 1.05d;
                case LOW -> 0.30d;
              };
        }
        return weight * varietyWeight(candidate, recentContacts);
      }

      private double opponentPreference(
          SimPlayer anchor, SimPlayer candidate, Set<Long> recentContacts) {
        double weight =
            switch (anchor.bucket()) {
              case HIGH ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.30d;
                    case MEDIUM -> 1.05d;
                    case LOW -> 1.70d;
                  };
              case MEDIUM ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.40d;
                    case MEDIUM -> 1.00d;
                    case LOW -> 1.35d;
                  };
              case LOW ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.15d;
                    case MEDIUM -> 1.10d;
                    case LOW -> 1.45d;
                  };
            };
        if (anchor.style() == PlayStyle.SHARK) {
          weight *=
              switch (candidate.bucket()) {
                case HIGH -> 0.40d;
                case MEDIUM -> 1.10d;
                case LOW -> 1.75d;
              };
        }
        return weight * varietyWeight(candidate, recentContacts);
      }

      private double varietyWeight(SimPlayer candidate, Set<Long> recentContacts) {
        return recentContacts.contains(candidate.user().getId()) ? 0.60d : 1.30d;
      }

      private double refusalWeight(SimPlayer anchor, SimPlayer candidate, List<Match> history) {
        if (!isClearlyStronger(anchor, candidate)) {
          return 1.0d;
        }

        MatchupExposure exposure = sharedExposure(anchor, candidate, history, 6);
        if (exposure.blowoutLossesToAnchor() >= 2) {
          return 0.08d;
        }
        if (exposure.blowoutLossesToAnchor() >= 1 && exposure.lossesToAnchor() >= 2) {
          return 0.25d;
        }
        if (exposure.lossesToAnchor() >= 2) {
          return 0.55d;
        }
        return 1.0d;
      }

      private boolean isClearlyStronger(SimPlayer anchor, SimPlayer candidate) {
        return anchor.skill() >= candidate.skill() + 0.18d;
      }
    },
    REC_PLAY_SKILL_AFFINITY("Observed rec play: strong players seek strong groups") {
      @Override
      Lineup selectLineup(
          List<SimPlayer> roster,
          Map<Long, Integer> ratingPoints,
          List<Match> history,
          Random random) {
        List<SimPlayer> pool = new ArrayList<>(roster);
        SimPlayer anchor =
            weightedPick(
                pool, player -> player.activity().weight() * initiatorBias(player), random);
        pool.remove(anchor);

        Set<Long> recentContacts = recentContacts(anchor, history, 5);
        SimPlayer partner =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * partnerPreference(anchor, candidate)
                        * varietyWeight(candidate, recentContacts),
                random);
        pool.remove(partner);

        SimPlayer opponentOne =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * opponentPreference(anchor, candidate)
                        * varietyWeight(candidate, recentContacts),
                random);
        pool.remove(opponentOne);

        SimPlayer opponentTwo =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * opponentPreference(anchor, candidate)
                        * varietyWeight(candidate, recentContacts),
                random);
        return new Lineup(anchor, partner, opponentOne, opponentTwo);
      }

      private double initiatorBias(SimPlayer player) {
        return switch (player.bucket()) {
          case HIGH -> 1.15d;
          case MEDIUM -> 1.00d;
          case LOW -> 0.90d;
        };
      }

      private double partnerPreference(SimPlayer anchor, SimPlayer candidate) {
        return switch (anchor.bucket()) {
          case HIGH ->
              switch (candidate.bucket()) {
                case HIGH -> 1.80d;
                case MEDIUM -> 0.90d;
                case LOW -> 0.12d;
              };
          case MEDIUM ->
              switch (candidate.bucket()) {
                case HIGH -> 0.95d;
                case MEDIUM -> 1.50d;
                case LOW -> 0.70d;
              };
          case LOW ->
              switch (candidate.bucket()) {
                case HIGH -> 0.10d;
                case MEDIUM -> 1.05d;
                case LOW -> 1.50d;
              };
        };
      }

      private double opponentPreference(SimPlayer anchor, SimPlayer candidate) {
        return switch (anchor.bucket()) {
          case HIGH ->
              switch (candidate.bucket()) {
                case HIGH -> 1.55d;
                case MEDIUM -> 0.95d;
                case LOW -> 0.10d;
              };
          case MEDIUM ->
              switch (candidate.bucket()) {
                case HIGH -> 0.95d;
                case MEDIUM -> 1.35d;
                case LOW -> 0.75d;
              };
          case LOW ->
              switch (candidate.bucket()) {
                case HIGH -> 0.08d;
                case MEDIUM -> 1.05d;
                case LOW -> 1.55d;
              };
        };
      }

      private double varietyWeight(SimPlayer candidate, Set<Long> recentContacts) {
        return recentContacts.contains(candidate.user().getId()) ? 0.75d : 1.15d;
      }
    },
    STREAK_GAMING_BY_BUCKET("Streak gaming: one player per skill bucket hunts safe streaks") {
      @Override
      Lineup selectLineup(
          List<SimPlayer> roster,
          Map<Long, Integer> ratingPoints,
          List<Match> history,
          Random random) {
        List<SimPlayer> pool = new ArrayList<>(roster);
        SimPlayer anchor =
            weightedPick(
                pool,
                player -> player.activity().weight() * initiatorBias(player, history),
                random);
        pool.remove(anchor);

        if (anchor.style() != PlayStyle.STREAK_CHASER) {
          List<SimPlayer> selected = new ArrayList<>(4);
          selected.add(anchor);
          for (int i = 0; i < 3; i++) {
            SimPlayer picked = weightedPick(pool, player -> player.activity().weight(), random);
            selected.add(picked);
            pool.remove(picked);
          }
          Collections.shuffle(selected, random);
          return new Lineup(selected.get(0), selected.get(1), selected.get(2), selected.get(3));
        }

        Set<Long> recentContacts = recentContacts(anchor, history, 5);
        int currentStreak = currentWinStreak(anchor, history);
        SimPlayer partner =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * partnerPreference(
                            anchor, candidate, ratingPoints, recentContacts, currentStreak)
                        * confidenceWeight(anchor, candidate, history, true),
                random);
        pool.remove(partner);

        SimPlayer opponentOne =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * opponentPreference(
                            anchor, candidate, ratingPoints, recentContacts, currentStreak)
                        * confidenceWeight(anchor, candidate, history, false),
                random);
        pool.remove(opponentOne);

        SimPlayer opponentTwo =
            weightedPick(
                pool,
                candidate ->
                    candidate.activity().weight()
                        * opponentPreference(
                            anchor, candidate, ratingPoints, recentContacts, currentStreak)
                        * confidenceWeight(anchor, candidate, history, false),
                random);
        return new Lineup(anchor, partner, opponentOne, opponentTwo);
      }

      private double initiatorBias(SimPlayer player, List<Match> history) {
        double base =
            switch (player.bucket()) {
              case HIGH -> 1.05d;
              case MEDIUM -> 1.00d;
              case LOW -> 0.95d;
            };
        if (player.style() != PlayStyle.STREAK_CHASER) {
          return base;
        }
        int currentStreak = currentWinStreak(player, history);
        return base * (1.75d + Math.min(currentStreak, 5) * 0.20d);
      }

      private double partnerPreference(
          SimPlayer anchor,
          SimPlayer candidate,
          Map<Long, Integer> ratingPoints,
          Set<Long> recentContacts,
          int currentStreak) {
        double weight =
            switch (anchor.bucket()) {
              case HIGH ->
                  switch (candidate.bucket()) {
                    case HIGH -> 1.70d;
                    case MEDIUM -> 1.15d;
                    case LOW -> 0.35d;
                  };
              case MEDIUM ->
                  switch (candidate.bucket()) {
                    case HIGH -> 1.90d;
                    case MEDIUM -> 1.00d;
                    case LOW -> 0.40d;
                  };
              case LOW ->
                  switch (candidate.bucket()) {
                    case HIGH -> 2.10d;
                    case MEDIUM -> 1.30d;
                    case LOW -> 0.50d;
                  };
            };
        return weight
            * perceivedStrengthWeight(candidate, ratingPoints)
            * repeatComfortWeight(candidate, recentContacts)
            * streakPressure(currentStreak);
      }

      private double opponentPreference(
          SimPlayer anchor,
          SimPlayer candidate,
          Map<Long, Integer> ratingPoints,
          Set<Long> recentContacts,
          int currentStreak) {
        double weight =
            switch (anchor.bucket()) {
              case HIGH ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.18d;
                    case MEDIUM -> 0.95d;
                    case LOW -> 2.00d;
                  };
              case MEDIUM ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.20d;
                    case MEDIUM -> 0.90d;
                    case LOW -> 1.85d;
                  };
              case LOW ->
                  switch (candidate.bucket()) {
                    case HIGH -> 0.10d;
                    case MEDIUM -> 0.95d;
                    case LOW -> 1.60d;
                  };
            };
        return weight
            * perceivedWeaknessWeight(candidate, ratingPoints)
            * repeatComfortWeight(candidate, recentContacts)
            * streakPressure(currentStreak);
      }

      private double perceivedStrengthWeight(SimPlayer candidate, Map<Long, Integer> ratingPoints) {
        int points = ratingPoints.getOrDefault(candidate.user().getId(), 0);
        return 1.0d + Math.max(-0.20d, Math.min(0.45d, points / 250.0d));
      }

      private double perceivedWeaknessWeight(SimPlayer candidate, Map<Long, Integer> ratingPoints) {
        int points = ratingPoints.getOrDefault(candidate.user().getId(), 0);
        return 1.0d + Math.max(-0.15d, Math.min(0.55d, (-points) / 220.0d));
      }

      private double repeatComfortWeight(SimPlayer candidate, Set<Long> recentContacts) {
        return recentContacts.contains(candidate.user().getId()) ? 1.25d : 0.90d;
      }

      private double streakPressure(int currentStreak) {
        return 1.0d + Math.min(currentStreak, 5) * 0.06d;
      }

      private double confidenceWeight(
          SimPlayer anchor, SimPlayer candidate, List<Match> history, boolean partner) {
        MatchupExposure anchorLosses = sharedExposure(candidate, anchor, history, 6);
        MatchupExposure anchorWins = sharedExposure(anchor, candidate, history, 6);
        if (partner) {
          if (anchorWins.lossesToAnchor() >= 2) {
            return 1.15d;
          }
          if (anchorLosses.lossesToAnchor() >= 1) {
            return 0.80d;
          }
          return 1.0d;
        }

        if (anchorLosses.lossesToAnchor() >= 2) {
          return 0.35d;
        }
        if (anchorLosses.lossesToAnchor() >= 1) {
          return 0.60d;
        }
        if (anchorWins.blowoutLossesToAnchor() >= 1) {
          return 1.20d;
        }
        return 1.0d;
      }
    };

    private final String label;

    Scenario(String label) {
      this.label = label;
    }

    abstract Lineup selectLineup(
        List<SimPlayer> roster,
        Map<Long, Integer> ratingPoints,
        List<Match> history,
        Random random);

    String label() {
      return label;
    }

    SimPlayer weightedPick(
        List<SimPlayer> candidates, ToDoubleFunction<SimPlayer> weightFn, Random random) {
      double total = 0.0d;
      for (SimPlayer candidate : candidates) {
        total += Math.max(0.0d, weightFn.applyAsDouble(candidate));
      }
      if (total <= 0.0d) {
        return candidates.get(random.nextInt(candidates.size()));
      }

      double target = random.nextDouble() * total;
      double running = 0.0d;
      for (SimPlayer candidate : candidates) {
        running += Math.max(0.0d, weightFn.applyAsDouble(candidate));
        if (running >= target) {
          return candidate;
        }
      }
      return candidates.get(candidates.size() - 1);
    }

    Set<Long> recentContacts(SimPlayer anchor, List<Match> history, int limit) {
      Set<Long> contacts = new HashSet<>();
      int countedMatches = 0;
      for (Match match : history) {
        if (countedMatches >= limit) {
          break;
        }
        if (!includesPlayer(match, anchor.user())) {
          continue;
        }
        countedMatches++;
        collectOtherPlayerIds(match, anchor.user(), contacts);
      }
      return contacts;
    }

    boolean includesPlayer(Match match, User user) {
      if (match == null || user == null || user.getId() == null) {
        return false;
      }
      Long userId = user.getId();
      return sameUser(match.getA1(), userId)
          || sameUser(match.getA2(), userId)
          || sameUser(match.getB1(), userId)
          || sameUser(match.getB2(), userId);
    }

    void collectOtherPlayerIds(Match match, User self, Set<Long> sink) {
      addOther(match.getA1(), self, sink);
      addOther(match.getA2(), self, sink);
      addOther(match.getB1(), self, sink);
      addOther(match.getB2(), self, sink);
    }

    MatchupExposure sharedExposure(
        SimPlayer anchor, SimPlayer candidate, List<Match> history, int limit) {
      int sharedMatches = 0;
      int lossesToAnchor = 0;
      int blowoutLossesToAnchor = 0;

      for (Match match : history) {
        if (sharedMatches >= limit) {
          break;
        }
        if (!includesPlayer(match, anchor.user()) || !includesPlayer(match, candidate.user())) {
          continue;
        }
        sharedMatches++;

        Team anchorTeam = teamFor(match, anchor.user());
        Team candidateTeam = teamFor(match, candidate.user());
        if (anchorTeam == null || candidateTeam == null || anchorTeam == candidateTeam) {
          continue;
        }

        boolean anchorWon = didPlayerWin(match, anchor.user());
        boolean candidateLost = !didPlayerWin(match, candidate.user());
        if (anchorWon && candidateLost) {
          lossesToAnchor++;
          if (isBlowout(match)) {
            blowoutLossesToAnchor++;
          }
        }
      }

      return new MatchupExposure(sharedMatches, lossesToAnchor, blowoutLossesToAnchor);
    }

    void addOther(User candidate, User self, Set<Long> sink) {
      if (candidate == null || candidate.getId() == null || self == null || self.getId() == null) {
        return;
      }
      if (!candidate.getId().equals(self.getId())) {
        sink.add(candidate.getId());
      }
    }

    boolean sameUser(User candidate, Long userId) {
      return candidate != null && candidate.getId() != null && candidate.getId().equals(userId);
    }

    Team teamFor(Match match, User user) {
      if (match == null || user == null || user.getId() == null) {
        return null;
      }
      Long userId = user.getId();
      if (sameUser(match.getA1(), userId) || sameUser(match.getA2(), userId)) {
        return Team.A;
      }
      if (sameUser(match.getB1(), userId) || sameUser(match.getB2(), userId)) {
        return Team.B;
      }
      return null;
    }

    boolean didPlayerWin(Match match, User user) {
      Team team = teamFor(match, user);
      if (team == null || match == null) {
        return false;
      }
      return team == Team.A ? match.isTeamAWinner() : !match.isTeamAWinner();
    }

    boolean isBlowout(Match match) {
      if (match == null) {
        return false;
      }
      int totalPoints = Math.max(0, match.getScoreA()) + Math.max(0, match.getScoreB());
      if (totalPoints <= 0) {
        return false;
      }
      double marginShare = Math.abs(match.getScoreA() - match.getScoreB()) / (double) totalPoints;
      return marginShare >= 0.20d;
    }

    int currentWinStreak(SimPlayer player, List<Match> history) {
      int streak = 0;
      for (Match match : history) {
        if (!includesPlayer(match, player.user())) {
          continue;
        }
        if (!didPlayerWin(match, player.user())) {
          break;
        }
        streak++;
      }
      return streak;
    }
  }

  private enum SkillBucket {
    HIGH(0.84d),
    MEDIUM(0.57d),
    LOW(0.30d);

    private final double baseSkill;

    SkillBucket(double baseSkill) {
      this.baseSkill = baseSkill;
    }

    double baseSkill() {
      return baseSkill;
    }
  }

  private enum ActivityProfile {
    ACTIVE(1.00d),
    SEMI_ACTIVE(0.70d),
    RARE(0.40d);

    private final double weight;

    ActivityProfile(double weight) {
      this.weight = weight;
    }

    double weight() {
      return weight;
    }
  }

  private enum PlayStyle {
    NORMAL,
    SHARK,
    STREAK_CHASER
  }

  private record SimPlayer(
      User user, SkillBucket bucket, ActivityProfile activity, double skill, PlayStyle style) {}

  private record Lineup(SimPlayer a1, SimPlayer a2, SimPlayer b1, SimPlayer b2) {}

  private record Scoreline(int scoreA, int scoreB) {}

  private enum Team {
    A,
    B
  }

  private record MatchupExposure(
      int sharedMatches, int lossesToAnchor, int blowoutLossesToAnchor) {}

  private record StandingRow(
      SimPlayer player, int rating, int points, int matchesPlayed, int wins, int losses, int rank) {
    StandingRow(SimPlayer player, int rating, int points, int matchesPlayed, int wins, int losses) {
      this(player, rating, points, matchesPlayed, wins, losses, 0);
    }

    StandingRow withRank(int rank) {
      return new StandingRow(player, rating, points, matchesPlayed, wins, losses, rank);
    }
  }

  private record BucketSummary(double averageRating, double averageMatches) {}

  private record SimulationReport(
      Scenario scenario,
      long rosterSeed,
      long matchSeed,
      int matchCount,
      List<StandingRow> standings,
      Map<SkillBucket, BucketSummary> bucketSummary) {
    double averageRating(SkillBucket bucket) {
      return bucketSummary.get(bucket).averageRating();
    }

    int ratingSpread() {
      int max = standings.stream().mapToInt(StandingRow::rating).max().orElse(BASELINE_RATING);
      int min = standings.stream().mapToInt(StandingRow::rating).min().orElse(BASELINE_RATING);
      return max - min;
    }
  }
}
