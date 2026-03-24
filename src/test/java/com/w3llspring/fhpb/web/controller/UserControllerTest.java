package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.controller.account.UserController;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.user.UserAccountSettingsService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

class UserControllerTest {

  private RecordingUserAccountSettingsService userAccountSettingsService;

  private UserController controller;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    controller = new UserController();
    userAccountSettingsService = new RecordingUserAccountSettingsService();
    ReflectionTestUtils.setField(
        controller, "userAccountSettingsService", userAccountSettingsService);
  }

  @Test
  void acceptTermsPostDelegatesToSettingsServiceAndUpdatesPrincipal() {
    User sessionUser = new User();
    sessionUser.setId(1L);
    sessionUser.setNickName("tester");

    User persistedUser = new User();
    Instant acknowledgedAt = Instant.parse("2026-03-17T22:00:00Z");
    persistedUser.setAcknowledgedTermsAt(acknowledgedAt);
    userAccountSettingsService.nextUser = persistedUser;

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(sessionUser), null);

    String viewName = controller.acceptTermsPost(authentication, "/stats");

    assertThat(viewName).isEqualTo("redirect:/stats");
    assertThat(sessionUser.getAcknowledgedTermsAt()).isEqualTo(acknowledgedAt);
    assertThat(userAccountSettingsService.acknowledgeTermsUserId).isEqualTo(1L);
    assertThat(userAccountSettingsService.acknowledgedAt).isNotNull();
  }

  @Test
  void viewLoginPageIgnoresTermsPromptFlagsAfterFailedLogin() {
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
    MockHttpServletResponse response = new MockHttpServletResponse();

    String viewName = controller.viewLoginPage("true", "/stats", request, response, model);

    assertThat(viewName).isEqualTo("public/login");
    assertThat(model.get("message")).isEqualTo("Invalid email or password");
    assertThat(model.get("returnTo")).isEqualTo("/stats");
    assertThat(model.containsAttribute("requireTermsAck")).isFalse();
  }

  private static class RecordingUserAccountSettingsService extends UserAccountSettingsService {
    private Long acknowledgeTermsUserId;
    private Instant acknowledgedAt;
    private User nextUser;

    private RecordingUserAccountSettingsService() {
      super(null);
    }

    @Override
    public User acknowledgeTerms(Long userId, Instant acknowledgedAt) {
      this.acknowledgeTermsUserId = userId;
      this.acknowledgedAt = acknowledgedAt;
      return nextUser;
    }

    @Override
    public User enableAppUi(Long userId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public User updateTimeZone(Long userId, String timeZone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public User updateBadgeSlot1(Long userId, Trophy badgeSlot1Trophy) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void touchLastSeen(Long userId, Instant lastSeenAt) {
      throw new UnsupportedOperationException();
    }
  }
}
