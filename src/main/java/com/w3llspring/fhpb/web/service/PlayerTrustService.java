package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Central service for calculating season-scoped player-integrity metrics.
 *
 * <p>This service measures behavioral integrity: 1. Logging Accuracy: % of matches logged that got
 * confirmed (prevents spam attacks) 2. Confirmation Honesty: % of required confirmations provided
 * (prevents refusing losses)
 *
 * <p>These metrics are intended for moderation, warnings, and season eligibility. They are not part
 * of rating-delta calculation.
 *
 * <p>See docs/current-competition-and-integrity-system-2026-03.md for the current system summary.
 * Older trust-system design notes are archived.
 *
 * @since 1.0
 */
@Service
public class PlayerTrustService {

  private static final Logger log = LoggerFactory.getLogger(PlayerTrustService.class);

  private final MatchRepository matchRepository;
  private final MatchConfirmationRepository confirmationRepository;

  // Configuration constants
  private static final int MINIMUM_MATCHES_FOR_PENALTY = 5;
  private static final double TRUST_THRESHOLD = 0.80; // 80%

  public PlayerTrustService(
      MatchRepository matchRepository, MatchConfirmationRepository confirmationRepository) {
    this.matchRepository = matchRepository;
    this.confirmationRepository = confirmationRepository;
  }

  /**
   * Calculate a normalized player-integrity score for a player's matches.
   *
   * <p>Score ranges from 0.0 (poor integrity signal) to 1.0 (clean signal). It is season-scoped so
   * each new season gives players a fresh chance to correct behavior.
   *
   * <p>Implements Option 6: Two-sided trust metrics - Logging accuracy rate (% of logged matches
   * that got confirmed) - Confirmation honesty rate (% of required confirmations provided) - Return
   * min(loggingAccuracy, confirmationHonesty)
   *
   * <p>Grace period: Players with < 5 qualifying matches get full trust (1.0) Threshold: Players
   * with both rates >= 80% get full trust (1.0)
   *
   * @param userId The player to evaluate
   * @param seasonId The season context (trust can vary by ladder/season)
   * @return Normalized integrity score between 0.0 and 1.0
   */
  public double calculateTrustWeight(Long userId, Long seasonId) {
    if (userId == null || seasonId == null) {
      return 1.0;
    }

    List<Match> loggedMatches = matchRepository.findByLoggedByIdAndSeasonId(userId, seasonId);
    List<MatchConfirmation> confirmationRequests =
        confirmationRepository.findByPlayerIdAndMatchSeasonId(userId, seasonId);
    return calculateTrustWeightInternal(userId, seasonId, loggedMatches, confirmationRequests);
  }

  /**
   * Batch overload that evaluates trust for multiple users in a single set of queries. Useful for
   * high-traffic dashboards where dozens of players are scored at once.
   */
  public Map<Long, Double> calculateTrustWeights(Collection<Long> userIds, Long seasonId) {
    if (seasonId == null || userIds == null || userIds.isEmpty()) {
      return Collections.emptyMap();
    }

    List<Long> distinctIds =
        userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    if (distinctIds.isEmpty()) {
      return Collections.emptyMap();
    }

    List<Match> allLogged = matchRepository.findByLoggedByIdInAndSeasonId(distinctIds, seasonId);
    Map<Long, List<Match>> loggedByUser =
        allLogged.stream()
            .filter(m -> m.getLoggedBy() != null && m.getLoggedBy().getId() != null)
            .collect(Collectors.groupingBy(m -> m.getLoggedBy().getId()));

    List<MatchConfirmation> allConfirmations =
        confirmationRepository.findByPlayerIdInAndMatchSeasonId(distinctIds, seasonId);
    Map<Long, List<MatchConfirmation>> confirmationsByUser =
        allConfirmations.stream()
            .filter(mc -> mc.getPlayer() != null && mc.getPlayer().getId() != null)
            .collect(Collectors.groupingBy(mc -> mc.getPlayer().getId()));

    Map<Long, Double> weights = new HashMap<>();
    for (Long userId : distinctIds) {
      List<Match> loggedMatches = loggedByUser.getOrDefault(userId, List.of());
      List<MatchConfirmation> confirmationRequests =
          confirmationsByUser.getOrDefault(userId, List.of());
      double weight =
          calculateTrustWeightInternal(userId, seasonId, loggedMatches, confirmationRequests);
      weights.put(userId, weight);
    }

    return Collections.unmodifiableMap(weights);
  }

