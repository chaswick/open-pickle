package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.controller.site.ForgotPasswordController;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.auth.ForgotPasswordAbuseGuard;
import com.w3llspring.fhpb.web.service.auth.PasswordPolicyService;
import com.w3llspring.fhpb.web.service.auth.UserPasswordResetService;
import com.w3llspring.fhpb.web.service.auth.UserPasswordResetService.PasswordResetResult;
import com.w3llspring.fhpb.web.service.auth.UserPasswordResetService.ResetTokenStatus;
import com.w3llspring.fhpb.web.service.email.EmailService;
import jakarta.mail.MessagingException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

class ForgotPasswordControllerTest {

  private static final String GENERIC_MESSAGE =
      "If an account exists for that email, a reset link will be sent shortly.";

  private ForgotPasswordController controller;
  private StubEmailService emailService;
  private RecordingUserPasswordResetService userPasswordResetService;

  @BeforeEach
  void setUp() {
    controller = new ForgotPasswordController();
    emailService = new StubEmailService();
    userPasswordResetService = new RecordingUserPasswordResetService();
    ReflectionTestUtils.setField(controller, "emailService", emailService);
    ReflectionTestUtils.setField(controller, "userPasswordResetService", userPasswordResetService);
    ReflectionTestUtils.setField(controller, "passwordPolicyService", new PasswordPolicyService());
    ReflectionTestUtils.setField(
        controller, "forgotPasswordAbuseGuard", new ForgotPasswordAbuseGuard(10, 3));
    ReflectionTestUtils.setField(controller, "publicBaseUrl", "https://app.example.com");
    ReflectionTestUtils.setField(controller, "resetPasswordTokenTtlMinutes", 60L);
  }

  @Test
  void processForgotPasswordShowsGenericMessageWhenMailAuthFails() throws Exception {
    User user = new User();
    user.setEmail("player@example.com");
    userPasswordResetService.issuedUser = user;
    emailService.runtimeFailure = new MailAuthenticationException("Authentication failed");

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/forgot-password");
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(8090);
    request.setRequestURI("/forgot-password");
    request.setServletPath("/forgot-password");
    request.addParameter("email", "player@example.com");
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.processForgotPassword(request, model);

    assertThat(view).isEqualTo("public/forgotPassword");
    assertThat(model.get("message")).isEqualTo(GENERIC_MESSAGE);
    assertThat(model.get("error")).isNull();
  }

  @Test
  void processForgotPasswordUsesConfiguredPublicBaseUrlAndSetsExpiry() {
    User user = new User();
    user.setEmail("player@example.com");
    userPasswordResetService.issuedUser = user;

    MockHttpServletRequest request =
        buildForgotPasswordRequest("player@example.com", "203.0.113.8");
    request.setServerName("malicious.example");
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.processForgotPassword(request, model);

    assertThat(view).isEqualTo("public/forgotPassword");
    assertThat(emailService.called).isTrue();
    assertThat(emailService.lastHtml).contains("https://app.example.com/reset-password?token=");
    assertThat(user.getResetPasswordToken()).isNotBlank();
    assertThat(user.getResetPasswordTokenExpiresAt()).isAfter(Instant.now().minusSeconds(5));
    assertThat(userPasswordResetService.issuedEmail).isEqualTo("player@example.com");
  }

  @Test
  void processForgotPasswordUsesGenericMessageForMissingUser() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/forgot-password");
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(8090);
    request.setRequestURI("/forgot-password");
    request.setServletPath("/forgot-password");
    request.addParameter("email", "missing@example.com");
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.processForgotPassword(request, model);

