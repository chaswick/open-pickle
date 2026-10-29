package com.w3llspring.fhpb.web.service.roundrobin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduler focused on two invariants for rotating-partner round robins: everyone sits as evenly as
 * possible, and partner repeats are avoided for as long as possible.
 *
 * <p>Opponent pairing is then chosen round-by-round to prefer fresh opponent matchups before
 * repeats.
 */
public class FairByeRoundRobinScheduler implements RoundRobinScheduler {

  private static final Logger log = LoggerFactory.getLogger(FairByeRoundRobinScheduler.class);

  private Random rng = new Random();

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
    Collections.shuffle(players, rng);
    int playerCount = players.size();
    if (playerCount < 4) {
      log(explanationLog, "FairByeScheduler: fewer than four participants. No schedule generated.");
      return List.of();
    }

    int targetRounds = resolveTargetRounds(playerCount, rounds);
    if (playerCount == 4 || playerCount == 8 || playerCount == 16 || playerCount == 32) {
      return buildBinarySchedule(players, targetRounds, priorPairCounts, explanationLog);
    }
    List<TemplateRound> templateRounds = buildTemplateRounds(players, targetRounds);
    List<Team> extraByeTeams = selectExtraByeTeams(templateRounds, players, playerCount);

    Map<Long, Integer> byeCounts = new LinkedHashMap<>();
    for (Long player : players) {
      byeCounts.put(player, 0);
    }

    Map<Long, Map<Long, Integer>> opponentCounts = new HashMap<>();
    List<List<MatchSpec>> schedule = new ArrayList<>();

    log(
        explanationLog,
        "FairByeScheduler: generating "
            + targetRounds
            + " round(s) for "
            + playerCount
            + " participants.");

    for (int roundIndex = 0; roundIndex < templateRounds.size(); roundIndex++) {
      TemplateRound templateRound = templateRounds.get(roundIndex);
      Team extraByeTeam = extraByeTeams.get(roundIndex);
      List<MatchSpec> roundSpecs = new ArrayList<>();

      for (Long soloBye : templateRound.soloByes) {
        roundSpecs.add(new MatchSpec(soloBye, null, null, null, true));
        incrementBye(byeCounts, soloBye);
      }

      List<Team> activeTeams = new ArrayList<>();
      for (Team team : templateRound.realTeams) {
        if (extraByeTeam != null && extraByeTeam.equals(team)) {
          roundSpecs.add(new MatchSpec(team.first, team.second, null, null, true));
          incrementBye(byeCounts, team.first);
          incrementBye(byeCounts, team.second);
        } else {
          activeTeams.add(team);
        }
      }

      if (activeTeams.size() % 2 == 1 && !activeTeams.isEmpty()) {
        Team fallbackBye = activeTeams.remove(activeTeams.size() - 1);
        roundSpecs.add(new MatchSpec(fallbackBye.first, fallbackBye.second, null, null, true));
        incrementBye(byeCounts, fallbackBye.first);
        incrementBye(byeCounts, fallbackBye.second);
      }

      List<TeamPairing> pairings = pairTeamsForRound(activeTeams, opponentCounts, priorPairCounts);
      for (TeamPairing pairing : pairings) {
        Team teamA = pairing.teamA;
        Team teamB = pairing.teamB;
        roundSpecs.add(new MatchSpec(teamA.first, teamA.second, teamB.first, teamB.second, false));
        recordOpponents(opponentCounts, teamA, teamB);
      }

      schedule.add(roundSpecs);
    }