  /** Shared evaluation logic once match/confirmation data is available. */
  private double calculateTrustWeightInternal(
      Long userId,
      Long seasonId,
      List<Match> loggedMatches,
      List<MatchConfirmation> confirmationRequests) {

    double loggingAccuracy = calculateLoggingAccuracy(loggedMatches);
    double confirmationHonesty = calculateConfirmationHonesty(confirmationRequests);

    int matchesLogged = countMatchesLoggedByUser(loggedMatches);
    int matchesRequiringConfirmation = countMatchesRequiringConfirmation(confirmationRequests);
    int totalQualifyingMatches = matchesLogged + matchesRequiringConfirmation;

    if (totalQualifyingMatches < MINIMUM_MATCHES_FOR_PENALTY) {
      log.debug(
          "Player {} in grace period ({} matches), integrity score: 1.0",
          userId,
          totalQualifyingMatches);
      return 1.0;
    }

    if (loggingAccuracy >= TRUST_THRESHOLD && confirmationHonesty >= TRUST_THRESHOLD) {
      log.debug(
          "Player {} above threshold (logging: {}, confirmation: {}), integrity score: 1.0",
          userId,
          loggingAccuracy,
          confirmationHonesty);
      return 1.0;
    }

    double integrityScore = Math.min(loggingAccuracy, confirmationHonesty);
    log.info(
        "Player {} integrity score: {} (logging: {}, confirmation: {})",
        userId,
        integrityScore,
        loggingAccuracy,
        confirmationHonesty);
    return integrityScore;
  }

  /**
   * Calculate logging accuracy rate. Prevents spam attacks by penalizing players who log matches
   * that don't get confirmed.
   *
   * @return Rate between 0.0 and 1.0, or 1.0 if no matches logged
   */
  private double calculateLoggingAccuracy(List<Match> matchesLogged) {
    int loggedCount = countMatchesLoggedByUser(matchesLogged);

    if (loggedCount == 0) {
      return 1.0; // No matches logged = perfect accuracy (no penalty)
    }

    int matchesConfirmed = countLoggedMatchesConfirmed(matchesLogged);

    return (double) matchesConfirmed / loggedCount;
  }

  /**
   * Calculate confirmation honesty rate. Prevents refusing legitimate losses by penalizing players
   * who don't confirm.
   *
   * @return Rate between 0.0 and 1.0, or 1.0 if no confirmations required
   */
  private double calculateConfirmationHonesty(List<MatchConfirmation> confirmations) {
    int matchesRequiringConfirmation = countMatchesRequiringConfirmation(confirmations);

    if (matchesRequiringConfirmation == 0) {
      return 1.0; // No confirmations required = perfect honesty (no penalty)
    }

    int matchesActuallyConfirmed = countMatchesActuallyConfirmed(confirmations);

    return (double) matchesActuallyConfirmed / matchesRequiringConfirmation;
  }

  /**
   * Count matches logged by this user in this season. Only counts matches in STANDARD/HIGH security
   * modes where confirmation matters.
   */
  private int countMatchesLoggedByUser(List<Match> matches) {
    if (matches == null || matches.isEmpty()) {
      return 0;
    }

    // Filter to only matches that required confirmation (STANDARD/HIGH security)
    // and have reached final state (CONFIRMED or NULLIFIED)
    return (int)
        matches.stream()
            .filter(
                m -> m.getState() == MatchState.CONFIRMED || m.getState() == MatchState.NULLIFIED)
            .filter(m -> !m.hasBothOpponentsAsGuests(m.getLoggedBy())) // Exclude all-guest matches
            .count();
  }

  /** Count matches logged by this user that got confirmed. */
  private int countLoggedMatchesConfirmed(List<Match> matches) {
    if (matches == null || matches.isEmpty()) {
      return 0;
    }

    return (int)
        matches.stream()
            .filter(m -> m.getState() == MatchState.CONFIRMED)
            .filter(m -> !m.hasBothOpponentsAsGuests(m.getLoggedBy()))
            .count();
  }

