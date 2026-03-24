package com.w3llspring.fhpb.web.service;

import org.apache.commons.codec.language.DoubleMetaphone;
import org.springframework.stereotype.Service;

/**
 * Centralized phonetic and string similarity utility service.
 *
 * <p>Provides: - Phonetic encoding using DoubleMetaphone algorithm - Levenshtein distance
 * calculation for string similarity - Normalized similarity scoring (0.0 - 1.0 range)
 *
 * <p>Used by: - DefaultSpokenMatchInterpreter: Name matching in voice transcripts -
 * UserCorrectionLearner: ML-based correction pattern matching
 *
 * <p>Design: Single instance of DoubleMetaphone, reusable algorithms
 */
@Service
public class PhoneticMatchingService {

  private final DoubleMetaphone metaphone;

  public PhoneticMatchingService() {
    this.metaphone = new DoubleMetaphone();
    this.metaphone.setMaxCodeLen(6); // Balance accuracy vs performance
  }

  /**
   * Converts a string to its phonetic representation using DoubleMetaphone.
   *
   * @param text The text to encode phonetically
   * @return Phonetic code, or empty string if input is null/blank
   */
  public String encodePhonetically(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    return metaphone.doubleMetaphone(text);
  }

  /**
   * Calculates Levenshtein distance between two strings.
   *
   * <p>Uses optimized space-efficient algorithm (O(min(m,n)) space).
   *
   * @param lhs First string
   * @param rhs Second string
   * @return Edit distance (number of insertions/deletions/substitutions needed)
   */
  public int computeLevenshteinDistance(String lhs, String rhs) {
    if (lhs == null || rhs == null) {
      return Math.max(lhs == null ? 0 : lhs.length(), rhs == null ? 0 : rhs.length());
    }

    if (lhs.equals(rhs)) {
      return 0;
    }

    int len0 = lhs.length() + 1;
    int len1 = rhs.length() + 1;

    int[] cost = new int[len0];
    int[] newcost = new int[len0];

    for (int i = 0; i < len0; i++) {
      cost[i] = i;
    }

    for (int j = 1; j < len1; j++) {
      newcost[0] = j;
      char rhsChar = rhs.charAt(j - 1);

      for (int i = 1; i < len0; i++) {
        char lhsChar = lhs.charAt(i - 1);
        int match = (lhsChar == rhsChar) ? 0 : 1;
        int costReplace = cost[i - 1] + match;
        int costInsert = cost[i] + 1;
        int costDelete = newcost[i - 1] + 1;

        int min = Math.min(Math.min(costInsert, costDelete), costReplace);
        newcost[i] = min;
      }

      int[] swap = cost;
      cost = newcost;
      newcost = swap;
    }

    return cost[len0 - 1];
  }

  /**
   * Calculates normalized string similarity (0.0 = completely different, 1.0 = identical).
   *
   * <p>Based on Levenshtein distance normalized by maximum possible edits (longer string length).
   *
   * @param s1 First string
   * @param s2 Second string
   * @return Similarity score between 0.0 and 1.0
   */
  public double computeStringSimilarity(String s1, String s2) {
    if (s1 == null && s2 == null) {
      return 1.0;
    }
    if (s1 == null || s2 == null) {
      return 0.0;
    }
    if (s1.equals(s2)) {
      return 1.0;
    }

    int distance = computeLevenshteinDistance(s1, s2);
    int maxLength = Math.max(s1.length(), s2.length());

    return maxLength == 0 ? 1.0 : 1.0 - ((double) distance / maxLength);
  }

  /**
   * Calculates phonetic similarity between two strings by comparing their phonetic codes.
   *
   * @param text1 First text
   * @param text2 Second text
   * @return Similarity score between 0.0 and 1.0
   */
  public double computePhoneticSimilarity(String text1, String text2) {
    if (text1 == null && text2 == null) {
      return 1.0;
    }
    if (text1 == null || text2 == null) {
      return 0.0;
    }

    String code1 = encodePhonetically(text1);
    String code2 = encodePhonetically(text2);

    return computeStringSimilarity(code1, code2);
  }
}
