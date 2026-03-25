package com.w3llspring.fhpb.web.config;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionAutoModerationService;
import com.w3llspring.fhpb.web.service.LadderConfigService;
import com.w3llspring.fhpb.web.service.user.UserAccountSettingsService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import com.w3llspring.fhpb.web.util.UserPublicName;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds common model attributes for all controllers so templates can rely on them (e.g. navbar)
 * without every controller needing to populate them.
 */
@ControllerAdvice
public class GlobalModelAttributes {

  private final UserRepository userRepository;
  private final int ladderMaxMembers;
  private final int defaultMaxOwnedLadders;
  private final String assetVersion;
  private final String publicBaseUrl;
  private final BrandingProperties brandingProperties;
  private final OperatorProperties operatorProperties;
  private final boolean checkInEnabled;
  private final long pwaInstallMinAccountAgeDays;
  private final boolean googleAnalyticsEnabled;
  private final String googleAnalyticsMeasurementId;
  private final String googleAdsId;
  private final int competitionAutoModWarningOneThreshold;
  private final int competitionAutoModWarningTwoThreshold;
  private final int competitionAutoModBlockThreshold;
  private LadderConfigService ladderConfigService;
  private CompetitionAutoModerationService competitionAutoModerationService;
  private UserAccountSettingsService userAccountSettingsService;

  @Autowired
  public GlobalModelAttributes(
      UserRepository userRepository,
      LadderConfigService ladderConfigService,
      CompetitionAutoModerationService competitionAutoModerationService,
      UserAccountSettingsService userAccountSettingsService,
      @Value("${fhpb.ladder.max-members:20}") int ladderMaxMembers,
      @Value("${fhpb.ladder.max-per-user:3}") int defaultMaxOwnedLadders,
      @Value("${fhpb.assets.version:20260313a}") String assetVersion,
      @Value("${fhpb.public.base-url:}") String publicBaseUrl,
      BrandingProperties brandingProperties,
      OperatorProperties operatorProperties,
      @Value("${fhpb.features.check-in.enabled:true}") boolean checkInEnabled,
      @Value("${fhpb.pwa.install-helper.min-account-age-days:7}") long pwaInstallMinAccountAgeDays,
      @Value("${fhpb.analytics.enabled:false}") boolean googleAnalyticsEnabled,
      @Value("${fhpb.analytics.google.measurement-id:}") String googleAnalyticsMeasurementId,
      @Value("${fhpb.analytics.google.ads-id:}") String googleAdsId,
      @Value("${fhpb.automod.competition.warning1-expired-confirmations:3}")
          int competitionAutoModWarningOneThreshold,
      @Value("${fhpb.automod.competition.warning2-expired-confirmations:5}")
          int competitionAutoModWarningTwoThreshold,
      @Value("${fhpb.automod.competition.block-expired-confirmations:7}")
          int competitionAutoModBlockThreshold) {
    this.userRepository = userRepository;
    this.ladderConfigService = ladderConfigService;
    this.competitionAutoModerationService = competitionAutoModerationService;
    this.userAccountSettingsService = userAccountSettingsService;
    this.ladderMaxMembers = ladderMaxMembers;
    this.defaultMaxOwnedLadders = defaultMaxOwnedLadders;
    this.assetVersion = assetVersion;
    this.publicBaseUrl = publicBaseUrl;
    this.brandingProperties = brandingProperties;
    this.operatorProperties = operatorProperties;
    this.checkInEnabled = checkInEnabled;
    this.pwaInstallMinAccountAgeDays = pwaInstallMinAccountAgeDays;
    this.googleAnalyticsEnabled = googleAnalyticsEnabled;
    this.googleAnalyticsMeasurementId = googleAnalyticsMeasurementId;
    this.googleAdsId = googleAdsId;
    int normalizedFirst = Math.max(1, competitionAutoModWarningOneThreshold);
    int normalizedSecond = Math.max(normalizedFirst + 1, competitionAutoModWarningTwoThreshold);
    int normalizedBlock = Math.max(normalizedSecond + 1, competitionAutoModBlockThreshold);
    this.competitionAutoModWarningOneThreshold = normalizedFirst;
    this.competitionAutoModWarningTwoThreshold = normalizedSecond;
    this.competitionAutoModBlockThreshold = normalizedBlock;
  }

