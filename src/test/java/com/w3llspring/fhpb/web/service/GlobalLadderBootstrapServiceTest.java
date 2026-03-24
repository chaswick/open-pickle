package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.util.UserPublicName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class GlobalLadderBootstrapServiceTest {

  @Test
  void ensureAdminUser_usesStaticFallbackWhenDisplayNameBlank() {
    UserRepository userRepo = mock(UserRepository.class);
    when(userRepo.findByEmail("admin@test.com")).thenReturn(null);
    when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    GlobalLadderBootstrapService service =
        new GlobalLadderBootstrapService(
            true,
            false,
            false,
            "admin@test.com",
            "secret",
            "",
            "Community Ladder",
            "Opening Season",
            userRepo,
            mock(LadderConfigRepository.class),
            mock(LadderSeasonRepository.class),
            mock(LadderMembershipRepository.class),
            null,
            new BCryptPasswordEncoder());

    User saved = (User) ReflectionTestUtils.invokeMethod(service, "ensureAdminUser");

    assertThat(saved.getNickName()).isEqualTo(UserPublicName.FALLBACK);
  }

  @Test
  void ensureAdminUser_replacesEmailDerivedNicknameWhenDisplayNameBlank() {
    UserRepository userRepo = mock(UserRepository.class);
    User existing = new User();
    existing.setEmail("admin@test.com");
    existing.setNickName("admin");
    existing.setAdmin(true);
    existing.setMaxOwnedLadders(1);

    when(userRepo.findByEmail("admin@test.com")).thenReturn(existing);
    when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    GlobalLadderBootstrapService service =
        new GlobalLadderBootstrapService(
            true,
            false,
            false,
            "admin@test.com",
            "secret",
            "",
            "Community Ladder",
            "Opening Season",
            userRepo,
            mock(LadderConfigRepository.class),
            mock(LadderSeasonRepository.class),
            mock(LadderMembershipRepository.class),
            null,
            new BCryptPasswordEncoder());

    User saved = (User) ReflectionTestUtils.invokeMethod(service, "ensureAdminUser");

    assertThat(saved.getNickName()).isEqualTo(UserPublicName.FALLBACK);
  }
}
