package com.w3llspring.fhpb.web.service.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PushEndpointValidatorTest {

  @Test
  void allowsConfiguredExactHost() {
    PushEndpointValidator validator = new PushEndpointValidator("fcm.googleapis.com");

    String endpoint = validator.requireAllowedEndpoint("https://fcm.googleapis.com/fcm/send/abc");

    assertThat(endpoint).isEqualTo("https://fcm.googleapis.com/fcm/send/abc");
  }

  @Test
  void allowsConfiguredWildcardHost() {
    PushEndpointValidator validator = new PushEndpointValidator("*.push.apple.com");

    String endpoint = validator.requireAllowedEndpoint("https://web.push.apple.com/QH123");

    assertThat(endpoint).isEqualTo("https://web.push.apple.com/QH123");
  }

  @Test
  void rejectsHostOutsideAllowList() {
    PushEndpointValidator validator = new PushEndpointValidator("fcm.googleapis.com");

    assertThatThrownBy(() -> validator.requireAllowedEndpoint("https://127.0.0.1/internal"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Push endpoint host is not allowed.");
  }

  @Test
  void rejectsUnexpectedPort() {
    PushEndpointValidator validator = new PushEndpointValidator("fcm.googleapis.com");

    assertThatThrownBy(
            () -> validator.requireAllowedEndpoint("https://fcm.googleapis.com:8443/fcm/send/abc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Push endpoint is invalid.");
  }

  @Test
  void rejectsUserInfo() {
    PushEndpointValidator validator = new PushEndpointValidator("fcm.googleapis.com");

    assertThatThrownBy(
            () -> validator.requireAllowedEndpoint("https://user@fcm.googleapis.com/fcm/send/abc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Push endpoint is invalid.");
  }
}
