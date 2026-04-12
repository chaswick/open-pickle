package com.w3llspring.fhpb.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.service.auth.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

@ExtendWith(OutputCaptureExtension.class)
class TermsAwareAuthenticationFailureHandlerTest {

  @Test
  void onAuthenticationFailure_neverAddsTermsPromptOrLooksUpUser() throws Exception {
    UserRepository userRepository = mock(UserRepository.class);
    TermsAwareAuthenticationFailureHandler handler = new TermsAwareAuthenticationFailureHandler();

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
    request.addParameter("user", "user@test.local");
    request.addParameter("termsRequired", "true");
    request.addParameter("acceptTerms", "true");
    request.addParameter("returnTo", "/groups/7");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationFailure(
        request, response, new BadCredentialsException("bad credentials"));

    assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true&returnTo=%2Fgroups%2F7");
    assertThat(response.getRedirectedUrl()).doesNotContain("userEmail");
    assertThat(response.getRedirectedUrl()).doesNotContain("termsRequired");
    verifyNoInteractions(userRepository);
  }

  @Test
  void onAuthenticationFailureLogsMaskedPrincipalAndResolvedClientIp(CapturedOutput output)
      throws Exception {
    TermsAwareAuthenticationFailureHandler handler =
        new TermsAwareAuthenticationFailureHandler(new ClientIpResolver("127.0.0.1"));

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Forwarded-For", "198.51.100.24");
    request.addParameter("user", "player@example.com");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationFailure(
        request, response, new BadCredentialsException("bad credentials"));

    assertThat(output.getAll()).contains("Login failed:");
    assertThat(output.getAll()).contains("ip=198.51.100.24");
    assertThat(output.getAll()).contains("principal=p***@example.com");
    assertThat(output.getAll()).contains("reason=BadCredentialsException");
    assertThat(output.getAll()).contains("uri=/login");
  }
}