  /**
   * Count matches requiring confirmation from this user. Includes matches where user needed to
   * confirm (was on required team).
   */
  private int countMatchesRequiringConfirmation(List<MatchConfirmation> confirmations) {
    if (confirmations == null || confirmations.isEmpty()) {
      return 0;
    }

    return (int) confirmations.stream().filter(this::isTrustEligibleConfirmationRequest).count();
  }

  /** Count matches this user actually confirmed (manually). */
  private int countMatchesActuallyConfirmed(List<MatchConfirmation> confirmations) {
    if (confirmations == null || confirmations.isEmpty()) {
      return 0;
    }

    return (int)
        confirmations.stream()
            .filter(this::isTrustEligibleConfirmationRequest)
            .filter(mc -> mc.getConfirmedAt() != null)
            .count();
  }

  /**
   * Only count confirmation rows that represented a real manual obligation.
   *
   * <p>Auto-confirmed rows are created for the logger's team (and self-confirm ladders), so they
   * must not dilute confirmation honesty. We also exclude disputed nullifications: those matches
   * are moderation events, not evidence that the player silently dodged a legitimate result.
   *
   * <p>Silent-expiration evidence is represented by preserved, still-unconfirmed confirmation rows
   * on NULLIFIED non-disputed matches.
   */
  private boolean isTrustEligibleConfirmationRequest(MatchConfirmation confirmation) {
    if (confirmation == null) {
      return false;
    }

    Match match = confirmation.getMatch();
    if (match == null) {
      return false;
    }
    if (match.getState() != MatchState.CONFIRMED && match.getState() != MatchState.NULLIFIED) {
      return false;
    }
    if (confirmation.getPlayer() != null
        && match.hasBothOpponentsAsGuests(confirmation.getPlayer())) {
      return false;
    }
    if (match.getState() == MatchState.NULLIFIED && match.isDisputed()) {
      return false;
    }

    return !isAutoConfirmedRow(confirmation);
  }

  private boolean isAutoConfirmedRow(MatchConfirmation confirmation) {
    return confirmation.getMethod() == MatchConfirmation.ConfirmationMethod.AUTO
        && confirmation.getConfirmedAt() != null;
  }

  /**
   * Get detailed trust metrics for display in UI.
   *
   * <p>FUTURE: Return breakdown of logging accuracy, confirmation honesty, behavioral signals, etc.
   *
   * @param userId The player to evaluate
   * @param seasonId The season context
   * @return Trust metrics (currently empty/null)
   */
  public TrustMetrics getTrustMetrics(Long userId, Long seasonId) {
    // TODO: Implement when trust system is enabled
    return null;
  }

  /**
   * Encapsulates all trust-related metrics for a player. Extensible: can add new metrics without
   * changing calling code.
   */
  public static class TrustMetrics {
    private Double loggingAccuracyRate;
    private Double confirmationHonestyRate;
    private Double combinedTrustWeight;
    private String trustLevel; // "High", "Medium", "Low", "Suspicious"
    private java.util.List<String> trustFactors; // Human-readable explanations

    // Getters and setters
    public Double getLoggingAccuracyRate() {
      return loggingAccuracyRate;
    }

    public void setLoggingAccuracyRate(Double rate) {
      this.loggingAccuracyRate = rate;
    }

    public Double getConfirmationHonestyRate() {
      return confirmationHonestyRate;
    }

    public void setConfirmationHonestyRate(Double rate) {
      this.confirmationHonestyRate = rate;
    }

    public Double getCombinedTrustWeight() {
      return combinedTrustWeight;
    }

    public void setCombinedTrustWeight(Double weight) {
      this.combinedTrustWeight = weight;
    }

    public String getTrustLevel() {
      return trustLevel;
    }

    public void setTrustLevel(String level) {
      this.trustLevel = level;
    }

    public java.util.List<String> getTrustFactors() {
      return trustFactors;
    }

    public void setTrustFactors(java.util.List<String> factors) {
      this.trustFactors = factors;
    }
  }
}
