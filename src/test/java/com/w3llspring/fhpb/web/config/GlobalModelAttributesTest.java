package com.w3llspring.fhpb.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.LadderConfigService;
import com.w3llspring.fhpb.web.service.MatchDashboardService;
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
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
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
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
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

  @Test
  void populateCommonAttributes_exposesNavigationSessionsFromLaunchState() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    GlobalModelAttributes attributes =
        new GlobalModelAttributes(
            mock(UserRepository.class), 20, 3, "test-version", "", true, 7, false, "", "", 3, 5, 7);
    LadderConfigService ladderConfigService = mock(LadderConfigService.class);
    ReflectionTestUtils.setField(attributes, "ladderConfigService", ladderConfigService);

    User user = new User();
    user.setId(42L);
    user.setNickName("Tester");
    LadderConfig session = new LadderConfig();
    session.setId(99L);
    session.setTitle("Saturday Open");
    session.setOwnerUserId(42L);
    when(ladderConfigService.resolveSessionLaunchState(42L))
        .thenReturn(
            new LadderConfigService.SessionLaunchState(session, List.of(session), 1, true, false));

    ExtendedModelMap model = new ExtendedModelMap();
    var authentication =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    attributes.populateCommonAttributes(model, authentication, null);

    assertThat(model.get("navigationSessionConfigs")).isEqualTo(List.of(session));
    assertThat(model.get("activeCompetitionSessionId")).isEqualTo(99L);
  }

  @Test
  void populateCommonAttributes_exposesNavigationInboxCount() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    GlobalModelAttributes attributes =
        new GlobalModelAttributes(
            mock(UserRepository.class), 20, 3, "test-version", "", true, 7, false, "", "", 3, 5, 7);
    MatchDashboardService matchDashboardService = mock(MatchDashboardService.class);
    ReflectionTestUtils.setField(attributes, "matchDashboardService", matchDashboardService);

    User user = new User();
    user.setId(42L);
    user.setNickName("Tester");
    when(matchDashboardService.countInboxForUser(any(User.class))).thenReturn(3);

    ExtendedModelMap model = new ExtendedModelMap();
    var authentication =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    attributes.populateCommonAttributes(model, authentication, null);

    assertThat(model.get("navigationInboxCount")).isEqualTo(3);
  }
}
