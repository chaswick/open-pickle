package com.w3llspring.fhpb.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import com.w3llspring.fhpb.web.util.UserPublicName;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

class GlobalModelAttributesTest {

  @AfterEach
  void clearAuthenticatedUserSupport() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
  }

  @Test
  void populateCommonAttributes_usesStaticFallbackInsteadOfAuthenticationName() {
    GlobalModelAttributes attributes =
        new GlobalModelAttributes(
            mock(UserRepository.class), 20, 3, "test-version", "", true, 7, false, "", "", 3, 5, 7);
    ExtendedModelMap model = new ExtendedModelMap();
    var authentication =
        new UsernamePasswordAuthenticationToken("viewer@test.local", null, List.of());

    attributes.populateCommonAttributes(model, authentication, null);

    assertThat(model.get("userName")).isEqualTo(UserPublicName.FALLBACK);
    assertThat(model.containsAttribute("loggedInUser")).isFalse();
  }

  @Test
  void populateCommonAttributes_buildsPublicMetadataBaseUrlFromRequestWhenConfigBlank() {
    GlobalModelAttributes attributes =
        new GlobalModelAttributes(
            mock(UserRepository.class), 20, 3, "test-version", "", true, 7, false, "", "", 3, 5, 7);
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setScheme("https");
    request.setServerName("open-pickle.example");
    request.setServerPort(443);

    attributes.populateCommonAttributes(model, null, request);

    assertThat(model.get("publicMetadataBaseUrl")).isEqualTo("https://open-pickle.example");
  }
}
