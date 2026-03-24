package com.w3llspring.fhpb.web.service.matchlog;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import com.w3llspring.fhpb.web.util.UserPublicName;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

public class MatchLogPlayerPickerService {

  private static final int SEARCHABLE_PLAYER_PICKER_THRESHOLD = 16;
  private static final int RECENT_PLAYER_SECTION_LIMIT = 6;

  private final MatchRepository matchRepository;
  private final LadderSeasonRepository seasonRepository;
  private final CourtNameService courtNameService;
  private final LadderConfigRepository ladderConfigRepository;

  public MatchLogPlayerPickerService(
      MatchRepository matchRepository,
      LadderSeasonRepository seasonRepository,
      CourtNameService courtNameService,
      LadderConfigRepository ladderConfigRepository) {
    this.matchRepository = matchRepository;
    this.seasonRepository = seasonRepository;
    this.courtNameService = courtNameService;
    this.ladderConfigRepository = ladderConfigRepository;
  }

  public void populatePlayerSelectionModel(
      Model model, List<User> users, List<User> otherPlayers, Long ladderId, Long seasonId) {
    PlayerPickerModel pickerModel = buildPlayerPickerModel(users, otherPlayers, ladderId, seasonId);
    model.addAttribute("users", pickerModel.users());
    model.addAttribute("otherPlayers", pickerModel.otherPlayers());
    model.addAttribute("playerPrimaryLabelByUser", pickerModel.primaryLabelByUser());
    model.addAttribute("playerSecondaryLabelByUser", pickerModel.secondaryLabelByUser());
    model.addAttribute("playerOptionLabelByUser", pickerModel.optionLabelByUser());
    model.addAttribute("playerSearchTextByUser", pickerModel.searchTextByUser());
    model.addAttribute("recentPlayerIds", pickerModel.recentPlayerIds());
    model.addAttribute("useSearchablePlayerPicker", pickerModel.useSearchablePlayerPicker());
  }

  public PlayerSelectionLists buildCreatePlayerSelectionLists(
      List<User> allUsers,
      User currentUser,
      String authEmail,
      Set<Long> eligibleMemberIds,
      Long seasonId,
      boolean voiceReviewMode) {
    List<User> ordered = new ArrayList<>();
    Set<Long> added = new LinkedHashSet<>();

    if (currentUser != null
        && (currentUser.getId() == null
            || eligibleMemberIds == null
            || eligibleMemberIds.contains(currentUser.getId()))) {
      ordered.add(currentUser);
      if (currentUser.getId() != null) {
        added.add(currentUser.getId());
      }
    } else if (StringUtils.hasText(authEmail)) {
      allUsers.stream()
          .filter(
              user -> authEmail.equalsIgnoreCase(safeLower(user != null ? user.getEmail() : null)))
          .findFirst()
          .ifPresent(
              user -> {
                ordered.add(user);
                if (user.getId() != null) {
                  added.add(user.getId());
                }
              });
    }

    Set<Long> activeSeasonPlayerIds = resolveActiveSeasonPlayerIds(seasonId);
    boolean hasSeasonContext = activeSeasonPlayerIds != null;
    Comparator<User> sortedByActivityThenName =
        Comparator.comparing(
                (User user) -> isInactiveForSeason(hasSeasonContext, activeSeasonPlayerIds, user))
            .thenComparing(this::sortKey);

    ordered.addAll(
        allUsers.stream()
            .filter(user -> user.getId() == null || !added.contains(user.getId()))
            .sorted(sortedByActivityThenName)
            .collect(Collectors.toList()));

    List<User> otherPlayers =
        currentUser == null
            ? ordered
            : voiceReviewMode
                ? ordered
                : ordered.stream()
                    .filter(user -> !Objects.equals(user.getId(), currentUser.getId()))
                    .collect(Collectors.toList());
    return new PlayerSelectionLists(ordered, otherPlayers);
  }

