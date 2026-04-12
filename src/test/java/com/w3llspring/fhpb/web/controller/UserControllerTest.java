package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.account.UserController;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.GlobalLadderBootstrapService;
import com.w3llspring.fhpb.web.service.auth.PasswordPolicyService;
import com.w3llspring.fhpb.web.service.auth.RegistrationAbuseGuard;
import com.w3llspring.fhpb.web.service.auth.RegistrationFormTokenService;
import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import com.w3llspring.fhpb.web.service.user.UserAccountSettingsService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;

@ExtendWith(OutputCaptureExtension.class)
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

  @Test
  void processRegisterRedirectsHomeAfterSuccessfulSignup() {
    UserRepository userRepository = mock(UserRepository.class);
    RegistrationAbuseGuard registrationAbuseGuard = mock(RegistrationAbuseGuard.class);
    GlobalLadderBootstrapService globalLadderBootstrapService = mock(GlobalLadderBootstrapService.class);
    AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

    RegistrationFormTokenService registrationFormTokenService =
        new RegistrationFormTokenService("test-secret", "", 360);
    PasswordPolicyService passwordPolicyService = new PasswordPolicyService();
    DisplayNameModerationService displayNameModerationService = displayName -> Optional.empty();

    ReflectionTestUtils.setField(controller, "userRepo", userRepository);
    ReflectionTestUtils.setField(controller, "registrationAbuseGuard", registrationAbuseGuard);
    ReflectionTestUtils.setField(
        controller, "registrationFormTokenService", registrationFormTokenService);
    ReflectionTestUtils.setField(controller, "passwordPolicyService", passwordPolicyService);
    ReflectionTestUtils.setField(
        controller, "displayNameModerationService", displayNameModerationService);
    ReflectionTestUtils.setField(
        controller, "globalLadderBootstrapService", globalLadderBootstrapService);
    ReflectionTestUtils.setField(controller, "authenticationManager", authenticationManager);
    ReflectionTestUtils.setField(controller, "defaultMaxOwnedLadders", 10);

    when(registrationAbuseGuard.resolveClientIp(any())).thenReturn("127.0.0.1");
    when(registrationAbuseGuard.evaluate(eq("127.0.0.1"), isNull(), anyLong()))
        .thenReturn(RegistrationAbuseGuard.Decision.allow());
    when(userRepository.findByEmail("new@example.com")).thenReturn(null);
    when(userRepository.findByNickName(any())).thenReturn(null);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(
            invocation -> {
              User saved = invocation.getArgument(0);
              saved.setId(77L);
              return saved;
            });
    when(authenticationManager.authenticate(any()))
        .thenReturn(
            new UsernamePasswordAuthenticationToken(
                "new@example.com", "ValidPass1", List.of()));

    User user = new User();
    user.setEmail("new@example.com");
    user.setPassword("ValidPass1");
    user.setCourtNamesInput("Center Court");
    user.setAcceptTerms(true);

    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(user, "user");
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/register");
    MockHttpServletResponse response = new MockHttpServletResponse();

    String viewName =
        controller.processRegister(
            user,
            bindingResult,
            model,
            "ValidPass1",
            null,
            null,
            registrationFormTokenService.issueToken(),
            request,
            response);

    assertThat(viewName).isEqualTo("redirect:/home");
    assertThat(bindingResult.hasErrors()).isFalse();
  }

  @Test
  void processRegisterLogsRejectedValidationReasons(CapturedOutput output) {
    UserRepository userRepository = mock(UserRepository.class);
    RegistrationAbuseGuard registrationAbuseGuard = mock(RegistrationAbuseGuard.class);
    GlobalLadderBootstrapService globalLadderBootstrapService = mock(GlobalLadderBootstrapService.class);
    AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

    RegistrationFormTokenService registrationFormTokenService =
        new RegistrationFormTokenService("test-secret", "", 360);
    PasswordPolicyService passwordPolicyService = new PasswordPolicyService();
    DisplayNameModerationService displayNameModerationService = displayName -> Optional.empty();

    ReflectionTestUtils.setField(controller, "userRepo", userRepository);
    ReflectionTestUtils.setField(controller, "registrationAbuseGuard", registrationAbuseGuard);
    ReflectionTestUtils.setField(
        controller, "registrationFormTokenService", registrationFormTokenService);
    ReflectionTestUtils.setField(controller, "passwordPolicyService", passwordPolicyService);
    ReflectionTestUtils.setField(
        controller, "displayNameModerationService", displayNameModerationService);
    ReflectionTestUtils.setField(
        controller, "globalLadderBootstrapService", globalLadderBootstrapService);
    ReflectionTestUtils.setField(controller, "authenticationManager", authenticationManager);
    ReflectionTestUtils.setField(controller, "defaultMaxOwnedLadders", 10);

    when(registrationAbuseGuard.resolveClientIp(any())).thenReturn("198.51.100.24");
    when(registrationAbuseGuard.evaluate(eq("198.51.100.24"), isNull(), anyLong()))
        .thenReturn(RegistrationAbuseGuard.Decision.allow());
    when(userRepository.findByEmail("new@example.com")).thenReturn(null);

    User user = new User();
    user.setEmail("new@example.com");
    user.setPassword("ValidPass1");
    user.setCourtNamesInput("Center Court");
    user.setAcceptTerms(false);

    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(user, "user");
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/register");
    MockHttpServletResponse response = new MockHttpServletResponse();

    String viewName =
        controller.processRegister(
            user,
            bindingResult,
            model,
            "ValidPass1",
            null,
            null,
            registrationFormTokenService.issueToken(),
            request,
            response);

    assertThat(viewName).isEqualTo("public/registrationForm");
    assertThat(output.getAll()).contains("Registration rejected:");
    assertThat(output.getAll()).contains("ip=198.51.100.24");
    assertThat(output.getAll()).contains("reasons=acceptTerms");
    assertThat(output.getAll()).contains("emailDomain=example.com");
  }

  @Test
  void processRegisterAcceptsLegacyCompanyHoneypotParameterDuringRollout() {
    UserRepository userRepository = mock(UserRepository.class);
    RegistrationAbuseGuard registrationAbuseGuard = mock(RegistrationAbuseGuard.class);
    GlobalLadderBootstrapService globalLadderBootstrapService = mock(GlobalLadderBootstrapService.class);
    AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

    RegistrationFormTokenService registrationFormTokenService =
        new RegistrationFormTokenService("test-secret", "", 360);
    PasswordPolicyService passwordPolicyService = new PasswordPolicyService();
    DisplayNameModerationService displayNameModerationService = displayName -> Optional.empty();

    ReflectionTestUtils.setField(controller, "userRepo", userRepository);
    ReflectionTestUtils.setField(controller, "registrationAbuseGuard", registrationAbuseGuard);
    ReflectionTestUtils.setField(
        controller, "registrationFormTokenService", registrationFormTokenService);
    ReflectionTestUtils.setField(controller, "passwordPolicyService", passwordPolicyService);
    ReflectionTestUtils.setField(
        controller, "displayNameModerationService", displayNameModerationService);
    ReflectionTestUtils.setField(
        controller, "globalLadderBootstrapService", globalLadderBootstrapService);
    ReflectionTestUtils.setField(controller, "authenticationManager", authenticationManager);
    ReflectionTestUtils.setField(controller, "defaultMaxOwnedLadders", 10);

    when(registrationAbuseGuard.resolveClientIp(any())).thenReturn("127.0.0.1");
    when(registrationAbuseGuard.evaluate(eq("127.0.0.1"), eq("filled"), anyLong()))
        .thenReturn(RegistrationAbuseGuard.Decision.block("honeypot"));

    User user = new User();
    user.setEmail("new@example.com");
    user.setPassword("ValidPass1");
    user.setCourtNamesInput("Center Court");
    user.setAcceptTerms(true);

    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(user, "user");
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/register");
    MockHttpServletResponse response = new MockHttpServletResponse();

    String viewName =
        controller.processRegister(
            user,
            bindingResult,
            model,
            "ValidPass1",
            null,
            "filled",
            registrationFormTokenService.issueToken(),
            request,
            response);

    assertThat(viewName).isEqualTo("redirect:/registration-success");
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
