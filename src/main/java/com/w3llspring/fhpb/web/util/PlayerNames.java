package com.w3llspring.fhpb.web.util;

import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Names for one displayed list; public IDs clarify repeated names without renaming players. */
public final class PlayerNames {
  private PlayerNames() {}

  public record Name(String name, String publicCode) {}

  public static Set<String> duplicates(Collection<String> names) {
    Set<String> seen = new LinkedHashSet<>(), repeated = new LinkedHashSet<>();
    if (names != null)
      for (String name : names) {
        if (name != null && !name.isBlank() && !seen.add(key(name))) repeated.add(key(name));
      }
    return repeated;
  }

  public static Set<String> duplicatesInMatch(String... names) {
    return duplicates(Arrays.asList(names));
  }

  public static boolean requiresId(Set<String> duplicates, String name) {
    return name != null && duplicates != null && duplicates.contains(key(name));
  }

  public static String memberCode(
      User user, String name, Set<String> duplicates, Map<Long, Name> list) {
    if (user == null) return null;
    Name label = list != null ? list.get(user.getId()) : null;
    return requiresId(duplicates, name) || (label != null && label.publicCode() != null)
        ? user.getPublicCode()
        : null;
  }

  public static Map<Long, Name> inMatches(Collection<Match> matches) {
    List<User> players = new ArrayList<>();
    List<String> guests = new ArrayList<>();
    if (matches != null)
      for (Match match : matches) {
        if (match == null) continue;
        participant(players, guests, match.getA1(), match.isA1Guest(), match.getA1GuestName());
        participant(players, guests, match.getA2(), match.isA2Guest(), match.getA2GuestName());
        participant(players, guests, match.getB1(), match.isB1Guest(), match.getB1GuestName());
        participant(players, guests, match.getB2(), match.isB2Guest(), match.getB2GuestName());
      }
    return resolve(players, null, guests);
  }

  private static void participant(
      List<User> players, List<String> guests, User user, boolean guest, String guestName) {
    if (!guest && user != null) players.add(user);
    else if (guestName != null && !guestName.isBlank()) guests.add(guestName);
  }

  public static Map<Long, Name> resolve(Collection<User> users, Map<Long, String> courtNames) {
    return resolve(users != null ? users : List.of(), courtNames, List.of());
  }

  public static Map<Long, Name> resolve(
      Collection<User> users, Map<Long, String> courtNames, Collection<String> guestNames) {
    Map<Long, User> people = new LinkedHashMap<>();
    for (User user : users) {
      if (user != null && user.getId() != null) people.putIfAbsent(user.getId(), user);
    }
    Map<Long, String> names = new LinkedHashMap<>();
    Map<String, Integer> counts = new LinkedHashMap<>();
    people.forEach(
        (id, user) -> {
          String alias = courtNames != null ? courtNames.get(id) : null;
          String name =
              alias != null && !alias.isBlank() ? alias.trim() : UserPublicName.forUser(user);
          names.put(id, name);
          counts.merge(key(name), 1, Integer::sum);
        });
    guestNames.stream()
        .filter(n -> n != null && !n.isBlank())
        .forEach(n -> counts.merge(key(n), 1, Integer::sum));
    Map<Long, Name> result = new LinkedHashMap<>();
    names.forEach(
        (id, name) ->
            result.put(
                id,
                new Name(name, counts.get(key(name)) > 1 ? people.get(id).getPublicCode() : null)));
    return result;
  }

  private static String key(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }
}
