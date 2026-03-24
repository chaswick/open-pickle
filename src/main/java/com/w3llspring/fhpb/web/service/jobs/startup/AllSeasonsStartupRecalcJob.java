package com.w3llspring.fhpb.web.service.jobs.startup;

import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AllSeasonsStartupRecalcJob {

  private static final Logger log = LoggerFactory.getLogger(AllSeasonsStartupRecalcJob.class);

  private final LadderSeasonRepository seasonRepository;
  private final LadderV2Service ladderV2Service;
  private final boolean enabled;

  public AllSeasonsStartupRecalcJob(
      LadderSeasonRepository seasonRepository,
      LadderV2Service ladderV2Service,
      @Value("${fhpb.standings.recalc.all-seasons-on-startup:false}") boolean enabled) {
    this.seasonRepository = seasonRepository;
    this.ladderV2Service = ladderV2Service;
    this.enabled = enabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recalcAllSeasonsOnStartup() {
    try (BackgroundJobLogContext ignored =
        BackgroundJobLogContext.open("startup-all-seasons-recalc")) {
      if (!enabled) {
        return;
      }

      List<LadderSeason> seasons =
          seasonRepository.findAll(Sort.by(Sort.Order.asc("startDate"), Sort.Order.asc("id")));
      if (seasons.isEmpty()) {
        log.info("Startup all-season recalc enabled, but there are no seasons to process.");
        return;
      }

      log.warn("Startup all-season recalc enabled; recalculating {} seasons", seasons.size());
      Instant startedAt = Instant.now();
      List<Long> failedSeasonIds = new ArrayList<>();

      for (LadderSeason season : seasons) {
        if (season == null || season.getId() == null) {
          continue;
        }
        Long seasonId = season.getId();
        try {
          log.info("Recalculating season {} on startup", seasonId);
          ladderV2Service.recalcSeasonStandings(season);
        } catch (Exception ex) {
          failedSeasonIds.add(seasonId);
          log.error("Startup recalc failed for season {}: {}", seasonId, ex.getMessage(), ex);
        }
      }

      Duration elapsed = Duration.between(startedAt, Instant.now());
      if (!failedSeasonIds.isEmpty()) {
        throw new IllegalStateException(
            "Startup all-season recalc failed for seasons "
                + failedSeasonIds
                + " after "
                + elapsed);
      }

      log.warn(
          "Startup all-season recalc finished successfully in {} seconds for {} seasons",
          elapsed.toSeconds(),
          seasons.size());
    }
  }
}
