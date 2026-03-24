package com.w3llspring.fhpb.web.service.matchlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.w3llspring.fhpb.web.db.InterpretationEventRepository;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.NameCorrectionRepository;
import com.w3llspring.fhpb.web.db.ScoreCorrectionRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.InterpretationEvent;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionAutoModerationService;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.LadderSecurityService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchFactory;
import com.w3llspring.fhpb.web.service.MatchLoggingQuotaService;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.MatchValidationRequest;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.MatchValidationResult;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.PlayerSlot;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.ScoreValidationResult;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

public class VoiceMatchLogWorkflowService {

  private static final Logger log = LoggerFactory.getLogger(VoiceMatchLogWorkflowService.class);
  private static final int MAX_VOICE_TRANSCRIPT_CHARS = 300;

  private final SpokenMatchInterpreter defaultInterpreter;
  private final SpokenMatchInterpreter spanishInterpreter;
  private final SpokenMatchInterpreter learningInterpreter;
  private final UserRepository userRepository;
  private final LadderSeasonRepository seasonRepository;
  private final LadderV2Service ladderV2Service;
  private final TrophyAwardService trophyAwardService;
  private final MatchValidationService matchValidationService;
  private final LadderSecurityService ladderSecurityService;
  private final LadderAccessService ladderAccessService;
  private final MatchFactory matchFactory;
  private final MatchRepository matchRepository;
  private final MatchConfirmationRepository matchConfirmationRepository;
  private final NameCorrectionRepository nameCorrectionRepository;
  private final InterpretationEventRepository interpretationEventRepository;
  private final InterpretationEventWriter interpretationEventWriter;
  private final ScoreCorrectionRepository scoreCorrectionRepository;
  private final MatchLoggingQuotaService matchLoggingQuotaService;
  private final LadderConfigRepository ladderConfigRepository;
  private final LadderMembershipRepository ladderMembershipRepository;
  private final CompetitionAutoModerationService competitionAutoModerationService;

  public VoiceMatchLogWorkflowService(
      SpokenMatchInterpreter defaultInterpreter,
      SpokenMatchInterpreter spanishInterpreter,
      SpokenMatchInterpreter learningInterpreter,
      UserRepository userRepository,
      LadderSeasonRepository seasonRepository,
      LadderV2Service ladderV2Service,
      TrophyAwardService trophyAwardService,
      MatchValidationService matchValidationService,
      LadderSecurityService ladderSecurityService,
      LadderAccessService ladderAccessService,
      MatchFactory matchFactory,
      MatchRepository matchRepository,
      MatchConfirmationRepository matchConfirmationRepository,
      NameCorrectionRepository nameCorrectionRepository,
      InterpretationEventRepository interpretationEventRepository,
      InterpretationEventWriter interpretationEventWriter,
      ScoreCorrectionRepository scoreCorrectionRepository,
      MatchLoggingQuotaService matchLoggingQuotaService,
      LadderConfigRepository ladderConfigRepository,
      LadderMembershipRepository ladderMembershipRepository,
      CompetitionAutoModerationService competitionAutoModerationService) {
    this.defaultInterpreter = defaultInterpreter;
    this.spanishInterpreter = spanishInterpreter;
    this.learningInterpreter = learningInterpreter;
    this.userRepository = userRepository;
    this.seasonRepository = seasonRepository;
    this.ladderV2Service = ladderV2Service;
    this.trophyAwardService = trophyAwardService;
    this.matchValidationService = matchValidationService;
    this.ladderSecurityService = ladderSecurityService;
    this.ladderAccessService = ladderAccessService;
    this.matchFactory = matchFactory;
    this.matchRepository = matchRepository;
    this.matchConfirmationRepository = matchConfirmationRepository;
    this.nameCorrectionRepository = nameCorrectionRepository;
    this.interpretationEventRepository = interpretationEventRepository;
    this.interpretationEventWriter = interpretationEventWriter;
    this.scoreCorrectionRepository = scoreCorrectionRepository;
    this.matchLoggingQuotaService = matchLoggingQuotaService;
    this.ladderConfigRepository = ladderConfigRepository;
    this.ladderMembershipRepository = ladderMembershipRepository;
    this.competitionAutoModerationService = competitionAutoModerationService;
  }

