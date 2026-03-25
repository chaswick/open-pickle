package com.w3llspring.fhpb.web.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fhpb.operator")
public class OperatorProperties {

  private static final String DEFAULT_NAME = "the operator of this deployment";
  private static final String DEFAULT_SUPPORT_EMAIL = "support@example.invalid";
  private static final String DEFAULT_PUBLIC_URL = "https://your-domain.example";
  private static final String DEFAULT_LAST_UPDATED = "March 19, 2026";

  private String name = DEFAULT_NAME;
  private String supportEmail = DEFAULT_SUPPORT_EMAIL;
  private String publicUrl = DEFAULT_PUBLIC_URL;
  private String donationUrl = "";

  public String getName() {
    return normalize(name, DEFAULT_NAME);
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSupportEmail() {
    return normalize(supportEmail, DEFAULT_SUPPORT_EMAIL);
  }

  public void setSupportEmail(String supportEmail) {
    this.supportEmail = supportEmail;
  }

  public String getPublicUrl() {
    return publicUrl == null ? "" : publicUrl.trim();
  }

  public void setPublicUrl(String publicUrl) {
    this.publicUrl = publicUrl;
  }

  public String getTermsLastUpdated() {
    return DEFAULT_LAST_UPDATED;
  }

  public String getPrivacyLastUpdated() {
    return DEFAULT_LAST_UPDATED;
  }

  public String getDonationUrl() {
    return donationUrl == null ? "" : donationUrl.trim();
  }

  public void setDonationUrl(String donationUrl) {
    this.donationUrl = donationUrl;
  }

  public String getSupportEmailHref() {
    return "mailto:" + getSupportEmail();
  }

  public boolean isSupportEmailConfigured() {
    String currentSupportEmail = getSupportEmail();
    return !DEFAULT_SUPPORT_EMAIL.equalsIgnoreCase(currentSupportEmail);
  }

  public boolean hasDonationUrl() {
    return !getDonationUrl().isBlank();
  }

  public String getPublicUrlDisplay() {
    String currentPublicUrl = getPublicUrl();
    try {
      URI uri = URI.create(currentPublicUrl);
      String host = uri.getHost();
      if (host != null && !host.isBlank()) {
        return host;
      }
    } catch (Exception ignored) {
      // Fall back to the configured raw value below.
    }
    return currentPublicUrl.replaceFirst("^https?://", "").replaceAll("/$", "");
  }

  private String normalize(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }
}