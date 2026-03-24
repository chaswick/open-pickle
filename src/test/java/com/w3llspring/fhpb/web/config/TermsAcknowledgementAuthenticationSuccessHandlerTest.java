package com.w3llspring.fhpb.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

class TermsAcknowledgementAuthenticationSuccessHandlerTest {

  private static final TermsAcceptancePolicy TERMS_POLICY =
      new TermsAcceptancePolicy("2026-03-19T00:00:00-04:00");

  @Test
  void onAuthenticationSuccess_discardsUnsafeSavedRequestBeforeRedirecting() throws Exception {
    UserRepository userRepository = mock(UserRepository.class);
    MutableRequestCache requestCache =
        new MutableRequestCache(
            savedRequest(
                "http://localhost:8090/.well-known/appspecific/com.chrome.devtools.json?continue"));
    TermsAcknowledgementAuthenticationSuccessHandler handler =
        new TermsAcknowledgementAuthenticationSuccessHandler(
            userRepository, TERMS_POLICY, requestCache);
    handler.setDefaultTargetUrl("/home");

    User user = new User();
    user.setId(1L);
    user.setEmail("user@test.local");
    user.setAcknowledgedTermsAt(Instant.now());

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(
        request,
        response,
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null));

    assertThat(response.getRedirectedUrl()).isEqualTo("/home");
    assertThat(requestCache.savedRequest()).isNull();
  }

  @Test
  void onAuthenticationSuccess_keepsSafeSavedRequestRedirect() throws Exception {
    UserRepository userRepository = mock(UserRepository.class);
    MutableRequestCache requestCache =
        new MutableRequestCache(savedRequest("http://localhost:8090/private-groups"));
    TermsAcknowledgementAuthenticationSuccessHandler handler =
        new TermsAcknowledgementAuthenticationSuccessHandler(
            userRepository, TERMS_POLICY, requestCache);
    handler.setDefaultTargetUrl("/home");

    User user = new User();
    user.setId(1L);
    user.setEmail("user@test.local");
    user.setAcknowledgedTermsAt(Instant.now());

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(
        request,
        response,
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null));

    assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:8090/private-groups");
  }

  @Test
  void onAuthenticationSuccess_doesNotAcknowledgeTermsFromLoginRequest() throws Exception {
    UserRepository userRepository = mock(UserRepository.class);
    MutableRequestCache requestCache = new MutableRequestCache(null);
    TermsAcknowledgementAuthenticationSuccessHandler handler =
        new TermsAcknowledgementAuthenticationSuccessHandler(
            userRepository, TERMS_POLICY, requestCache);
    handler.setDefaultTargetUrl("/home");

    User user = new User();
    user.setId(1L);
    user.setEmail("user@test.local");

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
    request.addParameter("acceptTerms", "true");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(
        request,
        response,
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null));

    assertThat(response.getRedirectedUrl()).isEqualTo("/accept-terms");
    assertThat(user.getAcknowledgedTermsAt()).isNull();
  }

  @Test
  void onAuthenticationSuccess_redirectsToAcceptTermsWithoutEmailQueryParam() throws Exception {
    UserRepository userRepository = mock(UserRepository.class);
    MutableRequestCache requestCache =
        new MutableRequestCache(savedRequest("http://localhost:8090/private-groups"));
    TermsAcknowledgementAuthenticationSuccessHandler handler =
        new TermsAcknowledgementAuthenticationSuccessHandler(
            userRepository, TERMS_POLICY, requestCache);
    handler.setDefaultTargetUrl("/home");

    User user = new User();
    user.setId(1L);
    user.setEmail("user@test.local");

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(
        request,
        response,
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null));

    assertThat(response.getRedirectedUrl()).isEqualTo("/accept-terms?returnTo=%2Fprivate-groups");
    assertThat(response.getRedirectedUrl()).doesNotContain("userEmail");
  }

  private SavedRequest savedRequest(String redirectUrl) {
    return new FixedSavedRequest(redirectUrl);
  }

  private static final class MutableRequestCache implements RequestCache {
    private SavedRequest savedRequest;

    private MutableRequestCache(SavedRequest savedRequest) {
      this.savedRequest = savedRequest;
    }

    private SavedRequest savedRequest() {
      return savedRequest;
    }

    @Override
    public void saveRequest(
        jakarta.servlet.http.HttpServletRequest request,
        jakarta.servlet.http.HttpServletResponse response) {}

    @Override
    public SavedRequest getRequest(
        jakarta.servlet.http.HttpServletRequest request,
        jakarta.servlet.http.HttpServletResponse response) {
      return savedRequest;
    }

    @Override
    public jakarta.servlet.http.HttpServletRequest getMatchingRequest(
        jakarta.servlet.http.HttpServletRequest request,
        jakarta.servlet.http.HttpServletResponse response) {
      return null;
    }

    @Override
    public void removeRequest(
        jakarta.servlet.http.HttpServletRequest request,
        jakarta.servlet.http.HttpServletResponse response) {
      savedRequest = null;
    }
  }

  private static final class FixedSavedRequest implements SavedRequest {
    private final String redirectUrl;

    private FixedSavedRequest(String redirectUrl) {
      this.redirectUrl = redirectUrl;
    }

    @Override
    public String getRedirectUrl() {
      return redirectUrl;
    }

    @Override
    public List<jakarta.servlet.http.Cookie> getCookies() {
      return List.of();
    }

    @Override
    public String getMethod() {
      return "GET";
    }

    @Override
    public List<String> getHeaderValues(String name) {
      return List.of();
    }

    @Override
    public Collection<String> getHeaderNames() {
      return List.of();
    }

    @Override
    public List<Locale> getLocales() {
      return List.of(Locale.US);
    }

    @Override
    public String[] getParameterValues(String name) {
      return null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
      return Map.of();
    }
  }
}
