package com.w3llspring.fhpb.web.service.matchentry;

import com.w3llspring.fhpb.web.model.LadderMatchLink;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import com.w3llspring.fhpb.web.util.UserPublicName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MatchEntryContextService {

  private final CourtNameService courtNameService;

  public MatchEntryContextService(CourtNameService courtNameService) {
    this.courtNameService = courtNameService;
  }

  public Map<Long, String> buildCourtNameByLinks(
      Collection<LadderMatchLink> links, Long ladderConfigId) {
    if (links == null || links.isEmpty()) {
      return Map.of();
    }
    return buildCourtNameByUserIds(extractParticipantUserIdsFromLinks(links), ladderConfigId);
  }

  public Map<Long, String> buildCourtNameByMatches(Collection<Match> matches, Long ladderConfigId) {
    return buildCourtNameByUserIds(extractParticipantUserIds(matches), ladderConfigId);
  }

  public Map<Long, String> buildCourtNameByUserIds(Collection<Long> userIds, Long ladderConfigId) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, Set<String>> namesByUser =
        courtNameService.gatherCourtNamesForUsers(userIds, ladderConfigId);
    if (namesByUser == null || namesByUser.isEmpty()) {
      return Map.of();
    }
    Map<Long, String> courtNameByUser = new LinkedHashMap<>();
    for (Long id : userIds) {
      Set<String> names = namesByUser.getOrDefault(id, Set.of());
      if (names != null && !names.isEmpty()) {
        courtNameByUser.put(id, names.iterator().next());
      }
    }
    return courtNameByUser;
  }

  public List<Long> extractParticipantUserIdsFromLinks(Collection<LadderMatchLink> links) {
    if (links == null || links.isEmpty()) {
      return List.of();
    }
    return extractParticipantUserIds(
        links.stream().map(LadderMatchLink::getMatch).filter(Objects::nonNull).toList());
  }

  public List<Long> extractParticipantUserIds(Collection<Match> matches) {
    if (matches == null || matches.isEmpty()) {
      return List.of();
    }
    return matches.stream()
        .flatMap(
            match ->
                java.util.stream.Stream.of(
                    match.getA1(), match.getA2(), match.getB1(), match.getB2()))
        .filter(Objects::nonNull)
        .map(User::getId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  public String determineVoiceLanguage(User currentUser) {
    return "en-US";
  }

  public List<String> buildVoicePhraseHintsFromUsers(User currentUser, Collection<User> users) {
    if (users == null || users.isEmpty()) {
      return buildVoicePhraseHints(currentUser, List.of());
    }
    return buildVoicePhraseHints(
        currentUser, users.stream().filter(Objects::nonNull).map(UserPublicName::forUser).toList());
  }

  public List<String> buildVoicePhraseHints(User currentUser, Collection<String> candidateNames) {
    LinkedHashSet<String> hints = new LinkedHashSet<>();
    String[] openers = {
      "I beat", "We beat", "We lost to", "Lost to", "Won", "Won against", "Beat", "Split sets with"
    };
    for (String opener : openers) {
      hints.add(opener);
    }

    String me = currentUser != null ? UserPublicName.forUser(currentUser) : "me";
    if (me != null && !me.isBlank()) {
      hints.add(me);
    }

    List<String> playerNames = normalizeCandidateNames(candidateNames, 60);
    for (String name : playerNames) {
      hints.add(name);
      hints.add("I beat " + name);
      hints.add("We beat " + name);
      hints.add("Lost to " + name);
      hints.add(name + " and I");
      hints.add("Beat " + name + " eleven");
      hints.add("Me and " + name);
      hints.add("Me and " + name + " beat");
      hints.add("Me and " + name + " lost to");
    }

    List<String> pairNames = new ArrayList<>();
    int pairBaseLimit = Math.min(playerNames.size(), 10);
    for (int i = 0; i < pairBaseLimit; i++) {
      for (int j = i + 1; j < pairBaseLimit; j++) {
        String pair = playerNames.get(i) + " and " + playerNames.get(j);
        pairNames.add(pair);
        if (pairNames.size() >= 20) {
          break;
        }
      }
      if (pairNames.size() >= 20) {
        break;
      }
    }

    for (String pair : pairNames) {
      hints.add(pair);
      hints.add(pair + " beat");
      hints.add(pair + " lost to");
      hints.add(pair + " beat us");
      hints.add(pair + " beat them");
    }

    int pairPhraseLimit = Math.min(pairNames.size(), 8);
    for (int i = 0; i < pairPhraseLimit; i++) {
      for (int j = 0; j < pairPhraseLimit; j++) {
        if (i == j) {
          continue;
        }
        String winners = pairNames.get(i);
        String losers = pairNames.get(j);
        hints.add(winners + " beat " + losers);
        hints.add(winners + " lost to " + losers);
      }
    }

    int partnerLimit = Math.min(playerNames.size(), 12);
    int opponentPairLimit = Math.min(pairNames.size(), 6);
    for (int i = 0; i < partnerLimit; i++) {
      String partner = playerNames.get(i);
      for (int j = 0; j < opponentPairLimit; j++) {
        String oppPair = pairNames.get(j);
        hints.add(partner + " and I beat " + oppPair);
        hints.add(partner + " and I lost to " + oppPair);
        hints.add("Me and " + partner + " beat " + oppPair);
        hints.add("Me and " + partner + " lost to " + oppPair);
      }
    }

    String[] scoreWords = {
      "zero",
      "one",
      "two",
      "three",
      "four",
      "five",
      "six",
      "seven",
      "eight",
      "nine",
      "ten",
      "eleven",
      "twelve",
      "thirteen",
      "fourteen",
      "fifteen"
    };
    for (int i = 0; i <= 15; i++) {
      hints.add(String.valueOf(i));
    }
    for (String word : scoreWords) {
      hints.add(word);
      hints.add("eleven " + word);
      hints.add("eleven to " + word);
    }

    return hints.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .limit(160)
        .toList();
  }

  private List<String> normalizeCandidateNames(Collection<String> candidateNames, int maxNames) {
    if (candidateNames == null || candidateNames.isEmpty()) {
      return List.of();
    }
    Set<String> normalized = new LinkedHashSet<>();
    for (String candidateName : candidateNames) {
      if (candidateName == null) {
        continue;
      }
      String normalizedName = candidateName.trim().replaceAll("\\s+", " ");
      if (normalizedName.isEmpty()) {
        continue;
      }
      normalized.add(normalizedName);
      if (normalized.size() >= maxNames) {
        break;
      }
    }
    return List.copyOf(normalized);
  }
}
