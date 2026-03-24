package com.w3llspring.fhpb.web.service.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.session.LadderPageState;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class HomeSelectionService {

  private final LadderMembershipRepository membershipRepo;
  private final LadderConfigRepository ladderConfigRepo;
  private final LadderSeasonRepository seasonRepo;

  public HomeSelectionService(
      LadderMembershipRepository membershipRepo,
      LadderConfigRepository ladderConfigRepo,
      LadderSeasonRepository seasonRepo) {
    this.membershipRepo = membershipRepo;
    this.ladderConfigRepo = ladderConfigRepo;
    this.seasonRepo = seasonRepo;
  }

  public HomeSelection resolveSelection(
      User user, Long ladderIdParam, Long seasonIdParam, List<LadderMembership> memberships) {
    LadderPageState state = new LadderPageState();

    List<LadderMembership> ladderMemberships =
        memberships != null
            ? memberships
            : membershipRepo.findByUserIdAndState(user.getId(), LadderMembership.State.ACTIVE);
    List<LadderMembership> selectorMemberships = selectorMemberships(ladderMemberships, null);

    List<Long> myLadderIds =
        selectorMemberships.stream()
            .map(LadderMembership::getLadderConfig)
            .filter(Objects::nonNull)
            .map(LadderConfig::getId)
            .distinct()
            .collect(Collectors.toList());
    if (myLadderIds.isEmpty()) {
      return new HomeSelection(state, null, null);
    }

    Long selectedLadderId =
        (ladderIdParam != null && myLadderIds.contains(ladderIdParam))
            ? ladderIdParam
            : myLadderIds.get(0);
    state.ladderId = selectedLadderId;

    LadderConfig ladder = ladderConfigRepo.findById(selectedLadderId).orElse(null);
    state.ladderName = ladderName(ladder);

    List<LadderSeason> seasonsDesc =
        seasonRepo.findByLadderConfigIdOrderByStartDateDesc(selectedLadderId);

    LadderSeason season = null;
    if (seasonIdParam != null) {
      season =
          seasonsDesc.stream()
              .filter(candidate -> Objects.equals(candidate.getId(), seasonIdParam))
              .findFirst()
              .orElse(null);
      if (season != null
          && (season.getLadderConfig() == null
              || !Objects.equals(season.getLadderConfig().getId(), selectedLadderId))) {
        season = null;
      }
    }
    if (season == null) {
      season =
          seasonsDesc.stream()
              .filter(candidate -> candidate.getState() == LadderSeason.State.ACTIVE)
              .findFirst()
              .orElse(null);
    }
    if (season == null) {
      season =
          seasonsDesc.stream()
              .filter(candidate -> candidate.getState() != LadderSeason.State.SCHEDULED)
              .findFirst()
              .orElse(seasonsDesc.stream().findFirst().orElse(null));
    }

    if (season != null) {
      state.seasonId = season.getId();
      state.seasonName = nullSafe(season.getName(), "");
      state.seasonDateRange = dateRange(season);

      LocalDate currentStart = season.getStartDate();

      state.prevSeasonId =
          seasonsDesc.stream()
              .filter(candidate -> candidate.getState() != LadderSeason.State.SCHEDULED)
              .filter(
                  candidate ->
                      currentStart != null
                          && candidate.getStartDate() != null
                          && candidate.getStartDate().isBefore(currentStart))
              .max(Comparator.comparing(LadderSeason::getStartDate))
              .map(LadderSeason::getId)
              .orElse(null);

      state.nextSeasonId =
          seasonsDesc.stream()
              .filter(candidate -> candidate.getState() != LadderSeason.State.SCHEDULED)
              .filter(
                  candidate ->
                      currentStart != null
                          && candidate.getStartDate() != null
                          && candidate.getStartDate().isAfter(currentStart))
              .min(Comparator.comparing(LadderSeason::getStartDate))
              .map(LadderSeason::getId)
              .orElse(null);
    }

    int idx = myLadderIds.indexOf(selectedLadderId);
    state.prevLadderId = (idx > 0) ? myLadderIds.get(idx - 1) : null;
    state.nextLadderId = (idx < myLadderIds.size() - 1) ? myLadderIds.get(idx + 1) : null;

    return new HomeSelection(state, ladder, season);
  }

  public List<LadderMembership> selectorMemberships(
      List<LadderMembership> memberships, Long selectedLadderId) {
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
              if (!config.isSessionType()) {
                return true;
              }
              return Objects.equals(config.getId(), selectedLadderId);
            })
        .collect(Collectors.toList());
  }

  public boolean isCompetitionSelection(Long ladderId, Long seasonId) {
    return isCompetitionLadder(ladderId) || isCompetitionSeason(seasonId);
  }

  public String ladderName(LadderConfig ladder) {
    if (ladder == null) {
      return "Ladder";
    }
    if (ladder.getTitle() != null && !ladder.getTitle().isBlank()) {
      return ladder.getTitle();
    }
    if (ladder.getId() != null) {
      return "Ladder #" + ladder.getId();
    }
    return "Ladder";
  }

  public String dateRange(LadderSeason season) {
    if (season == null || season.getStartDate() == null) {
      return "";
    }
    LocalDate start = season.getStartDate();
    LocalDate end = season.getEndDate();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy");
    boolean placeholder = end != null && end.isAfter(start.plusYears(80));
    String startText = start.format(formatter);
    String endText;
    if (placeholder) {
      endText = "Present";
    } else if (end != null) {
      endText = end.format(formatter);
    } else {
      endText = "Present";
    }
    return startText + " - " + endText;
  }

  private boolean isCompetitionLadder(Long ladderId) {
    if (ladderId == null) {
      return false;
    }
    return ladderConfigRepo.findById(ladderId).map(LadderConfig::isCompetitionType).orElse(false);
  }

  private boolean isCompetitionSeason(Long seasonId) {
    if (seasonId == null) {
      return false;
    }
    LadderSeason season =
        seasonRepo
            .findByIdWithLadderConfig(seasonId)
            .orElseGet(() -> seasonRepo.findById(seasonId).orElse(null));
    return season != null
        && season.getLadderConfig() != null
        && season.getLadderConfig().isCompetitionType();
  }

  private String nullSafe(String value, String defaultValue) {
    return value == null ? defaultValue : value;
  }

  public record HomeSelection(LadderPageState state, LadderConfig ladder, LadderSeason season) {}
}
