package com.w3llspring.fhpb.web.config;

import com.w3llspring.fhpb.web.service.auth.ClientIpResolver;
import com.w3llspring.fhpb.web.util.ReturnToSanitizer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.util.StringUtils;

public class TermsAwareAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {
  private static final Logger log =
      LoggerFactory.getLogger(TermsAwareAuthenticationFailureHandler.class);

  private final ClientIpResolver clientIpResolver;

  public TermsAwareAuthenticationFailureHandler() {
    this(new ClientIpResolver(""));
  }

  public TermsAwareAuthenticationFailureHandler(ClientIpResolver clientIpResolver) {
    super();
    this.clientIpResolver = clientIpResolver != null ? clientIpResolver : new ClientIpResolver("");
    // default target if nothing special: /login?error=true
    setDefaultFailureUrl("/login?error=true");
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request,
      HttpServletResponse response,
      org.springframework.security.core.AuthenticationException exception)
      throws IOException, ServletException {

    // Preserve state when redirecting back to the login page so the checkbox is shown
    String returnTo = request.getParameter("returnTo");
    String attemptedUser = request.getParameter("user");

    StringBuilder redirect = new StringBuilder();
    redirect.append(request.getContextPath()).append("/login?error=true");

    // Preserve safe returnTo (relative path only)
    String effectiveReturnTo = ReturnToSanitizer.sanitize(returnTo);
    log.info(
        "Login failed: ip={} principal={} reason={} uri={} returnTo={}",
        clientIpResolver.resolve(request),
        principalHint(attemptedUser),
        exception.getClass().getSimpleName(),
        request.getRequestURI(),
        StringUtils.hasText(effectiveReturnTo) ? effectiveReturnTo : "-");
    if (StringUtils.hasText(effectiveReturnTo)) {
      redirect
          .append("&returnTo=")
          .append(URLEncoder.encode(effectiveReturnTo, StandardCharsets.UTF_8));
    }

    getRedirectStrategy().sendRedirect(request, response, redirect.toString());
  }

  private String principalHint(String attemptedUser) {
    if (!StringUtils.hasText(attemptedUser)) {
      return "-";
    }
    String normalized = attemptedUser.trim().toLowerCase(Locale.US);
    int at = normalized.indexOf('@');
    if (at > 0 && at < normalized.length() - 1) {
      String local = normalized.substring(0, at);
      String domain = normalized.substring(at + 1);
      String prefix = local.length() <= 1 ? "*" : local.substring(0, 1) + "***";
      return prefix + "@" + domain;
    }
    if (normalized.length() <= 1) {
      return "*";
    }
    return normalized.substring(0, 1) + "***";
  }
}
