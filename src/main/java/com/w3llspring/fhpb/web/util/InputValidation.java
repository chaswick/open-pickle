package com.w3llspring.fhpb.web.util;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.PlayLocationAlias;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserCourtName;
import com.w3llspring.fhpb.web.model.UserPushSubscription;
import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class InputValidation {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
  private static final Pattern WEB_PUSH_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-+/=]+$");

  private InputValidation() {}

  public static String requireEmail(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
    if (!StringUtils.hasText(normalized)) {
      throw new IllegalArgumentException("E-mail address cannot be blank.");
    }
    if (normalized.length() > User.MAX_EMAIL_LENGTH) {
      throw new IllegalArgumentException("E-mail address is too long.");
    }
    if (containsUnsafeCharacters(normalized)) {
      throw new IllegalArgumentException("Enter a valid e-mail address.");
    }
    if (!EMAIL_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Enter a valid e-mail address.");
    }
    return normalized;
  }

  public static String normalizeEmailOrNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return requireEmail(value);
  }

  public static String requireDisplayName(
      String value, DisplayNameModerationService moderationService) {
    String trimmed = requirePlainText(value, "Display name", User.MAX_NICKNAME_LENGTH);
    if (trimmed.toUpperCase(Locale.US).contains("GUEST")) {
      throw new IllegalArgumentException("Display name can't be 'Guest'. Try another.");
    }
    if (moderationService != null) {
      moderationService
          .explainViolation(trimmed)
          .ifPresent(
              reason -> {
                throw new IllegalArgumentException(reason);
              });
    }
    return trimmed;
  }

  public static String requireCourtName(String value) {
    return requirePlainText(value, "Court name", UserCourtName.MAX_ALIAS_LENGTH);
  }

  public static List<String> parseCourtNames(String value) {
    if (!StringUtils.hasText(value)) {
      return List.of();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(StringUtils::hasText)
        .map(InputValidation::requireCourtName)
        .distinct()
        .limit(UserCourtName.MAX_ALIASES_PER_SCOPE)
        .toList();
  }

  public static String requireGroupTitle(String value) {
    return requirePlainText(value, "Group name", LadderConfig.MAX_TITLE_LENGTH);
  }

  public static String normalizeOptionalGroupTitle(String value) {
    return normalizeOptionalPlainText(value, "Group name", LadderConfig.MAX_TITLE_LENGTH);
  }

  public static String normalizeOptionalSeasonName(String value) {
    return normalizeOptionalPlainText(value, "Season name", LadderSeason.MAX_NAME_LENGTH);
  }

  public static String requireLocationName(
      String value, DisplayNameModerationService moderationService) {
    String trimmed = requirePlainText(value, "Location name", PlayLocationAlias.MAX_NAME_LENGTH);
    if (!trimmed.codePoints().anyMatch(Character::isLetterOrDigit)) {
      throw new IllegalArgumentException("Location name must include letters or numbers.");
    }
    if (moderationService != null) {
      moderationService
          .explainViolation(trimmed)
          .ifPresent(
              reason -> {
                throw new IllegalArgumentException(reason);
              });
    }
    return trimmed;
  }

  public static String requirePushEndpoint(String value) {
    String trimmed =
        requireSingleLine(value, "Push endpoint", UserPushSubscription.MAX_ENDPOINT_LENGTH);
    URI endpoint;
    try {
      endpoint = URI.create(trimmed);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Push endpoint is invalid.");
    }
    if (!"https".equalsIgnoreCase(endpoint.getScheme())
        || !StringUtils.hasText(endpoint.getHost())) {
      throw new IllegalArgumentException("Push endpoint is invalid.");
    }
    return trimmed;
  }

  public static String requirePushKey(String value, String label) {
    String trimmed = requireSingleLine(value, label, UserPushSubscription.MAX_KEY_LENGTH);
    if (!WEB_PUSH_KEY_PATTERN.matcher(trimmed).matches()) {
      throw new IllegalArgumentException(label + " is invalid.");
    }
    return trimmed;
  }

  public static String normalizeOptionalUserAgent(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return requireSingleLine(value, "User agent", UserPushSubscription.MAX_USER_AGENT_LENGTH);
  }

  private static String normalizeOptionalPlainText(String value, String fieldLabel, int maxLength) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return requirePlainText(value, fieldLabel, maxLength);
  }

  private static String requirePlainText(String value, String fieldLabel, int maxLength) {
    String trimmed = value == null ? "" : value.trim();
    if (!StringUtils.hasText(trimmed)) {
      throw new IllegalArgumentException(fieldLabel + " cannot be blank.");
    }
    if (trimmed.length() > maxLength) {
      throw new IllegalArgumentException(
          fieldLabel + " must be " + maxLength + " characters or fewer.");
    }
    if (containsUnsafeCharacters(trimmed)) {
      throw new IllegalArgumentException(fieldLabel + " contains unsupported characters.");
    }
    return trimmed;
  }

  private static String requireSingleLine(String value, String fieldLabel, int maxLength) {
    String trimmed = value == null ? "" : value.trim();
    if (!StringUtils.hasText(trimmed)) {
      throw new IllegalArgumentException(fieldLabel + " cannot be blank.");
    }
    if (trimmed.length() > maxLength) {
      throw new IllegalArgumentException(fieldLabel + " is too long.");
    }
    if (containsUnsafeCharacters(trimmed)) {
      throw new IllegalArgumentException(fieldLabel + " is invalid.");
    }
    return trimmed;
  }

  private static boolean containsUnsafeCharacters(String value) {
    for (int offset = 0; offset < value.length(); ) {
      int codePoint = value.codePointAt(offset);
      if (codePoint == '<' || codePoint == '>') {
        return true;
      }
      int type = Character.getType(codePoint);
      if (Character.isISOControl(codePoint)
          || type == Character.FORMAT
          || type == Character.PRIVATE_USE
          || type == Character.SURROGATE
          || type == Character.UNASSIGNED) {
        return true;
      }
      offset += Character.charCount(codePoint);
    }
    return false;
  }
}
