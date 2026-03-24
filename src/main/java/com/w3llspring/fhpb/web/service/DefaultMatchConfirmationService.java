package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.RoundRobinEntryRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.MatchWorkflowRules;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import com.w3llspring.fhpb.web.service.matchworkflow.OverdueMatchConfirmationProcessor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Service
public class DefaultMatchConfirmationService implements MatchConfirmationService {

  private static final Logger log = LoggerFactory.getLogger(DefaultMatchConfirmationService.class);
  private static final String REQUEST_PENDING_CACHE_KEY =
      DefaultMatchConfirmationService.class.getName() + ".pendingForUser";
  private static final List<String> TEAM_ORDER = List.of("A", "B");

  private final MatchConfirmationRepository confirmRepo;
  private final MatchRepository matchRepo;

  @SuppressWarnings("unused")
  private final RoundRobinEntryRepository roundRobinEntryRepository;

  private final UserRepository userRepo;

  @SuppressWarnings("unused")
  private final LadderAccessService ladderAccessService;

  private final MatchValidationService matchValidationService;
  private final com.w3llspring.fhpb.web.service.matchlog.LearningService learningService;
  private final OverdueMatchConfirmationProcessor overdueMatchConfirmationProcessor;
  private CompetitionAutoModerationService competitionAutoModerationService;

  @org.springframework.beans.factory.annotation.Autowired
  public DefaultMatchConfirmationService(
      MatchConfirmationRepository confirmRepo,
      MatchRepository matchRepo,
      RoundRobinEntryRepository roundRobinEntryRepository,
      UserRepository userRepo,
      LadderAccessService ladderAccessService,
      MatchValidationService matchValidationService,
      com.w3llspring.fhpb.web.service.matchlog.LearningService learningService,
      OverdueMatchConfirmationProcessor overdueMatchConfirmationProcessor,
      CompetitionAutoModerationService competitionAutoModerationService) {
    this.confirmRepo = confirmRepo;
    this.matchRepo = matchRepo;
    this.roundRobinEntryRepository = roundRobinEntryRepository;
    this.userRepo = userRepo;
    this.ladderAccessService = ladderAccessService;
    this.matchValidationService = matchValidationService;
    this.learningService = learningService;
    this.overdueMatchConfirmationProcessor = overdueMatchConfirmationProcessor;
    this.competitionAutoModerationService = competitionAutoModerationService;
  }

  public DefaultMatchConfirmationService(
      MatchConfirmationRepository confirmRepo,
      MatchRepository matchRepo,
      RoundRobinEntryRepository roundRobinEntryRepository,
      UserRepository userRepo,
      LadderAccessService ladderAccessService,
      MatchValidationService matchValidationService,
      com.w3llspring.fhpb.web.service.matchlog.LearningService learningService,
      OverdueMatchConfirmationProcessor overdueMatchConfirmationProcessor) {
    this(
        confirmRepo,
        matchRepo,
        roundRobinEntryRepository,
        userRepo,
        ladderAccessService,
        matchValidationService,
        learningService,
        overdueMatchConfirmationProcessor,
        null);
  }

  /** Backwards-compatible constructor used by tests that don't pass the overdue processor. */
  public DefaultMatchConfirmationService(
      MatchConfirmationRepository confirmRepo,
      MatchRepository matchRepo,
      RoundRobinEntryRepository roundRobinEntryRepository,
      UserRepository userRepo,
      LadderAccessService ladderAccessService,
      MatchValidationService matchValidationService,
      com.w3llspring.fhpb.web.service.matchlog.LearningService learningService) {
    this(
        confirmRepo,
        matchRepo,
        roundRobinEntryRepository,
        userRepo,
        ladderAccessService,
        matchValidationService,
        learningService,
        new OverdueMatchConfirmationProcessor(
            confirmRepo, matchRepo, matchValidationService, learningService));
  }

