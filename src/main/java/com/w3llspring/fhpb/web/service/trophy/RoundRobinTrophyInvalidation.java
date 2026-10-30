package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.db.RoundRobinRepository;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RoundRobinTrophyInvalidation {
  private static final Logger log = LoggerFactory.getLogger(RoundRobinTrophyInvalidation.class);
  private final RoundRobinRepository runs;
  private final RoundRobinTrophyAwardService awards;
  private final TransactionTemplate transactions;
  private final ConcurrentLinkedQueue<RoundRobinTrophyResultsChanged> retries =
      new ConcurrentLinkedQueue<>();
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          task -> {
            Thread thread = new Thread(task, "round-robin-trophy-changes");
            thread.setDaemon(true);
            return thread;
          });

  public RoundRobinTrophyInvalidation(
      RoundRobinRepository runs,
      RoundRobinTrophyAwardService awards,
      PlatformTransactionManager transactionManager) {
    this.runs = runs;
    this.awards = awards;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void resultsChanged(RoundRobinTrophyResultsChanged event) {
    // Let the score transaction release its connection before this work borrows one.
    worker.submit(() -> apply(event));
  }

  private void apply(RoundRobinTrophyResultsChanged event) {
    try {
      transactions.executeWithoutResult(
          status -> {
            if (event.matchId() != null)
              for (Long id : runs.findProcessedRunIdsForMatch(event.matchId())) reset(id);
            if (event.runId() != null) reset(event.runId());
            if (event.deletedSeasonId() != null) awards.reconcileSeason(event.deletedSeasonId());
          });
    } catch (RuntimeException ex) {
      retries.add(event);
      log.error("Round-robin trophy invalidation will be retried: {}", event, ex);
    }
  }

  /** Drain committed changes before selecting ready runs, including transient-failure retries. */
  public void flush() {
    try {
      worker
          .submit(
              () -> {
                int count = retries.size();
                for (int i = 0; i < count; i++) {
                  RoundRobinTrophyResultsChanged event = retries.poll();
                  if (event != null) apply(event);
                }
              })
          .get(30, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Trophy changes interrupted", ex);
    } catch (ExecutionException | TimeoutException ex) {
      throw new IllegalStateException("Trophy changes could not be drained", ex);
    }
  }

  @PreDestroy
  public void close() {
    worker.shutdown();
    try {
      if (!worker.awaitTermination(5, TimeUnit.SECONDS)) worker.shutdownNow();
    } catch (InterruptedException ex) {
      worker.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private void reset(Long id) {
    if (runs.resetTrophyProcessing(id) > 0) awards.withdrawRunAwards(id);
  }
}
