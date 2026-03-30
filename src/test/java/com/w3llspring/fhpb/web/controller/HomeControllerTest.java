package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.competition.HomeController;
import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
import com.w3llspring.fhpb.web.service.CompetitionDisplayNameModerationService;
import com.w3llspring.fhpb.web.service.CompetitionSeasonService;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.MatchDashboardService;
import com.w3llspring.fhpb.web.service.MatchRowModel;
import com.w3llspring.fhpb.web.service.MatchRowModelBuilder;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.competition.CompetitionSessionService;
import com.w3llspring.fhpb.web.service.competition.HomeSelectionService;
import com.w3llspring.fhpb.web.service.dashboard.MatchDashboardViewService;
import com.w3llspring.fhpb.web.service.matchentry.MatchEntryContextService;
import com.w3llspring.fhpb.web.service.scoring.BalancedV1LadderScoringAlgorithm;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms;
import com.w3llspring.fhpb.web.service.scoring.MarginCurveV1LadderScoringAlgorithm;
import com.w3llspring.fhpb.web.service.standings.SeasonStandingsViewService;
import com.w3llspring.fhpb.web.service.standings.StandingsPageService;
import com.w3llspring.fhpb.web.service.user.UserOnboardingService;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import com.w3llspring.fhpb.web.session.LadderPageState;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

  @Mock private BandPositionRepository posRepo;

  @Mock private LadderMatchLinkRepository linkRepo;

  @Mock private LadderSeasonRepository seasonRepo;

  @Mock private LadderStandingRepository standingRepo;

  @Mock private com.w3llspring.fhpb.web.service.LadderSecurityService ladderSecurityService;

  @Mock private com.w3llspring.fhpb.web.db.MatchRepository matchRepo;

  @Mock private com.w3llspring.fhpb.web.db.RoundRobinEntryRepository rrEntryRepo;

  @Mock private com.w3llspring.fhpb.web.service.LadderImprovementAdvisor improvementAdvisor;

  @Mock private LadderMembershipRepository membershipRepo;

  @Mock private LadderConfigRepository ladderConfigRepo;
  @Mock private UserOnboardingMarkerRepository userOnboardingMarkerRepository;

  @Mock private MatchConfirmationService matchConfirmationService;
  private RecordingCompetitionDisplayNameModerationService competitionDisplayNameModerationService;
  private CompetitionSeasonService competitionSeasonService;

  private HomeController controller;
  private com.w3llspring.fhpb.web.service.LadderV2Service ladderService;
  private StoryModeService storyModeService;
  private com.w3llspring.fhpb.web.service.LadderAccessService access;
  private MatchRowModelBuilder matchRowModelBuilder;
  private LadderSeason activeCompetitionSeason;

  private HomeController createController() {
    HomeController created =
        new HomeController(
            posRepo,
            linkRepo,
            matchRepo,
            rrEntryRepo,
            seasonRepo,
            ladderSecurityService,
            improvementAdvisor,
            membershipRepo,
            ladderConfigRepo,
            access,
            matchConfirmationService,
            matchRowModelBuilder,
            storyModeService,
            new HomeSelectionService(membershipRepo, ladderConfigRepo, seasonRepo),
            new CompetitionSessionService(seasonRepo),
            new StandingsPageService(),
            new SeasonStandingsViewService(
                standingRepo,
                ladderService,
                Optional.ofNullable(competitionDisplayNameModerationService)),
            new MatchDashboardViewService(),
            new MatchEntryContextService(courtNameService()),
            new UserOnboardingService(userOnboardingMarkerRepository));
    ReflectionTestUtils.setField(created, "competitionSeasonService", competitionSeasonService);
    ReflectionTestUtils.setField(
        created,
        "competitionDisplayNameModerationService",
        competitionDisplayNameModerationService);
    return created;
  }

  private CourtNameService courtNameService() {
    return new CourtNameService() {
      @Override
      public Map<Long, Set<String>> gatherCourtNamesForUsers(
          Collection<Long> userIds, Long ladderId) {
        return Map.of();
      }

      @Override
      public Set<String> gatherCourtNamesForUser(long userId, Long ladderConfigId) {
        return Set.of();
      }
    };
  }

  @AfterEach
  void tearDown() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
  }

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    activeCompetitionSeason = null;
    ladderService =
        new com.w3llspring.fhpb.web.service.LadderV2Service(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new com.w3llspring.fhpb.web.service.SeasonNameGenerator(),
            new LadderScoringAlgorithms(
                java.util.List.of(
                    new MarginCurveV1LadderScoringAlgorithm(),
                    new BalancedV1LadderScoringAlgorithm())),
            null,
            null,
            null,
            null) {
          @Override
          public java.util.List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>
              buildDisplayRows(java.util.List<LadderStanding> standings) {
            return java.util.List.of();
          }
        };
    storyModeService =
        new StoryModeService(null, null, null, null, null, null) {
          @Override
          public StoryPageModel buildPage(LadderSeason season, User viewer) {
            return StoryPageModel.disabled();
          }
        };
    competitionDisplayNameModerationService =
        new RecordingCompetitionDisplayNameModerationService();
    competitionSeasonService =
        new CompetitionSeasonService(null, null, null) {
          @Override
          public LadderSeason resolveActiveCompetitionSeason() {
            return activeCompetitionSeason;
          }
        };
    access =
        new com.w3llspring.fhpb.web.service.LadderAccessService(null, null) {
          @Override
          public boolean isSeasonAdmin(Long seasonId, User user) {
            return false;
          }
        };
    matchRowModelBuilder =
        new MatchRowModelBuilder(null, null, null) {
          @Override
          public MatchRowModel buildFor(
              User viewer,
              java.util.List<Match> matches,
              java.util.Set<Long> precomputedPendingMatchIds) {
            return new MatchRowModel(
                java.util.Set.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of());
          }
        };
    controller = createController();
  }

  @Test
  void logMatchHubAddsDashboardServiceAttributesToModel() {
    // Arrange: create a logged-in user in SecurityContext
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    // Minimal membership/season/ladders to allow controller path to run
    LadderConfig cfg = new LadderConfig();
    cfg.setId(7L);
    cfg.setTitle("Ladder X");
    LadderMembership m = new LadderMembership();
    m.setLadderConfig(cfg);
    m.setUserId(123L);
    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(m));
    when(ladderConfigRepo.findById(7L)).thenReturn(java.util.Optional.of(cfg));

    LadderSeason s = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(s, "id", 55L);
    s.setLadderConfig(cfg);
    s.setName("S");
    s.setState(LadderSeason.State.ACTIVE);
    when(seasonRepo.findByLadderConfigIdOrderByStartDateDesc(7L)).thenReturn(List.of(s));

    MatchRowModel canned =
        new MatchRowModel(
            Set.of(11L),
            Map.of(22L, "Alice"),
            Map.of(22L, true),
            Map.of(11L, true),
            Map.of(33L, true),
            Map.of(11L, false),
            Map.of(11L, false));
    controller = createController();
    MatchDashboardService dashboardService =
        new MatchDashboardService(null, null, null, null, null) {
          @Override
          public DashboardModel buildPendingForUserInSeason(User viewer, LadderSeason season) {
            return new DashboardModel(List.of(), canned);
          }
        };
    ReflectionTestUtils.setField(controller, "matchDashboardService", dashboardService);
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/match-log");
    LadderPageState homeState = new LadderPageState();

    // Act
    String view = controller.logMatchHub(7L, 55L, homeState, request, model);

    // Assert: model contains the attributes we expect from the builder
    assertThat(view).isEqualTo("auth/log-match-app");
    assertThat(model.get("confirmableMatchIds")).isEqualTo(canned.getConfirmableMatchIds());
    assertThat(model.get("confirmerByMatchId")).isEqualTo(canned.getConfirmerByMatchId());
    assertThat(model.get("casualAutoConfirmedByMatchId"))
        .isEqualTo(canned.getCasualAutoConfirmedByMatchId());
    assertThat(model.get("pendingByMatchId")).isEqualTo(canned.getPendingByMatchId());
    assertThat(model.get("waitingOnOpponentByMatchId"))
        .isEqualTo(canned.getWaitingOnOpponentByMatchId());
    assertThat(model.get("editableByMatchId")).isEqualTo(canned.getEditableByMatchId());
  }

  @Test
  void standingsDoesNotAddDashboardServiceAttributesToModel() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig cfg = new LadderConfig();
    cfg.setId(7L);
    cfg.setTitle("Ladder X");
    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(cfg);
    membership.setUserId(123L);
    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership));
    when(ladderConfigRepo.findById(7L)).thenReturn(java.util.Optional.of(cfg));

    LadderSeason season = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(season, "id", 55L);
    season.setLadderConfig(cfg);
    season.setName("S");
    season.setState(LadderSeason.State.ACTIVE);
    when(seasonRepo.findByLadderConfigIdOrderByStartDateDesc(7L)).thenReturn(List.of(season));
    when(standingRepo.findBySeasonOrderByRankNoAscWithUser(season)).thenReturn(List.of());

    controller = createController();
    MatchDashboardService dashboardService =
        new MatchDashboardService(null, null, null, null, null) {
          @Override
          public DashboardModel buildPendingForUserInSeason(
              User viewer, LadderSeason ladderSeason) {
            throw new AssertionError("Standings should not load dashboard data");
          }
        };
    ReflectionTestUtils.setField(controller, "matchDashboardService", dashboardService);

    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/standings");
    LadderPageState homeState = new LadderPageState();

    String view = controller.standings(7L, 55L, homeState, request, model);

    assertThat(view).isEqualTo("auth/standings");
    assertThat(model.get("links")).isNull();
    assertThat(model.get("confirmableMatchIds")).isNull();
    assertThat(model.get("confirmerByMatchId")).isNull();
    assertThat(model.get("pendingByMatchId")).isNull();
    assertThat(model.get("waitingOnOpponentByMatchId")).isNull();
    assertThat(model.get("editableByMatchId")).isNull();
    assertThat(model.get("voiceLanguage")).isNull();
    assertThat(model.get("voicePhraseHints")).isNull();
  }

  @Test
  void confirmMatchesUsesDashboardServiceModel() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    MatchRowModel canned =
        new MatchRowModel(
            Set.of(44L),
            Map.of(44L, "Jordan"),
            Map.of(44L, true),
            Map.of(44L, true),
            Map.of(77L, true),
            Map.of(44L, false),
            Map.of(44L, false));
    MatchDashboardService dashboardService =
        new MatchDashboardService(null, null, null, null, null) {
          @Override
          public DashboardModel buildPendingForUserInSeason(User viewer, LadderSeason season) {
            return new DashboardModel(List.of(), canned);
          }
        };
    ReflectionTestUtils.setField(controller, "matchDashboardService", dashboardService);

    ExtendedModelMap model = new ExtendedModelMap();
    String view = controller.confirmMatches(null, null, null, model);

    assertThat(view).isEqualTo("auth/confirm-matches");
    assertThat(model.get("returnToPath")).isEqualTo("/confirm-matches");
    assertThat(model.get("confirmableMatchIds")).isEqualTo(canned.getConfirmableMatchIds());
    assertThat(model.get("confirmerByMatchId")).isEqualTo(canned.getConfirmerByMatchId());
    assertThat(model.get("casualAutoConfirmedByMatchId"))
        .isEqualTo(canned.getCasualAutoConfirmedByMatchId());
    assertThat(model.get("pendingByMatchId")).isEqualTo(canned.getPendingByMatchId());
    assertThat(model.get("waitingOnOpponentByMatchId"))
        .isEqualTo(canned.getWaitingOnOpponentByMatchId());
  }

  @Test
  void competitionSessionsShowsSessionMembershipsAndChooserState() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig joinedSession = new LadderConfig();
    joinedSession.setId(8L);
    joinedSession.setTitle("Joined Session");
    joinedSession.setType(LadderConfig.Type.SESSION);
    joinedSession.setOwnerUserId(555L);
    LadderMembership joinedMembership = new LadderMembership();
    joinedMembership.setLadderConfig(joinedSession);
    joinedMembership.setUserId(123L);

    LadderConfig ownedSession = new LadderConfig();
    ownedSession.setId(9L);
    ownedSession.setTitle("Owned Session");
    ownedSession.setType(LadderConfig.Type.SESSION);
    ownedSession.setOwnerUserId(123L);
    LadderMembership ownedMembership = new LadderMembership();
    ownedMembership.setLadderConfig(ownedSession);
    ownedMembership.setUserId(123L);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(joinedMembership, ownedMembership));

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.competitionSessions(model);

    assertThat(view).isEqualTo("auth/competition-session-picker");
    assertThat(model.get("sessionMemberships"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .hasSize(2);
    assertThat(model.get("showCompetitionSessionChooser")).isEqualTo(true);
    assertThat(model.get("activeCompetitionSessionCount")).isEqualTo(2);
    assertThat(model.get("activeCompetitionSessionId")).isEqualTo(9L);
    assertThat(model.get("canCreateCompetitionSession")).isEqualTo(false);
  }

  @Test
  void competitionSessionsAllowsCreationWhenUserOnlyJoinedSessions() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig joinedSession = new LadderConfig();
    joinedSession.setId(8L);
    joinedSession.setTitle("Joined Session");
    joinedSession.setType(LadderConfig.Type.SESSION);
    joinedSession.setOwnerUserId(555L);
    LadderMembership joinedMembership = new LadderMembership();
    joinedMembership.setLadderConfig(joinedSession);
    joinedMembership.setUserId(123L);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(joinedMembership));

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.competitionSessions(model);

    assertThat(view).isEqualTo("auth/competition-session-picker");
    assertThat(model.get("sessionMemberships"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .hasSize(1);
    assertThat(model.get("showCompetitionSessionChooser")).isEqualTo(true);
    assertThat(model.get("activeCompetitionSessionCount")).isEqualTo(1);
    assertThat(model.get("activeCompetitionSessionId")).isEqualTo(8L);
    assertThat(model.get("canCreateCompetitionSession")).isEqualTo(true);
  }

  @Test
  void accountMenuShowsUserAccountHub() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.accountMenu(model);

    assertThat(view).isEqualTo("auth/account-menu");
    assertThat(model.get("userName")).isEqualTo("Tester");
    assertThat(model.get("showLadderSelection")).isEqualTo(false);
  }

  @Test
  void homeHidesStartHereCalloutAfterAnyMembershipHistoryEvenWithoutActiveMemberships() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of());
    when(membershipRepo.existsByUserId(123L)).thenReturn(true);
    when(userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
            123L, UserOnboardingService.HOME_TOUR_V1))
        .thenReturn(true);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    LadderPageState homeState = new LadderPageState();
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.home(null, null, null, null, null, homeState, request, model);

    assertThat(view).isEqualTo("auth/home");
    assertThat(model.get("showStartHereCallout")).isEqualTo(false);
    assertThat(model.get("showHomeIntro")).isEqualTo(false);
  }

  @Test
  void homeShowsIntroWhenPersistentMarkerHasNotBeenCompleted() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of());
    when(membershipRepo.existsByUserId(123L)).thenReturn(false);
    when(userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
            123L, UserOnboardingService.HOME_TOUR_V1))
        .thenReturn(false);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    LadderPageState homeState = new LadderPageState();
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.home(null, null, null, null, null, homeState, request, model);

    assertThat(view).isEqualTo("auth/home");
    assertThat(model.get("showHomeIntro")).isEqualTo(true);
  }

  @Test
  void completeHomeIntroMarksPersistentCompletion() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    when(userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
            123L, UserOnboardingService.HOME_TOUR_V1))
        .thenReturn(false);
    when(userOnboardingMarkerRepository.saveAndFlush(any(UserOnboardingMarker.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    org.springframework.http.ResponseEntity<Void> response = controller.completeHomeIntro();

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    verify(userOnboardingMarkerRepository)
        .saveAndFlush(
            argThat(
                marker ->
                    marker != null
                        && marker.getUserId().equals(123L)
                        && UserOnboardingService.HOME_TOUR_V1.equals(marker.getMarkerKey())
                        && marker.getCompletedAt() != null));
  }

  @Test
  void homeLauncherIgnoresCachedHistoricSeasonContext() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig cfg = new LadderConfig();
    cfg.setId(7L);
    cfg.setTitle("Ladder X");
    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(cfg);
    membership.setUserId(123L);
    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    MockHttpSession session = new MockHttpSession();
    com.w3llspring.fhpb.web.session.UserSessionState sessionState =
        new com.w3llspring.fhpb.web.session.UserSessionState();
    sessionState.setSelectedGroupId(7L);
    session.setAttribute(
        com.w3llspring.fhpb.web.session.UserSessionState.SESSION_KEY, sessionState);
    request.setSession(session);

    LadderPageState homeState = new LadderPageState();
    homeState.ladderId = 7L;
    homeState.seasonId = 44L;

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.home(null, null, null, null, null, homeState, request, model);

    assertThat(view).isEqualTo("auth/home");
    assertThat(model.get("ladderId")).isNull();
    assertThat(model.get("seasonId")).isNull();
    assertThat(homeState.seasonId).isNull();
  }

  @Test
  void homeRedirectsExplicitPrivateGroupSelectionToPrivateGroupHub() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig standardCfg = new LadderConfig();
    standardCfg.setId(7L);
    standardCfg.setTitle("Ladder X");
    LadderMembership standardMembership = new LadderMembership();
    standardMembership.setLadderConfig(standardCfg);
    standardMembership.setUserId(123L);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(standardMembership));
    when(ladderConfigRepo.findById(7L)).thenReturn(java.util.Optional.of(standardCfg));

    LadderSeason activeSeason = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(activeSeason, "id", 55L);
    activeSeason.setLadderConfig(standardCfg);
    activeSeason.setName("Active");
    activeSeason.setState(LadderSeason.State.ACTIVE);

    when(seasonRepo.findByLadderConfigIdOrderByStartDateDesc(7L)).thenReturn(List.of(activeSeason));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    LadderPageState homeState = new LadderPageState();
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.home(7L, 55L, null, null, null, homeState, request, model);

    assertThat(view).isEqualTo("redirect:/private-groups/7?seasonId=55");
  }

  @Test
  void homeExposesPreferredActiveCompetitionSessionForStartPlaying() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig joinedSession = new LadderConfig();
    joinedSession.setId(8L);
    joinedSession.setTitle("Joined Session");
    joinedSession.setType(LadderConfig.Type.SESSION);
    joinedSession.setOwnerUserId(555L);
    joinedSession.setCreatedAt(Instant.now().minusSeconds(60));
    LadderMembership joinedMembership = new LadderMembership();
    joinedMembership.setLadderConfig(joinedSession);
    joinedMembership.setUserId(123L);
    joinedMembership.setJoinedAt(Instant.now());

    LadderConfig ownedSession = new LadderConfig();
    ownedSession.setId(9L);
    ownedSession.setTitle("Owned Session");
    ownedSession.setType(LadderConfig.Type.SESSION);
    ownedSession.setOwnerUserId(123L);
    ownedSession.setCreatedAt(Instant.now().minusSeconds(3600));
    LadderMembership ownedMembership = new LadderMembership();
    ownedMembership.setLadderConfig(ownedSession);
    ownedMembership.setUserId(123L);
    ownedMembership.setJoinedAt(Instant.now().minusSeconds(3600));

    LadderConfig standardCfg = new LadderConfig();
    standardCfg.setId(7L);
    standardCfg.setTitle("Ladder X");
    LadderMembership standardMembership = new LadderMembership();
    standardMembership.setLadderConfig(standardCfg);
    standardMembership.setUserId(123L);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(joinedMembership, ownedMembership, standardMembership));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    LadderPageState homeState = new LadderPageState();
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.home(null, null, null, null, null, homeState, request, model);

    assertThat(view).isEqualTo("auth/home");
    assertThat(model.get("activeCompetitionSessionId")).isEqualTo(9L);
    assertThat(model.get("activeCompetitionSessionTitle")).isEqualTo("Owned Session");
    assertThat(model.get("showCompetitionSessionChooser")).isEqualTo(true);
    assertThat(model.get("activeCompetitionSessionCount")).isEqualTo(2);
  }

  @Test
  void homeShowsCompetitionSessionChooserWhenUserOnlyJoinedSomeoneElsesSession() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig joinedSession = new LadderConfig();
    joinedSession.setId(8L);
    joinedSession.setTitle("Joined Session");
    joinedSession.setType(LadderConfig.Type.SESSION);
    joinedSession.setOwnerUserId(555L);
    joinedSession.setCreatedAt(Instant.now().minusSeconds(60));
    LadderMembership joinedMembership = new LadderMembership();
    joinedMembership.setLadderConfig(joinedSession);
    joinedMembership.setUserId(123L);
    joinedMembership.setJoinedAt(Instant.now());

    LadderConfig standardCfg = new LadderConfig();
    standardCfg.setId(7L);
    standardCfg.setTitle("Ladder X");
    LadderMembership standardMembership = new LadderMembership();
    standardMembership.setLadderConfig(standardCfg);
    standardMembership.setUserId(123L);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(joinedMembership, standardMembership));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    LadderPageState homeState = new LadderPageState();
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.home(null, null, null, null, null, homeState, request, model);

    assertThat(view).isEqualTo("auth/home");
    assertThat(model.get("activeCompetitionSessionId")).isEqualTo(8L);
    assertThat(model.get("showCompetitionSessionChooser")).isEqualTo(true);
    assertThat(model.get("activeCompetitionSessionCount")).isEqualTo(1);
  }

  @Test
  void homeRedirectsDirectCompetitionSelectionToCompetitionPage() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig competition = new LadderConfig();
    competition.setId(99L);
    competition.setTitle("Competition");
    competition.setType(LadderConfig.Type.COMPETITION);
    when(ladderConfigRepo.findById(99L)).thenReturn(java.util.Optional.of(competition));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    LadderPageState homeState = new LadderPageState();
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.home(99L, null, null, null, null, homeState, request, model);

    assertThat(view).isEqualTo("redirect:/competition");
  }

  @Test
  void competitionLogMatchUsesSessionMembershipsOnly() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig sessionCfg = new LadderConfig();
    sessionCfg.setId(9L);
    sessionCfg.setTitle("Open Session");
    sessionCfg.setType(LadderConfig.Type.SESSION);
    sessionCfg.setTargetSeasonId(77L);

    LadderMembership sessionMembership = new LadderMembership();
    sessionMembership.setLadderConfig(sessionCfg);
    sessionMembership.setUserId(123L);

    LadderConfig standardCfg = new LadderConfig();
    standardCfg.setId(7L);
    standardCfg.setTitle("Ladder X");

    LadderMembership standardMembership = new LadderMembership();
    standardMembership.setLadderConfig(standardCfg);
    standardMembership.setUserId(123L);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(sessionMembership, standardMembership));

    LadderConfig competitionCfg = new LadderConfig();
    competitionCfg.setId(99L);
    competitionCfg.setTitle("Global League");
    competitionCfg.setType(LadderConfig.Type.COMPETITION);

    LadderSeason competitionSeason = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(competitionSeason, "id", 77L);
    competitionSeason.setLadderConfig(competitionCfg);
    competitionSeason.setName("Spring Global");
    competitionSeason.setState(LadderSeason.State.ACTIVE);
    activeCompetitionSeason = competitionSeason;
    when(seasonRepo.findById(77L)).thenReturn(java.util.Optional.of(competitionSeason));

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.competitionLogMatch(9L, null, null, null, model);

    assertThat(view).isEqualTo("auth/competition-log-match");
    assertThat(model.get("competitionLogMode")).isEqualTo(Boolean.TRUE);
    assertThat(model.get("ladderId")).isEqualTo(9L);
    assertThat(model.get("returnToPath")).isEqualTo("/competition/log-match?ladderId=9");
    assertThat(model.get("navHomePath")).isEqualTo("/home");
    assertThat(model.get("selectedSessionTitle")).isEqualTo("Open Session");
    assertThat(model.get("selectorMemberships"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .hasSize(1);
  }

  @Test
  void competitionPageUsesActiveCompetitionSeasonWithoutMembership() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig competition = new LadderConfig();
    competition.setId(99L);
    competition.setTitle("Competition");
    competition.setType(LadderConfig.Type.COMPETITION);

    LadderSeason season = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(season, "id", 55L);
    season.setLadderConfig(competition);
    season.setName("Spring");
    season.setState(LadderSeason.State.ACTIVE);

    activeCompetitionSeason = season;
    when(standingRepo.findBySeasonOrderByRankNoAscWithUser(season))
        .thenReturn(List.of(new LadderStanding()));

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.competition(null, null, null, null, null, "all", false, model);

    assertThat(view).isEqualTo("auth/competition");
    assertThat(model.get("competitionUnavailable")).isEqualTo(Boolean.FALSE);
    assertThat(model.get("competitionLadderId")).isEqualTo(99L);
    assertThat(model.get("competitionSeasonId")).isEqualTo(55L);
  }

  @Test
  void competitionPageAppliesSafeDisplayNameOverridesAndReportState() {
    User viewer = new User();
    viewer.setId(123L);
    viewer.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    User target = new User();
    target.setId(456L);
    target.setNickName("OriginalName");
    target.setPublicCode("PB-TARGET1");

    LadderConfig competition = new LadderConfig();
    competition.setId(99L);
    competition.setTitle("Competition");
    competition.setType(LadderConfig.Type.COMPETITION);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 55L);
    season.setLadderConfig(competition);
    season.setName("Spring");
    season.setState(LadderSeason.State.ACTIVE);

    LadderStanding standing = new LadderStanding();
    standing.setSeason(season);
    standing.setUser(target);
    standing.setRank(1);
    standing.setDisplayName("OriginalName");
    standing.setPoints(20);

    ladderService =
        new com.w3llspring.fhpb.web.service.LadderV2Service(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new com.w3llspring.fhpb.web.service.SeasonNameGenerator(),
            new LadderScoringAlgorithms(
                java.util.List.of(
                    new MarginCurveV1LadderScoringAlgorithm(),
                    new BalancedV1LadderScoringAlgorithm())),
            null,
            null,
            null,
            null) {
          @Override
          public java.util.List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>
              buildDisplayRows(java.util.List<LadderStanding> standings) {
            com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow row =
                new com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow();
            row.userId = 456L;
            row.displayName = "OriginalName";
            row.rank = 1;
            row.points = 20;
            row.userPublicCode = "PB-TARGET1";
            return java.util.List.of(row);
          }
        };
    controller = createController();
    ReflectionTestUtils.setField(controller, "competitionSeasonService", competitionSeasonService);
    ReflectionTestUtils.setField(
        controller,
        "competitionDisplayNameModerationService",
        competitionDisplayNameModerationService);

    activeCompetitionSeason = season;
    when(standingRepo.findBySeasonOrderByRankNoAscWithUser(season)).thenReturn(List.of(standing));
    competitionDisplayNameModerationService.reportedTargetUserIds = Set.of(456L);
    competitionDisplayNameModerationService.applyHandler =
        (rows, ignoredStandings) -> {
          rows.get(0).displayName = "Player TARGET1";
          rows.get(0).competitionSafeDisplayNameActive = true;
        };

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.competition(null, null, null, null, null, "all", false, model);

    assertThat(view).isEqualTo("auth/competition");
    @SuppressWarnings("unchecked")
    List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow> rows =
        (List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>)
            model.get("ladderDisplay");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).displayName).isEqualTo("Player TARGET1");
    assertThat(rows.get(0).competitionSafeDisplayNameActive).isTrue();
    assertThat(model.get("reportedCompetitionUserIds"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.collection(Long.class))
        .contains(456L);
  }

  @Test
  void reportCompetitionDisplayNameRedirectsWithToastFromOutcome() {
    User viewer = new User();
    viewer.setId(123L);
    viewer.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    User target = new User();
    target.setId(456L);
    target.setNickName("Target");

    LadderConfig competition = new LadderConfig();
    competition.setId(99L);
    competition.setType(LadderConfig.Type.COMPETITION);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 55L);
    season.setLadderConfig(competition);
    season.setState(LadderSeason.State.ACTIVE);

    LadderStanding standing = new LadderStanding();
    standing.setSeason(season);
    standing.setUser(target);

    activeCompetitionSeason = season;
    when(standingRepo.findBySeasonOrderByRankNoAscWithUser(season)).thenReturn(List.of(standing));
    competitionDisplayNameModerationService.reportOutcome =
        CompetitionDisplayNameModerationService.ReportOutcome.AUTO_HIDDEN;

    org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap redirectAttributes =
        new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();

    String view = controller.reportCompetitionDisplayName(456L, redirectAttributes);

    assertThat(view).isEqualTo("redirect:/competition");
    assertThat(redirectAttributes.getFlashAttributes().get("toastMessage"))
        .isEqualTo("Thanks. That name is now hidden on the competition table.");
    assertThat(competitionDisplayNameModerationService.lastReporter).isSameAs(viewer);
    assertThat(competitionDisplayNameModerationService.lastTarget).isSameAs(target);
  }

  @Test
  void competitionPageExposesPlayedAgainstOpponentsForFilters() {
    User viewer = new User();
    viewer.setId(123L);
    viewer.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    User opponent = new User();
    opponent.setId(456L);
    opponent.setNickName("Opponent");

    LadderConfig competition = new LadderConfig();
    competition.setId(99L);
    competition.setTitle("Competition");
    competition.setType(LadderConfig.Type.COMPETITION);

    LadderSeason season = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(season, "id", 55L);
    season.setLadderConfig(competition);
    season.setName("Spring");
    season.setState(LadderSeason.State.ACTIVE);

    Match match = new Match();
    match.setSeason(season);
    match.setA1(viewer);
    match.setB1(opponent);

    activeCompetitionSeason = season;
    when(standingRepo.findBySeasonOrderByRankNoAscWithUser(season))
        .thenReturn(List.of(new LadderStanding()));
    when(matchRepo.findBySeasonOrderByPlayedAtDescWithUsers(season)).thenReturn(List.of(match));

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.competition(null, null, null, null, null, "all", false, model);

    assertThat(view).isEqualTo("auth/competition");
    assertThat(model.get("hasPlayedAgainstOpponents")).isEqualTo(Boolean.TRUE);
    assertThat(model.get("playedAgainstUserIds"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.collection(Long.class))
        .contains(456L);
  }

  @Test
  void competitionPagePaginatesStandingsAndFindMeTargetsUsersPage() {
    User viewer = new User();
    viewer.setId(123L);
    viewer.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig competition = new LadderConfig();
    competition.setId(99L);
    competition.setTitle("Competition");
    competition.setType(LadderConfig.Type.COMPETITION);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 55L);
    season.setLadderConfig(competition);
    season.setName("Spring");
    season.setState(LadderSeason.State.ACTIVE);

    ladderService =
        new com.w3llspring.fhpb.web.service.LadderV2Service(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new com.w3llspring.fhpb.web.service.SeasonNameGenerator(),
            new LadderScoringAlgorithms(
                java.util.List.of(
                    new MarginCurveV1LadderScoringAlgorithm(),
                    new BalancedV1LadderScoringAlgorithm())),
            null,
            null,
            null,
            null) {
          @Override
          public java.util.List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>
              buildDisplayRows(java.util.List<LadderStanding> standings) {
            java.util.List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow> rows =
                new java.util.ArrayList<>();
            for (int i = 0; i < 30; i++) {
              com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow row =
                  new com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow();
              row.rank = i + 1;
              row.displayName = "Player " + (i + 1);
              row.points = 100 - i;
              row.userId = (i == 17) ? 123L : 1000L + i;
              row.bandName = i < 15 ? "Gold Division" : "Silver Division";
              row.bandLabel = row.bandName;
              row.bandCssClass = i < 15 ? "ladder-band-1" : "ladder-band-2";
              row.showBandHeader = (i == 0 || i == 15);
              rows.add(row);
            }
            return rows;
          }
        };
    controller = createController();
    ReflectionTestUtils.setField(controller, "competitionSeasonService", competitionSeasonService);
    ReflectionTestUtils.setField(
        controller,
        "competitionDisplayNameModerationService",
        competitionDisplayNameModerationService);

    activeCompetitionSeason = season;
    when(standingRepo.findBySeasonOrderByRankNoAscWithUser(season))
        .thenReturn(List.of(new LadderStanding()));

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.competition(null, null, null, 0, 10, "all", true, model);

    assertThat(view).isEqualTo("auth/competition");
    assertThat(model.get("standingsPage")).isEqualTo(1);
    assertThat(model.get("standingsPageSize")).isEqualTo(10);
    assertThat(model.get("standingsTotalPages")).isEqualTo(3);
    @SuppressWarnings("unchecked")
    List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow> rows =
        (List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>)
            model.get("ladderDisplay");
    assertThat(rows).hasSize(10);
    assertThat(rows).extracting(row -> row.userId).contains(123L);
    assertThat(model.get("competitionFindMeActive")).isEqualTo(Boolean.TRUE);
  }

  @Test
  void competitionPagePaginationPreservesBadgeUrls() {
    User viewer = new User();
    viewer.setId(123L);
    viewer.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig competition = new LadderConfig();
    competition.setId(99L);
    competition.setTitle("Competition");
    competition.setType(LadderConfig.Type.COMPETITION);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 55L);
    season.setLadderConfig(competition);
    season.setName("Spring");
    season.setState(LadderSeason.State.ACTIVE);

    ladderService =
        new com.w3llspring.fhpb.web.service.LadderV2Service(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new com.w3llspring.fhpb.web.service.SeasonNameGenerator(),
            new LadderScoringAlgorithms(
                java.util.List.of(
                    new MarginCurveV1LadderScoringAlgorithm(),
                    new BalancedV1LadderScoringAlgorithm())),
            null,
            null,
            null,
            null) {
          @Override
          public java.util.List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>
              buildDisplayRows(java.util.List<LadderStanding> standings) {
            java.util.List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow> rows =
                new java.util.ArrayList<>();
            for (int i = 0; i < 30; i++) {
              com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow row =
                  new com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow();
              row.rank = i + 1;
              row.displayName = "Player " + (i + 1);
              row.points = 100 - i;
              row.userId = 1000L + i;
              row.bandName = i < 15 ? "Gold Division" : "Silver Division";
              row.bandLabel = row.bandName;
              row.bandCssClass = i < 15 ? "ladder-band-1" : "ladder-band-2";
              row.showBandHeader = (i == 0 || i == 15);
              if (i == 17) {
                row.badgeViews =
                    List.of(
                        new BadgeView(
                            "/trophies/badge/75", "Platinum", "Opening Season - Platinum", null),
                        new BadgeView(
                            "/trophies/badge/79", "Silver", "Opening Season - Silver", null));
              }
              rows.add(row);
            }
            return rows;
          }
        };
    controller = createController();
    ReflectionTestUtils.setField(controller, "competitionSeasonService", competitionSeasonService);
    ReflectionTestUtils.setField(
        controller,
        "competitionDisplayNameModerationService",
        competitionDisplayNameModerationService);

    activeCompetitionSeason = season;
    when(standingRepo.findBySeasonOrderByRankNoAscWithUser(season))
        .thenReturn(List.of(new LadderStanding()));

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.competition(null, null, null, 1, 10, "all", false, model);

    assertThat(view).isEqualTo("auth/competition");
    @SuppressWarnings("unchecked")
    List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow> rows =
        (List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>)
            model.get("ladderDisplay");
    assertThat(rows).hasSize(10);
    assertThat(rows)
        .filteredOn(row -> row.rank == 18)
        .singleElement()
        .satisfies(
            row ->
                assertThat(row.badgeViews)
                    .extracting(BadgeView::getImageUrl)
                    .containsExactly("/trophies/badge/75", "/trophies/badge/79"));
  }

  @Test
  void competitionPageFindMeTargetsCorrectPageWithinPlayedOnlyView() {
    User viewer = new User();
    viewer.setId(123L);
    viewer.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    User opponent = new User();
    opponent.setId(456L);
    opponent.setNickName("Opponent");

    LadderConfig competition = new LadderConfig();
    competition.setId(99L);
    competition.setTitle("Competition");
    competition.setType(LadderConfig.Type.COMPETITION);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 55L);
    season.setLadderConfig(competition);
    season.setName("Spring");
    season.setState(LadderSeason.State.ACTIVE);

    Match match = new Match();
    match.setSeason(season);
    match.setA1(viewer);
    match.setB1(opponent);

    ladderService =
        new com.w3llspring.fhpb.web.service.LadderV2Service(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new com.w3llspring.fhpb.web.service.SeasonNameGenerator(),
            new LadderScoringAlgorithms(
                java.util.List.of(
                    new MarginCurveV1LadderScoringAlgorithm(),
                    new BalancedV1LadderScoringAlgorithm())),
            null,
            null,
            null,
            null) {
          @Override
          public java.util.List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>
              buildDisplayRows(java.util.List<LadderStanding> standings) {
            java.util.List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow> rows =
                new java.util.ArrayList<>();
            for (int i = 0; i < 40; i++) {
              com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow row =
                  new com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow();
              row.rank = i + 1;
              row.displayName = "Player " + (i + 1);
              row.points = 200 - i;
              row.userId = 1000L + i;
              row.bandName = i < 20 ? "Gold Division" : "Silver Division";
              row.bandLabel = row.bandName;
              row.bandCssClass = i < 20 ? "ladder-band-1" : "ladder-band-2";
              row.showBandHeader = (i == 0 || i == 20);
              rows.add(row);
            }
            rows.get(24).userId = 123L;
            rows.get(24).displayName = "Tester";
            rows.get(27).userId = 456L;
            rows.get(27).displayName = "Opponent";
            return rows;
          }
        };
    controller = createController();
    ReflectionTestUtils.setField(controller, "competitionSeasonService", competitionSeasonService);
    ReflectionTestUtils.setField(
        controller,
        "competitionDisplayNameModerationService",
        competitionDisplayNameModerationService);

    activeCompetitionSeason = season;
    when(standingRepo.findBySeasonOrderByRankNoAscWithUser(season))
        .thenReturn(List.of(new LadderStanding()));
    when(matchRepo.findBySeasonOrderByPlayedAtDescWithUsers(season)).thenReturn(List.of(match));

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.competition(null, null, null, 0, 5, "played", true, model);

    assertThat(view).isEqualTo("auth/competition");
    assertThat(model.get("competitionStandingsView")).isEqualTo("played");
    assertThat(model.get("standingsPage")).isEqualTo(0);
    assertThat(model.get("standingsPageSize")).isEqualTo(5);
    @SuppressWarnings("unchecked")
    List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow> rows =
        (List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>)
            model.get("ladderDisplay");
    assertThat(rows).extracting(row -> row.userId).contains(123L);
  }

  @Test
  void competitionPagePlayedOnlyFilterKeepsVisiblePlayersInCorrectDivisions() {
    User viewer = new User();
    viewer.setId(123L);
    viewer.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    User goldOpponent = new User();
    goldOpponent.setId(456L);
    goldOpponent.setNickName("Gold Opponent");

    User silverOpponent = new User();
    silverOpponent.setId(789L);
    silverOpponent.setNickName("Silver Opponent");

    LadderConfig competition = new LadderConfig();
    competition.setId(99L);
    competition.setTitle("Competition");
    competition.setType(LadderConfig.Type.COMPETITION);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 55L);
    season.setLadderConfig(competition);
    season.setName("Spring");
    season.setState(LadderSeason.State.ACTIVE);

    Match firstMatch = new Match();
    firstMatch.setSeason(season);
    firstMatch.setA1(viewer);
    firstMatch.setB1(goldOpponent);

    Match secondMatch = new Match();
    secondMatch.setSeason(season);
    secondMatch.setA1(viewer);
    secondMatch.setB1(silverOpponent);

    ladderService =
        new com.w3llspring.fhpb.web.service.LadderV2Service(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new com.w3llspring.fhpb.web.service.SeasonNameGenerator(),
            new LadderScoringAlgorithms(
                java.util.List.of(
                    new MarginCurveV1LadderScoringAlgorithm(),
                    new BalancedV1LadderScoringAlgorithm())),
            null,
            null,
            null,
            null) {
          @Override
          public java.util.List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>
              buildDisplayRows(java.util.List<LadderStanding> standings) {
            java.util.List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow> rows =
                new java.util.ArrayList<>();

            rows.add(buildCompetitionRow(1, 123L, "Tester", "Gold Division", "ladder-band-1"));
            rows.add(
                buildCompetitionRow(2, 456L, "Gold Opponent", "Gold Division", "ladder-band-1"));
            rows.add(
                buildCompetitionRow(3, 654L, "Gold Stranger", "Gold Division", "ladder-band-1"));
            rows.add(
                buildCompetitionRow(
                    21, 789L, "Silver Opponent", "Silver Division", "ladder-band-2"));
            rows.add(
                buildCompetitionRow(
                    22, 987L, "Silver Stranger", "Silver Division", "ladder-band-2"));
            rows.get(0).showBandHeader = true;
            rows.get(3).showBandHeader = true;
            return rows;
          }
        };
    controller = createController();
    ReflectionTestUtils.setField(controller, "competitionSeasonService", competitionSeasonService);
    ReflectionTestUtils.setField(
        controller,
        "competitionDisplayNameModerationService",
        competitionDisplayNameModerationService);

    activeCompetitionSeason = season;
    when(standingRepo.findBySeasonOrderByRankNoAscWithUser(season))
        .thenReturn(List.of(new LadderStanding()));
    when(matchRepo.findBySeasonOrderByPlayedAtDescWithUsers(season))
        .thenReturn(List.of(firstMatch, secondMatch));

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.competition(null, null, null, 0, 25, "played", false, model);

    assertThat(view).isEqualTo("auth/competition");
    @SuppressWarnings("unchecked")
    List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow> rows =
        (List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>)
            model.get("ladderDisplay");
    assertThat(rows).extracting(row -> row.userId).containsExactly(123L, 456L, 789L);
    assertThat(rows)
        .extracting(row -> row.bandName)
        .containsExactly("Gold Division", "Gold Division", "Silver Division");
    assertThat(rows.get(0).showBandHeader).isTrue();
    assertThat(rows.get(1).showBandHeader).isFalse();
    assertThat(rows.get(2).showBandHeader).isTrue();
  }

  private com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow buildCompetitionRow(
      int rank, long userId, String displayName, String bandName, String bandCssClass) {
    com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow row =
        new com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow();
    row.rank = rank;
    row.userId = userId;
    row.displayName = displayName;
    row.bandName = bandName;
    row.bandLabel = bandName;
    row.bandCssClass = bandCssClass;
    row.points = 200 - rank;
    return row;
  }

  @Test
  void privateGroupsPickerShowsOnlyPrivateGroupMemberships() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig sessionCfg = new LadderConfig();
    sessionCfg.setId(9L);
    sessionCfg.setTitle("Open Session");
    sessionCfg.setType(LadderConfig.Type.SESSION);
    LadderMembership sessionMembership = new LadderMembership();
    sessionMembership.setLadderConfig(sessionCfg);
    sessionMembership.setUserId(123L);

    LadderConfig standardCfg = new LadderConfig();
    standardCfg.setId(7L);
    standardCfg.setTitle("Ladder X");
    LadderMembership standardMembership = new LadderMembership();
    standardMembership.setLadderConfig(standardCfg);
    standardMembership.setUserId(123L);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(sessionMembership, standardMembership));

    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private-groups");
    MockHttpSession session = new MockHttpSession();
    com.w3llspring.fhpb.web.session.UserSessionState sessionState =
        new com.w3llspring.fhpb.web.session.UserSessionState();
    sessionState.setSelectedGroupId(7L);
    session.setAttribute(
        com.w3llspring.fhpb.web.session.UserSessionState.SESSION_KEY, sessionState);
    request.setSession(session);

    String view = controller.privateGroups(new LadderPageState(), request, model);

    assertThat(view).isEqualTo("auth/private-group-picker");
    assertThat(model.get("privateGroupMemberships"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .hasSize(1);
    assertThat(model.get("selectedPrivateGroupId")).isEqualTo(7L);
  }

  @Test
  void privateGroupsPickerUsesUserSessionStateInsteadOfHomeStateForSelectedGroup() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig standardCfg = new LadderConfig();
    standardCfg.setId(7L);
    standardCfg.setTitle("Ladder X");
    LadderMembership standardMembership = new LadderMembership();
    standardMembership.setLadderConfig(standardCfg);
    standardMembership.setUserId(123L);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(standardMembership));

    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private-groups");
    MockHttpSession session = new MockHttpSession();
    com.w3llspring.fhpb.web.session.UserSessionState sessionState =
        new com.w3llspring.fhpb.web.session.UserSessionState();
    sessionState.setSelectedGroupId(7L);
    session.setAttribute(
        com.w3llspring.fhpb.web.session.UserSessionState.SESSION_KEY, sessionState);
    request.setSession(session);

    LadderPageState staleHomeState = new LadderPageState();
    staleHomeState.ladderId = 999L;

    String view = controller.privateGroups(staleHomeState, request, model);

    assertThat(view).isEqualTo("auth/private-group-picker");
    assertThat(model.get("selectedPrivateGroupId")).isEqualTo(7L);
  }

  @Test
  void groupsCarriesExplicitReturnToPickerTarget() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig standardCfg = new LadderConfig();
    standardCfg.setId(7L);
    standardCfg.setTitle("Ladder X");
    LadderMembership standardMembership = new LadderMembership();
    standardMembership.setLadderConfig(standardCfg);
    standardMembership.setUserId(123L);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(standardMembership));
    when(ladderConfigRepo.findById(7L)).thenReturn(java.util.Optional.of(standardCfg));

    LadderSeason activeSeason = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(activeSeason, "id", 55L);
    activeSeason.setLadderConfig(standardCfg);
    activeSeason.setName("Active");
    activeSeason.setState(LadderSeason.State.ACTIVE);

    when(seasonRepo.findByLadderConfigIdOrderByStartDateDesc(7L)).thenReturn(List.of(activeSeason));

    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups");

    String view =
        controller.groups(null, null, "/private-groups", new LadderPageState(), request, model);

    assertThat(view).isEqualTo("auth/manage-groups");
    assertThat(model.get("groupHubReturnTo")).isEqualTo("/private-groups");
  }

  @Test
  void privateGroupHomeRedirectsToPickerWhenRequestedLadderFallsBackToAnotherGroup() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig standardCfg = new LadderConfig();
    standardCfg.setId(7L);
    standardCfg.setTitle("Ladder X");
    LadderMembership standardMembership = new LadderMembership();
    standardMembership.setLadderConfig(standardCfg);
    standardMembership.setUserId(123L);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(standardMembership));
    when(ladderConfigRepo.findById(99L)).thenReturn(java.util.Optional.empty());
    when(ladderConfigRepo.findById(7L)).thenReturn(java.util.Optional.of(standardCfg));

    LadderSeason activeSeason = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(activeSeason, "id", 55L);
    activeSeason.setLadderConfig(standardCfg);
    activeSeason.setName("Active");
    activeSeason.setState(LadderSeason.State.ACTIVE);

    when(seasonRepo.findByLadderConfigIdOrderByStartDateDesc(7L)).thenReturn(List.of(activeSeason));

    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private-groups/99");

    String view = controller.privateGroupHome(99L, null, new LadderPageState(), request, model);

    assertThat(view).isEqualTo("redirect:/private-groups");
  }

  @Test
  void privateGroupHomeUsesSummaryModelOnly() {
    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    org.springframework.security.core.Authentication auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);

    LadderConfig standardCfg = new LadderConfig();
    standardCfg.setId(7L);
    standardCfg.setTitle("Ladder X");
    LadderMembership standardMembership = new LadderMembership();
    standardMembership.setLadderConfig(standardCfg);
    standardMembership.setUserId(123L);

    when(membershipRepo.findByUserIdAndState(123L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(standardMembership));
    when(ladderConfigRepo.findById(7L)).thenReturn(java.util.Optional.of(standardCfg));

    LadderSeason activeSeason = new LadderSeason();
    ReflectionTestUtils.setField(activeSeason, "id", 55L);
    activeSeason.setLadderConfig(standardCfg);
    activeSeason.setName("Active");
    activeSeason.setState(LadderSeason.State.ACTIVE);
    when(seasonRepo.findByLadderConfigIdOrderByStartDateDesc(7L)).thenReturn(List.of(activeSeason));

    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private-groups/7");

    String view = controller.privateGroupHome(7L, 55L, new LadderPageState(), request, model);

    assertThat(view).isEqualTo("auth/private-groups");
    assertThat(model.get("ladderId")).isEqualTo(7L);
    assertThat(model.get("seasonId")).isEqualTo(55L);
    assertThat(model.get("ladderName")).isEqualTo("Ladder X");
    assertThat(model.get("seasonName")).isEqualTo("Active");
    assertThat(model.get("standings")).isNull();
    assertThat(model.get("ladderDisplay")).isNull();
    assertThat(model.get("links")).isNull();
    assertThat(model.get("voiceLanguage")).isNull();
    assertThat(model.get("improvementAdvice")).isNull();
    assertThat(model.get("storyMode")).isNull();
  }

  private static final class RecordingCompetitionDisplayNameModerationService
      extends CompetitionDisplayNameModerationService {
    private Set<Long> reportedTargetUserIds = Set.of();
    private java.util.function.BiConsumer<
            List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow>, List<LadderStanding>>
        applyHandler = (rows, standings) -> {};
    private CompetitionDisplayNameModerationService.ReportOutcome reportOutcome =
        CompetitionDisplayNameModerationService.ReportOutcome.REPORTED;
    private User lastReporter;
    private User lastTarget;

    private RecordingCompetitionDisplayNameModerationService() {
      super(null, null);
    }

    @Override
    public Set<Long> findReportedTargetUserIds(Long reporterUserId) {
      return reportedTargetUserIds;
    }

    @Override
    public void applyCompetitionDisplayNames(
        List<com.w3llspring.fhpb.web.service.LadderV2Service.LadderRow> rows,
        List<LadderStanding> standings) {
      applyHandler.accept(rows, standings);
    }

    @Override
    public ReportOutcome reportDisplayName(User reporter, User target) {
      this.lastReporter = reporter;
      this.lastTarget = target;
      return reportOutcome;
    }
  }
}
