package com.w3llspring.fhpb.web.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordPolicyServiceTest {

  private PasswordPolicyService service;

  @BeforeEach
  void setUp() {
    service = new PasswordPolicyService();
  }

  @Test
  void acceptsPasswordMeetingRequirements() {
    assertThat(service.validate("SemiStrong9")).isEmpty();
  }

  @Test
  void rejectsTooShortPassword() {
    assertThat(service.validate("Abc123")).contains("Password must be at least 8 characters long.");
  }

  @Test
  void rejectsPasswordMissingUppercase() {
    assertThat(service.validate("lowercase9"))
        .contains("Password must include uppercase, lowercase, and a number.");
  }

  @Test
  void rejectsPasswordWithWhitespace() {
    assertThat(service.validate("New Pass9")).contains("Password cannot contain spaces.");
  }
}
