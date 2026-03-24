package com.w3llspring.fhpb.web.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InputValidationTest {

  private static final DisplayNameModerationService ALLOW_ALL = value -> Optional.empty();

  @Test
  void requireEmailNormalizesCaseAndWhitespace() {
    String normalized = InputValidation.requireEmail("  Player@Example.COM  ");

    assertThat(normalized).isEqualTo("player@example.com");
  }

  @Test
  void requireDisplayNameRejectsMarkupCharacters() {
    assertThatThrownBy(() -> InputValidation.requireDisplayName("<script>", ALLOW_ALL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Display name contains unsupported characters.");
  }

  @Test
  void parseCourtNamesTrimsDeduplicatesAndLimitsScope() {
    List<String> aliases = InputValidation.parseCourtNames(" North , South, North, East, West ");

    assertThat(aliases).containsExactly("North", "South", "East");
  }

  @Test
  void requireLocationNameRejectsNamesWithoutLettersOrDigits() {
    assertThatThrownBy(() -> InputValidation.requireLocationName("---", ALLOW_ALL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Location name must include letters or numbers.");
  }

  @Test
  void requirePushEndpointRejectsNonHttpsUris() {
    assertThatThrownBy(() -> InputValidation.requirePushEndpoint("http://example.com/push"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Push endpoint is invalid.");
  }

  @Test
  void requirePushKeyRejectsUnexpectedCharacters() {
    assertThatThrownBy(() -> InputValidation.requirePushKey("abc def", "auth"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("auth is invalid.");
  }
}
