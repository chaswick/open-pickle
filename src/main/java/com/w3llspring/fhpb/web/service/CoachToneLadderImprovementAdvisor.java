package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.LadderRatingChangeRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderRatingChange;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithm;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringProfile;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CoachToneLadderImprovementAdvisor implements LadderImprovementAdvisor {
  private static final int RECENT_CHANGE_LIMIT = 5;

  private final LadderSeasonRepository seasonRepo;
  private final LadderRatingChangeRepository ratingChangeRepo;
  private final LadderScoringAlgorithms scoringAlgorithms;

  public CoachToneLadderImprovementAdvisor(
      LadderSeasonRepository seasonRepo,
      LadderRatingChangeRepository ratingChangeRepo,
      LadderScoringAlgorithms scoringAlgorithms) {
    this.seasonRepo = seasonRepo;
    this.ratingChangeRepo = ratingChangeRepo;
    this.scoringAlgorithms = scoringAlgorithms;
  }

  @Override
  public Advice buildAdvice(User user) {
    return buildAdvice(user, null, null);
  }

  @Override
  public Advice buildAdvice(User user, LadderConfig ladder, LadderSeason explicitSeason) {
    if (user == null || user.getId() == null) {
      return Advice.simple("Log in to review your recent rating changes.");
    }

    LadderSeason season = resolveSeasonContext(ladder, explicitSeason);
    if (season == null) {
      return Advice.simple("Pick an active season to review how your rating has moved.");
    }

    List<LadderRatingChange> changes =
        ratingChangeRepo.findRecentBySeasonAndUser(
            season, user.getId(), PageRequest.of(0, RECENT_CHANGE_LIMIT));

    if (changes.isEmpty()) {
      return Advice.simple(
          "Log and confirm a ladder match to start building your rating explanation history.");
    }

    LadderScoringAlgorithm algorithm = scoringAlgorithms.resolve(season);
    LadderScoringProfile profile = algorithm.profile();
    String quickSummary =
        (profile != null
                && profile.getOptimizationAdvice() != null
                && !profile.getOptimizationAdvice().isBlank())
            ? profile.getOptimizationAdvice()
            : "Recent rating changes below show exactly how this ladder moved your number.";
    List<String> actionTips = profile != null ? profile.getOptimizationTips() : List.of();
    String detailedSummary =
        changes.size() == 1
            ? "Your latest rating change is below. Expand it to see exactly how it was calculated."
            : String.format(
                "Your last %d rating changes are below. Expand any one to see exactly how it was calculated.",
                changes.size());

    List<RecentRatingChange> recentChanges =
        changes.stream().map(this::toRecentRatingChange).toList();

    return Advice.detail(quickSummary, detailedSummary, actionTips, recentChanges);
  }

  private LadderSeason resolveSeasonContext(LadderConfig ladder, LadderSeason explicitSeason) {
    if (explicitSeason != null) {
      return explicitSeason;
    }
    if (ladder != null && ladder.getId() != null) {
      return seasonRepo
          .findActive(ladder.getId())
          .orElseGet(
              () ->
                  seasonRepo
                      .findTopByLadderConfigIdOrderByStartDateDesc(ladder.getId())
                      .orElse(null));
    }
    return null;
  }

  private RecentRatingChange toRecentRatingChange(LadderRatingChange change) {
    return new RecentRatingChange(
        change.getOccurredAt(), change.getSummary(), splitDetails(change.getDetails()));
  }

  private List<String> splitDetails(String details) {
    if (details == null || details.isBlank()) {
      return List.of("No explanation available.");
    }
    return details.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
  }
}
