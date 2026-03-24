package com.w3llspring.fhpb.web.service.roundrobin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Scheduler that evaluates many circle rotations and picks the one that best balances bye
 * assignments and reduces repeat opponents based on prior season history.
 */
public class BalancingRoundRobinScheduler implements RoundRobinScheduler {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(BalancingRoundRobinScheduler.class);

  private Map<Long, Map<Long, Integer>> priorPairCounts = new LinkedHashMap<>();
  private Random rng = new Random();

  public void setPriorPairCounts(Map<Long, Map<Long, Integer>> priorPairCounts) {
    this.priorPairCounts = priorPairCounts == null ? new LinkedHashMap<>() : priorPairCounts;
  }

  @Override
  public List<List<MatchSpec>> generateWithLog(
      List<Long> participantIds,
      Map<Long, Map<Long, Integer>> priorPairCounts,
      int rounds,
      List<String> explanationLog) {
    if (participantIds == null || participantIds.isEmpty()) return List.of();
    this.priorPairCounts = priorPairCounts == null ? new LinkedHashMap<>() : priorPairCounts;

    List<Long> players = new ArrayList<>(participantIds);
    int attempts = Math.max(6, Math.min(players.size() * 4, 60));

    Candidate best = null;
    for (int attempt = 0; attempt < attempts; attempt++) {
      List<Long> rotation = new ArrayList<>(players);
      Collections.shuffle(rotation, rng);
      Candidate candidate = buildCandidate(rotation, rounds);
      candidate.attemptNumber = attempt + 1;
      if (best == null || candidate.isBetterThan(best)) {
        best = candidate;
        if (candidate.byeSpread() == 0 && candidate.repeatPenalty == 0) break;
      }
    }

    if (best != null && explanationLog != null) {
      explanationLog.add("Selected schedule after " + best.attemptNumber + " attempt(s).");
      explanationLog.add(
          "Bye spread: "
              + best.byeSpread()
              + " (max="
              + best.maxBye
              + ", min="
              + best.minBye
              + ")");
      explanationLog.add("Repeat penalty: " + best.repeatPenalty);
      explanationLog.addAll(best.logLines);
    }
    return best == null ? List.of() : best.schedule;
  }

  private Candidate buildCandidate(List<Long> initialOrder, int rounds) {
    List<String> logLines = new ArrayList<>();
    logLines.add("Initial rotation order: " + initialOrder);

    List<Long> rotation = new ArrayList<>(initialOrder);
    if (rotation.size() % 2 == 1) {
      rotation.add(null); // ghost participant
      logLines.add("Odd participant count -> ghost participant added.");
    }

    int n = rotation.size();
    int targetRounds = rounds <= 0 ? Math.max(0, n - 1) : rounds;

    List<List<MatchSpec>> schedule = new ArrayList<>();
    LinkedHashMap<Long, Integer> byeCounts = new LinkedHashMap<>();
    for (Long p : rotation) if (p != null) byeCounts.putIfAbsent(p, 0);

    Map<Long, Map<Long, Integer>> pairUsage = new LinkedHashMap<>();

    for (int round = 1; round <= targetRounds; round++) {
      List<MatchSpec> roundMatches = new ArrayList<>();
      List<Long[]> teams = new ArrayList<>();
      for (int i = 0; i < n; i += 2) {
        teams.add(new Long[] {rotation.get(i), rotation.get(i + 1)});
      }

      List<Integer> viableTeams = new ArrayList<>();
      List<Long> ghostPlayers = new ArrayList<>();
      for (int idx = 0; idx < teams.size(); idx++) {
        Long[] team = teams.get(idx);
        if (team[0] == null && team[1] == null) continue;
        if (team[0] == null ^ team[1] == null) {
          Long player = team[0] != null ? team[0] : team[1];
          ghostPlayers.add(player);
        } else {
          viableTeams.add(idx);
        }
      }

      while (viableTeams.size() >= 2) {
        int firstIdx = viableTeams.remove(0);
        Long[] teamA = teams.get(firstIdx);
        int bestIdx = -1;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < viableTeams.size(); i++) {
          Long[] teamB = teams.get(viableTeams.get(i));
          int score = pairingScore(teamA, teamB);
          if (score < bestScore) {
            bestScore = score;
            bestIdx = i;
          }
        }
        int matchIdx = viableTeams.remove(bestIdx);
        Long[] teamB = teams.get(matchIdx);
        roundMatches.add(new MatchSpec(teamA[0], teamA[1], teamB[0], teamB[1], false));
        recordPair(pairUsage, teamA, teamB);
      }

      if (!viableTeams.isEmpty()) {
        int idx = viableTeams.get(0);
        Long[] leftover = teams.get(idx);
        roundMatches.add(new MatchSpec(leftover[0], leftover[1], null, null, true));
        incrementBye(byeCounts, leftover[0]);
        incrementBye(byeCounts, leftover[1]);
      }

      if (!ghostPlayers.isEmpty()) {
        ghostPlayers.sort(Comparator.comparingInt(p -> byeCounts.getOrDefault(p, 0)));
        for (Long player : ghostPlayers) {
          roundMatches.add(new MatchSpec(player, null, null, null, true));
          incrementBye(byeCounts, player);
        }
      }

      schedule.add(roundMatches);

      List<Long> nextRotation = new ArrayList<>();
      nextRotation.add(rotation.get(0));
      nextRotation.add(rotation.get(n - 1));
      for (int j = 1; j < n - 1; j++) nextRotation.add(rotation.get(j));
      rotation = nextRotation;
    }

