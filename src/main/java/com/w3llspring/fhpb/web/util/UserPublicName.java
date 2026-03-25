package com.w3llspring.fhpb.web.util;

import com.w3llspring.fhpb.web.model.User;

public final class UserPublicName {

  public static final String FALLBACK = "Open-Pickle User";
  public static final String GUEST = "Guest";

  private UserPublicName() {}

  public static String forUser(User user) {
    if (user == null) {
      return FALLBACK;
    }
    String nickName = user.getNickName();
    if (nickName != null && !nickName.isBlank()) {
      return nickName.trim();
    }
    return FALLBACK;
  }

  public static String forUserOrGuest(User user) {
    return user == null ? GUEST : forUser(user);
  }
}
