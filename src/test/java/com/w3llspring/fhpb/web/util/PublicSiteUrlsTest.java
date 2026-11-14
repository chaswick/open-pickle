package com.w3llspring.fhpb.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicSiteUrlsTest {
  @Test
  void configuredCanonicalOriginWinsOverAnInternalProxyHostOrOperatorFallback() {
    var request = new MockHttpServletRequest("GET", "/?utm_source=invite");
    request.setServerName("internal-proxy");
    assertThat(PublicSiteUrls.baseUrl("https://public.example/", "https://other.example", request))
        .isEqualTo("https://public.example");
  }

  @Test
  void placeholderOperatorUrlDoesNotLeakIntoMetadataAndContextPathsArePreserved() {
    var request = new MockHttpServletRequest("GET", "/club/privacy");
    request.setScheme("https");
    request.setServerName("public.example");
    request.setServerPort(8443);
    request.setContextPath("/club");
    assertThat(PublicSiteUrls.baseUrl("", "https://your-domain.example", request))
        .isEqualTo("https://public.example:8443/club");
    assertThat(PublicSiteUrls.requestPath(request)).isEqualTo("/privacy");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "javascript:alert(1)",
        "https://user:secret@example.com",
        "https://example.com/?inviteCode=PRIVATE",
        "https://example.com/#account"
      })
  void invalidOrSensitiveConfigurationFallsBackToTheRequestOrigin(String configured) {
    var request = new MockHttpServletRequest("GET", "/join/session");
    request.setScheme("https");
    request.setServerName("public.example");
    request.setServerPort(443);
    request.setQueryString("inviteCode=PRIVATE");
    assertThat(PublicSiteUrls.baseUrl(configured, "", request)).isEqualTo("https://public.example");
  }
}
