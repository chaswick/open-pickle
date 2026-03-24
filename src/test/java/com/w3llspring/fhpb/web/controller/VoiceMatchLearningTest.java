package com.w3llspring.fhpb.web.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.w3llspring.fhpb.web.controller.match.VoiceMatchLogController;
import com.w3llspring.fhpb.web.db.InterpretationEventRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.NameCorrectionRepository;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.MatchFactory;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpretation;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpretationRequest;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpreter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class VoiceMatchLearningTest {

  private VoiceMatchLogController controller;
  private SpokenMatchInterpreter interpreter;
  private NameCorrectionRepository nameCorrectionRepository;
  private InterpretationEventRepository interpretationEventRepository;
  private com.w3llspring.fhpb.web.db.ScoreCorrectionRepository scoreCorrectionRepository;
  private MatchFactory matchFactory;
  private MatchRepository matchRepository;
  private com.w3llspring.fhpb.web.service.matchlog.MatchValidationService matchValidationService;
  private com.w3llspring.fhpb.web.db.LadderSeasonRepository seasonRepo;

  @BeforeEach
  void setup() {
    // minimal mocks for required constructor args
    interpreter = mock(SpokenMatchInterpreter.class);
    SpokenMatchInterpreter spanish = mock(SpokenMatchInterpreter.class);
    com.w3llspring.fhpb.web.db.UserRepository userRepo =
        mock(com.w3llspring.fhpb.web.db.UserRepository.class);
    seasonRepo = mock(com.w3llspring.fhpb.web.db.LadderSeasonRepository.class);
    com.w3llspring.fhpb.web.service.LadderV2Service lv2 =
        mock(com.w3llspring.fhpb.web.service.LadderV2Service.class);
    com.w3llspring.fhpb.web.service.trophy.TrophyAwardService tas =
        mock(com.w3llspring.fhpb.web.service.trophy.TrophyAwardService.class);
    matchValidationService =
        mock(com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.class);
    com.w3llspring.fhpb.web.service.matchlog.MatchValidationService mvs = matchValidationService;
    com.w3llspring.fhpb.web.service.LadderSecurityService lss =
        mock(com.w3llspring.fhpb.web.service.LadderSecurityService.class);
    com.w3llspring.fhpb.web.service.LadderAccessService las =
        mock(com.w3llspring.fhpb.web.service.LadderAccessService.class);
    matchFactory = mock(MatchFactory.class);
    matchRepository = mock(MatchRepository.class);
    com.w3llspring.fhpb.web.db.MatchConfirmationRepository confirmationRepo =
        mock(com.w3llspring.fhpb.web.db.MatchConfirmationRepository.class);

    controller =
        new VoiceMatchLogController(
            interpreter,
            spanish,
            userRepo,
            seasonRepo,
            lv2,
            tas,
            mvs,
            lss,
            las,
            matchFactory,
            matchRepository,
            confirmationRepo);

    // inject repositories into private fields
    nameCorrectionRepository = mock(NameCorrectionRepository.class);
    interpretationEventRepository = mock(InterpretationEventRepository.class);
    scoreCorrectionRepository = mock(com.w3llspring.fhpb.web.db.ScoreCorrectionRepository.class);
    // set private fields via reflection
    try {
      java.lang.reflect.Field ncField =
          VoiceMatchLogController.class.getDeclaredField("nameCorrectionRepository");
      ncField.setAccessible(true);
      ncField.set(controller, nameCorrectionRepository);
      java.lang.reflect.Field ieField =
          VoiceMatchLogController.class.getDeclaredField("interpretationEventRepository");
      ieField.setAccessible(true);
      ieField.set(controller, interpretationEventRepository);
      java.lang.reflect.Field scField =
          VoiceMatchLogController.class.getDeclaredField("scoreCorrectionRepository");
      scField.setAccessible(true);
      scField.set(controller, scoreCorrectionRepository);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    // default validation behavior: return a valid result
    com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.MatchValidationResult
        validResult =
            mock(
                com.w3llspring.fhpb.web.service.matchlog.MatchValidationService
                    .MatchValidationResult.class);
    when(validResult.isValid()).thenReturn(true);
    when(matchValidationService.validate(any())).thenReturn(validResult);
  }

  @Test
  void confirm_persists_event_without_creating_corrections_in_controller_path() throws Exception {
    // Setup: saved match has A1=101, A2=102, B1=201, B2=202
    User a1 = new User();
    a1.setId(101L);
    User a2 = new User();
    a2.setId(102L);
    User b1 = new User();
    b1.setId(201L);
    User b2 = new User();
    b2.setId(202L);

    Match saved = new Match();
    // Set id via reflection (entity id setter is not public)
    try {
      java.lang.reflect.Field idField = Match.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(saved, 77L);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    saved.setA1(a1);
    saved.setA2(a2);
    saved.setB1(b1);
    saved.setB2(b2);

    when(matchFactory.createMatch(any())).thenReturn(saved);
    when(matchRepository.findById(77L)).thenReturn(Optional.of(saved));

    // Interpreter returns tokens that are common STT errors and no matchedUserId
    SpokenMatchInterpretation interpretation = new SpokenMatchInterpretation();
    interpretation.setTranscript("Jon Dough and Two Pea play Jane Smith and Bob");
    SpokenMatchInterpretation.Team teamA = interpretation.addTeam(0, false);
    SpokenMatchInterpretation.PlayerResolution pA1 = teamA.addPlayer();
    pA1.setToken("Jon Dough");
    pA1.setMatchedUserId(null);
    SpokenMatchInterpretation.PlayerResolution pA2 = teamA.addPlayer();
    pA2.setToken("Two Pea");
    pA2.setMatchedUserId(null);
    SpokenMatchInterpretation.Team teamB = interpretation.addTeam(1, false);
    SpokenMatchInterpretation.PlayerResolution pB1 = teamB.addPlayer();
    pB1.setToken("Jane Smith");
    pB1.setMatchedUserId(null);
    SpokenMatchInterpretation.PlayerResolution pB2 = teamB.addPlayer();
    pB2.setToken("Bob");
    pB2.setMatchedUserId(null);

    when(interpreter.interpret(any(SpokenMatchInterpretationRequest.class)))
        .thenReturn(interpretation);

    // No existing corrections for tokens
    when(nameCorrectionRepository.findByTokenNormalized(anyString()))
        .thenReturn(Collections.emptyList());

    // Capture saved NameCorrection objects
    when(nameCorrectionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // Ensure season lookup succeeds for this test
    com.w3llspring.fhpb.web.model.LadderSeason season =
        new com.w3llspring.fhpb.web.model.LadderSeason();
    try {
      java.lang.reflect.Field idFieldS =
          com.w3llspring.fhpb.web.model.LadderSeason.class.getDeclaredField("id");
      idFieldS.setAccessible(true);
      idFieldS.set(season, 5L);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    when(seasonRepo.findById(5L)).thenReturn(Optional.of(season));

    // Call confirm via controller
    VoiceMatchLogController.ConfirmRequest req = new VoiceMatchLogController.ConfirmRequest();
    req.setLadderConfigId(9L);
    req.setSeasonId(5L);
    req.setTeamAUserIds(List.of(101L, 102L));
    req.setTeamBUserIds(List.of(201L, 202L));
    req.setScoreTeamA(11);
    req.setScoreTeamB(8);
    req.setTranscript(interpretation.getTranscript());
    // provide a current user principal - set as A1
    org.springframework.security.core.Authentication auth =
        mock(org.springframework.security.core.Authentication.class);
    com.w3llspring.fhpb.web.model.User principalUser = a1;
    com.w3llspring.fhpb.web.model.CustomUserDetails cud =
        new com.w3llspring.fhpb.web.model.CustomUserDetails(principalUser);
    when(auth.getPrincipal()).thenReturn(cud);

    // Instead of exercising the full confirm() path (which requires many collaborators),
    // Call the private helper directly to validate learning persistence behavior.
    try {
      java.lang.reflect.Method m =
          VoiceMatchLogController.class.getDeclaredMethod(
              "recordInterpretationEventAndCorrections",
              SpokenMatchInterpretation.class,
              Long.class,
              Long.class,
              Long.class);
      m.setAccessible(true);
      m.invoke(controller, interpretation, Long.valueOf(77L), Long.valueOf(9L), Long.valueOf(101L));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // InterpretationEvent should be saved
    verify(interpretationEventRepository, times(1)).save(any());

    // Controller path now only persists the interpretation event.
    // Corrections are applied later by LearningService after confirmation workflows.
    verify(nameCorrectionRepository, never()).save(any());
    verify(scoreCorrectionRepository, never()).save(any());
  }

  @Test
  void confirm_persists_event_without_corrections_when_interpretation_matches() throws Exception {
    // Setup: saved match has A1=101, A2=102
    User a1 = new User();
    a1.setId(101L);
    User a2 = new User();
    a2.setId(102L);
    Match saved = new Match();
    try {
      java.lang.reflect.Field idField2 = Match.class.getDeclaredField("id");
      idField2.setAccessible(true);
      idField2.set(saved, 88L);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    saved.setA1(a1);
    saved.setA2(a2);
    when(matchFactory.createMatch(any())).thenReturn(saved);
    when(matchRepository.findById(88L)).thenReturn(Optional.of(saved));

    // Interpreter returns matchedUserIds equal to final users
    SpokenMatchInterpretation interpretation = new SpokenMatchInterpretation();
    interpretation.setTranscript("John and Alice");
    SpokenMatchInterpretation.Team teamA = interpretation.addTeam(0, false);
    SpokenMatchInterpretation.PlayerResolution pA1 = teamA.addPlayer();
    pA1.setToken("John");
    pA1.setMatchedUserId(101L);
    SpokenMatchInterpretation.PlayerResolution pA2 = teamA.addPlayer();
    pA2.setToken("Alice");
    pA2.setMatchedUserId(102L);
    when(interpreter.interpret(any(SpokenMatchInterpretationRequest.class)))
        .thenReturn(interpretation);

    // Ensure season lookup succeeds for this test (seasonId == 3)
    com.w3llspring.fhpb.web.model.LadderSeason season2 =
        new com.w3llspring.fhpb.web.model.LadderSeason();
    try {
      java.lang.reflect.Field idFieldS2 =
          com.w3llspring.fhpb.web.model.LadderSeason.class.getDeclaredField("id");
      idFieldS2.setAccessible(true);
      idFieldS2.set(season2, 3L);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    when(seasonRepo.findById(3L)).thenReturn(Optional.of(season2));

    // Call confirm
    VoiceMatchLogController.ConfirmRequest req = new VoiceMatchLogController.ConfirmRequest();
    req.setLadderConfigId(2L);
    req.setSeasonId(3L);
    req.setTeamAUserIds(List.of(101L, 102L));
    // Provide a non-empty Team B list (null entry allowed to represent guest)
    req.setTeamBUserIds(java.util.Arrays.asList((Long) null));
    req.setScoreTeamA(11);
    req.setScoreTeamB(9);
    req.setTranscript(interpretation.getTranscript());
    org.springframework.security.core.Authentication auth =
        mock(org.springframework.security.core.Authentication.class);
    com.w3llspring.fhpb.web.model.CustomUserDetails cud =
        new com.w3llspring.fhpb.web.model.CustomUserDetails(a1);
    when(auth.getPrincipal()).thenReturn(cud);

    // Call the private helper directly to validate that no corrections are created
    try {
      java.lang.reflect.Method m =
          VoiceMatchLogController.class.getDeclaredMethod(
              "recordInterpretationEventAndCorrections",
              SpokenMatchInterpretation.class,
              Long.class,
              Long.class,
              Long.class);
      m.setAccessible(true);
      m.invoke(controller, interpretation, Long.valueOf(88L), Long.valueOf(2L), Long.valueOf(101L));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // InterpretationEvent should be saved
    verify(interpretationEventRepository, times(1)).save(any());

    // No name correction saves should occur because interpreted matchedUserId equals final user ids
    verify(nameCorrectionRepository, never()).save(any());
  }
}
