package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderSeason;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SeasonStandingsRecalcQueue {

  private static final Logger log = LoggerFactory.getLogger(SeasonStandingsRecalcQueue.class);

  private final Executor taskExecutor;
  private final LadderSeasonRepository seasonRepo;
  private final LadderV2Service ladderV2Service;
  private final long debounceMs;
  private final int maxConcurrentRecalcs;
  private final BlockingQueue<Long> pendingSeasonIds = new LinkedBlockingQueue<>();
  private final ConcurrentMap<Long, SeasonQueueState> queueBySeason = new ConcurrentHashMap<>();
  private final AtomicBoolean workersStarted = new AtomicBoolean(false);
  private final AtomicBoolean stopRequested = new AtomicBoolean(false);

  public SeasonStandingsRecalcQueue(
      @Qualifier("taskExecutor") Executor taskExecutor,
      LadderSeasonRepository seasonRepo,
      LadderV2Service ladderV2Service,
      @Value("${fhpb.standings.recalc.debounce-ms:0}") long debounceMs,
      @Value("${fhpb.standings.recalc.max-concurrent:1}") int maxConcurrentRecalcs) {
    this.taskExecutor = taskExecutor;
    this.seasonRepo = seasonRepo;
    this.ladderV2Service = ladderV2Service;
    this.debounceMs = Math.max(0L, debounceMs);
    this.maxConcurrentRecalcs = Math.max(1, maxConcurrentRecalcs);
  }

  @PostConstruct
  public void startWorkers() {
    if (!workersStarted.compareAndSet(false, true)) {
      return;
    }
    for (int i = 0; i < maxConcurrentRecalcs; i++) {
      taskExecutor.execute(this::workerLoop);
    }
  }

  @PreDestroy
  public void stopWorkers() {
    stopRequested.set(true);
  }

  @EventListener
  public void onContextClosed(ContextClosedEvent event) {
    stopRequested.set(true);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onRecalcRequested(SeasonStandingsRecalcRequestedEvent event) {
    if (event == null || event.seasonId() == null) {
      return;
    }
    enqueue(event.seasonId());
  }

  private void enqueue(Long seasonId) {
    SeasonQueueState state = queueBySeason.computeIfAbsent(seasonId, id -> new SeasonQueueState());
    state.requestedVersion.incrementAndGet();
    if (state.enqueued.compareAndSet(false, true)) {
      pendingSeasonIds.offer(seasonId);
    }
  }

  private void workerLoop() {
    while (!stopRequested.get()) {
      Long seasonId;
      try {
        seasonId = pendingSeasonIds.poll(1, TimeUnit.SECONDS);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        return;
      }
      if (seasonId == null) {
        continue;
      }
      processSeason(seasonId);
    }
  }

  private void processSeason(Long seasonId) {
    SeasonQueueState state = queueBySeason.get(seasonId);
    if (state == null) {
      return;
    }

    state.enqueued.set(false);
    if (!state.running.compareAndSet(false, true)) {
      return;
    }

    try {
      long targetVersion = state.requestedVersion.get();
      long completedVersion = state.completedVersion.get();
      if (completedVersion < targetVersion) {
        // Optional per-season debounce, disabled by default.
        if (debounceMs > 0L) {
          long lastRunStartedAt = state.lastRunStartedAtMs.get();
          if (lastRunStartedAt > 0L) {
            long elapsedMs = System.currentTimeMillis() - lastRunStartedAt;
            long waitMs = debounceMs - elapsedMs;
            if (waitMs > 0L) {
              try {
                Thread.sleep(waitMs);
              } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn(
                    "Season standings recalculation queue interrupted while waiting (seasonId={})",
                    seasonId);
                return;
              }
            }
          }
        }
        state.lastRunStartedAtMs.set(System.currentTimeMillis());
        LadderSeason season = seasonRepo.findById(seasonId).orElse(null);
        try {
          if (season != null) {
            ladderV2Service.recalcSeasonStandings(season);
          }
        } catch (Exception ex) {
          log.error(
              "Season standings recalculation failed for season {}: {}",
              seasonId,
              ex.getMessage(),
              ex);
        } finally {
          // Mark this request version as consumed to prevent infinite retries on hard failures.
          state.completedVersion.set(targetVersion);
        }
      }
    } finally {
      state.running.set(false);
      if (state.completedVersion.get() < state.requestedVersion.get()) {
        if (state.enqueued.compareAndSet(false, true)) {
          pendingSeasonIds.offer(seasonId);
        }
      }
      // Keep state entries to avoid dropping a late concurrent enqueue due to
      // map-entry removal races. Memory is bounded by seasons that have ever
      // requested recalculation.
    }
  }

  private static final class SeasonQueueState {
    private final AtomicLong requestedVersion = new AtomicLong(0);
    private final AtomicLong completedVersion = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean enqueued = new AtomicBoolean(false);
    private final AtomicLong lastRunStartedAtMs = new AtomicLong(0);
  }
}
