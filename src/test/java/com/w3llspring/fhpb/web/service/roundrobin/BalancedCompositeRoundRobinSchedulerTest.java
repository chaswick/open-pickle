package com.w3llspring.fhpb.web.service.roundrobin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class BalancedCompositeRoundRobinSchedulerTest {

  private final BalancedCompositeRoundRobinScheduler scheduler =
      new BalancedCompositeRoundRobinScheduler();

  @Test
  void evenRosterUsesTemplateAndPartnersAppearOnce() {
    List<Long> participants = LongStream.rangeClosed(1, 8).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);
    assertEquals(7, schedule.size(), "Eight players should yield seven rounds.");

    java.util.Map<Set<Long>, Integer> partnerCounts = new java.util.HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
      for (RoundRobinScheduler.MatchSpec spec : round) {
        if (spec.bye) continue;
        partnerCounts.merge(Set.of(spec.a1, spec.a2), 1, Integer::sum);
        partnerCounts.merge(Set.of(spec.b1, spec.b2), 1, Integer::sum);
      }
    }

    System.out.println("Even roster (n=8) partner counts:");
    String partnerSummary =
        partnerCounts.entrySet().stream()
            .limit(10)
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
    System.out.println("  " + partnerSummary);

    long uniquePartners = partnerCounts.values().stream().filter(c -> c == 1).count();
    System.out.println("  Unique partnerships: " + uniquePartners + "/" + partnerCounts.size());
    System.out.println(
        "  All partners appear exactly once: "
            + partnerCounts.values().stream().allMatch(count -> count == 1));

    assertTrue(
        partnerCounts.values().stream().allMatch(count -> count == 1),
        "Composite scheduler should ensure template partners appear once.");
  }

  @Test
  void oddRosterProducesScheduleWithByes() {
    List<Long> participants = LongStream.rangeClosed(1, 9).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);
    assertFalse(schedule.isEmpty(), "Odd roster should produce a schedule.");
    assertEquals(9, schedule.size(), "Nine players should produce nine rounds (n).");

    boolean anyBye = schedule.stream().flatMap(List::stream).anyMatch(spec -> spec.bye);

    // Count byes per player
    java.util.Map<Long, Integer> byeCounts = new java.util.HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
      for (RoundRobinScheduler.MatchSpec spec : round) {
        if (!spec.bye) continue;
        incrementIfPresent(byeCounts, spec.a1);
        incrementIfPresent(byeCounts, spec.a2);
        incrementIfPresent(byeCounts, spec.b1);
        incrementIfPresent(byeCounts, spec.b2);
      }
    }

    // Count partnerships
    java.util.Map<Set<Long>, Integer> partnerCounts = new java.util.HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
      for (RoundRobinScheduler.MatchSpec spec : round) {
        if (spec.bye) continue;
        partnerCounts.merge(Set.of(spec.a1, spec.a2), 1, Integer::sum);
        partnerCounts.merge(Set.of(spec.b1, spec.b2), 1, Integer::sum);
      }
    }

    System.out.println("Odd roster (n=9) statistics:");
    System.out.println("  Bye distribution: " + byeCounts);

    int minPartners = partnerCounts.values().stream().min(Integer::compareTo).orElse(0);
    int maxPartners = partnerCounts.values().stream().max(Integer::compareTo).orElse(0);
    System.out.println("  Partner count range: min=" + minPartners + ", max=" + maxPartners);

    String partnerSample =
        partnerCounts.entrySet().stream()
            .limit(5)
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
    System.out.println(
        "  Sample partnerships: " + partnerSample + (partnerCounts.size() > 5 ? ", ..." : ""));

    assertTrue(anyBye, "Odd rosters should contain bye entries.");
  }

  @Test
  void sixPlayersUseEvenTemplateWithoutExplicitByeEntries() {
    List<Long> participants = LongStream.rangeClosed(1, 6).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);
    assertFalse(schedule.isEmpty(), "Six players should produce a schedule.");

    // Legacy composite scheduler delegates even rosters to the balanced template path.
    // That path does not emit explicit bye entries for the leftover team when the roster
    // size is even-but-not-divisible-by-4; it simply yields the playable doubles matches.
    for (int i = 0; i < schedule.size(); i++) {
      List<RoundRobinScheduler.MatchSpec> round = schedule.get(i);
      long byeCount = round.stream().filter(spec -> spec.bye).count();
      long matchCount = round.stream().filter(spec -> !spec.bye).count();

      System.out.println(
          "Round " + (i + 1) + ": " + byeCount + " byes, " + matchCount + " matches");

      assertEquals(0, byeCount, "Even-template rounds do not emit explicit bye entries");
      assertEquals(
          1, matchCount, "Each round should yield exactly 1 playable doubles match for 6 players");
    }
  }

  @Test
  void tenPlayersUseEvenTemplateWithoutExplicitByeEntries() {
    List<Long> participants = LongStream.rangeClosed(1, 10).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);
    assertFalse(schedule.isEmpty(), "Ten players should produce a schedule.");

    // Same legacy behavior as the 6-player case: even rosters route through the template
    // scheduler and only expose the playable doubles matches.
    for (int i = 0; i < schedule.size(); i++) {
      List<RoundRobinScheduler.MatchSpec> round = schedule.get(i);
      long byeCount = round.stream().filter(spec -> spec.bye).count();
      long matchCount = round.stream().filter(spec -> !spec.bye).count();

      if (i < 3) { // Only print first few rounds to avoid clutter
        System.out.println(
            "Round " + (i + 1) + ": " + byeCount + " byes, " + matchCount + " matches");
      }

      assertEquals(0, byeCount, "Even-template rounds do not emit explicit bye entries");
      assertEquals(
          2,
          matchCount,
          "Each round should yield exactly 2 playable doubles matches for 10 players");
    }
  }

  private void incrementIfPresent(java.util.Map<Long, Integer> counts, Long player) {
    if (player == null) return;
    counts.merge(player, 1, Integer::sum);
  }
}
