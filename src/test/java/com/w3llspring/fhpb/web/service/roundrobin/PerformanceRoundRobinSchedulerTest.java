package com.w3llspring.fhpb.web.service.roundrobin;

import static org.assertj.core.api.Assertions.*;

import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinScheduler.MatchSpec;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PerformanceRoundRobinSchedulerTest {
  private final PerformanceRoundRobinScheduler scheduler = new PerformanceRoundRobinScheduler();

  static IntStream counts() {
    return IntStream.rangeClosed(4, 32);
  }

  @ParameterizedTest(name = "{0} players: thirty adaptive rounds")
  @MethodSource("counts")
  void roundsUseEveryPlayerOnceAndRotateByesFairly(int count) {
    List<Long> players = LongStream.range(0, count).map(i -> 101 + i * 17).boxed().toList();
    Map<Long, Double> ratings = new HashMap<>();
    players.forEach(id -> ratings.put(id, (id % 5) * 10d));
    var history = new ArrayList<List<MatchSpec>>();
    Map<Long, Integer> rests = new HashMap<>();
    players.forEach(id -> rests.put(id, 0));
    Set<Long> previousRest = Set.of();
    for (int round = 0; round < 30; round++) {
      List<Long> input = new ArrayList<>(players);
      if (round % 2 == 0) Collections.reverse(input);
      var games = scheduler.nextRound(input, ratings, history);
      assertThat(
              games.stream()
                  .flatMap(g -> PerformanceRoundRobinScheduler.players(g).stream())
                  .toList())
          .containsExactlyInAnyOrderElementsOf(players);
      assertThat(games.stream().filter(g -> !g.bye).count()).isEqualTo(count / 4);
      Set<Long> currentRest = new HashSet<>();
      for (var g : games) {
        if (g.bye) {
          currentRest.addAll(PerformanceRoundRobinScheduler.players(g));
          PerformanceRoundRobinScheduler.players(g).forEach(id -> rests.merge(id, 1, Integer::sum));
        } else {
          assertThat(PerformanceRoundRobinScheduler.players(g)).hasSize(4).doesNotHaveDuplicates();
        }
      }
      assertThat(currentRest).hasSize(count % 4);
      assertThat(Collections.max(rests.values()) - Collections.min(rests.values()))
          .isLessThanOrEqualTo(1);
      if (count >= 2 * (count % 4))
        assertThat(Collections.disjoint(currentRest, previousRest)).isTrue();
      previousRest = currentRest;
      history.add(games);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void requiresEnoughPlayersForDoubles(int count) {
    assertThatThrownBy(
            () ->
                scheduler.nextRound(
                    LongStream.range(0, count).boxed().toList(), Map.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {4, 8, 12, 16, 24, 32})
  void equalPlayersRotatePartnersAndMeetDifferentOpponents(int count) {
    var players = LongStream.rangeClosed(1, count).boxed().toList();
    var history = new ArrayList<List<MatchSpec>>();
    Map<Long, Set<Long>> partners = new HashMap<>(), opponents = new HashMap<>();
    Set<Set<Long>> previousTeams = new HashSet<>();
    for (int round = 0; round < 6; round++) {
      var games = scheduler.nextRound(players, Map.of(), history);
      Set<Set<Long>> teams = new HashSet<>();
      for (var g : games) {
        teams.add(Set.of(g.a1, g.a2));
        teams.add(Set.of(g.b1, g.b2));
        for (var team : List.of(List.of(g.a1, g.a2), List.of(g.b1, g.b2))) {
          partners.computeIfAbsent(team.get(0), id -> new HashSet<>()).add(team.get(1));
          partners.computeIfAbsent(team.get(1), id -> new HashSet<>()).add(team.get(0));
        }
        for (Long a : List.of(g.a1, g.a2))
          for (Long b : List.of(g.b1, g.b2)) {
            opponents.computeIfAbsent(a, id -> new HashSet<>()).add(b);
            opponents.computeIfAbsent(b, id -> new HashSet<>()).add(a);
          }
      }
      assertThat(Collections.disjoint(teams, previousTeams)).isTrue();
      previousTeams = teams;
      history.add(games);
    }
    for (Long id : players) {
      assertThat(partners.get(id).size()).isGreaterThanOrEqualTo(Math.min(4, count - 1));
      assertThat(opponents.get(id).size()).isGreaterThanOrEqualTo(Math.min(5, count - 1));
    }
  }

  @Test
  void openingRoundGroupsNearbyRatingsAndBalancesTeams() {
    var players = LongStream.rangeClosed(1, 16).boxed().toList();
    Map<Long, Double> ratings = new HashMap<>();
    players.forEach(id -> ratings.put(id, ((id - 1) / 4) * 1000d));
    for (var g : scheduler.nextRound(players, ratings, List.of())) {
      assertThat(PerformanceRoundRobinScheduler.players(g).stream().map(ratings::get).distinct())
          .hasSize(1);
    }
  }

  @Test
  void firstRestVariesBetweenRunsAndSchedulingIsStableWithinARun() {
    var players = LongStream.rangeClosed(1, 5).boxed().toList();
    Set<Long> firstSitters = new HashSet<>();
    for (int seed = 0; seed < 10; seed++) {
      var round = scheduler.nextRound(players, Map.of(), List.of(), seed);
      var repeat = scheduler.nextRound(players, Map.of(), List.of(), seed);
      assertThat(repeat).usingRecursiveComparison().isEqualTo(round);
      round.stream().filter(g -> g.bye).forEach(g -> firstSitters.add(g.a1));
    }
    assertThat(firstSitters.size()).isGreaterThan(1);
  }

  @Test
  void changingTheRatingScaleDoesNotChangeSchedulingPreferences() {
    var players = LongStream.rangeClosed(1, 16).boxed().toList();
    Map<Long, Double> points = new HashMap<>(), displayed = new HashMap<>();
    players.forEach(
        id -> {
          points.put(id, (id % 4) * 20d);
          displayed.put(id, 1000 + points.get(id) * 10);
        });
    assertThat(scheduler.nextRound(players, points, List.of(), 41))
        .usingRecursiveComparison()
        .isEqualTo(scheduler.nextRound(players, displayed, List.of(), 41));
  }

  @Test
  void rotatingPartnersDoesNotNeedlesslyMixDistantSkillGroups() {
    var players = LongStream.rangeClosed(1, 8).boxed().toList();
    Map<Long, Double> ratings = new HashMap<>();
    players.forEach(id -> ratings.put(id, id <= 4 ? 0d : 100d));
    var opening = scheduler.nextRound(players, ratings, List.of(), 41);
    var next = scheduler.nextRound(players, ratings, List.of(opening), 41);
    Set<Set<Long>> oldTeams = new HashSet<>();
    for (var g : opening) {
      oldTeams.add(Set.of(g.a1, g.a2));
      oldTeams.add(Set.of(g.b1, g.b2));
    }
    for (var g : next) {
      assertThat(
              PerformanceRoundRobinScheduler.players(g).stream()
                  .map(ratings::get)
                  .distinct()
                  .toList())
          .hasSize(1);
      assertThat(oldTeams).doesNotContain(Set.of(g.a1, g.a2), Set.of(g.b1, g.b2));
    }
  }
}
