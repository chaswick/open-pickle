package com.w3llspring.fhpb.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.model.User;
import org.junit.jupiter.api.Test;

class UserPublicNameTest {

  @Test
  void forUser_prefersNickname() {
    User user = new User();
    user.setNickName("CourtAce");
    user.setEmail("courtace@test.local");

    assertThat(UserPublicName.forUser(user)).isEqualTo("CourtAce");
  }

  @Test
  void forUser_returnsStaticFallbackWhenNicknameMissing() {
    User user = new User();
    user.setEmail("hidden@test.local");

    assertThat(UserPublicName.forUser(user)).isEqualTo(UserPublicName.FALLBACK);
  }

  @Test
  void forUserOrGuest_returnsGuestForNull() {
    assertThat(UserPublicName.forUserOrGuest(null)).isEqualTo(UserPublicName.GUEST);
  }
}
