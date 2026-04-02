package com.w3llspring.fhpb.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fhpb.branding")
public class BrandingProperties {

  private static final String DEFAULT_APP_NAME = "Open-Pickle";
  private static final String DEFAULT_LOGO_PATH = "/images/landing/op_logo.png";
  private static final String DEFAULT_SNAPSHOT_PATH = "/images/landing/openPickleScreenshot.png";
  private static final String DEFAULT_FLAG_PATH = "/images/openpickle_flag.png";
  private static final String DEFAULT_FOOTER_TAGLINE =
      "Open-source pickleball group and match tracker";
  private static final String DEFAULT_LANDING_PAGE_TITLE =
      "Free Global Pickleball Competition";
  private static final String DEFAULT_LANDING_META_DESCRIPTION =
      "Free global pickleball competition with 6-week seasons, rating resets, automated standings, easy match logging, private leagues, round robins, and season trophies.";
  private static final String DEFAULT_LANDING_KICKER = "Community-driven Pickleball";
  private static final String DEFAULT_MANIFEST_THEME_COLOR = "#2d4a3b";
  private static final String DEFAULT_MANIFEST_BACKGROUND_COLOR = "#2d4a3b";
  private static final String DEFAULT_MANIFEST_ICON_192_PATH = "/android-chrome-192x192.png";
  private static final String DEFAULT_MANIFEST_ICON_512_PATH = "/android-chrome-512x512.png";
  private static final String DEFAULT_NOTIFICATION_BADGE_PATH = "/favicon-192x192.png";

  private String appName = DEFAULT_APP_NAME;
  private String logoPath = DEFAULT_LOGO_PATH;
  private String snapshotPath = DEFAULT_SNAPSHOT_PATH;
  private String flagPath = DEFAULT_FLAG_PATH;

  public String getAppName() {
    return normalize(appName, DEFAULT_APP_NAME);
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getLogoPath() {
    return normalizePath(logoPath, DEFAULT_LOGO_PATH);
  }

  public void setLogoPath(String logoPath) {
    this.logoPath = logoPath;
  }

  public String getSnapshotPath() {
    return normalizePath(snapshotPath, DEFAULT_SNAPSHOT_PATH);
  }

  public void setSnapshotPath(String snapshotPath) {
    this.snapshotPath = snapshotPath;
  }

  public String getFlagPath() {
    return normalizePath(flagPath, DEFAULT_FLAG_PATH);
  }

  public void setFlagPath(String flagPath) {
    this.flagPath = flagPath;
  }

  public String getFooterTagline() {
    return DEFAULT_FOOTER_TAGLINE;
  }

  public String getLandingPageTitle() {
    return DEFAULT_LANDING_PAGE_TITLE;
  }

  public String getLandingMetaDescription() {
    return DEFAULT_LANDING_META_DESCRIPTION;
  }

  public String getLandingKicker() {
    return DEFAULT_LANDING_KICKER;
  }

  public String getLandingCopy() {
    return "Compete globally in a seasonal pickleball competition where your rating resets. "
        + getAppName()
        + " automates the standings, keeps match logging easy, and supports private leagues, round robins, trophies to track your finishes, and more.";
  }

  public String getSocialImagePath() {
    return getLogoPath();
  }

  public String getSocialImageAlt() {
    return getAppName() + " logo";
  }

  public String getLandingLogoPath() {
    return getLogoPath();
  }

  public String getLandingLogoAlt() {
    return getAppName() + " logo";
  }

  public String getCheckInFlagPath() {
    return getFlagPath();
  }

  public String getManifestName() {
    return getAppName();
  }

  public String getManifestShortName() {
    return getAppName();
  }

  public String getManifestStartUrl() {
    return "/";
  }

  public String getManifestScope() {
    return "/";
  }

  public String getManifestDisplay() {
    return "standalone";
  }

  public String getManifestThemeColor() {
    return DEFAULT_MANIFEST_THEME_COLOR;
  }

  public String getManifestBackgroundColor() {
    return DEFAULT_MANIFEST_BACKGROUND_COLOR;
  }

  public String getManifestIcon192Path() {
    return DEFAULT_MANIFEST_ICON_192_PATH;
  }

  public String getManifestIcon512Path() {
    return DEFAULT_MANIFEST_ICON_512_PATH;
  }

  public String getNotificationIconPath() {
    return DEFAULT_MANIFEST_ICON_192_PATH;
  }

  public String getNotificationBadgePath() {
    return DEFAULT_NOTIFICATION_BADGE_PATH;
  }

  private String normalize(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private String normalizePath(String value, String fallback) {
    String normalized = normalize(value, fallback);
    return normalized.startsWith("/") ? normalized : "/" + normalized;
  }
}
