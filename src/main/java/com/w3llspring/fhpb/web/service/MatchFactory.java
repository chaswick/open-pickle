package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Factory service for creating Match entities with proper confirmation setup. Ensures that every
 * created match has appropriate confirmation rows.
 */
@Service
public class MatchFactory {

  private static final Logger log = LoggerFactory.getLogger(MatchFactory.class);

  private final MatchRepository matchRepository;
  private final MatchConfirmationService matchConfirmationService;

  public MatchFactory(
      MatchRepository matchRepository, MatchConfirmationService matchConfirmationService) {
    this.matchRepository = matchRepository;
    this.matchConfirmationService = matchConfirmationService;
  }

  /**
   * Creates and saves a new match with proper confirmation setup. This method ensures that
   * confirmation rows are created for the match.
   *
   * @param season The ladder season this match belongs to
   * @param playedAt When the match was played
   * @param loggedBy The user who logged this match
   * @return The saved match with confirmation rows created
   */
  @Transactional
  public Match createMatch(LadderSeason season, Instant playedAt, User loggedBy) {
    Match match = new Match();
    match.setSeason(season);
    Instant occurredAt = playedAt != null ? playedAt : Instant.now();
    match.setPlayedAt(occurredAt);
    match.setCreatedAt(occurredAt);
    match.setLoggedBy(loggedBy);
    match.synchronizeTimestampsForCreate();
    applyStandingsExclusionPolicy(match);

    Match saved = matchRepository.save(match);

    // Ensure confirmation rows are created for this match
    try {
      matchConfirmationService.createRequests(saved);
      log.debug("Created confirmation requests for match {}", saved.getId());
    } catch (Exception ex) {
      log.error("Failed to create match confirmation requests for match {}", saved.getId(), ex);
      // Don't fail the entire operation, but log the error
    }

    return saved;
  }

  /**
   * Creates and saves a match with all details and proper confirmation setup. This is the preferred
   * way to create matches to ensure data integrity.
   *
   * @param match The match entity with all details set (except ID)
   * @return The saved match with confirmation rows created
   */
  @Transactional
  public Match createMatch(Match match) {
    if (match.getId() != null) {
      throw new IllegalArgumentException("Cannot create a match that already has an ID");
    }
    if (match.getPlayedAt() == null && match.getCreatedAt() == null) {
      Instant now = Instant.now();
      match.setPlayedAt(now);
      match.setCreatedAt(now);
    } else if (match.getPlayedAt() == null) {
      match.setPlayedAt(match.getCreatedAt());
    } else if (match.getCreatedAt() == null) {
      match.setCreatedAt(match.getPlayedAt());
    }
    match.synchronizeTimestampsForCreate();
    applyStandingsExclusionPolicy(match);

    Match saved = matchRepository.save(match);

    // Ensure confirmation rows are created for this match
    try {
      matchConfirmationService.createRequests(saved);
      log.debug("Created confirmation requests for match {}", saved.getId());
    } catch (Exception ex) {
      log.error("Failed to create match confirmation requests for match {}", saved.getId(), ex);
      // Don't fail the entire operation, but log the error
    }

    return saved;
  }

  public void applyStandingsExclusionPolicy(Match match) {
    if (match == null) {
      return;
    }

    LadderSeason season = match.getSeason();
    LadderConfig cfg = (season != null) ? season.getLadderConfig() : null;

    boolean allowGuestOnlyPersonalRecords =
        cfg != null
            && cfg.isAllowGuestOnlyPersonalMatches()
            && cfg.getSecurityLevel() != null
            && LadderSecurity.normalize(cfg.getSecurityLevel()).isSelfConfirm();

    boolean isPersonalRecordOnly =
        allowGuestOnlyPersonalRecords && match.hasGuestOnlyOpposingTeam();
    match.setExcludeFromStandings(isPersonalRecordOnly);
  }
}
