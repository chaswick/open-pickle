package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.util.UserPublicName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LadderV2ServiceDisplayNameTest {

  @Test
  void resolveStandingDisplayName_usesStaticFallbackWhenNicknameMissing() {
    LadderV2Service service =
        new LadderV2Service(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new SeasonNameGenerator(),
            null,
            null,
            null,
            null,
            null);
    User user = new User();
    user.setEmail("hidden@test.local");

    String displayName =
        (String) ReflectionTestUtils.invokeMethod(service, "resolveStandingDisplayName", user);

    assertThat(displayName).isEqualTo(UserPublicName.FALLBACK);
  }
}
