package com.w3llspring.fhpb.web.service.roundrobin;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composite scheduler that chooses between the balanced template implementation for even roster
 * sizes and the heuristic search implementation for odd (or unsupported) roster sizes.
 */
public class BalancedCompositeRoundRobinScheduler implements RoundRobinScheduler {

  private static final Logger log =
      LoggerFactory.getLogger(BalancedCompositeRoundRobinScheduler.class);

  private final BalancedTemplateRoundRobinScheduler templateScheduler =
      new BalancedTemplateRoundRobinScheduler();
  private final OddHeuristicRoundRobinScheduler oddScheduler =
      new OddHeuristicRoundRobinScheduler();

  @Override
  public List<List<MatchSpec>> generateWithLog(
      List<Long> participantIds,
      Map<Long, Map<Long, Integer>> priorPairCounts,
      int rounds,
      List<String> explanationLog) {
    int size = participantIds == null ? 0 : participantIds.size();
    if (size % 2 == 0 && size >= 4) {
      List<List<MatchSpec>> schedule =
          templateScheduler.generateWithLog(
              participantIds, priorPairCounts, rounds, explanationLog);
      if (!schedule.isEmpty()) {
        if (explanationLog != null) {
          explanationLog.add(
              "Composite scheduler: used balanced template for even roster size " + size + ".");
        }
        log.debug("Composite scheduler used template scheduler for size {}.", size);
        return schedule;
      }
      if (explanationLog != null) {
        explanationLog.add(
            "Composite scheduler: template unavailable for size "
                + size
                + ", falling back to heuristic search.");
      }
    }
    log.debug("Composite scheduler using heuristic scheduler for size {}.", size);
    return oddScheduler.generateWithLog(participantIds, priorPairCounts, rounds, explanationLog);
  }
}
