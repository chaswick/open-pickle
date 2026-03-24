package com.w3llspring.fhpb.web.config;

import com.w3llspring.fhpb.web.util.ReturnToSanitizer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.util.StringUtils;

public class TermsAwareAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

  public TermsAwareAuthenticationFailureHandler() {
    super();
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

    StringBuilder redirect = new StringBuilder();
    redirect.append(request.getContextPath()).append("/login?error=true");

    // Preserve safe returnTo (relative path only)
    String effectiveReturnTo = ReturnToSanitizer.sanitize(returnTo);
    if (StringUtils.hasText(effectiveReturnTo)) {
      redirect
          .append("&returnTo=")
          .append(URLEncoder.encode(effectiveReturnTo, StandardCharsets.UTF_8));
    }

    getRedirectStrategy().sendRedirect(request, response, redirect.toString());
  }
}
