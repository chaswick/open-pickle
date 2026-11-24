package com.w3llspring.fhpb.web.service.roundrobin;

import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinScheduler.MatchSpec;
import java.util.*;

/** One round at a time: fair rests first, then close games and varied partners/opponents. */
public final class PerformanceRoundRobinScheduler {
  public List<MatchSpec> nextRound(
      List<Long> participants, Map<Long, Double> ratings, List<List<MatchSpec>> history) {
    return nextRound(participants, ratings, history, 0);
  }

  public List<MatchSpec> nextRound(
      List<Long> participants,
      Map<Long, Double> ratings,
      List<List<MatchSpec>> history,
      long runSeed) {
    List<Long> ids = new ArrayList<>(new TreeSet<>(participants));
    if (ids.size() < 4) throw new IllegalArgumentException("At least four players are required.");
    Map<Long, Double> strengths = relativeStrengths(ids, ratings);
    Map<Long, Integer> byes = new HashMap<>(), lastBye = new HashMap<>();
    Map<String, Integer> partners = new HashMap<>(), opponents = new HashMap<>();
    Set<String> recentPartners = new HashSet<>(), recentOpponents = new HashSet<>();
    for (int r = 0; r < history.size(); r++) {
      for (MatchSpec g : history.get(r)) {
        if (g.bye) {
          for (Long id : players(g)) {
            byes.merge(id, 1, Integer::sum);
            lastBye.put(id, r);
          }
        } else {
          count(partners, g.a1, g.a2);
          count(partners, g.b1, g.b2);
          for (Long a : List.of(g.a1, g.a2))
            for (Long b : List.of(g.b1, g.b2)) count(opponents, a, b);
          if (r == history.size() - 1) {
            recentPartners.add(pair(g.a1, g.a2));
            recentPartners.add(pair(g.b1, g.b2));
            for (Long a : List.of(g.a1, g.a2))
              for (Long b : List.of(g.b1, g.b2)) recentOpponents.add(pair(a, b));
          }
        }
      }
    }
    // A rotating tie break prevents the same IDs always getting the first rests.
    Collections.shuffle(ids, new Random(runSeed));
    Collections.rotate(ids, -(history.size() % ids.size()));
    ids.sort(
        Comparator.<Long>comparingInt(id -> byes.getOrDefault(id, 0))
            .thenComparingInt(id -> lastBye.getOrDefault(id, -1)));
    List<Long> resting = new ArrayList<>(ids.subList(0, ids.size() % 4));
    ids.removeAll(resting);
    ids.sort(
        Comparator.<Long>comparingDouble(id -> rating(strengths, id))
            .reversed()
            .thenComparingLong(id -> id));
    // Try all three team divisions inside each nearby quartet before exchanging players
    // across courts. Otherwise escaping an old partnership can trap local search in
    // unnecessarily mixed skill groups even when a nearby solution exists.
    for (int i = 0; i < ids.size(); i += 4) {
      Long a = ids.get(i), b = ids.get(i + 1), c = ids.get(i + 2), d = ids.get(i + 3);
      List<Long> bestTeam = List.of(a, b, c, d);
      double bestCost = Double.POSITIVE_INFINITY;
      for (List<Long> team :
          List.of(List.of(a, b, c, d), List.of(a, c, b, d), List.of(a, d, b, c))) {
        for (int p = 0; p < 4; p++) ids.set(i + p, team.get(p));
        double candidate =
            cost(ids, strengths, partners, opponents, recentPartners, recentOpponents);
        if (candidate < bestCost) {
          bestCost = candidate;
          bestTeam = team;
        }
      }
      for (int p = 0; p < 4; p++) ids.set(i + p, bestTeam.get(p));
    }
    // Local search can exchange players across neighboring groups and rotate teams within groups.
    Random random =
        new Random(runSeed ^ (31L * history.size() + new TreeSet<>(participants).hashCode()));
    double best = cost(ids, strengths, partners, opponents, recentPartners, recentOpponents);
    for (int i = 0; i < Math.min(12000, ids.size() * 180); i++) {
      int a = random.nextInt(ids.size()), b = random.nextInt(ids.size());
      Collections.swap(ids, a, b);
      double candidate = cost(ids, strengths, partners, opponents, recentPartners, recentOpponents);
      if (candidate < best - 1e-8) best = candidate;
      else Collections.swap(ids, a, b);
    }
    List<MatchSpec> games = new ArrayList<>();
    for (int i = 0; i < ids.size(); i += 4)
      games.add(new MatchSpec(ids.get(i), ids.get(i + 1), ids.get(i + 2), ids.get(i + 3), false));
    for (Long id : resting) games.add(new MatchSpec(id, null, null, null, true));
    return games;
  }

  private Map<Long, Double> relativeStrengths(List<Long> ids, Map<Long, Double> ratings) {
    var range = ids.stream().mapToDouble(id -> rating(ratings, id)).summaryStatistics();
    double span = range.getMax() - range.getMin();
    Map<Long, Double> strengths = new HashMap<>();
    // These units only balance scheduling costs; season points are not win probabilities.
    // Preserve ties and relative gaps, independent of the rating's numerical scale or base.
    for (Long id : ids)
      strengths.put(id, span == 0 ? 0 : 1000 * (rating(ratings, id) - range.getMin()) / span);
    return strengths;
  }

  private double cost(
      List<Long> ids,
      Map<Long, Double> ratings,
      Map<String, Integer> partners,
      Map<String, Integer> opponents,
      Set<String> recentPartners,
      Set<String> recentOpponents) {
    double cost = 0;
    for (int i = 0; i < ids.size(); i += 4) {
      Long a = ids.get(i), b = ids.get(i + 1), c = ids.get(i + 2), d = ids.get(i + 3);
      double ra = rating(ratings, a),
          rb = rating(ratings, b),
          rc = rating(ratings, c),
          rd = rating(ratings, d);
      double mean = (ra + rb + rc + rd) / 4;
      cost +=
          (square(ra - mean) + square(rb - mean) + square(rc - mean) + square(rd - mean)) / 1000;
      cost += square((ra + rb - rc - rd) / 2) / 300;
      for (String p : List.of(pair(a, b), pair(c, d)))
        // Prefer breaking up last round's teams even when recent form separates their estimates.
        cost += 300 * square(partners.getOrDefault(p, 0)) + (recentPartners.contains(p) ? 5000 : 0);
      for (String p : List.of(pair(a, c), pair(a, d), pair(b, c), pair(b, d)))
        cost += 35 * square(opponents.getOrDefault(p, 0)) + (recentOpponents.contains(p) ? 60 : 0);
    }
    return cost;
  }

  private static double rating(Map<Long, Double> ratings, Long id) {
    return ratings.getOrDefault(id, 0d);
  }

  private static double square(double value) {
    return value * value;
  }

  private static String pair(Long a, Long b) {
    return Math.min(a, b) + ":" + Math.max(a, b);
  }

  private static void count(Map<String, Integer> counts, Long a, Long b) {
    counts.merge(pair(a, b), 1, Integer::sum);
  }

  public static List<Long> players(MatchSpec g) {
    return Arrays.asList(g.a1, g.a2, g.b1, g.b2).stream().filter(Objects::nonNull).toList();
  }
}
