package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.competition.LadderConfigController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
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
  @Mock private GroupAdministrationOperations groupAdministration;
  private LadderConfigController controller;
  private List<com.w3llspring.fhpb.web.model.RoundRobinStanding> sessionReportStandings;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    sessionReportStandings = List.of();
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
            return sessionReportStandings;
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
    when(membershipRepo.findByLadderConfigIdAndUserId(42L, 7L))
        .thenReturn(Optional.of(currentMembership));
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
    when(membershipRepo.findByLadderConfigIdAndUserId(42L, 7L))
        .thenReturn(Optional.of(currentMembership));
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

    when(configs.findById(42L)).thenReturn(Optional.of(cfg));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndUserId(42L, 7L))
        .thenReturn(Optional.of(currentMembership));
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
                List.of(link),
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
    sessionReportStandings = List.of(reportStanding);
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
    assertThat(model.get("links")).isEqualTo(List.of(link));
    assertThat(model.get("confirmableMatchIds")).isEqualTo(java.util.Set.of(400L));
    assertThat(model.get("waitingOnOpponentByMatchId")).isEqualTo(java.util.Map.of(401L, true));
    assertThat(model.get("sessionStandingRow")).isNotNull();
    assertThat(model.get("sessionReportStandings")).isEqualTo(List.of(reportStanding));
    assertThat(model.get("sessionStandingsRecalculationPending")).isEqualTo(Boolean.TRUE);
    assertThat(model.get("improvementAdvice")).isEqualTo(improvementAdvice);
    assertThat(model.get("sessionDisplayTitle")).isEqualTo("Saturday Open Session");
    assertThat(model.get("returnToPath")).isEqualTo("/groups/42");
    assertThat(model.get("canRestore")).isNull();
    assertThat(model.get("activeAdminCount")).isNull();
    assertThat(model.get("voiceLanguage")).isEqualTo("en-US");
    assertThat(model.get("voiceMaxAlternatives")).isEqualTo(Integer.valueOf(3));
    assertThat((List<String>) model.get("voicePhraseHints"))
        .contains("Tester", "I beat", "We beat");
    assertThat(model.get("courtNameByUser")).isEqualTo(java.util.Map.of(7L, "Center Court"));
    assertThat((String) model.get("ladderInviteLink")).contains("inviteCode=DINK-7");
    assertThat((String) model.get("ladderInviteLink")).contains("autoJoin=true");
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
    when(membershipRepo.findByLadderConfigIdAndUserId(42L, 7L))
        .thenReturn(Optional.of(currentMembership));
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
    when(membershipRepo.findByLadderConfigIdAndUserId(42L, 7L))
        .thenReturn(Optional.of(currentMembership));
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
}
