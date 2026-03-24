package com.w3llspring.fhpb.web.controller.match;

import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.service.CompetitionAutoModerationService;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.LadderSecurityService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchFactory;
import com.w3llspring.fhpb.web.service.MatchLoggingQuotaService;
import com.w3llspring.fhpb.web.service.matchlog.InterpretationEventWriter;
import com.w3llspring.fhpb.web.service.matchlog.LearningService;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpretation;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpreter;
import com.w3llspring.fhpb.web.service.matchlog.VoiceMatchLogWorkflowService;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voice-match-log")
public class VoiceMatchLogController {

  private static final Logger log = LoggerFactory.getLogger(VoiceMatchLogController.class);
  // Backend guardrail for voice payload size; silently truncates oversized transcripts.
  private static final int MAX_VOICE_TRANSCRIPT_CHARS = 300;

  private final SpokenMatchInterpreter defaultInterpreter;
  private final SpokenMatchInterpreter spanishInterpreter;
  private SpokenMatchInterpreter learningInterpreter;
  private final UserRepository userRepository;
  // per-user passphrase support removed
  private final LadderSeasonRepository seasonRepository;
  private final LadderV2Service ladderV2Service;
  private final TrophyAwardService trophyAwardService;
  private final MatchValidationService matchValidationService;
  private final LadderSecurityService ladderSecurityService;
  private final LadderAccessService ladderAccessService;
  private final MatchFactory matchFactory;
  private final MatchRepository matchRepository;
  private final MatchConfirmationRepository matchConfirmationRepository;
  private final LearningService learningService;
  private com.w3llspring.fhpb.web.db.NameCorrectionRepository nameCorrectionRepository;
  private com.w3llspring.fhpb.web.db.InterpretationEventRepository interpretationEventRepository;
  private InterpretationEventWriter interpretationEventWriter;
  private com.w3llspring.fhpb.web.db.ScoreCorrectionRepository scoreCorrectionRepository;
  private MatchLoggingQuotaService matchLoggingQuotaService;
  private com.w3llspring.fhpb.web.db.LadderConfigRepository ladderConfigRepository;
  private com.w3llspring.fhpb.web.db.LadderMembershipRepository ladderMembershipRepository;
  private CompetitionAutoModerationService competitionAutoModerationService;

  @Autowired
  public VoiceMatchLogController(
      @Qualifier("defaultSpokenMatchInterpreter") SpokenMatchInterpreter defaultInterpreter,
      @Qualifier("spanishSpokenMatchInterpreter") SpokenMatchInterpreter spanishInterpreter,
      @Qualifier("learningSpokenMatchInterpreter") SpokenMatchInterpreter learningInterpreter,
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
      LearningService learningService,
      com.w3llspring.fhpb.web.db.NameCorrectionRepository nameCorrectionRepository,
      com.w3llspring.fhpb.web.db.InterpretationEventRepository interpretationEventRepository,
      InterpretationEventWriter interpretationEventWriter,
      com.w3llspring.fhpb.web.db.ScoreCorrectionRepository scoreCorrectionRepository,
      MatchLoggingQuotaService matchLoggingQuotaService,
      com.w3llspring.fhpb.web.db.LadderConfigRepository ladderConfigRepository,
      com.w3llspring.fhpb.web.db.LadderMembershipRepository ladderMembershipRepository,
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
    this.learningService = learningService;
    this.nameCorrectionRepository = nameCorrectionRepository;
    this.interpretationEventRepository = interpretationEventRepository;
    this.interpretationEventWriter = interpretationEventWriter;
    this.scoreCorrectionRepository = scoreCorrectionRepository;
    this.matchLoggingQuotaService = matchLoggingQuotaService;
    this.ladderConfigRepository = ladderConfigRepository;
    this.ladderMembershipRepository = ladderMembershipRepository;
    this.competitionAutoModerationService = competitionAutoModerationService;
  }

