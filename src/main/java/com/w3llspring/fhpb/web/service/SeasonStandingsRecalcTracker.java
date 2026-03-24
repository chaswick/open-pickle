package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeasonStandingsRecalcTracker {

  private static final Logger log = LoggerFactory.getLogger(SeasonStandingsRecalcTracker.class);

  private final LadderSeasonRepository seasonRepo;

  public SeasonStandingsRecalcTracker(LadderSeasonRepository seasonRepo) {
    this.seasonRepo = seasonRepo;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markStarted(Long seasonId) {
    if (seasonId == null) {
      return;
    }
    try {
      int updated = seasonRepo.incrementStandingsRecalcInFlight(seasonId, Instant.now());
      if (updated == 0) {
        log.debug("Unable to mark standings recalculation started for missing season {}", seasonId);
      }
    } catch (Exception ex) {
      log.warn(
          "Failed to mark standings recalculation started for season {}: {}",
          seasonId,
          ex.getMessage());
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFinished(Long seasonId) {
    if (seasonId == null) {
      return;
    }
    try {
      int updated = seasonRepo.decrementStandingsRecalcInFlight(seasonId, Instant.now());
      if (updated == 0) {
        log.debug(
            "Unable to mark standings recalculation finished for missing season {}", seasonId);
      }
    } catch (Exception ex) {
      log.warn(
          "Failed to mark standings recalculation finished for season {}: {}",
          seasonId,
          ex.getMessage());
    }
  }
}