    log(explanationLog, "FairByeScheduler: bye distribution " + byeCounts);
    return schedule;
  }

  private List<List<MatchSpec>> buildBinarySchedule(
      List<Long> players,
      int rounds,
      Map<Long, Map<Long, Integer>> history,
      List<String> explanationLog) {
    int count = players.size();
    int polynomial =
        switch (count) {
          case 4 -> 0b111;
          case 8 -> 0b1011;
          case 16 -> 0b10011;
          case 32 -> 0b100101;
          default -> throw new IllegalArgumentException("Unsupported binary roster size");
        };
    // Label players in a binary field. Each nonzero difference is a partnership
    // round. Its two opponent differences run through every other player twice
    // over a full cycle. This avoids the circle template's eight-player trap:
    // unique partners alone can still force the same opponents repeatedly.
    List<List<MatchSpec>> cycle = new ArrayList<>();
    for (int partner = 1; partner < count; partner++) {
      int opponent = partner << 1;
      if (opponent >= count) opponent ^= polynomial;
      boolean[] used = new boolean[count];
      List<MatchSpec> round = new ArrayList<>();
      for (int a = 0; a < count; a++) {
        if (used[a]) continue;
        int b = a ^ partner, c = a ^ opponent, d = b ^ opponent;
        used[a] = used[b] = used[c] = used[d] = true;
        round.add(
            new MatchSpec(players.get(a), players.get(b), players.get(c), players.get(d), false));
      }
      cycle.add(round);
    }
    // For a short session, prefer the rounds with fresher historical opponents.
    cycle.sort(
        Comparator.comparingLong(
            round -> {
              long cost = 0;
              for (MatchSpec match : round) {
                for (Long a : List.of(match.a1, match.a2)) {
                  for (Long b : List.of(match.b1, match.b2)) cost += lookupCount(history, a, b);
                }
              }
              return cost;
            }));
    List<List<MatchSpec>> schedule = new ArrayList<>();
    for (int r = 0; r < rounds; r++) {
      List<MatchSpec> round = new ArrayList<>();
      for (MatchSpec match : cycle.get(r % cycle.size())) {
        round.add(new MatchSpec(match.a1, match.a2, match.b1, match.b2, false));
      }
      schedule.add(round);
    }
    log(
        explanationLog,
        "FairByeScheduler: generating "
            + rounds
            + " round(s) for "
            + count
            + " participants with balanced partnerships and opponents; no byes.");
    return schedule;
  }

  private int resolveTargetRounds(int playerCount, int requestedRounds) {
    int sittersPerRound = playerCount % 4;
    int fairRounds;
    if (sittersPerRound == 0) {
      fairRounds = playerCount - 1;
    } else if (playerCount % sittersPerRound == 0) {
      fairRounds = playerCount / sittersPerRound;
    } else {
      fairRounds = playerCount;
    }
    if (requestedRounds <= 0) {
      return fairRounds;
    }
    return Math.min(requestedRounds, 30);
  }

  private List<TemplateRound> buildTemplateRounds(List<Long> players, int targetRounds) {
    List<Long> labels = new ArrayList<>(players);
    if (labels.size() % 2 == 1) {
      labels.add(null);
    }

    List<List<int[]>> template = buildTemplate(labels.size());
    int roundsToUse = targetRounds;
    List<TemplateRound> rounds = new ArrayList<>(roundsToUse);

    for (int roundIndex = 0; roundIndex < roundsToUse; roundIndex++) {
      List<Team> realTeams = new ArrayList<>();
      List<Long> soloByes = new ArrayList<>();

      for (int[] pair : template.get(roundIndex % template.size())) {
        Long first = labels.get(pair[0]);
        Long second = labels.get(pair[1]);
        if (first == null && second == null) {
          continue;
        }
        if (first == null || second == null) {
          soloByes.add(first != null ? first : second);
          continue;
        }
        realTeams.add(new Team(first, second));
      }

      rounds.add(new TemplateRound(realTeams, soloByes));
    }

    return rounds;
  }

  private List<List<int[]>> buildTemplate(int participantCountEven) {
    List<List<int[]>> rounds = new ArrayList<>();
    int rotating = participantCountEven - 1;
    for (int round = 0; round < rotating; round++) {
      List<int[]> pairs = new ArrayList<>();
      pairs.add(new int[] {rotating, round});
      for (int i = 1; i < participantCountEven / 2; i++) {
        int first = (round + i) % rotating;
        int second = (round - i + rotating) % rotating;
        pairs.add(new int[] {first, second});
      }
      rounds.add(pairs);
    }
    return rounds;
  }

  private List<Team> selectExtraByeTeams(
      List<TemplateRound> rounds, List<Long> players, int count) {
    List<Team> chosen = new ArrayList<>(Collections.nCopies(rounds.size(), null));
    if (count % 4 < 2) return chosen;
    Map<Long, Integer> index = new HashMap<>();
    for (int i = 0; i < count; i++) index.put(players.get(i), i);
    // Prefer fair prefixes so a session that stops early still shares playing time.
    for (boolean prefixes : new boolean[] {true, false}) {
      if (findBalancedByes(rounds, index, chosen, prefixes, 150_000)) return chosen;
    }
    // Some larger rosters exhaust a chronological search before finding balanced
    // byes. Search the same constraints in another order; keep the actual rounds
    // and their partnerships unchanged. Every attempt has a fixed work budget.
    List<Integer> order = new ArrayList<>();
    for (int r = 0; r < rounds.size(); r++) order.add(r);
    // Constraint ordering should not make fairness depend on the shuffled roster.
    Random searchOrder = new Random(0);
    for (int attempt = 0; attempt < 24; attempt++) {
      Collections.shuffle(order, searchOrder);
      List<TemplateRound> reordered = order.stream().map(rounds::get).toList();
      List<Team> reorderedChoices = new ArrayList<>(Collections.nCopies(rounds.size(), null));
      if (findBalancedByes(reordered, index, reorderedChoices, false, 25_000)) {
        for (int r = 0; r < order.size(); r++) chosen.set(order.get(r), reorderedChoices.get(r));
        return chosen;
      }
    }
    // Retain a valid schedule if bounded searches cannot meet the exact bounds.
    // Include solo byes in the totals used for every fallback decision.
    int[] totals = new int[count];
    for (int r = 0; r < rounds.size(); r++) {
      TemplateRound round = rounds.get(r);
      for (Long id : round.soloByes) totals[index.get(id)]++;
      Team team =
          round.realTeams.stream()
              .min(
                  Comparator.comparingInt(
                      t -> totals[index.get(t.first)] + totals[index.get(t.second)]))
              .orElseThrow();
      chosen.set(r, team);
      totals[index.get(team.first)]++;
      totals[index.get(team.second)]++;
    }
    return chosen;
  }

  private boolean findBalancedByes(
      List<TemplateRound> rounds,
      Map<Long, Integer> index,
      List<Team> chosen,
      boolean prefixes,
      int budget) {
    int count = index.size();
    int[][] futureSolo = new int[rounds.size() + 1][count];
    int[][] futureAvailability = new int[rounds.size() + 1][count];
    for (int r = rounds.size() - 1; r >= 0; r--) {
      futureSolo[r] = futureSolo[r + 1].clone();
      futureAvailability[r] = futureAvailability[r + 1].clone();
      for (Long id : rounds.get(r).soloByes) futureSolo[r][index.get(id)]++;
      for (Team team : rounds.get(r).realTeams) {
        futureAvailability[r][index.get(team.first)]++;
        futureAvailability[r][index.get(team.second)]++;
      }
    }
    int[] previous = new int[count];
    Arrays.fill(previous, -2);
    return chooseByes(
        0,
        rounds,
        index,
        new int[count],
        previous,
        futureSolo,
        futureAvailability,
        count % 4,
        chosen,
        prefixes,
        new int[] {budget},
        new HashSet<>());
  }

  private boolean chooseByes(
      int r,
      List<TemplateRound> rounds,
      Map<Long, Integer> index,
      int[] totals,
      int[] previous,
      int[][] futureSolo,
      int[][] futureAvailability,
      int sitters,
      List<Team> chosen,
      boolean prefixes,
      int[] budget,
      Set<String> failed) {
    if (--budget[0] < 0) return false;
    int count = totals.length;
    int minTotal = rounds.size() * sitters / count;
    int maxTotal = (rounds.size() * sitters + count - 1) / count;
    if (r == rounds.size()) {
      return Arrays.stream(totals).allMatch(n -> n >= minTotal && n <= maxTotal);
    }
    String state = r + ":" + Arrays.toString(totals);
    if (failed.contains(state)) return false;
    TemplateRound round = rounds.get(r);
    for (Long id : round.soloByes) totals[index.get(id)]++;
    List<Team> candidates = new ArrayList<>(round.realTeams);
    candidates.sort(
        Comparator.comparingInt(
            t -> {
              int a = index.get(t.first), b = index.get(t.second);
              return 100 * (totals[a] + totals[b])
                  + 20 * (futureSolo[r + 1][a] + futureSolo[r + 1][b])
                  + (previous[a] == r - 1 ? 30 : 0)
                  + (previous[b] == r - 1 ? 30 : 0);
            }));
    for (Team team : candidates) {
      int a = index.get(team.first), b = index.get(team.second);
      totals[a]++;
      totals[b]++;
      int low = prefixes ? (r + 1) * sitters / count : 0;
      int high = prefixes ? ((r + 1) * sitters + count - 1) / count : maxTotal;
      boolean possible = true;
      for (int i = 0; i < count; i++) {
        if (totals[i] < low
            || totals[i] > high
            || totals[i] + futureSolo[r + 1][i] > maxTotal
            || totals[i] + futureSolo[r + 1][i] + futureAvailability[r + 1][i] < minTotal) {
          possible = false;
          break;
        }
      }
      if (possible) {
        int[] nextPrevious = previous.clone();
        for (Long id : round.soloByes) nextPrevious[index.get(id)] = r;
        nextPrevious[a] = r;
        nextPrevious[b] = r;
        chosen.set(r, team);
        if (chooseByes(
            r + 1,
            rounds,
            index,
            totals,
            nextPrevious,
            futureSolo,
            futureAvailability,
            sitters,
            chosen,
            prefixes,
            budget,
            failed)) return true;
      }
      totals[a]--;
      totals[b]--;
    }
    for (Long id : round.soloByes) totals[index.get(id)]--;
    chosen.set(r, null);
    failed.add(state);
    return false;
  }

  private List<TeamPairing> pairTeamsForRound(
      List<Team> teams,
      Map<Long, Map<Long, Integer>> opponentCounts,
      Map<Long, Map<Long, Integer>> priorPairCounts) {
    if (teams.isEmpty()) {
      return List.of();
    }
    return buildPairingPlan(
            teams, (1 << teams.size()) - 1, opponentCounts, priorPairCounts, new HashMap<>())
        .pairings;
  }

  private PairingPlan buildPairingPlan(
      List<Team> teams,
      int mask,
      Map<Long, Map<Long, Integer>> opponentCounts,
      Map<Long, Map<Long, Integer>> priorPairCounts,
      Map<Integer, PairingPlan> plans) {
    if (mask == 0) {
      return new PairingPlan(0, List.of());
    }
    // Costs are fixed for this round. Reuse the best plan for each remaining set of
    // teams instead of exploring its permutations again (especially on eight courts).
    PairingPlan cached = plans.get(mask);
    if (cached != null) {
      return cached;
    }

    int firstIndex = Integer.numberOfTrailingZeros(mask);
    int remainingMask = mask & ~(1 << firstIndex);
    PairingPlan best = null;

    for (int secondIndex = firstIndex + 1; secondIndex < teams.size(); secondIndex++) {
      if ((remainingMask & (1 << secondIndex)) == 0) {
        continue;
      }

      Team first = teams.get(firstIndex);
      Team second = teams.get(secondIndex);
      int pairCost = opponentCost(first, second, opponentCounts, priorPairCounts);
      PairingPlan remainder =
          buildPairingPlan(
              teams, remainingMask & ~(1 << secondIndex), opponentCounts, priorPairCounts, plans);

      List<TeamPairing> pairings = new ArrayList<>();
      pairings.add(new TeamPairing(first, second));
      pairings.addAll(remainder.pairings);
      PairingPlan candidate = new PairingPlan(pairCost + remainder.cost, pairings);

      if (best == null || candidate.cost < best.cost) {
        best = candidate;
      }
    }

    PairingPlan result = best == null ? new PairingPlan(0, List.of()) : best;
    plans.put(mask, result);
    return result;
  }

  private int opponentCost(
      Team first,
      Team second,
      Map<Long, Map<Long, Integer>> opponentCounts,
      Map<Long, Map<Long, Integer>> priorPairCounts) {
    int cost = 0;
    for (Long a : first.players()) {
      for (Long b : second.players()) {
        int current = lookupCount(opponentCounts, a, b);
        int prior = lookupCount(priorPairCounts, a, b);
        if (current > 0) {
          cost += 1000;
        }
        cost += current * 25;
        cost += prior;
      }
    }
    return cost;
  }

  private int lookupCount(Map<Long, Map<Long, Integer>> counts, Long first, Long second) {
    if (counts == null) {
      return 0;
    }
    return counts.getOrDefault(first, Map.of()).getOrDefault(second, 0);
  }

  private void recordOpponents(
      Map<Long, Map<Long, Integer>> opponentCounts, Team first, Team second) {
    for (Long a : first.players()) {
      for (Long b : second.players()) {
        opponentCounts.computeIfAbsent(a, ignored -> new HashMap<>()).merge(b, 1, Integer::sum);
        opponentCounts.computeIfAbsent(b, ignored -> new HashMap<>()).merge(a, 1, Integer::sum);
      }
    }
  }

  private void incrementBye(Map<Long, Integer> byeCounts, Long player) {
    if (player == null) {
      return;
    }
    byeCounts.merge(player, 1, Integer::sum);
  }

  private void log(List<String> explanationLog, String message) {
    log.debug(message);
    if (explanationLog != null) {
      explanationLog.add(message);
    }
  }

  private static final class TemplateRound {
    private final List<Team> realTeams;
    private final List<Long> soloByes;

    private TemplateRound(List<Team> realTeams, List<Long> soloByes) {
      this.realTeams = realTeams;
      this.soloByes = soloByes;
    }
  }

  private static final class Team {
    private final Long first;
    private final Long second;

    private Team(Long first, Long second) {
      if (Objects.equals(first, second)) {
        throw new IllegalArgumentException("Team cannot contain duplicate players.");
      }
      if (first <= second) {
        this.first = first;
        this.second = second;
      } else {
        this.first = second;
        this.second = first;
      }
    }

    private List<Long> players() {
      return List.of(first, second);
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof Team team)) {
        return false;
      }
      return Objects.equals(first, team.first) && Objects.equals(second, team.second);
    }

    @Override
    public int hashCode() {
      return Objects.hash(first, second);
    }
  }

  private static final class TeamPairing {
    private final Team teamA;
    private final Team teamB;

    private TeamPairing(Team teamA, Team teamB) {
      this.teamA = teamA;
      this.teamB = teamB;
    }
  }

  private static final class PairingPlan {
    private final int cost;
    private final List<TeamPairing> pairings;

    private PairingPlan(int cost, List<TeamPairing> pairings) {
      this.cost = cost;
      this.pairings = pairings;
    }
  }
}