  public GlobalModelAttributes(
      UserRepository userRepository,
      @Value("${fhpb.ladder.max-members:20}") int ladderMaxMembers,
      @Value("${fhpb.ladder.max-per-user:3}") int defaultMaxOwnedLadders,
      @Value("${fhpb.assets.version:20260313a}") String assetVersion,
      @Value("${fhpb.public.base-url:}") String publicBaseUrl,
      @Value("${fhpb.features.check-in.enabled:true}") boolean checkInEnabled,
      @Value("${fhpb.pwa.install-helper.min-account-age-days:7}") long pwaInstallMinAccountAgeDays,
      @Value("${fhpb.analytics.enabled:false}") boolean googleAnalyticsEnabled,
      @Value("${fhpb.analytics.google.measurement-id:}") String googleAnalyticsMeasurementId,
      @Value("${fhpb.analytics.google.ads-id:}") String googleAdsId,
      @Value("${fhpb.automod.competition.warning1-expired-confirmations:3}")
          int competitionAutoModWarningOneThreshold,
      @Value("${fhpb.automod.competition.warning2-expired-confirmations:5}")
          int competitionAutoModWarningTwoThreshold,
      @Value("${fhpb.automod.competition.block-expired-confirmations:7}")
          int competitionAutoModBlockThreshold) {
    this(
        userRepository,
        null,
        null,
        null,
        ladderMaxMembers,
        defaultMaxOwnedLadders,
        assetVersion,
        publicBaseUrl,
        defaultBrandingProperties(),
        defaultOperatorProperties(),
        checkInEnabled,
        pwaInstallMinAccountAgeDays,
        googleAnalyticsEnabled,
        googleAnalyticsMeasurementId,
        googleAdsId,
        competitionAutoModWarningOneThreshold,
        competitionAutoModWarningTwoThreshold,
        competitionAutoModBlockThreshold);
  }

  private static BrandingProperties defaultBrandingProperties() {
    return new BrandingProperties();
  }

  private static OperatorProperties defaultOperatorProperties() {
    OperatorProperties properties = new OperatorProperties();
    properties.setPublicUrl("");
    return properties;
  }

  @ModelAttribute
  public void populateCommonAttributes(
      Model model, Authentication authentication, HttpServletRequest request) {
    model.addAttribute("ladderMaxMembers", ladderMaxMembers);
    model.addAttribute("defaultMaxOwnedLadders", defaultMaxOwnedLadders);
    model.addAttribute("assetVersion", assetVersion);
    model.addAttribute("branding", brandingProperties);
    model.addAttribute("operator", operatorProperties);
    model.addAttribute("publicMetadataBaseUrl", resolvePublicMetadataBaseUrl(request));
    model.addAttribute("checkInEnabled", checkInEnabled);
    model.addAttribute("googleAnalyticsEnabled", googleAnalyticsEnabled);
    model.addAttribute("googleAnalyticsMeasurementId", googleAnalyticsMeasurementId);
    model.addAttribute("googleAdsId", googleAdsId);
    model.addAttribute("activeCompetitionSessionCount", 0);
    model.addAttribute("showCompetitionSessionChooser", false);
    model.addAttribute(
        "competitionAutoModWarningOneThreshold", resolveCompetitionAutoModWarningOneThreshold());
    model.addAttribute(
        "competitionAutoModWarningTwoThreshold", resolveCompetitionAutoModWarningTwoThreshold());
    model.addAttribute(
        "competitionAutoModBlockThreshold", resolveCompetitionAutoModBlockThreshold());

    if (authentication == null) {
      return;
    }

    User user = AuthenticatedUserSupport.currentUser(authentication);
    if (user != null) {
      updateLastSeenIfHomeRequest(user, request);

      String name = user.getNickName();
      model.addAttribute("userName", name);
      // SECURITY: Create a sanitized user object without email exposure
      model.addAttribute("loggedInUser", createSanitizedUser(user));
      model.addAttribute("currentUserId", user.getId());
      model.addAttribute("pwaInstallEligible", isPwaInstallEligible(user));
      populateReusableSessionAttributes(model, user);
      populateCompetitionAutoModerationAttributes(model, user);
      return;
    }

    // Do not expose the authentication name because it is the login email in this app.
    String authName = authentication.getName();
    if (authName != null && !authName.isBlank() && !"anonymousUser".equalsIgnoreCase(authName)) {
      model.addAttribute("userName", UserPublicName.FALLBACK);
    }
  }

  private void populateReusableSessionAttributes(Model model, User user) {
    if (ladderConfigService == null || user == null || user.getId() == null) {
      return;
    }

    LadderConfigService.SessionLaunchState launchState =
        ladderConfigService.resolveSessionLaunchState(user.getId());
    LadderConfig sessionConfig = launchState.preferredSession();
    model.addAttribute(
        "activeCompetitionSessionId", sessionConfig != null ? sessionConfig.getId() : null);
    model.addAttribute(
        "activeCompetitionSessionTitle", sessionConfig != null ? sessionConfig.getTitle() : null);
    model.addAttribute("activeCompetitionSessionCount", launchState.activeSessionCount());
    model.addAttribute("showCompetitionSessionChooser", launchState.chooserRequired());
  }

