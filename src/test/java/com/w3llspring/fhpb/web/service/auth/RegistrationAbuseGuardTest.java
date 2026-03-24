package com.w3llspring.fhpb.web.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegistrationAbuseGuardTest {

  @Test
  void blocksWhenFormTokenIsMissingAndDelayIsEnabled() {
    RegistrationAbuseGuard guard = new RegistrationAbuseGuard(5, 20, 2);

    assertThat(guard.evaluate("198.51.100.7", "", null).allowed()).isFalse();
  }

  @Test
  void capsTrackedIpKeys() {
    RegistrationAbuseGuard guard = new RegistrationAbuseGuard(10, 20, 0, 2);

    assertThat(guard.evaluate("198.51.100.1", "", null).allowed()).isTrue();
    assertThat(guard.evaluate("198.51.100.2", "", null).allowed()).isTrue();
    assertThat(guard.evaluate("198.51.100.3", "", null).allowed()).isTrue();

    assertThat(guard.trackedIpHourKeyCount()).isLessThanOrEqualTo(2);
  }
}
