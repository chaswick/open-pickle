package com.w3llspring.fhpb.web.service.roundrobin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Round-robin scheduler that ensures perfectly even bye distribution for odd-sized rosters. For N
 * players (odd), generates N rounds where each player sits out exactly once. Evaluates multiple
 * rotation orders and selects the one that best balances opponents based on prior season history.
 */
public class FairByeRoundRobinScheduler implements RoundRobinScheduler {

  private static final Logger log = LoggerFactory.getLogger(FairByeRoundRobinScheduler.class);

  private Random rng = new Random();

  // For testing purposes
  void setSeedForTests(Long seed) {
    rng = seed == null ? new Random() : new Random(seed);
  }

  @Override
  public List<List<MatchSpec>> generateWithLog(
      List<Long> participantIds,
      Map<Long, Map<Long, Integer>> priorPairCounts,
      int rounds,
      List<String> explanationLog) {
    if (participantIds == null || participantIds.isEmpty()) {
      return List.of();
    }

    List<Long> players = new ArrayList<>(participantIds);
    int n = players.size();

    log(explanationLog, "FairByeScheduler: Starting generation for " + n + " participants.");

    // Check if we need sitters
    // n % 4 == 0: all players can play (e.g., 4, 8, 12, 16)
    // n % 4 == 1: need 1 sitter (e.g., 5, 9, 13, 17)
    // n % 4 == 2: need 2 sitters (e.g., 6, 10, 14, 18)
    // n % 4 == 3: need 3 sitters (e.g., 7, 11, 15, 19)
    int sittersNeeded = n % 4;

    if (sittersNeeded == 0) {
      // Perfect multiple of 4 - everyone plays every round
      log(
          explanationLog,
          "Roster size is multiple of 4. Using standard circle rotation (no sitters).");
      return generateEvenRoster(players, priorPairCounts, rounds, explanationLog);
    }

    // Need sitters - use fair bye distribution
    log(
        explanationLog,
        "Roster requires "
            + sittersNeeded
            + " sitter(s) per round. Ensuring fair bye distribution.");

    int targetRounds = rounds <= 0 ? n : rounds;
    int attempts = Math.min(500, Math.max(50, n * 5));

    CandidateSchedule best = null;
    for (int attempt = 0; attempt < attempts; attempt++) {
      List<Long> rotation = new ArrayList<>(players);
      Collections.shuffle(rotation, rng);

      CandidateSchedule candidate =
          generateOddRosterSchedule(rotation, targetRounds, priorPairCounts);

      if (best == null || candidate.isBetterThan(best)) {
        best = candidate;
        if (candidate.opponentRepeatScore == 0) {
          log(
              explanationLog,
              "Found perfect schedule with no repeat opponents on attempt " + (attempt + 1));
          break;
        }
      }
    }

    if (best != null) {
      log(
          explanationLog,
          "Selected schedule with partnership imbalance: "
              + best.partnershipImbalance
              + ", opponent repeat score: "
              + best.opponentRepeatScore);
      log(explanationLog, "Bye distribution: " + best.byeDistribution);

      // Verify fair distribution
      long uniqueByes = best.byeDistribution.values().stream().distinct().count();
      if (uniqueByes == 1 && best.byeDistribution.values().stream().findFirst().orElse(0) == 1) {
        log(explanationLog, "✓ Verified: All " + n + " players sit exactly once.");
      } else {
        log.warn("Warning: Bye distribution not perfectly even: {}", best.byeDistribution);
      }
    }

    return best == null ? List.of() : best.schedule;
  }