  public PlayerSelectionLists buildEditPlayerSelectionLists(
      List<User> allUsers, Match match, User currentUser, Long seasonId) {
    List<User> ordered = new ArrayList<>();
    Set<Long> added = new LinkedHashSet<>();
    if (currentUser != null) {
      ordered.add(currentUser);
      if (currentUser.getId() != null) {
        added.add(currentUser.getId());
      }
    }

    Set<Long> activeSeasonPlayerIds = resolveActiveSeasonPlayerIds(seasonId);
    boolean hasSeasonContext = activeSeasonPlayerIds != null;
    Comparator<User> sortedByActivityThenName =
        Comparator.comparing(
                (User user) -> isInactiveForSeason(hasSeasonContext, activeSeasonPlayerIds, user))
            .thenComparing(this::sortKey);

    ordered.addAll(
        allUsers.stream()
            .filter(user -> user.getId() == null || !added.contains(user.getId()))
            .sorted(sortedByActivityThenName)
            .collect(Collectors.toList()));

    List<User> users = ensureMatchParticipantsPresent(ordered, match);
    boolean currentUserIsParticipant = isMatchParticipant(match, currentUser);
    List<User> otherPlayers =
        currentUser == null || currentUserIsParticipant
            ? users
            : users.stream()
                .filter(user -> !Objects.equals(user.getId(), currentUser.getId()))
                .collect(Collectors.toList());
    return new PlayerSelectionLists(users, otherPlayers);
  }

  private PlayerPickerModel buildPlayerPickerModel(
      List<User> users, List<User> otherPlayers, Long ladderId, Long seasonId) {
    Map<Long, String> courtNameByUser = resolvePrimaryCourtNames(users, ladderId);
    List<User> sortedUsers = sortPlayersForPicker(users, courtNameByUser);
    List<User> sortedOtherPlayers = sortPlayersForPicker(otherPlayers, courtNameByUser);

    PlayerPickerPresentation presentation =
        buildPlayerPickerPresentation(sortedUsers, ladderId, seasonId, courtNameByUser);

    return new PlayerPickerModel(
        sortedUsers,
        sortedOtherPlayers,
        presentation.primaryLabelByUser(),
        presentation.secondaryLabelByUser(),
        presentation.optionLabelByUser(),
        presentation.searchTextByUser(),
        presentation.recentPlayerIds(),
        sortedUsers.size() > SEARCHABLE_PLAYER_PICKER_THRESHOLD);
  }

  private PlayerPickerPresentation buildPlayerPickerPresentation(
      List<User> users, Long ladderId, Long seasonId, Map<Long, String> courtNameByUser) {
    Map<String, Long> courtNameCounts = countNormalizedValues(courtNameByUser.values());
    Map<String, Long> displayNameCounts =
        countNormalizedValues(
            users.stream().map(this::playerDisplayName).collect(Collectors.toList()));

    Map<Long, String> primaryLabelByUser = new LinkedHashMap<>();
    Map<Long, String> secondaryLabelByUser = new LinkedHashMap<>();
    Map<Long, String> optionLabelByUser = new LinkedHashMap<>();
    Map<Long, String> searchTextByUser = new LinkedHashMap<>();

    for (User user : users) {
      if (user == null || user.getId() == null) {
        continue;
      }

      String courtName = courtNameByUser.get(user.getId());
      String displayName = playerDisplayName(user);
      String publicCode =
          StringUtils.hasText(user.getPublicCode()) ? user.getPublicCode().trim() : null;

      String primary = StringUtils.hasText(courtName) ? courtName : displayName;
      String secondary =
          buildPlayerSecondaryLabel(
              courtName, displayName, publicCode, courtNameCounts, displayNameCounts);
      String optionLabel = StringUtils.hasText(secondary) ? primary + " - " + secondary : primary;

      primaryLabelByUser.put(user.getId(), primary);
      secondaryLabelByUser.put(user.getId(), secondary);
      optionLabelByUser.put(user.getId(), optionLabel);
      searchTextByUser.put(
          user.getId(), buildPlayerSearchText(courtName, displayName, publicCode, optionLabel));
    }

    return new PlayerPickerPresentation(
        primaryLabelByUser,
        secondaryLabelByUser,
        optionLabelByUser,
        searchTextByUser,
        resolveRecentPlayerIds(users, ladderId, seasonId));
  }

  private Map<Long, String> resolvePrimaryCourtNames(List<User> users, Long ladderId) {
    List<Long> userIds =
        users == null
            ? List.of()
            : users.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

    Map<Long, Set<String>> namesByUser =
        (courtNameService != null && !userIds.isEmpty())
            ? courtNameService.gatherCourtNamesForUsers(userIds, ladderId)
            : Map.of();

    Map<Long, String> courtNameByUser = new LinkedHashMap<>();
    for (Long id : userIds) {
      Set<String> names = namesByUser.getOrDefault(id, Set.of());
      if (names != null && !names.isEmpty()) {
        courtNameByUser.put(id, names.iterator().next());
      }
    }
    return courtNameByUser;
  }

