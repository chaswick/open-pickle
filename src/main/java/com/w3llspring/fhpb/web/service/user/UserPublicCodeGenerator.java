package com.w3llspring.fhpb.web.service.user;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/** Generates short, immutable public-facing identity codes without exposing raw database ids. */
public final class UserPublicCodeGenerator {

  private static final String WORDS_RESOURCE = "/identity/member-code-words.txt";
  private static final Pattern WORD = Pattern.compile("[a-z]{3,6}");
  private static final List<String> WORDS = loadWords();
  // Previously issued numeric-suffix IDs remain valid and unchanged.
  private static final Pattern CURRENT_FORMAT =
      Pattern.compile(
          "^(?:[a-z]{3,6}-[a-z]{3,6}-[a-z]{3,6}|PB-[a-z]{3,4}-[a-z]{3,4}-(?:\\d{3}|\\d{6}))$");
  private static final SecureRandom RANDOM = new SecureRandom();

  private UserPublicCodeGenerator() {}

  public static String nextCode() {
    return nextCode(RANDOM);
  }

  static String nextCode(RandomGenerator random) {
    String first = WORDS.get(random.nextInt(WORDS.size()));
    String second;
    do {
      second = WORDS.get(random.nextInt(WORDS.size()));
    } while (second.equals(first));
    String third;
    do {
      third = WORDS.get(random.nextInt(WORDS.size()));
    } while (third.equals(first) || third.equals(second));
    return first + "-" + second + "-" + third;
  }

  public static boolean isCurrentFormat(String code) {
    return code != null && CURRENT_FORMAT.matcher(code).matches();
  }

  private static List<String> loadWords() {
    var input = UserPublicCodeGenerator.class.getResourceAsStream(WORDS_RESOURCE);
    if (input == null) throw new IllegalStateException("Member ID word list is missing.");
    try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      List<String> words =
          reader
              .lines()
              .map(String::trim)
              .filter(line -> !line.isEmpty() && !line.startsWith("#"))
              .toList();
      if (words.size() != 1024
          || words.stream().distinct().count() != words.size()
          || words.stream().anyMatch(word -> !WORD.matcher(word).matches())) {
        throw new IllegalStateException(
            "Member ID word list must contain 1,024 distinct short words.");
      }
      return words;
    } catch (IOException ex) {
      throw new IllegalStateException("Member ID word list could not be read.", ex);
    }
  }
}