  public VoiceInterpretResult interpret(VoiceInterpretCommand command, Authentication auth) {
    VoiceInterpretCommand normalizedCommand = normalizeInterpretCommand(command);
    if (log.isDebugEnabled()) {
      log.debug(
          "Interpret request received: transcript='{}', ladderConfigId={}, seasonId={}, language={}",
          normalizedCommand.transcript(),
          normalizedCommand.ladderConfigId(),
          normalizedCommand.seasonId(),
          normalizedCommand.language());
    }

    SpokenMatchInterpretationRequest svcRequest = new SpokenMatchInterpretationRequest();
    svcRequest.setTranscript(normalizedCommand.transcript());
    svcRequest.setLadderConfigId(normalizedCommand.ladderConfigId());
    svcRequest.setSeasonId(normalizedCommand.seasonId());
    svcRequest.setCurrentUserId(resolveCurrentUserId(auth));

    SpokenMatchInterpreter interpreter = resolveInterpreter(normalizedCommand.language());
    SpokenMatchInterpretation interpretation = interpreter.interpret(svcRequest);

    if (log.isDebugEnabled()) {
      try {
        log.debug(
            "Interpretation result: winningTeamIndex={} teamsCount={} transcript='{}'",
            interpretation != null ? interpretation.getWinningTeamIndex() : null,
            interpretation != null && interpretation.getTeams() != null
                ? interpretation.getTeams().size()
                : 0,
            interpretation != null ? interpretation.getTranscript() : null);
      } catch (Exception e) {
        log.debug("Interpretation result (toString): {}", interpretation, e);
      }
    }

    try {
      Long matchId = autoSubmitMatch(interpretation, normalizedCommand, auth);
      return new VoiceInterpretResult(interpretation, true, matchId);
    } catch (Exception e) {
      try {
        if (log.isWarnEnabled()) {
          log.warn(
              "Auto-submit failed for transcript='{}' ladderConfigId={} seasonId={} interpretationSummary={}: {}",
              normalizedCommand.transcript(),
              normalizedCommand.ladderConfigId(),
              normalizedCommand.seasonId(),
              interpretation != null
                  ? String.format(
                      "winner=%s teams=%d",
                      interpretation.getWinningTeamIndex(),
                      interpretation.getTeams() != null ? interpretation.getTeams().size() : 0)
                  : "null",
              e.toString());
        }
      } catch (Exception logEx) {
        log.warn("Auto-submit failed and secondary logging also failed", logEx);
      }
      return new VoiceInterpretResult(interpretation, false, null);
    }
  }

  public Long confirm(VoiceConfirmCommand command, Authentication auth) {
    User currentUser = requireUser(auth);
    VoiceConfirmCommand normalizedCommand = normalizeConfirmCommand(command);

    if (matchLoggingQuotaService != null) {
      MatchLoggingQuotaService.QuotaStatus quota = matchLoggingQuotaService.evaluate(currentUser);
      if (!quota.allowed()) {
        throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,
            "Weekly logging limit reached ("
                + quota.limit()
                + " matches in 7 days). Please try again later.");
      }
    }