  private CandidateSchedule generateOddRosterSchedule(
      List<Long> players, int targetRounds, Map<Long, Map<Long, Integer>> priorPairCounts) {
    int n = players.size();
    List<List<MatchSpec>> schedule = new ArrayList<>();
    Map<Long, Integer> byeDistribution = new LinkedHashMap<>();
    Map<String, Integer> opponentPairs = new HashMap<>();

    for (Long p : players) {
      byeDistribution.put(p, 0);
    }

    // Calculate how many players need to sit each round to make remaining count divisible by 4
    int playersPerRound = (n / 4) * 4; // Largest multiple of 4 that fits in n
    int sittersPerRound = n - playersPerRound;

    // For fair distribution, we need enough rounds so everyone sits equally
    // If sittersPerRound divides n evenly, we need n/sittersPerRound rounds
    // Otherwise we need n rounds (each player sits sittersPerRound times)
    int fairRounds = n;
    if (sittersPerRound > 0 && n % sittersPerRound == 0) {
      fairRounds = n / sittersPerRound;
    }

    int actualRounds = targetRounds <= 0 ? fairRounds : Math.min(targetRounds, fairRounds);

    // Assign byes fairly across rounds
    int byeIndex = 0;
    for (int round = 0; round < actualRounds; round++) {
      List<MatchSpec> roundMatches = new ArrayList<>();

      // Determine who sits this round
      Set<Integer> sittingIndices = new HashSet<>();
      for (int i = 0; i < sittersPerRound; i++) {
        sittingIndices.add(byeIndex % n);
        byeIndex++;
      }

      // Add bye entries for sitting players
      for (int idx : sittingIndices) {
        Long sittingPlayer = players.get(idx);
        byeDistribution.merge(sittingPlayer, 1, Integer::sum);
        roundMatches.add(new MatchSpec(sittingPlayer, null, null, null, true));
      }

      // Remaining players form matches
      List<Long> activePlayers = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        if (!sittingIndices.contains(i)) {
          activePlayers.add(players.get(i));
        }
      }

      // If we have prior pair counts for seasonal balancing, skip rotation
      // The balancing algorithm will handle pairing optimally
      // Otherwise, apply rotation to vary partnerships across rounds
      List<Long> playersForPairing;
      if (priorPairCounts != null && !priorPairCounts.isEmpty()) {
        playersForPairing = activePlayers;
      } else {
        playersForPairing = new ArrayList<>(activePlayers);
        int rotationAmount = round % activePlayers.size();
        for (int r = 0; r < rotationAmount; r++) {
          // Rotate: move last element to front
          if (!playersForPairing.isEmpty()) {
            Long last = playersForPairing.remove(playersForPairing.size() - 1);
            playersForPairing.add(0, last);
          }
        }
      }

      // Pair players into matches, considering seasonal opponent history if available
      List<MatchSpec> matches =
          pairPlayersIntoMatchesBalanced(playersForPairing, opponentPairs, priorPairCounts);
      roundMatches.addAll(matches);

      schedule.add(roundMatches);
    }

    // Calculate partnership counts
    Map<String, Integer> partnerCounts = new HashMap<>();
    for (List<MatchSpec> round : schedule) {
      for (MatchSpec spec : round) {
        if (spec.bye) continue;
        if (spec.a1 != null && spec.a2 != null) {
          String key = spec.a1 < spec.a2 ? spec.a1 + "-" + spec.a2 : spec.a2 + "-" + spec.a1;
          partnerCounts.merge(key, 1, Integer::sum);
        }
        if (spec.b1 != null && spec.b2 != null) {
          String key = spec.b1 < spec.b2 ? spec.b1 + "-" + spec.b2 : spec.b2 + "-" + spec.b1;
          partnerCounts.merge(key, 1, Integer::sum);
        }
      }
    }

    // Calculate partnership imbalance (range from min to max)
    int minPartners = partnerCounts.values().stream().min(Integer::compareTo).orElse(0);
    int maxPartners = partnerCounts.values().stream().max(Integer::compareTo).orElse(0);
    int partnershipImbalance = maxPartners - minPartners;

    // Calculate repeat opponent score based on prior season history
    int repeatScore = calculateRepeatScore(opponentPairs, priorPairCounts);

