package com.w3llspring.fhpb.web.service.auth;

import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Centralizes password strength rules so we can reuse them across controllers and keep UI hints
 * consistent with the server-side validation.
 */
@Component
public class PasswordPolicyService {

  public static final int MIN_LENGTH = 8;
  public static final int MAX_LENGTH = 64;

  private static final Pattern LOWER = Pattern.compile("[a-z]");
  private static final Pattern UPPER = Pattern.compile("[A-Z]");
  private static final Pattern DIGIT = Pattern.compile("\\d");
  private static final Pattern WHITESPACE = Pattern.compile("\\s");

  public Optional<String> validate(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      return Optional.of("Password is required.");
    }
    if (rawPassword.length() < MIN_LENGTH) {
      return Optional.of(
          String.format("Password must be at least %d characters long.", MIN_LENGTH));
    }
    if (rawPassword.length() > MAX_LENGTH) {
      return Optional.of(String.format("Password must be %d characters or fewer.", MAX_LENGTH));
    }
    if (WHITESPACE.matcher(rawPassword).find()) {
      return Optional.of("Password cannot contain spaces.");
    }
    if (!LOWER.matcher(rawPassword).find()
        || !UPPER.matcher(rawPassword).find()
        || !DIGIT.matcher(rawPassword).find()) {
      return Optional.of("Password must include uppercase, lowercase, and a number.");
    }
    return Optional.empty();
  }

  public String getRequirementsDescription() {
    return "Use 8-64 characters with upper & lower case letters and a number; no spaces.";
  }

  public String getHtmlPattern() {
    return "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)\\S{" + MIN_LENGTH + "," + MAX_LENGTH + "}";
  }
}