    int maxBye = byeCounts.values().stream().max(Integer::compareTo).orElse(0);
    int minBye = byeCounts.values().stream().min(Integer::compareTo).orElse(0);
    int repeatPenalty = computeRepeatPenalty(pairUsage);

    logLines.add("Bye distribution: " + byeCounts);
    logLines.add("Repeat penalty: " + repeatPenalty);

    return new Candidate(schedule, byeCounts, repeatPenalty, logLines);
  }

  private int pairingScore(Long[] teamA, Long[] teamB) {
    return repeatCost(teamA[0], teamB[0])
        + repeatCost(teamA[0], teamB[1])
        + repeatCost(teamA[1], teamB[0])
        + repeatCost(teamA[1], teamB[1]);
  }

  private int repeatCost(Long a, Long b) {
    if (a == null || b == null) return 0;
    return priorPairCounts.getOrDefault(a, Map.of()).getOrDefault(b, 0);
  }

  private void recordPair(Map<Long, Map<Long, Integer>> usage, Long[] teamA, Long[] teamB) {
    for (Long a : teamA) {
      if (a == null) continue;
      for (Long b : teamB) {
        if (b == null) continue;
        usage.computeIfAbsent(a, k -> new LinkedHashMap<>()).merge(b, 1, Integer::sum);
        usage.computeIfAbsent(b, k -> new LinkedHashMap<>()).merge(a, 1, Integer::sum);
      }
    }
  }

  private int computeRepeatPenalty(Map<Long, Map<Long, Integer>> usage) {
    int penalty = 0;
    for (var outer : usage.entrySet()) {
      Long player = outer.getKey();
      Map<Long, Integer> opponents = outer.getValue();
      for (var inner : opponents.entrySet()) {
        Long opponent = inner.getKey();
        int playedThisSchedule = inner.getValue();
        int priorCount = repeatCost(player, opponent);
        penalty += playedThisSchedule * priorCount;
      }
    }
    return penalty;
  }

  private void incrementBye(Map<Long, Integer> counts, Long player) {
    if (player == null) return;
    counts.merge(player, 1, Integer::sum);
  }

  void setSeedForTests(Long seed) {
    rng = seed == null ? new Random() : new Random(seed);
  }

  private static final class Candidate {
    final List<List<MatchSpec>> schedule;
    final Map<Long, Integer> byeCounts;
    final int repeatPenalty;
    final List<String> logLines;
    final int maxBye;
    final int minBye;
    int attemptNumber;

    Candidate(
        List<List<MatchSpec>> schedule,
        Map<Long, Integer> byeCounts,
        int repeatPenalty,
        List<String> logLines) {
      this.schedule = schedule;
      this.byeCounts = byeCounts;
      this.repeatPenalty = repeatPenalty;
      this.logLines = logLines;
      this.maxBye = byeCounts.values().stream().max(Integer::compareTo).orElse(0);
      this.minBye = byeCounts.values().stream().min(Integer::compareTo).orElse(0);
    }

    int byeSpread() {
      return maxBye - minBye;
    }

    boolean isBetterThan(Candidate other) {
      if (byeSpread() != other.byeSpread()) return byeSpread() < other.byeSpread();
      if (maxBye != other.maxBye) return maxBye < other.maxBye;
      if (repeatPenalty != other.repeatPenalty) return repeatPenalty < other.repeatPenalty;
      return logLines.size() < other.logLines.size(); // prefer shorter logs (less work)
    }
  }
}
