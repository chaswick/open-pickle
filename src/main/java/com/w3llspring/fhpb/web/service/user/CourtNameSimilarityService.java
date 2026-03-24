package com.w3llspring.fhpb.web.service.user;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserCourtNameRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserCourtName;
import com.w3llspring.fhpb.web.service.matchlog.DoubleMetaphone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service to detect phonetically similar court names that may cause confusion when logging matches.
 */
@Service
public class CourtNameSimilarityService {

  @Autowired private UserCourtNameRepository userCourtNameRepo;

  @Autowired private LadderMembershipRepository ladderMembershipRepo;

  @Autowired private UserRepository userRepo;

  private final DoubleMetaphone metaphone = new DoubleMetaphone();

  /**
   * Check if a court name is phonetically similar to other users' court names on the same ladder.
   *
   * @param user The user whose court name to check
   * @param courtName The court name to check
   * @param ladderConfig The ladder config (null for universal/all ladders)
   * @return Optional containing similarity warning if a similar name is found
   */
  public Optional<SimilarityWarning> checkSimilarity(
      User user, String courtName, LadderConfig ladderConfig) {
    if (user == null || user.getId() == null || !StringUtils.hasText(courtName)) {
      return Optional.empty();
    }

    ComparisonContext context = buildComparisonContext(user);
    if (context.isEmpty()) {
      return Optional.empty();
    }

    return findSimilarity(courtName, ladderConfig, context, new HashMap<>());
  }

  /** Check if two phonetic encodings are equal. Compares both primary and alternate encodings. */
  private boolean arePhoneticallyEqual(String[] phonetic1, String[] phonetic2) {
    if (phonetic1 == null || phonetic2 == null) {
      return false;
    }

    String primary1 = phonetic1[0];
    String alternate1 = phonetic1.length > 1 ? phonetic1[1] : "";
    String primary2 = phonetic2[0];
    String alternate2 = phonetic2.length > 1 ? phonetic2[1] : "";

    // Check if primary codes match
    if (StringUtils.hasText(primary1) && primary1.equals(primary2)) {
      return true;
    }

    // Check if primary of one matches alternate of other
    if (StringUtils.hasText(primary1)
        && StringUtils.hasText(alternate2)
        && primary1.equals(alternate2)) {
      return true;
    }

    if (StringUtils.hasText(alternate1)
        && StringUtils.hasText(primary2)
        && alternate1.equals(primary2)) {
      return true;
    }

    // Check if alternates match (if both have them)
    if (StringUtils.hasText(alternate1)
        && StringUtils.hasText(alternate2)
        && alternate1.equals(alternate2)) {
      return true;
    }

    return false;
  }

