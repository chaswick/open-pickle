package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.model.*;
import com.w3llspring.fhpb.web.model.RoundRobinEntry.Position;
import java.util.*;
import org.springframework.stereotype.Component;

/** Match facts belong to the people who played; fixed-team placements belong to stable teams. */
@Component
public class RoundRobinTrophyStats {
  private final TrophyAwardService sharedStats;

  public RoundRobinTrophyStats(TrophyAwardService sharedStats) {
    this.sharedStats = sharedStats;
  }

  public record Result(
      Map<Long, User> users,
      Map<Long, Map<String, Double>> metrics,
      Map<Long, String> awardUnits) {}

  private static class Standing {
    int wins;
    int points;
    int rank;

    void record(boolean won, int pointsFor) {
      if (won) wins++;
      points += pointsFor;
    }
  }

  public Result calculate(
      RoundRobin run, List<RoundRobinEntry> entries, List<Match> confirmed, User host) {
    Map<Long, Map<String, Double>> metrics = sharedStats.buildMatchMetrics(confirmed);
    Map<Long, User> people = new LinkedHashMap<>();
    Map<Long, RoundRobinEntry> assignments = new HashMap<>();
    entries.stream()
        .filter(e -> e.getMatchId() != null)
        .forEach(e -> assignments.put(e.getMatchId(), e));
    Map<String, Standing> standings = new LinkedHashMap<>();
    Map<Long, Set<String>> userUnits = new HashMap<>();
    Map<Long, Map<Long, Integer>> partnerWins = new HashMap<>();
    Set<String> participants = new HashSet<>();
    boolean fixed = run.getFormat() == RoundRobin.Format.FIXED_TEAMS;
    for (Match match : confirmed) {
      RoundRobinEntry entry = assignments.get(match.getId());
      if (entry == null) continue;
      User[] players = {match.getA1(), match.getA2(), match.getB1(), match.getB2()};
      boolean[] guests = {
        match.isA1Guest(), match.isA2Guest(), match.isB1Guest(), match.isB2Guest()
      };
      String[] keys = new String[4];
      for (int i = 0; i < 4; i++) {
        keys[i] =
            !guests[i] && players[i] != null
                ? "user:" + players[i].getId()
                : guests[i]
                    ? "guest:"
                        + (entry.slotId(Position.values()[i]) != null
                            ? entry.slotId(Position.values()[i])
                            : entry.getId() + ":" + i)
                        + ":"
                        + entry.guestName(Position.values()[i])
                    : null;
        if (keys[i] != null) participants.add(keys[i]);
      }
      String[] teams = {teamKey(entry, keys, 0), teamKey(entry, keys, 2)};
      if (fixed) {
        for (int team = 0; team < 2; team++)
          if (teams[team] != null)
            standings
                .computeIfAbsent(teams[team], k -> new Standing())
                .record(
                    team == 0
                        ? match.getScoreA() > match.getScoreB()
                        : match.getScoreB() > match.getScoreA(),
                    team == 0 ? match.getScoreA() : match.getScoreB());
      }
      for (int i = 0; i < 4; i++) {
        if (keys[i] == null) continue;
        int team = i / 2;
        boolean won =
            team == 0
                ? match.getScoreA() > match.getScoreB()
                : match.getScoreB() > match.getScoreA();
        int pointsFor = team == 0 ? match.getScoreA() : match.getScoreB();
        String unit = fixed ? teams[team] : keys[i];
        if (!fixed) standings.computeIfAbsent(unit, k -> new Standing()).record(won, pointsFor);
        if (guests[i] || players[i] == null) continue;
        User user = players[i];
        people.put(user.getId(), user);
        int partner = i ^ 1;
        if (won && !guests[partner] && players[partner] != null) {
          partnerWins
              .computeIfAbsent(user.getId(), k -> new HashMap<>())
              .merge(players[partner].getId(), 1, Integer::sum);
        }
        userUnits.computeIfAbsent(user.getId(), k -> new LinkedHashSet<>()).add(unit);
        metrics
            .computeIfAbsent(user.getId(), k -> new HashMap<>())
            .merge("points_for", (double) pointsFor, Double::sum);
      }
    }
    List<Standing> ordered = new ArrayList<>(standings.values());
    ordered.sort(
        Comparator.comparingInt((Standing s) -> s.wins)
            .reversed()
            .thenComparing(Comparator.comparingInt((Standing s) -> s.points).reversed()));
    int rank = 0;
    Standing previous = null;
    for (Standing row : ordered) {
      if (previous == null || previous.wins != row.wins || previous.points != row.points) rank++;
      row.rank = rank;
      previous = row;
    }
    if (host != null) {
      people.putIfAbsent(host.getId(), host);
      metrics.computeIfAbsent(host.getId(), k -> new HashMap<>());
    }
    Map<Long, String> awardUnits = new HashMap<>();
    for (var person : people.entrySet()) {
      Long id = person.getKey();
      var values = metrics.computeIfAbsent(id, k -> new HashMap<>());
      Set<String> playedUnits = userUnits.getOrDefault(id, Set.of());
      // A substitute who played for multiple fixed teams has no single team placement.
      String unit = playedUnits.size() == 1 ? playedUnits.iterator().next() : null;
      Standing standing = unit == null ? null : standings.get(unit);
      values.put("final_rank", standing == null ? 0d : (double) standing.rank);
      values.put("final_total_competitors", (double) standings.size());
      values.put(
          "is_final_overall_last", standing != null && rank > 1 && standing.rank == rank ? 1d : 0d);
      values.put("round_robin_players", (double) participants.size());
      values.put("round_robin_games", (double) confirmed.size());
      values.put("is_round_robin_host", host != null && Objects.equals(host.getId(), id) ? 1d : 0d);
      values.put("rotating_partners", fixed ? 0d : 1d);
      values.put(
          "wins_with_same_partner",
          partnerWins.getOrDefault(id, Map.of()).values().stream()
              .mapToDouble(Integer::doubleValue)
              .max()
              .orElse(0));
      awardUnits.put(id, unit == null ? "user:" + id : unit);
    }
    return new Result(people, metrics, awardUnits);
  }

  private String teamKey(RoundRobinEntry entry, String[] keys, int start) {
    List<String> slots = new ArrayList<>();
    for (int i = start; i < start + 2; i++) {
      if (keys[i] == null) continue;
      Long slot = entry.slotId(Position.values()[i]);
      slots.add(slot != null ? "slot:" + slot : keys[i]);
    }
    Collections.sort(slots);
    return slots.isEmpty() ? null : String.join("+", slots);
  }
}
