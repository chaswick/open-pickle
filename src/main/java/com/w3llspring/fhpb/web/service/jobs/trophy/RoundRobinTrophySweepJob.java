package com.w3llspring.fhpb.web.service.jobs.trophy;

import com.w3llspring.fhpb.web.db.RoundRobinRepository;
import com.w3llspring.fhpb.web.service.trophy.RoundRobinTrophyAwardService;
import com.w3llspring.fhpb.web.service.trophy.RoundRobinTrophyInvalidation;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "fhpb.trophies.round-robin-sweep-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RoundRobinTrophySweepJob {
  private static final Logger log = LoggerFactory.getLogger(RoundRobinTrophySweepJob.class);
  private final RoundRobinRepository runs;
  private final RoundRobinTrophyAwardService awards;
  private final RoundRobinTrophyInvalidation changes;
  private final AtomicBoolean running = new AtomicBoolean();

  public RoundRobinTrophySweepJob(
      RoundRobinRepository runs,
      RoundRobinTrophyAwardService awards,
      RoundRobinTrophyInvalidation changes) {
    this.runs = runs;
    this.awards = awards;
    this.changes = changes;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(
      initialDelayString = "${fhpb.trophies.round-robin-sweep-delay-ms:60000}",
      fixedDelayString = "${fhpb.trophies.round-robin-sweep-delay-ms:60000}")
  public void sweep() {
    if (!running.compareAndSet(false, true)) return;
    try {
      changes.flush();
      for (Long id : runs.findUnprocessedFinishedRuns(Instant.now(), PageRequest.of(0, 50))) {
        try {
          awards.process(id);
        } catch (RuntimeException ex) {
          log.error("Round-robin trophy processing failed for run {}", id, ex);
        }
      }
    } finally {
      running.set(false);
    }
  }
}
