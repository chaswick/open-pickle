package com.w3llspring.fhpb.web.model;

public class BadgeView {

  private final String imageUrl;
  private final String label;
  private final String title;
  private final String unlockCondition;

  public BadgeView(String imageUrl, String label, String title, String unlockCondition) {
    this.imageUrl = imageUrl;
    this.label = label;
    this.title = title;
    this.unlockCondition = unlockCondition;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public String getLabel() {
    return label;
  }

  public String getTitle() {
    return title;
  }

  public String getUnlockCondition() {
    return unlockCondition;
  }
}
