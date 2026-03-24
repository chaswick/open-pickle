package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.match.MatchLogController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.RoundRobinEntry;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionAutoModerationService;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.MatchFactory;
import com.w3llspring.fhpb.web.service.RecentDuplicateMatchWarningService;
import com.w3llspring.fhpb.web.service.UserMatchLogGateService;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import com.w3llspring.fhpb.web.service.matchworkflow.MatchStateTransitionService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinModificationException;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class MatchLogControllerAdminEditTest {

  @Mock private UserRepository userRepo;
  @Mock private MatchRepository matchRepo;
  @Mock private LadderSeasonRepository seasonRepo;
  @Mock private LadderMembershipRepository membershipRepo;
  @Mock private LadderConfigRepository ladderConfigRepository;
  @Mock private MatchConfirmationService matchConfirmationService;
  @Mock private CourtNameService courtNameService;
  @Mock private MatchConfirmationRepository matchConfirmationRepository;

  private MatchLogController controller;
  private MatchStateTransitionService matchStateTransitionService;
  private LadderV2Service ladderV2;
  private TrophyAwardService trophyAwardService;
  private MatchValidationService matchValidationService;
  private LadderAccessService ladderAccessService;
  private RecentDuplicateMatchWarningService recentDuplicateMatchWarningService;
  private boolean seasonAdmin;
  private Match appliedMatch;
  private Match trophyEvaluatedMatch;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    seasonAdmin = false;
    appliedMatch = null;
    trophyEvaluatedMatch = null;

    ladderV2 =
        new LadderV2Service(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms(
                java.util.List.of(
                    new com.w3llspring.fhpb.web.service.scoring
                        .MarginCurveV1LadderScoringAlgorithm(),
                    new com.w3llspring.fhpb.web.service.scoring
                        .BalancedV1LadderScoringAlgorithm())),
            null,
            null,
            null,
            null) {
          @Override
          public void applyMatch(Match match) {
            appliedMatch = match;
          }
        };
    trophyAwardService =
        new TrophyAwardService(null, null, null, null, null, null, null, null) {
          @Override
          public void evaluateMatch(Match match) {
            trophyEvaluatedMatch = match;
          }
        };
    matchValidationService = new MatchValidationService(seasonRepo, membershipRepo);
    recentDuplicateMatchWarningService =
        new RecentDuplicateMatchWarningService(matchRepo, matchConfirmationRepository, 120);
    ladderAccessService =
        new LadderAccessService(null, null) {
          @Override
          public boolean isSeasonAdmin(Long seasonId, User user) {
            return seasonAdmin;
          }
        };
    lenient().when(courtNameService.gatherCourtNamesForUsers(any(), any())).thenReturn(Map.of());
    lenient().when(matchRepo.findDistinctA1IdsBySeason(any())).thenReturn(List.of());
    lenient().when(matchRepo.findDistinctA2IdsBySeason(any())).thenReturn(List.of());
    lenient().when(matchRepo.findDistinctB1IdsBySeason(any())).thenReturn(List.of());
    lenient().when(matchRepo.findDistinctB2IdsBySeason(any())).thenReturn(List.of());
    lenient()
        .when(matchRepo.findRecentPlayedMatchesForPlayers(any(), any(), any()))
        .thenReturn(List.of());
    lenient()
        .when(matchRepo.findRecentPlayedMatchesForPlayersInSeason(any(), any(), any(), any()))
        .thenReturn(List.of());
    lenient()
        .when(matchRepo.findRecentPlayedMatchesForPlayersInSession(anyLong(), any(), any(), any()))
        .thenReturn(List.of());
    lenient().when(matchRepo.findByCreatedAtInRange(any(), any())).thenReturn(List.of());
    lenient().when(matchConfirmationRepository.findByMatchIdIn(any())).thenReturn(List.of());
    lenient()
        .when(matchRepo.findByIdWithUsersForUpdate(anyLong()))
        .thenAnswer(invocation -> matchRepo.findByIdWithUsers(invocation.getArgument(0)));
    lenient()
        .when(matchRepo.saveAndFlush(any(Match.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    MatchFactory matchFactory =
        new MatchFactory(matchRepo, matchConfirmationService) {
          @Override
          public Match createMatch(Match match) {
            if (match.getId() == null) {
              setId(match, 999L);
            }
            return match;
          }
        };
    matchStateTransitionService =
        new MatchStateTransitionService(
            matchRepo,
            matchFactory,
            matchConfirmationService,
            matchValidationService,
            ladderV2,
            trophyAwardService);
    UserMatchLogGateService userMatchLogGateService =
        new UserMatchLogGateService(userRepo) {
          @Override
          public MatchLogGateResult reserveMatchLogging(
              Long userId, java.time.Instant now, boolean rateLimitEnabled) {
            return MatchLogGateResult.allowed(
                userRepo
                    .findById(userId)
                    .orElseGet(() -> user(userId, "user" + userId + "@test.local", false)));
          }
        };

    controller =
        new MatchLogController(
            userRepo,
            matchRepo,
            ladderV2,
            seasonRepo,
            trophyAwardService,
            matchValidationService,
            ladderAccessService,
            matchFactory,
            matchStateTransitionService,
            userMatchLogGateService,
            recentDuplicateMatchWarningService,
            matchConfirmationService,
            courtNameService,
            null,
            true);
    org.springframework.test.util.ReflectionTestUtils.setField(
        controller, "ladderConfigRepository", ladderConfigRepository);
    org.springframework.test.util.ReflectionTestUtils.setField(
        controller, "ladderMembershipRepository", membershipRepo);
  }

  @Test
  void form_redirectsDirectCompetitionSelectionToCompetitionPage() {
    User currentUser = user(10L, "me@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig competition = new LadderConfig();
    competition.setId(900L);
    competition.setType(LadderConfig.Type.COMPETITION);
    when(ladderConfigRepository.findById(900L)).thenReturn(Optional.of(competition));

    String view =
        controller.form(
            model, auth, request, null, 900L, null, false, null, null, null, null, null, null);

    assertThat(view).isEqualTo("redirect:/competition");
  }

  @Nested
  @DisplayName("Edit workflow business rules")
  class EditWorkflowBusinessRules {

    @Test
    void private_group_admin_can_edit_confirmed_match() {
      User groupAdmin = user(10L, "groupadmin@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(
              new CustomUserDetails(groupAdmin), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addParameter("expectedVersion", "1");
      Model model = new ExtendedModelMap();

      LadderConfig ladder = new LadderConfig();
      ladder.setId(301L);
      ladder.setType(LadderConfig.Type.STANDARD);
      LadderSeason season = new LadderSeason();
      setId(season, 201L);
      season.setLadderConfig(ladder);

      User a1 = user(1L, "a1@test.local", false);
      User a2 = user(2L, "a2@test.local", false);
      User b1 = user(3L, "b1@test.local", false);
      User b2 = user(4L, "b2@test.local", false);
      User logger = user(99L, "logger@test.local", false);

      Match match = new Match();
      setId(match, 101L);
      match.setSeason(season);
      match.setState(MatchState.CONFIRMED);
      match.setLoggedBy(logger);
      match.setA1(a1);
      match.setA2(a2);
      match.setB1(b1);
      match.setB2(b2);
      match.setA1Guest(false);
      match.setA2Guest(false);
      match.setB1Guest(false);
      match.setB2Guest(false);
      match.setScoreA(11);
      match.setScoreB(9);
      ReflectionTestUtils.setField(match, "version", 1L);

      seasonAdmin = true;

      when(matchRepo.findByIdWithUsers(101L)).thenReturn(Optional.of(match));
      when(seasonRepo.findById(201L)).thenReturn(Optional.of(season));
      when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              301L, LadderMembership.State.ACTIVE))
          .thenReturn(
              List.of(
                  membership(301L, 1L, LadderMembership.State.ACTIVE),
                  membership(301L, 2L, LadderMembership.State.ACTIVE),
                  membership(301L, 3L, LadderMembership.State.ACTIVE),
                  membership(301L, 4L, LadderMembership.State.ACTIVE)));
      when(userRepo.findById(1L)).thenReturn(Optional.of(a1));
      when(userRepo.findById(2L)).thenReturn(Optional.of(a2));
      when(userRepo.findById(3L)).thenReturn(Optional.of(b1));
      when(userRepo.findById(4L)).thenReturn(Optional.of(b2));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "1",
              "2",
              "3",
              "4",
              11,
              8,
              Optional.empty(),
              201L,
              301L,
              false,
              null,
              null,
              101L);

      assertThat(view)
          .isEqualTo("redirect:/standings?ladderId=301&seasonId=201&toast=matchUpdated");
      assertThat(match.getScoreB()).isEqualTo(8);
      assertThat(match.getEditedBy()).isEqualTo(groupAdmin);
      assertThat(match.getEditedAt()).isNotNull();
      assertThat(appliedMatch).isEqualTo(match);
      assertThat(trophyEvaluatedMatch).isEqualTo(match);

      verify(matchConfirmationService, never()).rebuildConfirmationRequests(any());
    }

    @Test
    void non_admin_cannot_edit_confirmed_match() {
      User user = user(20L, "user@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      Model model = new ExtendedModelMap();

      LadderConfig ladder = new LadderConfig();
      ladder.setId(401L);
      LadderSeason season = new LadderSeason();
      setId(season, 301L);
      season.setLadderConfig(ladder);

      Match confirmed = new Match();
      setId(confirmed, 202L);
      confirmed.setSeason(season);
      confirmed.setState(MatchState.CONFIRMED);
      confirmed.setLoggedBy(user);

      seasonAdmin = false;
      when(matchRepo.findByIdWithUsers(202L)).thenReturn(Optional.of(confirmed));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "1",
              "2",
              "3",
              "4",
              11,
              9,
              Optional.empty(),
              301L,
              401L,
              false,
              null,
              null,
              202L);

      assertThat(view).isEqualTo("redirect:/home?toast=forbidden");
      verify(matchRepo, never()).save(any(Match.class));
      assertThat(appliedMatch).isNull();
      assertThat(trophyEvaluatedMatch).isNull();
    }

    @Test
    void session_admin_cannot_edit_confirmed_global_competition_match() {
      User sessionAdmin = user(21L, "sessionadmin@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(
              new CustomUserDetails(sessionAdmin), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addParameter("expectedVersion", "2");
      Model model = new ExtendedModelMap();

      LadderConfig competition = new LadderConfig();
      competition.setId(402L);
      competition.setType(LadderConfig.Type.COMPETITION);
      competition.setOwnerUserId(999L);

      LadderSeason competitionSeason = new LadderSeason();
      setId(competitionSeason, 302L);
      competitionSeason.setLadderConfig(competition);

      LadderConfig sourceSession = new LadderConfig();
      sourceSession.setId(702L);
      sourceSession.setType(LadderConfig.Type.SESSION);
      sourceSession.setOwnerUserId(sessionAdmin.getId());

      Match confirmed = new Match();
      setId(confirmed, 203L);
      confirmed.setSeason(competitionSeason);
      confirmed.setSourceSessionConfig(sourceSession);
      confirmed.setState(MatchState.CONFIRMED);
      confirmed.setLoggedBy(sessionAdmin);
      ReflectionTestUtils.setField(confirmed, "version", 2L);

      seasonAdmin = false;
      when(matchRepo.findByIdWithUsers(203L)).thenReturn(Optional.of(confirmed));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "21",
              "",
              "22",
              "",
              11,
              9,
              Optional.empty(),
              302L,
              402L,
              false,
              null,
              null,
              203L);

      assertThat(view).isEqualTo("redirect:/home?toast=forbidden");
      verify(matchRepo, never()).save(any(Match.class));
      assertThat(appliedMatch).isNull();
      assertThat(trophyEvaluatedMatch).isNull();
    }

    @Test
    void competition_match_edit_form_bypasses_direct_competition_redirect() {
      User logger = user(10L, "logger@test.local", false);
      User opponent = user(20L, "opponent@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(new CustomUserDetails(opponent), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      Model model = new ExtendedModelMap();

      LadderConfig competition = new LadderConfig();
      competition.setId(462L);
      competition.setType(LadderConfig.Type.COMPETITION);
      competition.setSecurityLevel(LadderSecurity.STANDARD);

      LadderSeason competitionSeason = new LadderSeason();
      setId(competitionSeason, 362L);
      competitionSeason.setLadderConfig(competition);

      Match match = new Match();
      setId(match, 214L);
      match.setSeason(competitionSeason);
      match.setState(MatchState.PROVISIONAL);
      match.setLoggedBy(logger);
      match.setA1(logger);
      match.setB1(opponent);
      match.setA1Guest(false);
      match.setA2Guest(true);
      match.setB1Guest(false);
      match.setB2Guest(true);
      match.setScoreA(11);
      match.setScoreB(5);
      ReflectionTestUtils.setField(match, "version", 6L);

      when(matchRepo.findByIdWithUsers(214L)).thenReturn(Optional.of(match));
      when(seasonRepo.findById(362L)).thenReturn(Optional.of(competitionSeason));
      when(membershipRepo.findByUserIdAndState(20L, LadderMembership.State.ACTIVE))
          .thenReturn(List.of(membership(462L, 20L, LadderMembership.State.ACTIVE)));
      when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              462L, LadderMembership.State.ACTIVE))
          .thenReturn(
              List.of(
                  membership(462L, 10L, LadderMembership.State.ACTIVE),
                  membership(462L, 20L, LadderMembership.State.ACTIVE)));
      when(userRepo.findAllById(Set.of(10L, 20L))).thenReturn(List.of(logger, opponent));

      String view =
          controller.form(
              model,
              auth,
              request,
              362L,
              null,
              214L,
              false,
              null,
              null,
              null,
              null,
              "/confirm-matches",
              null,
              null,
              null);

      assertThat(view).isEqualTo("auth/logMatch");
      assertThat(model.asMap().get("editMode")).isEqualTo(Boolean.TRUE);
      assertThat(model.asMap().get("editMatchId")).isEqualTo(214L);
      assertThat(model.asMap().get("returnToPath")).isEqualTo("/confirm-matches");
      assertThat(model.asMap()).doesNotContainKeys("season", "courtNameByUser");
    }

    @Test
    void logger_cannot_edit_provisional_standard_match_after_handing_off_confirmation_priority() {
      User logger = user(10L, "logger@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(new CustomUserDetails(logger), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addParameter("expectedVersion", "4");
      Model model = new ExtendedModelMap();

      LadderConfig ladder = new LadderConfig();
      ladder.setId(451L);
      ladder.setSecurityLevel(LadderSecurity.STANDARD);
      LadderSeason season = new LadderSeason();
      setId(season, 351L);
      season.setLadderConfig(ladder);

      User opponent = user(20L, "opponent@test.local", false);

      Match match = new Match();
      setId(match, 212L);
      match.setSeason(season);
      match.setState(MatchState.PROVISIONAL);
      match.setLoggedBy(logger);
      match.setA1(logger);
      match.setB1(opponent);
      match.setA1Guest(false);
      match.setA2Guest(true);
      match.setB1Guest(false);
      match.setB2Guest(true);
      match.setScoreA(11);
      match.setScoreB(9);
      ReflectionTestUtils.setField(match, "version", 4L);

      when(matchRepo.findByIdWithUsers(212L)).thenReturn(Optional.of(match));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "10",
              "",
              "20",
              "",
              11,
              9,
              Optional.empty(),
              351L,
              451L,
              false,
              null,
              null,
              212L);

      assertThat(view).isEqualTo("redirect:/home?toast=forbidden");
      verify(matchRepo, never()).save(any(Match.class));
      verify(matchConfirmationService, never()).rebuildConfirmationRequests(any());
    }

    @Test
    void
        voice_submitted_logger_can_edit_provisional_standard_match_once_and_rebuild_confirmations() {
      User logger = user(10L, "logger@test.local", false);
      User opponent = user(20L, "opponent@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(new CustomUserDetails(logger), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addParameter("expectedVersion", "8");
      Model model = new ExtendedModelMap();

      LadderConfig ladder = new LadderConfig();
      ladder.setId(472L);
      ladder.setSecurityLevel(LadderSecurity.STANDARD);
      LadderSeason season = new LadderSeason();
      setId(season, 372L);
      season.setLadderConfig(ladder);

      Match match = new Match();
      setId(match, 216L);
      match.setSeason(season);
      match.setState(MatchState.PROVISIONAL);
      match.setLoggedBy(logger);
      match.setTranscript("me and guest beat opponent 11-9");
      match.setA1(logger);
      match.setB1(opponent);
      match.setA1Guest(false);
      match.setA2Guest(true);
      match.setB1Guest(false);
      match.setB2Guest(true);
      match.setScoreA(11);
      match.setScoreB(9);
      ReflectionTestUtils.setField(match, "version", 8L);

      when(matchRepo.findByIdWithUsers(216L)).thenReturn(Optional.of(match));
      when(seasonRepo.findById(372L)).thenReturn(Optional.of(season));
      when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              472L, LadderMembership.State.ACTIVE))
          .thenReturn(
              List.of(
                  membership(472L, 10L, LadderMembership.State.ACTIVE),
                  membership(472L, 20L, LadderMembership.State.ACTIVE)));
      when(userRepo.findById(20L)).thenReturn(Optional.of(opponent));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "10",
              "",
              "20",
              "",
              11,
              8,
              Optional.empty(),
              372L,
              472L,
              false,
              null,
              null,
              216L);

      assertThat(view)
          .isEqualTo("redirect:/standings?ladderId=472&seasonId=372&toast=matchUpdated");
      assertThat(match.getScoreB()).isEqualTo(8);
      assertThat(match.getEditedBy()).isEqualTo(logger);
      assertThat(match.getEditedAt()).isNotNull();
      assertThat(match.isUserCorrected()).isTrue();
      assertThat(com.w3llspring.fhpb.web.model.MatchWorkflowRules.canEdit(match, logger, false))
          .isFalse();
      assertThat(com.w3llspring.fhpb.web.model.MatchWorkflowRules.canEdit(match, opponent, false))
          .isTrue();
      verify(matchConfirmationService).rebuildConfirmationRequests(match);
    }

    @Test
    void pending_opponent_can_edit_provisional_standard_match_and_rebuild_confirmations() {
      User logger = user(10L, "logger@test.local", false);
      User opponent = user(20L, "opponent@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(new CustomUserDetails(opponent), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addParameter("expectedVersion", "5");
      Model model = new ExtendedModelMap();

      LadderConfig ladder = new LadderConfig();
      ladder.setId(452L);
      ladder.setSecurityLevel(LadderSecurity.STANDARD);
      LadderSeason season = new LadderSeason();
      setId(season, 352L);
      season.setLadderConfig(ladder);

      Match match = new Match();
      setId(match, 213L);
      match.setSeason(season);
      match.setState(MatchState.PROVISIONAL);
      match.setLoggedBy(logger);
      match.setA1(logger);
      match.setB1(opponent);
      match.setA1Guest(false);
      match.setA2Guest(true);
      match.setB1Guest(false);
      match.setB2Guest(true);
      match.setScoreA(11);
      match.setScoreB(9);
      ReflectionTestUtils.setField(match, "version", 5L);

      when(matchRepo.findByIdWithUsers(213L)).thenReturn(Optional.of(match));
      when(seasonRepo.findById(352L)).thenReturn(Optional.of(season));
      when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              452L, LadderMembership.State.ACTIVE))
          .thenReturn(
              List.of(
                  membership(452L, 10L, LadderMembership.State.ACTIVE),
                  membership(452L, 20L, LadderMembership.State.ACTIVE)));
      when(userRepo.findById(20L)).thenReturn(Optional.of(opponent));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "10",
              "",
              "20",
              "",
              9,
              11,
              Optional.empty(),
              352L,
              452L,
              false,
              null,
              null,
              213L);

      assertThat(view)
          .isEqualTo("redirect:/standings?ladderId=452&seasonId=352&toast=matchUpdated");
      assertThat(match.getEditedBy()).isEqualTo(opponent);
      assertThat(match.getEditedAt()).isNotNull();
      assertThat(match.isUserCorrected()).isTrue();
      verify(matchConfirmationService).rebuildConfirmationRequests(match);
    }

    @Test
    void edit_rejects_update_when_no_match_fields_changed() {
      User logger = user(10L, "logger@test.local", false);
      User opponent = user(20L, "opponent@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(new CustomUserDetails(opponent), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addParameter("expectedVersion", "10");
      Model model = new ExtendedModelMap();

      LadderConfig ladder = new LadderConfig();
      ladder.setId(453L);
      ladder.setSecurityLevel(LadderSecurity.STANDARD);
      LadderSeason season = new LadderSeason();
      setId(season, 353L);
      season.setLadderConfig(ladder);

      Match match = new Match();
      setId(match, 218L);
      match.setSeason(season);
      match.setState(MatchState.PROVISIONAL);
      match.setLoggedBy(logger);
      match.setA1(logger);
      match.setB1(opponent);
      match.setA1Guest(false);
      match.setA2Guest(true);
      match.setB1Guest(false);
      match.setB2Guest(true);
      match.setScoreA(11);
      match.setScoreB(9);
      ReflectionTestUtils.setField(match, "version", 10L);

      when(matchRepo.findByIdWithUsers(218L)).thenReturn(Optional.of(match));
      when(matchRepo.findByIdWithUsersForUpdate(218L)).thenReturn(Optional.of(match));
      when(seasonRepo.findById(353L)).thenReturn(Optional.of(season));
      when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              453L, LadderMembership.State.ACTIVE))
          .thenReturn(
              List.of(
                  membership(453L, 10L, LadderMembership.State.ACTIVE),
                  membership(453L, 20L, LadderMembership.State.ACTIVE)));
      when(membershipRepo.findByUserIdAndState(20L, LadderMembership.State.ACTIVE))
          .thenReturn(List.of(membership(453L, 20L, LadderMembership.State.ACTIVE)));
      when(userRepo.findById(20L)).thenReturn(Optional.of(opponent));
      when(userRepo.findAllById(Set.of(10L, 20L))).thenReturn(List.of(logger, opponent));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "10",
              "guest",
              "20",
              "guest",
              11,
              9,
              Optional.empty(),
              353L,
              453L,
              false,
              null,
              null,
              218L);

      assertThat(view).isEqualTo("auth/logMatch");
      assertThat(model.asMap().get("toastMessage"))
          .isEqualTo("No changes were made to this match.");
      assertThat(match.getEditedBy()).isNull();
      assertThat(match.getEditedAt()).isNull();
      verify(matchConfirmationService, never()).rebuildConfirmationRequests(match);
    }

    @Test
    void competition_session_edit_rejects_player_outside_source_session_roster() {
      User logger = user(10L, "logger@test.local", false);
      User opponent = user(20L, "opponent@test.local", false);
      User outsider = user(30L, "outsider@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(new CustomUserDetails(opponent), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addParameter("expectedVersion", "11");
      Model model = new ExtendedModelMap();

      LadderConfig session = new LadderConfig();
      session.setId(703L);
      session.setType(LadderConfig.Type.SESSION);

      LadderConfig competition = new LadderConfig();
      competition.setId(466L);
      competition.setType(LadderConfig.Type.COMPETITION);
      competition.setSecurityLevel(LadderSecurity.STANDARD);

      LadderSeason competitionSeason = new LadderSeason();
      setId(competitionSeason, 366L);
      competitionSeason.setLadderConfig(competition);

      Match match = new Match();
      setId(match, 219L);
      match.setSeason(competitionSeason);
      match.setSourceSessionConfig(session);
      match.setState(MatchState.PROVISIONAL);
      match.setLoggedBy(logger);
      match.setA1(logger);
      match.setB1(opponent);
      match.setA1Guest(false);
      match.setA2Guest(true);
      match.setB1Guest(false);
      match.setB2Guest(true);
      match.setScoreA(11);
      match.setScoreB(5);
      ReflectionTestUtils.setField(match, "version", 11L);

      when(matchRepo.findByIdWithUsers(219L)).thenReturn(Optional.of(match));
      when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              703L, LadderMembership.State.ACTIVE))
          .thenReturn(
              List.of(
                  membership(703L, 10L, LadderMembership.State.ACTIVE),
                  membership(703L, 20L, LadderMembership.State.ACTIVE)));
      when(membershipRepo.findByUserIdAndState(20L, LadderMembership.State.ACTIVE))
          .thenReturn(List.of(membership(703L, 20L, LadderMembership.State.ACTIVE)));
      when(userRepo.findById(20L)).thenReturn(Optional.of(opponent));
      when(userRepo.findById(30L)).thenReturn(Optional.of(outsider));
      when(userRepo.findAllById(Set.of(10L, 20L))).thenReturn(List.of(logger, opponent));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "10",
              "guest",
              "20",
              "30",
              11,
              6,
              Optional.empty(),
              366L,
              null,
              false,
              null,
              null,
              null,
              null,
              219L);

      assertThat(view).isEqualTo("auth/logMatch");
      assertThat(model.asMap().get("toastMessage"))
          .asString()
          .contains("Only members of this ladder can be selected for this season.");
      @SuppressWarnings("unchecked")
      List<User> users = (List<User>) model.asMap().get("users");
      assertThat(users).extracting(User::getId).doesNotContain(30L);
      assertThat(match.getB2()).isNull();
      verify(matchConfirmationService, never()).rebuildConfirmationRequests(match);
    }

    @Test
    void competition_match_edit_rejects_blocked_actor_before_updating() {
      User logger = user(10L, "logger@test.local", false);
      User opponent = user(20L, "opponent@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(new CustomUserDetails(opponent), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addParameter("expectedVersion", "12");
      Model model = new ExtendedModelMap();

      LadderConfig session = new LadderConfig();
      session.setId(704L);
      session.setType(LadderConfig.Type.SESSION);

      LadderConfig competition = new LadderConfig();
      competition.setId(467L);
      competition.setType(LadderConfig.Type.COMPETITION);
      competition.setSecurityLevel(LadderSecurity.STANDARD);

      LadderSeason competitionSeason = new LadderSeason();
      setId(competitionSeason, 367L);
      competitionSeason.setLadderConfig(competition);

      Match match = new Match();
      setId(match, 220L);
      match.setSeason(competitionSeason);
      match.setSourceSessionConfig(session);
      match.setState(MatchState.PROVISIONAL);
      match.setLoggedBy(logger);
      match.setA1(logger);
      match.setB1(opponent);
      match.setA1Guest(false);
      match.setA2Guest(true);
      match.setB1Guest(false);
      match.setB2Guest(true);
      match.setScoreA(11);
      match.setScoreB(5);
      ReflectionTestUtils.setField(match, "version", 12L);

      CompetitionAutoModerationService autoModerationService =
          new CompetitionAutoModerationService(null, null, true, 3, 5, 7) {
            @Override
            public Set<Long> filterEligibleUserIds(LadderSeason season, Set<Long> userIds) {
              return userIds;
            }

            @Override
            public void requireNotBlocked(User user, LadderSeason season) {
              if (Objects.equals(user.getId(), opponent.getId())) {
                throw new SecurityException("Competition access blocked.");
              }
            }
          };
      ReflectionTestUtils.setField(
          controller, "competitionAutoModerationService", autoModerationService);

      when(matchRepo.findByIdWithUsers(220L)).thenReturn(Optional.of(match));
      when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              704L, LadderMembership.State.ACTIVE))
          .thenReturn(
              List.of(
                  membership(704L, 10L, LadderMembership.State.ACTIVE),
                  membership(704L, 20L, LadderMembership.State.ACTIVE)));
      when(membershipRepo.findByUserIdAndState(20L, LadderMembership.State.ACTIVE))
          .thenReturn(List.of(membership(704L, 20L, LadderMembership.State.ACTIVE)));
      when(userRepo.findAllById(Set.of(10L, 20L))).thenReturn(List.of(logger, opponent));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "10",
              "guest",
              "20",
              "guest",
              11,
              6,
              Optional.empty(),
              367L,
              null,
              false,
              null,
              null,
              null,
              null,
              220L);

      assertThat(view).isEqualTo("auth/logMatch");
      assertThat(model.asMap().get("toastMessage")).isEqualTo("Competition access blocked.");
      assertThat(model.asMap().get("toastLevel")).isEqualTo("danger");
      assertThat(match.getScoreB()).isEqualTo(5);
      assertThat(match.getEditedBy()).isNull();
      verify(matchConfirmationService, never()).rebuildConfirmationRequests(match);
    }

    @Test
    void
        competition_match_edit_submission_bypasses_direct_competition_redirect_and_returns_to_confirm_matches() {
      User logger = user(10L, "logger@test.local", false);
      User opponent = user(20L, "opponent@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(new CustomUserDetails(opponent), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addParameter("expectedVersion", "7");
      Model model = new ExtendedModelMap();

      LadderConfig competition = new LadderConfig();
      competition.setId(463L);
      competition.setType(LadderConfig.Type.COMPETITION);
      competition.setSecurityLevel(LadderSecurity.STANDARD);

      LadderSeason competitionSeason = new LadderSeason();
      setId(competitionSeason, 363L);
      competitionSeason.setLadderConfig(competition);

      Match match = new Match();
      setId(match, 215L);
      match.setSeason(competitionSeason);
      match.setState(MatchState.PROVISIONAL);
      match.setLoggedBy(logger);
      match.setA1(logger);
      match.setB1(opponent);
      match.setA1Guest(false);
      match.setA2Guest(true);
      match.setB1Guest(false);
      match.setB2Guest(true);
      match.setScoreA(11);
      match.setScoreB(5);
      ReflectionTestUtils.setField(match, "version", 7L);

      when(matchRepo.findByIdWithUsers(215L)).thenReturn(Optional.of(match));
      when(seasonRepo.findById(363L)).thenReturn(Optional.of(competitionSeason));
      when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              463L, LadderMembership.State.ACTIVE))
          .thenReturn(
              List.of(
                  membership(463L, 10L, LadderMembership.State.ACTIVE),
                  membership(463L, 20L, LadderMembership.State.ACTIVE)));
      when(userRepo.findById(20L)).thenReturn(Optional.of(opponent));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "10",
              "guest",
              "20",
              "guest",
              11,
              6,
              Optional.empty(),
              363L,
              null,
              false,
              "/confirm-matches",
              null,
              null,
              null,
              215L);

      assertThat(view).isEqualTo("redirect:/confirm-matches?toast=matchUpdated");
      assertThat(match.getScoreA()).isEqualTo(11);
      assertThat(match.getScoreB()).isEqualTo(6);
      assertThat(match.getEditedBy()).isEqualTo(opponent);
      assertThat(match.getEditedAt()).isNotNull();
      assertThat(match.isUserCorrected()).isTrue();
      verify(matchConfirmationService).rebuildConfirmationRequests(match);
      assertThat(appliedMatch).isEqualTo(match);
      assertThat(trophyEvaluatedMatch).isEqualTo(match);
    }

    @Test
    void competition_match_edit_submission_returns_to_session_page_when_requested() {
      User logger = user(10L, "logger@test.local", false);
      User opponent = user(20L, "opponent@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(new CustomUserDetails(opponent), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addParameter("expectedVersion", "8");
      Model model = new ExtendedModelMap();

      LadderConfig competition = new LadderConfig();
      competition.setId(464L);
      competition.setType(LadderConfig.Type.COMPETITION);
      competition.setSecurityLevel(LadderSecurity.STANDARD);

      LadderSeason competitionSeason = new LadderSeason();
      setId(competitionSeason, 364L);
      competitionSeason.setLadderConfig(competition);

      Match match = new Match();
      setId(match, 216L);
      match.setSeason(competitionSeason);
      match.setState(MatchState.PROVISIONAL);
      match.setLoggedBy(logger);
      match.setA1(logger);
      match.setB1(opponent);
      match.setA1Guest(false);
      match.setA2Guest(true);
      match.setB1Guest(false);
      match.setB2Guest(true);
      match.setScoreA(11);
      match.setScoreB(5);
      ReflectionTestUtils.setField(match, "version", 8L);

      when(matchRepo.findByIdWithUsers(216L)).thenReturn(Optional.of(match));
      when(seasonRepo.findById(364L)).thenReturn(Optional.of(competitionSeason));
      when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              464L, LadderMembership.State.ACTIVE))
          .thenReturn(
              List.of(
                  membership(464L, 10L, LadderMembership.State.ACTIVE),
                  membership(464L, 20L, LadderMembership.State.ACTIVE)));
      when(userRepo.findById(20L)).thenReturn(Optional.of(opponent));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "10",
              "guest",
              "20",
              "guest",
              11,
              6,
              Optional.empty(),
              364L,
              null,
              false,
              "/groups/701",
              null,
              null,
              null,
              216L);

      assertThat(view).isEqualTo("redirect:/groups/701?toast=matchUpdated");
      assertThat(match.getScoreB()).isEqualTo(6);
      assertThat(match.getEditedBy()).isEqualTo(opponent);
      verify(matchConfirmationService).rebuildConfirmationRequests(match);
    }

    @Test
    void competition_match_edit_submission_falls_back_to_source_session_page_without_return_to() {
      User logger = user(10L, "logger@test.local", false);
      User opponent = user(20L, "opponent@test.local", false);
      Authentication auth =
          new UsernamePasswordAuthenticationToken(new CustomUserDetails(opponent), null, List.of());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addParameter("expectedVersion", "9");
      Model model = new ExtendedModelMap();

      LadderConfig session = new LadderConfig();
      session.setId(702L);
      session.setType(LadderConfig.Type.SESSION);

      LadderConfig competition = new LadderConfig();
      competition.setId(465L);
      competition.setType(LadderConfig.Type.COMPETITION);
      competition.setSecurityLevel(LadderSecurity.STANDARD);

      LadderSeason competitionSeason = new LadderSeason();
      setId(competitionSeason, 365L);
      competitionSeason.setLadderConfig(competition);

      Match match = new Match();
      setId(match, 217L);
      match.setSeason(competitionSeason);
      match.setSourceSessionConfig(session);
      match.setState(MatchState.PROVISIONAL);
      match.setLoggedBy(logger);
      match.setA1(logger);
      match.setB1(opponent);
      match.setA1Guest(false);
      match.setA2Guest(true);
      match.setB1Guest(false);
      match.setB2Guest(true);
      match.setScoreA(11);
      match.setScoreB(5);
      ReflectionTestUtils.setField(match, "version", 9L);

      when(matchRepo.findByIdWithUsers(217L)).thenReturn(Optional.of(match));
      when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              702L, LadderMembership.State.ACTIVE))
          .thenReturn(
              List.of(
                  membership(702L, 10L, LadderMembership.State.ACTIVE),
                  membership(702L, 20L, LadderMembership.State.ACTIVE)));
      when(userRepo.findById(20L)).thenReturn(Optional.of(opponent));

      String view =
          controller.submit(
              model,
              auth,
              request,
              "10",
              "guest",
              "20",
              "guest",
              11,
              6,
              Optional.empty(),
              365L,
              null,
              false,
              null,
              null,
              null,
              null,
              217L);

      assertThat(view).isEqualTo("redirect:/groups/702?toast=matchUpdated");
      assertThat(match.getScoreB()).isEqualTo(6);
      assertThat(match.getEditedBy()).isEqualTo(opponent);
      verify(matchConfirmationService).rebuildConfirmationRequests(match);
    }
  }

  // Player picker, voice-review, and form presentation coverage.

  @Test
  void form_marksUsersActiveWhenReturnedFromDistinctParticipantQueries() {
    User currentUser = user(10L, "me@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(501L);
    LadderSeason season = new LadderSeason();
    setId(season, 601L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(3));
    season.setEndDate(LocalDate.now().plusDays(3));

    User activeA = user(11L, "alpha@test.local", false);
    activeA.setNickName("Alpha");
    User activeB = user(12L, "beta@test.local", false);
    activeB.setNickName("Beta");
    User inactive = user(13L, "zeta@test.local", false);
    inactive.setNickName("Zeta");

    when(seasonRepo.findById(601L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            501L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(501L, 10L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE),
                membership(501L, 11L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE),
                membership(501L, 12L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE),
                membership(
                    501L, 13L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndUserId(501L, 10L))
        .thenReturn(
            Optional.of(
                membership(
                    501L, 10L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByUserIdAndState(
            10L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(
                    501L, 10L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE)));
    when(userRepo.findAllById(Set.of(10L, 11L, 12L, 13L)))
        .thenReturn(List.of(currentUser, activeA, activeB, inactive));

    String view =
        controller.form(
            model, auth, request, 601L, 501L, null, false, null, null, null, null, null, null);

    assertThat(view).isEqualTo("auth/logMatch");
    @SuppressWarnings("unchecked")
    List<User> users = (List<User>) model.asMap().get("users");
    assertThat(users).extracting(User::getId).containsExactly(11L, 12L, 10L, 13L);

    @SuppressWarnings("unchecked")
    List<User> otherPlayers = (List<User>) model.asMap().get("otherPlayers");
    assertThat(otherPlayers).extracting(User::getId).containsExactly(11L, 12L, 13L);
  }

  @Test
  void form_voiceReviewModeKeepsCurrentUserSelectableAcrossAllSlots() {
    User currentUser = user(10L, "me@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addParameter("voiceReview", "1");
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(511L);
    LadderSeason season = new LadderSeason();
    setId(season, 611L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(3));
    season.setEndDate(LocalDate.now().plusDays(3));

    User teammate = user(11L, "ally@test.local", false);
    teammate.setNickName("Ally");

    when(seasonRepo.findById(611L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            511L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(511L, 10L, LadderMembership.State.ACTIVE),
                membership(511L, 11L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndUserId(511L, 10L))
        .thenReturn(Optional.of(membership(511L, 10L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByUserIdAndState(10L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership(511L, 10L, LadderMembership.State.ACTIVE)));
    when(userRepo.findAllById(Set.of(10L, 11L))).thenReturn(List.of(currentUser, teammate));

    String view =
        controller.form(
            model, auth, request, 611L, 511L, null, false, null, null, null, null, null, null);

    assertThat(view).isEqualTo("auth/logMatch");
    assertThat(model.asMap().get("voiceReviewMode")).isEqualTo(Boolean.TRUE);
    assertThat(model.asMap().get("selectedA1")).isEqualTo(10L);
    @SuppressWarnings("unchecked")
    List<User> otherPlayers = (List<User>) model.asMap().get("otherPlayers");
    assertThat(otherPlayers).extracting(User::getId).containsExactly(11L, 10L);
  }

  @Test
  void form_usesCourtNameFirstAndAddsStableIdentityForCourtNameCollisions() {
    User currentUser = user(10L, "me@test.local", false);
    currentUser.setNickName("Logger");
    currentUser.setPublicCode("PB-logger-mark-010");
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(521L);
    LadderSeason season = new LadderSeason();
    setId(season, 621L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(3));
    season.setEndDate(LocalDate.now().plusDays(3));

    User johnOne = user(11L, "john.one@test.local", false);
    johnOne.setNickName("John Smith");
    johnOne.setPublicCode("PB-john-ace-011");
    User johnTwo = user(12L, "john.two@test.local", false);
    johnTwo.setNickName("John Davis");
    johnTwo.setPublicCode("PB-john-dink-012");
    User ace = user(13L, "ace@test.local", false);
    ace.setNickName("Alex Carter");
    ace.setPublicCode("PB-alex-pace-013");

    when(seasonRepo.findById(621L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            521L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(521L, 10L, LadderMembership.State.ACTIVE),
                membership(521L, 11L, LadderMembership.State.ACTIVE),
                membership(521L, 12L, LadderMembership.State.ACTIVE),
                membership(521L, 13L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndUserId(521L, 10L))
        .thenReturn(Optional.of(membership(521L, 10L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByUserIdAndState(10L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership(521L, 10L, LadderMembership.State.ACTIVE)));
    when(userRepo.findAllById(Set.of(10L, 11L, 12L, 13L)))
        .thenReturn(List.of(currentUser, johnOne, johnTwo, ace));
    when(courtNameService.gatherCourtNamesForUsers(anyList(), eq(521L)))
        .thenReturn(
            Map.of(
                11L, Set.of("John"),
                12L, Set.of("John"),
                13L, Set.of("Ace")));

    String view =
        controller.form(
            model, auth, request, 621L, 521L, null, false, null, null, null, null, null, null);

    assertThat(view).isEqualTo("auth/logMatch");
    @SuppressWarnings("unchecked")
    Map<Long, String> primaryLabels =
        (Map<Long, String>) model.asMap().get("playerPrimaryLabelByUser");
    @SuppressWarnings("unchecked")
    Map<Long, String> secondaryLabels =
        (Map<Long, String>) model.asMap().get("playerSecondaryLabelByUser");
    @SuppressWarnings("unchecked")
    Map<Long, String> optionLabels =
        (Map<Long, String>) model.asMap().get("playerOptionLabelByUser");
    @SuppressWarnings("unchecked")
    Map<Long, String> searchText = (Map<Long, String>) model.asMap().get("playerSearchTextByUser");

    assertThat(primaryLabels.get(11L)).isEqualTo("John");
    assertThat(secondaryLabels.get(11L)).isEqualTo("John Smith - PB-john-ace-011");
    assertThat(optionLabels.get(11L)).isEqualTo("John - John Smith - PB-john-ace-011");
    assertThat(primaryLabels.get(13L)).isEqualTo("Ace");
    assertThat(secondaryLabels.get(13L)).isEmpty();
    assertThat(optionLabels.get(13L)).isEqualTo("Ace");
    assertThat(searchText.get(11L)).contains("PB-john-ace-011");
    assertThat(model.asMap().get("useSearchablePlayerPicker")).isEqualTo(Boolean.FALSE);
  }

  @Test
  void form_sortsPlayersByStableCourtNameOrder() {
    User currentUser = user(10L, "me@test.local", false);
    currentUser.setNickName("Current Player");
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(522L);
    LadderSeason season = new LadderSeason();
    setId(season, 622L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(3));
    season.setEndDate(LocalDate.now().plusDays(3));

    User moe = user(11L, "moe@test.local", false);
    moe.setNickName("Moe Player");
    User ace = user(12L, "ace@test.local", false);
    ace.setNickName("Ace Player");

    when(seasonRepo.findById(622L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            522L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(522L, 10L, LadderMembership.State.ACTIVE),
                membership(522L, 11L, LadderMembership.State.ACTIVE),
                membership(522L, 12L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndUserId(522L, 10L))
        .thenReturn(Optional.of(membership(522L, 10L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByUserIdAndState(10L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership(522L, 10L, LadderMembership.State.ACTIVE)));
    when(userRepo.findAllById(Set.of(10L, 11L, 12L))).thenReturn(List.of(currentUser, moe, ace));
    when(courtNameService.gatherCourtNamesForUsers(anyList(), eq(522L)))
        .thenReturn(
            Map.of(
                10L, Set.of("Zoe"),
                11L, Set.of("Moe"),
                12L, Set.of("Ace")));

    String view =
        controller.form(
            model, auth, request, 622L, 522L, null, false, null, null, null, null, null, null);

    assertThat(view).isEqualTo("auth/logMatch");
    @SuppressWarnings("unchecked")
    List<User> orderedUsers = (List<User>) model.asMap().get("users");
    @SuppressWarnings("unchecked")
    List<User> otherPlayers = (List<User>) model.asMap().get("otherPlayers");

    assertThat(orderedUsers).extracting(User::getId).containsExactly(12L, 11L, 10L);
    assertThat(otherPlayers).extracting(User::getId).containsExactly(12L, 11L);
  }

  @Test
  void form_enablesSearchablePlayerPickerForLargerRosters() {
    User currentUser = user(10L, "me@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(531L);
    LadderSeason season = new LadderSeason();
    setId(season, 631L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(3));
    season.setEndDate(LocalDate.now().plusDays(3));

    java.util.List<LadderMembership> memberships = new java.util.ArrayList<>();
    java.util.List<User> users = new java.util.ArrayList<>();
    users.add(currentUser);
    memberships.add(membership(531L, 10L, LadderMembership.State.ACTIVE));
    for (long id = 11L; id <= 26L; id++) {
      User user = user(id, "player" + id + "@test.local", false);
      user.setNickName("Player " + id);
      users.add(user);
      memberships.add(membership(531L, id, LadderMembership.State.ACTIVE));
    }

    when(seasonRepo.findById(631L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            531L, LadderMembership.State.ACTIVE))
        .thenReturn(memberships);
    when(membershipRepo.findByLadderConfigIdAndUserId(531L, 10L))
        .thenReturn(Optional.of(membership(531L, 10L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByUserIdAndState(10L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership(531L, 10L, LadderMembership.State.ACTIVE)));
    when(userRepo.findAllById(
            Set.of(
                10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L, 22L, 23L, 24L, 25L,
                26L)))
        .thenReturn(users);

    Match recentOne = new Match();
    recentOne.setPlayedAt(Instant.now().minusSeconds(60));
    recentOne.setA1(
        users.stream().filter(user -> Objects.equals(user.getId(), 18L)).findFirst().orElseThrow());
    recentOne.setA2(
        users.stream().filter(user -> Objects.equals(user.getId(), 14L)).findFirst().orElseThrow());
    Match recentTwo = new Match();
    recentTwo.setPlayedAt(Instant.now().minusSeconds(120));
    recentTwo.setA1(
        users.stream().filter(user -> Objects.equals(user.getId(), 13L)).findFirst().orElseThrow());
    when(matchRepo.findRecentPlayedMatchesForPlayersInSeason(eq(season), anyList(), any(), any()))
        .thenReturn(List.of(recentOne, recentTwo));

    String view =
        controller.form(
            model, auth, request, 631L, 531L, null, false, null, null, null, null, null, null);

    assertThat(view).isEqualTo("auth/logMatch");
    assertThat(model.asMap().get("useSearchablePlayerPicker")).isEqualTo(Boolean.TRUE);
    @SuppressWarnings("unchecked")
    Set<Long> recentPlayerIds = (Set<Long>) model.asMap().get("recentPlayerIds");
    assertThat(recentPlayerIds).containsExactly(18L, 14L, 13L);
  }

  @Test
  void submit_voiceReviewModeAllowsCurrentUserOnOpposingTeam() {
    User currentUser = user(10L, "me@test.local", false);
    User opponent = user(20L, "opponent@test.local", false);
    User partner = user(30L, "partner@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addParameter("voiceReview", "1");
    request.addParameter("transcript", "Ryan and guest beat me and Alex 11-8");
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(711L);
    ladder.setType(LadderConfig.Type.STANDARD);

    LadderSeason season = new LadderSeason();
    setId(season, 811L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(3));
    season.setEndDate(LocalDate.now().plusDays(3));

    when(seasonRepo.findById(811L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            711L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(711L, 10L, LadderMembership.State.ACTIVE),
                membership(711L, 20L, LadderMembership.State.ACTIVE),
                membership(711L, 30L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndUserId(711L, 10L))
        .thenReturn(Optional.of(membership(711L, 10L, LadderMembership.State.ACTIVE)));
    when(userRepo.findById(10L)).thenReturn(Optional.of(currentUser));
    when(userRepo.findById(20L)).thenReturn(Optional.of(opponent));
    when(userRepo.findById(30L)).thenReturn(Optional.of(partner));

    String view =
        controller.submit(
            model,
            auth,
            request,
            "20",
            "",
            "10",
            "30",
            8,
            11,
            Optional.empty(),
            811L,
            711L,
            false,
            null,
            null,
            null,
            null);

    assertThat(view)
        .isEqualTo("redirect:/standings?ladderId=711&seasonId=811&toast=matchLogged&matchId=999");
    assertThat(appliedMatch).isNotNull();
    assertThat(appliedMatch.getA1()).isEqualTo(opponent);
    assertThat(appliedMatch.isA1Guest()).isFalse();
    assertThat(appliedMatch.getA2()).isNull();
    assertThat(appliedMatch.getB1()).isEqualTo(currentUser);
    assertThat(appliedMatch.getB2()).isEqualTo(partner);
    assertThat(appliedMatch.getTranscript()).isEqualTo("Ryan and guest beat me and Alex 11-8");
    assertThat(trophyEvaluatedMatch).isEqualTo(appliedMatch);
  }

  @Test
  void submit_surfacesRecentDuplicateWarningBeforeCreatingMatch() {
    Instant now = Instant.now();
    User currentUser = user(10L, "me@test.local", false);
    User partner = user(20L, "partner@test.local", false);
    User opponentOne = user(30L, "opponent1@test.local", false);
    User opponentTwo = user(40L, "opponent2@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(711L);
    ladder.setType(LadderConfig.Type.STANDARD);

    LadderSeason season = new LadderSeason();
    setId(season, 811L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(3));
    season.setEndDate(LocalDate.now().plusDays(3));

    when(seasonRepo.findById(811L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            711L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(711L, 10L, LadderMembership.State.ACTIVE),
                membership(711L, 20L, LadderMembership.State.ACTIVE),
                membership(711L, 30L, LadderMembership.State.ACTIVE),
                membership(711L, 40L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndUserId(711L, 10L))
        .thenReturn(Optional.of(membership(711L, 10L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByUserIdAndState(10L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership(711L, 10L, LadderMembership.State.ACTIVE)));
    when(userRepo.findAllById(Set.of(10L, 20L, 30L, 40L)))
        .thenReturn(List.of(currentUser, partner, opponentOne, opponentTwo));
    when(userRepo.findById(20L)).thenReturn(Optional.of(partner));
    when(userRepo.findById(30L)).thenReturn(Optional.of(opponentOne));
    when(userRepo.findById(40L)).thenReturn(Optional.of(opponentTwo));
    Match existingDuplicate = new Match();
    setId(existingDuplicate, 555L);
    existingDuplicate.setSeason(season);
    existingDuplicate.setCreatedAt(now.minusSeconds(30));
    existingDuplicate.setState(MatchState.PROVISIONAL);
    existingDuplicate.setLoggedBy(opponentOne);
    existingDuplicate.setA1(opponentOne);
    existingDuplicate.setA2(opponentTwo);
    existingDuplicate.setB1(currentUser);
    existingDuplicate.setB2(partner);
    existingDuplicate.setA1Guest(false);
    existingDuplicate.setA2Guest(false);
    existingDuplicate.setB1Guest(false);
    existingDuplicate.setB2Guest(false);
    existingDuplicate.setScoreA(9);
    existingDuplicate.setScoreB(11);
    MatchConfirmation confirmation = new MatchConfirmation();
    confirmation.setMatch(existingDuplicate);
    confirmation.setPlayer(partner);
    confirmation.setTeam("B");
    confirmation.setConfirmedAt(now.minusSeconds(10));
    when(matchRepo.findByCreatedAtInRange(any(), any())).thenReturn(List.of(existingDuplicate));
    when(matchConfirmationRepository.findByMatchIdIn(List.of(555L)))
        .thenReturn(List.of(confirmation));

    String view =
        controller.submit(
            model,
            auth,
            request,
            "10",
            "20",
            "30",
            "40",
            11,
            9,
            Optional.empty(),
            811L,
            711L,
            false,
            null,
            null,
            null,
            null);

    assertThat(view).isEqualTo("auth/logMatch");
    assertThat(model.asMap().get("duplicateWarningMatchId")).isEqualTo(555L);
    assertThat(model.asMap().get("duplicateWarningMessage"))
        .isEqualTo(
            "It looks like opponent1 already logged this match and partner confirmed it. Are you sure you want to log it?");
    assertThat(appliedMatch).isNull();
    assertThat(trophyEvaluatedMatch).isNull();
  }

  @Test
  void submit_allowsAcknowledgedRecentDuplicateWarningToProceed() {
    Instant now = Instant.now();
    User currentUser = user(10L, "me@test.local", false);
    User partner = user(20L, "partner@test.local", false);
    User opponentOne = user(30L, "opponent1@test.local", false);
    User opponentTwo = user(40L, "opponent2@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addParameter("duplicateWarningAcceptedMatchId", "555");
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(712L);
    ladder.setType(LadderConfig.Type.STANDARD);

    LadderSeason season = new LadderSeason();
    setId(season, 812L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(3));
    season.setEndDate(LocalDate.now().plusDays(3));

    when(seasonRepo.findById(812L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            712L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(712L, 10L, LadderMembership.State.ACTIVE),
                membership(712L, 20L, LadderMembership.State.ACTIVE),
                membership(712L, 30L, LadderMembership.State.ACTIVE),
                membership(712L, 40L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndUserId(712L, 10L))
        .thenReturn(Optional.of(membership(712L, 10L, LadderMembership.State.ACTIVE)));
    when(userRepo.findById(10L)).thenReturn(Optional.of(currentUser));
    when(userRepo.findById(20L)).thenReturn(Optional.of(partner));
    when(userRepo.findById(30L)).thenReturn(Optional.of(opponentOne));
    when(userRepo.findById(40L)).thenReturn(Optional.of(opponentTwo));
    Match existingDuplicate = new Match();
    setId(existingDuplicate, 555L);
    existingDuplicate.setSeason(season);
    existingDuplicate.setCreatedAt(now.minusSeconds(25));
    existingDuplicate.setState(MatchState.PROVISIONAL);
    existingDuplicate.setLoggedBy(opponentOne);
    existingDuplicate.setA1(opponentOne);
    existingDuplicate.setA2(opponentTwo);
    existingDuplicate.setB1(currentUser);
    existingDuplicate.setB2(partner);
    existingDuplicate.setA1Guest(false);
    existingDuplicate.setA2Guest(false);
    existingDuplicate.setB1Guest(false);
    existingDuplicate.setB2Guest(false);
    existingDuplicate.setScoreA(9);
    existingDuplicate.setScoreB(11);
    MatchConfirmation confirmation = new MatchConfirmation();
    confirmation.setMatch(existingDuplicate);
    confirmation.setPlayer(partner);
    confirmation.setTeam("B");
    confirmation.setConfirmedAt(now.minusSeconds(5));
    when(matchRepo.findByCreatedAtInRange(any(), any())).thenReturn(List.of(existingDuplicate));
    when(matchConfirmationRepository.findByMatchIdIn(List.of(555L)))
        .thenReturn(List.of(confirmation));

    String view =
        controller.submit(
            model,
            auth,
            request,
            "10",
            "20",
            "30",
            "40",
            11,
            9,
            Optional.empty(),
            812L,
            712L,
            false,
            null,
            null,
            null,
            null);

    assertThat(view)
        .isEqualTo("redirect:/standings?ladderId=712&seasonId=812&toast=matchLogged&matchId=999");
    assertThat(appliedMatch).isNotNull();
    assertThat(appliedMatch.getLoggedBy().getId()).isEqualTo(10L);
    assertThat(appliedMatch.getA1()).isEqualTo(currentUser);
    assertThat(appliedMatch.getA2()).isEqualTo(partner);
    assertThat(appliedMatch.getB1()).isEqualTo(opponentOne);
    assertThat(appliedMatch.getB2()).isEqualTo(opponentTwo);
    assertThat(trophyEvaluatedMatch).isEqualTo(appliedMatch);
  }

  @Test
  void submit_allowsPromotedSessionAdminToLogCompetitionSessionMatchWithoutThemselves() {
    User currentUser = user(10L, "sessionadmin@test.local", false);
    User a1 = user(20L, "a1@test.local", false);
    User b1 = user(30L, "b1@test.local", false);
    User b2 = user(40L, "b2@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig sessionLadder = new LadderConfig();
    sessionLadder.setId(712L);
    sessionLadder.setType(LadderConfig.Type.SESSION);
    sessionLadder.setTargetSeasonId(812L);
    sessionLadder.setOwnerUserId(99L);

    LadderConfig competitionCfg = new LadderConfig();
    competitionCfg.setId(814L);
    competitionCfg.setType(LadderConfig.Type.COMPETITION);

    LadderSeason competitionSeason = new LadderSeason();
    setId(competitionSeason, 812L);
    competitionSeason.setLadderConfig(competitionCfg);
    competitionSeason.setState(LadderSeason.State.ACTIVE);
    competitionSeason.setStartDate(LocalDate.now().minusDays(3));
    competitionSeason.setEndDate(LocalDate.now().plusDays(3));

    LadderMembership sessionAdminMembership =
        membership(sessionLadder, 10L, LadderMembership.State.ACTIVE, LadderMembership.Role.ADMIN);

    when(ladderConfigRepository.findById(712L)).thenReturn(Optional.of(sessionLadder));
    when(seasonRepo.findById(812L)).thenReturn(Optional.of(competitionSeason));
    when(membershipRepo.findByLadderConfigIdAndUserId(712L, 10L))
        .thenReturn(Optional.of(sessionAdminMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            712L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                sessionAdminMembership,
                membership(
                    sessionLadder,
                    20L,
                    LadderMembership.State.ACTIVE,
                    LadderMembership.Role.MEMBER),
                membership(
                    sessionLadder,
                    30L,
                    LadderMembership.State.ACTIVE,
                    LadderMembership.Role.MEMBER),
                membership(
                    sessionLadder,
                    40L,
                    LadderMembership.State.ACTIVE,
                    LadderMembership.Role.MEMBER)));
    when(userRepo.findById(20L)).thenReturn(Optional.of(a1));
    when(userRepo.findById(30L)).thenReturn(Optional.of(b1));
    when(userRepo.findById(40L)).thenReturn(Optional.of(b2));

    String view =
        controller.submit(
            model,
            auth,
            request,
            "20",
            "",
            "30",
            "40",
            11,
            7,
            Optional.empty(),
            812L,
            712L,
            true,
            null,
            null,
            null,
            null);

    assertThat(view).isEqualTo("redirect:/groups/712?toast=matchLogged&matchId=999");
    assertThat(appliedMatch).isNotNull();
    assertThat(appliedMatch.getLoggedBy().getId()).isEqualTo(10L);
    assertThat(appliedMatch.getA1()).isEqualTo(a1);
    assertThat(appliedMatch.getA2()).isNull();
    assertThat(appliedMatch.getB1()).isEqualTo(b1);
    assertThat(appliedMatch.getB2()).isEqualTo(b2);
    assertThat(appliedMatch.getSourceSessionConfig()).isEqualTo(sessionLadder);
    assertThat(appliedMatch.getA1().getId()).isNotEqualTo(currentUser.getId());
    assertThat(appliedMatch.getB1().getId()).isNotEqualTo(currentUser.getId());
    assertThat(trophyEvaluatedMatch).isEqualTo(appliedMatch);
  }

  @Test
  void submit_rejectsRegularSessionMemberLoggingCompetitionSessionMatchWithoutThemselves() {
    User currentUser = user(10L, "member@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig sessionLadder = new LadderConfig();
    sessionLadder.setId(713L);
    sessionLadder.setType(LadderConfig.Type.SESSION);
    sessionLadder.setTargetSeasonId(813L);
    sessionLadder.setOwnerUserId(99L);

    LadderConfig competitionCfg = new LadderConfig();
    competitionCfg.setId(815L);
    competitionCfg.setType(LadderConfig.Type.COMPETITION);

    LadderSeason competitionSeason = new LadderSeason();
    setId(competitionSeason, 813L);
    competitionSeason.setLadderConfig(competitionCfg);
    competitionSeason.setState(LadderSeason.State.ACTIVE);
    competitionSeason.setStartDate(LocalDate.now().minusDays(3));
    competitionSeason.setEndDate(LocalDate.now().plusDays(3));

    LadderMembership memberMembership =
        membership(sessionLadder, 10L, LadderMembership.State.ACTIVE, LadderMembership.Role.MEMBER);

    when(ladderConfigRepository.findById(713L)).thenReturn(Optional.of(sessionLadder));
    when(seasonRepo.findById(813L)).thenReturn(Optional.of(competitionSeason));
    when(membershipRepo.findByLadderConfigIdAndUserId(713L, 10L))
        .thenReturn(Optional.of(memberMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            713L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                memberMembership,
                membership(
                    sessionLadder,
                    20L,
                    LadderMembership.State.ACTIVE,
                    LadderMembership.Role.MEMBER),
                membership(
                    sessionLadder,
                    30L,
                    LadderMembership.State.ACTIVE,
                    LadderMembership.Role.MEMBER),
                membership(
                    sessionLadder,
                    40L,
                    LadderMembership.State.ACTIVE,
                    LadderMembership.Role.MEMBER)));

    String view =
        controller.submit(
            model,
            auth,
            request,
            "20",
            "",
            "30",
            "40",
            11,
            7,
            Optional.empty(),
            813L,
            713L,
            true,
            null,
            null,
            null,
            null);

    assertThat(view).isEqualTo("redirect:/competition/log-match?ladderId=713&toast=forbidden");
    assertThat(appliedMatch).isNull();
    assertThat(trophyEvaluatedMatch).isNull();
  }

  // Edit input validation and integrity coverage.

  @Test
  void submit_recomputesExcludeFlagForGuestOnlyPersonalRecordOnEdit() {
    User admin = user(10L, "admin@test.local", true);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(admin), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addParameter("expectedVersion", "6");
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(501L);
    ladder.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    ladder.setAllowGuestOnlyPersonalMatches(true);

    LadderSeason season = new LadderSeason();
    setId(season, 401L);
    season.setLadderConfig(ladder);

    User a1 = user(1L, "a1@test.local", false);
    Match match = new Match();
    setId(match, 303L);
    match.setSeason(season);
    match.setState(MatchState.CONFIRMED);
    match.setLoggedBy(admin);
    match.setA1(a1);
    match.setA1Guest(false);
    match.setA2Guest(true);
    match.setB1Guest(true);
    match.setB2Guest(true);
    match.setScoreA(11);
    match.setScoreB(7);
    match.setExcludeFromStandings(false);
    ReflectionTestUtils.setField(match, "version", 6L);

    seasonAdmin = true;

    when(matchRepo.findByIdWithUsers(303L)).thenReturn(Optional.of(match));
    when(seasonRepo.findById(401L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            501L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership(501L, 1L, LadderMembership.State.ACTIVE)));
    when(userRepo.findById(1L)).thenReturn(Optional.of(a1));

    String view =
        controller.submit(
            model,
            auth,
            request,
            "1",
            "",
            "",
            "",
            11,
            6,
            Optional.empty(),
            401L,
            501L,
            false,
            null,
            null,
            303L);

    assertThat(view).isEqualTo("redirect:/standings?ladderId=501&seasonId=401&toast=matchUpdated");
    assertThat(match.getScoreB()).isEqualTo(6);
    assertThat(match.isExcludeFromStandings()).isTrue();
    assertThat(appliedMatch).isEqualTo(match);
  }

  @Test
  void submit_rejectsCrossTeamDuplicatePlayersForAdminEdit() {
    User admin = user(10L, "admin@test.local", true);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(admin), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addParameter("expectedVersion", "4");
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(601L);
    LadderSeason season = new LadderSeason();
    setId(season, 701L);
    season.setLadderConfig(ladder);

    User a1 = user(1L, "a1@test.local", false);
    User a2 = user(2L, "a2@test.local", false);
    User b1 = user(3L, "b1@test.local", false);
    User b2 = user(4L, "b2@test.local", false);

    Match match = new Match();
    setId(match, 404L);
    match.setSeason(season);
    match.setState(MatchState.CONFIRMED);
    match.setLoggedBy(admin);
    match.setA1(a1);
    match.setA2(a2);
    match.setB1(b1);
    match.setB2(b2);
    match.setA1Guest(false);
    match.setA2Guest(false);
    match.setB1Guest(false);
    match.setB2Guest(false);
    match.setScoreA(11);
    match.setScoreB(9);
    ReflectionTestUtils.setField(match, "version", 4L);

    seasonAdmin = true;

    when(matchRepo.findByIdWithUsers(404L)).thenReturn(Optional.of(match));
    when(seasonRepo.findById(701L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            601L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE))
        .thenReturn(List.of());
    when(userRepo.findById(1L)).thenReturn(Optional.of(a1));
    when(userRepo.findById(2L)).thenReturn(Optional.of(a2));
    when(userRepo.findById(4L)).thenReturn(Optional.of(b2));

    String view =
        controller.submit(
            model,
            auth,
            request,
            "1",
            "2",
            "1",
            "4",
            11,
            9,
            Optional.empty(),
            701L,
            601L,
            false,
            null,
            null,
            404L);

    assertThat(view).isEqualTo("auth/logMatch");
    assertThat(model.asMap().get("toastMessage"))
        .isEqualTo("A player cannot be selected on both teams.");
    verify(matchRepo, never()).save(any(Match.class));
    assertThat(appliedMatch).isNull();
    assertThat(trophyEvaluatedMatch).isNull();
  }

  @Test
  void submit_rejectsCraftedInvalidPlayerIdOnEditBeforeMutatingMatch() {
    User logger = user(10L, "logger@test.local", false);
    User opponent = user(20L, "opponent@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(opponent), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addParameter("expectedVersion", "7");
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(602L);
    ladder.setSecurityLevel(LadderSecurity.STANDARD);
    LadderSeason season = new LadderSeason();
    setId(season, 702L);
    season.setLadderConfig(ladder);

    Match match = new Match();
    setId(match, 405L);
    match.setSeason(season);
    match.setState(MatchState.PROVISIONAL);
    match.setLoggedBy(logger);
    match.setA1(logger);
    match.setB1(opponent);
    match.setA1Guest(false);
    match.setA2Guest(true);
    match.setB1Guest(false);
    match.setB2Guest(true);
    match.setScoreA(11);
    match.setScoreB(9);
    ReflectionTestUtils.setField(match, "version", 7L);

    when(matchRepo.findByIdWithUsers(405L)).thenReturn(Optional.of(match));
    when(seasonRepo.findById(702L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            602L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(602L, 10L, LadderMembership.State.ACTIVE),
                membership(602L, 20L, LadderMembership.State.ACTIVE)));
    when(userRepo.findById(999L)).thenReturn(Optional.empty());

    String view =
        controller.submit(
            model,
            auth,
            request,
            "10",
            "",
            "999",
            "",
            11,
            9,
            Optional.empty(),
            702L,
            602L,
            false,
            null,
            null,
            405L);

    assertThat(view).isEqualTo("auth/logMatch");
    assertThat(model.asMap().get("toastMessage"))
        .asString()
        .contains("Only members of this ladder can be selected for this season.");
    assertThat(match.getB1()).isEqualTo(opponent);
    verify(matchRepo, never()).save(any(Match.class));
    verify(matchConfirmationService, never()).rebuildConfirmationRequests(any());
  }

  @Test
  void submit_rejectsCrossTeamDuplicatePlayersOnCreate() {
    User logger = user(50L, "logger@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(logger), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(901L);
    LadderSeason season = new LadderSeason();
    setId(season, 801L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(7));
    season.setEndDate(LocalDate.now().plusDays(7));

    User shared = user(2L, "shared@test.local", false);

    when(seasonRepo.findById(801L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            901L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE))
        .thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndUserId(901L, 50L))
        .thenReturn(
            Optional.of(
                membership(
                    901L, 50L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE)));
    when(userRepo.findById(2L)).thenReturn(Optional.of(shared));

    String view =
        controller.submit(
            model,
            auth,
            request,
            "50",
            "2",
            "2",
            "guest",
            11,
            9,
            Optional.empty(),
            801L,
            901L,
            false,
            null,
            null,
            null);

    assertThat(view).isEqualTo("auth/logMatch");
    assertThat(model.asMap().get("toastMessage"))
        .isEqualTo("A player cannot be selected on both teams.");
    verify(matchRepo, never()).save(any(Match.class));
    assertThat(appliedMatch).isNull();
    assertThat(trophyEvaluatedMatch).isNull();
  }

  // Access and routing coverage for session, competition, and crafted posts.

  @Test
  void submit_redirectsSessionLoggingBackToSessionWhenReturnToProvided() {
    User logger = user(50L, "logger@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(logger), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig sessionConfig = new LadderConfig();
    sessionConfig.setId(701L);
    sessionConfig.setType(LadderConfig.Type.SESSION);
    sessionConfig.setTargetSeasonId(801L);
    sessionConfig.setExpiresAt(java.time.Instant.now().plusSeconds(600));

    LadderConfig competitionConfig = new LadderConfig();
    competitionConfig.setId(901L);
    competitionConfig.setType(LadderConfig.Type.COMPETITION);

    LadderSeason targetSeason = new LadderSeason();
    setId(targetSeason, 801L);
    targetSeason.setLadderConfig(competitionConfig);
    targetSeason.setState(LadderSeason.State.ACTIVE);
    targetSeason.setStartDate(LocalDate.now().minusDays(7));
    targetSeason.setEndDate(LocalDate.now().plusDays(7));

    User opponent = user(60L, "opponent@test.local", false);

    when(ladderConfigRepository.findById(701L)).thenReturn(Optional.of(sessionConfig));
    when(seasonRepo.findById(801L)).thenReturn(Optional.of(targetSeason));
    when(membershipRepo.findByLadderConfigIdAndUserId(701L, 50L))
        .thenReturn(Optional.of(membership(701L, 50L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            701L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(701L, 50L, LadderMembership.State.ACTIVE),
                membership(701L, 60L, LadderMembership.State.ACTIVE)));
    when(userRepo.findById(60L)).thenReturn(Optional.of(opponent));
    String view =
        controller.submit(
            model,
            auth,
            request,
            null,
            "",
            "60",
            "",
            11,
            9,
            Optional.empty(),
            801L,
            701L,
            false,
            "/groups/701",
            null,
            null,
            null,
            null);

    assertThat(view).isEqualTo("redirect:/groups/701?toast=matchLogged&matchId=999");
    assertThat(appliedMatch).isNotNull();
    assertThat(appliedMatch.getSourceSessionConfig()).isEqualTo(sessionConfig);
    assertThat(trophyEvaluatedMatch).isNotNull();
  }

  @Test
  void form_inCompetitionModeUsesSessionSelectorOnly() {
    User currentUser = user(10L, "me@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig sessionLadder = new LadderConfig();
    sessionLadder.setId(701L);
    sessionLadder.setTitle("Open Session");
    sessionLadder.setType(LadderConfig.Type.SESSION);
    sessionLadder.setTargetSeasonId(801L);

    LadderConfig standardLadder = new LadderConfig();
    standardLadder.setId(702L);
    standardLadder.setTitle("Private Group");

    LadderSeason competitionSeason = new LadderSeason();
    setId(competitionSeason, 801L);
    LadderConfig competitionCfg = new LadderConfig();
    competitionCfg.setId(803L);
    competitionCfg.setType(LadderConfig.Type.COMPETITION);
    competitionSeason.setLadderConfig(competitionCfg);
    competitionSeason.setState(LadderSeason.State.ACTIVE);
    competitionSeason.setStartDate(LocalDate.now().minusDays(3));
    competitionSeason.setEndDate(LocalDate.now().plusDays(3));

    com.w3llspring.fhpb.web.model.LadderMembership sessionMembership =
        membership(701L, 10L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE);
    sessionMembership.setLadderConfig(sessionLadder);
    com.w3llspring.fhpb.web.model.LadderMembership standardMembership =
        membership(702L, 10L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE);
    standardMembership.setLadderConfig(standardLadder);

    User sessionPeer = user(11L, "peer@test.local", false);
    when(ladderConfigRepository.findById(701L)).thenReturn(Optional.of(sessionLadder));
    when(seasonRepo.findById(801L)).thenReturn(Optional.of(competitionSeason));
    when(membershipRepo.findByLadderConfigIdAndUserId(701L, 10L))
        .thenReturn(Optional.of(membership(701L, 10L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByUserIdAndState(
            10L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE))
        .thenReturn(List.of(sessionMembership, standardMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            701L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(701L, 10L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE),
                membership(
                    701L, 11L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE)));
    when(userRepo.findAllById(Set.of(10L, 11L))).thenReturn(List.of(currentUser, sessionPeer));

    String view =
        controller.form(
            model, auth, request, null, 701L, null, true, null, null, null, null, null, null);

    assertThat(view).isEqualTo("auth/logMatch");
    assertThat(model.asMap().get("competitionLogMode")).isEqualTo(Boolean.TRUE);
    assertThat(model.asMap().get("selectorMemberships"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .hasSize(1);
    @SuppressWarnings("unchecked")
    List<User> users = (List<User>) model.asMap().get("users");
    assertThat(users).extracting(User::getId).containsExactly(10L, 11L);
    assertThat(model.asMap().get("navHomePath")).isEqualTo("/home");
    assertThat(model.asMap()).doesNotContainKeys("season", "courtNameByUser");
  }

  @Test
  void form_allowsSessionOwnerToLogCompetitionMatchWithoutParticipating() {
    User currentUser = user(10L, "owner@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig sessionLadder = new LadderConfig();
    sessionLadder.setId(711L);
    sessionLadder.setTitle("Hosted Session");
    sessionLadder.setType(LadderConfig.Type.SESSION);
    sessionLadder.setTargetSeasonId(811L);
    sessionLadder.setOwnerUserId(10L);

    LadderSeason competitionSeason = new LadderSeason();
    setId(competitionSeason, 811L);
    LadderConfig competitionCfg = new LadderConfig();
    competitionCfg.setId(813L);
    competitionCfg.setType(LadderConfig.Type.COMPETITION);
    competitionSeason.setLadderConfig(competitionCfg);
    competitionSeason.setState(LadderSeason.State.ACTIVE);
    competitionSeason.setStartDate(LocalDate.now().minusDays(3));
    competitionSeason.setEndDate(LocalDate.now().plusDays(3));

    LadderMembership ownerMembership =
        membership(sessionLadder, 10L, LadderMembership.State.ACTIVE, LadderMembership.Role.MEMBER);
    User sessionPeer = user(11L, "peer@test.local", false);

    when(ladderConfigRepository.findById(711L)).thenReturn(Optional.of(sessionLadder));
    when(seasonRepo.findById(811L)).thenReturn(Optional.of(competitionSeason));
    when(membershipRepo.findByLadderConfigIdAndUserId(711L, 10L))
        .thenReturn(Optional.of(ownerMembership));
    when(membershipRepo.findByUserIdAndState(10L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(ownerMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            711L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                ownerMembership,
                membership(
                    sessionLadder,
                    11L,
                    LadderMembership.State.ACTIVE,
                    LadderMembership.Role.MEMBER)));
    when(userRepo.findAllById(Set.of(10L, 11L))).thenReturn(List.of(currentUser, sessionPeer));

    String view =
        controller.form(
            model, auth, request, null, 711L, null, true, null, null, null, null, null, null);

    assertThat(view).isEqualTo("auth/logMatch");
    assertThat(model.asMap().get("quickLogAdminMode")).isEqualTo(Boolean.TRUE);
    assertThat(model.asMap().get("selectedA1")).isEqualTo(10L);
    assertThat(model.asMap().get("hasSeasonAdminOverride")).isEqualTo(Boolean.FALSE);
  }

  @Test
  void form_forSessionClassicLoggingUsesPlainHomeNavTarget() {
    User currentUser = user(10L, "me@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig sessionLadder = new LadderConfig();
    sessionLadder.setId(701L);
    sessionLadder.setTitle("Open Session");
    sessionLadder.setType(LadderConfig.Type.SESSION);
    sessionLadder.setTargetSeasonId(801L);
    sessionLadder.setExpiresAt(java.time.Instant.now().plusSeconds(600));

    LadderSeason competitionSeason = new LadderSeason();
    setId(competitionSeason, 801L);
    LadderConfig competitionCfg = new LadderConfig();
    competitionCfg.setId(803L);
    competitionCfg.setType(LadderConfig.Type.COMPETITION);
    competitionSeason.setLadderConfig(competitionCfg);
    competitionSeason.setState(LadderSeason.State.ACTIVE);
    competitionSeason.setStartDate(LocalDate.now().minusDays(3));
    competitionSeason.setEndDate(LocalDate.now().plusDays(3));

    com.w3llspring.fhpb.web.model.LadderMembership sessionMembership =
        membership(701L, 10L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE);
    sessionMembership.setLadderConfig(sessionLadder);

    User sessionPeer = user(11L, "peer@test.local", false);
    when(ladderConfigRepository.findById(701L)).thenReturn(Optional.of(sessionLadder));
    when(seasonRepo.findById(801L)).thenReturn(Optional.of(competitionSeason));
    when(membershipRepo.findByLadderConfigIdAndUserId(701L, 10L))
        .thenReturn(Optional.of(membership(701L, 10L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByUserIdAndState(
            10L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE))
        .thenReturn(List.of(sessionMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            701L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(701L, 10L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE),
                membership(
                    701L, 11L, com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE)));
    when(userRepo.findAllById(Set.of(10L, 11L))).thenReturn(List.of(currentUser, sessionPeer));

    String view =
        controller.form(
            model, auth, request, 801L, 701L, null, false, null, null, null, null, null, null);

    assertThat(view).isEqualTo("auth/logMatch");
    assertThat(model.asMap().get("navHomePath")).isEqualTo("/home");
  }

  @Test
  void form_blocks_standard_ladder_nonmember_from_viewing_roster() {
    User currentUser = user(10L, "viewer@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();
    LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));

    LadderConfig ladder = new LadderConfig();
    ladder.setId(501L);
    ladder.setType(LadderConfig.Type.STANDARD);

    LadderSeason season = new LadderSeason();
    setId(season, 601L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(today.minusDays(3));
    season.setEndDate(today.plusDays(3));

    when(ladderConfigRepository.findById(501L)).thenReturn(Optional.of(ladder));
    when(seasonRepo
            .findFirstByLadderConfigIdAndStateAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                501L, LadderSeason.State.ACTIVE, today, today))
        .thenReturn(Optional.of(season));
    when(seasonRepo.findById(601L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            501L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership(501L, 11L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndUserId(501L, 10L)).thenReturn(Optional.empty());
    when(membershipRepo.findByUserIdAndState(10L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of());

    String view =
        controller.form(
            model, auth, request, null, 501L, null, false, null, null, null, null, null, null);

    assertThat(view).isEqualTo("redirect:/home?toast=forbidden");
    verify(userRepo, never()).findAllById(any());
  }

  @Test
  void submit_blocks_standard_ladder_nonmember_crafted_post() {
    User currentUser = user(10L, "viewer@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(701L);
    ladder.setType(LadderConfig.Type.STANDARD);

    LadderSeason season = new LadderSeason();
    setId(season, 801L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(3));
    season.setEndDate(LocalDate.now().plusDays(3));

    when(seasonRepo.findById(801L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            701L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership(701L, 11L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndUserId(701L, 10L)).thenReturn(Optional.empty());

    String view =
        controller.submit(
            model,
            auth,
            request,
            "10",
            "",
            "11",
            "",
            11,
            7,
            Optional.empty(),
            801L,
            null,
            false,
            null,
            null,
            null);

    assertThat(view).isEqualTo("redirect:/home?toast=forbidden");
    verify(matchRepo, never()).save(any(Match.class));
  }

  // Tournament-specific logging behavior.

  @Test
  void submit_rejectsTournamentMatchWithoutActiveRoundRobin() {
    User logger = user(50L, "logger@test.local", false);
    User opponent = user(60L, "opponent@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(logger), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(901L);
    ladder.setTournamentMode(true);
    ladder.setSecurityLevel(LadderSecurity.STANDARD);

    LadderSeason season = new LadderSeason();
    setId(season, 801L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(3));
    season.setEndDate(LocalDate.now().plusDays(3));

    when(seasonRepo.findById(801L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            901L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(901L, 50L, LadderMembership.State.ACTIVE),
                membership(901L, 60L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndUserId(901L, 50L))
        .thenReturn(Optional.of(membership(901L, 50L, LadderMembership.State.ACTIVE)));
    when(userRepo.findById(60L)).thenReturn(Optional.of(opponent));
    RoundRobinService roundRobinService =
        new RoundRobinService(null, null, null, null, null, null, null) {
          @Override
          public RoundRobinEntry reserveTournamentEntry(
              LadderSeason season, Long entryId, User a1, User a2, User b1, User b2) {
            throw new RoundRobinModificationException(
                "Tournament matches must be assigned to an active round-robin matchup.");
          }
        };
    org.springframework.test.util.ReflectionTestUtils.setField(
        controller, "roundRobinService", roundRobinService);
    org.springframework.test.util.ReflectionTestUtils.setField(
        matchStateTransitionService, "roundRobinService", roundRobinService);

    String view =
        controller.submit(
            model,
            auth,
            request,
            "50",
            "",
            "60",
            "",
            11,
            7,
            Optional.empty(),
            801L,
            901L,
            false,
            null,
            null,
            null,
            null);

    assertThat(view).isEqualTo("auth/logMatch");
    assertThat(model.asMap().get("toastMessage"))
        .isEqualTo("Tournament matches must be assigned to an active round-robin matchup.");
    assertThat(appliedMatch).isNull();
    assertThat(trophyEvaluatedMatch).isNull();
  }

  @Test
  void submit_linksTournamentMatchToReservedRoundRobinEntry() {
    User logger = user(50L, "logger@test.local", false);
    User opponent = user(60L, "opponent@test.local", false);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(logger), null, List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    Model model = new ExtendedModelMap();

    LadderConfig ladder = new LadderConfig();
    ladder.setId(902L);
    ladder.setTournamentMode(true);
    ladder.setSecurityLevel(LadderSecurity.STANDARD);

    LadderSeason season = new LadderSeason();
    setId(season, 802L);
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartDate(LocalDate.now().minusDays(3));
    season.setEndDate(LocalDate.now().plusDays(3));

    RoundRobinEntry reservedEntry = new RoundRobinEntry();
    setId(reservedEntry, 777L);
    final Long[] linkedMatchId = new Long[1];

    when(seasonRepo.findById(802L)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            902L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(902L, 50L, LadderMembership.State.ACTIVE),
                membership(902L, 60L, LadderMembership.State.ACTIVE)));
    when(membershipRepo.findByLadderConfigIdAndUserId(902L, 50L))
        .thenReturn(Optional.of(membership(902L, 50L, LadderMembership.State.ACTIVE)));
    when(userRepo.findById(60L)).thenReturn(Optional.of(opponent));
    RoundRobinService roundRobinService =
        new RoundRobinService(null, null, null, null, null, null, null) {
          @Override
          public RoundRobinEntry reserveTournamentEntry(
              LadderSeason season, Long entryId, User a1, User a2, User b1, User b2) {
            assertThat(entryId).isEqualTo(777L);
            return reservedEntry;
          }

          @Override
          public RoundRobinEntry linkEntryToMatch(RoundRobinEntry entry, Long matchId) {
            linkedMatchId[0] = matchId;
            return entry;
          }
        };
    org.springframework.test.util.ReflectionTestUtils.setField(
        controller, "roundRobinService", roundRobinService);
    org.springframework.test.util.ReflectionTestUtils.setField(
        matchStateTransitionService, "roundRobinService", roundRobinService);

    String view =
        controller.submit(
            model,
            auth,
            request,
            "50",
            "",
            "60",
            "",
            11,
            7,
            Optional.empty(),
            802L,
            902L,
            false,
            123L,
            777L,
            1,
            null);

    assertThat(view)
        .isEqualTo("redirect:/round-robin/view/123?round=1&toast=matchLogged&matchId=999");
    assertThat(linkedMatchId[0]).isEqualTo(999L);
    assertThat(appliedMatch).isNotNull();
    assertThat(trophyEvaluatedMatch).isNotNull();
  }

  private static User user(Long id, String email, boolean admin) {
    User user = new User();
    user.setId(id);
    user.setEmail(email);
    user.setNickName(email != null ? email.split("@")[0] : "user" + id);
    user.setAdmin(admin);
    return user;
  }

  private static com.w3llspring.fhpb.web.model.LadderMembership membership(
      Long ladderId, Long userId, com.w3llspring.fhpb.web.model.LadderMembership.State state) {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(ladderId);

    com.w3llspring.fhpb.web.model.LadderMembership membership =
        new com.w3llspring.fhpb.web.model.LadderMembership();
    membership.setLadderConfig(ladder);
    membership.setUserId(userId);
    membership.setState(state);
    return membership;
  }

  private static com.w3llspring.fhpb.web.model.LadderMembership membership(
      LadderConfig ladder,
      Long userId,
      com.w3llspring.fhpb.web.model.LadderMembership.State state,
      com.w3llspring.fhpb.web.model.LadderMembership.Role role) {
    com.w3llspring.fhpb.web.model.LadderMembership membership =
        new com.w3llspring.fhpb.web.model.LadderMembership();
    membership.setLadderConfig(ladder);
    membership.setUserId(userId);
    membership.setState(state);
    membership.setRole(role);
    return membership;
  }

  private static void setId(Object obj, long id) {
    try {
      java.lang.reflect.Field f = obj.getClass().getDeclaredField("id");
      f.setAccessible(true);
      f.set(obj, id);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