    assertThat(view).isEqualTo("public/forgotPassword");
    assertThat(model.get("message")).isEqualTo(GENERIC_MESSAGE);
    assertThat(model.get("error")).isNull();
    assertThat(emailService.called).isFalse();
  }

  @Test
  void processForgotPasswordThrottlesRepeatedRequestsFromSameIp() {
    ReflectionTestUtils.setField(
        controller, "forgotPasswordAbuseGuard", new ForgotPasswordAbuseGuard(1, 10));

    User user = new User();
    user.setEmail("player@example.com");
    userPasswordResetService.issuedUser = user;

    MockHttpServletRequest first = buildForgotPasswordRequest("player@example.com", "203.0.113.8");
    ExtendedModelMap firstModel = new ExtendedModelMap();
    String firstView = controller.processForgotPassword(first, firstModel);

    assertThat(firstView).isEqualTo("public/forgotPassword");
    assertThat(firstModel.get("message")).isEqualTo(GENERIC_MESSAGE);
    assertThat(emailService.called).isTrue();

    emailService.called = false;

    MockHttpServletRequest second = buildForgotPasswordRequest("player@example.com", "203.0.113.8");
    ExtendedModelMap secondModel = new ExtendedModelMap();
    String secondView = controller.processForgotPassword(second, secondModel);

    assertThat(secondView).isEqualTo("public/forgotPassword");
    assertThat(secondModel.get("message")).isEqualTo(GENERIC_MESSAGE);
    assertThat(secondModel.get("error")).isNull();
    assertThat(emailService.called).isFalse();
  }

  @Test
  void showResetPasswordFormRejectsExpiredTokens() {
    userPasswordResetService.nextTokenStatus = ResetTokenStatus.EXPIRED;
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.showResetPasswordForm("expired-token", model);

    assertThat(view).isEqualTo("public/resetPassword");
    assertThat(model.get("error")).isEqualTo("Invalid token.");
    assertThat(userPasswordResetService.inspectedToken).isEqualTo("expired-token");
  }

  @Test
  void processResetPasswordShowsSuccessWhenServiceConsumesToken() {
    userPasswordResetService.nextTokenStatus = ResetTokenStatus.VALID;
    userPasswordResetService.nextPasswordResetResult = PasswordResetResult.UPDATED;

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/reset-password");
    request.addParameter("token", "reset-token");
    request.addParameter("password", "Str0ngPassword!");
    request.addParameter("confirm_password", "Str0ngPassword!");
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.processResetPassword(request, model);

    assertThat(view).isEqualTo("public/login");
    assertThat(model.get("message")).isEqualTo("You have successfully changed your password.");
    assertThat(userPasswordResetService.resetToken).isEqualTo("reset-token");
    assertThat(userPasswordResetService.resetPassword).isEqualTo("Str0ngPassword!");
  }

  private MockHttpServletRequest buildForgotPasswordRequest(String email, String remoteAddr) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/forgot-password");
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(8090);
    request.setRequestURI("/forgot-password");
    request.setServletPath("/forgot-password");
    request.setRemoteAddr(remoteAddr);
    request.addParameter("email", email);
    return request;
  }

  private static final class StubEmailService extends EmailService {
    private boolean called;
    private RuntimeException runtimeFailure;
    private String lastHtml;

    private StubEmailService() {
      super(null);
    }

    @Override
    public void sendHtml(String to, String subject, String html) throws MessagingException {
      called = true;
      lastHtml = html;
      if (runtimeFailure != null) {
        throw runtimeFailure;
      }
    }
  }

  private static final class RecordingUserPasswordResetService extends UserPasswordResetService {
    private User issuedUser;
    private String issuedEmail;
    private String issuedToken;
    private Instant issuedExpiresAt;
    private String inspectedToken;
    private ResetTokenStatus nextTokenStatus = ResetTokenStatus.INVALID;
    private String resetToken;
    private String resetPassword;
    private PasswordResetResult nextPasswordResetResult = PasswordResetResult.INVALID_TOKEN;

    private RecordingUserPasswordResetService() {
      super(null, null);
    }

    @Override
    public User issueResetPasswordToken(
        String normalizedEmail, String proposedToken, Instant expiresAt) {
      issuedEmail = normalizedEmail;
      issuedToken = proposedToken;
      issuedExpiresAt = expiresAt;
      if (issuedUser != null) {
        issuedUser.setResetPasswordToken(proposedToken);
        issuedUser.setResetPasswordTokenExpiresAt(expiresAt);
      }
      return issuedUser;
    }

    @Override
    public ResetTokenStatus inspectResetPasswordToken(String token) {
      inspectedToken = token;
      return nextTokenStatus;
    }

    @Override
    public PasswordResetResult resetPassword(String token, String rawPassword) {
      resetToken = token;
      resetPassword = rawPassword;
      return nextPasswordResetResult;
    }
  }
}
