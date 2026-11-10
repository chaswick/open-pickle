package com.w3llspring.fhpb.web.controller.account;

import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.auth.AuthenticatedUserService;
import com.w3llspring.fhpb.web.service.auth.PlayerAccessAbuseGuard;
import com.w3llspring.fhpb.web.service.auth.PlayerAccessService;
import com.w3llspring.fhpb.web.service.auth.PlayerRememberMeServices;
import com.w3llspring.fhpb.web.service.auth.RegistrationFormTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PlayerAccessController {
  private final PlayerAccessService players;
  private final PlayerRememberMeServices remember;
  private final AuthenticatedUserService authenticated;
  private final RegistrationFormTokenService forms;
  private final PlayerAccessAbuseGuard abuse;

  public PlayerAccessController(
      PlayerAccessService players,
      PlayerRememberMeServices remember,
      AuthenticatedUserService authenticated,
      RegistrationFormTokenService forms,
      PlayerAccessAbuseGuard abuse) {
    this.players = players;
    this.remember = remember;
    this.authenticated = authenticated;
    this.forms = forms;
    this.abuse = abuse;
  }

  @GetMapping("/join/session")
  public String invite(
      @RequestParam(defaultValue = "") String inviteCode,
      @RequestParam(defaultValue = "true") boolean direct,
      Model model,
      HttpServletRequest request,
      HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    if (!abuse.allowPreview(request)) {
      response.setStatus(429);
      model.addAttribute("inviteAvailable", false);
      model.addAttribute("joinError", "Too many attempts right now. Please try again later.");
      return "public/joinSession";
    }
    User current = authenticated.currentUser();
    if (current != null) {
      try {
        return "redirect:" + players.joinExisting(inviteCode, current, direct);
      } catch (IllegalArgumentException | IllegalStateException e) {
        model.addAttribute("joinError", e.getMessage());
      }
    }
    if (current == null) {
      try {
        players.preview(inviteCode);
        // Keep the invite if this player chooses normal sign-in/registration instead.
        new org.springframework.security.web.savedrequest.HttpSessionRequestCache()
            .saveRequest(request, response);
        if (!remember.isAvailable()) return "redirect:/login";
      } catch (IllegalArgumentException e) {
        model.addAttribute("joinError", e.getMessage());
      }
    }
    return prepare(inviteCode, direct, model);
  }

  @PostMapping("/join/session")
  public String join(
      @RequestParam(defaultValue = "") String inviteCode,
      @RequestParam(defaultValue = "true") boolean direct,
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "false") boolean acceptTerms,
      @RequestParam(defaultValue = "") String formToken,
      @RequestParam(defaultValue = "") String website,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    try {
      User current = authenticated.currentUser();
      if (current != null) return "redirect:" + players.joinExisting(inviteCode, current, direct);
      if (!remember.isAvailable())
        throw new IllegalArgumentException("Please sign in or create an account to join.");
      if (!website.isBlank() || forms.resolveIssuedAt(formToken) == null)
        throw new IllegalArgumentException("Please open the invite again and try joining.");
      if (!abuse.allowJoin(request)) {
        response.setStatus(429);
        throw new IllegalArgumentException("Too many attempts right now. Please try again later.");
      }
      var joined = players.createAndJoin(inviteCode, name, acceptTerms, direct);
      remember.signInPlayer(joined.user(), request, response);
      return "redirect:" + joined.destination();
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      model.addAttribute("joinError", "Please try joining again.");
      model.addAttribute("enteredName", name);
      return prepare(inviteCode, direct, model);
    } catch (IllegalArgumentException | IllegalStateException e) {
      model.addAttribute("joinError", e.getMessage());
      model.addAttribute("enteredName", name);
      return prepare(inviteCode, direct, model);
    }
  }

  private String prepare(String inviteCode, boolean direct, Model model) {
    model.addAttribute("inviteCode", inviteCode);
    model.addAttribute("directJoin", direct);
    model.addAttribute("formToken", forms.issueToken());
    model.addAttribute("nameMaxLength", User.MAX_NICKNAME_LENGTH);
    model.addAttribute("inviteAvailable", false);
    model.addAttribute("playerAccessEnabled", remember.isAvailable());
    try {
      model.addAttribute("invitedSession", players.preview(inviteCode));
      model.addAttribute("inviteAvailable", true);
    } catch (IllegalArgumentException e) {
      model.addAttribute("joinError", e.getMessage());
    }
    return "public/joinSession";
  }

  @GetMapping("/recover-player")
  public String recovery(Model model, HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    return "public/recoverPlayer";
  }

  @PostMapping("/recover-player")
  public String recover(
      @RequestParam(defaultValue = "") String recoveryCode,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    if (authenticated.currentUser() != null) return "redirect:/account-menu";
    if (!abuse.allowRecovery(request)) {
      response.setStatus(429);
      model.addAttribute("recoveryError", "Too many attempts. Please try again later.");
      return "public/recoverPlayer";
    }
    try {
      if (!remember.isAvailable())
        throw new IllegalArgumentException("Recovery is unavailable right now.");
      remember.signInPlayer(players.recover(recoveryCode), request, response);
      var cache = new org.springframework.security.web.savedrequest.HttpSessionRequestCache();
      String destination =
          com.w3llspring.fhpb.web.util.ReturnToSanitizer.sanitizeSavedRequest(
              cache.getRequest(request, response), request.getContextPath());
      cache.removeRequest(request, response);
      return "redirect:" + (destination != null ? destination : "/account-menu");
    } catch (IllegalArgumentException e) {
      model.addAttribute("recoveryError", e.getMessage());
      return "public/recoverPlayer";
    }
  }

  @PostMapping("/account/recovery-code")
  public ResponseEntity<byte[]> recoveryCode() {
    User user = authenticated.requireCurrentUser();
    String code = players.createRecoveryCode(user.getId());
    String text =
        "Your private pickleball recovery code\n\n"
            + code
            + "\n\nKeep this file private. Anyone with this code can sign in as you.\n"
            + "To sign back in, choose 'Use a recovery code' on the login page.\n"
            + "Saving a new code replaces the previous code. Your public member ID cannot sign you in.\n";
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pickleball-recovery-code.txt")
        .contentType(MediaType.TEXT_PLAIN)
        .body(text.getBytes(StandardCharsets.UTF_8));
  }
}
