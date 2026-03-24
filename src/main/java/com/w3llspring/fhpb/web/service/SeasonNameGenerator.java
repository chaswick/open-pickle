package com.w3llspring.fhpb.web.service;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Generates creative, seasonal names for ladder seasons based on the time of year. Provides
 * Florida-themed names with variety to avoid repetition across seasons.
 */
@Component
public class SeasonNameGenerator {

  private final Random random = new Random();

  // Best-effort repeat reduction (in-memory). This avoids obvious repeats without needing DB
  // lookups.
  private final Deque<String> recentBaseNames = new ArrayDeque<>();
  private static final int RECENT_BASE_NAME_LIMIT = 30;
  private static final int RECENT_AVOID_ATTEMPTS = 30;
  private static final List<String> GENERIC_DESCRIPTORS =
      List.of(
          "Open",
          "Cup",
          "Series",
          "Masters",
          "Shootout",
          "Classic",
          "Challenge",
          "Clash",
          "Showdown",
          "Circuit");

  /**
   * Generate a season name based on the start date. Returns a creative seasonal name with year,
   * e.g., "Summer Storm - 2025"
   */
  public String generate(LocalDate startDate) {
    if (startDate == null) {
      return "Season";
    }

    SeasonTheme theme = determineTheme(startDate);
    String baseName = selectRandomName(theme);

    return baseName + " - " + startDate.getYear();
  }

  /**
   * Generate a season name with ordinal suffix when multiple seasons of the same type occur in the
   * same year (e.g., "Fall Fiesta II - 2025")
   */
  public String generateWithOrdinal(LocalDate startDate, int ordinalWithinYear) {
    if (startDate == null) {
      return "Season";
    }

    SeasonTheme theme = determineTheme(startDate);
    String baseName = selectRandomName(theme);

    if (ordinalWithinYear <= 1) {
      return baseName + " - " + startDate.getYear();
    }

    String suffix = toRoman(ordinalWithinYear);
    return baseName + " " + suffix + " - " + startDate.getYear();
  }

  /** Determine the seasonal theme based on the month */
  private SeasonTheme determineTheme(LocalDate date) {
    Month month = date.getMonth();
    int day = date.getDayOfMonth();

    // More granular season detection
    switch (month) {
      case JANUARY:
        return SeasonTheme.WINTER_EARLY;
      case FEBRUARY:
        return day <= 14 ? SeasonTheme.WINTER_EARLY : SeasonTheme.WINTER_LATE;
      case MARCH:
        return day <= 20 ? SeasonTheme.SPRING_EARLY : SeasonTheme.SPRING_MID;
      case APRIL:
        return SeasonTheme.SPRING_MID;
      case MAY:
        return day <= 15 ? SeasonTheme.SPRING_LATE : SeasonTheme.SUMMER_EARLY;
      case JUNE:
        return SeasonTheme.SUMMER_EARLY;
      case JULY:
        return SeasonTheme.SUMMER_MID;
      case AUGUST:
        return SeasonTheme.SUMMER_LATE;
      case SEPTEMBER:
        return day <= 22 ? SeasonTheme.SUMMER_LATE : SeasonTheme.FALL_EARLY;
      case OCTOBER:
        return SeasonTheme.FALL_MID;
      case NOVEMBER:
        return SeasonTheme.FALL_LATE;
      case DECEMBER:
        return day <= 21 ? SeasonTheme.FALL_LATE : SeasonTheme.WINTER_EARLY;
      default:
        return SeasonTheme.SPRING_MID;
    }
  }

  /** Select a random name from the theme's name pool */
  private String selectRandomName(SeasonTheme theme) {
    List<String> names = getThemeNames(theme);
    if (names.isEmpty()) {
      return "Seasonal Showdown";
    }

    // Try to avoid recently-used names.
    for (int i = 0; i < RECENT_AVOID_ATTEMPTS; i++) {
      String candidate = names.get(random.nextInt(names.size()));
      if (!isRecentlyUsed(candidate)) {
        markRecentlyUsed(candidate);
        return candidate;
      }
    }

    // Fallback: accept a repeat.
    String fallback = names.get(random.nextInt(names.size()));
    markRecentlyUsed(fallback);
    return fallback;
  }

  /** Get all available names for a given theme */
  private List<String> getThemeNames(SeasonTheme theme) {
    String period = timeOfYearLabel(theme);
    List<String> names = new ArrayList<>(GENERIC_DESCRIPTORS.size());

    for (String descriptor : GENERIC_DESCRIPTORS) {
      names.add(period + " " + descriptor);
    }

    if (names.isEmpty()) {
      names.add("Season Open");
    }

    return names;
  }

  private String timeOfYearLabel(SeasonTheme theme) {
    if (theme == null) return "Season";
    return switch (theme) {
      case WINTER_EARLY -> "Early Winter";
      case WINTER_LATE -> "Late Winter";
      case SPRING_EARLY -> "Early Spring";
      case SPRING_MID -> "Mid Spring";
      case SPRING_LATE -> "Late Spring";
      case SUMMER_EARLY -> "Early Summer";
      case SUMMER_MID -> "Mid Summer";
      case SUMMER_LATE -> "Late Summer";
      case FALL_EARLY -> "Early Fall";
      case FALL_MID -> "Mid Fall";
      case FALL_LATE -> "Late Fall";
    };
  }

  private boolean isRecentlyUsed(String candidate) {
    if (candidate == null || candidate.isBlank()) return false;
    synchronized (recentBaseNames) {
      return recentBaseNames.contains(candidate);
    }
  }

  private void markRecentlyUsed(String baseName) {
    if (baseName == null || baseName.isBlank()) return;
    synchronized (recentBaseNames) {
      recentBaseNames.remove(baseName);
      recentBaseNames.addFirst(baseName);
      while (recentBaseNames.size() > RECENT_BASE_NAME_LIMIT) {
        recentBaseNames.removeLast();
      }
    }
  }

  /** Convert integer to Roman numerals for ordinal display */
  private String toRoman(int number) {
    if (number <= 0) {
      return "I";
    }

    int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    StringBuilder result = new StringBuilder();
    int remaining = number;

    for (int i = 0; i < values.length; i++) {
      while (remaining >= values[i]) {
        result.append(numerals[i]);
        remaining -= values[i];
      }
    }

    return result.toString();
  }

  /** Internal enum for categorizing seasons with more granularity */
  private enum SeasonTheme {
    WINTER_EARLY, // Jan - mid Feb
    WINTER_LATE, // mid Feb - early Mar
    SPRING_EARLY, // mid Mar - early Apr
    SPRING_MID, // Apr
    SPRING_LATE, // May
    SUMMER_EARLY, // Jun
    SUMMER_MID, // Jul
    SUMMER_LATE, // Aug - late Sep
    FALL_EARLY, // late Sep - early Oct
    FALL_MID, // Oct
    FALL_LATE // Nov - late Dec
  }
}
