package com.w3llspring.fhpb.web.service.auth;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserPasswordResetService {

  public enum ResetTokenStatus {
    VALID,
    INVALID,
    EXPIRED
  }

  public enum PasswordResetResult {
    UPDATED,
    INVALID_TOKEN,
    EXPIRED_TOKEN
  }

  private final UserRepository userRepository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final Clock clock;

  @Autowired
  public UserPasswordResetService(
      UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
    this(userRepository, passwordEncoder, Clock.systemUTC());
  }

  UserPasswordResetService(
      UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Transactional
  public User issueResetPasswordToken(
      String normalizedEmail, String proposedToken, Instant expiresAt) {
    if (!StringUtils.hasText(normalizedEmail)
        || !StringUtils.hasText(proposedToken)
        || expiresAt == null) {
      return null;
    }
    User user = userRepository.findByEmailForUpdate(normalizedEmail).orElse(null);
    if (user == null) {
      return null;
    }
    if (hasActiveResetPasswordToken(user)) {
      return user;
    }
    user.setResetPasswordToken(proposedToken);
    user.setResetPasswordTokenExpiresAt(expiresAt);
    return userRepository.save(user);
  }

  @Transactional
  public ResetTokenStatus inspectResetPasswordToken(String token) {
    if (!StringUtils.hasText(token)) {
      return ResetTokenStatus.INVALID;
    }
    User user = userRepository.findByResetPasswordTokenForUpdate(token).orElse(null);
    if (user == null) {
      return ResetTokenStatus.INVALID;
    }
    if (!hasActiveResetPasswordToken(user)) {
      clearResetPasswordToken(user);
      userRepository.save(user);
      return ResetTokenStatus.EXPIRED;
    }
    return ResetTokenStatus.VALID;
  }

  @Transactional
  public PasswordResetResult resetPassword(String token, String rawPassword) {
    if (!StringUtils.hasText(token) || !StringUtils.hasText(rawPassword)) {
      return PasswordResetResult.INVALID_TOKEN;
    }
    User user = userRepository.findByResetPasswordTokenForUpdate(token).orElse(null);
    if (user == null) {
      return PasswordResetResult.INVALID_TOKEN;
    }
    if (!hasActiveResetPasswordToken(user)) {
      clearResetPasswordToken(user);
      userRepository.save(user);
      return PasswordResetResult.EXPIRED_TOKEN;
    }
    user.setPassword(passwordEncoder.encode(rawPassword));
    clearResetPasswordToken(user);
    userRepository.save(user);
    return PasswordResetResult.UPDATED;
  }

  private boolean hasActiveResetPasswordToken(User user) {
    Instant expiresAt = user.getResetPasswordTokenExpiresAt();
    return StringUtils.hasText(user.getResetPasswordToken())
        && expiresAt != null
        && expiresAt.isAfter(Instant.now(clock));
  }

  private void clearResetPasswordToken(User user) {
    user.setResetPasswordToken(null);
    user.setResetPasswordTokenExpiresAt(null);
  }
}
