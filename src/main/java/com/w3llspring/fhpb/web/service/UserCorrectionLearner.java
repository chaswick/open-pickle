package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Phase D: ML Integration for User Self-Correction Feature
 *
 * <p>Learns from user's correction history to improve future match interpretations. Uses phonetic
 * similarity to match spoken names to corrected player assignments.
 *
 * <p>Design Philosophy: - POST-PROCESS only (doesn't override good matches) - Only improves
 * LOW-CONFIDENCE tokens (< 0.7) - Per-user learning (personal speech patterns) - Zero storage
 * overhead (queries existing Match data) - Confidence boost (+0.15) for matched patterns
 */
@Service
public class UserCorrectionLearner {

  private static final double LOW_CONFIDENCE_THRESHOLD = 0.70;
  private static final double PHONETIC_MATCH_THRESHOLD = 0.80;
  private static final double CONFIDENCE_BOOST = 0.15;
  private static final int MAX_CORRECTIONS_TO_ANALYZE = 50;

  private final MatchRepository matchRepository;
  private final PhoneticMatchingService phoneticService;

  public UserCorrectionLearner(
      MatchRepository matchRepository, PhoneticMatchingService phoneticService) {
    this.matchRepository = matchRepository;
    this.phoneticService = phoneticService;
  }

  /**
   * Analyzes user's correction history to build a personal name mapping.
   *
   * @param userId The user whose correction history to analyze
   * @return Map of phonetic codes to corrected player names/IDs
   */
  public Map<String, List<PlayerCorrection>> buildUserCorrectionMap(Long userId) {
    if (userId == null) {
      return Map.of();
    }

    // Fetch user's recent corrected matches (limited for performance)
    List<Match> corrections =
        matchRepository.findUserCorrectedMatchesByParticipant(
            userId, PageRequest.of(0, MAX_CORRECTIONS_TO_ANALYZE));

    if (corrections.isEmpty()) {
      return Map.of();
    }

    Map<String, List<PlayerCorrection>> correctionMap = new HashMap<>();

    for (Match match : corrections) {
      // Extract player names from corrected match
      List<User> players = extractPlayers(match);

      // For each player, store phonetic code mapping
      for (User player : players) {
        if (player == null) continue;

        String playerName = getPlayerDisplayName(player);
        String phoneticCode = phoneticService.encodePhonetically(playerName);

        correctionMap
            .computeIfAbsent(phoneticCode, k -> new ArrayList<>())
            .add(new PlayerCorrection(player.getId(), playerName, match.getTranscript()));
      }
    }

    return correctionMap;
  }

  /**
   * Finds the best matching player from correction history for a given spoken name.
   *
   * @param spokenName The name as spoken/transcribed
   * @param correctionMap User's correction history map
   * @return Best matching player ID, or null if no good match found
   */
  public Long findBestMatch(String spokenName, Map<String, List<PlayerCorrection>> correctionMap) {
    if (spokenName == null || spokenName.isBlank() || correctionMap.isEmpty()) {
      return null;
    }

    String spokenPhonetic = phoneticService.encodePhonetically(spokenName);

    // Direct phonetic match
    List<PlayerCorrection> directMatches = correctionMap.get(spokenPhonetic);
    if (directMatches != null && !directMatches.isEmpty()) {
      // Return most frequent correction
      return getMostFrequent(directMatches);
    }

    // Fuzzy phonetic match (compare similarity)
    BestMatch bestMatch = null;

    for (Map.Entry<String, List<PlayerCorrection>> entry : correctionMap.entrySet()) {
      String correctedPhonetic = entry.getKey();
      double similarity = calculatePhoneticSimilarity(spokenPhonetic, correctedPhonetic);

      if (similarity >= PHONETIC_MATCH_THRESHOLD) {
        if (bestMatch == null || similarity > bestMatch.similarity) {
          bestMatch = new BestMatch(getMostFrequent(entry.getValue()), similarity);
        }
      }
    }

    return bestMatch != null ? bestMatch.playerId : null;
  }

  /**
   * Calculates phonetic similarity between two phonetic codes. Uses Levenshtein distance normalized
   * to 0-1 range.
   */
  private double calculatePhoneticSimilarity(String code1, String code2) {
    return phoneticService.computeStringSimilarity(code1, code2);
  }

  /** Extracts all non-null players from a match. */
  private List<User> extractPlayers(Match match) {
    List<User> players = new ArrayList<>(4);

    if (match.getA1() != null) players.add(match.getA1());
    if (match.getA2() != null) players.add(match.getA2());
    if (match.getB1() != null) players.add(match.getB1());
    if (match.getB2() != null) players.add(match.getB2());

    return players;
  }

  /** Gets a public-safe display name for a player. */
  private String getPlayerDisplayName(User player) {
    return com.w3llspring.fhpb.web.util.UserPublicName.forUser(player);
  }

  /** Returns the most frequently occurring player ID from corrections. */
  private Long getMostFrequent(List<PlayerCorrection> corrections) {
    return corrections.stream()
        .collect(Collectors.groupingBy(PlayerCorrection::getPlayerId, Collectors.counting()))
        .entrySet()
        .stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(corrections.get(0).getPlayerId());
  }

  /** Represents a player correction from history. */
  public static class PlayerCorrection {
    private final Long playerId;
    private final String playerName;
    private final String originalTranscript;

    public PlayerCorrection(Long playerId, String playerName, String originalTranscript) {
      this.playerId = playerId;
      this.playerName = playerName;
      this.originalTranscript = originalTranscript;
    }

    public Long getPlayerId() {
      return playerId;
    }

    public String getPlayerName() {
      return playerName;
    }

    public String getOriginalTranscript() {
      return originalTranscript;
    }
  }

  /** Represents a best match result. */
  private static class BestMatch {
    final Long playerId;
    final double similarity;

    BestMatch(Long playerId, double similarity) {
      this.playerId = playerId;
      this.similarity = similarity;
    }
  }
}
