package com.w3llspring.fhpb.web.service.roundrobin;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduler for rosters where the participant count is odd (or when the balanced template cannot
 * apply). Delegates to {@link BalancingRoundRobinScheduler} which runs multiple randomised attempts
 * and picks the fairest schedule based on bye distribution and opponent repeat counts.
 */
public class OddHeuristicRoundRobinScheduler implements RoundRobinScheduler {

  private static final Logger log = LoggerFactory.getLogger(OddHeuristicRoundRobinScheduler.class);

  private final BalancingRoundRobinScheduler delegate = new BalancingRoundRobinScheduler();

  @Override
  public List<List<MatchSpec>> generateWithLog(
      List<Long> participantIds,
      Map<Long, Map<Long, Integer>> priorPairCounts,
      int rounds,
      List<String> explanationLog) {
    int size = participantIds == null ? 0 : participantIds.size();
    if (explanationLog != null) {
      explanationLog.add(
          "Odd heuristic scheduler: generating schedule for " + size + " participant(s).");
    }
    log.debug("Odd heuristic scheduler invoked for {} participant(s).", size);
    return delegate.generateWithLog(participantIds, priorPairCounts, rounds, explanationLog);
  }
}
