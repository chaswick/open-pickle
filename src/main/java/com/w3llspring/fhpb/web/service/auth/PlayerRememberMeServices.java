package com.w3llspring.fhpb.web.service.auth;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/** Keeps name-only players signed in using Spring's signed, password-bound remember-me cookies. */
public class PlayerRememberMeServices extends TokenBasedRememberMeServices {

  private final UserRepository users;
  private final boolean available;
  private final int playerLifetimeSeconds;

  public PlayerRememberMeServices(
      String key,
      UserDetailsService details,
      UserRepository users,
      boolean available,
      int normalLifetimeSeconds,
      int playerLifetimeDays,
      boolean secureCookie) {
    super(key, details);
    this.users = users;
    this.available = available;
    this.playerLifetimeSeconds =
        Math.multiplyExact(Math.max(1, Math.min(365, playerLifetimeDays)), 86400);
    setTokenValiditySeconds(normalLifetimeSeconds);
    setUseSecureCookie(secureCookie);
    setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", "Lax"));
  }

  public boolean isAvailable() {
    return available;
  }

  public void signInPlayer(User user, HttpServletRequest request, HttpServletResponse response) {
    if (!available || !user.isNameOnlyAccount())
      throw new IllegalStateException("Name-only sign-in is unavailable.");
    signIn(user, request, response);
  }

  public void signInConnectedPlayer(
      User user, HttpServletRequest request, HttpServletResponse response) {
    if (!available || user.isNameOnlyAccount())
      throw new IllegalStateException("Email sign-in is unavailable.");
    signIn(user, request, response);
  }

  private void signIn(User user, HttpServletRequest request, HttpServletResponse response) {
    var details = new CustomUserDetails(user);
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    if (request.getSession(false) != null) request.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    // Authentication changes also invalidate the anonymous page's CSRF token.
    CookieCsrfTokenRepository.withHttpOnlyFalse().saveToken(null, request, response);
    onLoginSuccess(request, response, authentication);
  }

  @Override
  protected int calculateLoginLifetime(HttpServletRequest request, Authentication authentication) {
    return isNameOnly(authentication)
        ? playerLifetimeSeconds
        : super.calculateLoginLifetime(request, authentication);
  }

  @Override
  protected UserDetails processAutoLoginCookie(
      String[] cookies, HttpServletRequest request, HttpServletResponse response) {
    UserDetails details = super.processAutoLoginCookie(cookies, request, response);
    if (details instanceof CustomUserDetails player && player.getUserObject().isNameOnlyAccount()) {
      // Renew on returning visits, including after the HTTP session expired or the app restarted.
      onLoginSuccess(
          request,
          response,
          UsernamePasswordAuthenticationToken.authenticated(
              details, null, details.getAuthorities()));
    }
    return details;
  }

  @Override
  public void logout(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    if (authentication == null) authentication = autoLogin(request, response);
    if (isNameOnly(authentication)) {
      // Changing the unguessable internal credential revokes previously issued guest cookies.
      User user =
          users.findById(((CustomUserDetails) authentication.getPrincipal()).getId()).orElse(null);
      if (user != null && user.isNameOnlyAccount()) {
        user.setPassword(PlayerAccessService.newInternalCredential());
        users.saveAndFlush(user);
      }
    }
    super.logout(request, response, authentication);
  }

  private boolean isNameOnly(Authentication authentication) {
    return authentication != null
        && authentication.getPrincipal() instanceof CustomUserDetails details
        && details.getUserObject().isNameOnlyAccount();
  }
}
