package com.w3llspring.fhpb.tools;

import com.w3llspring.fhpb.web.service.roundrobin.BalancingRoundRobinScheduler;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinScheduler;
import java.util.*;

public class BalancingSchedulerDemo {
  public static void main(String[] args) {
    BalancingRoundRobinScheduler sched = new BalancingRoundRobinScheduler();

    List<Long> participants = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L);

    Map<Long, Map<Long, Integer>> prior = new HashMap<>();
    for (Long p : participants) prior.put(p, new HashMap<>());
    // make 1 have heavy prior counts with 2 and 3
    prior.get(1L).put(2L, 5);
    prior.get(2L).put(1L, 5);
    prior.get(1L).put(3L, 5);
    prior.get(3L).put(1L, 5);

    // pass prior counts and request default number of rounds (0 -> scheduler chooses)
    List<List<RoundRobinScheduler.MatchSpec>> schedule = sched.generate(participants, prior, 0);

    System.out.println("Generated schedule (rounds: " + schedule.size() + ")");
    for (int r = 0; r < schedule.size(); r++) {
      System.out.println("Round " + (r + 1));
      for (RoundRobinScheduler.MatchSpec m : schedule.get(r)) {
        if (m.bye) {
          System.out.printf("  BYE: %s %s\n", safe(m.a1), safe(m.a2));
        } else {
          System.out.printf(
              "  Match: %s/%s vs %s/%s\n", safe(m.a1), safe(m.a2), safe(m.b1), safe(m.b2));
        }
      }
    }
  }

  private static String safe(Long x) {
    return x == null ? "-" : x.toString();
  }
}
