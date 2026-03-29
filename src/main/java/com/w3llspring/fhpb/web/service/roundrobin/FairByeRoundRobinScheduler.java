package com.w3llspring.fhpb.web.service.roundrobin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Scheduler focused on two invariants for rotating-partner round robins:
 * everyone sits as evenly as possible, and partner repeats are avoided for as long as possible.
 *
 * Opponent pairing is then chosen round-by-round to prefer fresh opponent matchups before repeats.
 */
public class FairByeRoundRobinScheduler implements RoundRobinScheduler {

    private static final Logger log = LoggerFactory.getLogger(FairByeRoundRobinScheduler.class);

    private Random rng = new Random();

    void setSeedForTests(Long seed) {
        rng = seed == null ? new Random() : new Random(seed);
    }

    @Override
    public List<List<MatchSpec>> generateWithLog(List<Long> participantIds,
                                                 Map<Long, Map<Long, Integer>> priorPairCounts,
                                                 int rounds,
                                                 List<String> explanationLog) {
        if (participantIds == null || participantIds.isEmpty()) {
            return List.of();
        }

        List<Long> players = new ArrayList<>(participantIds);
        int playerCount = players.size();
        if (playerCount < 4) {
            log(explanationLog, "FairByeScheduler: fewer than four participants. No schedule generated.");
            return List.of();
        }

        int targetRounds = resolveTargetRounds(playerCount, rounds);
        List<TemplateRound> templateRounds = buildTemplateRounds(players, targetRounds);
        Map<Long, Integer> extraByeTargets = buildExtraByeTargets(players, playerCount, targetRounds);
        List<Team> extraByeTeams = selectExtraByeTeams(templateRounds, extraByeTargets);

        Map<Long, Integer> byeCounts = new LinkedHashMap<>();
        for (Long player : players) {
            byeCounts.put(player, 0);
        }

        Map<Long, Map<Long, Integer>> opponentCounts = new HashMap<>();
        List<List<MatchSpec>> schedule = new ArrayList<>();

        log(explanationLog,
                "FairByeScheduler: generating " + targetRounds + " round(s) for " + playerCount + " participants.");

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
        return Math.min(requestedRounds, fairRounds);
    }

