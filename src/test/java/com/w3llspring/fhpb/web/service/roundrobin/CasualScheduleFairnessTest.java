package com.w3llspring.fhpb.web.service.roundrobin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CasualScheduleFairnessTest {
  static IntStream playerCounts() {
    return IntStream.rangeClosed(0, 32);
  }

  @ParameterizedTest(name = "{0} players: rotating partners, every length and multiple seeds")
  @MethodSource("playerCounts")
  @Timeout(30)
  void schedulesThroughThirtyTwoPlayersStayValidAndFair(int count) {
    // Sparse IDs and reversed input catch accidental reliance on player numbering or order.
    List<Long> players = LongStream.range(0, count).map(i -> 101 + 17 * i).boxed().toList();
    for (long seed : new long[] {1, 42, 999}) {
      List<Long> roster = new ArrayList<>(players);
      Map<Long, Map<Long, Integer>> history = new HashMap<>();
      if (seed == 999) {
        Collections.reverse(roster);
        for (Long a : roster) {
          Map<Long, Integer> opponents = new HashMap<>();
          for (Long b : roster) if (!a.equals(b)) opponents.put(b, (int) ((a + b) % 7));
          history.put(a, opponents);
        }
      }
      for (int requested = 0; requested <= 30; requested++) {
        String context = count + " players / " + requested + " requested rounds / seed " + seed;
        FairByeRoundRobinScheduler scheduler = new FairByeRoundRobinScheduler();
        scheduler.setSeedForTests(seed);
        var schedule = scheduler.generate(roster, history, requested);
        if (count < 4) {
          assertThat(schedule).as(context).isEmpty();
          continue;
        }
        int sitters = count % 4;
        int expectedRounds =
            requested > 0
                ? requested
                : sitters == 0 ? count - 1 : count % sitters == 0 ? count / sitters : count;
        assertThat(schedule).as(context).hasSize(expectedRounds);
        Map<Long, Integer> byes = new HashMap<>();
        Map<Long, Set<Long>> opponents = new HashMap<>();
        Map<Set<Long>, Integer> opponentMeetings = new HashMap<>();
        Set<Set<Long>> partners = new HashSet<>();
        int partnershipCycle = count % 2 == 0 ? count - 1 : count;
        for (Long player : players) {
          byes.put(player, 0);
          opponents.put(player, new HashSet<>());
        }
        for (int r = 0; r < schedule.size(); r++) {
          List<Long> appearances = new ArrayList<>();
          int games = 0, sitting = 0;
          for (var match : schedule.get(r)) {
            for (Long id : Arrays.asList(match.a1, match.a2, match.b1, match.b2)) {
              if (id == null) continue;
              appearances.add(id);
              if (match.bye) {
                byes.merge(id, 1, Integer::sum);
                sitting++;
              }
            }
            if (!match.bye) {
              games++;
              assertThat(Arrays.asList(match.a1, match.a2, match.b1, match.b2))
                  .as(context + " round " + (r + 1))
                  .doesNotContainNull()
                  .doesNotHaveDuplicates();
              for (Set<Long> team :
                  List.of(Set.of(match.a1, match.a2), Set.of(match.b1, match.b2))) {
                if (r < partnershipCycle) {
                  assertThat(partners.add(team))
                      .as(context + " repeats partners before a full cycle")
                      .isTrue();
                }
              }
              for (Long a : List.of(match.a1, match.a2)) {
                for (Long b : List.of(match.b1, match.b2)) {
                  opponentMeetings.merge(Set.of(a, b), 1, Integer::sum);
                  opponents.get(a).add(b);
                  opponents.get(b).add(a);
                }
              }
            }
          }
          assertThat(appearances)
              .as(context + " round " + (r + 1))
              .containsExactlyInAnyOrderElementsOf(players);
          assertThat(games).as(context).isEqualTo(count / 4);
          assertThat(sitting).as(context).isEqualTo(sitters);
        }
        assertThat(Collections.max(byes.values()) - Collections.min(byes.values()))
            .as("bye spread: " + context)
            .isLessThanOrEqualTo(1);
        // In a complete cycle with zero or one bye per round, everyone partners
        // with every other player once and also faces every other player.
        if (requested == 0 && sitters <= 1) {
          assertThat(partners).as(context).hasSize(count * (count - 1) / 2);
          for (var faced : opponents.values()) assertThat(faced).as(context).hasSize(count - 1);
        }
        if (count == 4 || count == 8 || count == 16 || count == 32) {
          int completeCycles = expectedRounds / (count - 1);
          assertThat(opponentMeetings.values())
              .as(context + " concentrates repeated opponents")
              .allMatch(n -> n >= 2 * completeCycles && n <= 2 * (completeCycles + 1));
          if (expectedRounds % (count - 1) == 0) {
            assertThat(opponentMeetings).as(context).hasSize(count * (count - 1) / 2);
            assertThat(opponentMeetings.values())
                .as(context)
                .allMatch(n -> n == 2 * completeCycles);
          }
        }
      }
    }
  }

  @Test
  void sevenPlayersCanPlayThreeRoundsWithoutAnyoneSittingOutEveryGame() {
    FairByeRoundRobinScheduler scheduler = new FairByeRoundRobinScheduler();
    scheduler.setSeedForTests(1L);
    var schedule = scheduler.generate(LongStream.rangeClosed(1, 7).boxed().toList(), Map.of(), 3);
    Map<Long, Integer> played = new HashMap<>();
    for (var round : schedule)
      for (var match : round)
        if (!match.bye) {
          for (Long id : List.of(match.a1, match.a2, match.b1, match.b2))
            played.merge(id, 1, Integer::sum);
        }
    assertThat(played).hasSize(7);
    assertThat(played.values()).allMatch(n -> n == 1 || n == 2);
  }

  @Test
  void fifteenPlayersHaveOneByeEachInTheDefaultFiveRoundSchedule() {
    FairByeRoundRobinScheduler scheduler = new FairByeRoundRobinScheduler();
    scheduler.setSeedForTests(1L);
    var schedule = scheduler.generate(LongStream.rangeClosed(1, 15).boxed().toList(), Map.of(), 0);
    Map<Long, Integer> byes = new HashMap<>();
    for (var round : schedule)
      for (var match : round)
        if (match.bye) {
          for (Long id : Arrays.asList(match.a1, match.a2, match.b1, match.b2))
            if (id != null) byes.merge(id, 1, Integer::sum);
        }
    assertThat(schedule).hasSize(5);
    assertThat(byes).hasSize(15);
    assertThat(byes.values()).allMatch(n -> n == 1);
  }
}