  /**
   * Backwards-compatible constructor used by some tests and callers that don't pass repositories.
   */
  public VoiceMatchLogController(
      @Qualifier("defaultSpokenMatchInterpreter") SpokenMatchInterpreter defaultInterpreter,
      @Qualifier("spanishSpokenMatchInterpreter") SpokenMatchInterpreter spanishInterpreter,
      UserRepository userRepository,
      LadderSeasonRepository seasonRepository,
      LadderV2Service ladderV2Service,
      TrophyAwardService trophyAwardService,
      MatchValidationService matchValidationService,
      LadderSecurityService ladderSecurityService,
      LadderAccessService ladderAccessService,
      MatchFactory matchFactory) {
    this(
        defaultInterpreter,
        spanishInterpreter,
        null,
        userRepository,
        seasonRepository,
        ladderV2Service,
        trophyAwardService,
        matchValidationService,
        ladderSecurityService,
        ladderAccessService,
        matchFactory,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * Backwards-compatible constructor used by some tests that supply repositories but not the
   * LearningService. Preserves older test signatures.
   */
  public VoiceMatchLogController(
      @Qualifier("defaultSpokenMatchInterpreter") SpokenMatchInterpreter defaultInterpreter,
      @Qualifier("spanishSpokenMatchInterpreter") SpokenMatchInterpreter spanishInterpreter,
      UserRepository userRepository,
      LadderSeasonRepository seasonRepository,
      LadderV2Service ladderV2Service,
      TrophyAwardService trophyAwardService,
      MatchValidationService matchValidationService,
      LadderSecurityService ladderSecurityService,
      LadderAccessService ladderAccessService,
      MatchFactory matchFactory,
      MatchRepository matchRepository,
      MatchConfirmationRepository matchConfirmationRepository) {
    this(
        defaultInterpreter,
        spanishInterpreter,
        null,
        userRepository,
        seasonRepository,
        ladderV2Service,
        trophyAwardService,
        matchValidationService,
        ladderSecurityService,
        ladderAccessService,
        matchFactory,
        matchRepository,
        matchConfirmationRepository,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @PostMapping("/interpret")
  public InterpretResponse interpret(@RequestBody InterpretRequest request, Authentication auth) {
    VoiceMatchLogWorkflowService.VoiceInterpretResult result =
        workflowService().interpret(toInterpretCommand(request), auth);
    InterpretResponse response = new InterpretResponse();
    response.setInterpretation(result.autoSubmitted() ? null : result.interpretation());
    response.setAutoSubmitted(result.autoSubmitted());
    response.setMatchId(result.matchId());
    return response;
  }

  @PostMapping("/confirm")
  public ConfirmResponse confirm(@RequestBody ConfirmRequest request, Authentication auth) {
    return new ConfirmResponse(workflowService().confirm(toConfirmCommand(request), auth));
  }

  private VoiceMatchLogWorkflowService workflowService() {
    return new VoiceMatchLogWorkflowService(
        defaultInterpreter,
        spanishInterpreter,
        learningInterpreter,
        userRepository,
        seasonRepository,
        ladderV2Service,
        trophyAwardService,
        matchValidationService,
        ladderSecurityService,
        ladderAccessService,
        matchFactory,
        matchRepository,
        matchConfirmationRepository,
        nameCorrectionRepository,
        interpretationEventRepository,
        interpretationEventWriter,
        scoreCorrectionRepository,
        matchLoggingQuotaService,
        ladderConfigRepository,
        ladderMembershipRepository,
        competitionAutoModerationService);
  }

  private VoiceMatchLogWorkflowService.VoiceInterpretCommand toInterpretCommand(
      InterpretRequest request) {
    if (request == null) {
      return new VoiceMatchLogWorkflowService.VoiceInterpretCommand(null, null, null, null);
    }
    return new VoiceMatchLogWorkflowService.VoiceInterpretCommand(
        request.getTranscript(),
        request.getLadderConfigId(),
        request.getSeasonId(),
        request.getLanguage());
  }

  private VoiceMatchLogWorkflowService.VoiceConfirmCommand toConfirmCommand(
      ConfirmRequest request) {
    if (request == null) {
      return null;
    }
    return new VoiceMatchLogWorkflowService.VoiceConfirmCommand(
        request.getSeasonId(),
        request.getLadderConfigId(),
        request.getTeamAUserIds() != null
            ? new ArrayList<>(request.getTeamAUserIds())
            : new ArrayList<>(),
        request.getTeamBUserIds() != null
            ? new ArrayList<>(request.getTeamBUserIds())
            : new ArrayList<>(),
        request.getScoreTeamA(),
        request.getScoreTeamB(),
        request.getPlayedAtEpochMillis(),
        request.getTranscript(),
        request.getConfidenceScore(),
        request.getScoreEstimated(),
        request.getVerificationNotes());
  }

  // Kept as a controller-local seam for legacy learning tests that still invoke it reflectively.
  private void recordInterpretationEventAndCorrections(
      SpokenMatchInterpretation interpretation,
      Long matchId,
      Long ladderConfigId,
      Long currentUserId) {
    workflowService()
        .recordInterpretationEventAndCorrections(
            interpretation, matchId, ladderConfigId, currentUserId);
  }

  public static class InterpretRequest {
    private String transcript;
    private Long ladderConfigId;
    private Long seasonId;
    private String language;

    public String getTranscript() {
      return transcript;
    }

    public void setTranscript(String transcript) {
      this.transcript = transcript;
    }

    public Long getLadderConfigId() {
      return ladderConfigId;
    }

    public void setLadderConfigId(Long ladderConfigId) {
      this.ladderConfigId = ladderConfigId;
    }

    public Long getSeasonId() {
      return seasonId;
    }

    public void setSeasonId(Long seasonId) {
      this.seasonId = seasonId;
    }

    public String getLanguage() {
      return language;
    }

    public void setLanguage(String language) {
      this.language = language;
    }
  }

  /**
   * Response wrapper for interpret endpoint. Can either contain interpretation data for review, or
   * indicate auto-submission occurred.
   */
  public static class InterpretResponse {
    private SpokenMatchInterpretation interpretation;
    private boolean autoSubmitted;
    private Long matchId;

    public SpokenMatchInterpretation getInterpretation() {
      return interpretation;
    }

    public void setInterpretation(SpokenMatchInterpretation interpretation) {
      this.interpretation = interpretation;
    }

    public boolean isAutoSubmitted() {
      return autoSubmitted;
    }

    public void setAutoSubmitted(boolean autoSubmitted) {
      this.autoSubmitted = autoSubmitted;
    }

    public Long getMatchId() {
      return matchId;
    }

    public void setMatchId(Long matchId) {
      this.matchId = matchId;
    }
  }

  public static class ConfirmRequest {
    private Long seasonId;
    private Long ladderConfigId;
    private List<Long> teamAUserIds = new ArrayList<>();
    private List<Long> teamBUserIds = new ArrayList<>();
    private Integer scoreTeamA;
    private Integer scoreTeamB;
    private Long playedAtEpochMillis;

    // Metadata for ML training and debugging
    private String transcript;
    private Integer confidenceScore;
    private Boolean scoreEstimated;
    private String verificationNotes;

    public Long getSeasonId() {
      return seasonId;
    }

    public void setSeasonId(Long seasonId) {
      this.seasonId = seasonId;
    }

    public Long getLadderConfigId() {
      return ladderConfigId;
    }

    public void setLadderConfigId(Long ladderConfigId) {
      this.ladderConfigId = ladderConfigId;
    }

    public List<Long> getTeamAUserIds() {
      return teamAUserIds;
    }

    public void setTeamAUserIds(List<Long> teamAUserIds) {
      this.teamAUserIds = (teamAUserIds != null) ? teamAUserIds : new ArrayList<>();
    }

    public List<Long> getTeamBUserIds() {
      return teamBUserIds;
    }

    public void setTeamBUserIds(List<Long> teamBUserIds) {
      this.teamBUserIds = (teamBUserIds != null) ? teamBUserIds : new ArrayList<>();
    }

    public Integer getScoreTeamA() {
      return scoreTeamA;
    }

    public void setScoreTeamA(Integer scoreTeamA) {
      this.scoreTeamA = scoreTeamA;
    }

    public Integer getScoreTeamB() {
      return scoreTeamB;
    }

    public void setScoreTeamB(Integer scoreTeamB) {
      this.scoreTeamB = scoreTeamB;
    }

    public Long getPlayedAtEpochMillis() {
      return playedAtEpochMillis;
    }

    public void setPlayedAtEpochMillis(Long playedAtEpochMillis) {
      this.playedAtEpochMillis = playedAtEpochMillis;
    }

    public String getTranscript() {
      return transcript;
    }

    public void setTranscript(String transcript) {
      this.transcript = transcript;
    }

    public Integer getConfidenceScore() {
      return confidenceScore;
    }

    public void setConfidenceScore(Integer confidenceScore) {
      this.confidenceScore = confidenceScore;
    }

    public Boolean getScoreEstimated() {
      return scoreEstimated;
    }

    public void setScoreEstimated(Boolean scoreEstimated) {
      this.scoreEstimated = scoreEstimated;
    }

    public String getVerificationNotes() {
      return verificationNotes;
    }

    public void setVerificationNotes(String verificationNotes) {
      this.verificationNotes = verificationNotes;
    }
  }

  public static class ConfirmResponse {
    private Long matchId;

    ConfirmResponse(Long matchId) {
      this.matchId = matchId;
    }

    public Long getMatchId() {
      return matchId;
    }

    public void setMatchId(Long matchId) {
      this.matchId = matchId;
    }
  }
}
