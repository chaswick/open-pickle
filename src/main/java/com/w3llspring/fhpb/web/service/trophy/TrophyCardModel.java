package com.w3llspring.fhpb.web.service.trophy;

public class TrophyCardModel {
  private final Long id;
  private final String title;
  private final String summary;
  private final String unlockCondition;
  private final String rarity;
  private final boolean limited;
  private final boolean repeatable;
  private final Integer maxClaims;
  private final String imageUrl;
  private final String seasonName;
  private final String status;
  private final boolean ownedByUser;
  private final long earnedCount;
  private final long ownerCount;
  private final boolean imageAvailable;
  private final String rarityCssClass;

  public TrophyCardModel(
      Long id,
      String title,
      String summary,
      String unlockCondition,
      String rarity,
      boolean limited,
      boolean repeatable,
      Integer maxClaims,
      String imageUrl,
      String seasonName,
      String status,
      boolean ownedByUser,
      long earnedCount,
      long ownerCount,
      boolean imageAvailable,
      String rarityCssClass) {
    this.id = id;
    this.title = title;
    this.summary = summary;
    this.unlockCondition = unlockCondition;
    this.rarity = rarity;
    this.limited = limited;
    this.repeatable = repeatable;
    this.maxClaims = maxClaims;
    this.imageUrl = imageUrl;
    this.seasonName = seasonName;
    this.status = status;
    this.ownedByUser = ownedByUser;
    this.earnedCount = earnedCount;
    this.ownerCount = ownerCount;
    this.imageAvailable = imageAvailable;
    this.rarityCssClass = rarityCssClass;
  }

  public Long getId() {
    return id;
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

  public String getRarity() {
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

  public String getSeasonName() {
    return seasonName;
  }

  public String getStatus() {
    return status;
  }

  public boolean isOwnedByUser() {
    return ownedByUser;
  }

  public long getEarnedCount() {
    return earnedCount;
  }

  public long getOwnerCount() {
    return ownerCount;
  }

  public boolean isImageAvailable() {
    return imageAvailable;
  }

  public String getRarityCssClass() {
    return rarityCssClass;
  }
}
