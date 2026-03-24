package com.w3llspring.fhpb.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.w3llspring.fhpb.web.db.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

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
}
