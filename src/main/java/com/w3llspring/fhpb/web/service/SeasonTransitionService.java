package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Centralized guardrail enforcing a maximum number of season creations per day. Limits season
 * creation to once per 24-hour window per ladder.
 *
 * <p>Usage: SeasonTransitionWindow tw = seasonTransitionService.canCreateSeason(ladderId); if
 * (!tw.isAllowed()) { show countdown tw.getWaitDuration() ... }
 */
@Service
public class SeasonTransitionService {

  private final LadderConfigRepository ladderRepo;
  private final LadderSeasonRepository seasonRepo;

  public SeasonTransitionService(
      LadderConfigRepository ladderRepo, LadderSeasonRepository seasonRepo) {
    this.ladderRepo = ladderRepo;
    this.seasonRepo = seasonRepo;
  }

  /**
   * Check if season creation is allowed for the given ladderId. Enforces: maximum 1 season creation
   * per 24-hour window.
   */
  public SeasonTransitionWindow canCreateSeason(Long ladderId) {
    LadderConfig ladder =
        ladderRepo
            .findById(ladderId)
            .orElseThrow(
                () -> new IllegalArgumentException("LadderConfig not found: id=" + ladderId));
    return canCreateSeason(ladder);
  }

  /**
   * Check if season creation is allowed for the given ladder. Enforces: maximum 1 season creation
   * per 24-hour window. This check is time-window only; callers should separately enforce whether
   * an active season currently exists for their specific action.
   */
  public SeasonTransitionWindow canCreateSeason(LadderConfig ladder) {
    // Check if ladder has lastSeasonCreatedAt timestamp
    final Instant lastCreated = ladder.getLastSeasonCreatedAt();
    if (lastCreated == null) {
      // No previous season creation tracked, allow creation
      return SeasonTransitionWindow.ok();
    }

    // Check if 24 hours have passed since last season creation
    final Instant now = Instant.now();
    final Instant nextAllowedAt = lastCreated.plus(Duration.ofHours(24));

    if (!now.isBefore(nextAllowedAt)) {
      // 24 hours have passed since last creation, allow now
      return SeasonTransitionWindow.ok();
    }

    // Less than 24 hours since last creation, block with countdown
    return SeasonTransitionWindow.blocked(nextAllowedAt, "Season creation limit reached.");
  }

  /**
   * Convenience helper to present a "Hh Mm" countdown string for the UI. Returns empty string if
   * allowed or no wait needed.
   */
  public String formatCountdown(SeasonTransitionWindow window) {
    Duration d = window.getWaitDuration();
    if (d.isZero() || d.isNegative()) return "";
    long hours = d.toHours();
    long minutes = d.minusHours(hours).toMinutes();
    return hours + "h " + minutes + "m";
  }

  /**
   * Optional: utility that ensures at most one ACTIVE season exists (app-level enforcement).
   * Returns the ACTIVE season if present.
   */
  public Optional<LadderSeason> findActive(Long ladderId) {
    return seasonRepo.findActive(ladderId);
  }
}
