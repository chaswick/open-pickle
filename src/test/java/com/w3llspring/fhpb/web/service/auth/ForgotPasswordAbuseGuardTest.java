package com.w3llspring.fhpb.web.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ForgotPasswordAbuseGuardTest {

  @Test
  void blocksWhenIpHourlyLimitIsReached() {
    ForgotPasswordAbuseGuard guard = new ForgotPasswordAbuseGuard(1, 10);
    MockHttpServletRequest request = requestFrom("198.51.100.7");

    assertThat(guard.evaluate(request, "one@example.com").allowed()).isTrue();
    assertThat(guard.evaluate(request, "two@example.com").allowed()).isFalse();
  }

  @Test
  void blocksWhenEmailHourlyLimitIsReachedAcrossIps() {
    ForgotPasswordAbuseGuard guard = new ForgotPasswordAbuseGuard(10, 1);

    assertThat(guard.evaluate(requestFrom("198.51.100.7"), "player@example.com").allowed())
        .isTrue();
    assertThat(guard.evaluate(requestFrom("198.51.100.8"), "player@example.com").allowed())
        .isFalse();
  }

  @Test
  void capsTrackedIpKeys() {
    ForgotPasswordAbuseGuard guard = new ForgotPasswordAbuseGuard(10, 10, 2);

    assertThat(guard.evaluate(requestFrom("198.51.100.1"), "one@example.com").allowed()).isTrue();
    assertThat(guard.evaluate(requestFrom("198.51.100.2"), "two@example.com").allowed()).isTrue();
    assertThat(guard.evaluate(requestFrom("198.51.100.3"), "three@example.com").allowed()).isTrue();

    assertThat(guard.trackedIpHourKeyCount()).isLessThanOrEqualTo(2);
  }

  private MockHttpServletRequest requestFrom(String remoteAddr) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddr);
    return request;
  }
}
