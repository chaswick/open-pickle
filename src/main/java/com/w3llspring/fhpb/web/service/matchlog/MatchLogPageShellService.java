package com.w3llspring.fhpb.web.service.matchlog;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ui.Model;

public class MatchLogPageShellService {

  private static final DateTimeFormatter SEASON_DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yy");

  public Long defaultLadderId(List<LadderMembership> memberships) {
    Long preferred =
        selectorMemberships(memberships, null, false).stream()
            .map(LadderMembership::getLadderConfig)
            .filter(Objects::nonNull)
            .map(LadderConfig::getId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    if (preferred != null) {
      return preferred;
    }
    if (memberships == null || memberships.isEmpty()) {
      return null;
    }
    return memberships.stream()
        .map(LadderMembership::getLadderConfig)
        .filter(Objects::nonNull)
        .filter(config -> !config.isCompetitionType())
        .map(LadderConfig::getId)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  public Long defaultCompetitionSessionLadderId(List<LadderMembership> memberships) {
    return selectorMemberships(memberships, null, true).stream()
        .map(LadderMembership::getLadderConfig)
        .filter(Objects::nonNull)
        .map(LadderConfig::getId)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  public void populateLadderSelectorContext(
      Model model,
      List<LadderMembership> memberships,
      Long ladderId,
      LadderSeason season,
      boolean competitionMode,
      boolean plainHomeNav) {
    model.addAttribute("showLadderSelection", competitionMode);
    model.addAttribute("myMemberships", memberships == null ? List.of() : memberships);
    model.addAttribute(
        "selectorMemberships", selectorMemberships(memberships, ladderId, competitionMode));
    model.addAttribute("ladderId", ladderId);
    model.addAttribute("seasonId", season != null ? season.getId() : null);
    model.addAttribute(
        "seasonName", season != null && season.getName() != null ? season.getName() : "");
    model.addAttribute("seasonDateRange", seasonDateRange(season));
    model.addAttribute("competitionLogMode", competitionMode);
    model.addAttribute("navHomePath", plainHomeNav ? "/home" : null);
    if (competitionMode) {
      model.addAttribute("selectorTitle", "Session");
      model.addAttribute("selectorSingleMessage", "You're only in one active session.");
      model.addAttribute(
          "selectorEmptyMessage",
          "You don't have any active match sessions right now. Start one from Global League first.");
    }
  }

  private List<LadderMembership> selectorMemberships(
      List<LadderMembership> memberships, Long selectedLadderId, boolean competitionMode) {
    if (memberships == null || memberships.isEmpty()) {
      return List.of();
    }
    return memberships.stream()
        .filter(Objects::nonNull)
        .filter(
            membership -> {
              LadderConfig config = membership.getLadderConfig();
              if (config == null) {
                return false;
              }
              if (config.isCompetitionType()) {
                return false;
              }
              if (competitionMode) {
                return isActiveSessionConfig(config);
              }
              if (!config.isSessionType()) {
                return true;
              }
              return Objects.equals(config.getId(), selectedLadderId);
            })
        .collect(Collectors.toList());
  }

  private boolean isActiveSessionConfig(LadderConfig config) {
    if (config == null || !config.isSessionType()) {
      return false;
    }
    return config.getExpiresAt() == null || config.getExpiresAt().isAfter(java.time.Instant.now());
  }

  private String seasonDateRange(LadderSeason season) {
    if (season == null || season.getStartDate() == null || season.getEndDate() == null) {
      return "";
    }
    return SEASON_DATE_FORMAT.format(season.getStartDate())
        + " - "
        + SEASON_DATE_FORMAT.format(season.getEndDate());
  }
}