    if (normalizedCommand.confidenceScore() != null && normalizedCommand.confidenceScore() < 30) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Match interpretation confidence is too low ("
              + normalizedCommand.confidenceScore()
              + "%). "
              + "Please try again with clearer details about the players and score.");
    }

    ScoreValidationResult scoreValidation =
        matchValidationService.validateScore(
            normalizedCommand.scoreTeamA(), normalizedCommand.scoreTeamB());
    if (!scoreValidation.isValid()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, scoreValidation.getErrorMessage());
    }

    validateTeams(normalizedCommand);

    LadderSeason season = resolveSeason(normalizedCommand);
    requireCompetitionEligibility(currentUser, season);
    Set<Long> eligibleMemberIds = resolveEligibleMemberIds(normalizedCommand, season);

    Long teamA1Id =
        !normalizedCommand.teamAUserIds().isEmpty()
            ? normalizedCommand.teamAUserIds().get(0)
            : null;
    Long teamA2Id =
        normalizedCommand.teamAUserIds().size() > 1
            ? normalizedCommand.teamAUserIds().get(1)
            : null;
    Long teamB1Id =
        !normalizedCommand.teamBUserIds().isEmpty()
            ? normalizedCommand.teamBUserIds().get(0)
            : null;
    Long teamB2Id =
        normalizedCommand.teamBUserIds().size() > 1
            ? normalizedCommand.teamBUserIds().get(1)
            : null;

    Long currentUserId = currentUser.getId();
    String reporterSlotName = null;
    if (Objects.equals(teamA1Id, currentUserId)) {
      reporterSlotName = "A1";
    } else if (Objects.equals(teamA2Id, currentUserId)) {
      reporterSlotName = "A2";
    } else if (Objects.equals(teamB1Id, currentUserId)) {
      reporterSlotName = "B1";
    } else if (Objects.equals(teamB2Id, currentUserId)) {
      reporterSlotName = "B2";
    }

    MatchValidationRequest validationRequest = new MatchValidationRequest();
    validationRequest.setSeason(season);
    validationRequest.setEligibleMemberIds(eligibleMemberIds);
    validationRequest.setRequireOpponentMember(!isGuestOnlyPersonalRecordAllowed(season));
    validationRequest.setReporterSlot(
        PlayerSlot.builder(reporterSlotName != null ? reporterSlotName : "A1")
            .userId(reporterSlotName != null ? currentUserId : teamA1Id)
            .guest(reporterSlotName != null ? false : (teamA1Id == null))
            .guestAllowed(reporterSlotName != null ? false : true)
            .requireMemberCheck(true)
            .includeInDuplicateCheck(false)
            .build());
    validationRequest.setPartnerSlot(
        PlayerSlot.builder("A2")
            .userId(teamA2Id)
            .guest(teamA2Id == null)
            .guestAllowed(true)
            .requireMemberCheck(true)
            .guestSuggestion("leave A2 as Guest")
            .build());
    validationRequest.setOpponentOneSlot(
        PlayerSlot.builder("B1")
            .userId(teamB1Id)
            .guest(teamB1Id == null)
            .guestAllowed(true)
            .requireMemberCheck(true)
            .guestSuggestion("set B1 as Guest")
            .build());
    validationRequest.setOpponentTwoSlot(
        PlayerSlot.builder("B2")
            .userId(teamB2Id)
            .guest(teamB2Id == null)
            .guestAllowed(true)
            .requireMemberCheck(true)
            .guestSuggestion("set B2 as Guest")
            .build());

    MatchValidationResult validationResult = matchValidationService.validate(validationRequest);
    if (!validationResult.isValid()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, String.join("; ", validationResult.getErrors()));
    }

    if (season.getLadderConfig() != null && eligibleMemberIds != null) {
      if (currentUser.getId() == null || !eligibleMemberIds.contains(currentUser.getId())) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "You must be a member of this ladder to submit matches for it.");
      }
    }

    boolean isAdmin =
        season.getId() != null && ladderAccessService.isSeasonAdmin(season.getId(), currentUser);
    if (!isAdmin) {
      List<Long> combined =
          Stream.concat(
                  normalizedCommand.teamAUserIds().stream(),
                  normalizedCommand.teamBUserIds().stream())
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
      Long curId = currentUser.getId();
      boolean isParticipant = curId != null && combined.contains(curId);
      if (!isParticipant) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "You can only log matches that you participated in.");
      }
    }

    List<Long> allUserIds = new ArrayList<>();
    allUserIds.addAll(normalizedCommand.teamAUserIds());
    allUserIds.addAll(normalizedCommand.teamBUserIds());
    Map<Long, User> users =
        userRepository.findAllById(allUserIds).stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(User::getId, u -> u));

    verifyAllPlayersPresent(allUserIds, users);

    Match match = new Match();
    match.setSeason(season);
    match.setSourceSessionConfig(resolveSourceSessionConfig(normalizedCommand));
    match.setPlayedAt(resolvePlayedAt(normalizedCommand));
    match.setScoreA(normalizedCommand.scoreTeamA());
    match.setScoreB(normalizedCommand.scoreTeamB());
    match.setState(MatchState.PROVISIONAL);

    assignTeam(
        match::setA1,
        match::setA2,
        match::setA1Guest,
        match::setA2Guest,
        normalizedCommand.teamAUserIds(),
        users);
    assignTeam(
        match::setB1,
        match::setB2,
        match::setB1Guest,
        match::setB2Guest,
        normalizedCommand.teamBUserIds(),
        users);

    match.setCosignedBy(null);
    match.setTranscript(normalizedCommand.transcript());
    match.setConfidenceScore(normalizedCommand.confidenceScore());
    match.setScoreEstimated(Boolean.TRUE.equals(normalizedCommand.scoreEstimated()));
    match.setVerificationNotes(normalizedCommand.verificationNotes());
    match.setLoggedBy(currentUser);

    Match saved = matchFactory.createMatch(match);
    ladderV2Service.applyMatch(saved);
    trophyAwardService.evaluateMatch(saved);

    try {
      SpokenMatchInterpretationRequest svcRequest = new SpokenMatchInterpretationRequest();
      svcRequest.setTranscript(normalizedCommand.transcript());
      svcRequest.setLadderConfigId(normalizedCommand.ladderConfigId());
      svcRequest.setSeasonId(normalizedCommand.seasonId());
      svcRequest.setCurrentUserId(currentUser.getId());
      SpokenMatchInterpreter interpreter =
          resolveInterpreter(normalizedCommand.verificationNotes());
      SpokenMatchInterpretation interpretation = null;
      try {
        interpretation = interpreter.interpret(svcRequest);
      } catch (Exception e) {
        log.debug("Interpreter failed during confirm recording: {}", e.getMessage());
      }
      recordInterpretationEventAndCorrections(
          interpretation,
          saved.getId(),
          normalizedCommand.ladderConfigId(),
          currentUser != null ? currentUser.getId() : null);
    } catch (Exception e) {
      log.warn("Failed to record interpretation event after confirm: {}", e.getMessage());
    }

    return saved.getId();
  }

  public void recordInterpretationEventAndCorrections(
      SpokenMatchInterpretation interpretation,
      Long matchId,
      Long ladderConfigId,
      Long currentUserId) {
    if (interpretation == null) {
      return;
    }
    if ((interpretationEventRepository == null && interpretationEventWriter == null)
        || nameCorrectionRepository == null) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Learning pipeline disabled: interpretationEventRepository={} interpretationEventWriter={} nameCorrectionRepository={} - interpretation summary='{}'",
            interpretationEventRepository == null ? "MISSING" : "present",
            interpretationEventWriter == null ? "MISSING" : "present",
            nameCorrectionRepository == null ? "MISSING" : "present",
            interpretation.getTranscript());
      }
      return;
    }

    try {
      InterpretationEvent event = new InterpretationEvent();
      event.setEventUuid(UUID.randomUUID().toString());
      event.setCreatedAt(Instant.now());
      event.setLadderConfigId(ladderConfigId);
      event.setMatchId(matchId);
      event.setCurrentUserId(currentUserId);
      event.setAutoSubmitted(true);
      event.setTranscript(interpretation.getTranscript());
      try {
        ObjectMapper objectMapper = new ObjectMapper();
        event.setInterpretationJson(objectMapper.writeValueAsString(interpretation));
      } catch (Exception ignored) {
      }
      if (interpretationEventWriter != null) {
        interpretationEventWriter.write(event);
      } else {
        interpretationEventRepository.save(event);
      }
      if (log.isInfoEnabled()) {
        log.info(
            "Saved InterpretationEvent for match {}: eventUuid={} ladderId={} autoSubmitted={} transcriptPreview='{}'",
            matchId,
            event.getEventUuid(),
            event.getLadderConfigId(),
            event.getAutoSubmitted(),
            event.getTranscript() != null && event.getTranscript().length() > 120
                ? event.getTranscript().substring(0, 120) + "..."
                : event.getTranscript());
      }
    } catch (Exception e) {
      log.warn("Failed to record interpretation event/corrections: {}", e.getMessage());
    }
  }

  private VoiceInterpretCommand normalizeInterpretCommand(VoiceInterpretCommand command) {
    if (command == null) {
      return new VoiceInterpretCommand(null, null, null, null);
    }
    return new VoiceInterpretCommand(
        sanitizeTranscript(command.transcript()),
        command.ladderConfigId(),
        command.seasonId(),
        command.language());
  }

  private VoiceConfirmCommand normalizeConfirmCommand(VoiceConfirmCommand command) {
    if (command == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Match data is missing or incomplete. Please try submitting again.");
    }
    return new VoiceConfirmCommand(
        command.seasonId(),
        command.ladderConfigId(),
        command.teamAUserIds() != null ? command.teamAUserIds() : new ArrayList<>(),
        command.teamBUserIds() != null ? command.teamBUserIds() : new ArrayList<>(),
        command.scoreTeamA(),
        command.scoreTeamB(),
        command.playedAtEpochMillis(),
        sanitizeTranscript(command.transcript()),
        command.confidenceScore(),
        command.scoreEstimated(),
        command.verificationNotes());
  }

  private SpokenMatchInterpreter resolveInterpreter(String language) {
    if (language == null) {
      return learningInterpreter != null ? learningInterpreter : defaultInterpreter;
    }
    String normalized = language.trim().toLowerCase();
    if ("es".equals(normalized) || "español".equals(normalized) || "spanish".equals(normalized)) {
      return spanishInterpreter;
    }
    return defaultInterpreter;
  }

  private void assignTeam(
      Consumer<User> primarySetter,
      Consumer<User> secondarySetter,
      Consumer<Boolean> primaryGuestSetter,
      Consumer<Boolean> secondaryGuestSetter,
      List<Long> userIds,
      Map<Long, User> users) {
    User primary = userIds.isEmpty() ? null : users.get(userIds.get(0));
    User secondary = userIds.size() > 1 ? users.get(userIds.get(1)) : null;
    primarySetter.accept(primary);
    secondarySetter.accept(secondary);
    primaryGuestSetter.accept(primary == null);
    secondaryGuestSetter.accept(secondary == null);
  }

  private LadderSeason resolveSeason(VoiceConfirmCommand command) {
    if (command.seasonId() != null) {
      return seasonRepository
          .findById(command.seasonId())
          .orElseThrow(
              () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Season not found."));
    }

    if (command.ladderConfigId() != null) {
      return seasonRepository
          .findTopByLadderConfigIdAndStateOrderByStartedAtDesc(
              command.ladderConfigId(), LadderSeason.State.ACTIVE)
          .orElseThrow(
              () ->
                  new ResponseStatusException(
                      HttpStatus.BAD_REQUEST,
                      "No active season for ladder " + command.ladderConfigId() + "."));
    }

    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Season or ladder information is missing. Please access the match log form from your ladder's page to ensure matches are logged to the correct season.");
  }

  private Set<Long> resolveEligibleMemberIds(VoiceConfirmCommand command, LadderSeason season) {
    if (command.ladderConfigId() != null && ladderConfigRepository != null) {
      LadderConfig ladderConfig =
          ladderConfigRepository.findById(command.ladderConfigId()).orElse(null);
      if (ladderConfig != null
          && ladderConfig.isSessionType()
          && ladderMembershipRepository != null) {
        Set<Long> sessionMemberIds =
            ladderMembershipRepository
                .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                    ladderConfig.getId(), LadderMembership.State.ACTIVE)
                .stream()
                .map(LadderMembership::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return filterCompetitionEligibleMembers(season, sessionMemberIds);
      }
    }
    return season != null && season.getId() != null
        ? matchValidationService.resolveEligibleMemberUserIdsForSeason(season.getId())
        : null;
  }

  private Set<Long> filterCompetitionEligibleMembers(LadderSeason season, Set<Long> userIds) {
    if (competitionAutoModerationService == null) {
      return userIds;
    }
    return competitionAutoModerationService.filterEligibleUserIds(season, userIds);
  }

  private void requireCompetitionEligibility(User user, LadderSeason season) {
    if (competitionAutoModerationService == null) {
      return;
    }
    try {
      competitionAutoModerationService.requireNotBlocked(user, season);
    } catch (SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
    }
  }

  private LadderConfig resolveSourceSessionConfig(VoiceConfirmCommand command) {
    if (command.ladderConfigId() == null || ladderConfigRepository == null) {
      return null;
    }
    LadderConfig ladderConfig =
        ladderConfigRepository.findById(command.ladderConfigId()).orElse(null);
    if (ladderConfig == null || !ladderConfig.isSessionType()) {
      return null;
    }
    return ladderConfig;
  }

  private void verifyAllPlayersPresent(List<Long> requestedUserIds, Map<Long, User> loadedUsers) {
    List<Long> missing =
        requestedUserIds.stream()
            .filter(Objects::nonNull)
            .filter(id -> !loadedUsers.containsKey(id))
            .collect(Collectors.toList());
    if (!missing.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "One or more selected players could not be found. Please refresh the page and try again.");
    }
  }

  private Instant resolvePlayedAt(VoiceConfirmCommand command) {
    if (command.playedAtEpochMillis() != null) {
      return Instant.ofEpochMilli(command.playedAtEpochMillis());
    }
    return Instant.now();
  }

  private String sanitizeTranscript(String transcript) {
    if (transcript == null) {
      return null;
    }
    String trimmed = transcript.trim();
    if (trimmed.length() <= MAX_VOICE_TRANSCRIPT_CHARS) {
      return trimmed;
    }
    return trimmed.substring(0, MAX_VOICE_TRANSCRIPT_CHARS);
  }

  private Long resolveCurrentUserId(Authentication auth) {
    User currentUser = AuthenticatedUserSupport.currentUser(auth);
    return currentUser != null ? currentUser.getId() : null;
  }

  private User requireUser(Authentication auth) {
    User currentUser = AuthenticatedUserSupport.currentUser(auth);
    if (currentUser != null && currentUser.getId() != null) {
      return currentUser;
    }
    throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "Your login session has expired. Please log in again to submit the match.");
  }

  private void validateTeams(VoiceConfirmCommand command) {
    if (command.teamAUserIds().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Team 1 needs at least one player. Please select a player for Team 1.");
    }
    if (command.teamBUserIds().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Team 2 needs at least one player. Please select a player for Team 2.");
    }
    if (command.teamAUserIds().size() > 2 || command.teamBUserIds().size() > 2) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Each team can only have up to 2 players. Please select 1-2 players per team.");
    }

    boolean teamAHasUser = command.teamAUserIds().stream().anyMatch(Objects::nonNull);
    boolean teamBHasUser = command.teamBUserIds().stream().anyMatch(Objects::nonNull);
    if (!teamAHasUser && !teamBHasUser) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "At least one team must have a registered user. Please select a user for Team 1 or Team 2.");
    }

    Set<Long> seen = new HashSet<>();
    Set<Long> duplicates = new HashSet<>();
    command.teamAUserIds().stream()
        .filter(Objects::nonNull)
        .forEach(
            id -> {
              if (!seen.add(id)) {
                duplicates.add(id);
              }
            });
    command.teamBUserIds().stream()
        .filter(Objects::nonNull)
        .forEach(
            id -> {
              if (!seen.add(id)) {
                duplicates.add(id);
              }
            });
    if (!duplicates.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "A player cannot play on both teams or multiple times. Please ensure each player is selected only once.");
    }
  }

  private boolean isGuestOnlyPersonalRecordAllowed(LadderSeason season) {
    if (season == null || season.getLadderConfig() == null) {
      return false;
    }
    LadderConfig config = season.getLadderConfig();
    return config.isAllowGuestOnlyPersonalMatches()
        && config.getSecurityLevel() != null
        && com.w3llspring.fhpb.web.model.LadderSecurity.normalize(config.getSecurityLevel())
            .isSelfConfirm();
  }

  private Long autoSubmitMatch(
      SpokenMatchInterpretation interpretation,
      VoiceInterpretCommand command,
      Authentication auth) {
    VoiceConfirmCommand confirmCommand =
        new VoiceConfirmCommand(
            command.seasonId(),
            command.ladderConfigId(),
            extractTeamUserIds(interpretation, 0),
            extractTeamUserIds(interpretation, 1),
            null,
            null,
            null,
            interpretation != null ? interpretation.getTranscript() : null,
            null,
            null,
            null);

    boolean scoreEstimated = applyScoreDefaulting(interpretation, confirmCommand);
    VoiceConfirmCommand finalizedCommand =
        new VoiceConfirmCommand(
            confirmCommand.seasonId(),
            confirmCommand.ladderConfigId(),
            confirmCommand.teamAUserIds(),
            confirmCommand.teamBUserIds(),
            confirmCommand.scoreTeamA(),
            confirmCommand.scoreTeamB(),
            confirmCommand.playedAtEpochMillis(),
            confirmCommand.transcript(),
            computeOverallConfidence(interpretation, scoreEstimated),
            scoreEstimated,
            buildVerificationNotes(interpretation, scoreEstimated));

    if (log.isDebugEnabled()) {
      log.debug(
          "Attempting auto-submit with VoiceConfirmCommand: ladderConfigId={} seasonId={} teamAUsers={} teamBUsers={} confidence={} scoreEstimated={} notes={}",
          finalizedCommand.ladderConfigId(),
          finalizedCommand.seasonId(),
          finalizedCommand.teamAUserIds(),
          finalizedCommand.teamBUserIds(),
          finalizedCommand.confidenceScore(),
          finalizedCommand.scoreEstimated(),
          finalizedCommand.verificationNotes());
    }
    if (log.isInfoEnabled() && interpretation != null) {
      try {
        StringBuilder sb = new StringBuilder();
        int teamIndex = 0;
        for (SpokenMatchInterpretation.Team team : interpretation.getTeams()) {
          int playerIndex = 0;
          for (SpokenMatchInterpretation.PlayerResolution player : team.getPlayers()) {
            sb.append(
                String.format(
                    "team=%d player=%d token='%s' matchedUser=%s alias=%s conf=%.2f; ",
                    teamIndex,
                    playerIndex,
                    player.getToken(),
                    player.getMatchedUserId(),
                    player.getMatchedAlias(),
                    player.getConfidence()));
            playerIndex++;
          }
          teamIndex++;
        }
        log.info("Interpretation -> Confirm mapping: {}", sb.toString());
      } catch (Exception ex) {
        log.debug("Failed to stringify interpretation mapping for logging", ex);
      }
    }

    Long matchId = confirm(finalizedCommand, auth);

    try {
      if (matchId != null && matchRepository != null && matchConfirmationRepository != null) {
        Match match = matchRepository.findById(matchId).orElse(null);
        if (match != null) {
          if (log.isInfoEnabled()) {
            log.info(
                "Auto-submitted match {} created: A1={} A2={} B1={} B2={} scoreA={} scoreB={} transcript='{}'",
                matchId,
                match.getA1() != null ? match.getA1().getId() : null,
                match.getA2() != null ? match.getA2().getId() : null,
                match.getB1() != null ? match.getB1().getId() : null,
                match.getB2() != null ? match.getB2().getId() : null,
                match.getScoreA(),
                match.getScoreB(),
                match.getTranscript());
          }
          List<MatchConfirmation> existing = matchConfirmationRepository.findByMatch(match);
          if (existing == null || existing.isEmpty()) {
            User logger = match.getLoggedBy();
            if (logger != null && logger.getId() != null) {
              MatchConfirmation confirmation = new MatchConfirmation();
              confirmation.setMatch(match);
              confirmation.setPlayer(logger);
              boolean onA =
                  (match.getA1() != null && Objects.equals(match.getA1().getId(), logger.getId()))
                      || (match.getA2() != null
                          && Objects.equals(match.getA2().getId(), logger.getId()));
              confirmation.setTeam(onA ? "A" : "B");
              confirmation.setMethod(MatchConfirmation.ConfirmationMethod.AUTO);
              confirmation.setConfirmedAt(null);
              matchConfirmationRepository.save(confirmation);
              if (log.isDebugEnabled()) {
                log.debug(
                    "Created pending confirmation for logging user {} on match {}",
                    logger.getId(),
                    matchId);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn(
          "Failed to create follow-up confirmation for logging user on auto-submitted match {}: {}",
          matchId,
          e.getMessage());
    }

    return matchId;
  }

  private List<Long> extractTeamUserIds(SpokenMatchInterpretation interpretation, int teamIndex) {
    List<Long> userIds = new ArrayList<>();
    if (interpretation == null || interpretation.getTeams().size() <= teamIndex) {
      return userIds;
    }
    SpokenMatchInterpretation.Team team = interpretation.getTeams().get(teamIndex);
    for (SpokenMatchInterpretation.PlayerResolution player : team.getPlayers()) {
      userIds.add(player.getMatchedUserId());
    }
    return userIds;
  }

  private boolean applyScoreDefaulting(
      SpokenMatchInterpretation interpretation, VoiceConfirmCommand confirmCommand) {
    Integer scoreA = interpretation.getScoreTeamA();
    Integer scoreB = interpretation.getScoreTeamB();
    Integer winningTeamIndex = interpretation.getWinningTeamIndex();

    if (scoreA != null
        && scoreB != null
        && scoreA >= 0
        && scoreB >= 0
        && !Objects.equals(scoreA, scoreB)) {
      confirmCommand.setScoreTeamA(scoreA);
      confirmCommand.setScoreTeamB(scoreB);
      return false;
    }

    if (winningTeamIndex != null) {
      if (winningTeamIndex == 0) {
        confirmCommand.setScoreTeamA(11);
        confirmCommand.setScoreTeamB(9);
      } else {
        confirmCommand.setScoreTeamA(9);
        confirmCommand.setScoreTeamB(11);
      }
      return true;
    }

    confirmCommand.setScoreTeamA(scoreA);
    confirmCommand.setScoreTeamB(scoreB);
    return false;
  }

  private Integer computeOverallConfidence(
      SpokenMatchInterpretation interpretation, boolean scoreEstimated) {
    if (interpretation == null || interpretation.getTeams().isEmpty()) {
      return null;
    }

    double totalConfidence = 0.0;
    int playerCount = 0;
    for (SpokenMatchInterpretation.Team team : interpretation.getTeams()) {
      for (SpokenMatchInterpretation.PlayerResolution player : team.getPlayers()) {
        totalConfidence += player.getConfidence();
        playerCount++;
      }
    }
    if (playerCount == 0) {
      return null;
    }

    double avgPlayerConfidence = totalConfidence / playerCount;
    double scoreConfidence = scoreEstimated ? 0.65 : 1.0;
    double overallConfidence = (avgPlayerConfidence * 0.70) + (scoreConfidence * 0.30);
    return (int) Math.round(overallConfidence * 100);
  }

  private String buildVerificationNotes(
      SpokenMatchInterpretation interpretation, boolean scoreEstimated) {
    List<String> notes = new ArrayList<>();
    if (scoreEstimated) {
      notes.add("Score estimated as 11-9 - exact score could not be determined");
    }

    if (interpretation != null && !interpretation.getWarnings().isEmpty()) {
      for (String warning : interpretation.getWarnings()) {
        notes.add(enhanceWarningMessage(warning));
      }
    }

    if (interpretation != null) {
      for (SpokenMatchInterpretation.Team team : interpretation.getTeams()) {
        for (SpokenMatchInterpretation.PlayerResolution player : team.getPlayers()) {
          if (player.isNeedsReview()) {
            String playerNote = buildPlayerResolutionNote(player);
            if (playerNote != null) {
              notes.add(playerNote);
            }
          }
        }
      }
    }

    return notes.isEmpty() ? null : String.join("; ", notes);
  }

  private String enhanceWarningMessage(String warning) {
    if (warning == null || warning.isBlank()) {
      return warning;
    }

    String lower = warning.toLowerCase();
    if (lower.contains("could not find")) {
      return warning + " - might not be registered yet or using a different name";
    }
    if (lower.contains("audio") || lower.contains("unclear") || lower.contains("quality")) {
      return warning + " - please verify details";
    }
    return warning;
  }

  private String buildPlayerResolutionNote(SpokenMatchInterpretation.PlayerResolution player) {
    if (player.getMatchedUserId() == null) {
      return String.format(
          "'%s' logged as guest - no registered user found",
          player.getToken() != null ? player.getToken() : "player");
    }
    if (player.getConfidence() < 0.7) {
      return String.format(
          "Low confidence match for '%s' - please verify",
          player.getToken() != null ? player.getToken() : "player");
    }
    return null;
  }

  public record VoiceInterpretCommand(
      String transcript, Long ladderConfigId, Long seasonId, String language) {}

  public record VoiceInterpretResult(
      SpokenMatchInterpretation interpretation, boolean autoSubmitted, Long matchId) {}

  public static final class VoiceConfirmCommand {
    private final Long seasonId;
    private final Long ladderConfigId;
    private final List<Long> teamAUserIds;
    private final List<Long> teamBUserIds;
    private Integer scoreTeamA;
    private Integer scoreTeamB;
    private final Long playedAtEpochMillis;
    private final String transcript;
    private final Integer confidenceScore;
    private final Boolean scoreEstimated;
    private final String verificationNotes;

    public VoiceConfirmCommand(
        Long seasonId,
        Long ladderConfigId,
        List<Long> teamAUserIds,
        List<Long> teamBUserIds,
        Integer scoreTeamA,
        Integer scoreTeamB,
        Long playedAtEpochMillis,
        String transcript,
        Integer confidenceScore,
        Boolean scoreEstimated,
        String verificationNotes) {
      this.seasonId = seasonId;
      this.ladderConfigId = ladderConfigId;
      this.teamAUserIds = teamAUserIds;
      this.teamBUserIds = teamBUserIds;
      this.scoreTeamA = scoreTeamA;
      this.scoreTeamB = scoreTeamB;
      this.playedAtEpochMillis = playedAtEpochMillis;
      this.transcript = transcript;
      this.confidenceScore = confidenceScore;
      this.scoreEstimated = scoreEstimated;
      this.verificationNotes = verificationNotes;
    }

    public Long seasonId() {
      return seasonId;
    }

    public Long ladderConfigId() {
      return ladderConfigId;
    }

    public List<Long> teamAUserIds() {
      return teamAUserIds;
    }

    public List<Long> teamBUserIds() {
      return teamBUserIds;
    }

    public Integer scoreTeamA() {
      return scoreTeamA;
    }

    public void setScoreTeamA(Integer scoreTeamA) {
      this.scoreTeamA = scoreTeamA;
    }

    public Integer scoreTeamB() {
      return scoreTeamB;
    }

    public void setScoreTeamB(Integer scoreTeamB) {
      this.scoreTeamB = scoreTeamB;
    }

    public Long playedAtEpochMillis() {
      return playedAtEpochMillis;
    }

    public String transcript() {
      return transcript;
    }

    public Integer confidenceScore() {
      return confidenceScore;
    }

    public Boolean scoreEstimated() {
      return scoreEstimated;
    }

    public String verificationNotes() {
      return verificationNotes;
    }
  }
}
