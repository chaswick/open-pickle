package com.w3llspring.fhpb.web.service.roundrobin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduler that pre-computes balanced whist-style templates (1-factorisations of K_n) so every
 * participant partners with every other participant exactly once when the roster size is even.
 *
 * <p>Opposing teams are paired greedily each round to minimise replays against the same opponents,
 * incorporating both historical opponent counts and matches scheduled earlier in the run.
 */
public class BalancedTemplateRoundRobinScheduler implements RoundRobinScheduler {

  private static final Logger log =
      LoggerFactory.getLogger(BalancedTemplateRoundRobinScheduler.class);

  private final Map<Integer, List<List<int[]>>> pairingCache = new ConcurrentHashMap<>();

  @Override
  public List<List<MatchSpec>> generateWithLog(
      List<Long> participantIds,
      Map<Long, Map<Long, Integer>> priorPairCounts,
      int rounds,
      List<String> explanationLog) {
    if (participantIds == null || participantIds.isEmpty()) {
      if (explanationLog != null)
        explanationLog.add("Balanced template scheduler: empty participant list.");
      return List.of();
    }

    List<Long> players = new ArrayList<>(participantIds);
    if (players.size() % 2 == 1) {
      if (explanationLog != null) {
        explanationLog.add(
            "Balanced template scheduler: roster size "
                + players.size()
                + " is odd; no even template available.");
      }
      return List.of();
    }
    if (players.size() < 4) {
      if (explanationLog != null) {
        explanationLog.add(
            "Balanced template scheduler: roster size "
                + players.size()
                + " has no doubles template.");
      }
      return List.of();
    }

    int n = players.size();
    int targetRounds = rounds <= 0 ? n - 1 : Math.min(rounds, n - 1);
    if (rounds > 0 && rounds > n - 1 && explanationLog != null) {
      explanationLog.add(
          "Balanced template scheduler: requested "
              + rounds
              + " round(s) but "
              + (n - 1)
              + " are available. Truncating.");
    }

    List<List<int[]>> template = pairingCache.computeIfAbsent(n, this::buildTemplate);
    if (template.isEmpty()) {
      if (explanationLog != null)
        explanationLog.add("Balanced template scheduler: no template generated.");
      return List.of();
    }

    Map<Long, Map<Long, Integer>> opponentCounts = new HashMap<>();
    List<List<MatchSpec>> schedule = new ArrayList<>();

    for (int roundIndex = 0; roundIndex < targetRounds; roundIndex++) {
      List<int[]> pairings = template.get(roundIndex);
      List<Team> teams = new ArrayList<>();
      for (int[] pair : pairings) {
        Long p1 = players.get(pair[0]);
        Long p2 = players.get(pair[1]);
        if (p1 == null || p2 == null) continue;
        teams.add(new Team(p1, p2));
      }

      List<MatchSpec> matches = pairTeams(teams, opponentCounts, priorPairCounts);
      schedule.add(matches);
    }

    if (explanationLog != null) {
      explanationLog.add(
          "Balanced template scheduler: generated "
              + schedule.size()
              + " round(s) for "
              + n
              + " participants.");
    }
    log.debug(
        "Balanced template scheduler produced {} round(s) for {} participants.",
        schedule.size(),
        n);

    return schedule;
  }

  private List<List<int[]>> buildTemplate(int n) {
    List<List<int[]>> rounds = new ArrayList<>();

    int rotating = n - 1;
    for (int round = 0; round < rotating; round++) {
      List<int[]> pairs = new ArrayList<>();
      pairs.add(new int[] {rotating, round});
      for (int i = 1; i < n / 2; i++) {
        int first = (round + i) % rotating;
        int second = (round - i + rotating) % rotating;
        pairs.add(new int[] {first, second});
      }
      rounds.add(pairs);
    }

    return rounds;
  }

  private List<MatchSpec> pairTeams(
      List<Team> teams,
      Map<Long, Map<Long, Integer>> opponentCounts,
      Map<Long, Map<Long, Integer>> priorPairCounts) {
    List<MatchSpec> matches = new ArrayList<>();
    if (teams.isEmpty()) {
      return matches;
    }

    List<Team> remaining = new ArrayList<>(teams);
    remaining.sort(Comparator.naturalOrder());

    while (remaining.size() >= 2) {
      Team anchor = remaining.remove(0);
      int bestIdx = -1;
      int bestCost = Integer.MAX_VALUE;
      for (int i = 0; i < remaining.size(); i++) {
        Team candidate = remaining.get(i);
        int cost = opponentCost(anchor, candidate, opponentCounts, priorPairCounts);
        if (cost < bestCost) {
          bestCost = cost;
          bestIdx = i;
        }
      }
      Team opponent = remaining.remove(bestIdx);
      MatchSpec spec = new MatchSpec(anchor.p1, anchor.p2, opponent.p1, opponent.p2, false);
      matches.add(spec);
      recordMatch(anchor, opponent, opponentCounts);
    }

    return matches;
  }

  private void recordMatch(Team teamA, Team teamB, Map<Long, Map<Long, Integer>> opponentCounts) {
    for (Long a : teamA.players()) {
      for (Long b : teamB.players()) {
        opponentCounts.computeIfAbsent(a, k -> new HashMap<>()).merge(b, 1, Integer::sum);
        opponentCounts.computeIfAbsent(b, k -> new HashMap<>()).merge(a, 1, Integer::sum);
      }
    }
  }

  private int opponentCost(
      Team teamA,
      Team teamB,
      Map<Long, Map<Long, Integer>> currentOpponents,
      Map<Long, Map<Long, Integer>> priorOpponents) {
    int cost = 0;
    for (Long a : teamA.players()) {
      for (Long b : teamB.players()) {
        cost += lookupCount(currentOpponents, a, b);
        cost += lookupCount(priorOpponents, a, b);
      }
    }
    return cost;
  }

  private int lookupCount(Map<Long, Map<Long, Integer>> counts, Long a, Long b) {
    if (counts == null) return 0;
    return counts.getOrDefault(a, Map.of()).getOrDefault(b, 0);
  }

  private static final class Team implements Comparable<Team> {
    final Long p1;
    final Long p2;
    final Set<Long> asSet;

    Team(Long p1, Long p2) {
      if (Objects.equals(p1, p2)) {
        throw new IllegalArgumentException("Team cannot contain duplicate players: " + p1);
      }
      this.p1 = p1;
      this.p2 = p2;
      this.asSet = Set.of(p1, p2);
    }

    Set<Long> players() {
      return asSet;
    }

    @Override
    public int compareTo(Team o) {
      int first = Long.compare(Math.min(p1, p2), Math.min(o.p1, o.p2));
      if (first != 0) return first;
      return Long.compare(Math.max(p1, p2), Math.max(o.p1, o.p2));
    }

    @Override
    public String toString() {
      return "[" + p1 + ", " + p2 + "]";
    }
  }

  /** Convenience method used by tests to confirm the template partners each pair exactly once. */
  List<List<int[]>> debugTemplate(int participantCount) {
    return pairingCache.computeIfAbsent(participantCount, this::buildTemplate);
  }
}
