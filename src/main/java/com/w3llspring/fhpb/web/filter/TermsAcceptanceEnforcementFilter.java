package com.w3llspring.fhpb.web.filter;

import com.w3llspring.fhpb.web.config.TermsAcceptancePolicy;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.auth.AuthenticatedUserService;
import com.w3llspring.fhpb.web.util.ReturnToSanitizer;
import com.w3llspring.fhpb.web.util.SecurityRequestMatchers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

public class TermsAcceptanceEnforcementFilter extends OncePerRequestFilter {

  private final AuthenticatedUserService authenticatedUserService;
  private final TermsAcceptancePolicy termsAcceptancePolicy;
  private final List<RequestMatcher> excludedMatchers =
      List.of(
          SecurityRequestMatchers.path("/accept-terms"),
          SecurityRequestMatchers.path("/logout"),
          SecurityRequestMatchers.path("/terms"),
          SecurityRequestMatchers.path("/privacy"),
          SecurityRequestMatchers.path("/css/**"),
          SecurityRequestMatchers.path("/js/**"),
          SecurityRequestMatchers.path("/images/**"),
          SecurityRequestMatchers.path("/webjars/**"),
          SecurityRequestMatchers.path("/sw.js"),
          SecurityRequestMatchers.path("/favicon.ico"),
          SecurityRequestMatchers.path("/favicon*.png"),
          SecurityRequestMatchers.path("/android-chrome-*.png"),
          SecurityRequestMatchers.path("/apple-touch-icon.png"),
          SecurityRequestMatchers.path("/apple-touch-icon-precomposed.png"),
          SecurityRequestMatchers.path("/mstile-*.png"),
          SecurityRequestMatchers.path("/safari-pinned-tab.svg"),
          SecurityRequestMatchers.path("/browserconfig.xml"),
          SecurityRequestMatchers.path("/site.webmanifest"),
          SecurityRequestMatchers.path("/robots.txt"),
          SecurityRequestMatchers.path("/.well-known/security.txt"),
          SecurityRequestMatchers.path("/error"));

  public TermsAcceptanceEnforcementFilter(
      AuthenticatedUserService authenticatedUserService,
      TermsAcceptancePolicy termsAcceptancePolicy) {
    this.authenticatedUserService = authenticatedUserService;
    this.termsAcceptancePolicy = termsAcceptancePolicy;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return excludedMatchers.stream().anyMatch(matcher -> matcher.matches(request));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    User currentUser = authenticatedUserService.currentUser();
    if (currentUser == null || !termsAcceptancePolicy.requiresAcceptance(currentUser)) {
      filterChain.doFilter(request, response);
      return;
    }

    response.sendRedirect(buildAcceptTermsUrl(request));
  }

  private String buildAcceptTermsUrl(HttpServletRequest request) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromPath(request.getContextPath() + "/accept-terms");
    if ("GET".equalsIgnoreCase(request.getMethod())) {
      String returnTo = ReturnToSanitizer.sanitize(ReturnToSanitizer.toAppRelativePath(request));
      if (StringUtils.hasText(returnTo)) {
        builder.queryParam("returnTo", returnTo);
      }
    }
    return builder.build().encode().toUriString();
  }
}
