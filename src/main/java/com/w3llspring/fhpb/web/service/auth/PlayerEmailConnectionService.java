package com.w3llspring.fhpb.web.service.auth;

import com.w3llspring.fhpb.web.db.UserOnboardingMarkerRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserOnboardingMarker;
import com.w3llspring.fhpb.web.service.email.EmailService;
import com.w3llspring.fhpb.web.util.InputValidation;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
public class PlayerEmailConnectionService {
  private static final String REMINDER = "connect_email_reminder";
  private static final Duration WEEK = Duration.ofDays(7);
  private final UserRepository users;
  private final UserOnboardingMarkerRepository markers;
  private final EmailService mail;
  private final PasswordPolicyService passwords;
  private final BCryptPasswordEncoder encoder;

  public PlayerEmailConnectionService(
      UserRepository users,
      UserOnboardingMarkerRepository markers,
      EmailService mail,
      PasswordPolicyService passwords,
      BCryptPasswordEncoder encoder) {
    this.users = users;
    this.markers = markers;
    this.mail = mail;
    this.passwords = passwords;
    this.encoder = encoder;
  }

  @Transactional(readOnly = true)
  public boolean shouldRemind(User user) {
    Instant cutoff = Instant.now().minus(WEEK);
    if (user == null
        || !user.isNameOnlyAccount()
        || user.getRegisteredAt() == null
        || user.getRegisteredAt().isAfter(cutoff)) return false;
    return markers
        .findByUserIdAndMarkerKey(user.getId(), REMINDER)
        .map(marker -> !marker.getCompletedAt().isAfter(cutoff))
        .orElse(true);
  }

  @Transactional
  public void dismiss(Long userId) {
    User user = users.findByIdForUpdate(userId).orElseThrow();
    if (!user.isNameOnlyAccount()) return;
    UserOnboardingMarker marker =
        markers.findByUserIdAndMarkerKey(userId, REMINDER).orElseGet(UserOnboardingMarker::new);
    marker.setUserId(userId);
    marker.setMarkerKey(REMINDER);
    marker.setCompletedAt(Instant.now());
    markers.save(marker);
  }

  @Transactional
  public void request(Long userId, String email, String confirmationUrl) {
    String normalized = InputValidation.requireEmail(email);
    User user = users.findByIdForUpdate(userId).orElseThrow();
    if (!user.isNameOnlyAccount() || user.isAdmin())
      throw new IllegalArgumentException("This account already has an email sign-in.");
    requireUnusedEmail(normalized);
    String token = PlayerAccessService.randomSecret();
    user.setConnectEmailPending(normalized);
    user.setConnectEmailTokenHash(PlayerAccessService.hash(token));
    user.setConnectEmailExpiresAt(Instant.now().plus(Duration.ofMinutes(30)));
    users.saveAndFlush(user);
    try {
      mail.sendHtml(
          normalized,
          "Connect your email to your pickleball account",
          "<p>Connect this email to your pickleball account and choose a password to keep access to your scores and trophies.</p>"
              + "<p><a href=\""
              + HtmlUtils.htmlEscape(confirmationUrl + "?token=" + token)
              + "\">Connect email</a></p><p>This link expires in 30 minutes. If you didn't request it, ignore this email.</p>");
    } catch (Exception e) {
      // Roll back the pending change too. Never put the private link in logs or the response.
      throw new IllegalStateException("We couldn't send the email. Please try again later.");
    }
  }

  @Transactional(readOnly = true)
  public User preview(String token) {
    User user = users.findByConnectEmailTokenHash(tokenHash(token)).orElseThrow(this::invalidLink);
    return validate(user);
  }

  @Transactional
  public User complete(String token, Long signedInUserId, String password, String confirmation) {
    User user =
        validate(
            users
                .findByConnectEmailTokenHashForUpdate(tokenHash(token))
                .orElseThrow(this::invalidLink));
    if (signedInUserId != null && !signedInUserId.equals(user.getId()))
      throw new IllegalArgumentException(
          "You're signed in to a different account. Open this email link in a private window.");
    passwords
        .validate(password)
        .ifPresent(
            message -> {
              throw new IllegalArgumentException(message);
            });
    if (!password.equals(confirmation)) throw new IllegalArgumentException("Passwords must match.");
    requireUnusedEmail(user.getConnectEmailPending());
    user.setEmail(user.getConnectEmailPending());
    user.setPassword(encoder.encode(password));
    user.setNameOnlyAccount(false);
    user.setRecoveryCodeHash(null);
    user.setResetPasswordToken(null);
    user.setResetPasswordTokenExpiresAt(null);
    user.setConnectEmailPending(null);
    user.setConnectEmailTokenHash(null);
    user.setConnectEmailExpiresAt(null);
    return users.saveAndFlush(user);
  }

  private User validate(User user) {
    if (!user.isNameOnlyAccount()
        || user.isAdmin()
        || user.getConnectEmailPending() == null
        || user.getConnectEmailExpiresAt() == null
        || !user.getConnectEmailExpiresAt().isAfter(Instant.now())) throw invalidLink();
    return user;
  }

  private String tokenHash(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidLink();
    return PlayerAccessService.hash(token);
  }

  private void requireUnusedEmail(String email) {
    if (users.findByEmail(email) != null)
      throw new IllegalArgumentException(
          "Use a different email, or sign in to your existing account.");
  }

  private IllegalArgumentException invalidLink() {
    return new IllegalArgumentException(
        "This link has expired or has already been used. Request a new email from Account settings.");
  }
}
