package com.w3llspring.fhpb.web.config;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.user.UserAccountSettingsService;
import com.w3llspring.fhpb.web.util.ReturnToSanitizer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.util.StringUtils;

public class TermsAcknowledgementAuthenticationSuccessHandler
    extends SavedRequestAwareAuthenticationSuccessHandler {

  private final UserAccountSettingsService userAccountSettingsService;
  private final TermsAcceptancePolicy termsAcceptancePolicy;
  private final RequestCache requestCache;

  public TermsAcknowledgementAuthenticationSuccessHandler(
      UserRepository userRepository, TermsAcceptancePolicy termsAcceptancePolicy) {
    this(
        new UserAccountSettingsService(userRepository),
        termsAcceptancePolicy,
        new HttpSessionRequestCache());
  }

  public TermsAcknowledgementAuthenticationSuccessHandler(
      UserRepository userRepository,
      TermsAcceptancePolicy termsAcceptancePolicy,
      RequestCache requestCache) {
    this(new UserAccountSettingsService(userRepository), termsAcceptancePolicy, requestCache);
  }

  public TermsAcknowledgementAuthenticationSuccessHandler(
      UserAccountSettingsService userAccountSettingsService,
      TermsAcceptancePolicy termsAcceptancePolicy,
      RequestCache requestCache) {
    this.userAccountSettingsService = userAccountSettingsService;
    this.termsAcceptancePolicy = termsAcceptancePolicy;
    this.requestCache = requestCache != null ? requestCache : new HttpSessionRequestCache();
    setRequestCache(this.requestCache);
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws ServletException, IOException {
    String requestReturnTo = sanitizeReturnTo(request.getParameter("returnTo"));
    String savedReturnTo = resolveSafeSavedReturnTo(request, response);

    Object principal = authentication.getPrincipal();
    if (principal instanceof CustomUserDetails) {
      CustomUserDetails cud = (CustomUserDetails) principal;
      User user = cud.getUserObject();
      if (user != null && termsAcceptancePolicy.requiresAcceptance(user)) {
        // Keep the user authenticated and move them into the dedicated
        // acceptance screen instead of acknowledging terms from /login.
        String redirectUrl = buildAcceptTermsUrl(request, requestReturnTo, savedReturnTo);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        return;
      }
    }

    if (StringUtils.hasText(requestReturnTo)) {
      requestCache.removeRequest(request, response);
      clearAuthenticationAttributes(request);
      getRedirectStrategy()
          .sendRedirect(request, response, request.getContextPath() + requestReturnTo);
      return;
    }

    super.onAuthenticationSuccess(request, response, authentication);
  }

  private String buildAcceptTermsUrl(
      HttpServletRequest request, String requestReturnTo, String savedReturnTo) {
    StringBuilder builder = new StringBuilder();
    builder.append(request.getContextPath()).append("/accept-terms");

    String returnTo = StringUtils.hasText(requestReturnTo) ? requestReturnTo : savedReturnTo;

    if (StringUtils.hasText(returnTo)) {
      builder
          .append("?")
          .append("returnTo=")
          .append(URLEncoder.encode(returnTo, StandardCharsets.UTF_8));
    }
    return builder.toString();
  }

  private String sanitizeReturnTo(String returnTo) {
    return ReturnToSanitizer.sanitize(returnTo);
  }

  private String resolveSafeSavedReturnTo(
      HttpServletRequest request, HttpServletResponse response) {
    SavedRequest saved = requestCache.getRequest(request, response);
    String savedReturnTo = ReturnToSanitizer.sanitizeSavedRequest(saved, request.getContextPath());
    if (saved != null && !StringUtils.hasText(savedReturnTo)) {
      requestCache.removeRequest(request, response);
    }
    return savedReturnTo;
  }
}
