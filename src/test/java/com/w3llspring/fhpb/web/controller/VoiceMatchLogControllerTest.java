package com.w3llspring.fhpb.web.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.w3llspring.fhpb.web.controller.match.VoiceMatchLogController;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionAutoModerationService;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.LadderSecurityService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchFactory;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpretation;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpreter;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VoiceMatchLogController")
class VoiceMatchLogControllerTest {

  @Mock private SpokenMatchInterpreter defaultInterpreter;
  @Mock private SpokenMatchInterpreter spanishInterpreter;
  @Mock private UserRepository userRepository;
  @Mock private LadderSeasonRepository seasonRepository;
  @Mock private LadderV2Service ladderV2Service;
  @Mock private TrophyAwardService trophyAwardService;
  @Mock private MatchValidationService matchValidationService;
  @Mock private LadderSecurityService ladderSecurityService;
  @Mock private LadderAccessService ladderAccessService;
  @Mock private MatchFactory matchFactory;
  @Mock private Authentication authentication;

  private VoiceMatchLogController controller;

  private User user1;
  private User user2;
  private User user3;
  private User user4;
  private LadderSeason season;
  private LadderConfig ladderConfig;

  @BeforeEach
  void setUp() {
    controller =
        new VoiceMatchLogController(
            defaultInterpreter,
            spanishInterpreter,
            userRepository,
            seasonRepository,
            ladderV2Service,
            trophyAwardService,
            matchValidationService,
            ladderSecurityService,
            ladderAccessService,
            matchFactory);

    // Set up test users
    user1 = new User();
    ReflectionTestUtils.setField(user1, "id", 1L);
    user1.setEmail("user1@test.com");
    user1.setNickName("User1");

    user2 = new User();
    ReflectionTestUtils.setField(user2, "id", 2L);
    user2.setEmail("user2@test.com");
    user2.setNickName("User2");

    user3 = new User();
    ReflectionTestUtils.setField(user3, "id", 3L);
    user3.setEmail("user3@test.com");
    user3.setNickName("User3");

    user4 = new User();
    ReflectionTestUtils.setField(user4, "id", 4L);
    user4.setEmail("user4@test.com");
    user4.setNickName("User4");

    // Set up ladder config and season
    ladderConfig = new LadderConfig();
    ReflectionTestUtils.setField(ladderConfig, "id", 1L);
    ladderConfig.setSecurityLevel(LadderSecurity.NONE);

    season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 1L);
    season.setLadderConfig(ladderConfig);
    season.setState(LadderSeason.State.ACTIVE);

