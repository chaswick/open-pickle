package com.w3llspring.fhpb.trophygen.core;

public class GeneratedTrophy {
  private final String title;
  private final String summary;
  private final String unlockCondition;
  private final String unlockExpression;
  private final TrophyRarity rarity;
  private final boolean limited;
  private final Integer maxClaims;
  private final String imageUrl;
  private final byte[] imageBytes;
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
      Integer maxClaims,
      String imageUrl,
      byte[] imageBytes,
      String aiProvider,
      String prompt,
      String generationSeed) {
    this.title = title;
    this.summary = summary;
    this.unlockCondition = unlockCondition;
    this.unlockExpression = unlockExpression;
    this.rarity = rarity;
    this.limited = limited;
    this.maxClaims = maxClaims;
    this.imageUrl = imageUrl;
    this.imageBytes = imageBytes;
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

  public Integer getMaxClaims() {
    return maxClaims;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public byte[] getImageBytes() {
    return imageBytes;
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