    private List<TemplateRound> buildTemplateRounds(List<Long> players, int targetRounds) {
        List<Long> labels = new ArrayList<>(players);
        if (labels.size() % 2 == 1) {
            labels.add(null);
        }

        List<List<int[]>> template = buildTemplate(labels.size());
        int roundsToUse = Math.min(targetRounds, template.size());
        List<TemplateRound> rounds = new ArrayList<>(roundsToUse);

        for (int roundIndex = 0; roundIndex < roundsToUse; roundIndex++) {
            List<Team> realTeams = new ArrayList<>();
            List<Long> soloByes = new ArrayList<>();

            for (int[] pair : template.get(roundIndex)) {
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
            pairs.add(new int[]{rotating, round});
            for (int i = 1; i < participantCountEven / 2; i++) {
                int first = (round + i) % rotating;
                int second = (round - i + rotating) % rotating;
                pairs.add(new int[]{first, second});
            }
            rounds.add(pairs);
        }
        return rounds;
    }

    private Map<Long, Integer> buildExtraByeTargets(List<Long> players, int playerCount, int rounds) {
        Map<Long, Integer> targets = new LinkedHashMap<>();
        for (Long player : players) {
            targets.put(player, 0);
        }

        int realTeamsPerRound = playerCount / 2;
        int matchesPerRound = (playerCount - (playerCount % 4)) / 4;
        int extraByeTeamsPerRound = Math.max(0, realTeamsPerRound - (matchesPerRound * 2));
        if (extraByeTeamsPerRound == 0) {
            return targets;
        }

        int totalExtraByeSeats = rounds * extraByeTeamsPerRound * 2;
        int baseTarget = totalExtraByeSeats / playerCount;
        int remainder = totalExtraByeSeats % playerCount;

        for (Long player : players) {
            targets.put(player, baseTarget);
        }
        for (int i = 0; i < remainder; i++) {
            Long player = players.get(i);
            targets.put(player, targets.get(player) + 1);
        }

        return targets;
    }

    private List<Team> selectExtraByeTeams(List<TemplateRound> rounds, Map<Long, Integer> extraByeTargets) {
        boolean needsExtraByes = extraByeTargets.values().stream().anyMatch(target -> target > 0);
        List<Team> chosen = new ArrayList<>();
        for (int i = 0; i < rounds.size(); i++) {
            chosen.add(null);
        }
        if (!needsExtraByes) {
            return chosen;
        }

        List<Long> players = new ArrayList<>(extraByeTargets.keySet());
        Map<Long, Integer> playerIndex = new HashMap<>();
        for (int i = 0; i < players.size(); i++) {
            playerIndex.put(players.get(i), i);
        }

        int[][] futureAvailability = buildFutureAvailability(rounds, players, playerIndex);
        int[] targetCounts = new int[players.size()];
        int[] currentCounts = new int[players.size()];
        for (int i = 0; i < players.size(); i++) {
            targetCounts[i] = extraByeTargets.get(players.get(i));
        }

        if (!chooseExtraByeTeam(0, rounds, futureAvailability, currentCounts, targetCounts, chosen, playerIndex)) {
            for (int roundIndex = 0; roundIndex < rounds.size(); roundIndex++) {
                Team greedy = rounds.get(roundIndex).realTeams.stream()
                        .min(Comparator.comparingInt(team ->
                                currentCounts[playerIndex.get(team.first)] + currentCounts[playerIndex.get(team.second)]))
                        .orElse(null);
                chosen.set(roundIndex, greedy);
                if (greedy != null) {
                    currentCounts[playerIndex.get(greedy.first)]++;
                    currentCounts[playerIndex.get(greedy.second)]++;
                }
            }
        }

        return chosen;
    }

    private int[][] buildFutureAvailability(List<TemplateRound> rounds,
                                            List<Long> players,
                                            Map<Long, Integer> playerIndex) {
        int[][] future = new int[rounds.size() + 1][players.size()];
        for (int roundIndex = rounds.size() - 1; roundIndex >= 0; roundIndex--) {
            System.arraycopy(future[roundIndex + 1], 0, future[roundIndex], 0, players.size());
            for (Team team : rounds.get(roundIndex).realTeams) {
                future[roundIndex][playerIndex.get(team.first)]++;
                future[roundIndex][playerIndex.get(team.second)]++;
            }
        }
        return future;
    }

    private boolean chooseExtraByeTeam(int roundIndex,
                                       List<TemplateRound> rounds,
                                       int[][] futureAvailability,
                                       int[] currentCounts,
                                       int[] targetCounts,
                                       List<Team> chosen,
                                       Map<Long, Integer> playerIndex) {
        if (roundIndex == rounds.size()) {
            for (int i = 0; i < targetCounts.length; i++) {
                if (currentCounts[i] != targetCounts[i]) {
                    return false;
                }
            }
            return true;
        }

        List<Team> candidates = new ArrayList<>(rounds.get(roundIndex).realTeams);
        candidates.sort(Comparator.comparingInt(team ->
                currentCounts[playerIndex.get(team.first)] + currentCounts[playerIndex.get(team.second)]));

        for (Team team : candidates) {
            int firstIndex = playerIndex.get(team.first);
            int secondIndex = playerIndex.get(team.second);
            if (currentCounts[firstIndex] >= targetCounts[firstIndex]
                    || currentCounts[secondIndex] >= targetCounts[secondIndex]) {
                continue;
            }

            currentCounts[firstIndex]++;
            currentCounts[secondIndex]++;

            boolean feasible = true;
            for (int i = 0; i < targetCounts.length; i++) {
                if (currentCounts[i] > targetCounts[i]
                        || currentCounts[i] + futureAvailability[roundIndex + 1][i] < targetCounts[i]) {
                    feasible = false;
                    break;
                }
            }

            if (feasible) {
                chosen.set(roundIndex, team);
                if (chooseExtraByeTeam(
                        roundIndex + 1,
                        rounds,
                        futureAvailability,
                        currentCounts,
                        targetCounts,
                        chosen,
                        playerIndex)) {
                    return true;
                }
                chosen.set(roundIndex, null);
            }

            currentCounts[firstIndex]--;
            currentCounts[secondIndex]--;
        }

        return false;
    }

    private List<TeamPairing> pairTeamsForRound(List<Team> teams,
                                                Map<Long, Map<Long, Integer>> opponentCounts,
                                                Map<Long, Map<Long, Integer>> priorPairCounts) {
        if (teams.isEmpty()) {
            return List.of();
        }
        return buildPairingPlan(teams, (1 << teams.size()) - 1, opponentCounts, priorPairCounts).pairings;
    }

    private PairingPlan buildPairingPlan(List<Team> teams,
                                         int mask,
                                         Map<Long, Map<Long, Integer>> opponentCounts,
                                         Map<Long, Map<Long, Integer>> priorPairCounts) {
        if (mask == 0) {
            return new PairingPlan(0, List.of());
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
            PairingPlan remainder = buildPairingPlan(
                    teams,
                    remainingMask & ~(1 << secondIndex),
                    opponentCounts,
                    priorPairCounts);

            List<TeamPairing> pairings = new ArrayList<>();
            pairings.add(new TeamPairing(first, second));
            pairings.addAll(remainder.pairings);
            PairingPlan candidate = new PairingPlan(pairCost + remainder.cost, pairings);

            if (best == null || candidate.cost < best.cost) {
                best = candidate;
            }
        }

        return best == null ? new PairingPlan(0, List.of()) : best;
    }

    private int opponentCost(Team first,
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

    private void recordOpponents(Map<Long, Map<Long, Integer>> opponentCounts, Team first, Team second) {
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
