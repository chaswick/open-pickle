package com.w3llspring.fhpb.web.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

  @Test
  void ignoresForwardedHeadersFromUntrustedRemote() {
    ClientIpResolver resolver = new ClientIpResolver("");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.10");
    request.addHeader("X-Forwarded-For", "203.0.113.7");

    assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
  }

  @Test
  void usesForwardedHeadersFromTrustedProxy() {
    ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.1.2.3");
    request.addHeader("X-Forwarded-For", "203.0.113.7, 10.1.2.3");

    assertThat(resolver.resolve(request)).isEqualTo("203.0.113.7");
  }
}
