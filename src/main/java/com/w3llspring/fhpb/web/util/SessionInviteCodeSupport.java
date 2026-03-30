package com.w3llspring.fhpb.web.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public final class SessionInviteCodeSupport {

  private static final Pattern CANONICAL_PATTERN = Pattern.compile("^([A-Z]+)-([A-Z]+)-(\\d{1,3})$");

  public static final List<String> WORD_ONE_OPTIONS =
      List.of(
          "AMBER",
          "APPLE",
          "BOLD",
          "BRISK",
          "CANYON",
          "CEDAR",
          "CITRUS",
          "CLEAR",
          "CLOVER",
          "COPPER",
          "CRISP",
          "DELTA",
          "EAGER",
          "EMBER",
          "FRESH",
          "GOLD",
          "GROVE",
          "HARBOR",
          "IVORY",
          "JADE",
          "KINETIC",
          "LUCKY",
          "MAPLE",
          "MINT",
          "NOVA",
          "OAK",
          "PEPPER",
          "PINE",
          "QUICK",
          "RIVER",
          "SOLAR",
          "SPRUCE",
          "SUNNY",
          "TIDAL",
          "UNION",
          "VIVID",
          "WILD",
          "YONDER",
          "ZEST");

  public static final List<String> WORD_TWO_OPTIONS =
      List.of(
          "ACE",
          "BALL",
          "COURT",
          "DINK",
          "DRIVE",
          "DROP",
          "GAME",
          "GLIDE",
          "LOB",
          "MATCH",
          "NET",
          "PADDLE",
          "PICKLE",
          "POINT",
          "RALLY",
          "RETURN",
          "SERVE",
          "SHOT",
          "SLICE",
          "SPIN",
          "VOLLEY",
          "WIN",
          "ZONE");

  public static final List<String> NUMBER_OPTIONS =
      IntStream.rangeClosed(10, 99).mapToObj(Integer::toString).toList();

  private SessionInviteCodeSupport() {}

  public static String generateCode(SecureRandom random) {
    SecureRandom rng = random != null ? random : new SecureRandom();
    String wordOne = WORD_ONE_OPTIONS.get(rng.nextInt(WORD_ONE_OPTIONS.size()));
    String wordTwo = WORD_TWO_OPTIONS.get(rng.nextInt(WORD_TWO_OPTIONS.size()));
    String number = NUMBER_OPTIONS.get(rng.nextInt(NUMBER_OPTIONS.size()));
    return compose(wordOne, wordTwo, number);
  }

  public static String normalizeForLookup(String value) {
    if (value == null) {
      return null;
    }
    String normalized =
        value.trim()
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
    return normalized.isBlank() ? null : normalized;
  }

  public static String compose(String wordOne, String wordTwo, String number) {
    String normalizedWordOne = normalizeWord(wordOne);
    String normalizedWordTwo = normalizeWord(wordTwo);
    String normalizedNumber = normalizeNumber(number);
    if (normalizedWordOne == null || normalizedWordTwo == null || normalizedNumber == null) {
      return null;
    }
    return normalizedWordOne + "-" + normalizedWordTwo + "-" + normalizedNumber;
  }

  public static Parts split(String value) {
    String normalized = normalizeForLookup(value);
    if (normalized == null) {
      return null;
    }
    Matcher matcher = CANONICAL_PATTERN.matcher(normalized);
    if (!matcher.matches()) {
      return null;
    }
    return new Parts(matcher.group(1), matcher.group(2), matcher.group(3));
  }

  public static Instant activeUntil(Instant lastInviteChangeAt, long activeSeconds) {
    if (lastInviteChangeAt == null) {
      return null;
    }
    return lastInviteChangeAt.plusSeconds(Math.max(1L, activeSeconds));
  }

  public static boolean isCurrentlyActive(
      String inviteCode, Instant lastInviteChangeAt, long activeSeconds, Instant now) {
    if (inviteCode == null || inviteCode.isBlank()) {
      return false;
    }
    if (lastInviteChangeAt == null) {
      return true;
    }
    Instant expiresAt = activeUntil(lastInviteChangeAt, activeSeconds);
    if (expiresAt == null) {
      return true;
    }
    Instant reference = now != null ? now : Instant.now();
    return reference.isBefore(expiresAt);
  }

  private static String normalizeWord(String value) {
    if (value == null) {
      return null;
    }
    String normalized =
        value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", "");
    return normalized.isBlank() ? null : normalized;
  }

  private static String normalizeNumber(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim().replaceAll("[^0-9]+", "");
    return normalized.isBlank() ? null : normalized;
  }

  public record Parts(String wordOne, String wordTwo, String number) {}
}
