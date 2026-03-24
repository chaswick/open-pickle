package com.w3llspring.fhpb.web.service.roundrobin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of RoundRobinScheduler using the circle method previously embedded in
 * RoundRobinService. Behavior is preserved to minimize functional changes.
 */
public class DefaultRoundRobinScheduler implements RoundRobinScheduler {

  private static final Logger log = LoggerFactory.getLogger(DefaultRoundRobinScheduler.class);

  @Override
  public List<List<MatchSpec>> generateWithLog(
      List<Long> participantIds,
      java.util.Map<Long, java.util.Map<Long, Integer>> priorPairCounts,
      int rounds,
      java.util.List<String> explanationLog) {
    List<Long> players = new ArrayList<>(participantIds == null ? List.of() : participantIds);
    log(explanationLog, "Starting schedule generation for " + players.size() + " participants.");
    java.util.Collections.shuffle(players);
    log(explanationLog, "Shuffled participant order: " + players);

    java.util.LinkedHashMap<Long, Integer> byeCounts = new java.util.LinkedHashMap<>();
    for (Long p : players) {
      if (p != null) byeCounts.putIfAbsent(p, 0);
    }

    if (players.size() % 2 == 1) {
      players.add(null);
      log(explanationLog, "Odd participant count detected; adding ghost player to balance byes.");
    }

    int n = players.size();
    int baseRounds = Math.max(0, n - 1);
    if (rounds <= 0) rounds = baseRounds;
    log(
        explanationLog,
        "Generating " + rounds + " round(s); traditional count would be " + baseRounds + ".");

    List<Long> rotation = new ArrayList<>(players);
    List<List<MatchSpec>> schedule = new ArrayList<>();

    for (int round = 1; round <= rounds; round++) {
      List<MatchSpec> specs = new ArrayList<>();
      log(explanationLog, "\nRound " + round + " rotation: " + rotation);

      // build adjacent teams
      List<Long[]> teamsThisRound = new ArrayList<>();
      for (int i = 0; i < n; i += 2) {
        Long p1 = rotation.get(i);
        Long p2 = rotation.get(i + 1);
        teamsThisRound.add(new Long[] {p1, p2});
        log(
            explanationLog,
            "  Pairing indices " + i + " and " + (i + 1) + " -> team: " + p1 + ", " + p2);
      }

      List<Long[]> fullTeams = new ArrayList<>();
      List<Long> soloPlayers = new ArrayList<>();
      for (Long[] team : teamsThisRound) {
        Long p1 = team[0];
        Long p2 = team[1];
        if (p1 != null && p2 != null) {
          fullTeams.add(team);
          log(explanationLog, "    Team formed: " + p1 + " & " + p2);
        } else if (p1 != null) {
          soloPlayers.add(p1);
          log(explanationLog, "    Solo player (bye candidate): " + p1);
        } else if (p2 != null) {
          soloPlayers.add(p2);
          log(explanationLog, "    Solo player (bye candidate): " + p2);
        }
      }

      // Pair up full teams sequentially (default behavior)
      boolean[] used = new boolean[fullTeams.size()];
      for (int f = 0; f + 1 < fullTeams.size(); f += 2) {
        Long[] t1 = fullTeams.get(f);
        Long[] t2 = fullTeams.get(f + 1);
        specs.add(new MatchSpec(t1[0], t1[1], t2[0], t2[1], false));
        log(
            explanationLog,
            "    Match: [" + t1[0] + ", " + t1[1] + "] vs [" + t2[0] + ", " + t2[1] + "]");
        used[f] = true;
        used[f + 1] = true;
      }

      // leftover full team -> bye
      if (fullTeams.size() % 2 == 1) {
        int candidateIndex = -1;
        int candidateScore = Integer.MAX_VALUE;
        for (int idx = 0; idx < fullTeams.size(); idx++) {
          Long[] team = fullTeams.get(idx);
          int score = byeCounts.getOrDefault(team[0], 0) + byeCounts.getOrDefault(team[1], 0);
          if (score < candidateScore) {
            candidateScore = score;
            candidateIndex = idx;
          }
        }
        // If calculated candidate already consumed in a match, fall back to the unused team.
        if (candidateIndex >= 0 && candidateIndex < used.length && used[candidateIndex]) {
          candidateIndex = -1;
        }

        if (candidateIndex == -1) {
          for (int idx = 0; idx < used.length; idx++) {
            if (!used[idx]) {
              candidateIndex = idx;
              break;
            }
          }
        }

        Long[] leftover = fullTeams.get(candidateIndex);
        specs.add(new MatchSpec(leftover[0], leftover[1], null, null, true));
        log(
            explanationLog,
            "    Bye assigned to team: "
                + leftover[0]
                + ", "
                + leftover[1]
                + " (lowest prior byes="
                + candidateScore
                + ")");
        incrementBye(byeCounts, leftover[0]);
        incrementBye(byeCounts, leftover[1]);
      }

      // solo players -> byes
      soloPlayers.sort(java.util.Comparator.comparingInt(p -> byeCounts.getOrDefault(p, 0)));
      for (Long solo : soloPlayers) {
        specs.add(new MatchSpec(solo, null, null, null, true));
        log(
            explanationLog,
            "    Bye assigned to solo player: "
                + solo
                + " (paired with ghost slot, byeCount before="
                + byeCounts.getOrDefault(solo, 0)
                + ")");
        incrementBye(byeCounts, solo);
      }

      schedule.add(specs);
      log(explanationLog, "  Bye counts after round " + round + ": " + formatByeCounts(byeCounts));

      // rotate (keep first fixed)
      List<Long> newRot = new ArrayList<>();
      newRot.add(rotation.get(0));
      newRot.add(rotation.get(n - 1));
      for (int j = 1; j < n - 1; j++) {
        newRot.add(rotation.get(j));
      }
      rotation = newRot;
    }

    log(explanationLog, "Final bye totals: " + formatByeCounts(byeCounts));
    java.util.List<Long> noByePlayers =
        byeCounts.entrySet().stream()
            .filter(e -> e.getValue() == 0)
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toList());
    if (!noByePlayers.isEmpty()) {
      log(explanationLog, "Players without any bye: " + noByePlayers);
    }

    return schedule;
  }

  private void log(List<String> explanationLog, String message) {
    log.trace(message);
    if (explanationLog != null) {
      explanationLog.add(message);
    }
  }

  private void incrementBye(java.util.Map<Long, Integer> byeCounts, Long player) {
    if (player == null) return;
    byeCounts.merge(player, 1, Integer::sum);
  }

  private String formatByeCounts(java.util.Map<Long, Integer> byeCounts) {
    return byeCounts.entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.joining(", "));
  }
}
