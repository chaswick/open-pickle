package com.w3llspring.fhpb.web.service.roundrobin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

class DefaultRoundRobinSchedulerTest {

  @Test
  void circleSchedulerProducesExpectedByeDistributionWithOddParticipants(TestReporter reporter) {
    List<Long> participants = LongStream.rangeClosed(1, 11).boxed().collect(Collectors.toList());

    DefaultRoundRobinScheduler scheduler = new DefaultRoundRobinScheduler();
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);
    assertEquals(
        11,
        schedule.size(),
        "Circle method should emit n rounds (with ghost) for 11 participants.");

    Map<Long, Integer> byeCounts = computeByeCounts(schedule);
    Map<Long, Integer> matchCounts = computeMatchCounts(schedule);

    for (int r = 0; r < schedule.size(); r++) {
      List<RoundRobinScheduler.MatchSpec> round = schedule.get(r);
      Set<Long> seen = new HashSet<>();
      for (RoundRobinScheduler.MatchSpec spec : round) {
        addIfPresent(seen, spec.a1);
        addIfPresent(seen, spec.a2);
        addIfPresent(seen, spec.b1);
        addIfPresent(seen, spec.b2);
      }
      assertEquals(
          new HashSet<>(participants),
          seen,
          "Each real participant should appear exactly once in round " + (r + 1));
    }

    System.out.println("byeDistribution: " + byeCounts);
    System.out.println("matchCounts: " + matchCounts);
    reporter.publishEntry("byeDistribution", byeCounts.toString());
    reporter.publishEntry("matchCounts", matchCounts.toString());
  }

  @Test
  void circleSchedulerImbalanceExamples(TestReporter reporter) {
    assertTeamImbalanceForParticipants(
        LongStream.rangeClosed(1, 8).boxed().collect(Collectors.toList()), reporter);
    assertTeamImbalanceForParticipants(
        LongStream.rangeClosed(1, 10).boxed().collect(Collectors.toList()), reporter);
    assertTeamImbalanceForParticipants(
        LongStream.rangeClosed(1, 11).boxed().collect(Collectors.toList()), reporter);
    assertTeamImbalanceForParticipants(
        LongStream.rangeClosed(1, 14).boxed().collect(Collectors.toList()), reporter);
  }

  private void assertTeamImbalanceForParticipants(List<Long> participants, TestReporter reporter) {
    DefaultRoundRobinScheduler scheduler = new DefaultRoundRobinScheduler();
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);

    Map<Set<Long>, Integer> pairCounts = computePairCounts(schedule);
    int min = pairCounts.values().stream().min(Integer::compareTo).orElse(0);
    int max = pairCounts.values().stream().max(Integer::compareTo).orElse(0);
    String summary =
        pairCounts.entrySet().stream()
            .limit(5)
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
    if (pairCounts.size() > 5) {
      summary += ", ...";
    }

    System.out.println("pairCounts(n=" + participants.size() + "): " + summary);
    System.out.println("pairStats(n=" + participants.size() + "): min=" + min + ", max=" + max);
    reporter.publishEntry("pairCounts(n=" + participants.size() + ")", summary);
    reporter.publishEntry(
        "pairStats(n=" + participants.size() + ")", "min=" + min + ", max=" + max);
    assertTrue(
        max - min >= 1,
        "Circle schedule should have at least some team pairing imbalance for "
            + participants.size()
            + " players.");
  }

  private Map<Long, Integer> computeByeCounts(List<List<RoundRobinScheduler.MatchSpec>> rounds) {
    Map<Long, Integer> counts = new HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : rounds) {
      for (RoundRobinScheduler.MatchSpec m : round) {
        if (!m.bye) continue;
        increment(counts, m.a1);
        increment(counts, m.a2);
        increment(counts, m.b1);
        increment(counts, m.b2);
      }
    }
    return counts;
  }

  private Map<Long, Integer> computeMatchCounts(List<List<RoundRobinScheduler.MatchSpec>> rounds) {
    Map<Long, Integer> counts = new HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : rounds) {
      for (RoundRobinScheduler.MatchSpec m : round) {
        if (m.bye) continue;
        increment(counts, m.a1);
        increment(counts, m.a2);
        increment(counts, m.b1);
        increment(counts, m.b2);
      }
    }
    return counts;
  }

  private Map<Set<Long>, Integer> computePairCounts(
      List<List<RoundRobinScheduler.MatchSpec>> rounds) {
    Map<Set<Long>, Integer> counts = new HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : rounds) {
      for (RoundRobinScheduler.MatchSpec m : round) {
        if (m.bye) continue;
        Set<Long> teamA = extractTeam(m.a1, m.a2);
        Set<Long> teamB = extractTeam(m.b1, m.b2);
        counts.merge(teamA, 1, Integer::sum);
        counts.merge(teamB, 1, Integer::sum);
      }
    }
    return counts;
  }

  private Set<Long> extractTeam(Long p1, Long p2) {
    Set<Long> team = new HashSet<>();
    if (p1 != null) team.add(p1);
    if (p2 != null) team.add(p2);
    return team;
  }

  private void increment(Map<Long, Integer> counts, Long player) {
    if (player == null) return;
    counts.merge(player, 1, Integer::sum);
  }

  private void addIfPresent(Set<Long> set, Long player) {
    if (player == null) {
      return;
    }
    boolean added = set.add(player);
    assertTrue(added, "Player " + player + " appears more than once in a single round.");
  }
}
