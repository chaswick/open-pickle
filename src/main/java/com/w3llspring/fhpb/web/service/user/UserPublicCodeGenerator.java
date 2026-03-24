package com.w3llspring.fhpb.web.service.user;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/** Generates short, immutable public-facing identity codes without exposing raw database ids. */
public final class UserPublicCodeGenerator {

  private static final String[] PREFIX_WORDS = {
    "aqua", "bays", "calm", "citr", "cove", "dawn", "dune", "fern",
    "gold", "jade", "kiwi", "lake", "lime", "mist", "navy", "nova",
    "orch", "pine", "reef", "rose", "ruby", "sage", "sand", "sky",
    "surf", "teal", "tide", "wave", "wind", "zest", "brio", "glow"
  };
  private static final String[] SUFFIX_WORDS = {
    "ace", "ball", "beam", "bolt", "dash", "dink", "dock", "drop",
    "flip", "game", "glow", "grip", "jump", "kick", "king", "lane",
    "lift", "link", "loop", "luck", "mark", "mint", "pace", "path",
    "play", "race", "ride", "rise", "roll", "rung", "rush", "save"
  };
  private static final Pattern CURRENT_FORMAT = Pattern.compile("^PB-[a-z]{4}-[a-z]{3,4}-\\d{3}$");
  private static final SecureRandom RANDOM = new SecureRandom();

  private UserPublicCodeGenerator() {}

  public static String nextCode() {
    String prefix = PREFIX_WORDS[RANDOM.nextInt(PREFIX_WORDS.length)];
    String suffix = SUFFIX_WORDS[RANDOM.nextInt(SUFFIX_WORDS.length)];
    int number = RANDOM.nextInt(1000);
    return String.format(Locale.US, "PB-%s-%s-%03d", prefix, suffix, number);
  }

  public static boolean isCurrentFormat(String code) {
    return code != null && CURRENT_FORMAT.matcher(code).matches();
  }
}