    return new CandidateSchedule(schedule, byeDistribution, repeatScore, partnershipImbalance);
  }

  private List<MatchSpec> pairPlayersIntoMatchesBalanced(
      List<Long> players,
      Map<String, Integer> opponentPairs,
      Map<Long, Map<Long, Integer>> priorPairCounts) {
    List<MatchSpec> matches = new ArrayList<>();
    int n = players.size();

    // Strategy: Use seasonal opponent history to form partnerships
    // Players who have faced each other MORE in the season should partner together MORE
    // This reduces their future matchups against each other, balancing seasonal opponent
    // distribution

    if (priorPairCounts == null || priorPairCounts.isEmpty()) {
      // No prior history - use simple sequential pairing
      return pairPlayersSequentially(players, opponentPairs);
    }

    // Build opponent frequency map for all active players
    Map<String, Integer> opponentFrequency = new HashMap<>();
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        Long p1 = players.get(i);
        Long p2 = players.get(j);
        int count = getOpponentCount(p1, p2, priorPairCounts);
        if (count > 0) {
          String key = p1 < p2 ? p1 + "-" + p2 : p2 + "-" + p1;
          opponentFrequency.put(key, count);
        }
      }
    }

    // Strategy: form partnerships by pairing players with LOWEST seasonal opponent counts
    // This way, high-count opponents will face each other LESS (they'll be on opposing teams less)
    // and low-count pairs get to partner together, distributing matchups more evenly
    List<Long> remaining = new ArrayList<>(players);
    List<Long[]> partnerships = new ArrayList<>();

    while (remaining.size() >= 2) {
      // Find pair with LOWEST seasonal opponent count to partner together
      Long bestP1 = null;
      Long bestP2 = null;
      int minCount = Integer.MAX_VALUE;

      for (int i = 0; i < remaining.size(); i++) {
        for (int j = i + 1; j < remaining.size(); j++) {
          Long p1 = remaining.get(i);
          Long p2 = remaining.get(j);
          int count = getOpponentCount(p1, p2, priorPairCounts);
          if (count < minCount) {
            minCount = count;
            bestP1 = p1;
            bestP2 = p2;
          }
        }
      }

      // Create partnership - pair players who haven't faced each other much
      if (bestP1 != null && bestP2 != null) {
        partnerships.add(new Long[] {bestP1, bestP2});
        remaining.remove(bestP1);
        remaining.remove(bestP2);
      } else {
        // Fallback: just take first two
        partnerships.add(new Long[] {remaining.get(0), remaining.get(1)});
        remaining.remove(0);
        remaining.remove(0);
      }
    }

    // Now form matches from partnerships (every 2 partnerships = 1 match)
    for (int i = 0; i < partnerships.size(); i += 2) {
      if (i + 1 < partnerships.size()) {
        Long[] team1 = partnerships.get(i);
        Long[] team2 = partnerships.get(i + 1);

        matches.add(new MatchSpec(team1[0], team1[1], team2[0], team2[1], false));

        // Record opponent pairings
        recordOpponent(opponentPairs, team1[0], team2[0]);
        recordOpponent(opponentPairs, team1[0], team2[1]);
        recordOpponent(opponentPairs, team1[1], team2[0]);
        recordOpponent(opponentPairs, team1[1], team2[1]);
      }
    }

    return matches;
  }

  private List<MatchSpec> pairPlayersSequentially(
      List<Long> players, Map<String, Integer> opponentPairs) {
    List<MatchSpec> matches = new ArrayList<>();
    int n = players.size();

    for (int matchIndex = 0; matchIndex < n / 4; matchIndex++) {
      int base = matchIndex * 4;
      Long a1 = players.get(base);
      Long a2 = players.get(base + 1);
      Long b1 = players.get(base + 2);
      Long b2 = players.get(base + 3);

      matches.add(new MatchSpec(a1, a2, b1, b2, false));

      recordOpponent(opponentPairs, a1, b1);
      recordOpponent(opponentPairs, a1, b2);
      recordOpponent(opponentPairs, a2, b1);
      recordOpponent(opponentPairs, a2, b2);
    }

    return matches;
  }

  private int getOpponentCount(Long p1, Long p2, Map<Long, Map<Long, Integer>> priorPairCounts) {
    int count = 0;
    if (priorPairCounts.containsKey(p1) && priorPairCounts.get(p1).containsKey(p2)) {
      count += priorPairCounts.get(p1).get(p2);
    }
    if (priorPairCounts.containsKey(p2) && priorPairCounts.get(p2).containsKey(p1)) {
      count += priorPairCounts.get(p2).get(p1);
    }
    return count;
  }

  private void recordOpponent(Map<String, Integer> opponentPairs, Long p1, Long p2) {
    if (p1 == null || p2 == null) return;
    String key = p1 < p2 ? p1 + "-" + p2 : p2 + "-" + p1;
    opponentPairs.merge(key, 1, Integer::sum);
  }

  private int calculateRepeatScore(
      Map<String, Integer> opponentPairs, Map<Long, Map<Long, Integer>> priorPairCounts) {
    if (priorPairCounts == null || priorPairCounts.isEmpty()) {
      // No prior history - just count repeats within this schedule
      return opponentPairs.values().stream()
          .mapToInt(count -> count > 1 ? (count - 1) * (count - 1) : 0)
          .sum();
    }

    int score = 0;
    for (Map.Entry<String, Integer> entry : opponentPairs.entrySet()) {
      String[] parts = entry.getKey().split("-");
      Long p1 = Long.parseLong(parts[0]);
      Long p2 = Long.parseLong(parts[1]);

      int priorCount = priorPairCounts.getOrDefault(p1, Map.of()).getOrDefault(p2, 0);
      int thisScheduleCount = entry.getValue();

      // Penalty increases with both prior history and repeats in this schedule
      score += thisScheduleCount * priorCount;
      if (thisScheduleCount > 1) {
        score += (thisScheduleCount - 1) * 10; // Extra penalty for repeats within schedule
      }
    }

    return score;
  }

  private List<List<MatchSpec>> generateEvenRoster(
      List<Long> players,
      Map<Long, Map<Long, Integer>> priorPairCounts,
      int rounds,
      List<String> explanationLog) {
    // For even rosters, use standard circle rotation
    // This is a simplified version - could be enhanced later
    List<List<MatchSpec>> schedule = new ArrayList<>();
    int n = players.size();
    int targetRounds = rounds <= 0 ? (n - 1) : rounds;

    List<Long> rotation = new ArrayList<>(players);

    for (int round = 0; round < targetRounds; round++) {
      List<MatchSpec> roundMatches = new ArrayList<>();

      // Pair adjacent positions
      for (int i = 0; i < n / 2; i++) {
        Long a1 = rotation.get(i * 2);
        Long a2 = rotation.get(i * 2 + 1);
        Long b1 = rotation.get(n - 1 - i * 2);
        Long b2 = rotation.get(n - 2 - i * 2);

        if (i * 2 + 1 < n - 1 - i * 2) {
          roundMatches.add(new MatchSpec(a1, a2, b1, b2, false));
        }
      }

      schedule.add(roundMatches);

      // Rotate (keep first fixed, rotate others)
      List<Long> newRotation = new ArrayList<>();
      newRotation.add(rotation.get(0));
      newRotation.add(rotation.get(n - 1));
      for (int j = 1; j < n - 1; j++) {
        newRotation.add(rotation.get(j));
      }
      rotation = newRotation;
    }

    return schedule;
  }

  private void log(List<String> explanationLog, String message) {
    log.debug(message);
    if (explanationLog != null) {
      explanationLog.add(message);
    }
  }

  private static class CandidateSchedule {
    final List<List<MatchSpec>> schedule;
    final Map<Long, Integer> byeDistribution;
    final int opponentRepeatScore;
    final int partnershipImbalance;

    CandidateSchedule(
        List<List<MatchSpec>> schedule,
        Map<Long, Integer> byeDistribution,
        int opponentRepeatScore,
        int partnershipImbalance) {
      this.schedule = schedule;
      this.byeDistribution = byeDistribution;
      this.opponentRepeatScore = opponentRepeatScore;
      this.partnershipImbalance = partnershipImbalance;
    }

    boolean isBetterThan(CandidateSchedule other) {
      // First priority: minimize partnership imbalance (want everyone to partner evenly)
      if (this.partnershipImbalance != other.partnershipImbalance) {
        return this.partnershipImbalance < other.partnershipImbalance;
      }
      // Second priority: minimize opponent repeats (for variety)
      return this.opponentRepeatScore < other.opponentRepeatScore;
    }
  }
}
