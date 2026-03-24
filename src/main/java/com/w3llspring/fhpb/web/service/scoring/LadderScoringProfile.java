package com.w3llspring.fhpb.web.service.scoring;

import com.w3llspring.fhpb.web.model.LadderConfig;
import java.util.List;

public final class LadderScoringProfile {
  private final LadderConfig.ScoringAlgorithm key;
  private final String displayName;
  private final double varietyTarget;
  private final double lossFloorRatio;
  private final String coachPrimer;
  private final String optimizationAdvice;
  private final List<String> optimizationTips;

  public LadderScoringProfile(
      LadderConfig.ScoringAlgorithm key,
      String displayName,
      double varietyTarget,
      double lossFloorRatio,
      String coachPrimer) {
    this(key, displayName, varietyTarget, lossFloorRatio, coachPrimer, null);
  }

  public LadderScoringProfile(
      LadderConfig.ScoringAlgorithm key,
      String displayName,
      double varietyTarget,
      double lossFloorRatio,
      String coachPrimer,
      String optimizationAdvice) {
    this(
        key,
        displayName,
        varietyTarget,
        lossFloorRatio,
        coachPrimer,
        optimizationAdvice,
        List.of());
  }

  public LadderScoringProfile(
      LadderConfig.ScoringAlgorithm key,
      String displayName,
      double varietyTarget,
      double lossFloorRatio,
      String coachPrimer,
      String optimizationAdvice,
      List<String> optimizationTips) {
    this.key = key;
    this.displayName = displayName;
    this.varietyTarget = varietyTarget;
    this.lossFloorRatio = lossFloorRatio;
    this.coachPrimer = coachPrimer;
    this.optimizationAdvice = optimizationAdvice;
    this.optimizationTips = optimizationTips == null ? List.of() : List.copyOf(optimizationTips);
  }

  public LadderConfig.ScoringAlgorithm getKey() {
    return key;
  }

  public String getDisplayName() {
    return displayName;
  }

  public double getVarietyTarget() {
    return varietyTarget;
  }

  public double getLossFloorRatio() {
    return lossFloorRatio;
  }

  public String getCoachPrimer() {
    return coachPrimer;
  }

  public String getOptimizationAdvice() {
    return optimizationAdvice;
  }

  public List<String> getOptimizationTips() {
    return optimizationTips;
  }

  public int estimateLossRisk(int baseStep, double guestScale) {
    return (int) Math.round(baseStep * guestScale * lossFloorRatio);
  }
}
