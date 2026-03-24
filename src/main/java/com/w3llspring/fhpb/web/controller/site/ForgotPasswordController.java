package com.w3llspring.fhpb.web.controller.site;

import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.auth.ForgotPasswordAbuseGuard;
import com.w3llspring.fhpb.web.service.auth.PasswordPolicyService;
import com.w3llspring.fhpb.web.service.auth.UserPasswordResetService;
import com.w3llspring.fhpb.web.service.auth.UserPasswordResetService.PasswordResetResult;
import com.w3llspring.fhpb.web.service.auth.UserPasswordResetService.ResetTokenStatus;
import com.w3llspring.fhpb.web.service.email.EmailService;
import com.w3llspring.fhpb.web.util.InputValidation;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ForgotPasswordController {
  private static final Logger log = LoggerFactory.getLogger(ForgotPasswordController.class);
  private static final String GENERIC_FORGOT_PASSWORD_MESSAGE =
      "If an account exists for that email, a reset link will be sent shortly.";
  private static final Clock CLOCK = Clock.systemUTC();
  private static final SecureRandom RESET_TOKEN_RANDOM = new SecureRandom();
  private static final char[] RESET_TOKEN_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
  private static final int RESET_TOKEN_LENGTH = 30;

  private EmailService emailService;

  private UserPasswordResetService userPasswordResetService;

  private PasswordPolicyService passwordPolicyService;

  private ForgotPasswordAbuseGuard forgotPasswordAbuseGuard;

  @org.springframework.beans.factory.annotation.Value("${fhpb.public.base-url:}")
  private String publicBaseUrl;

  @org.springframework.beans.factory.annotation.Value(
      "${fhpb.security.forgot-password.reset-token-ttl-minutes:60}")
  private long resetPasswordTokenTtlMinutes;

  @Autowired
  public ForgotPasswordController(
      EmailService emailService,
      UserPasswordResetService userPasswordResetService,
      PasswordPolicyService passwordPolicyService,
      ForgotPasswordAbuseGuard forgotPasswordAbuseGuard) {
    this(
        emailService,
        userPasswordResetService,
        passwordPolicyService,
        forgotPasswordAbuseGuard,
        true);
  }

  public ForgotPasswordController() {
    this(null, null, null, null, false);
  }

  private ForgotPasswordController(
      EmailService emailService,
      UserPasswordResetService userPasswordResetService,
      PasswordPolicyService passwordPolicyService,
      ForgotPasswordAbuseGuard forgotPasswordAbuseGuard,
      boolean ignored) {
    this.emailService = emailService;
    this.userPasswordResetService = userPasswordResetService;
    this.passwordPolicyService = passwordPolicyService;
    this.forgotPasswordAbuseGuard = forgotPasswordAbuseGuard;
  }

  @ModelAttribute("passwordRequirements")
  public String passwordRequirements() {
    return passwordPolicyService.getRequirementsDescription();
  }

  @ModelAttribute("passwordPattern")
  public String passwordPattern() {
    return passwordPolicyService.getHtmlPattern();
  }

  @GetMapping("/forgot-password")
  public String showForgotPasswordForm() {
    return "public/forgotPassword";
  }

  @PostMapping("/forgot-password")
  public String processForgotPassword(HttpServletRequest request, Model model) {
    String email = normalizeEmail(request.getParameter("email"));
    if (forgotPasswordAbuseGuard != null) {
      ForgotPasswordAbuseGuard.Decision decision =
          forgotPasswordAbuseGuard.evaluate(request, email);
      if (!decision.allowed()) {
        log.warn(
            "Throttled forgot-password request from ip={} reason={}",
            forgotPasswordAbuseGuard.resolveClientIp(request),
            decision.reason());
        addGenericForgotPasswordMessage(model);
        return "public/forgotPassword";
      }
    }

    try {
      String resetPasswordLinkBase = getPublicBaseUrl(request);
      if (StringUtils.hasText(resetPasswordLinkBase)) {
        User user =
            userPasswordResetService.issueResetPasswordToken(
                email,
                generateResetPasswordToken(),
                Instant.now(CLOCK).plusSeconds(resolveResetPasswordTokenTtlSeconds()));
        if (user != null) {
          String resetPasswordLink =
              resetPasswordLinkBase + "/reset-password?token=" + user.getResetPasswordToken();
          sendEmail(user.getEmail(), resetPasswordLink);
        }
      } else {
        log.error(
            "Cannot send forgot-password email because no safe public base URL is configured.");
      }
    } catch (MailAuthenticationException ex) {
      log.error("SMTP authentication failed while sending a password reset email.", ex);
    } catch (MailException | UnsupportedEncodingException | MessagingException ex) {
      log.warn("Failed to send password reset email.", ex);
    }

    addGenericForgotPasswordMessage(model);
    return "public/forgotPassword";
  }

  @GetMapping("/reset-password")
  public String showResetPasswordForm(@Param(value = "token") String token, Model model) {
    model.addAttribute("token", token);

    ResetTokenStatus tokenStatus = userPasswordResetService.inspectResetPasswordToken(token);
    if (tokenStatus != ResetTokenStatus.VALID) {
      model.addAttribute("error", "Invalid token.");
      return "public/resetPassword";
    }

    return "public/resetPassword";
  }

  @PostMapping("/reset-password")
  public String processResetPassword(HttpServletRequest request, Model model) {
    String token = request.getParameter("token");
    String password = request.getParameter("password");
    String confirmPassword = request.getParameter("confirm_password");
    model.addAttribute("title", "Reset your password");

    if (userPasswordResetService.inspectResetPasswordToken(token) != ResetTokenStatus.VALID) {
      model.addAttribute("error", "Invalid token.");
      return "public/resetPassword";
    }

    model.addAttribute("token", token);

    var validationError = passwordPolicyService.validate(password);
    if (validationError.isPresent()) {
      model.addAttribute("error", validationError.get());
      return "public/resetPassword";
    }

    if (confirmPassword == null || !java.util.Objects.equals(confirmPassword, password)) {
      model.addAttribute("error", "Passwords must match.");
      return "public/resetPassword";
    }

    PasswordResetResult result = userPasswordResetService.resetPassword(token, password);
    if (result != PasswordResetResult.UPDATED) {
      model.addAttribute("error", "Invalid token.");
      return "public/resetPassword";
    }
    model.addAttribute("message", "You have successfully changed your password.");
    return "public/login";
  }

  public void sendEmail(String recipientEmail, String link)
      throws MessagingException, UnsupportedEncodingException {
    String subject = "Here's the link to reset your password";

    String content =
        "<p>Hello,</p>"
            + "<p>You have requested to reset your password.</p>"
            + "<p>Click the link below to change your password:</p>"
            + "<p><a href=\""
            + link
            + "\">Change my password</a></p>"
            + "<br>"
            + "<p>Ignore this email if you do remember your password, "
            + "or you have not made the request.</p>";

    emailService.sendHtml(recipientEmail, subject, content);
  }

  public String getPublicBaseUrl(HttpServletRequest request) {
    String normalizedBase = normalizeConfiguredPublicBaseUrl(publicBaseUrl);
    if (StringUtils.hasText(normalizedBase)) {
      return normalizedBase;
    }

    if (!isLocalRequest(request)) {
      return null;
    }

    String scheme = request.getScheme();
    String serverName = request.getServerName();
    int serverPort = request.getServerPort();
    String contextPath = request.getContextPath();

    StringBuilder base = new StringBuilder();
    base.append(scheme).append("://").append(serverName);
    if (shouldIncludePort(scheme, serverPort)) {
      base.append(':').append(serverPort);
    }
    if (StringUtils.hasText(contextPath)) {
      if (!contextPath.startsWith("/")) {
        base.append('/');
      }
      base.append(contextPath);
    }
    return base.toString();
  }

  private void addGenericForgotPasswordMessage(Model model) {
    model.addAttribute("message", GENERIC_FORGOT_PASSWORD_MESSAGE);
    model.asMap().remove("error");
  }

  private String normalizeEmail(String email) {
    try {
      return InputValidation.normalizeEmailOrNull(email);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private String normalizeConfiguredPublicBaseUrl(String configured) {
    if (!StringUtils.hasText(configured)) {
      return null;
    }
    try {
      URI uri = URI.create(configured.trim());
      String scheme = uri.getScheme();
      if (!StringUtils.hasText(scheme)
          || !StringUtils.hasText(uri.getHost())
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
        return null;
      }
      String normalized = configured.trim();
      return normalized.endsWith("/")
          ? normalized.substring(0, normalized.length() - 1)
          : normalized;
    } catch (Exception ex) {
      return null;
    }
  }

  private boolean isLocalRequest(HttpServletRequest request) {
    if (request == null) {
      return false;
    }
    String serverName = request.getServerName();
    if (!StringUtils.hasText(serverName)) {
      return false;
    }
    return "localhost".equalsIgnoreCase(serverName)
        || "127.0.0.1".equals(serverName)
        || "::1".equals(serverName)
        || "0:0:0:0:0:0:0:1".equals(serverName);
  }

  private boolean shouldIncludePort(String scheme, int serverPort) {
    if (serverPort <= 0) {
      return false;
    }
    if ("http".equalsIgnoreCase(scheme) && serverPort == 80) {
      return false;
    }
    if ("https".equalsIgnoreCase(scheme) && serverPort == 443) {
      return false;
    }
    return true;
  }

  private long resolveResetPasswordTokenTtlSeconds() {
    long minutes = Math.max(1L, resetPasswordTokenTtlMinutes);
    return minutes * 60L;
  }

  private String generateResetPasswordToken() {
    char[] token = new char[RESET_TOKEN_LENGTH];
    for (int i = 0; i < token.length; i++) {
      token[i] = RESET_TOKEN_ALPHABET[RESET_TOKEN_RANDOM.nextInt(RESET_TOKEN_ALPHABET.length)];
    }
    return new String(token);
  }
}