  private List<User> sortPlayersForPicker(List<User> users, Map<Long, String> courtNameByUser) {
    if (users == null || users.isEmpty()) {
      return Collections.emptyList();
    }
    Comparator<User> comparator =
        Comparator.comparing((User user) -> pickerSortPrimary(user, courtNameByUser))
            .thenComparing(this::sortKey)
            .thenComparing(
                user -> user != null && user.getId() != null ? user.getId() : Long.MAX_VALUE);
    return users.stream()
        .filter(Objects::nonNull)
        .distinct()
        .sorted(comparator)
        .collect(Collectors.toList());
  }

  private String pickerSortPrimary(User user, Map<Long, String> courtNameByUser) {
    if (user == null) {
      return "";
    }
    String courtName = user.getId() == null ? null : courtNameByUser.get(user.getId());
    String primary = StringUtils.hasText(courtName) ? courtName : playerDisplayName(user);
    return normalizePlayerLabelPart(primary);
  }

  private Set<Long> resolveRecentPlayerIds(List<User> users, Long ladderId, Long seasonId) {
    if (users == null || users.size() <= SEARCHABLE_PLAYER_PICKER_THRESHOLD) {
      return Collections.emptySet();
    }

    List<User> eligibleUsers =
        users.stream()
            .filter(Objects::nonNull)
            .filter(user -> user.getId() != null)
            .distinct()
            .collect(Collectors.toList());
    if (eligibleUsers.isEmpty()) {
      return Collections.emptySet();
    }

    Instant end = Instant.now();
    Instant start = end.minus(30, ChronoUnit.DAYS);
    List<Match> recentMatches;
    if (isSessionLadderId(ladderId)) {
      recentMatches =
          matchRepository.findRecentPlayedMatchesForPlayersInSession(
              ladderId, eligibleUsers, start, end);
    } else if (seasonId != null) {
      LadderSeason season = seasonRepository.findById(seasonId).orElse(null);
      recentMatches =
          season != null
              ? matchRepository.findRecentPlayedMatchesForPlayersInSeason(
                  season, eligibleUsers, start, end)
              : matchRepository.findRecentPlayedMatchesForPlayers(eligibleUsers, start, end);
    } else {
      recentMatches = matchRepository.findRecentPlayedMatchesForPlayers(eligibleUsers, start, end);
    }

    LinkedHashSet<Long> recentPlayerIds = new LinkedHashSet<>();
    for (Match match : recentMatches) {
      addRecentPlayerId(recentPlayerIds, match != null ? match.getA1() : null);
      addRecentPlayerId(recentPlayerIds, match != null ? match.getA2() : null);
      addRecentPlayerId(recentPlayerIds, match != null ? match.getB1() : null);
      addRecentPlayerId(recentPlayerIds, match != null ? match.getB2() : null);
      if (recentPlayerIds.size() >= RECENT_PLAYER_SECTION_LIMIT) {
        break;
      }
    }

    if (recentPlayerIds.size() > RECENT_PLAYER_SECTION_LIMIT) {
      return recentPlayerIds.stream()
          .limit(RECENT_PLAYER_SECTION_LIMIT)
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    return recentPlayerIds;
  }

  private void addRecentPlayerId(LinkedHashSet<Long> recentPlayerIds, User user) {
    if (recentPlayerIds == null || user == null || user.getId() == null) {
      return;
    }
    if (recentPlayerIds.size() < RECENT_PLAYER_SECTION_LIMIT) {
      recentPlayerIds.add(user.getId());
    }
  }

  private Map<String, Long> countNormalizedValues(Collection<String> values) {
    Map<String, Long> counts = new LinkedHashMap<>();
    if (values == null || values.isEmpty()) {
      return counts;
    }

    for (String value : values) {
      String normalized = normalizePlayerLabelPart(value);
      if (normalized.isEmpty()) {
        continue;
      }
      counts.merge(normalized, 1L, Long::sum);
    }
    return counts;
  }

  private String buildPlayerSecondaryLabel(
      String courtName,
      String displayName,
      String publicCode,
      Map<String, Long> courtNameCounts,
      Map<String, Long> displayNameCounts) {
    List<String> parts = new ArrayList<>();

    if (StringUtils.hasText(courtName)) {
      if (countNormalizedValue(courtNameCounts, courtName) <= 1L) {
        return "";
      }
      if (!sameNormalizedValue(courtName, displayName)) {
        parts.add(displayName);
      }
      if (StringUtils.hasText(publicCode)) {
        parts.add(publicCode);
      }
      return String.join(" - ", parts);
    }

    if (countNormalizedValue(displayNameCounts, displayName) > 1L
        && StringUtils.hasText(publicCode)) {
      return publicCode;
    }
    return "";
  }

  private long countNormalizedValue(Map<String, Long> counts, String value) {
    if (counts == null || counts.isEmpty()) {
      return 0L;
    }
    return counts.getOrDefault(normalizePlayerLabelPart(value), 0L);
  }

  private String buildPlayerSearchText(
      String courtName, String displayName, String publicCode, String optionLabel) {
    List<String> parts = new ArrayList<>();
    addSearchPart(parts, courtName);
    addSearchPart(parts, displayName);
    addSearchPart(parts, publicCode);
    addSearchPart(parts, optionLabel);
    return String.join(" ", parts);
  }

  private void addSearchPart(List<String> parts, String value) {
    if (!StringUtils.hasText(value)) {
      return;
    }
    String trimmed = value.trim();
    if (!parts.contains(trimmed)) {
      parts.add(trimmed);
    }
  }

  private String playerDisplayName(User user) {
    if (user == null) {
      return UserPublicName.FALLBACK;
    }
    return UserPublicName.forUser(user);
  }

  private boolean sameNormalizedValue(String left, String right) {
    return normalizePlayerLabelPart(left).equals(normalizePlayerLabelPart(right));
  }

  private String normalizePlayerLabelPart(String value) {
    return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
  }

  private String sortKey(User user) {
    return safeLower(playerDisplayName(user));
  }

  private String safeLower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private Set<Long> resolveActiveSeasonPlayerIds(Long seasonId) {
    // Picker order is now stable label-based order, so season activity no longer influences
    // sorting.
    return null;
  }

  private boolean isInactiveForSeason(
      boolean hasSeasonContext, Set<Long> activeSeasonPlayerIds, User user) {
    return hasSeasonContext
        && user != null
        && user.getId() != null
        && !activeSeasonPlayerIds.contains(user.getId());
  }

  private List<User> ensureMatchParticipantsPresent(List<User> users, Match match) {
    if (match == null) {
      return users;
    }
    List<User> mergedUsers = new ArrayList<>(users);
    Set<Long> userIds =
        mergedUsers.stream().map(User::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    addParticipantIfMissing(mergedUsers, userIds, match.getA1());
    addParticipantIfMissing(mergedUsers, userIds, match.getA2());
    addParticipantIfMissing(mergedUsers, userIds, match.getB1());
    addParticipantIfMissing(mergedUsers, userIds, match.getB2());
    return mergedUsers.stream()
        .distinct()
        .sorted(Comparator.comparing(this::sortKey))
        .collect(Collectors.toList());
  }

  private void addParticipantIfMissing(List<User> users, Set<Long> userIds, User participant) {
    if (participant == null
        || participant.getId() == null
        || userIds.contains(participant.getId())) {
      return;
    }
    users.add(participant);
    userIds.add(participant.getId());
  }

  private boolean isMatchParticipant(Match match, User currentUser) {
    if (match == null || currentUser == null || currentUser.getId() == null) {
      return false;
    }
    Long currentUserId = currentUser.getId();
    return Stream.of(match.getA1(), match.getA2(), match.getB1(), match.getB2())
        .filter(Objects::nonNull)
        .map(User::getId)
        .anyMatch(id -> Objects.equals(id, currentUserId));
  }

  private boolean isSessionLadderId(Long ladderId) {
    if (ladderId == null || ladderConfigRepository == null) {
      return false;
    }
    LadderConfig ladderConfig = ladderConfigRepository.findById(ladderId).orElse(null);
    return ladderConfig != null && ladderConfig.isSessionType();
  }

  private record PlayerPickerPresentation(
      Map<Long, String> primaryLabelByUser,
      Map<Long, String> secondaryLabelByUser,
      Map<Long, String> optionLabelByUser,
      Map<Long, String> searchTextByUser,
      Set<Long> recentPlayerIds) {}

  private record PlayerPickerModel(
      List<User> users,
      List<User> otherPlayers,
      Map<Long, String> primaryLabelByUser,
      Map<Long, String> secondaryLabelByUser,
      Map<Long, String> optionLabelByUser,
      Map<Long, String> searchTextByUser,
      Set<Long> recentPlayerIds,
      boolean useSearchablePlayerPicker) {}

  public record PlayerSelectionLists(List<User> users, List<User> otherPlayers) {}
}
