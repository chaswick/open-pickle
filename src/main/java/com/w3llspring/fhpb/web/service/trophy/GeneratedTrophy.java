package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.model.TrophyRarity;

public class GeneratedTrophy {

  private final String title;
  private final String summary;
  private final String unlockCondition;
  private final String unlockExpression;
  private final TrophyRarity rarity;
  private final boolean limited;
  private final boolean repeatable;
  private final Integer maxClaims;
  private final String imageUrl;
  private final String aiProvider;
  private final String prompt;
  private final String generationSeed;

  public GeneratedTrophy(
      String title,
      String summary,
      String unlockCondition,
      String unlockExpression,
      TrophyRarity rarity,
      boolean limited,
      boolean repeatable,
      Integer maxClaims,
      String imageUrl,
      String aiProvider,
      String prompt,
      String generationSeed) {
    this.title = title;
    this.summary = summary;
    this.unlockCondition = unlockCondition;
    this.unlockExpression = unlockExpression;
    this.rarity = rarity;
    this.limited = limited;
    this.repeatable = repeatable;
    this.maxClaims = maxClaims;
    this.imageUrl = imageUrl;
    this.aiProvider = aiProvider;
    this.prompt = prompt;
    this.generationSeed = generationSeed;
  }

  public String getTitle() {
    return title;
  }

  public String getSummary() {
    return summary;
  }

  public String getUnlockCondition() {
    return unlockCondition;
  }

  public String getUnlockExpression() {
    return unlockExpression;
  }

  public TrophyRarity getRarity() {
    return rarity;
  }

  public boolean isLimited() {
    return limited;
  }

  public boolean isRepeatable() {
    return repeatable;
  }

  public Integer getMaxClaims() {
    return maxClaims;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public String getAiProvider() {
    return aiProvider;
  }

  public String getPrompt() {
    return prompt;
  }

  public String getGenerationSeed() {
    return generationSeed;
  }
}
