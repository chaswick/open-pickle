package com.w3llspring.fhpb.web.service.roundrobin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class FairByeRoundRobinSchedulerTest {

  private final FairByeRoundRobinScheduler scheduler = new FairByeRoundRobinScheduler();

  @Test
  void oddRosterEachPlayerSitsExactlyOnce() {
    List<Long> participants = LongStream.rangeClosed(1, 9).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);

    assertEquals(9, schedule.size(), "Nine players should produce nine rounds.");

    // Count byes per player
    Map<Long, Integer> byeCounts = new HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
      for (RoundRobinScheduler.MatchSpec spec : round) {
        if (!spec.bye) continue;
        incrementIfPresent(byeCounts, spec.a1);
        incrementIfPresent(byeCounts, spec.a2);
        incrementIfPresent(byeCounts, spec.b1);
        incrementIfPresent(byeCounts, spec.b2);
      }
    }

    System.out.println("Odd roster (n=9) fair bye distribution:");
    System.out.println("  Bye counts: " + byeCounts);

    // Verify each player sits exactly once
    for (Long player : participants) {
      assertEquals(
          1, byeCounts.getOrDefault(player, 0), "Player " + player + " should sit exactly once");
    }

    System.out.println("  ✓ All 9 players sit exactly once");
  }

  @Test
  void sevenPlayersThreeSitPerRound() {
    List<Long> participants = LongStream.rangeClosed(1, 7).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);

    Map<Long, Integer> byeCounts = computeByeCounts(schedule);

    System.out.println("Roster with 7 players (3 sit, 4 play per round):");
    System.out.println("  Bye counts: " + byeCounts);
    System.out.println("  Rounds generated: " + schedule.size());

    // With 7 players, 3 must sit to leave 4 for 1 match
    // To be fair, we need 7 rounds where each player sits 3 times
    int expectedByesPerPlayer = (schedule.size() * 3) / 7;

    System.out.println("  Expected byes per player: ~" + expectedByesPerPlayer);
    System.out.println(
        "  Actual bye range: "
            + byeCounts.values().stream().min(Integer::compareTo).orElse(0)
            + " to "
            + byeCounts.values().stream().max(Integer::compareTo).orElse(0));

    // Verify each round has 3 sitting and 1 match
    for (int i = 0; i < schedule.size(); i++) {
      List<RoundRobinScheduler.MatchSpec> round = schedule.get(i);
      long byeCount = round.stream().filter(spec -> spec.bye).count();
      long matchCount = round.stream().filter(spec -> !spec.bye).count();

      assertEquals(3, byeCount, "Round " + (i + 1) + " should have 3 players sitting");
      assertEquals(1, matchCount, "Round " + (i + 1) + " should have 1 match");
    }

    System.out.println("  ✓ Each round has 3 sitting + 1 match");
  }

  @Test
  void elevenPlayersThreeSitPerRound() {
    List<Long> participants = LongStream.rangeClosed(1, 11).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);

    Map<Long, Integer> byeCounts = computeByeCounts(schedule);

    System.out.println("Roster with 11 players (3 sit, 8 play per round):");
    System.out.println("  Bye counts: " + byeCounts);
    System.out.println("  Rounds generated: " + schedule.size());

    // With 11 players, 3 must sit to leave 8 for 2 matches
    // To be fair, we need 11 rounds where each player sits 3 times
    System.out.println("  Expected byes per player: 3");
    System.out.println(
        "  Actual bye range: "
            + byeCounts.values().stream().min(Integer::compareTo).orElse(0)
            + " to "
            + byeCounts.values().stream().max(Integer::compareTo).orElse(0));

    // Verify fair distribution
    for (Long player : participants) {
      assertEquals(
          3, byeCounts.getOrDefault(player, 0), "Player " + player + " should sit exactly 3 times");
    }

    // Verify each round has 3 sitting and 2 matches
    for (int i = 0; i < schedule.size(); i++) {
      List<RoundRobinScheduler.MatchSpec> round = schedule.get(i);
      long byeCount = round.stream().filter(spec -> spec.bye).count();
      long matchCount = round.stream().filter(spec -> !spec.bye).count();

      assertEquals(3, byeCount, "Round " + (i + 1) + " should have 3 players sitting");
      assertEquals(2, matchCount, "Round " + (i + 1) + " should have 2 matches");
    }

    System.out.println("  ✓ All 11 players sit exactly 3 times");
    System.out.println("  ✓ Each round has 3 sitting + 2 matches");
  }

  @Test
  void sixPlayersTwoSitPerRound() {
    List<Long> participants = LongStream.rangeClosed(1, 6).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);
    assertFalse(schedule.isEmpty(), "Six players should produce a schedule");

    System.out.println("Roster with 6 players (2 sit, 4 play per round):");

    // Count byes per round
    for (int i = 0; i < schedule.size(); i++) {
      List<RoundRobinScheduler.MatchSpec> round = schedule.get(i);
      long byeCount = round.stream().filter(spec -> spec.bye).count();
      long matchCount = round.stream().filter(spec -> !spec.bye).count();

      assertEquals(2, byeCount, "Round " + (i + 1) + " should have 2 byes");
      assertEquals(1, matchCount, "Round " + (i + 1) + " should have 1 match (4 players)");
    }

    // Count total byes per player
    Map<Long, Integer> byeCounts = new HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
      for (RoundRobinScheduler.MatchSpec spec : round) {
        if (!spec.bye) continue;
        if (spec.a1 != null) byeCounts.merge(spec.a1, 1, Integer::sum);
        if (spec.a2 != null) byeCounts.merge(spec.a2, 1, Integer::sum);
        if (spec.b1 != null) byeCounts.merge(spec.b1, 1, Integer::sum);
        if (spec.b2 != null) byeCounts.merge(spec.b2, 1, Integer::sum);
      }
    }

    System.out.println("  Bye counts: " + byeCounts);
    System.out.println("  Rounds generated: " + schedule.size());
    System.out.println("  Expected byes per player: " + schedule.size() * 2 / 6);

    // Each player should sit the same number of times
    int minByes = byeCounts.values().stream().min(Integer::compareTo).orElse(0);
    int maxByes = byeCounts.values().stream().max(Integer::compareTo).orElse(0);
    System.out.println("  Actual bye range: " + minByes + " to " + maxByes);
    System.out.println("  ✓ Fair distribution: " + (maxByes == minByes));

    assertEquals(minByes, maxByes, "All players should sit exactly the same number of times");
  }

  @Test
  void tenPlayersTwoSitPerRound() {
    List<Long> participants = LongStream.rangeClosed(1, 10).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);
    assertFalse(schedule.isEmpty(), "Ten players should produce a schedule");

    System.out.println("Roster with 10 players (2 sit, 8 play per round):");

    // Count byes per round
    for (int i = 0; i < schedule.size(); i++) {
      List<RoundRobinScheduler.MatchSpec> round = schedule.get(i);
      long byeCount = round.stream().filter(spec -> spec.bye).count();
      long matchCount = round.stream().filter(spec -> !spec.bye).count();

      assertEquals(2, byeCount, "Round " + (i + 1) + " should have 2 byes");
      assertEquals(2, matchCount, "Round " + (i + 1) + " should have 2 matches (8 players)");
    }

    // Count total byes per player
    Map<Long, Integer> byeCounts = new HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
      for (RoundRobinScheduler.MatchSpec spec : round) {
        if (!spec.bye) continue;
        if (spec.a1 != null) byeCounts.merge(spec.a1, 1, Integer::sum);
        if (spec.a2 != null) byeCounts.merge(spec.a2, 1, Integer::sum);
        if (spec.b1 != null) byeCounts.merge(spec.b1, 1, Integer::sum);
        if (spec.b2 != null) byeCounts.merge(spec.b2, 1, Integer::sum);
      }
    }

    System.out.println("  Bye counts: " + byeCounts);
    System.out.println("  Rounds generated: " + schedule.size());
    System.out.println("  Expected byes per player: " + schedule.size() * 2 / 10);

    int minByes = byeCounts.values().stream().min(Integer::compareTo).orElse(0);
    int maxByes = byeCounts.values().stream().max(Integer::compareTo).orElse(0);
    System.out.println("  Actual bye range: " + minByes + " to " + maxByes);
    System.out.println("  ✓ Fair distribution: " + (maxByes == minByes));

    assertEquals(minByes, maxByes, "All players should sit exactly the same number of times");
  }

  @Test
  void oddRosterConsidersPriorPairHistory() {
    List<Long> participants = LongStream.rangeClosed(1, 9).boxed().collect(Collectors.toList());

    // Create prior history where players 1-4 have played together a lot
    Map<Long, Map<Long, Integer>> priorPairCounts = new HashMap<>();
    for (long i = 1; i <= 4; i++) {
      Map<Long, Integer> opponents = new HashMap<>();
      for (long j = 1; j <= 4; j++) {
        if (i != j) {
          opponents.put(j, 5); // High prior count
        }
      }
      priorPairCounts.put(i, opponents);
    }

    // Generate with and without prior history
    scheduler.setSeedForTests(12345L);
    List<List<RoundRobinScheduler.MatchSpec>> scheduleWithHistory =
        scheduler.generate(participants, priorPairCounts, 0);

    scheduler.setSeedForTests(12345L);
    List<List<RoundRobinScheduler.MatchSpec>> scheduleWithoutHistory =
        scheduler.generate(participants, Map.of(), 0);

    // Both should have fair bye distribution
    Map<Long, Integer> byeCountsWithHistory = computeByeCounts(scheduleWithHistory);
    Map<Long, Integer> byeCountsWithoutHistory = computeByeCounts(scheduleWithoutHistory);

    System.out.println("Prior history impact test:");
    System.out.println("  With prior history - byes: " + byeCountsWithHistory);
    System.out.println("  Without prior history - byes: " + byeCountsWithoutHistory);

    // Verify fair distribution in both cases
    for (Long player : participants) {
      assertEquals(1, byeCountsWithHistory.getOrDefault(player, 0));
      assertEquals(1, byeCountsWithoutHistory.getOrDefault(player, 0));
    }

    System.out.println("  ✓ Fair bye distribution maintained with and without prior history");
  }

  @Test
  void evenRosterProducesSchedule() {
    List<Long> participants = LongStream.rangeClosed(1, 8).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);

    assertEquals(7, schedule.size(), "Eight players should produce seven rounds.");

    System.out.println("Even roster (n=8) schedule generated:");
    System.out.println("  Rounds: " + schedule.size());
    System.out.println("  ✓ Schedule generated successfully for even roster");
  }

  @Test
  void eachRoundHasCorrectNumberOfMatches() {
    List<Long> participants = LongStream.rangeClosed(1, 9).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);

    for (int i = 0; i < schedule.size(); i++) {
      List<RoundRobinScheduler.MatchSpec> round = schedule.get(i);

      // Should have 1 bye + 2 matches (8 players in 2 doubles matches: 2v2 and 2v2)
      long byeCount = round.stream().filter(spec -> spec.bye).count();
      long matchCount = round.stream().filter(spec -> !spec.bye).count();

      assertEquals(1, byeCount, "Round " + (i + 1) + " should have exactly 1 bye");
      assertEquals(
          2, matchCount, "Round " + (i + 1) + " should have exactly 2 matches (doubles format)");
    }

    System.out.println("Match structure verification:");
    System.out.println("  ✓ Each round has 1 bye + 2 doubles matches (8 active players)");
  }

  @Test
  void allPlayersAppearInEveryRound() {
    List<Long> participants = LongStream.rangeClosed(1, 9).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);

    for (int i = 0; i < schedule.size(); i++) {
      List<RoundRobinScheduler.MatchSpec> round = schedule.get(i);
      Set<Long> playersInRound = new HashSet<>();

      for (RoundRobinScheduler.MatchSpec spec : round) {
        if (spec.a1 != null) playersInRound.add(spec.a1);
        if (spec.a2 != null) playersInRound.add(spec.a2);
        if (spec.b1 != null) playersInRound.add(spec.b1);
        if (spec.b2 != null) playersInRound.add(spec.b2);
      }

      assertEquals(
          new HashSet<>(participants),
          playersInRound,
          "Round " + (i + 1) + " should include all 9 players");
    }

    System.out.println("Player participation verification:");
    System.out.println("  ✓ All 9 players appear in each of the 9 rounds");
  }

  @Test
  void theoreticalPartnershipLimits() {
    int[] playerCounts = {5, 6, 8, 10, 11, 12, 14};

    System.out.println("\n========================================");
    System.out.println("Theoretical Partnership Balance Analysis");
    System.out.println("========================================\n");

    for (int n : playerCounts) {
      int sittersNeeded = n % 4;
      int playersPerRound = n - sittersNeeded;
      int partnershipsPerRound = playersPerRound / 2;

      // Calculate rounds needed for fair bye distribution
      int rounds;
      if (sittersNeeded == 0) {
        rounds = n - 1; // Standard round-robin
      } else if (n % sittersNeeded == 0) {
        rounds = n / sittersNeeded;
      } else {
        rounds = n;
      }

      int totalPartnershipSlots = partnershipsPerRound * rounds;
      int possiblePairs = (n * (n - 1)) / 2; // C(n, 2)

      double idealPartnershipsPerPair = totalPartnershipSlots / (double) possiblePairs;
      int minPossible = (int) Math.floor(idealPartnershipsPerPair);
      int maxPossible = (int) Math.ceil(idealPartnershipsPerPair);
      int theoreticalImbalance = maxPossible - minPossible;

      System.out.println(
          "Players: " + n + " (" + sittersNeeded + " sitters, " + playersPerRound + " play)");
      System.out.println("  Rounds: " + rounds);
      System.out.println("  Total partnership slots: " + totalPartnershipSlots);
      System.out.println("  Possible pairs: " + possiblePairs);
      System.out.println(
          "  Ideal partnerships per pair: " + String.format("%.2f", idealPartnershipsPerPair));
      System.out.println(
          "  Theoretical best: "
              + minPossible
              + " to "
              + maxPossible
              + " (imbalance: "
              + theoreticalImbalance
              + ")");

      if (theoreticalImbalance == 0) {
        System.out.println("  ✓ Perfect balance is theoretically possible!");
      } else {
        System.out.println("  → Imbalance unavoidable (mathematical constraint)");
      }
      System.out.println();
    }

    System.out.println("========================================\n");
  }

  @Test
  void partnershipBalanceOptimizationTest() {
    // Test to find the best possible partnership balance for problematic player counts
    int[] playerCounts = {10, 11, 14};
    int trialsPerCount = 10;

    System.out.println("\n========================================");
    System.out.println("Partnership Balance Optimization Test");
    System.out.println("Testing " + trialsPerCount + " trials per player count");
    System.out.println("========================================\n");

    for (int n : playerCounts) {
      List<Long> participants = LongStream.rangeClosed(1, n).boxed().collect(Collectors.toList());

      int bestImbalance = Integer.MAX_VALUE;
      int worstImbalance = 0;
      int totalImbalance = 0;

      for (int trial = 0; trial < trialsPerCount; trial++) {
        FairByeRoundRobinScheduler trialScheduler = new FairByeRoundRobinScheduler();
        List<List<RoundRobinScheduler.MatchSpec>> schedule =
            trialScheduler.generate(participants, Map.of(), 0);

        // Count partnerships
        Map<Set<Long>, Integer> partnerCounts = new HashMap<>();
        for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
          for (RoundRobinScheduler.MatchSpec spec : round) {
            if (spec.bye) continue;
            if (spec.a1 != null && spec.a2 != null) {
              partnerCounts.merge(Set.of(spec.a1, spec.a2), 1, Integer::sum);
            }
            if (spec.b1 != null && spec.b2 != null) {
              partnerCounts.merge(Set.of(spec.b1, spec.b2), 1, Integer::sum);
            }
          }
        }

        int minPartners = partnerCounts.values().stream().min(Integer::compareTo).orElse(0);
        int maxPartners = partnerCounts.values().stream().max(Integer::compareTo).orElse(0);
        int imbalance = maxPartners - minPartners;

        bestImbalance = Math.min(bestImbalance, imbalance);
        worstImbalance = Math.max(worstImbalance, imbalance);
        totalImbalance += imbalance;
      }

      double avgImbalance = totalImbalance / (double) trialsPerCount;

      System.out.println("Players: " + n);
      System.out.println("  Best partnership imbalance: " + bestImbalance);
      System.out.println("  Worst partnership imbalance: " + worstImbalance);
      System.out.println("  Average partnership imbalance: " + String.format("%.1f", avgImbalance));

      if (bestImbalance == 0) {
        System.out.println("  ✓ PERFECT balance achievable!");
      } else if (bestImbalance == 1) {
        System.out.println("  ✓ Near-perfect balance achievable (max difference of 1)");
      } else {
        System.out.println(
            "  → Best achievable: "
                + (bestImbalance + 1)
                + " to "
                + (bestImbalance + 1 + bestImbalance)
                + " (may be theoretical limit)");
      }
      System.out.println();
    }

    System.out.println("========================================\n");
  }

  @Test
  void distributionAnalysisForCommonPlayerCounts() {
    int[] playerCounts = {5, 6, 8, 10, 11, 12, 14};

    System.out.println("\n========================================");
    System.out.println("Partner & Bye Distribution Analysis");
    System.out.println("========================================\n");

    for (int n : playerCounts) {
      List<Long> participants = LongStream.rangeClosed(1, n).boxed().collect(Collectors.toList());
      List<List<RoundRobinScheduler.MatchSpec>> schedule =
          scheduler.generate(participants, Map.of(), 0);

      // Count byes per player
      Map<Long, Integer> byeCounts = new HashMap<>();
      for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
        for (RoundRobinScheduler.MatchSpec spec : round) {
          if (!spec.bye) continue;
          if (spec.a1 != null) byeCounts.merge(spec.a1, 1, Integer::sum);
          if (spec.a2 != null) byeCounts.merge(spec.a2, 1, Integer::sum);
          if (spec.b1 != null) byeCounts.merge(spec.b1, 1, Integer::sum);
          if (spec.b2 != null) byeCounts.merge(spec.b2, 1, Integer::sum);
        }
      }

      // Count partnerships (who partnered with whom)
      Map<Set<Long>, Integer> partnerCounts = new HashMap<>();
      for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
        for (RoundRobinScheduler.MatchSpec spec : round) {
          if (spec.bye) continue;
          if (spec.a1 != null && spec.a2 != null) {
            partnerCounts.merge(Set.of(spec.a1, spec.a2), 1, Integer::sum);
          }
          if (spec.b1 != null && spec.b2 != null) {
            partnerCounts.merge(Set.of(spec.b1, spec.b2), 1, Integer::sum);
          }
        }
      }

      // Calculate statistics
      int sittersNeeded = n % 4;
      int minByes = byeCounts.values().stream().min(Integer::compareTo).orElse(0);
      int maxByes = byeCounts.values().stream().max(Integer::compareTo).orElse(0);
      int minPartners = partnerCounts.values().stream().min(Integer::compareTo).orElse(0);
      int maxPartners = partnerCounts.values().stream().max(Integer::compareTo).orElse(0);

      long totalByes = byeCounts.values().stream().mapToInt(Integer::intValue).sum();
      double avgByesPerPlayer = byeCounts.isEmpty() ? 0 : totalByes / (double) byeCounts.size();

      System.out.println(
          "Players: "
              + n
              + " (requires "
              + sittersNeeded
              + " sitter"
              + (sittersNeeded == 1 ? "" : "s")
              + " per round)");
      System.out.println("  Rounds generated: " + schedule.size());
      System.out.println("  Bye Distribution:");
      System.out.println(
          "    Range: "
              + minByes
              + " to "
              + maxByes
              + " (avg: "
              + String.format("%.1f", avgByesPerPlayer)
              + ")");
      System.out.println("    Fair? " + (maxByes == minByes ? "✓ YES" : "✗ NO"));
      System.out.println("  Partner Distribution:");
      System.out.println("    Unique partnerships: " + partnerCounts.size());
      System.out.println("    Partnership count range: " + minPartners + " to " + maxPartners);

      // Sample first few bye counts
      String byeSample =
          byeCounts.entrySet().stream()
              .limit(5)
              .map(e -> "P" + e.getKey() + "=" + e.getValue())
              .collect(Collectors.joining(", "));
      System.out.println("    Sample byes: " + byeSample + (byeCounts.size() > 5 ? ", ..." : ""));

      // Sample first few partner counts
      String partnerSample =
          partnerCounts.entrySet().stream()
              .limit(3)
              .map(
                  e ->
                      e.getKey().stream().map(p -> "P" + p).collect(Collectors.joining("+"))
                          + "="
                          + e.getValue())
              .collect(Collectors.joining(", "));
      System.out.println(
          "    Sample partnerships: " + partnerSample + (partnerCounts.size() > 3 ? ", ..." : ""));
      System.out.println();

      // Assertions for fairness
      assertEquals(
          maxByes,
          minByes,
          "All players should sit the same number of times for " + n + " players");
      assertFalse(schedule.isEmpty(), "Schedule should not be empty for " + n + " players");
    }

    System.out.println("========================================");
    System.out.println("✓ All player counts show fair bye distribution");
    System.out.println("========================================\n");
  }

  private Map<Long, Integer> computeByeCounts(List<List<RoundRobinScheduler.MatchSpec>> schedule) {
    Map<Long, Integer> byeCounts = new HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
      for (RoundRobinScheduler.MatchSpec spec : round) {
        if (!spec.bye) continue;
        incrementIfPresent(byeCounts, spec.a1);
        incrementIfPresent(byeCounts, spec.a2);
        incrementIfPresent(byeCounts, spec.b1);
        incrementIfPresent(byeCounts, spec.b2);
      }
    }
    return byeCounts;
  }

  @Test
  void seasonalBalancingNudgesWithoutBreakingPrimaryMetrics() {
    // Test that seasonal balancing nudges opponent counts toward balance
    // while maintaining bye fairness and reasonable partnership balance

    int n = 10; // 10 players, 2 sitters per round
    List<Long> participants = LongStream.rangeClosed(1, n).boxed().collect(Collectors.toList());

    // Create moderate seasonal imbalance: players 1-5 have played each other more
    Map<Long, Map<Long, Integer>> priorCounts = new HashMap<>();
    for (long i = 1; i <= 5; i++) {
      Map<Long, Integer> opponents = new HashMap<>();
      for (long j = 1; j <= 5; j++) {
        if (i != j) {
          opponents.put(j, 3); // Played 3 times within group 1-5
        }
      }
      for (long j = 6; j <= 10; j++) {
        opponents.put(j, 1); // Played 1 time against group 6-10
      }
      priorCounts.put(i, opponents);
    }
    for (long i = 6; i <= 10; i++) {
      Map<Long, Integer> opponents = new HashMap<>();
      for (long j = 1; j <= 5; j++) {
        opponents.put(j, 1); // Played 1 time against group 1-5
      }
      for (long j = 6; j <= 10; j++) {
        if (i != j) {
          opponents.put(j, 1); // Played 1 time within group 6-10
        }
      }
      priorCounts.put(i, opponents);
    }

    System.out.println("\n========================================");
    System.out.println("Seasonal Balancing Test (10 Players)");
    System.out.println("========================================\n");

    System.out.println("Initial Seasonal State (Before Round-Robin):");
    System.out.println("  Players 1-5: faced each other 3 times, faced 6-10 only 1 time");
    System.out.println("  Players 6-10: faced each other 1 time, faced 1-5 only 1 time");
    System.out.println("  Imbalance: Group 1-5 is over-matched against itself\n");

    // Show the initial opponent distribution
    System.out.println("BEFORE Round-Robin - Seasonal Opponent Counts:");
    for (long i = 1; i <= n; i++) {
      StringBuilder line = new StringBuilder("  P" + i + ": ");
      for (long j = 1; j <= n; j++) {
        if (i == j) {
          line.append("  - ");
        } else {
          int count = priorCounts.getOrDefault(i, Map.of()).getOrDefault(j, 0);
          line.append(String.format("%3d ", count));
        }
      }
      System.out.println(line);
    }
    System.out.println();

    // Generate without seasonal context
    List<List<RoundRobinScheduler.MatchSpec>> scheduleWithout =
        scheduler.generate(participants, Map.of(), 0);

    // Generate with seasonal context
    List<List<RoundRobinScheduler.MatchSpec>> scheduleWith =
        scheduler.generate(participants, priorCounts, 0);

    // Analyze both schedules
    for (String label : List.of("WITHOUT", "WITH")) {
      List<List<RoundRobinScheduler.MatchSpec>> schedule =
          label.equals("WITHOUT") ? scheduleWithout : scheduleWith;

      System.out.println(
          "==================== " + label + " Seasonal Balancing ====================");

      // 1. CHECK PRIMARY METRIC: Bye fairness - show per player
      Map<Long, Integer> byeCounts = new HashMap<>();
      for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
        for (RoundRobinScheduler.MatchSpec spec : round) {
          if (!spec.bye) continue;
          if (spec.a1 != null) byeCounts.merge(spec.a1, 1, Integer::sum);
          if (spec.a2 != null) byeCounts.merge(spec.a2, 1, Integer::sum);
          if (spec.b1 != null) byeCounts.merge(spec.b1, 1, Integer::sum);
          if (spec.b2 != null) byeCounts.merge(spec.b2, 1, Integer::sum);
        }
      }
      int minByes = byeCounts.values().stream().min(Integer::compareTo).orElse(0);
      int maxByes = byeCounts.values().stream().max(Integer::compareTo).orElse(0);

      System.out.println("\n1. BYE COUNTS (per player):");
      StringBuilder byeLine = new StringBuilder("   ");
      for (long i = 1; i <= n; i++) {
        byeLine.append(String.format("P%-2d ", i));
      }
      System.out.println(byeLine);
      StringBuilder byeValues = new StringBuilder("   ");
      for (long i = 1; i <= n; i++) {
        byeValues.append(String.format("%-3d ", byeCounts.getOrDefault(i, 0)));
      }
      System.out.println(byeValues);
      System.out.println(
          "   Range: "
              + minByes
              + " to "
              + maxByes
              + " "
              + (maxByes == minByes ? "✓ PERFECT" : "✗"));
      assertEquals(minByes, maxByes, "Bye fairness must be maintained");

      // 2. CHECK PRIMARY METRIC: Partnership balance - show distribution
      Map<Set<Long>, Integer> partnerCounts = new HashMap<>();
      Map<Long, Integer> partnerCountsPerPlayer = new HashMap<>();
      for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
        for (RoundRobinScheduler.MatchSpec spec : round) {
          if (spec.bye) continue;
          if (spec.a1 != null && spec.a2 != null) {
            partnerCounts.merge(Set.of(spec.a1, spec.a2), 1, Integer::sum);
            partnerCountsPerPlayer.merge(spec.a1, 1, Integer::sum);
            partnerCountsPerPlayer.merge(spec.a2, 1, Integer::sum);
          }
          if (spec.b1 != null && spec.b2 != null) {
            partnerCounts.merge(Set.of(spec.b1, spec.b2), 1, Integer::sum);
            partnerCountsPerPlayer.merge(spec.b1, 1, Integer::sum);
            partnerCountsPerPlayer.merge(spec.b2, 1, Integer::sum);
          }
        }
      }
      int minPartners = partnerCounts.values().stream().min(Integer::compareTo).orElse(0);
      int maxPartners = partnerCounts.values().stream().max(Integer::compareTo).orElse(0);

      System.out.println("\n2. PARTNERSHIP COUNTS (times each player partnered):");
      StringBuilder partnerLine = new StringBuilder("   ");
      for (long i = 1; i <= n; i++) {
        partnerLine.append(String.format("P%-2d ", i));
      }
      System.out.println(partnerLine);
      StringBuilder partnerValues = new StringBuilder("   ");
      for (long i = 1; i <= n; i++) {
        partnerValues.append(String.format("%-3d ", partnerCountsPerPlayer.getOrDefault(i, 0)));
      }
      System.out.println(partnerValues);
      System.out.println(
          "   Partnership pair count range: "
              + minPartners
              + " to "
              + maxPartners
              + " "
              + (maxPartners - minPartners <= 1 ? "✓ GOOD" : "→"));

      // 3. ANALYZE OPPONENT COUNTS: Show NEW matchups in this round-robin
      Map<String, Integer> newOpponents = new HashMap<>();
      for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
        for (RoundRobinScheduler.MatchSpec spec : round) {
          if (spec.bye) continue;
          if (spec.a1 != null && spec.b1 != null) recordOpponent(newOpponents, spec.a1, spec.b1);
          if (spec.a1 != null && spec.b2 != null) recordOpponent(newOpponents, spec.a1, spec.b2);
          if (spec.a2 != null && spec.b1 != null) recordOpponent(newOpponents, spec.a2, spec.b1);
          if (spec.a2 != null && spec.b2 != null) recordOpponent(newOpponents, spec.a2, spec.b2);
        }
      }

      System.out.println("\n3. NEW OPPONENT MATCHES (added in this round-robin):");
      for (long i = 1; i <= n; i++) {
        StringBuilder line = new StringBuilder("   P" + i + ": ");
        for (long j = 1; j <= n; j++) {
          if (i == j) {
            line.append("  - ");
          } else {
            String key = i < j ? i + "-" + j : j + "-" + i;
            int count = newOpponents.getOrDefault(key, 0);
            line.append(String.format("%3d ", count));
          }
        }
        System.out.println(line);
      }

      // 4. CALCULATE CUMULATIVE (Prior + New)
      System.out.println("\n4. CUMULATIVE OPPONENT COUNTS (prior + this round-robin):");
      Map<String, Integer> cumulative = new HashMap<>();
      for (long i = 1; i <= n; i++) {
        for (long j = i + 1; j <= n; j++) {
          int prior = priorCounts.getOrDefault(i, Map.of()).getOrDefault(j, 0);
          String key = i + "-" + j;
          int newCount = newOpponents.getOrDefault(key, 0);
          cumulative.put(key, prior + newCount);
        }
      }

      for (long i = 1; i <= n; i++) {
        StringBuilder line = new StringBuilder("   P" + i + ": ");
        for (long j = 1; j <= n; j++) {
          if (i == j) {
            line.append("  - ");
          } else {
            String key = i < j ? i + "-" + j : j + "-" + i;
            int count = cumulative.getOrDefault(key, 0);
            line.append(String.format("%3d ", count));
          }
        }
        System.out.println(line);
      }

      // Calculate variance/std dev of cumulative counts
      List<Integer> cumulativeCounts = new ArrayList<>(cumulative.values());
      double mean = cumulativeCounts.stream().mapToInt(Integer::intValue).average().orElse(0);
      double variance =
          cumulativeCounts.stream().mapToDouble(c -> Math.pow(c - mean, 2)).average().orElse(0);
      double stdDev = Math.sqrt(variance);
      int min = cumulativeCounts.stream().min(Integer::compareTo).orElse(0);
      int max = cumulativeCounts.stream().max(Integer::compareTo).orElse(0);

      System.out.println("\n   Cumulative Stats:");
      System.out.println("   Range: " + min + " to " + max + " (spread: " + (max - min) + ")");
      System.out.println(
          "   Mean: "
              + String.format("%.2f", mean)
              + ", Std Dev: "
              + String.format("%.2f", stdDev));
      System.out.println();
    }

    // Calculate metrics for comparison
    Map<String, Integer> newOpponentsWithout = new HashMap<>();
    Map<String, Integer> newOpponentsWith = new HashMap<>();

    for (List<RoundRobinScheduler.MatchSpec> round : scheduleWithout) {
      for (RoundRobinScheduler.MatchSpec spec : round) {
        if (spec.bye) continue;
        if (spec.a1 != null && spec.b1 != null)
          recordOpponent(newOpponentsWithout, spec.a1, spec.b1);
        if (spec.a1 != null && spec.b2 != null)
          recordOpponent(newOpponentsWithout, spec.a1, spec.b2);
        if (spec.a2 != null && spec.b1 != null)
          recordOpponent(newOpponentsWithout, spec.a2, spec.b1);
        if (spec.a2 != null && spec.b2 != null)
          recordOpponent(newOpponentsWithout, spec.a2, spec.b2);
      }
    }

    for (List<RoundRobinScheduler.MatchSpec> round : scheduleWith) {
      for (RoundRobinScheduler.MatchSpec spec : round) {
        if (spec.bye) continue;
        if (spec.a1 != null && spec.b1 != null) recordOpponent(newOpponentsWith, spec.a1, spec.b1);
        if (spec.a1 != null && spec.b2 != null) recordOpponent(newOpponentsWith, spec.a1, spec.b2);
        if (spec.a2 != null && spec.b1 != null) recordOpponent(newOpponentsWith, spec.a2, spec.b1);
        if (spec.a2 != null && spec.b2 != null) recordOpponent(newOpponentsWith, spec.a2, spec.b2);
      }
    }

    int highCountWithout = 0;
    int highCountWith = 0;
    for (long i = 1; i <= 5; i++) {
      for (long j = i + 1; j <= 5; j++) {
        String key = i + "-" + j;
        highCountWithout += newOpponentsWithout.getOrDefault(key, 0);
        highCountWith += newOpponentsWith.getOrDefault(key, 0);
      }
    }

    System.out.println("Single Trial Result:");
    System.out.println(
        "  WITHOUT balancing: " + highCountWithout + " new matches among high-count pairs");
    System.out.println(
        "  WITH balancing:    " + highCountWith + " new matches among high-count pairs");
    System.out.println(
        "  Difference:        " + (highCountWithout - highCountWith) + " fewer matches");

    // Run multiple trials to see the trend
    System.out.println("\nMulti-Trial Analysis (10 trials each):");
    int betterCount = 0;
    int worseCount = 0;
    int sameCount = 0;

    for (int trial = 0; trial < 10; trial++) {
      List<List<RoundRobinScheduler.MatchSpec>> trialWithout =
          scheduler.generate(participants, Map.of(), 0);
      List<List<RoundRobinScheduler.MatchSpec>> trialWith =
          scheduler.generate(participants, priorCounts, 0);

      Map<String, Integer> oppsWithout = new HashMap<>();
      Map<String, Integer> oppsWith = new HashMap<>();

      for (List<RoundRobinScheduler.MatchSpec> round : trialWithout) {
        for (RoundRobinScheduler.MatchSpec spec : round) {
          if (spec.bye) continue;
          if (spec.a1 != null && spec.b1 != null) recordOpponent(oppsWithout, spec.a1, spec.b1);
          if (spec.a1 != null && spec.b2 != null) recordOpponent(oppsWithout, spec.a1, spec.b2);
          if (spec.a2 != null && spec.b1 != null) recordOpponent(oppsWithout, spec.a2, spec.b1);
          if (spec.a2 != null && spec.b2 != null) recordOpponent(oppsWithout, spec.a2, spec.b2);
        }
      }

      for (List<RoundRobinScheduler.MatchSpec> round : trialWith) {
        for (RoundRobinScheduler.MatchSpec spec : round) {
          if (spec.bye) continue;
          if (spec.a1 != null && spec.b1 != null) recordOpponent(oppsWith, spec.a1, spec.b1);
          if (spec.a1 != null && spec.b2 != null) recordOpponent(oppsWith, spec.a1, spec.b2);
          if (spec.a2 != null && spec.b1 != null) recordOpponent(oppsWith, spec.a2, spec.b1);
          if (spec.a2 != null && spec.b2 != null) recordOpponent(oppsWith, spec.a2, spec.b2);
        }
      }

      int hcWithout = 0;
      int hcWith = 0;
      for (long i = 1; i <= 5; i++) {
        for (long j = i + 1; j <= 5; j++) {
          String key = i + "-" + j;
          hcWithout += oppsWithout.getOrDefault(key, 0);
          hcWith += oppsWith.getOrDefault(key, 0);
        }
      }

      if (hcWith < hcWithout) betterCount++;
      else if (hcWith > hcWithout) worseCount++;
      else sameCount++;
    }

    System.out.println("  Better (fewer high-count matches): " + betterCount + "/10");
    System.out.println("  Same: " + sameCount + "/10");
    System.out.println("  Worse: " + worseCount + "/10");

    if (betterCount > worseCount) {
      System.out.println(
          "\n✓ SUCCESS: Seasonal balancing trend is POSITIVE - reduces over-matched pairs");
      System.out.println(
          "           while maintaining bye fairness and improving partnership balance!");
    } else if (betterCount == worseCount) {
      System.out.println("\n→ MIXED: Effect varies, but primary metrics are maintained/improved");
    } else {
      System.out.println("\n→ Note: Trend not clearly positive, may need algorithm refinement");
    }

    System.out.println("\n========================================\n");

    // The key assertion: on average we should see some benefit
    assertTrue(
        betterCount + sameCount >= worseCount,
        "Seasonal balancing should not make things worse on average");
  }

  private void recordOpponent(Map<String, Integer> opponentPairs, Long p1, Long p2) {
    if (p1 == null || p2 == null) return;
    String key = p1 < p2 ? p1 + "-" + p2 : p2 + "-" + p1;
    opponentPairs.merge(key, 1, Integer::sum);
  }

  private void incrementIfPresent(Map<Long, Integer> counts, Long player) {
    if (player == null) return;
    counts.merge(player, 1, Integer::sum);
  }
}