  /**
   * Batch check similarities for multiple court names. Returns a map of court name ID to similarity
   * warning.
   */
  public Map<Long, SimilarityWarning> checkSimilarities(User user, List<UserCourtName> courtNames) {
    if (user == null || user.getId() == null || courtNames == null || courtNames.isEmpty()) {
      return Collections.emptyMap();
    }

    ComparisonContext context = buildComparisonContext(user);
    if (context.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<Long, SimilarityWarning> warnings = new HashMap<>();
    Map<String, String[]> phoneticCache = new HashMap<>();

    for (UserCourtName courtName : courtNames) {
      if (courtName == null
          || courtName.getId() == null
          || !StringUtils.hasText(courtName.getAlias())) {
        continue;
      }

      Optional<SimilarityWarning> warning =
          findSimilarity(courtName.getAlias(), courtName.getLadderConfig(), context, phoneticCache);

      warning.ifPresent(w -> warnings.put(courtName.getId(), w));
    }

    return warnings;
  }

  private Optional<SimilarityWarning> findSimilarity(
      String courtName,
      LadderConfig ladderConfig,
      ComparisonContext context,
      Map<String, String[]> phoneticCache) {
    if (!StringUtils.hasText(courtName) || context.isEmpty()) {
      return Optional.empty();
    }

    String normalizedCourtName = courtName.trim();
    String[] thisPhonetic = phoneticFor(normalizedCourtName, phoneticCache);

    List<Long> ladderIdsToCheck =
        ladderConfig == null
            ? context.getOrderedLadderIds()
            : context.containsLadder(ladderConfig.getId())
                ? List.of(ladderConfig.getId())
                : List.of();

    for (Long ladderId : ladderIdsToCheck) {
      for (CandidateCourtName candidate : context.getCandidatesFor(ladderId)) {
        String otherName = candidate.getCourtName();
        if (!StringUtils.hasText(otherName) || normalizedCourtName.equalsIgnoreCase(otherName)) {
          continue;
        }

        if (arePhoneticallyEqual(thisPhonetic, phoneticFor(otherName, phoneticCache))) {
          return Optional.of(
              new SimilarityWarning(
                  candidate.getOtherUserName(), otherName, candidate.getLadderTitle()));
        }
      }
    }

    return Optional.empty();
  }

  private String[] phoneticFor(String name, Map<String, String[]> phoneticCache) {
    return phoneticCache.computeIfAbsent(name, metaphone::doubleMetaphone);
  }

  private ComparisonContext buildComparisonContext(User user) {
    List<LadderMembership> activeMemberships =
        ladderMembershipRepo.findByUserIdAndState(user.getId(), LadderMembership.State.ACTIVE);
    if (activeMemberships.isEmpty()) {
      return ComparisonContext.empty();
    }

    Map<Long, LadderConfig> laddersById =
        activeMemberships.stream()
            .map(LadderMembership::getLadderConfig)
            .filter(Objects::nonNull)
            .filter(cfg -> cfg.getId() != null)
            .collect(
                Collectors.toMap(
                    LadderConfig::getId, cfg -> cfg, (left, right) -> left, LinkedHashMap::new));
    if (laddersById.isEmpty()) {
      return ComparisonContext.empty();
    }

    Map<Long, List<Long>> memberIdsByLadderId = new LinkedHashMap<>();
    Set<Long> otherUserIds = new LinkedHashSet<>();
    for (Long ladderId : laddersById.keySet()) {
      List<Long> memberIds =
          ladderMembershipRepo
              .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                  ladderId, LadderMembership.State.ACTIVE)
              .stream()
              .map(LadderMembership::getUserId)
              .filter(Objects::nonNull)
              .filter(memberId -> !memberId.equals(user.getId()))
              .distinct()
              .collect(Collectors.toList());
      if (memberIds.isEmpty()) {
        continue;
      }
      memberIdsByLadderId.put(ladderId, memberIds);
      otherUserIds.addAll(memberIds);
    }
    if (memberIdsByLadderId.isEmpty() || otherUserIds.isEmpty()) {
      return ComparisonContext.empty();
    }

    Map<Long, User> usersById =
        StreamSupport.stream(userRepo.findAllById(otherUserIds).spliterator(), false)
            .filter(Objects::nonNull)
            .filter(otherUser -> otherUser.getId() != null)
            .collect(
                Collectors.toMap(
                    User::getId,
                    otherUser -> otherUser,
                    (left, right) -> left,
                    LinkedHashMap::new));
    if (usersById.isEmpty()) {
      return ComparisonContext.empty();
    }

    Map<Long, List<UserCourtName>> globalCourtNamesByUserId =
        groupCourtNamesByUserId(
            userCourtNameRepo.findByUser_IdInAndLadderConfigIsNull(otherUserIds));

    Map<Long, List<CandidateCourtName>> candidatesByLadderId = new LinkedHashMap<>();
    for (Map.Entry<Long, LadderConfig> ladderEntry : laddersById.entrySet()) {
      Long ladderId = ladderEntry.getKey();
      LadderConfig ladder = ladderEntry.getValue();
      List<Long> memberIds = memberIdsByLadderId.getOrDefault(ladderId, Collections.emptyList());
      if (memberIds.isEmpty()) {
        continue;
      }

      Map<Long, List<UserCourtName>> ladderSpecificCourtNamesByUserId =
          groupCourtNamesByUserId(
              userCourtNameRepo.findByUser_IdInAndLadderConfig_Id(memberIds, ladderId));

      List<CandidateCourtName> candidates = new ArrayList<>();
      for (Long memberId : memberIds) {
        User otherUser = usersById.get(memberId);
        if (otherUser == null) {
          continue;
        }
        addCandidates(candidates, globalCourtNamesByUserId.get(memberId), otherUser, ladder);
        addCandidates(
            candidates, ladderSpecificCourtNamesByUserId.get(memberId), otherUser, ladder);
      }

      if (!candidates.isEmpty()) {
        candidatesByLadderId.put(ladderId, candidates);
      }
    }

    return new ComparisonContext(new ArrayList<>(laddersById.keySet()), candidatesByLadderId);
  }

