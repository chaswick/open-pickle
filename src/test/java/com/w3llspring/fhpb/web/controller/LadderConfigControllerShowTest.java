package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.competition.LadderConfigController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserDisplayNameAuditRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMatchLink;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionSeasonService;
import com.w3llspring.fhpb.web.service.LadderImprovementAdvisor;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchDashboardService;
import com.w3llspring.fhpb.web.service.MatchRowModel;
import com.w3llspring.fhpb.web.service.SeasonTransitionService;
import com.w3llspring.fhpb.web.service.SeasonTransitionWindow;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationOperations;
import com.w3llspring.fhpb.web.service.dashboard.MatchDashboardViewService;
import com.w3llspring.fhpb.web.service.matchentry.MatchEntryContextService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.service.scoring.BalancedV1LadderScoringAlgorithm;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms;
import com.w3llspring.fhpb.web.service.scoring.MarginCurveV1LadderScoringAlgorithm;
import com.w3llspring.fhpb.web.service.standings.SeasonStandingsViewService;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
class LadderConfigControllerShowTest {

  @Mock private UserRepository userRepo;
  @Mock private UserDisplayNameAuditRepository userDisplayNameAuditRepository;
  @Mock private LadderConfigRepository configs;
  @Mock private LadderSeasonRepository seasons;
  @Mock private LadderMembershipRepository membershipRepo;
  @Mock private LadderStandingRepository standingRepo;
  @Mock private MatchRepository matchRepo;
  @Mock private MatchConfirmationRepository matchConfirmationRepository;
  @Mock private GroupAdministrationOperations groupAdministration;
  private LadderConfigController controller;
  private List<com.w3llspring.fhpb.web.model.RoundRobinStanding> sessionStandings;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    sessionStandings = List.of();
    SeasonTransitionService transitionSvcStub =
        new SeasonTransitionService(null, null) {
          @Override
          public SeasonTransitionWindow canCreateSeason(LadderConfig ladder) {
            return SeasonTransitionWindow.ok();
          }

          @Override
          public String formatCountdown(SeasonTransitionWindow window) {
            return "";
          }
        };
    StoryModeService storyModeServiceStub =
        new StoryModeService(null, null, null, null, null, null) {
          @Override
          public boolean isFeatureEnabled() {
            return true;
          }
        };
    RoundRobinService roundRobinServiceStub =
        new RoundRobinService(null, null, null, null, null, null, null) {
          @Override
          public List<com.w3llspring.fhpb.web.model.RoundRobinStanding> computeStandingsForSession(
              LadderConfig sessionConfig) {
            return sessionStandings;
          }

          @Override
          public Optional<ActiveSessionAssignment> findActiveSessionAssignment(
              LadderConfig sessionConfig, Long userId) {
            return Optional.empty();
          }
        };
    controller =
        new LadderConfigController(
            userRepo,
            userDisplayNameAuditRepository,
            null,
            groupAdministration,
            configs,
            seasons,
            membershipRepo,
            transitionSvcStub,
            null,
            roundRobinServiceStub,
            storyModeServiceStub,
            20);
    ReflectionTestUtils.setField(controller, "matchRepo", matchRepo);
    ReflectionTestUtils.setField(controller, "matchConfirmationRepository", matchConfirmationRepository);
    ReflectionTestUtils.setField(controller, "siteWideAdminEmail", "admin@test.com");
  }

  @Test
  void show_populatesMemberSectionTitleFromCounts() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");

    User otherUser = new User();
    otherUser.setId(8L);
    otherUser.setNickName("Other");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Test Group");
    cfg.setOwnerUserId(7L);
    cfg.setType(LadderConfig.Type.STANDARD);
    cfg.setInviteCode("DINK-7");

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.ADMIN);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    LadderMembership otherMembership = new LadderMembership();
    otherMembership.setId(102L);
    otherMembership.setLadderConfig(cfg);
    otherMembership.setUserId(8L);
    otherMembership.setRole(LadderMembership.Role.MEMBER);
    otherMembership.setState(LadderMembership.State.ACTIVE);
    otherMembership.setJoinedAt(Instant.now().minusSeconds(100));

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findActive(42L)).thenReturn(Optional.empty());
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(currentMembership, otherMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser, otherUser));

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat(model.get("memberSectionTitle")).isEqualTo("Members (2/20)");
    assertThat((String) model.get("ladderInviteLink")).contains("inviteCode=DINK-7");
    assertThat((String) model.get("ladderInviteLink")).doesNotContain("autoJoin=true");
  }

  @Test
  void show_sessionInviteLinkPrefersConfiguredPublicBaseUrl() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Saturday Open Session");
    cfg.setOwnerUserId(7L);
    cfg.setType(LadderConfig.Type.SESSION);
    cfg.setInviteCode("DINK-7");
    cfg.setLastInviteChangeAt(Instant.now());

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.ADMIN);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(currentMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser));
    ReflectionTestUtils.setField(controller, "publicBaseUrl", "https://play.openpickle.test/app");

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");
    request.setScheme("http");
    request.setServerName("internal.openpickle.test");
    request.setServerPort(8080);

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat((String) model.get("ladderInviteLink"))
        .startsWith("https://play.openpickle.test/app/groups/join?");
    assertThat((String) model.get("ladderInviteLink")).contains("inviteCode=DINK-7");
    assertThat((String) model.get("ladderInviteLink")).contains("autoJoin=true");
    assertThat((String) model.get("ladderInviteLink")).doesNotContain("internal.openpickle.test");
  }

  @Test
  void show_sessionPopulatesDashboardAttributes() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");

    User tickerPartner = new User();
    tickerPartner.setId(8L);
    tickerPartner.setNickName("Partner");

    User tickerOpponent = new User();
    tickerOpponent.setId(9L);
    tickerOpponent.setNickName("Opponent");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Saturday Open Session");
    cfg.setOwnerUserId(7L);
    cfg.setType(LadderConfig.Type.SESSION);
    cfg.setInviteCode("DINK-7");

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.ADMIN);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    LadderSeason targetSeason = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(targetSeason, "id", 55L);
    targetSeason.setLadderConfig(cfg);
    targetSeason.setName("Global Competition");
    targetSeason.setStandingsRecalcInFlight(1);

    Match match = new Match();
    org.springframework.test.util.ReflectionTestUtils.setField(match, "id", 400L);
    match.setSeason(targetSeason);

    LadderMatchLink link = new LadderMatchLink();
    link.setMatch(match);
    link.setSeason(targetSeason);

    Match waitingMatch = new Match();
    org.springframework.test.util.ReflectionTestUtils.setField(waitingMatch, "id", 401L);
    waitingMatch.setSeason(targetSeason);

    LadderMatchLink waitingLink = new LadderMatchLink();
    waitingLink.setMatch(waitingMatch);
    waitingLink.setSeason(targetSeason);

    Match recentConfirmedMatch = new Match();
    ReflectionTestUtils.setField(recentConfirmedMatch, "id", 499L);
    recentConfirmedMatch.setSeason(targetSeason);
    recentConfirmedMatch.setSourceSessionConfig(cfg);
    recentConfirmedMatch.setA1(currentUser);
    recentConfirmedMatch.setA2(tickerPartner);
    recentConfirmedMatch.setB1(tickerOpponent);
    recentConfirmedMatch.setB2(null);
    recentConfirmedMatch.setB2Guest(true);
    recentConfirmedMatch.setScoreA(11);
    recentConfirmedMatch.setScoreB(5);
    recentConfirmedMatch.setPlayedAt(Instant.parse("2026-03-31T15:55:00Z"));

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(currentMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser, tickerPartner, tickerOpponent));
    when(matchConfirmationRepository.findRecentConfirmedSessionTimelines(
            org.mockito.ArgumentMatchers.eq(42L),
            org.mockito.ArgumentMatchers.eq(com.w3llspring.fhpb.web.model.MatchState.CONFIRMED),
            org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(
            List.of(
                tickerTimeline(499L, Instant.parse("2026-03-31T16:05:00Z"))));
    when(matchRepo.findAllByIdInWithUsers(List.of(499L))).thenReturn(List.of(recentConfirmedMatch));

    CompetitionSeasonService competitionSeasonService =
        new CompetitionSeasonService(null, null, null) {
          @Override
          public LadderSeason resolveTargetSeason(LadderConfig ladderConfig) {
            return targetSeason;
          }
        };
    MatchDashboardService dashboardService =
        new MatchDashboardService(null, null, null, null, null) {
          @Override
          public DashboardModel buildPendingForUserInSeason(User viewer, LadderSeason season) {
            return new DashboardModel(
                List.of(link, waitingLink),
                new MatchRowModel(
                    java.util.Set.of(400L),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(400L, true),
                    java.util.Map.of(401L, true),
                    java.util.Map.of(400L, false),
                    java.util.Map.of(400L, false)));
          }
        };
    LadderStanding standing = new LadderStanding();
    standing.setSeason(targetSeason);
    standing.setUser(currentUser);
    standing.setDisplayName("Tester");
    standing.setRank(4);
    standing.setPoints(27);
    when(standingRepo.findBySeasonOrderByRankNoAscWithUser(targetSeason))
        .thenReturn(List.of(standing));
    com.w3llspring.fhpb.web.model.RoundRobinStanding reportStanding =
        new com.w3llspring.fhpb.web.model.RoundRobinStanding(7L, "Tester");
    reportStanding.incWins();
    reportStanding.addPointsFor(22);
    sessionStandings = List.of(reportStanding);
    LadderV2Service ladderV2Service =
        new LadderV2Service(
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
          public java.util.List<LadderRow> buildDisplayRows(
              java.util.List<LadderStanding> standings) {
            LadderRow row = new LadderRow();
            row.userId = 7L;
            row.displayName = "Tester";
            row.rank = 4;
            row.points = 27;
            row.bandName = "Division 1";
            row.momentum = 3;
            return List.of(row);
          }
        };
    ReflectionTestUtils.setField(controller, "competitionSeasonService", competitionSeasonService);
    ReflectionTestUtils.setField(controller, "matchDashboardService", dashboardService);
    ReflectionTestUtils.setField(
        controller, "matchDashboardViewService", new MatchDashboardViewService());
    ReflectionTestUtils.setField(
        controller,
        "seasonStandingsViewService",
        new SeasonStandingsViewService(standingRepo, ladderV2Service, Optional.empty()));
    ReflectionTestUtils.setField(
        controller,
        "matchEntryContextService",
            new MatchEntryContextService(
                new CourtNameService() {
                  @Override
                  public java.util.Map<Long, Set<String>> gatherCourtNamesForUsers(
                      java.util.Collection<Long> userIds, Long ladderId) {
                return java.util.Map.of(7L, Set.of("Center Court"));
                  }

              @Override
              public Set<String> gatherCourtNamesForUser(long userId, Long ladderConfigId) {
                return Set.of();
              }
            }));
    LadderImprovementAdvisor.Advice improvementAdvice =
        LadderImprovementAdvisor.Advice.simple("Keep logging wins.");
    ReflectionTestUtils.setField(
        controller,
        "improvementAdvisor",
        new LadderImprovementAdvisor() {
          @Override
          public Advice buildAdvice(User user) {
            return improvementAdvice;
          }

          @Override
          public Advice buildAdvice(User user, LadderConfig ladder, LadderSeason season) {
            return improvementAdvice;
          }
        });

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat(model.get("links")).isEqualTo(List.of(link, waitingLink));
    assertThat(model.get("confirmableMatchIds")).isEqualTo(java.util.Set.of(400L));
    assertThat(model.get("waitingOnOpponentByMatchId")).isEqualTo(java.util.Map.of(401L, true));
    assertThat(model.get("sessionConfirmationInboxCount")).isEqualTo(Integer.valueOf(1));
    assertThat(model.get("sessionConfirmationOutboxCount")).isEqualTo(Integer.valueOf(1));
    assertThat(model.get("sessionConfirmationInboxLinks")).isEqualTo(List.of(link));
    assertThat(model.get("sessionConfirmationOutboxLinks")).isEqualTo(List.of(waitingLink));
    assertThat(model.get("sessionConfirmationInboxConfirmableMatchIds"))
        .isEqualTo(java.util.Set.of(400L));
    assertThat(model.get("sessionConfirmationInboxNullifyApprovableByMatchId"))
        .isEqualTo(java.util.Map.of());
    assertThat(model.get("sessionConfirmationOutboxWaitingOnOpponentByMatchId"))
        .isEqualTo(java.util.Map.of(401L, true));
    assertThat(model.get("sessionConfirmationOutboxNullifyWaitingOnOpponentByMatchId"))
        .isEqualTo(java.util.Map.of());
    assertThat(model.get("sessionStandingsRecalculationPending")).isEqualTo(Boolean.TRUE);
    assertThat(model.get("improvementAdvice")).isEqualTo(improvementAdvice);
    assertThat(model.get("sessionDisplayTitle")).isEqualTo("Saturday Open Session");
    assertThat(model.get("returnToPath")).isEqualTo("/groups/42");
    assertThat(model.get("canStartSessionRoundRobin")).isEqualTo(Boolean.TRUE);
    assertThat(model.get("sessionRoundRobinTask")).isNull();
    assertThat(model.get("canRestore")).isNull();
    assertThat(model.get("activeAdminCount")).isNull();
    assertThat(model.get("voiceLanguage")).isEqualTo("en-US");
    assertThat(model.get("voiceMaxAlternatives")).isEqualTo(Integer.valueOf(3));
    assertThat((List<String>) model.get("voicePhraseHints"))
        .contains("Tester", "I beat", "We beat");
    assertThat(model.get("courtNameByUser")).isEqualTo(java.util.Map.of(7L, "Center Court"));
    assertThat((java.util.Map<Long, Integer>) model.get("sessionMomentumByUserId"))
        .containsEntry(7L, 3);
    assertThat((String) model.get("ladderInviteLink")).contains("inviteCode=DINK-7");
    assertThat((String) model.get("ladderInviteLink")).contains("autoJoin=true");
    assertThat((List<LadderConfigController.SessionRecentTickerItem>) model.get("sessionRecentTickerItems"))
        .extracting(LadderConfigController.SessionRecentTickerItem::summary)
        .containsExactly("Tester & Partner def Opponent & Guest 11-5");
  }

  @Test
  void show_sessionTickerUsesLastThreeConfirmedMatchesByConfirmedTime() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");

    User partner = new User();
    partner.setId(8L);
    partner.setNickName("Partner");

    User opponentOne = new User();
    opponentOne.setId(9L);
    opponentOne.setNickName("Opponent One");

    User opponentTwo = new User();
    opponentTwo.setId(10L);
    opponentTwo.setNickName("Opponent Two");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Saturday Open Session");
    cfg.setOwnerUserId(7L);
    cfg.setType(LadderConfig.Type.SESSION);
    cfg.setInviteCode("DINK-7");

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.ADMIN);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    Match firstDisplayed = tickerMatch(501L, cfg, currentUser, partner, opponentOne, 11, 5);
    firstDisplayed.setPlayedAt(Instant.parse("2026-03-31T12:00:00Z"));

    Match secondDisplayed = tickerMatch(502L, cfg, partner, opponentOne, opponentTwo, 11, 7);
    secondDisplayed.setPlayedAt(Instant.parse("2026-03-31T18:00:00Z"));

    Match newestConfirmed = tickerMatch(503L, cfg, opponentOne, opponentTwo, currentUser, 11, 9);
    newestConfirmed.setPlayedAt(Instant.parse("2026-03-31T08:00:00Z"));

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(currentMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser, partner, opponentOne, opponentTwo));
    when(matchConfirmationRepository.findRecentConfirmedSessionTimelines(
            org.mockito.ArgumentMatchers.eq(42L),
            org.mockito.ArgumentMatchers.eq(com.w3llspring.fhpb.web.model.MatchState.CONFIRMED),
            org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(
            List.of(
                tickerTimeline(503L, Instant.parse("2026-03-31T16:03:00Z")),
                tickerTimeline(501L, Instant.parse("2026-03-31T16:02:00Z")),
                tickerTimeline(502L, Instant.parse("2026-03-31T16:01:00Z"))));
    when(matchRepo.findAllByIdInWithUsers(List.of(503L, 501L, 502L)))
        .thenReturn(List.of(firstDisplayed, newestConfirmed, secondDisplayed));

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, null, model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat((List<LadderConfigController.SessionRecentTickerItem>) model.get("sessionRecentTickerItems"))
        .extracting(
            LadderConfigController.SessionRecentTickerItem::matchId,
            LadderConfigController.SessionRecentTickerItem::confirmedAt)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(503L, Instant.parse("2026-03-31T16:03:00Z")),
            org.assertj.core.groups.Tuple.tuple(501L, Instant.parse("2026-03-31T16:02:00Z")),
            org.assertj.core.groups.Tuple.tuple(502L, Instant.parse("2026-03-31T16:01:00Z")));
  }

  @Test
  void show_sessionFallsBackToActiveCompetitionSeasonForInitialStandingsDisplay() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Saturday Open Session");
    cfg.setOwnerUserId(7L);
    cfg.setType(LadderConfig.Type.SESSION);
    cfg.setInviteCode("DINK-7");

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.ADMIN);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    LadderSeason activeCompetitionSeason = new LadderSeason();
    ReflectionTestUtils.setField(activeCompetitionSeason, "id", 55L);
    LadderConfig competitionConfig = new LadderConfig();
    competitionConfig.setId(90L);
    competitionConfig.setType(LadderConfig.Type.COMPETITION);
    activeCompetitionSeason.setLadderConfig(competitionConfig);
    activeCompetitionSeason.setName("Global Competition");

    LadderStanding standing = new LadderStanding();
    standing.setSeason(activeCompetitionSeason);
    standing.setUser(currentUser);
    standing.setDisplayName("Tester");
    standing.setRank(4);
    standing.setPoints(27);

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(currentMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser));
    when(standingRepo.findBySeasonOrderByRankNoAscWithUser(activeCompetitionSeason))
        .thenReturn(List.of(standing));

    sessionStandings = List.of(new com.w3llspring.fhpb.web.model.RoundRobinStanding(7L, "Tester"));

    CompetitionSeasonService competitionSeasonService =
        new CompetitionSeasonService(null, null, null) {
          @Override
          public LadderSeason resolveTargetSeason(LadderConfig ladderConfig) {
            return null;
          }

          @Override
          public LadderSeason resolveActiveCompetitionSeason() {
            return activeCompetitionSeason;
          }
        };
    LadderV2Service ladderV2Service =
        new LadderV2Service(
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
          public java.util.List<LadderRow> buildDisplayRows(
              java.util.List<LadderStanding> standings) {
            LadderRow row = new LadderRow();
            row.userId = 7L;
            row.displayName = "Tester";
            row.rank = 4;
            row.points = 27;
            row.bandName = "Division 1";
            row.momentum = 4;
            return List.of(row);
          }
        };

    ReflectionTestUtils.setField(controller, "competitionSeasonService", competitionSeasonService);
    ReflectionTestUtils.setField(
        controller,
        "seasonStandingsViewService",
        new SeasonStandingsViewService(standingRepo, ladderV2Service, Optional.empty()));

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat(model.get("targetSeason")).isEqualTo(activeCompetitionSeason);
    assertThat((Map<Long, Integer>) model.get("sessionMomentumByUserId")).containsEntry(7L, 4);
    assertThat((Map<Long, Integer>) model.get("sessionRatingByUserId")).containsEntry(7L, 1027);
  }

  @Test
  void show_sessionPopulatesActiveRoundRobinTask() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");

    User partner = new User();
    partner.setId(8L);
    partner.setNickName("Partner");

    User opponentOne = new User();
    opponentOne.setId(9L);
    opponentOne.setNickName("OpponentOne");

    User opponentTwo = new User();
    opponentTwo.setId(10L);
    opponentTwo.setNickName("OpponentTwo");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Saturday Open Session");
    cfg.setOwnerUserId(7L);
    cfg.setType(LadderConfig.Type.SESSION);

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.ADMIN);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    LadderSeason targetSeason = new LadderSeason();
    ReflectionTestUtils.setField(targetSeason, "id", 55L);
    targetSeason.setLadderConfig(cfg);

    com.w3llspring.fhpb.web.model.RoundRobin rr = new com.w3llspring.fhpb.web.model.RoundRobin();
    ReflectionTestUtils.setField(rr, "id", 88L);
    rr.setSessionConfig(cfg);
    rr.setSeason(targetSeason);

    com.w3llspring.fhpb.web.model.RoundRobinEntry entry =
        new com.w3llspring.fhpb.web.model.RoundRobinEntry();
    ReflectionTestUtils.setField(entry, "id", 66L);
    entry.setRoundRobin(rr);
    entry.setRoundNumber(1);
    entry.setA1(partner);
    entry.setA2(opponentOne);
    entry.setB1(currentUser);
    entry.setB2(opponentTwo);

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(currentMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser, partner, opponentOne, opponentTwo));

    CompetitionSeasonService competitionSeasonService =
        new CompetitionSeasonService(null, null, null) {
          @Override
          public LadderSeason resolveTargetSeason(LadderConfig ladderConfig) {
            return targetSeason;
          }
        };
    MatchDashboardService dashboardService =
        new MatchDashboardService(null, null, null, null, null) {
          @Override
          public DashboardModel buildPendingForUserInSeason(User viewer, LadderSeason season) {
            return new DashboardModel(
                List.of(),
                new MatchRowModel(
                    java.util.Set.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of()));
          }
        };
    RoundRobinService roundRobinServiceStub =
        new RoundRobinService(null, null, null, null, null, null, null) {
          @Override
          public List<com.w3llspring.fhpb.web.model.RoundRobinStanding> computeStandingsForSession(
              LadderConfig sessionConfig) {
            return List.of();
          }

          @Override
          public Optional<ActiveSessionAssignment> findActiveSessionAssignment(
              LadderConfig sessionConfig, Long userId) {
            return Optional.of(new ActiveSessionAssignment(rr, entry, null, 1, 3));
          }

          @Override
          public Map<Long, String> buildDisplayNameMap(
              java.util.Collection<Long> userIds, Long ladderConfigId) {
            return Map.of(7L, "Tester", 8L, "Partner", 9L, "OpponentOne", 10L, "OpponentTwo");
          }
        };

    ReflectionTestUtils.setField(controller, "competitionSeasonService", competitionSeasonService);
    ReflectionTestUtils.setField(controller, "matchDashboardService", dashboardService);
    ReflectionTestUtils.setField(
        controller, "matchDashboardViewService", new MatchDashboardViewService());
    ReflectionTestUtils.setField(controller, "roundRobinService", roundRobinServiceStub);

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    @SuppressWarnings("unchecked")
    Map<String, Object> task = (Map<String, Object>) model.get("sessionRoundRobinTask");
    assertThat(task).isNotNull();
    assertThat(task.get("roundRobinId")).isEqualTo(88L);
    assertThat(task.get("entryId")).isEqualTo(66L);
    assertThat(task.get("readyToLog")).isEqualTo(Boolean.TRUE);
    assertThat(task.get("quickLogA1")).isEqualTo(7L);
    assertThat(task.get("quickLogB1")).isEqualTo(8L);
  }

  @Test
  void show_sessionRoutesNullifyApprovalsIntoInboxAndNullifyWaitsIntoOutbox() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Saturday Open Session");
    cfg.setOwnerUserId(7L);
    cfg.setType(LadderConfig.Type.SESSION);

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.ADMIN);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    LadderSeason targetSeason = new LadderSeason();
    ReflectionTestUtils.setField(targetSeason, "id", 55L);
    targetSeason.setLadderConfig(cfg);

    Match nullifyInboxMatch = new Match();
    ReflectionTestUtils.setField(nullifyInboxMatch, "id", 500L);
    nullifyInboxMatch.setSeason(targetSeason);

    Match nullifyOutboxMatch = new Match();
    ReflectionTestUtils.setField(nullifyOutboxMatch, "id", 501L);
    nullifyOutboxMatch.setSeason(targetSeason);

    LadderMatchLink nullifyInboxLink = new LadderMatchLink();
    nullifyInboxLink.setMatch(nullifyInboxMatch);
    nullifyInboxLink.setSeason(targetSeason);

    LadderMatchLink nullifyOutboxLink = new LadderMatchLink();
    nullifyOutboxLink.setMatch(nullifyOutboxMatch);
    nullifyOutboxLink.setSeason(targetSeason);

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(currentMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser));

    CompetitionSeasonService competitionSeasonService =
        new CompetitionSeasonService(null, null, null) {
          @Override
          public LadderSeason resolveTargetSeason(LadderConfig ladderConfig) {
            return targetSeason;
          }
        };
    MatchDashboardService dashboardService =
        new MatchDashboardService(null, null, null, null, null) {
          @Override
          public DashboardModel buildPendingForUserInSeason(User viewer, LadderSeason season) {
            return new DashboardModel(
                List.of(nullifyInboxLink, nullifyOutboxLink),
                new MatchRowModel(
                    Set.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(500L, true),
                    Map.of(501L, true)));
          }
        };
    ReflectionTestUtils.setField(controller, "competitionSeasonService", competitionSeasonService);
    ReflectionTestUtils.setField(controller, "matchDashboardService", dashboardService);
    ReflectionTestUtils.setField(
        controller, "matchDashboardViewService", new MatchDashboardViewService());

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat(model.get("sessionConfirmationInboxCount")).isEqualTo(Integer.valueOf(1));
    assertThat(model.get("sessionConfirmationOutboxCount")).isEqualTo(Integer.valueOf(1));
    assertThat(model.get("sessionConfirmationInboxLinks")).isEqualTo(List.of(nullifyInboxLink));
    assertThat(model.get("sessionConfirmationOutboxLinks")).isEqualTo(List.of(nullifyOutboxLink));
    assertThat(model.get("sessionConfirmationInboxConfirmableMatchIds")).isEqualTo(Set.of());
    assertThat(model.get("sessionConfirmationInboxNullifyApprovableByMatchId"))
        .isEqualTo(Map.of(500L, true));
    assertThat(model.get("sessionConfirmationOutboxWaitingOnOpponentByMatchId"))
        .isEqualTo(Map.of());
    assertThat(model.get("sessionConfirmationOutboxNullifyWaitingOnOpponentByMatchId"))
        .isEqualTo(Map.of(501L, true));
    assertThat(model.get("sessionConfirmationsAutoOpen")).isEqualTo(Boolean.TRUE);
  }

  @Test
  void show_sessionStripsLegacyTimestampFromAutoGeneratedTitle() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Tester's Session - Mar 16, 7:00 PM");
    cfg.setOwnerUserId(7L);
    cfg.setType(LadderConfig.Type.SESSION);

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.ADMIN);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(currentMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser));

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat(model.get("sessionDisplayTitle")).isEqualTo("Tester's Session");
    assertThat(model.get("sessionHeroTitle")).isEqualTo("Tester's Session");
  }

  @Test
  void show_sessionJoinerUsesNormalizedSessionNameForHeroTitle() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");

    User owner = new User();
    owner.setId(8L);
    owner.setNickName("Charlie");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Charlie's Session - Mar 16, 7:00 PM");
    cfg.setOwnerUserId(8L);
    cfg.setType(LadderConfig.Type.SESSION);

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.MEMBER);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    LadderMembership ownerMembership = new LadderMembership();
    ownerMembership.setId(102L);
    ownerMembership.setLadderConfig(cfg);
    ownerMembership.setUserId(8L);
    ownerMembership.setRole(LadderMembership.Role.ADMIN);
    ownerMembership.setState(LadderMembership.State.ACTIVE);
    ownerMembership.setJoinedAt(Instant.now().minusSeconds(300));

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(ownerMembership, currentMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser, owner));

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat(model.get("sessionDisplayTitle")).isEqualTo("Charlie's Session");
    assertThat(model.get("sessionHeroTitle")).isEqualTo("Charlie's Session");
  }

  @Test
  void show_sessionJoinerUsesActiveRosterWhenDirectMembershipLookupIsStale() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");

    User owner = new User();
    owner.setId(8L);
    owner.setNickName("Charlie");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Charlie's Session - Mar 16, 7:00 PM");
    cfg.setOwnerUserId(8L);
    cfg.setType(LadderConfig.Type.SESSION);

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.MEMBER);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    LadderMembership ownerMembership = new LadderMembership();
    ownerMembership.setId(102L);
    ownerMembership.setLadderConfig(cfg);
    ownerMembership.setUserId(8L);
    ownerMembership.setRole(LadderMembership.Role.ADMIN);
    ownerMembership.setState(LadderMembership.State.ACTIVE);
    ownerMembership.setJoinedAt(Instant.now().minusSeconds(300));

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(ownerMembership, currentMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser, owner));

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat(model.get("currentUserIsAdmin")).isEqualTo(false);
    assertThat(model.get("sessionDisplayTitle")).isEqualTo("Charlie's Session");
    assertThat(model.get("sessionHeroTitle")).isEqualTo("Charlie's Session");
  }

  @Test
  void show_sessionMemberWhoIsSiteWideAdminIsNotTreatedAsSessionAdmin() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");
    currentUser.setEmail("admin@test.com");

    User owner = new User();
    owner.setId(8L);
    owner.setNickName("Charlie");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Charlie's Session - Mar 16, 7:00 PM");
    cfg.setOwnerUserId(8L);
    cfg.setType(LadderConfig.Type.SESSION);

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.MEMBER);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    LadderMembership ownerMembership = new LadderMembership();
    ownerMembership.setId(102L);
    ownerMembership.setLadderConfig(cfg);
    ownerMembership.setUserId(8L);
    ownerMembership.setRole(LadderMembership.Role.ADMIN);
    ownerMembership.setState(LadderMembership.State.ACTIVE);
    ownerMembership.setJoinedAt(Instant.now().minusSeconds(300));

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(ownerMembership, currentMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser, owner));

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat(model.get("currentUserIsAdmin")).isEqualTo(false);
    assertThat(model.get("pendingSessionJoinRequests")).isEqualTo(List.of());
  }

  @Test
  void show_sessionOwnerUsesEndSessionCopy() {
    User currentUser = new User();
    currentUser.setId(7L);
    currentUser.setNickName("Tester");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Saturday Open Session");
    cfg.setOwnerUserId(7L);
    cfg.setType(LadderConfig.Type.SESSION);

    LadderMembership currentMembership = new LadderMembership();
    currentMembership.setId(101L);
    currentMembership.setLadderConfig(cfg);
    currentMembership.setUserId(7L);
    currentMembership.setRole(LadderMembership.Role.ADMIN);
    currentMembership.setState(LadderMembership.State.ACTIVE);
    currentMembership.setJoinedAt(Instant.now().minusSeconds(200));

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(currentMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(currentUser));

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat(model.get("leaveConfirmMessage"))
        .isEqualTo("End this session? Everyone will be removed immediately.");
    assertThat(model.get("leaveActionLabel")).isEqualTo("End Session");
  }

  @Test
  void show_competitionRedirectsNonSiteWideAdminAwayFromDetailPage() {
    User admin = new User();
    admin.setId(100L);
    admin.setNickName("Other Admin");
    admin.setAdmin(true);
    admin.setEmail("other-admin@test.com");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Global Competition");
    cfg.setType(LadderConfig.Type.COMPETITION);

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(admin), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("redirect:/competition");
    assertThat(model.containsAttribute("members")).isFalse();
    assertThat(model.containsAttribute("bannedMembers")).isFalse();
  }

  private MatchConfirmationRepository.SessionConfirmedMatchTimeline tickerTimeline(
      Long matchId, Instant confirmedAt) {
    return new MatchConfirmationRepository.SessionConfirmedMatchTimeline() {
      @Override
      public Long getMatchId() {
        return matchId;
      }

      @Override
      public Instant getConfirmedAt() {
        return confirmedAt;
      }
    };
  }

  private Match tickerMatch(
      Long matchId,
      LadderConfig sessionConfig,
      User a1,
      User a2,
      User b1,
      int scoreA,
      int scoreB) {
    Match match = new Match();
    ReflectionTestUtils.setField(match, "id", matchId);
    match.setSourceSessionConfig(sessionConfig);
    match.setA1(a1);
    match.setA2(a2);
    match.setB1(b1);
    match.setB2(null);
    match.setB2Guest(true);
    match.setScoreA(scoreA);
    match.setScoreB(scoreB);
    return match;
  }
}
