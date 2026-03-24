package com.w3llspring.fhpb.web.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserServiceTest {

  @Mock private UserRepository userRepository;

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void currentUserRefreshesSessionSnapshotAndCachesPerRequest() {
    AuthenticatedUserService service = new AuthenticatedUserService(userRepository);

    User sessionUser = new User();
    sessionUser.setId(41L);
    sessionUser.setEmail("admin@test.com");
    sessionUser.setNickName("Old Nick");
    sessionUser.setAdmin(true);
    sessionUser.setTimeZone("America/New_York");

    User dbUser = new User();
    dbUser.setId(41L);
    dbUser.setEmail("viewer@test.com");
    dbUser.setNickName("Fresh Nick");
    dbUser.setAdmin(false);
    dbUser.setTimeZone("America/Chicago");

    when(userRepository.findById(41L)).thenReturn(Optional.of(dbUser));

    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));

    var auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(sessionUser), null, List.of());

    User first = service.currentUser(auth);
    User second = service.currentUser(auth);

    assertThat(first).isSameAs(sessionUser);
    assertThat(second).isSameAs(sessionUser);
    assertThat(sessionUser.getEmail()).isEqualTo("viewer@test.com");
    assertThat(sessionUser.getNickName()).isEqualTo("Fresh Nick");
    assertThat(sessionUser.isAdmin()).isFalse();
    assertThat(sessionUser.getTimeZone()).isEqualTo("America/Chicago");
    verify(userRepository, times(1)).findById(41L);
  }

  @Test
  void currentUserReturnsNullWhenBackingUserNoLongerExists() {
    AuthenticatedUserService service = new AuthenticatedUserService(userRepository);

    User sessionUser = new User();
    sessionUser.setId(99L);
    sessionUser.setEmail("stale@test.com");

    when(userRepository.findById(99L)).thenReturn(Optional.empty());

    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));

    var auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(sessionUser), null, List.of());

    assertThat(service.currentUser(auth)).isNull();
  }
}
