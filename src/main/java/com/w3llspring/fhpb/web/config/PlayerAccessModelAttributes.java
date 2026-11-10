package com.w3llspring.fhpb.web.config;

import com.w3llspring.fhpb.web.service.auth.AuthenticatedUserService;
import com.w3llspring.fhpb.web.service.auth.PlayerEmailConnectionService;
import com.w3llspring.fhpb.web.service.auth.PlayerRememberMeServices;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class PlayerAccessModelAttributes {
  private final PlayerRememberMeServices remember;
  private final PlayerEmailConnectionService emails;
  private final AuthenticatedUserService authenticated;

  public PlayerAccessModelAttributes(
      PlayerRememberMeServices remember,
      PlayerEmailConnectionService emails,
      AuthenticatedUserService authenticated) {
    this.remember = remember;
    this.emails = emails;
    this.authenticated = authenticated;
  }

  @ModelAttribute("playerAccessEnabled")
  public boolean enabled() {
    return remember.isAvailable();
  }

  @ModelAttribute("showConnectEmailReminder")
  public boolean showConnectEmailReminder(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return remember.isAvailable()
        && "GET".equals(request.getMethod())
        && "/account-menu".equals(path)
        && emails.shouldRemind(authenticated.currentUser());
  }
}
