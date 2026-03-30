package com.w3llspring.fhpb.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReturnToSanitizerTest {

  @Test
  void allowsNormalAppRoutes() {
    assertThat(ReturnToSanitizer.sanitize("/home")).isEqualTo("/home");
    assertThat(ReturnToSanitizer.sanitize("/competition?season=current"))
        .isEqualTo("/competition?season=current");
  }

  @Test
  void rejectsBrowserStaticAndInternalTargets() {
    assertThat(
            ReturnToSanitizer.sanitize(
                "/.well-known/appspecific/com.chrome.devtools.json?continue"))
        .isNull();
    assertThat(ReturnToSanitizer.sanitize("/error?continue")).isNull();
    assertThat(ReturnToSanitizer.sanitize("/css/site.css")).isNull();
    assertThat(ReturnToSanitizer.sanitize("/sw.js")).isNull();
  }

  @Test
  void rejectsProtocolRelativeAndBackslashTargets() {
    assertThat(ReturnToSanitizer.sanitize("//evil.example")).isNull();
    assertThat(ReturnToSanitizer.sanitize("/\\evil.example")).isNull();
    assertThat(ReturnToSanitizer.sanitize("/home#fragment")).isNull();
  }
}
