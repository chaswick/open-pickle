package com.w3llspring.fhpb.trophygen.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class PromptLibrary {
  private final Map<TrophyRarity, List<String>> palette = new EnumMap<>(TrophyRarity.class);
  private final Map<TrophyRarity, List<String>> lighting = new EnumMap<>(TrophyRarity.class);
  private final Map<TrophyRarity, List<String>> material = new EnumMap<>(TrophyRarity.class);
  private final Map<TrophyRarity, List<String>> border = new EnumMap<>(TrophyRarity.class);
  private final Map<TrophyRarity, List<String>> flourish = new EnumMap<>(TrophyRarity.class);
  private final Map<TrophyRarity, List<String>> depth = new EnumMap<>(TrophyRarity.class);
  private final Map<TrophyRarity, List<String>> motif = new EnumMap<>(TrophyRarity.class);
  private final Map<TrophyRarity, List<String>> unlockCondition = new EnumMap<>(TrophyRarity.class);
  private final Map<TrophyRarity, List<String>> unlockExpression =
      new EnumMap<>(TrophyRarity.class);

  public Map<TrophyRarity, List<String>> getPalette() {
    return palette;
  }

  public Map<TrophyRarity, List<String>> getLighting() {
    return lighting;
  }

  public Map<TrophyRarity, List<String>> getMaterial() {
    return material;
  }

  public Map<TrophyRarity, List<String>> getBorder() {
    return border;
  }

  public Map<TrophyRarity, List<String>> getFlourish() {
    return flourish;
  }

  public Map<TrophyRarity, List<String>> getDepth() {
    return depth;
  }

  public Map<TrophyRarity, List<String>> getMotif() {
    return motif;
  }

  public Map<TrophyRarity, List<String>> getUnlockCondition() {
    return unlockCondition;
  }

  public Map<TrophyRarity, List<String>> getUnlockExpression() {
    return unlockExpression;
  }
}