    // Default authentication to user1
    CustomUserDetails userDetails = new CustomUserDetails(user1);
    when(authentication.getPrincipal()).thenReturn(userDetails);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(matchValidationService.validateScore(any(Integer.class), any(Integer.class)))
        .thenReturn(createValidScoreResult());
  }

  @Nested
  @DisplayName("Interpret Endpoint - Auto-Submit")
  class InterpretAutoSubmit {

    @Test
    @DisplayName("Should truncate oversized transcript before interpretation")
    void shouldTruncateOversizedTranscriptBeforeInterpretation() {
      // Given
      String longTranscript = "  " + "a".repeat(320) + "  ";
      VoiceMatchLogController.InterpretRequest request =
          new VoiceMatchLogController.InterpretRequest();
      request.setSeasonId(1L);
      request.setTranscript(longTranscript);

      when(defaultInterpreter.interpret(any())).thenReturn(new SpokenMatchInterpretation());

      // When
      controller.interpret(request, authentication);

      // Then
      ArgumentCaptor<com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpretationRequest>
          captor =
              ArgumentCaptor.forClass(
                  com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpretationRequest.class);
      verify(defaultInterpreter).interpret(captor.capture());
      String sentTranscript = captor.getValue().getTranscript();
      assertNotNull(sentTranscript);
      assertEquals(300, sentTranscript.length());
      assertEquals("a".repeat(300), sentTranscript);
    }

    @Test
    @DisplayName("Should auto-submit when all players >= 85% confidence and no passphrase required")
    void shouldAutoSubmitHighConfidenceNoPassphrase() {
      // Given
      SpokenMatchInterpretation interpretation =
          createHighConfidenceInterpretation(
              Arrays.asList(1L, 2L), // Team A
              Arrays.asList(3L, 4L), // Team B
              11,
              9);

      VoiceMatchLogController.InterpretRequest request =
          new VoiceMatchLogController.InterpretRequest();
      request.setSeasonId(1L);
      request.setTranscript("User1 and User2 beat User3 and User4 11 to 9");

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(ladderSecurityService.isPassphraseRequired(ladderConfig)).thenReturn(false);
      when(defaultInterpreter.interpret(any())).thenReturn(interpretation);
      when(matchValidationService.validate(any())).thenReturn(createValidResult());
      when(matchValidationService.resolveEligibleMemberUserIdsForSeason(1L))
          .thenReturn(java.util.Set.of(1L, 2L, 3L, 4L));
      when(ladderAccessService.isSeasonAdmin(1L, user1)).thenReturn(false);
      when(userRepository.findAllById(anyList()))
          .thenReturn(Arrays.asList(user1, user2, user3, user4));

      when(matchFactory.createMatch(any(Match.class)))
          .thenAnswer(
              invocation -> {
                Match match = invocation.getArgument(0);
                // Simulate ID assignment that would happen in DB
                ReflectionTestUtils.setField(match, "id", 100L);
                return match;
              });

      // When
      VoiceMatchLogController.InterpretResponse response =
          controller.interpret(request, authentication);

      // Then
      assertTrue(
          response.isAutoSubmitted(), "Should auto-submit with high confidence and no passphrase");
      assertNotNull(response.getMatchId());
      assertNull(response.getInterpretation());
      verify(matchFactory).createMatch(any(Match.class));
      verify(ladderV2Service).applyMatch(any(Match.class));
      verify(trophyAwardService).evaluateMatch(any(Match.class));
    }

    @Test
    @DisplayName("Should NOT auto-submit when confidence < 85%")
    void shouldNotAutoSubmitLowConfidence() {
      // Given
      SpokenMatchInterpretation interpretation =
          createLowConfidenceInterpretation(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 11, 9);

      VoiceMatchLogController.InterpretRequest request =
          new VoiceMatchLogController.InterpretRequest();
      request.setSeasonId(1L);
      request.setTranscript("User1 and User2 beat User3 and User4 11 to 9");

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(ladderSecurityService.isPassphraseRequired(ladderConfig)).thenReturn(false);
      when(defaultInterpreter.interpret(any())).thenReturn(interpretation);

      // When
      VoiceMatchLogController.InterpretResponse response =
          controller.interpret(request, authentication);

      // Then
      assertFalse(response.isAutoSubmitted(), "Should NOT auto-submit with low confidence");
      assertNotNull(response.getInterpretation());
      verify(matchFactory, never()).createMatch(any(Match.class));
    }

    @Test
    @DisplayName("Should NOT auto-submit when passphrase required")
    void shouldNotAutoSubmitPassphraseRequired() {
      // Given
      ladderConfig.setSecurityLevel(LadderSecurity.STANDARD);
      SpokenMatchInterpretation interpretation =
          createHighConfidenceInterpretation(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 11, 9);

      VoiceMatchLogController.InterpretRequest request =
          new VoiceMatchLogController.InterpretRequest();
      request.setSeasonId(1L);
      request.setTranscript("User1 and User2 beat User3 and User4 11 to 9");

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(ladderSecurityService.isPassphraseRequired(ladderConfig)).thenReturn(true);
      when(defaultInterpreter.interpret(any())).thenReturn(interpretation);

      // When
      VoiceMatchLogController.InterpretResponse response =
          controller.interpret(request, authentication);

      // Then
      assertFalse(response.isAutoSubmitted(), "Should NOT auto-submit when passphrase required");
      assertNotNull(response.getInterpretation());
      verify(matchFactory, never()).createMatch(any(Match.class));
    }

    @Test
    @DisplayName("Should NOT auto-submit when interpretation incomplete")
    void shouldNotAutoSubmitIncomplete() {
      // Given
      SpokenMatchInterpretation interpretation =
          createHighConfidenceInterpretation(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 11, 9);
      // interpretation.setComplete(false); // Removed - completeness validation moved to controller
      // layer

      VoiceMatchLogController.InterpretRequest request =
          new VoiceMatchLogController.InterpretRequest();
      request.setSeasonId(1L);

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(defaultInterpreter.interpret(any())).thenReturn(interpretation);

      // When
      VoiceMatchLogController.InterpretResponse response =
          controller.interpret(request, authentication);

      // Then
      assertFalse(response.isAutoSubmitted(), "Should NOT auto-submit when incomplete");
      assertNotNull(response.getInterpretation());
      verify(matchFactory, never()).createMatch(any(Match.class));
    }

    @Test
    @DisplayName("Should return response that supports auto-redirect when auto-submitted")
    void shouldReturnAutoRedirectResponseWhenAutoSubmitted() {
      // Given
      SpokenMatchInterpretation interpretation =
          createHighConfidenceInterpretation(
              Arrays.asList(1L, 2L), // Team A
              Arrays.asList(3L, 4L), // Team B
              11,
              9);

      VoiceMatchLogController.InterpretRequest request =
          new VoiceMatchLogController.InterpretRequest();
      request.setSeasonId(1L);
      request.setTranscript("User1 and User2 beat User3 and User4 11 to 9");

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(ladderSecurityService.isPassphraseRequired(ladderConfig)).thenReturn(false);
      when(defaultInterpreter.interpret(any())).thenReturn(interpretation);
      when(matchValidationService.validate(any())).thenReturn(createValidResult());
      when(matchValidationService.resolveEligibleMemberUserIdsForSeason(1L))
          .thenReturn(java.util.Set.of(1L, 2L, 3L, 4L));
      when(ladderAccessService.isSeasonAdmin(1L, user1)).thenReturn(false);
      when(userRepository.findAllById(anyList()))
          .thenReturn(Arrays.asList(user1, user2, user3, user4));

      Long expectedMatchId = 100L;
      when(matchFactory.createMatch(any(Match.class)))
          .thenAnswer(
              invocation -> {
                Match match = invocation.getArgument(0);
                ReflectionTestUtils.setField(match, "id", expectedMatchId);
                return match;
              });

      // When
      VoiceMatchLogController.InterpretResponse response =
          controller.interpret(request, authentication);

      // Then - Verify response structure supports auto-redirect
      assertTrue(response.isAutoSubmitted(), "Response must indicate auto-submission occurred");
      assertEquals(
          expectedMatchId,
          response.getMatchId(),
          "Response must include matchId for redirect to match details page");
      assertNull(
          response.getInterpretation(),
          "Response should not include interpretation when auto-submitted (no review needed)");

      // Verify the match was actually saved
      verify(matchFactory).createMatch(any(Match.class));
      verify(ladderV2Service).applyMatch(any(Match.class));
      verify(trophyAwardService).evaluateMatch(any(Match.class));

      // This response structure allows frontend to:
      // 1. Check if (response.autoSubmitted) to know if redirect is needed
      // 2. Use response.matchId to construct redirect URL: /matches/{matchId}
      // 3. Skip showing the review form since interpretation is null
    }
  }

  @Nested
  @DisplayName("Confirm Endpoint - Passphrase Security Tests")
  class PassphraseSecurityTests {

    @Test
    @DisplayName("Should ACCEPT match without passphrase when ladder security is NONE")
    void shouldAcceptWithoutPassphraseWhenNotRequired() {
      // Given - Default setup has LadderSecurity.NONE
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 11, 9);
      // No passphrase provided - this is intentional

      setupSuccessfulConfirmMocks();

      // When
      VoiceMatchLogController.ConfirmResponse response =
          controller.confirm(request, authentication);

      // Then
      assertNotNull(
          response.getMatchId(),
          "Match should be created without passphrase when security is NONE");
      verify(matchFactory).createMatch(any(Match.class));
    }
  }

  @Nested
  @DisplayName("Confirm Endpoint - Passing Cases")
  class ConfirmPassingCases {

    @Test
    @DisplayName("Should truncate oversized transcript before saving match")
    void shouldTruncateOversizedTranscriptBeforeSavingMatch() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 11, 9);
      request.setTranscript("  " + "b".repeat(340) + "  ");
      setupSuccessfulConfirmMocks();

      // When
      controller.confirm(request, authentication);

      // Then
      ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
      verify(matchFactory).createMatch(matchCaptor.capture());
      String savedTranscript = matchCaptor.getValue().getTranscript();
      assertNotNull(savedTranscript);
      assertEquals(300, savedTranscript.length());
      assertEquals("b".repeat(300), savedTranscript);
    }

    @Test
    @DisplayName("Should accept: Current user on team A with other user")
    void shouldAcceptUserOnTeamAWithOtherUser() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(1L, 2L), // Team A (user1=CURRENT USER, user2)
              Arrays.asList(3L, 4L), // Team B
              11,
              9);

      setupSuccessfulConfirmMocks();

      // When
      VoiceMatchLogController.ConfirmResponse response =
          controller.confirm(request, authentication);

      // Then
      assertNotNull(response.getMatchId());
      verify(matchFactory).createMatch(any(Match.class));
    }

    @Test
    @DisplayName("Should reject: blocked competition player cannot log another match")
    void shouldRejectBlockedCompetitionPlayer() {
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 11, 9);

      setupSuccessfulConfirmMocks();
      ReflectionTestUtils.setField(
          controller,
          "competitionAutoModerationService",
          blockingAutoModerationService(user1, season));

      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class, () -> controller.confirm(request, authentication));

      assertEquals(403, exception.getStatusCode().value());
      assertTrue(exception.getReason().contains("rest of this season"));
    }

    @Test
    @DisplayName("Should accept: Current user on team B with other user")
    void shouldAcceptUserOnTeamBWithOtherUser() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(2L, 3L), // Team A
              Arrays.asList(1L, 4L), // Team B (user1=CURRENT USER, user4)
              11,
              9);

      setupSuccessfulConfirmMocks();

      // When
      VoiceMatchLogController.ConfirmResponse response =
          controller.confirm(request, authentication);

      // Then
      assertNotNull(response.getMatchId());
      verify(matchFactory).createMatch(any(Match.class));
    }

    @Test
    @DisplayName("Should accept: Current user with guest on team A")
    void shouldAcceptUserWithGuestOnTeamA() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(1L, null), // Team A (user1=CURRENT USER, guest)
              Arrays.asList(3L, 4L), // Team B
              11,
              9);

      setupSuccessfulConfirmMocks();

      // When
      VoiceMatchLogController.ConfirmResponse response =
          controller.confirm(request, authentication);

      // Then
      assertNotNull(response.getMatchId());
      verify(matchFactory).createMatch(any(Match.class));
    }

    @Test
    @DisplayName("Should accept: Current user with guest on team B")
    void shouldAcceptUserWithGuestOnTeamB() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(2L, 3L), // Team A
              Arrays.asList(1L, null), // Team B (user1=CURRENT USER, guest)
              11,
              9);

      setupSuccessfulConfirmMocks();

      // When
      VoiceMatchLogController.ConfirmResponse response =
          controller.confirm(request, authentication);

      // Then
      assertNotNull(response.getMatchId());
      verify(matchFactory).createMatch(any(Match.class));
    }

    @Test
    @DisplayName("Should accept: Winner has score 11")
    void shouldAcceptScore11To9() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 11, 9);

      setupSuccessfulConfirmMocks();

      // When
      VoiceMatchLogController.ConfirmResponse response =
          controller.confirm(request, authentication);

      // Then
      assertNotNull(response.getMatchId());
    }

    @Test
    @DisplayName("Should accept: Winner >= 11 and loser exactly 2 less (13-11)")
    void shouldAcceptScore13To11() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 13, 11);

      setupSuccessfulConfirmMocks();

      // When
      VoiceMatchLogController.ConfirmResponse response =
          controller.confirm(request, authentication);

      // Then
      assertNotNull(response.getMatchId());
    }

    @Test
    @DisplayName("Should accept: Winner >= 11 and loser exactly 2 less (12-10)")
    void shouldAcceptScore12To10() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 12, 10);

      setupSuccessfulConfirmMocks();

      // When
      VoiceMatchLogController.ConfirmResponse response =
          controller.confirm(request, authentication);

      // Then
      assertNotNull(response.getMatchId());
    }

    @Test
    @DisplayName("Should accept: Higher-score formats like 13-10")
    void shouldAcceptScore13To10() {
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 13, 10);

      setupSuccessfulConfirmMocks();

      VoiceMatchLogController.ConfirmResponse response =
          controller.confirm(request, authentication);

      assertNotNull(response.getMatchId());
    }
  }

  @Nested
  @DisplayName("Confirm Endpoint - Failing Cases")
  class ConfirmFailingCases {

    @Test
    @DisplayName(
        "Should allow: Two guests on team A when SELF_CONFIRM personal records are enabled")
    void shouldAllowTwoGuestsOnTeamAForSelfConfirmPersonalRecords() {
      // Given - Team A are both guests, but Team B has registered users
      ladderConfig.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
      ladderConfig.setAllowGuestOnlyPersonalMatches(true);

      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(null, null), // Team A (both guests)
              Arrays.asList(1L, 2L), // Team B (registered users)
              11,
              9);

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(ladderSecurityService.isPassphraseRequired(ladderConfig)).thenReturn(false);
      when(matchValidationService.validate(any())).thenReturn(createValidResult());
      when(matchValidationService.resolveEligibleMemberUserIdsForSeason(1L))
          .thenReturn(java.util.Set.of(1L, 2L));
      when(ladderAccessService.isSeasonAdmin(1L, user1)).thenReturn(false);
      // Only Team B users are returned (Team A are guests)
      when(userRepository.findAllById(anyList())).thenReturn(Arrays.asList(user1, user2));
      when(matchFactory.createMatch(any(Match.class)))
          .thenAnswer(
              invocation -> {
                Match match = invocation.getArgument(0);
                ReflectionTestUtils.setField(match, "id", 99L);
                return match;
              });

      // When
      var response = controller.confirm(request, authentication);

      // Then - Match is saved (personal record only for Team B)
      assertNotNull(response);
      assertEquals(99L, response.getMatchId());
      verify(matchFactory, times(1)).createMatch(any(Match.class));
    }

    @Test
    @DisplayName(
        "Should allow: Two guests on team B when SELF_CONFIRM personal records are enabled")
    void shouldAllowTwoGuestsOnTeamBForSelfConfirmPersonalRecords() {
      // Given - Team B are both guests, but Team A has registered users
      ladderConfig.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
      ladderConfig.setAllowGuestOnlyPersonalMatches(true);

      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(1L, 2L), // Team A (registered users)
              Arrays.asList(null, null), // Team B (both guests)
              11,
              9);

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(ladderSecurityService.isPassphraseRequired(ladderConfig)).thenReturn(false);
      when(matchValidationService.validate(any())).thenReturn(createValidResult());
      when(matchValidationService.resolveEligibleMemberUserIdsForSeason(1L))
          .thenReturn(java.util.Set.of(1L, 2L));
      when(ladderAccessService.isSeasonAdmin(1L, user1)).thenReturn(false);
      // Only Team A users are returned (Team B are guests)
      when(userRepository.findAllById(anyList())).thenReturn(Arrays.asList(user1, user2));
      when(matchFactory.createMatch(any(Match.class)))
          .thenAnswer(
              invocation -> {
                Match match = invocation.getArgument(0);
                ReflectionTestUtils.setField(match, "id", 99L);
                return match;
              });

      // When
      var response = controller.confirm(request, authentication);

      // Then - Match is saved (personal record only for Team A)
      assertNotNull(response);
      assertEquals(99L, response.getMatchId());
      verify(matchFactory, times(1)).createMatch(any(Match.class));
    }

    @Test
    @DisplayName(
        "Should reject: Two guests on team B when SELF_CONFIRM personal records are disabled")
    void shouldRejectTwoGuestsOnTeamBWhenPersonalRecordsDisabled() {
      // Given
      ladderConfig.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
      ladderConfig.setAllowGuestOnlyPersonalMatches(false);

      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(Arrays.asList(1L, 2L), Arrays.asList(null, null), 11, 9);

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(ladderSecurityService.isPassphraseRequired(ladderConfig)).thenReturn(false);
      when(matchValidationService.resolveEligibleMemberUserIdsForSeason(1L))
          .thenReturn(java.util.Set.of(1L, 2L, 3L, 4L));
      when(matchValidationService.validate(any()))
          .thenReturn(
              MatchValidationService.MatchValidationResult.invalid(
                  java.util.List.of("Opponent team: at least one player must be a ladder member")));

      // When / Then
      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class, () -> controller.confirm(request, authentication));
      assertTrue(
          exception
              .getReason()
              .contains("Opponent team: at least one player must be a ladder member"));

      ArgumentCaptor<MatchValidationService.MatchValidationRequest> captor =
          ArgumentCaptor.forClass(MatchValidationService.MatchValidationRequest.class);
      verify(matchValidationService).validate(captor.capture());
      assertTrue(
          captor.getValue().isRequireOpponentMember(),
          "Opponent member should be required when personal record mode is disabled");
    }

    @Test
    @DisplayName("Should reject: Two guests on team B in STANDARD ladders even if flag is enabled")
    void shouldRejectTwoGuestsOnTeamBForStandardLadder() {
      // Given
      ladderConfig.setSecurityLevel(LadderSecurity.STANDARD);
      ladderConfig.setAllowGuestOnlyPersonalMatches(true);

      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(Arrays.asList(1L, 2L), Arrays.asList(null, null), 11, 9);

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(ladderSecurityService.isPassphraseRequired(ladderConfig)).thenReturn(false);
      when(matchValidationService.resolveEligibleMemberUserIdsForSeason(1L))
          .thenReturn(java.util.Set.of(1L, 2L, 3L, 4L));
      when(matchValidationService.validate(any()))
          .thenReturn(
              MatchValidationService.MatchValidationResult.invalid(
                  java.util.List.of("Opponent team: at least one player must be a ladder member")));

      // When / Then
      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class, () -> controller.confirm(request, authentication));
      assertTrue(
          exception
              .getReason()
              .contains("Opponent team: at least one player must be a ladder member"));

      ArgumentCaptor<MatchValidationService.MatchValidationRequest> captor =
          ArgumentCaptor.forClass(MatchValidationService.MatchValidationRequest.class);
      verify(matchValidationService).validate(captor.capture());
      assertTrue(
          captor.getValue().isRequireOpponentMember(),
          "Opponent member should be required in STANDARD ladders");
    }

    @Disabled(
        "per-user passphrase behavior removed; temporarily disabled until ladder-level passphrase checks implemented")
    @Test
    @DisplayName("Should reject: Ladder requires passphrase, no passphrase provided")
    void shouldRejectMissingPassphrase() {
      // Given
      ladderConfig.setSecurityLevel(LadderSecurity.STANDARD);
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 11, 9);
      // No passphrase set

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(ladderSecurityService.isPassphraseRequired(ladderConfig)).thenReturn(true);

      // When/Then
      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class, () -> controller.confirm(request, authentication));
      assertTrue(exception.getReason().contains("passphrase"));
    }

    @Test
    @DisplayName("Should reject: One player is empty (team A has 0 players)")
    void shouldRejectOnePlayerEmpty() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(), // Team A empty
              Arrays.asList(1L, 2L),
              11,
              9);

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));

      // When/Then
      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class, () -> controller.confirm(request, authentication));
      assertTrue(exception.getReason().contains("Team 1 needs at least one player"));
    }

    @Test
    @DisplayName("Should reject: Two players are empty (both teams have 0 players)")
    void shouldRejectTwoPlayersEmpty() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(), // Team A empty
              Arrays.asList(), // Team B empty
              11,
              9);

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));

      // When/Then
      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class, () -> controller.confirm(request, authentication));
      assertTrue(exception.getReason().contains("Team 1 needs at least one player"));
    }

    @Test
    @DisplayName("Should reject: Three players are empty (one team has 1, other has 0)")
    void shouldRejectThreePlayersEmpty() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(1L), // Team A has 1 player
              Arrays.asList(), // Team B empty
              11,
              9);

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));

      // When/Then
      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class, () -> controller.confirm(request, authentication));
      assertTrue(exception.getReason().contains("Team 2 needs at least one player"));
    }

    @Test
    @DisplayName("Should reject: Four players are empty (all nulls)")
    void shouldRejectFourPlayersEmpty() {
      // Given - both teams have players but all are null (guests)
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(null, null), // Team A both guests
              Arrays.asList(null, null), // Team B both guests
              11,
              9);

      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));

      // When/Then - At least one team must have a registered user
      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class, () -> controller.confirm(request, authentication));
      assertTrue(exception.getReason().contains("At least one team must have a registered user"));
    }

    @Test
    @DisplayName("Should reject: Winner score < 11")
    void shouldRejectScoreLessThan11() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 10, 8 // Invalid: winner has < 11
              );

      setupSuccessfulConfirmMocks();
      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(matchValidationService.validateScore(Integer.valueOf(10), Integer.valueOf(8)))
          .thenReturn(
              MatchValidationService.ScoreValidationResult.invalid(
                  "Winning team must score at least 11 points."));

      // When/Then
      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class, () -> controller.confirm(request, authentication));
      assertTrue(exception.getReason().contains("at least 11 points"));
    }

    @Test
    @DisplayName("Should reject: Scores above configured cap")
    void shouldRejectScoreAboveConfiguredCap() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 36, 34);

      setupSuccessfulConfirmMocks();
      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(matchValidationService.validateScore(Integer.valueOf(36), Integer.valueOf(34)))
          .thenReturn(
              MatchValidationService.ScoreValidationResult.invalid(
                  "Scores cannot exceed 35 points."));

      // When/Then
      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class, () -> controller.confirm(request, authentication));
      assertTrue(exception.getReason().contains("cannot exceed 35 points"));
    }

    @Test
    @DisplayName("Should reject: Tied score (7-7)")
    void shouldRejectTiedScore() {
      // Given
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(1L, 2L), Arrays.asList(3L, 4L), 7, 7 // Invalid: tied score
              );

      setupSuccessfulConfirmMocks();
      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
      when(matchValidationService.validateScore(Integer.valueOf(7), Integer.valueOf(7)))
          .thenReturn(
              MatchValidationService.ScoreValidationResult.invalid("Match scores cannot be tied."));

      // When/Then
      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class, () -> controller.confirm(request, authentication));
      assertTrue(exception.getReason().contains("cannot be tied"));
    }

    @Test
    @DisplayName(
        "Should NOT reject: Current user explicitly included in teamBUserIds (reporter not counted as duplicate)")
    void shouldNotRejectCurrentUserInTeamBUserIds() {
      // Given: Current user (ID 1) is explicitly in teamBUserIds[0]
      // This tests the fix for the duplicate player detection bug where reporter slot
      // was being counted as a duplicate when the current user was already in a team slot.
      VoiceMatchLogController.ConfirmRequest request =
          createConfirmRequest(
              Arrays.asList(null, 2L), // Team A: guest + user 2
              Arrays.asList(1L, 3L), // Team B: current user (1) + user 3
              9,
              11 // Team B wins
              );

      setupSuccessfulConfirmMocks();
      when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));

      // When - Should NOT throw "Duplicate players detected: 1" exception
      VoiceMatchLogController.ConfirmResponse response =
          controller.confirm(request, authentication);

      // Then: Should succeed and return match ID
      assertNotNull(response);
      assertNotNull(response.getMatchId());
      assertEquals(100L, response.getMatchId());
    }
  }

  // Helper methods

  private VoiceMatchLogController.ConfirmRequest createConfirmRequest(
      List<Long> teamA, List<Long> teamB, int scoreA, int scoreB) {
    VoiceMatchLogController.ConfirmRequest request = new VoiceMatchLogController.ConfirmRequest();
    request.setSeasonId(1L);
    request.setLadderConfigId(1L);
    request.setTeamAUserIds(teamA);
    request.setTeamBUserIds(teamB);
    request.setScoreTeamA(scoreA);
    request.setScoreTeamB(scoreB);
    return request;
  }

  private SpokenMatchInterpretation createHighConfidenceInterpretation(
      List<Long> teamA, List<Long> teamB, int scoreA, int scoreB) {
    SpokenMatchInterpretation interpretation = new SpokenMatchInterpretation();
    // interpretation.setComplete(true); // Removed - completeness validation moved to controller
    // layer
    interpretation.setScoreTeamA(scoreA);
    interpretation.setScoreTeamB(scoreB);
    interpretation.setWinningTeamIndex(scoreA > scoreB ? 0 : 1);

    SpokenMatchInterpretation.Team team0 = interpretation.addTeam(0, scoreA > scoreB);
    for (Long userId : teamA) {
      SpokenMatchInterpretation.PlayerResolution player = team0.addPlayer();
      player.setMatchedUserId(userId);
      player.setConfidence(0.95); // High confidence
      player.setNeedsReview(false);
    }

    SpokenMatchInterpretation.Team team1 = interpretation.addTeam(1, scoreB > scoreA);
    for (Long userId : teamB) {
      SpokenMatchInterpretation.PlayerResolution player = team1.addPlayer();
      player.setMatchedUserId(userId);
      player.setConfidence(0.90); // High confidence
      player.setNeedsReview(false);
    }

    return interpretation;
  }

  private SpokenMatchInterpretation createLowConfidenceInterpretation(
      List<Long> teamA, List<Long> teamB, int scoreA, int scoreB) {
    SpokenMatchInterpretation interpretation = new SpokenMatchInterpretation();
    // interpretation.setComplete(true); // Removed - completeness validation moved to controller
    // layer
    interpretation.setScoreTeamA(scoreA);
    interpretation.setScoreTeamB(scoreB);
    interpretation.setWinningTeamIndex(scoreA > scoreB ? 0 : 1);

    SpokenMatchInterpretation.Team team0 = interpretation.addTeam(0, scoreA > scoreB);
    for (Long userId : teamA) {
      SpokenMatchInterpretation.PlayerResolution player = team0.addPlayer();
      player.setMatchedUserId(userId);
      player.setConfidence(0.70); // Low confidence
      player.setNeedsReview(true);
    }

    SpokenMatchInterpretation.Team team1 = interpretation.addTeam(1, scoreB > scoreA);
    for (Long userId : teamB) {
      SpokenMatchInterpretation.PlayerResolution player = team1.addPlayer();
      player.setMatchedUserId(userId);
      player.setConfidence(0.75); // Low confidence
      player.setNeedsReview(true);
    }

    return interpretation;
  }

  private MatchValidationService.MatchValidationResult createValidResult() {
    return MatchValidationService.MatchValidationResult.valid();
  }

  private MatchValidationService.ScoreValidationResult createValidScoreResult() {
    return MatchValidationService.ScoreValidationResult.valid();
  }

  private void setupSuccessfulConfirmMocks() {
    when(seasonRepository.findById(1L)).thenReturn(Optional.of(season));
    when(ladderSecurityService.isPassphraseRequired(ladderConfig)).thenReturn(false);
    when(matchValidationService.validate(any())).thenReturn(createValidResult());
    when(matchValidationService.validateScore(any(Integer.class), any(Integer.class)))
        .thenReturn(createValidScoreResult());
    when(matchValidationService.resolveEligibleMemberUserIdsForSeason(1L))
        .thenReturn(java.util.Set.of(1L, 2L, 3L, 4L));
    when(ladderAccessService.isSeasonAdmin(1L, user1)).thenReturn(false);
    when(userRepository.findAllById(anyList()))
        .thenReturn(Arrays.asList(user1, user2, user3, user4));
    when(matchFactory.createMatch(any(Match.class)))
        .thenAnswer(
            invocation -> {
              Match match = invocation.getArgument(0);
              ReflectionTestUtils.setField(match, "id", 100L);
              return match;
            });
  }

  private CompetitionAutoModerationService blockingAutoModerationService(
      User blockedUser, LadderSeason blockedSeason) {
    return new CompetitionAutoModerationService(
        null,
        new com.w3llspring.fhpb.web.service.CompetitionSeasonService(null, null, null),
        true,
        1,
        2,
        3) {
      @Override
      public void requireNotBlocked(User user, LadderSeason season) {
        if (user == blockedUser && season == blockedSeason) {
          throw new SecurityException(
              "You cannot be included in competition matches for the rest of this season.");
        }
      }
    };
  }
}
