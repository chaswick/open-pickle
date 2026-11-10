package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.controller.site.LandingController;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class LandingControllerTest {

  private final LandingController controller = new LandingController();
  private Object previousAuthenticatedUserService;

  @BeforeEach
  void isolateAuthenticationSupport() {
    previousAuthenticatedUserService =
        ReflectionTestUtils.getField(AuthenticatedUserSupport.class, "authenticatedUserService");
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    ReflectionTestUtils.setField(
        AuthenticatedUserSupport.class,
        "authenticatedUserService",
        previousAuthenticatedUserService);
  }

  @Test
  void rootShowsLandingWhenNoAuthenticationExists() {
    SecurityContextHolder.clearContext();

    assertThat(controller.root()).isEqualTo("public/landing");
  }

  @Test
  void rootShowsLandingForAnonymousAuthentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "test-key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    assertThat(controller.root()).isEqualTo("public/landing");
  }

  @Test
  void rootRedirectsAuthenticatedUsersHome() {
    User user = new User();
    user.setId(7L);
    user.setNickName("Tester");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of()));

    assertThat(controller.root()).isEqualTo("redirect:/home");
  }
}
