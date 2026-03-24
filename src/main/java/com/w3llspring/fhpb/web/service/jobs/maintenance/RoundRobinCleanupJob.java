package com.w3llspring.fhpb.web.service.jobs.maintenance;

import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RoundRobinCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(RoundRobinCleanupJob.class);

  private final RoundRobinService roundRobinService;

  public RoundRobinCleanupJob(RoundRobinService roundRobinService) {
    this.roundRobinService = roundRobinService;
  }

  // Run daily at 02:00
  @Scheduled(cron = "0 0 2 * * *")
  public void runDailyPrune() {
    try (BackgroundJobLogContext ignored = BackgroundJobLogContext.open("round-robin-cleanup")) {
      try {
        int removed = roundRobinService.pruneCompletedRoundRobinEntriesWithoutMatch();
        if (removed > 0) {
          log.info("Pruned {} orphaned round-robin entries from completed round-robins", removed);
        } else {
          log.debug("No orphaned round-robin entries found to prune");
        }
      } catch (Exception ex) {
        log.warn("RoundRobinCleanupJob failed: {}", ex.getMessage(), ex);
      }
    }
  }
}