  /** Backwards-compatible constructor used by tests that don't pass LearningService. */
  public DefaultMatchConfirmationService(
      MatchConfirmationRepository confirmRepo,
      MatchRepository matchRepo,
      UserRepository userRepo,
      MatchValidationService matchValidationService) {
    this(
        confirmRepo,
        matchRepo,
        null,
        userRepo,
        null,
        matchValidationService,
        null,
        new OverdueMatchConfirmationProcessor(
            confirmRepo, matchRepo, matchValidationService, null));
  }

  @Override
  @Transactional
  public void rebuildConfirmationRequests(Match match) {
    if (match == null) {
      log.warn("rebuildConfirmationRequests called with null match");
      return;
    }

    log.info("Rebuilding confirmation requests for match {}", match.getId());
    try {
      confirmRepo.deleteByMatch(match);
    } catch (Exception ex) {
      log.warn(
          "Failed to clear confirmation rows for match {}: {}", match.getId(), ex.getMessage());
    }

    createRequests(match);
  }

  /** Periodic cleanup to prune old, unconfirmed AUTO rows to prevent unbounded growth. */
  @Transactional
  public void pruneOldConfirmationRows() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(90));
    try {
      int deleted = confirmRepo.deleteByCreatedAtBeforeAndConfirmedAtIsNull(cutoff);
      log.info("Pruned {} old confirmation rows older than {}", deleted, cutoff);
    } catch (Exception ex) {
      log.warn("Failed to prune old confirmation rows: {}", ex.getMessage());
    }
  }

  @Override
  @Transactional
  public void createRequests(Match match) {
    if (match == null || match.isConfirmationLocked()) {
      return;
    }
    if (match.getState() == MatchState.NULLIFIED
        || match.getState() == MatchState.CONFIRMED
        || match.getState() == MatchState.FLAGGED) {
      return;
    }

    List<MatchConfirmation> existing = loadConfirmations(match);
    Map<Long, MatchConfirmation> existingByPlayerId =
        existing.stream()
            .filter(this::hasPlayerId)
            .collect(
                Collectors.toMap(
                    mc -> mc.getPlayer().getId(),
                    mc -> mc,
                    (left, right) -> left,
                    LinkedHashMap::new));

    Set<Long> keepPlayerIds = new LinkedHashSet<>();
    LadderSecurity securityLevel = resolveSecurityLevel(match);

    if (LadderSecurity.normalize(securityLevel).isSelfConfirm()) {
      createSelfConfirmRequests(match, existingByPlayerId, keepPlayerIds);
    } else {
      createStandardRequests(match, existingByPlayerId, keepPlayerIds);
    }

    deleteStaleConfirmations(existing, keepPlayerIds);

    List<MatchConfirmation> refreshed = loadConfirmations(match);
    if (isReadyToConfirm(match, refreshed)) {
      markConfirmed(match);
    }
  }

  @Override
  @Transactional
  public boolean confirmMatch(long matchId, long userId) {
    return confirmMatch(matchId, userId, null);
  }

  @Override
  @Transactional
  public boolean confirmMatch(long matchId, long userId, Long expectedVersion) {
    log.info("Attempting to confirm match: matchId={}, userId={}", matchId, userId);

    Optional<Match> lockedMatch = matchRepo.findByIdWithUsersForUpdate(matchId);
    if (lockedMatch == null || lockedMatch.isEmpty()) {
      lockedMatch = matchRepo.findByIdWithUsers(matchId);
    }

    Match match = lockedMatch != null ? lockedMatch.orElse(null) : null;
    if (match == null) {
      throw new IllegalArgumentException("Match not found");
    }
    requireExpectedVersion(match, expectedVersion);

    User user = userRepo.findById(userId).orElse(null);
    if (user == null) {
      throw new IllegalArgumentException("User not found");
    }

    if (match.isConfirmationLocked()) {
      throw new IllegalStateException("Confirmation is locked for this match.");
    }
    if (match.getState() == MatchState.NULLIFIED) {
      throw new IllegalStateException("This match is no longer available for confirmation.");
    }
    if (match.getState() == MatchState.FLAGGED) {
      throw new IllegalStateException("This match is under review after a dispute.");
    }
    if (match.getState() == MatchState.CONFIRMED) {
      throw new IllegalStateException("This match has already been confirmed.");
    }
    requireCompetitionEligibility(user, match.getSeason());

    MatchValidationService.ScoreValidationResult scoreValidation;
    try {
      scoreValidation = matchValidationService.validateScore(match.getScoreA(), match.getScoreB());
    } catch (Exception ex) {
      log.warn("Score validation failed for match {}: {}", match.getId(), ex.getMessage());
      scoreValidation =
          MatchValidationService.ScoreValidationResult.invalid("Match score is not valid.");
    }
    if (!scoreValidation.isValid()) {
      throw new IllegalArgumentException(scoreValidation.getErrorMessage());
    }

    String team = teamForUser(match, userId);
    if (team == null) {
      throw new IllegalArgumentException("User not a participant in match");
    }

    List<MatchConfirmation> confirmations = new ArrayList<>(loadConfirmations(match));
    if (isReadyToConfirm(match, confirmations)) {
      markConfirmed(match);
      throw new IllegalStateException("This match has already been confirmed.");
    }
    if (teamHasConfirmed(confirmations, team)) {
      throw new IllegalStateException("Your team has already confirmed this match.");
    }

    MatchConfirmation target = findByPlayerId(confirmations, userId).orElse(null);
    if (target == null) {
      target = new MatchConfirmation();
      target.setMatch(match);
      target.setPlayer(user);
      confirmations.add(target);
    }

    target.setTeam(team);
    target.setMethod(MatchConfirmation.ConfirmationMethod.MANUAL);
    target.setConfirmedAt(Instant.now());
    target.setCasualModeAutoConfirmed(false);
    confirmRepo.save(target);

    removeOtherPendingRowsForTeam(confirmations, team, userId);

    if (isReadyToConfirm(match, confirmations)) {
      markConfirmed(match);
    }

    return true;
  }

  @Override
  @Transactional
  public void disputeMatch(long matchId, long userId, String note) {
    disputeMatch(matchId, userId, note, null);
  }

  @Override
  @Transactional
  public void disputeMatch(long matchId, long userId, String note, Long expectedVersion) {
    log.info("Attempting to dispute match: matchId={}, userId={}", matchId, userId);

    Optional<Match> lockedMatch = matchRepo.findByIdWithUsersForUpdate(matchId);
    if (lockedMatch == null || lockedMatch.isEmpty()) {
      lockedMatch = matchRepo.findByIdWithUsers(matchId);
    }

    Match match = lockedMatch != null ? lockedMatch.orElse(null) : null;
    if (match == null) {
      throw new IllegalArgumentException("Match not found");
    }
    requireExpectedVersion(match, expectedVersion);

    User user = userRepo.findById(userId).orElse(null);
    if (user == null) {
      throw new IllegalArgumentException("User not found");
    }

    if (match.isConfirmationLocked()) {
      throw new IllegalStateException("Confirmation is locked for this match.");
    }
    if (match.getState() == MatchState.NULLIFIED) {
      throw new IllegalStateException("This match is no longer available for dispute.");
    }
    if (match.getState() == MatchState.FLAGGED) {
      throw new IllegalStateException("This match has already been disputed and is under review.");
    }
    if (match.getState() == MatchState.CONFIRMED) {
      throw new IllegalStateException("This match has already been confirmed.");
    }
    requireCompetitionEligibility(user, match.getSeason());

    String team = teamForUser(match, userId);
    if (team == null) {
      throw new IllegalArgumentException("User not a participant in match");
    }

    List<MatchConfirmation> confirmations = new ArrayList<>(loadConfirmations(match));
    if (isReadyToConfirm(match, confirmations)) {
      markConfirmed(match);
      throw new IllegalStateException("This match has already been confirmed.");
    }
    if (teamHasConfirmed(confirmations, team)) {
      throw new IllegalStateException("Your team has already confirmed this match.");
    }

    markFlagged(match, user, note);
  }

  @SuppressWarnings("unchecked")
  private List<MatchConfirmation> getCachedPending(long userId) {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return null;
    }
    Object cacheObj =
        attrs.getAttribute(REQUEST_PENDING_CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
    if (!(cacheObj instanceof Map<?, ?> map)) {
      return null;
    }
    Object cached = map.get(userId);
    if (cached instanceof List<?>) {
      return (List<MatchConfirmation>) cached;
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private void cachePending(long userId, List<MatchConfirmation> pending) {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return;
    }
    Object cacheObj =
        attrs.getAttribute(REQUEST_PENDING_CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
    Map<Long, List<MatchConfirmation>> cache;
    if (cacheObj instanceof Map<?, ?> existing) {
      cache = (Map<Long, List<MatchConfirmation>>) existing;
    } else {
      cache = new ConcurrentHashMap<>();
      attrs.setAttribute(REQUEST_PENDING_CACHE_KEY, cache, RequestAttributes.SCOPE_REQUEST);
    }
    cache.put(userId, pending);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MatchConfirmation> pendingForUser(long userId) {
    List<MatchConfirmation> cached = getCachedPending(userId);
    if (cached != null) {
      return cached;
    }

    if (userId <= 0) {
      return List.of();
    }

    User user = userRepo.findById(userId).orElse(null);
    if (user == null) {
      return List.of();
    }

    List<Match> participantMatches = matchRepo.findByParticipantWithUsers(user);
    Set<Long> matchIds =
        participantMatches.stream()
            .map(Match::getId)
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    Map<Long, List<MatchConfirmation>> confirmationsByMatch =
        matchIds.isEmpty()
            ? Collections.emptyMap()
            : confirmRepo.findByMatchIdIn(matchIds).stream()
                .filter(mc -> mc.getMatch() != null && mc.getMatch().getId() != null)
                .collect(Collectors.groupingBy(mc -> mc.getMatch().getId()));

    List<MatchConfirmation> result = new ArrayList<>();
    Map<Long, Boolean> blockedBySeasonId = new LinkedHashMap<>();
    for (Match match : participantMatches) {
      if (match == null || match.getId() == null) {
        continue;
      }
      if (match.isConfirmationLocked()
          || match.getState() == MatchState.FLAGGED
          || match.getState() == MatchState.CONFIRMED
          || match.getState() == MatchState.NULLIFIED) {
        continue;
      }
      if (isBlockedFromCompetitionSeason(user, match.getSeason(), blockedBySeasonId)) {
        continue;
      }

      String team = teamForUser(match, userId);
      if (team == null) {
        continue;
      }

      List<MatchConfirmation> confirmations =
          confirmationsByMatch.getOrDefault(match.getId(), Collections.emptyList());
      if (isReadyToConfirm(match, confirmations) || teamHasConfirmed(confirmations, team)) {
        continue;
      }

      MatchConfirmation existing =
          findByPlayerId(confirmations, userId)
              .filter(mc -> mc.getConfirmedAt() == null)
              .orElse(null);
      if (existing != null) {
        result.add(existing);
        continue;
      }

      MatchConfirmation synthetic = new MatchConfirmation();
      synthetic.setMatch(match);
      synthetic.setPlayer(user);
      synthetic.setTeam(team);
      synthetic.setMethod(MatchConfirmation.ConfirmationMethod.AUTO);
      synthetic.setConfirmedAt(null);
      result.add(synthetic);
    }

    List<MatchConfirmation> immutableResult = List.copyOf(result);
    cachePending(userId, immutableResult);
    return immutableResult;
  }

  @Override
  public void autoConfirmOverdue() {
    Instant cutoff = Instant.now().minus(Duration.ofHours(48));
    List<Long> candidateIds = matchRepo.findPendingConfirmationCandidateIdsCreatedBefore(cutoff);
    for (Long matchId : candidateIds) {
      if (matchId == null) {
        continue;
      }
      try {
        overdueMatchConfirmationProcessor.processOverdueMatch(matchId, cutoff);
      } catch (OptimisticLockingFailureException ex) {
        log.info(
            "Skipping overdue confirmation maintenance for match {} due to concurrent update",
            matchId);
      } catch (RuntimeException ex) {
        log.warn(
            "Failed overdue confirmation maintenance for match {}: {}", matchId, ex.getMessage());
      }
    }
  }

  private void createStandardRequests(
      Match match, Map<Long, MatchConfirmation> existingByPlayerId, Set<Long> keepPlayerIds) {
    User representedUser = MatchWorkflowRules.representedUserForStandardFlow(match);
    String representedTeam = MatchWorkflowRules.teamForUser(match, representedUser);
    boolean editedParticipantRepresentation =
        MatchWorkflowRules.isEditedMatch(match) && representedTeam != null;
    for (String team : TEAM_ORDER) {
      List<User> participants = participantsForTeam(match, team);
      if (participants.isEmpty()) {
        continue;
      }

      if (team.equals(representedTeam)) {
        User confirmer = resolveRepresentedUser(match, team, participants, representedUser);
        if (confirmer != null) {
          saveConfirmation(
              existingByPlayerId,
              match,
              confirmer,
              team,
              editedParticipantRepresentation
                  ? MatchConfirmation.ConfirmationMethod.MANUAL
                  : MatchConfirmation.ConfirmationMethod.AUTO,
              editedParticipantRepresentation
                  ? (match.getEditedAt() != null ? match.getEditedAt() : Instant.now())
                  : Instant.now(),
              false);
          keepPlayerIds.add(confirmer.getId());
          continue;
        }
      }

      for (User participant : participants) {
        saveConfirmation(
            existingByPlayerId,
            match,
            participant,
            team,
            MatchConfirmation.ConfirmationMethod.AUTO,
            null,
            false);
        keepPlayerIds.add(participant.getId());
      }
    }
  }

  private void createSelfConfirmRequests(
      Match match, Map<Long, MatchConfirmation> existingByPlayerId, Set<Long> keepPlayerIds) {
    for (String team : TEAM_ORDER) {
      List<User> participants = participantsForTeam(match, team);
      if (participants.isEmpty()) {
        continue;
      }

      User autoConfirmer = chooseAutoConfirmer(match, team, participants);
      saveConfirmation(
          existingByPlayerId,
          match,
          autoConfirmer,
          team,
          MatchConfirmation.ConfirmationMethod.AUTO,
          Instant.now(),
          true);
      keepPlayerIds.add(autoConfirmer.getId());
    }
  }

  private void saveConfirmation(
      Map<Long, MatchConfirmation> existingByPlayerId,
      Match match,
      User player,
      String team,
      MatchConfirmation.ConfirmationMethod method,
      Instant confirmedAt,
      boolean casualModeAutoConfirmed) {
    if (player == null || player.getId() == null) {
      return;
    }

    MatchConfirmation confirmation = existingByPlayerId.get(player.getId());
    if (confirmation == null) {
      confirmation = new MatchConfirmation();
      confirmation.setMatch(match);
      confirmation.setPlayer(player);
      existingByPlayerId.put(player.getId(), confirmation);
    }

    confirmation.setTeam(team);
    confirmation.setMethod(method);
    confirmation.setConfirmedAt(confirmedAt);
    confirmation.setCasualModeAutoConfirmed(casualModeAutoConfirmed);
    confirmRepo.save(confirmation);
  }

  private void deleteStaleConfirmations(List<MatchConfirmation> existing, Set<Long> keepPlayerIds) {
    for (MatchConfirmation confirmation : existing) {
      Long playerId = confirmation.getPlayer() != null ? confirmation.getPlayer().getId() : null;
      if (playerId == null || !keepPlayerIds.contains(playerId)) {
        confirmRepo.delete(confirmation);
      }
    }
  }

  private void removeOtherPendingRowsForTeam(
      List<MatchConfirmation> confirmations, String team, long confirmedUserId) {
    List<MatchConfirmation> toDelete =
        confirmations.stream()
            .filter(mc -> team.equals(mc.getTeam()))
            .filter(mc -> hasPlayerId(mc) && !mc.getPlayer().getId().equals(confirmedUserId))
            .filter(mc -> mc.getConfirmedAt() == null)
            .collect(Collectors.toList());

    for (MatchConfirmation confirmation : toDelete) {
      confirmRepo.delete(confirmation);
      confirmations.remove(confirmation);
    }
  }

  private boolean isReadyToConfirm(Match match, List<MatchConfirmation> confirmations) {
    return teamSatisfied(match, confirmations, "A") && teamSatisfied(match, confirmations, "B");
  }

  private boolean teamSatisfied(Match match, List<MatchConfirmation> confirmations, String team) {
    return participantsForTeam(match, team).isEmpty() || teamHasConfirmed(confirmations, team);
  }

  private boolean teamHasConfirmed(List<MatchConfirmation> confirmations, String team) {
    return confirmations.stream()
        .anyMatch(mc -> team.equals(mc.getTeam()) && mc.getConfirmedAt() != null);
  }

  private void markConfirmed(Match match) {
    if (match == null || match.getState() == MatchState.CONFIRMED) {
      return;
    }

    match.setState(MatchState.CONFIRMED);
    matchRepo.save(match);
    log.info("Match {} transitioned to CONFIRMED", match.getId());

    if (learningService != null && match.isUserCorrected() && match.getId() != null) {
      try {
        learningService.applyCorrectionsForMatch(match.getId());
      } catch (Exception ex) {
        log.warn(
            "LearningService failed to apply corrections for match {}: {}",
            match.getId(),
            ex.getMessage());
      }
    }
  }

  private void markFlagged(Match match, User disputedBy, String note) {
    if (match == null) {
      return;
    }

    match.setState(MatchState.FLAGGED);
    match.setDisputedBy(disputedBy);
    match.setDisputedAt(Instant.now());
    match.setDisputeNote(note == null || note.isBlank() ? null : note.trim());
    matchRepo.save(match);
    log.info(
        "Match {} transitioned to FLAGGED by user {}",
        match.getId(),
        disputedBy != null ? disputedBy.getId() : null);
  }

  private void nullifyMatch(Match match, String reason) {
    try {
      confirmRepo.deleteByMatch(match);
    } catch (Exception ex) {
      log.warn(
          "Failed to delete confirmation rows for match {}: {}", match.getId(), ex.getMessage());
    }

    match.setState(MatchState.NULLIFIED);
    matchRepo.save(match);
    log.info("Marked match {} as NULLIFIED due to {}", match.getId(), reason);
  }

  private List<MatchConfirmation> loadConfirmations(Match match) {
    List<MatchConfirmation> confirmations = confirmRepo.findByMatch(match);
    return confirmations != null ? confirmations : List.of();
  }

  private Optional<MatchConfirmation> findByPlayerId(
      List<MatchConfirmation> confirmations, long userId) {
    return confirmations.stream()
        .filter(this::hasPlayerId)
        .filter(mc -> mc.getPlayer().getId().equals(userId))
        .findFirst();
  }

  private void requireExpectedVersion(Match match, Long expectedVersion) {
    String message =
        "This match was updated by someone else. Reload and review the latest version before continuing.";
    if (expectedVersion == null) {
      return;
    }
    if (match == null || match.getVersion() != expectedVersion.longValue()) {
      throw new OptimisticLockingFailureException(message);
    }
  }

  private boolean hasPlayerId(MatchConfirmation confirmation) {
    return confirmation != null
        && confirmation.getPlayer() != null
        && confirmation.getPlayer().getId() != null;
  }

  private boolean isEditedMatch(Match match) {
    return MatchWorkflowRules.isEditedMatch(match);
  }

  private String teamForUser(Match match, User user) {
    if (user == null || user.getId() == null) {
      return null;
    }
    return teamForUser(match, user.getId());
  }

  private String teamForUser(Match match, Long userId) {
    if (match == null || userId == null) {
      return null;
    }

    if (isSameUser(match.getA1(), userId) || isSameUser(match.getA2(), userId)) {
      return "A";
    }
    if (isSameUser(match.getB1(), userId) || isSameUser(match.getB2(), userId)) {
      return "B";
    }
    return null;
  }

  private boolean isSameUser(User candidate, Long userId) {
    return candidate != null && candidate.getId() != null && candidate.getId().equals(userId);
  }

  private List<User> participantsForTeam(Match match, String team) {
    Map<Long, User> participants = new LinkedHashMap<>();
    addParticipant(participants, "A".equals(team) ? match.getA1() : match.getB1());
    addParticipant(participants, "A".equals(team) ? match.getA2() : match.getB2());
    return new ArrayList<>(participants.values());
  }

  private void addParticipant(Map<Long, User> participants, User candidate) {
    if (candidate == null || candidate.getId() == null) {
      return;
    }
    participants.putIfAbsent(candidate.getId(), candidate);
  }

  private User chooseAutoConfirmer(Match match, String team, Collection<User> participants) {
    User logger = match.getLoggedBy();
    if (logger != null && logger.getId() != null && team.equals(teamForUser(match, logger))) {
      for (User participant : participants) {
        if (logger.getId().equals(participant.getId())) {
          return participant;
        }
      }
    }
    return participants.iterator().next();
  }

  private User resolveRepresentedUser(
      Match match, String team, Collection<User> participants, User representedUser) {
    if (participants == null || participants.isEmpty()) {
      return null;
    }
    if (representedUser != null
        && representedUser.getId() != null
        && team.equals(teamForUser(match, representedUser))) {
      for (User participant : participants) {
        if (participant != null
            && participant.getId() != null
            && participant.getId().equals(representedUser.getId())) {
          return participant;
        }
      }
      return null;
    }
    return chooseAutoConfirmer(match, team, participants);
  }

  private LadderSecurity resolveSecurityLevel(Match match) {
    if (match == null || match.getSeason() == null || match.getSeason().getLadderConfig() == null) {
      return LadderSecurity.STANDARD;
    }
    LadderSecurity securityLevel = match.getSeason().getLadderConfig().getSecurityLevel();
    return securityLevel != null ? securityLevel : LadderSecurity.STANDARD;
  }

  private void requireCompetitionEligibility(User user, LadderSeason season) {
    if (competitionAutoModerationService == null) {
      return;
    }
    competitionAutoModerationService.requireNotBlocked(user, season);
  }

  private boolean isBlockedFromCompetitionSeason(
      User user, LadderSeason season, Map<Long, Boolean> cache) {
    if (competitionAutoModerationService == null
        || user == null
        || user.getId() == null
        || season == null
        || season.getId() == null) {
      return false;
    }
    Boolean cached = cache.get(season.getId());
    if (cached != null) {
      return cached;
    }
    CompetitionAutoModerationService.AutoModerationStatus status =
        competitionAutoModerationService.statusForSeason(user, season);
    boolean blocked = status != null && status.isBlocked();
    cache.put(season.getId(), blocked);
    return blocked;
  }
}
