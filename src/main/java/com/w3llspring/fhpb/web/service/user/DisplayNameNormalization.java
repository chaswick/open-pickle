package com.w3llspring.fhpb.web.service.user;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public final class DisplayNameNormalization {

  private static final Map<Character, Character> LEET_MAP =
      Map.ofEntries(
          Map.entry('0', 'o'),
          Map.entry('1', 'i'),
          Map.entry('3', 'e'),
          Map.entry('4', 'a'),
          Map.entry('5', 's'),
          Map.entry('7', 't'),
          Map.entry('@', 'a'),
          Map.entry('$', 's'),
          Map.entry('!', 'i'),
          Map.entry('€', 'e'),
          Map.entry('£', 'l'),
          Map.entry('8', 'b'));

  private DisplayNameNormalization() {}

  public static String normalize(String input) {
    String lower = StringUtils.hasText(input) ? input : "";
    lower =
        Normalizer.normalize(lower, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.US);
    StringBuilder sb = new StringBuilder(lower.length());
    for (int i = 0; i < lower.length(); i++) {
      char ch = lower.charAt(i);
      char mapped = LEET_MAP.getOrDefault(ch, ch);
      if (Character.isLetterOrDigit(mapped)) {
        sb.append(mapped);
      }
    }
    return sb.toString();
  }

  public static String collapseRepeated(String value) {
    if (value == null || value.length() < 2) {
      return value == null ? "" : value;
    }
    StringBuilder sb = new StringBuilder(value.length());
    char last = 0;
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (current != last) {
        sb.append(current);
        last = current;
      }
    }
    return sb.toString();
  }

  public static Set<String> tokenize(String input) {
    if (!StringUtils.hasText(input)) {
      return Set.of();
    }
    return Arrays.stream(input.split("[^\\p{Alnum}]+"))
        .map(String::trim)
        .filter(StringUtils::hasText)
        .map(DisplayNameNormalization::normalize)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());
  }

  public static String normalizedCollapsed(String input) {
    return collapseRepeated(normalize(input));
  }

  public static int levenshteinDistance(String left, String right) {
    String a = left != null ? left : "";
    String b = right != null ? right : "";
    if (a.equals(b)) {
      return 0;
    }
    if (a.isEmpty()) {
      return b.length();
    }
    if (b.isEmpty()) {
      return a.length();
    }

    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        current[j] =
            Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }
}
