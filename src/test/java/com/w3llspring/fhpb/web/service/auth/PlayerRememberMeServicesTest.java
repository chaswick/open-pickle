package com.w3llspring.fhpb.web.service.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

class PlayerRememberMeServicesTest {
  @Test
  void existingRegisteredRememberMeCookiesRemainCompatible() {
    User user = new User();
    user.setEmail("member@test.local");
    user.setPassword("existing-hash");
    var details = new CustomUserDetails(user);
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    var previous = new TokenBasedRememberMeServices("stable-key", email -> details);
    previous.setTokenValiditySeconds(2592000);
    var response = new MockHttpServletResponse();
    previous.onLoginSuccess(new MockHttpServletRequest(), response, authentication);
    var current =
        new PlayerRememberMeServices(
            "stable-key", email -> details, mock(UserRepository.class), true, 2592000, 365, true);
    var request = new MockHttpServletRequest();
    request.setCookies(response.getCookie("remember-me"));
    assertThat(current.autoLogin(request, new MockHttpServletResponse()).getName())
        .isEqualTo(user.getEmail());
  }

  @Test
  void guestCookiesAreSecureHttpOnlyAndCappedAtOneYearWhileNormalLoginsKeepTheirLifetime() {
    User user = new User();
    user.setEmail("player@g.invalid");
    user.setPassword("internal-secret");
    user.setNameOnlyAccount(true);
    var service =
        new PlayerRememberMeServices(
            "stable-key",
            email -> new CustomUserDetails(user),
            mock(UserRepository.class),
            true,
            2592000,
            2000,
            true);
    var response = new MockHttpServletResponse();
    try {
      service.signInPlayer(user, new MockHttpServletRequest(), response);
    } finally {
      SecurityContextHolder.clearContext();
    }
    var cookie = response.getCookie("remember-me");
    assertThat(cookie.getMaxAge()).isEqualTo(31536000);
    assertThat(cookie.getSecure()).isTrue();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    user.setNameOnlyAccount(false);
    var details = new CustomUserDetails(user);
    var normal = new MockHttpServletResponse();
    service.onLoginSuccess(
        new MockHttpServletRequest(),
        normal,
        UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));
    assertThat(normal.getCookie("remember-me").getMaxAge()).isEqualTo(2592000);
  }

  @Test
  void sharedWifiAllowsThirtyTwoPlayersAndRecoveryGuessingIsLimitedSeparately() {
    var guard = new PlayerAccessAbuseGuard(new ClientIpResolver(""), 64);
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.55");
    for (int i = 0; i < 64; i++) assertThat(guard.allowJoin(request)).isTrue();
    assertThat(guard.allowJoin(request)).isFalse();
    for (int i = 0; i < 10; i++) assertThat(guard.allowRecovery(request)).isTrue();
    assertThat(guard.allowRecovery(request)).isFalse();
    assertThat(guard.allowPreview(request)).isTrue();
  }
}
