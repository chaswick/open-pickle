package com.w3llspring.fhpb.web.controller.account;

import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.auth.AuthenticatedUserService;
import com.w3llspring.fhpb.web.service.auth.PasswordPolicyService;
import com.w3llspring.fhpb.web.service.auth.PlayerAccessAbuseGuard;
import com.w3llspring.fhpb.web.service.auth.PlayerEmailConnectionService;
import com.w3llspring.fhpb.web.service.auth.PlayerRememberMeServices;
import com.w3llspring.fhpb.web.util.InputValidation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PlayerEmailConnectionController {
  private static final String TOKEN_ATTRIBUTE =
      PlayerEmailConnectionController.class.getName() + ".token";
  private final PlayerEmailConnectionService emails;
  private final AuthenticatedUserService authenticated;
  private final PlayerRememberMeServices remember;
  private final PlayerAccessAbuseGuard abuse;
  private final PasswordPolicyService passwords;
  private final String publicBaseUrl;

  public PlayerEmailConnectionController(
      PlayerEmailConnectionService emails,
      AuthenticatedUserService authenticated,
      PlayerRememberMeServices remember,
      PlayerAccessAbuseGuard abuse,
      PasswordPolicyService passwords,
      @Value("${fhpb.public.base-url:}") String publicBaseUrl) {
    this.emails = emails;
    this.authenticated = authenticated;
    this.remember = remember;
    this.abuse = abuse;
    this.passwords = passwords;
    this.publicBaseUrl = publicBaseUrl;
  }

  @GetMapping("/account/connect-email")
  public String form(Model model) {
    User user = authenticated.currentUser();
    if (user == null) return "redirect:/login";
    if (!user.isNameOnlyAccount() || !remember.isAvailable()) return "redirect:/account";
    return "auth/connect-email";
  }

  @PostMapping("/account/connect-email")
  public String request(
      @RequestParam(defaultValue = "") String email,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    User user = authenticated.currentUser();
    if (user == null) return "redirect:/login";
    if (!user.isNameOnlyAccount() || !remember.isAvailable()) return "redirect:/account";
    model.addAttribute("enteredEmail", email);
    try {
      String normalized = InputValidation.requireEmail(email);
      if (!abuse.allowEmailConnection(request, user.getId(), normalized)) {
        response.setStatus(429);
        throw new IllegalArgumentException("Too many requests. Please try again later.");
      }
      emails.request(user.getId(), normalized, confirmationUrl(request));
      model.addAttribute("emailSent", true);
    } catch (IllegalArgumentException | IllegalStateException e) {
      model.addAttribute("connectError", e.getMessage());
    }
    return "auth/connect-email";
  }

  @PostMapping("/account/connect-email/dismiss")
  public String dismiss() {
    User user = authenticated.currentUser();
    if (user != null) emails.dismiss(user.getId());
    return "redirect:/home";
  }

  @GetMapping("/connect-email")
  public String confirmation(
      @RequestParam(required = false) String token,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    privateResponse(response);
    if (token != null) {
      // Strip the private link from the URL before rendering analytics/translation/assets.
      request.getSession().setAttribute(TOKEN_ATTRIBUTE, token.length() <= 64 ? token : "");
      return "redirect:/connect-email";
    }
    return prepareConfirmation(request, model);
  }

  @PostMapping("/connect-email")
  public String complete(
      @RequestParam(defaultValue = "") String password,
      @RequestParam(name = "confirm_password", defaultValue = "") String confirmation,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model,
      RedirectAttributes flash) {
    privateResponse(response);
    try {
      if (!remember.isAvailable())
        throw new IllegalArgumentException("Email sign-in is unavailable right now.");
      if (!abuse.allowRecovery(request)) {
        response.setStatus(429);
        throw new IllegalArgumentException("Too many attempts. Please try again later.");
      }
      User current = authenticated.currentUser();
      User connected =
          emails.complete(
              token(request), current != null ? current.getId() : null, password, confirmation);
      request.getSession().removeAttribute(TOKEN_ATTRIBUTE);
      remember.signInConnectedPlayer(connected, request, response);
      flash.addFlashAttribute(
          "message", "Email connected. You can now sign in with your email and password.");
      return "redirect:/home";
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      model.addAttribute(
          "connectError", "Use a different email, or sign in to your existing account.");
    } catch (IllegalArgumentException e) {
      model.addAttribute("connectError", e.getMessage());
    }
    return prepareConfirmation(request, model);
  }

  private String prepareConfirmation(HttpServletRequest request, Model model) {
    model.addAttribute("connectionAvailable", false);
    model.addAttribute("passwordRequirements", passwords.getRequirementsDescription());
    model.addAttribute("passwordPattern", passwords.getHtmlPattern());
    try {
      if (!remember.isAvailable())
        throw new IllegalArgumentException("Email sign-in is unavailable right now.");
      User target = emails.preview(token(request));
      User current = authenticated.currentUser();
      if (current != null && !current.getId().equals(target.getId()))
        throw new IllegalArgumentException(
            "You're signed in to a different account. Open this email link in a private window.");
      model.addAttribute("connectionAvailable", true);
      model.addAttribute("connectingEmail", target.getConnectEmailPending());
    } catch (IllegalArgumentException e) {
      model.addAttribute("connectError", e.getMessage());
    }
    return "public/connect-email";
  }

  private String token(HttpServletRequest request) {
    var session = request.getSession(false);
    return session != null ? (String) session.getAttribute(TOKEN_ATTRIBUTE) : null;
  }

  private void privateResponse(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
  }

  private String confirmationUrl(HttpServletRequest request) {
    String base = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
    try {
      URI uri = URI.create(base);
      if (uri.getHost() != null
          && uri.getUserInfo() == null
          && uri.getQuery() == null
          && uri.getFragment() == null
          && ("https".equalsIgnoreCase(uri.getScheme())
              || "http".equalsIgnoreCase(uri.getScheme()))) return base + "/connect-email";
    } catch (IllegalArgumentException ignored) {
    }
    if (java.util.Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1")
        .contains(request.getServerName())) {
      return "http://localhost:"
          + request.getServerPort()
          + request.getContextPath()
          + "/connect-email";
    }
    throw new IllegalStateException(
        "Email connection is unavailable right now. Please try again later.");
  }
}
