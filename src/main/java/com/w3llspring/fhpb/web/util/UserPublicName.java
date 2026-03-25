package com.w3llspring.fhpb.web.util;

import com.w3llspring.fhpb.web.model.User;

public final class UserPublicName {

  private static final String DEFAULT_FALLBACK = "Open-Pickle User";
  public static volatile String FALLBACK = DEFAULT_FALLBACK;
  public static final String GUEST = "Guest";

  private UserPublicName() {}

  public static void configureFallbackFromAppName(String appName) {
    if (appName == null || appName.isBlank()) {
      FALLBACK = DEFAULT_FALLBACK;
      return;
    }
    FALLBACK = appName.trim() + " User";
  }

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
