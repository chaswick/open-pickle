package com.w3llspring.fhpb.web.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegistrationFormTokenServiceTest {

  @Test
  void issueAndResolveRoundTrip() {
    RegistrationFormTokenService service = new RegistrationFormTokenService("test-secret", "", 60);

    String token = service.issueToken();

    assertThat(service.resolveIssuedAt(token)).isNotNull();
  }

  @Test
  void rejectsTamperedToken() {
    RegistrationFormTokenService service = new RegistrationFormTokenService("test-secret", "", 60);

    String token = service.issueToken(1_700_000_000_000L);
    String tampered = token.substring(0, token.length() - 1) + "A";

    assertThat(service.resolveIssuedAt(tampered)).isNull();
  }
}
