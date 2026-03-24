package com.w3llspring.fhpb.web.service.roundrobin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class BalancedTemplateRoundRobinSchedulerTest {

  private final BalancedTemplateRoundRobinScheduler scheduler =
      new BalancedTemplateRoundRobinScheduler();

  @Test
  void allTeammatesAppearExactlyOnceForEightPlayers() {
    List<Long> participants = LongStream.rangeClosed(1, 8).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);

    assertEquals(7, schedule.size(), "Eight players should yield seven rounds (n - 1).");
    assertTeammatePairsAppearOnce(schedule);
  }

  @Test
  void tenPlayerRosterProducesNineRoundsWithoutDuplicatePartners() {
    List<Long> participants = LongStream.rangeClosed(1, 10).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);

    assertEquals(9, schedule.size(), "Ten players should yield nine rounds (n - 1).");
    assertTeammatePairsAppearOnce(schedule);
  }

  @Test
  void oddRosterSupportedViaFallback() {
    List<Long> participants = LongStream.rangeClosed(1, 9).boxed().collect(Collectors.toList());
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        scheduler.generate(participants, Map.of(), 0);
    assertTrue(schedule.isEmpty(), "Template scheduler should not schedule odd roster sizes.");
  }

  private void assertTeammatePairsAppearOnce(List<List<RoundRobinScheduler.MatchSpec>> schedule) {
    Map<Set<Long>, Integer> partnerCounts = new HashMap<>();
    for (List<RoundRobinScheduler.MatchSpec> round : schedule) {
      for (RoundRobinScheduler.MatchSpec spec : round) {
        if (!spec.bye) {
          registerTeam(partnerCounts, spec.a1, spec.a2);
          registerTeam(partnerCounts, spec.b1, spec.b2);
        }
      }
    }

    for (Integer count : partnerCounts.values()) {
      assertEquals(1, count, "Each teammate pairing should occur exactly once.");
    }
  }

  private void registerTeam(Map<Set<Long>, Integer> partnerCounts, Long p1, Long p2) {
    if (p1 == null || p2 == null) return;
    Set<Long> team = new HashSet<>(Set.of(p1, p2));
    partnerCounts.merge(team, 1, Integer::sum);
  }
}
