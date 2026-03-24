package com.w3llspring.fhpb.web.service.jobs.trophy;

import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.service.trophy.AutoTrophyService;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TrophyAwardSweepJob {

  private static final Logger log = LoggerFactory.getLogger(TrophyAwardSweepJob.class);
  private static final ZoneId LADDER_ZONE = ZoneId.of("America/New_York");

  private final LadderSeasonRepository seasonRepository;
  private final TrophyAwardService trophyAwardService;
  private final AutoTrophyService autoTrophyService;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicReference<Instant> lastCompletedAt = new AtomicReference<>();
  private final int endedRetentionDays;

  public TrophyAwardSweepJob(
      LadderSeasonRepository seasonRepository,
      TrophyAwardService trophyAwardService,
      AutoTrophyService autoTrophyService,
      @org.springframework.beans.factory.annotation.Value(
              "${fhpb.trophies.award-sweep.ended-retention-days:30}")
          int endedRetentionDays) {
    this.seasonRepository = seasonRepository;
    this.trophyAwardService = trophyAwardService;
    this.autoTrophyService = autoTrophyService;
    this.endedRetentionDays = Math.max(0, endedRetentionDays);
  }

  public Instant getLastCompletedAt() {
    return lastCompletedAt.get();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runInitialSweep() {
    runSweep(SweepMode.ACTIVE_ONLY);
    runSweep(SweepMode.RECENT_ENDED_ONLY);
  }

  @Scheduled(cron = "${fhpb.trophies.award-sweep.active-cron:0 */30 * * * *}")
  public void runActiveSweep() {
    runSweep(SweepMode.ACTIVE_ONLY);
  }

  @Scheduled(cron = "${fhpb.trophies.award-sweep.ended-cron:0 15 2 * * *}")
  public void runRecentEndedSweep() {
    runSweep(SweepMode.RECENT_ENDED_ONLY);
  }

  private void runSweep(SweepMode mode) {
    try (BackgroundJobLogContext ignored = BackgroundJobLogContext.open(mode.logName())) {
      if (!running.compareAndSet(false, true)) {
        log.debug("Skipping trophy sweep because a previous run is still active.");
        return;
      }

      Instant startedAt = Instant.now();
      int seasonCount = 0;
      int awardedCount = 0;
      try {
        List<LadderSeason> seasons = selectSeasonsForMode(mode, startedAt);
        seasonCount = seasons.size();
        for (LadderSeason season : seasons) {
          if (season == null || season.getId() == null) {
            continue;
          }
          autoTrophyService.generateSeasonTrophies(season);
          awardedCount += trophyAwardService.evaluateSeasonSweep(season);
        }
        lastCompletedAt.set(Instant.now());
        long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();
        log.info(
            "Trophy sweep complete (mode={}): seasonsChecked={}, trophiesAwarded={}, elapsedMs={}",
            mode,
            seasonCount,
            awardedCount,
            elapsedMs);
      } catch (Exception ex) {
        log.error(
            "Trophy sweep failed (mode={}) after checking {} seasons: {}",
            mode,
            seasonCount,
            ex.getMessage(),
            ex);
      } finally {
        running.set(false);
      }
    }
  }

  private List<LadderSeason> selectSeasonsForMode(SweepMode mode, Instant now) {
    if (mode == SweepMode.ACTIVE_ONLY) {
      return seasonRepository.findByStateOrderByStartDateDesc(LadderSeason.State.ACTIVE);
    }

    List<LadderSeason> endedSeasons =
        seasonRepository.findByStateOrderByStartDateDesc(LadderSeason.State.ENDED);
    if (endedSeasons.isEmpty()) {
      return List.of();
    }
    if (endedRetentionDays <= 0) {
      return List.of();
    }

    Instant cutoff = now.minus(Duration.ofDays(endedRetentionDays));
    LocalDate cutoffDate = cutoff.atZone(LADDER_ZONE).toLocalDate();
    return endedSeasons.stream()
        .filter(
            season -> {
              if (season == null) {
                return false;
              }
              if (season.getEndedAt() != null) {
                return !season.getEndedAt().isBefore(cutoff);
              }
              return season.getEndDate() != null && !season.getEndDate().isBefore(cutoffDate);
            })
        .toList();
  }

  private enum SweepMode {
    ACTIVE_ONLY,
    RECENT_ENDED_ONLY;

    private String logName() {
      return switch (this) {
        case ACTIVE_ONLY -> "trophy-award-sweep-active";
        case RECENT_ENDED_ONLY -> "trophy-award-sweep-recent-ended";
      };
    }
  }
}
