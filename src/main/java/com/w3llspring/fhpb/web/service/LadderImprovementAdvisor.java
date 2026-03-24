package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.util.List;

public interface LadderImprovementAdvisor {

  Advice buildAdvice(User user);

  Advice buildAdvice(User user, LadderConfig ladder, LadderSeason season);

  final class Advice {
    private final String quickSummary;
    private final String detailedSummary;
    private final List<String> actionTips;
    private final List<RecentRatingChange> recentChanges;

    private Advice(
        String quickSummary,
        String detailedSummary,
        List<String> actionTips,
        List<RecentRatingChange> recentChanges) {
      this.quickSummary = quickSummary;
      this.detailedSummary = detailedSummary;
      this.actionTips = actionTips == null ? List.of() : List.copyOf(actionTips);
      this.recentChanges = recentChanges;
    }

    public static Advice simple(String message) {
      return new Advice(message, null, List.of(), List.of());
    }

    public static Advice detail(
        String quickSummary, String detailedSummary, List<RecentRatingChange> recentChanges) {
      return detail(quickSummary, detailedSummary, List.of(), recentChanges);
    }

    public static Advice detail(
        String quickSummary,
        String detailedSummary,
        List<String> actionTips,
        List<RecentRatingChange> recentChanges) {
      List<RecentRatingChange> safeChanges =
          recentChanges == null ? List.of() : List.copyOf(recentChanges);
      return new Advice(quickSummary, detailedSummary, actionTips, safeChanges);
    }

    public static Advice history(String quickSummary, List<RecentRatingChange> recentChanges) {
      return detail(quickSummary, null, recentChanges);
    }

    public String getQuickSummary() {
      return quickSummary;
    }

    public String getDetailedSummary() {
      return detailedSummary;
    }

    public List<String> getActionTips() {
      return actionTips;
    }

    public List<RecentRatingChange> getRecentChanges() {
      return recentChanges;
    }
  }

  final class RecentRatingChange {
    private final Instant occurredAt;
    private final String summary;
    private final List<String> explanationSteps;

    public RecentRatingChange(Instant occurredAt, String summary, List<String> explanationSteps) {
      this.occurredAt = occurredAt;
      this.summary = summary;
      this.explanationSteps = explanationSteps == null ? List.of() : List.copyOf(explanationSteps);
    }

    public Instant getOccurredAt() {
      return occurredAt;
    }

    public String getSummary() {
      return summary;
    }

    public List<String> getExplanationSteps() {
      return explanationSteps;
    }
  }
}