  private Map<Long, List<UserCourtName>> groupCourtNamesByUserId(List<UserCourtName> courtNames) {
    if (courtNames == null || courtNames.isEmpty()) {
      return Collections.emptyMap();
    }

    return courtNames.stream()
        .filter(Objects::nonNull)
        .filter(courtName -> courtName.getUser() != null && courtName.getUser().getId() != null)
        .collect(Collectors.groupingBy(courtName -> courtName.getUser().getId()));
  }

  private void addCandidates(
      List<CandidateCourtName> candidates,
      List<UserCourtName> courtNames,
      User otherUser,
      LadderConfig ladder) {
    if (courtNames == null || courtNames.isEmpty()) {
      return;
    }

    for (UserCourtName courtName : courtNames) {
      if (courtName == null || !StringUtils.hasText(courtName.getAlias())) {
        continue;
      }
      candidates.add(
          new CandidateCourtName(
              otherUser.getNickName(),
              courtName.getAlias(),
              ladder != null ? ladder.getTitle() : null));
    }
  }

  private static final class ComparisonContext {
    private final List<Long> orderedLadderIds;
    private final Map<Long, List<CandidateCourtName>> candidatesByLadderId;

    private ComparisonContext(
        List<Long> orderedLadderIds, Map<Long, List<CandidateCourtName>> candidatesByLadderId) {
      this.orderedLadderIds = orderedLadderIds;
      this.candidatesByLadderId = candidatesByLadderId;
    }

    private static ComparisonContext empty() {
      return new ComparisonContext(List.of(), Collections.emptyMap());
    }

    private boolean isEmpty() {
      return orderedLadderIds.isEmpty() || candidatesByLadderId.isEmpty();
    }

    private List<Long> getOrderedLadderIds() {
      return orderedLadderIds;
    }

    private boolean containsLadder(Long ladderId) {
      return ladderId != null && orderedLadderIds.contains(ladderId);
    }

    private List<CandidateCourtName> getCandidatesFor(Long ladderId) {
      return candidatesByLadderId.getOrDefault(ladderId, Collections.emptyList());
    }
  }

  private static final class CandidateCourtName {
    private final String otherUserName;
    private final String courtName;
    private final String ladderTitle;

    private CandidateCourtName(String otherUserName, String courtName, String ladderTitle) {
      this.otherUserName = otherUserName;
      this.courtName = courtName;
      this.ladderTitle = ladderTitle;
    }

    private String getOtherUserName() {
      return otherUserName;
    }

    private String getCourtName() {
      return courtName;
    }

    private String getLadderTitle() {
      return ladderTitle;
    }
  }

  /** Represents a warning about a similar court name. */
  public static class SimilarityWarning {
    private final String otherUserName;
    private final String otherCourtName;
    private final String ladderTitle;

    public SimilarityWarning(String otherUserName, String otherCourtName, String ladderTitle) {
      this.otherUserName = otherUserName;
      this.otherCourtName = otherCourtName;
      this.ladderTitle = ladderTitle;
    }

    public String getOtherUserName() {
      return otherUserName;
    }

    public String getOtherCourtName() {
      return otherCourtName;
    }

    public String getLadderTitle() {
      return ladderTitle;
    }

    public String formatWarning() {
      if (StringUtils.hasText(ladderTitle)) {
        return String.format(
            "This name sounds similar to \"%s\" (used by %s on %s), which may make it harder for others to log matches including you.",
            otherCourtName, otherUserName, ladderTitle);
      } else {
        return String.format(
            "This name sounds similar to \"%s\" (used by %s), which may make it harder for others to log matches including you.",
            otherCourtName, otherUserName);
      }
    }
  }
}
