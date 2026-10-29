package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.w3llspring.fhpb.web.controller.account.UserController;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.GlobalLadderBootstrapService;
import com.w3llspring.fhpb.web.service.auth.PasswordPolicyService;
import com.w3llspring.fhpb.web.service.auth.RegistrationAbuseGuard;
import com.w3llspring.fhpb.web.service.auth.RegistrationFormTokenService;
import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import com.w3llspring.fhpb.web.service.user.UserAccountSettingsService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RegistrationBindingSecurityTest {

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void signupCreatesANormalNewAccountEvenWhenInternalFieldsAreSubmitted(boolean tampered)
      throws Exception {
    UserController controller = new UserController();
    UserRepository users = mock(UserRepository.class);
    RegistrationAbuseGuard guard = mock(RegistrationAbuseGuard.class);
    AuthenticationManager authentication = mock(AuthenticationManager.class);
    RegistrationFormTokenService tokens = new RegistrationFormTokenService("test-secret", "", 360);
    DisplayNameModerationService names = displayName -> Optional.empty();
    ReflectionTestUtils.setField(controller, "userRepo", users);
    ReflectionTestUtils.setField(controller, "registrationAbuseGuard", guard);
    ReflectionTestUtils.setField(controller, "registrationFormTokenService", tokens);
    ReflectionTestUtils.setField(controller, "passwordPolicyService", new PasswordPolicyService());
    ReflectionTestUtils.setField(controller, "displayNameModerationService", names);
    ReflectionTestUtils.setField(controller, "globalLadderBootstrapService",
        mock(GlobalLadderBootstrapService.class));
    ReflectionTestUtils.setField(controller, "userAccountSettingsService",
        mock(UserAccountSettingsService.class));
    ReflectionTestUtils.setField(controller, "authenticationManager", authentication);
    ReflectionTestUtils.setField(controller, "defaultMaxOwnedLadders", 10);
    when(guard.resolveClientIp(any())).thenReturn("127.0.0.1");
    when(guard.evaluate(any(), any(), any())).thenReturn(RegistrationAbuseGuard.Decision.allow());
    when(authentication.authenticate(any())).thenReturn(
        new UsernamePasswordAuthenticationToken("signup@example.com", null, List.of()));
    AtomicReference<SubmittedAccount> submitted = new AtomicReference<>();
    when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      submitted.set(new SubmittedAccount(user.getId(), user.isAdmin(), user.getMaxOwnedLadders(),
          user.getPublicCode(), user.getResetPasswordToken(), user.isMeetupsEmailOptIn()));
      assertThat(user.getEmail()).isEqualTo("signup@example.com");
      assertThat(user.getTimeZone()).isEqualTo("America/New_York");
      assertThat(user.getPassword()).isNotEqualTo("ValidPass1");
      user.setId(77L);
      return user;
    });

    var request = post("/register")
        .param("email", "signup@example.com")
        .param("password", "ValidPass1")
        .param("confirm_password", "ValidPass1")
        .param("courtNamesInput", "SignupPlayer")
        .param("acceptTerms", "true")
        .param("timeZone", "America/New_York")
        .param("formToken", tokens.issueToken());
    if (tampered) {
      request.param("id", "1")
          .param("admin", "true")
          .param("isAdmin", "true")
          .param("maxOwnedLadders", "999")
          .param("publicCode", "PB-attacker-code")
          .param("resetPasswordToken", "attacker-reset-token")
          .param("meetupsEmailOptIn", "true");
    }

    MockMvcBuilders.standaloneSetup(controller).build().perform(request)
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/home"));

    assertThat(submitted.get()).isEqualTo(new SubmittedAccount(null, false, 10, null, null, false));
  }

  private record SubmittedAccount(
      Long id, boolean admin, Integer quota, String publicCode, String resetToken, boolean emailOptIn) {}
}
