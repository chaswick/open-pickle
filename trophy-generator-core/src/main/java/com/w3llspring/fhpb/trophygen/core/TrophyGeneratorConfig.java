package com.w3llspring.fhpb.trophygen.core;

public class TrophyGeneratorConfig {
  private boolean enabled;
  private String apiKey;
  private String model = "gpt-image-1";
  private String imageSize = "1024x1024";
  private String baseUrl = "https://api.openai.com/v1/images/generations";
  private String promptTemplate =
      "Create a {rarity} pickleball achievement badge for the {season} season. Use {palette} colors and a subtle coastal Florida vibe. Focus on emblem-style iconography with no words.";
  private String quality = "standard";
  private int desiredCount = 1;
  private int requestTimeoutSeconds = 45;
  private boolean debugPrompts;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getImageSize() {
    return imageSize;
  }

  public void setImageSize(String imageSize) {
    this.imageSize = imageSize;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getPromptTemplate() {
    return promptTemplate;
  }

  public void setPromptTemplate(String promptTemplate) {
    this.promptTemplate = promptTemplate;
  }

  public String getQuality() {
    return quality;
  }

  public void setQuality(String quality) {
    this.quality = quality;
  }

  public int getDesiredCount() {
    return desiredCount;
  }

  public void setDesiredCount(int desiredCount) {
    this.desiredCount = desiredCount;
  }

  public int getRequestTimeoutSeconds() {
    return requestTimeoutSeconds;
  }

  public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
    this.requestTimeoutSeconds = requestTimeoutSeconds;
  }

  public boolean isDebugPrompts() {
    return debugPrompts;
  }

  public void setDebugPrompts(boolean debugPrompts) {
    this.debugPrompts = debugPrompts;
  }
}
