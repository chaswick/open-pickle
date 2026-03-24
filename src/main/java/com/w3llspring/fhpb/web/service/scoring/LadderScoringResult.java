package com.w3llspring.fhpb.web.service.scoring;

import com.w3llspring.fhpb.web.model.User;
import java.util.Map;

public final class LadderScoringResult {
  private final Map<User, Integer> adjustments;
  private final Map<Long, LadderScoringExplanation> explanations;
  private final int baseStep;
  private final double guestScale;

  public LadderScoringResult(
      Map<User, Integer> adjustments,
      Map<Long, LadderScoringExplanation> explanations,
      int baseStep,
      double guestScale) {
    this.adjustments = adjustments;
    this.explanations = explanations;
    this.baseStep = baseStep;
    this.guestScale = guestScale;
  }

  public static LadderScoringResult empty(int baseStep, double guestScale) {
    return new LadderScoringResult(Map.of(), Map.of(), baseStep, guestScale);
  }

  public Map<User, Integer> getAdjustments() {
    return adjustments;
  }

  public Map<Long, LadderScoringExplanation> getExplanations() {
    return explanations;
  }

  public int getBaseStep() {
    return baseStep;
  }

  public double getGuestScale() {
    return guestScale;
  }

  public int deltaFor(User user) {
    return user == null ? 0 : adjustments.getOrDefault(user, 0);
  }

  public LadderScoringExplanation explanationFor(User user) {
    if (user == null || user.getId() == null) {
      return null;
    }
    return explanations.get(user.getId());
  }
}
