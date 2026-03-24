package com.w3llspring.fhpb.web.service.standings;

import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionDisplayNameModerationService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SeasonStandingsViewService {

  private final LadderStandingRepository standingRepository;
  private final LadderV2Service ladderV2Service;
  private final CompetitionDisplayNameModerationService competitionDisplayNameModerationService;

  public SeasonStandingsViewService(
      LadderStandingRepository standingRepository,
      LadderV2Service ladderV2Service,
      Optional<CompetitionDisplayNameModerationService> competitionDisplayNameModerationService) {
    this.standingRepository = standingRepository;
    this.ladderV2Service = ladderV2Service;
    this.competitionDisplayNameModerationService =
        competitionDisplayNameModerationService.orElse(null);
  }

  public SeasonStandingsView load(LadderSeason season) {
    if (season == null) {
      return SeasonStandingsView.empty();
    }
    List<LadderStanding> standings =
        standingRepository.findBySeasonOrderByRankNoAscWithUser(season);
    if (standings == null || standings.isEmpty()) {
      return SeasonStandingsView.empty();
    }
    List<LadderV2Service.LadderRow> rows = ladderV2Service.buildDisplayRows(standings);
    if (shouldApplyCompetitionDisplayNames(season)) {
      competitionDisplayNameModerationService.applyCompetitionDisplayNames(rows, standings);
    }
    return new SeasonStandingsView(List.copyOf(standings), List.copyOf(rows));
  }

  public LadderV2Service.LadderRow findRowForUser(SeasonStandingsView standingsView, Long userId) {
    if (standingsView == null || userId == null) {
      return null;
    }
    return standingsView.rows().stream()
        .filter(Objects::nonNull)
        .filter(row -> Objects.equals(row.userId, userId))
        .findFirst()
        .orElse(null);
  }

  public User findStandingUser(SeasonStandingsView standingsView, Long userId) {
    if (standingsView == null || userId == null) {
      return null;
    }
    return standingsView.standings().stream()
        .map(LadderStanding::getUser)
        .filter(Objects::nonNull)
        .filter(user -> Objects.equals(user.getId(), userId))
        .findFirst()
        .orElse(null);
  }

  private boolean shouldApplyCompetitionDisplayNames(LadderSeason season) {
    return competitionDisplayNameModerationService != null
        && season.getLadderConfig() != null
        && season.getLadderConfig().isCompetitionType();
  }

  public record SeasonStandingsView(
      List<LadderStanding> standings, List<LadderV2Service.LadderRow> rows) {

    public static SeasonStandingsView empty() {
      return new SeasonStandingsView(List.of(), List.of());
    }
  }
}