  private void populateCompetitionAutoModerationAttributes(Model model, User user) {
    if (competitionAutoModerationService == null || user == null || user.getId() == null) {
      return;
    }

    CompetitionAutoModerationService.AutoModerationStatus status =
        competitionAutoModerationService.activeCompetitionStatus(user);
    if (status != null && status.isVisibleBanner()) {
      model.addAttribute("competitionAutoModStatus", status);
    }
  }

  private int resolveCompetitionAutoModWarningOneThreshold() {
    return competitionAutoModerationService != null
        ? competitionAutoModerationService.getFirstWarningThreshold()
        : competitionAutoModWarningOneThreshold;
  }

  private int resolveCompetitionAutoModWarningTwoThreshold() {
    return competitionAutoModerationService != null
        ? competitionAutoModerationService.getSecondWarningThreshold()
        : competitionAutoModWarningTwoThreshold;
  }

  private int resolveCompetitionAutoModBlockThreshold() {
    return competitionAutoModerationService != null
        ? competitionAutoModerationService.getBlockThreshold()
        : competitionAutoModBlockThreshold;
  }

  /**
   * Creates a sanitized User object safe for frontend use. SECURITY: Removes email and other
   * sensitive fields to prevent exposure.
   */
  private User createSanitizedUser(User user) {
    User sanitized = new User();
    sanitized.setId(user.getId());
    sanitized.setNickName(user.getNickName());
    sanitized.setAdmin(user.isAdmin());
    sanitized.setMeetupsEmailOptIn(user.isMeetupsEmailOptIn());
    sanitized.setAppUiEnabled(true);
    sanitized.setTimeZone(user.getTimeZone());
    // Deliberately NOT setting email, password, tokens, or other sensitive fields
    return sanitized;
  }

  private void updateLastSeenIfHomeRequest(User user, HttpServletRequest request) {
    if (user.getId() == null || request == null) {
      return;
    }

    String requestPath = resolveRequestPath(request);
    if (!(requestPath.isEmpty() || "/".equals(requestPath))) {
      return;
    }

    User managedUser = userRepository.findById(user.getId()).orElse(null);
    if (managedUser != null && userAccountSettingsService != null) {
      Instant seenAt = Instant.now();
      userAccountSettingsService.touchLastSeen(user.getId(), seenAt);
      user.setLastSeenAt(seenAt);
    }
  }

  private boolean isPwaInstallEligible(User user) {
    if (user == null) {
      return false;
    }
    if (pwaInstallMinAccountAgeDays <= 0) {
      return true;
    }

    Instant registeredAt = user.getRegisteredAt();
    if (registeredAt == null) {
      // Older accounts may predate this field; treat them as eligible.
      return true;
    }

    Instant cutoff = Instant.now().minus(pwaInstallMinAccountAgeDays, ChronoUnit.DAYS);
    return !registeredAt.isAfter(cutoff);
  }

  private String resolvePublicMetadataBaseUrl(HttpServletRequest request) {
    String normalizedOperatorUrl = normalizePublicBaseUrl(operatorProperties.getPublicUrl());
    if (!normalizedOperatorUrl.isEmpty()) {
      return normalizedOperatorUrl;
    }
    String normalizedConfigured = normalizePublicBaseUrl(publicBaseUrl);
    if (!normalizedConfigured.isEmpty()) {
      return normalizedConfigured;
    }
    return buildRequestBaseUrl(request);
  }

  private String normalizePublicBaseUrl(String value) {
    if (value == null) {
      return "";
    }

    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    if (trimmed.endsWith("/")) {
      return trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  private String buildRequestBaseUrl(HttpServletRequest request) {
    if (request == null) {
      return "";
    }

    String scheme = request.getScheme();
    String serverName = request.getServerName();
    if (scheme == null || scheme.isBlank() || serverName == null || serverName.isBlank()) {
      return "";
    }

    int port = request.getServerPort();
    boolean defaultPort =
        port <= 0
            || ("http".equalsIgnoreCase(scheme) && port == 80)
            || ("https".equalsIgnoreCase(scheme) && port == 443);
    String portSuffix = defaultPort ? "" : ":" + port;
    return scheme + "://" + serverName + portSuffix;
  }

  private String resolveRequestPath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (uri == null) {
      return "";
    }

    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
      return uri.substring(contextPath.length());
    }

    return uri;
  }
}
