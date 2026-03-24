package com.w3llspring.fhpb.web.service.roundrobin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class BalancingRoundRobinSchedulerTest {

  @Test
  void oddParticipantScenarioProducesFairByesAndFreshOpponents() {
    List<Long> participants = LongStream.rangeClosed(1, 11).boxed().collect(Collectors.toList());
    Map<Long, Map<Long, Integer>> prior = createOddPrior();

    BalancingRoundRobinScheduler scheduler = new BalancingRoundRobinScheduler();
    scheduler.setSeedForTests(42L);
    List<List<RoundRobinScheduler.MatchSpec>> schedule = scheduler.generate(participants, prior, 0);

    Map<Long, Integer> byeCounts = computeByeCounts(schedule);
    assertTrue(!byeCounts.isEmpty(), "Bye counts should be present");
    assertEquals(
        33,
        byeCounts.values().stream().mapToInt(Integer::intValue).sum(),
        "Eleven-player schedules should assign 3 sitters across 11 rounds");

    Map<Long, Set<Long>> opponents = computeOpponents(schedule);
    for (Long player : List.of(2L, 4L, 5L, 6L)) {
      Set<Long> faced = opponents.getOrDefault(player, Set.of());
      boolean sawNewOpponent = faced.stream().anyMatch(o -> priorCount(prior, player, o) == 0);
      assertTrue(sawNewOpponent, "Player " + player + " should face at least one new opponent");
    }

    assertImprovedRepeatPenalty(schedule, prior, participants);
  }

  @Test
  void evenParticipantScenarioBalancesCrossGroupMatches() {
    List<Long> participants = LongStream.rangeClosed(1, 12).boxed().collect(Collectors.toList());
    Map<Long, Map<Long, Integer>> prior = createEvenPrior();

    BalancingRoundRobinScheduler scheduler = new BalancingRoundRobinScheduler();
    scheduler.setSeedForTests(42L);
    List<List<RoundRobinScheduler.MatchSpec>> schedule = scheduler.generate(participants, prior, 0);

    Map<Long, Integer> byeCounts = computeByeCounts(schedule);
    assertTrue(byeCounts.isEmpty(), "Even 12-player schedules should not emit bye entries");

    Map<Long, Set<Long>> opponents = computeOpponents(schedule);
    Set<Long> groupA = LongStream.rangeClosed(1, 6).boxed().collect(Collectors.toSet());
    Set<Long> groupB = LongStream.rangeClosed(7, 12).boxed().collect(Collectors.toSet());

    for (Long player : groupA) {
      long crossMatches =
          opponents.getOrDefault(player, Set.of()).stream().filter(groupB::contains).count();
      assertTrue(
          crossMatches >= 2,
          "Player " + player + " should face at least two opponents from group B");
    }
    for (Long player : groupB) {
      long crossMatches =
          opponents.getOrDefault(player, Set.of()).stream().filter(groupA::contains).count();
      assertTrue(
          crossMatches >= 2,
          "Player " + player + " should face at least two opponents from group A");
    }

    assertImprovedRepeatPenalty(schedule, prior, participants);
  }

  private void assertImprovedRepeatPenalty(
      List<List<RoundRobinScheduler.MatchSpec>> candidate,
      Map<Long, Map<Long, Integer>> prior,
      List<Long> participants) {
    DefaultRoundRobinScheduler baselineScheduler = new DefaultRoundRobinScheduler();
    List<List<RoundRobinScheduler.MatchSpec>> baseline =
        baselineScheduler.generate(participants, prior, 0);

    int baselinePenalty = repeatPenalty(baseline, prior);
    int candidatePenalty = repeatPenalty(candidate, prior);
    assertTrue(
        candidatePenalty <= baselinePenalty,
        "Expected candidate repeat penalty <= baseline. candidate="
            + candidatePenalty
            + ", baseline="
            + baselinePenalty);
  }

  private Map<Long, Integer> computeByeCounts(List<List<RoundRobinScheduler.MatchSpec>> schedule) {
    Map<Long, Integer> counts = new HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
      for (RoundRobinScheduler.MatchSpec match : round) {
        if (!match.bye) continue;
        increment(counts, match.a1);
        increment(counts, match.a2);
        increment(counts, match.b1);
        increment(counts, match.b2);
      }
    }
    return counts;
  }

  private Map<Long, Set<Long>> computeOpponents(
      List<List<RoundRobinScheduler.MatchSpec>> schedule) {
    Map<Long, Set<Long>> opponents = new HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
      for (RoundRobinScheduler.MatchSpec match : round) {
        if (match.bye) continue;
        Set<Long> teamA = extractTeam(match.a1, match.a2);
        Set<Long> teamB = extractTeam(match.b1, match.b2);
        if (teamA.isEmpty() || teamB.isEmpty()) continue;
        for (Long a : teamA) {
          opponents.computeIfAbsent(a, k -> new HashSet<>()).addAll(teamB);
        }
        for (Long b : teamB) {
          opponents.computeIfAbsent(b, k -> new HashSet<>()).addAll(teamA);
        }
      }
    }
    return opponents;
  }

  private int repeatPenalty(
      List<List<RoundRobinScheduler.MatchSpec>> schedule, Map<Long, Map<Long, Integer>> prior) {
    Map<Long, Map<Long, Integer>> usage = new HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
      for (RoundRobinScheduler.MatchSpec match : round) {
        if (match.bye) continue;
        Set<Long> teamA = extractTeam(match.a1, match.a2);
        Set<Long> teamB = extractTeam(match.b1, match.b2);
        if (teamA.isEmpty() || teamB.isEmpty()) continue;
        for (Long a : teamA) {
          for (Long b : teamB) {
            usage.computeIfAbsent(a, k -> new HashMap<>()).merge(b, 1, Integer::sum);
            usage.computeIfAbsent(b, k -> new HashMap<>()).merge(a, 1, Integer::sum);
          }
        }
      }
    }
    int penalty = 0;
    for (var outer : usage.entrySet()) {
      Long player = outer.getKey();
      for (var inner : outer.getValue().entrySet()) {
        penalty += inner.getValue() * priorCount(prior, player, inner.getKey());
      }
    }
    return penalty;
  }

  private Set<Long> extractTeam(Long p1, Long p2) {
    Set<Long> team = new HashSet<>();
    if (p1 != null) team.add(p1);
    if (p2 != null) team.add(p2);
    return team;
  }

  private Map<Long, Map<Long, Integer>> createOddPrior() {
    Map<Long, Map<Long, Integer>> prior = emptyPrior(11);
    addPrior(prior, 2L, 4L, 12);
    addPrior(prior, 2L, 5L, 10);
    addPrior(prior, 2L, 6L, 9);
    addPrior(prior, 4L, 5L, 11);
    addPrior(prior, 4L, 6L, 10);
    addPrior(prior, 5L, 6L, 9);
    addPrior(prior, 1L, 3L, 6);
    addPrior(prior, 7L, 8L, 7);
    addPrior(prior, 9L, 10L, 5);
    return prior;
  }

  private Map<Long, Map<Long, Integer>> createEvenPrior() {
    Map<Long, Map<Long, Integer>> prior = emptyPrior(12);
    for (long a = 1; a <= 6; a++) {
      for (long b = a + 1; b <= 6; b++) {
        addPrior(prior, a, b, 8);
      }
    }
    for (long a = 7; a <= 12; a++) {
      for (long b = a + 1; b <= 12; b++) {
        addPrior(prior, a, b, 8);
      }
    }
    return prior;
  }

  private Map<Long, Map<Long, Integer>> emptyPrior(int maxParticipant) {
    Map<Long, Map<Long, Integer>> prior = new HashMap<>();
    for (long p = 1; p <= maxParticipant; p++) {
      prior.put(p, new HashMap<>());
    }
    return prior;
  }

  private void addPrior(Map<Long, Map<Long, Integer>> prior, Long a, Long b, int count) {
    prior.computeIfAbsent(a, k -> new HashMap<>()).put(b, count);
    prior.computeIfAbsent(b, k -> new HashMap<>()).put(a, count);
  }

  private int priorCount(Map<Long, Map<Long, Integer>> prior, Long a, Long b) {
    return prior.getOrDefault(a, Map.of()).getOrDefault(b, 0);
  }

  private void increment(Map<Long, Integer> counts, Long player) {
    if (player == null) return;
    counts.merge(player, 1, Integer::sum);
  }
}
