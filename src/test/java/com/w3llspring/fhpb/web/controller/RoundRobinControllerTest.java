package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.roundrobin.RoundRobinController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.RoundRobin;
import com.w3llspring.fhpb.web.model.RoundRobinEntry;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.auth.AuthenticatedUserService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class RoundRobinControllerTest {

  @Mock private LadderMembershipRepository ladderMembershipRepository;
  @Mock private LadderConfigRepository ladderConfigRepository;
  @Mock private MatchConfirmationService matchConfirmationService;
  @Mock private com.w3llspring.fhpb.web.db.UserRepository userRepository;

  @AfterEach
  void tearDown() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
  }

  @Test
  void showStartRejectsUserOutsideLadder() {
    RoundRobinController controller =
        new RoundRobinController(
            stubRoundRobinService(null),
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);
    User viewer = user(1L, "viewer");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());

    when(ladderMembershipRepository.findByLadderConfigIdAndUserId(10L, 1L))
        .thenReturn(java.util.Optional.empty());

    assertThatThrownBy(
            () ->
                controller.showStart(
                    10L, new MockHttpServletRequest(), auth, new ExtendedModelMap()))
        .isInstanceOf(SecurityException.class)
        .hasMessage("Round-robin unavailable.");
  }

  @Test
  void showStartRedirectsCompetitionLadderToCompetitionPage() {
    RoundRobinController controller =
        new RoundRobinController(
            stubRoundRobinService(null),
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);
    org.springframework.test.util.ReflectionTestUtils.setField(
        controller, "ladderConfigRepository", ladderConfigRepository);

    LadderConfig competition = new LadderConfig();
    competition.setId(10L);
    competition.setType(LadderConfig.Type.COMPETITION);
    when(ladderConfigRepository.findById(10L)).thenReturn(Optional.of(competition));

    User viewer = user(1L, "viewer");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());

    String view =
        controller.showStart(10L, new MockHttpServletRequest(), auth, new ExtendedModelMap());

    assertThat(view).isEqualTo("redirect:/competition");
  }

  @Test
  void showStartDoesNotAutoSelectSessionWhenOnlySessionMembershipExists() {
    RoundRobinController controller =
        new RoundRobinController(
            stubRoundRobinService(null),
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);
    User viewer = user(1L, "viewer");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());

    LadderConfig sessionConfig = new LadderConfig();
    sessionConfig.setId(42L);
    sessionConfig.setType(LadderConfig.Type.SESSION);
    LadderMembership sessionMembership = membership(42L, 1L, LadderMembership.State.ACTIVE);
    sessionMembership.setLadderConfig(sessionConfig);
    when(ladderMembershipRepository.findByUserIdAndState(1L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(sessionMembership));

    ExtendedModelMap model = new ExtendedModelMap();
    String view = controller.showStart(null, new MockHttpServletRequest(), auth, model);

    assertThat(view).isEqualTo("roundrobin/start");
    assertThat(model.get("ladderId")).isNull();
    assertThat(model.get("seasonId")).isNull();
  }

  @Test
  void startFixedTeamsPassesFormatAndAssignmentsToService() {
    User viewer = user(1L, "viewer");
    User partner = user(2L, "partner");
    User opponentOne = user(3L, "opponentOne");
    User opponentTwo = user(4L, "opponentTwo");
    CustomUserDetails principal = new CustomUserDetails(viewer);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 77L);

    RoundRobin created = new RoundRobin();
    ReflectionTestUtils.setField(created, "id", 55L);

    final RoundRobin.Format[] capturedFormat = new RoundRobin.Format[1];
    @SuppressWarnings("unchecked")
    final List<List<Long>>[] capturedTeams = new List[1];

    RoundRobinService service =
        new RoundRobinService(null, null, null, null, null, null, null) {
          @Override
          public List<User> listMembersForLadder(Long ladderConfigId) {
            return List.of(viewer, partner, opponentOne, opponentTwo);
          }

          @Override
          public LadderSeason findSeasonForLadder(Long ladderConfigId) {
            return season;
          }

          @Override
          public RoundRobin createAndStart(
              Long ladderConfigId,
              String name,
              List<Long> participantIds,
              int rounds,
              Long createdById,
              RoundRobin.Format format,
              List<List<Long>> fixedTeams) {
            capturedFormat[0] = format;
            capturedTeams[0] = fixedTeams;
            return created;
          }
        };

    RoundRobinController controller =
        new RoundRobinController(
            service,
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);

    String view =
        controller.start(
            42L,
            null,
            List.of(1L, 2L, 3L, 4L),
            5,
            "FIXED_TEAMS",
            "[[1,2],[3,4]]",
            new MockHttpServletRequest(),
            new ExtendedModelMap(),
            new RedirectAttributesModelMap(),
            principal);

    assertThat(view).isEqualTo("redirect:/round-robin/view/55");
    assertThat(capturedFormat[0]).isEqualTo(RoundRobin.Format.FIXED_TEAMS);
    assertThat(capturedTeams[0]).containsExactly(List.of(1L, 2L), List.of(3L, 4L));
  }

  @Test
  void startWithTooFewPlayersUsesToastWithoutInlineError() {
    User viewer = user(1L, "viewer");
    User partner = user(2L, "partner");
    User opponent = user(3L, "opponent");
    CustomUserDetails principal = new CustomUserDetails(viewer);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 77L);

    RoundRobinService service =
        new RoundRobinService(null, null, null, null, null, null, null) {
          @Override
          public List<User> listMembersForLadder(Long ladderConfigId) {
            return List.of(viewer, partner, opponent);
          }

          @Override
          public LadderSeason findSeasonForLadder(Long ladderConfigId) {
            return season;
          }

          @Override
          public String getDisplayNameForUser(User user, Long ladderConfigId) {
            return user.getNickName();
          }

          @Override
          public Map<Long, String> buildCourtNameMap(
              java.util.Collection<Long> userIds, Long ladderConfigId) {
            return Map.of();
          }
        };

    RoundRobinController controller =
        new RoundRobinController(
            service,
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);

    ExtendedModelMap model = new ExtendedModelMap();
    String view =
        controller.start(
            42L,
            null,
            List.of(1L, 2L, 3L),
            0,
            "ROTATING_PARTNERS",
            "",
            new MockHttpServletRequest(),
            model,
            new RedirectAttributesModelMap(),
            principal);

    assertThat(view).isEqualTo("roundrobin/start");
    assertThat(model.get("toastMessage"))
        .isEqualTo("Please select at least 4 players to start a round-robin.");
    assertThat(model.get("toastLevel")).isEqualTo("danger");
    assertThat(model.get("errorMessage")).isNull();
  }

  @Test
  void startDoesNotTrustStaleAdminFlagFromSessionPrincipal() {
    User staleActor = user(1L, "viewer");
    staleActor.setAdmin(true);
    User refreshedActor = user(1L, "viewer");
    refreshedActor.setAdmin(false);
    when(userRepository.findById(1L)).thenReturn(Optional.of(refreshedActor));
    ReflectionTestUtils.setField(
        AuthenticatedUserSupport.class,
        "authenticatedUserService",
        new AuthenticatedUserService(userRepository));

    User partner = user(2L, "partner");
    User opponentOne = user(3L, "opponentOne");
    User opponentTwo = user(4L, "opponentTwo");
    User reserve = user(5L, "reserve");
    CustomUserDetails principal = new CustomUserDetails(staleActor);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 77L);

    RoundRobinService service =
        new RoundRobinService(null, null, null, null, null, null, null) {
          @Override
          public List<User> listMembersForLadder(Long ladderConfigId) {
            return List.of(partner, opponentOne, opponentTwo, reserve);
          }

          @Override
          public LadderSeason findSeasonForLadder(Long ladderConfigId) {
            return season;
          }

          @Override
          public String getDisplayNameForUser(User user, Long ladderConfigId) {
            return user.getNickName();
          }

          @Override
          public Map<Long, String> buildCourtNameMap(
              java.util.Collection<Long> userIds, Long ladderConfigId) {
            return Map.of();
          }
        };

    RoundRobinController controller =
        new RoundRobinController(
            service,
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);

    ExtendedModelMap model = new ExtendedModelMap();
    String view =
        controller.start(
            42L,
            null,
            List.of(2L, 3L, 4L, 5L),
            5,
            "ROTATING_PARTNERS",
            "",
            new MockHttpServletRequest(),
            model,
            new RedirectAttributesModelMap(),
            principal);

    assertThat(view).isEqualTo("roundrobin/start");
    assertThat(model.get("toastMessage"))
        .isEqualTo("You must be a member of this ladder to start a round-robin.");
    assertThat(model.get("toastLevel")).isEqualTo("danger");
  }

  @Test
  void viewRejectsUserOutsideSeason() {
    User viewer = user(1L, "viewer");
    CustomUserDetails principal = new CustomUserDetails(viewer);
    RoundRobin rr = new RoundRobin();
    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 77L);
    rr.setSeason(season);
    RoundRobinController controller =
        new RoundRobinController(
            stubRoundRobinService(rr),
            stubAccessService(viewer, true),
            ladderMembershipRepository,
            matchConfirmationService);

    assertThatThrownBy(
            () ->
                controller.view(
                    55L, null, new ExtendedModelMap(), new RedirectAttributesModelMap(), principal))
        .isInstanceOf(SecurityException.class)
        .hasMessage("You must be a ladder member to view this.");
  }

  @Test
  void viewSessionRoundRobinUsesSessionMembershipInsteadOfSeasonAccess() {
    User viewer = user(1L, "viewer");
    CustomUserDetails principal = new CustomUserDetails(viewer);

    LadderConfig sessionConfig = new LadderConfig();
    sessionConfig.setId(42L);
    sessionConfig.setType(LadderConfig.Type.SESSION);

    LadderSeason targetSeason = new LadderSeason();
    ReflectionTestUtils.setField(targetSeason, "id", 77L);

    RoundRobin rr = new RoundRobin();
    rr.setSessionConfig(sessionConfig);
    rr.setSeason(targetSeason);

    RoundRobinService service =
        new RoundRobinService(null, null, null, null, null, null, null) {
          @Override
          public RoundRobin getRoundRobin(Long id) {
            return rr;
          }

          @Override
          public int resolveCurrentRound(RoundRobin rr) {
            return 1;
          }

          @Override
          public List<RoundRobinEntry> getEntriesForRound(Long rrId, int roundNumber) {
            return List.of();
          }

          @Override
          public List<com.w3llspring.fhpb.web.model.RoundRobinStanding> computeStandings(
              Long rrId) {
            return List.of();
          }

          @Override
          public boolean canAdvance(Long rrId) {
            return false;
          }

          @Override
          public int getMaxRound(Long rrId) {
            return 0;
          }

          @Override
          public List<User> listMembersForLadder(Long ladderConfigId) {
            return List.of(viewer);
          }

          @Override
          public Map<Long, String> buildDisplayNameMap(
              java.util.Collection<Long> userIds, Long ladderConfigId) {
            return Map.of(viewer.getId(), "viewer");
          }
        };

    RoundRobinController controller =
        new RoundRobinController(
            service,
            new LadderAccessService(null, null) {
              @Override
              public void requireMember(Long seasonId, User user) {
                throw new AssertionError(
                    "season membership should not be required for session round-robins");
              }
            },
            ladderMembershipRepository,
            matchConfirmationService);

    when(ladderMembershipRepository.findByLadderConfigIdAndUserId(42L, 1L))
        .thenReturn(Optional.of(membership(42L, 1L, LadderMembership.State.ACTIVE)));

    String viewName =
        controller.view(
            55L, null, new ExtendedModelMap(), new RedirectAttributesModelMap(), principal);

    assertThat(viewName).isEqualTo("roundrobin/view");
  }

  @Test
  void listRejectsUserOutsideLadder() {
    RoundRobinController controller =
        new RoundRobinController(
            stubRoundRobinService(null),
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);
    User viewer = user(1L, "viewer");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());

    LadderMembership leftMembership = membership(10L, 1L, LadderMembership.State.LEFT);
    when(ladderMembershipRepository.findByLadderConfigIdAndUserId(10L, 1L))
        .thenReturn(java.util.Optional.of(leftMembership));

    assertThatThrownBy(
            () ->
                controller.list(
                    10L, null, 0, 10, new MockHttpServletRequest(), auth, new ExtendedModelMap()))
        .isInstanceOf(SecurityException.class)
        .hasMessage("Round-robin unavailable.");
  }

  @Test
  void listSessionLadderUsesSessionContext() {
    User viewer = user(1L, "viewer");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());

    LadderConfig sessionConfig = new LadderConfig();
    sessionConfig.setId(42L);
    sessionConfig.setType(LadderConfig.Type.SESSION);

    LadderSeason targetSeason = new LadderSeason();
    ReflectionTestUtils.setField(targetSeason, "id", 77L);

    LadderMembership activeMembership = membership(42L, 1L, LadderMembership.State.ACTIVE);
    activeMembership.setLadderConfig(sessionConfig);

    RoundRobinController controller =
        new RoundRobinController(
            new RoundRobinService(null, null, null, null, null, null, null) {
              @Override
              public LadderSeason findSeasonForLadder(Long ladderConfigId) {
                return targetSeason;
              }

              @Override
              public org.springframework.data.domain.Page<RoundRobin> listForLadderSeason(
                  Long ladderConfigId, Long seasonId, int page, int size) {
                return new org.springframework.data.domain.PageImpl<>(List.of());
              }

              @Override
              public List<com.w3llspring.fhpb.web.model.RoundRobinStanding>
                  computeStandingsForSessionRoundRobins(LadderConfig sessionConfig) {
                return List.of();
              }
            },
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);
    ReflectionTestUtils.setField(controller, "ladderConfigRepository", ladderConfigRepository);

    when(ladderConfigRepository.findById(42L)).thenReturn(Optional.of(sessionConfig));
    when(ladderMembershipRepository.findByUserIdAndState(1L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(activeMembership));
    when(ladderMembershipRepository.findByLadderConfigIdAndUserId(42L, 1L))
        .thenReturn(Optional.of(activeMembership));

    ExtendedModelMap model = new ExtendedModelMap();
    String view = controller.list(42L, null, 0, 10, new MockHttpServletRequest(), auth, model);

    assertThat(view).isEqualTo("roundrobin/list");
    assertThat(model.get("isSessionLadder")).isEqualTo(Boolean.TRUE);
    assertThat(model.get("roundRobinBackPath")).isEqualTo("/groups/42");
  }

  @Test
  void viewMarksQuickLoggedSelfConfirmMatchAsConfirmable() {
    User viewer = user(1L, "viewer");
    User opponent = user(2L, "opponent");

    LadderConfig ladderConfig = new LadderConfig();
    ladderConfig.setId(10L);
    ladderConfig.setSecurityLevel(com.w3llspring.fhpb.web.model.LadderSecurity.SELF_CONFIRM);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 77L);
    season.setLadderConfig(ladderConfig);

    RoundRobin roundRobin = new RoundRobin();
    ReflectionTestUtils.setField(roundRobin, "id", 55L);
    roundRobin.setSeason(season);
    ReflectionTestUtils.setField(roundRobin, "createdAt", Instant.now());
    roundRobin.setCreatedBy(viewer);

    RoundRobinEntry entry = new RoundRobinEntry();
    ReflectionTestUtils.setField(entry, "id", 500L);
    entry.setRoundRobin(roundRobin);
    entry.setRoundNumber(1);
    entry.setA1(viewer);
    entry.setB1(opponent);
    entry.setMatchId(900L);

    Match match = new Match();
    ReflectionTestUtils.setField(match, "id", 900L);
    match.setSeason(season);
    match.setA1(viewer);
    match.setB1(opponent);
    match.setLoggedBy(viewer);
    match.setScoreA(11);
    match.setScoreB(7);
    match.setState(MatchState.PROVISIONAL);

    MatchConfirmation pending = new MatchConfirmation();
    pending.setMatch(match);
    pending.setPlayer(viewer);

    RoundRobinService service =
        new RoundRobinService(null, null, null, null, null, null, null) {
          @Override
          public RoundRobin getRoundRobin(Long id) {
            return roundRobin;
          }

          @Override
          public int resolveCurrentRound(RoundRobin rr) {
            return 1;
          }

          @Override
          public List<RoundRobinEntry> getEntriesForRound(Long rrId, int roundNumber) {
            return List.of(entry);
          }

          @Override
          public List<com.w3llspring.fhpb.web.model.RoundRobinStanding> computeStandings(
              Long rrId) {
            return Collections.emptyList();
          }

          @Override
          public boolean canAdvance(Long rrId) {
            return false;
          }

          @Override
          public int getMaxRound(Long rrId) {
            return 1;
          }

          @Override
          public List<User> listMembersForLadder(Long ladderConfigId) {
            return List.of(viewer, opponent);
          }

          @Override
          public Map<Long, String> buildDisplayNameMap(
              java.util.Collection<Long> userIds, Long ladderConfigId) {
            return Map.of(viewer.getId(), "viewer", opponent.getId(), "opponent");
          }

          @Override
          public Map<Long, Match> loadMatchesWithParticipants(java.util.Collection<Long> matchIds) {
            return Map.of(900L, match);
          }
        };

    when(matchConfirmationService.pendingForUser(1L)).thenReturn(List.of(pending));

    RoundRobinController controller =
        new RoundRobinController(
            service,
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);

    ExtendedModelMap model = new ExtendedModelMap();
    String viewName =
        controller.view(
            55L, null, model, new RedirectAttributesModelMap(), new CustomUserDetails(viewer));

    assertThat(viewName).isEqualTo("roundrobin/view");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entries = (List<Map<String, Object>>) model.get("entries");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).get("confirmEnabled")).isEqualTo(Boolean.TRUE);
  }

  @Test
  void startRejectsTournamentNonAdmin() {
    User viewer = user(1L, "viewer");
    User partner = user(2L, "partner");
    User opponentOne = user(3L, "opponentOne");
    User opponentTwo = user(4L, "opponentTwo");
    CustomUserDetails principal = new CustomUserDetails(viewer);

    LadderConfig tournament = new LadderConfig();
    tournament.setId(42L);
    tournament.setTournamentMode(true);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 77L);

    RoundRobinController controller =
        new RoundRobinController(
            new RoundRobinService(null, null, null, null, null, null, null) {
              @Override
              public List<User> listMembersForLadder(Long ladderConfigId) {
                return List.of(viewer, partner, opponentOne, opponentTwo);
              }

              @Override
              public LadderSeason findSeasonForLadder(Long ladderConfigId) {
                return season;
              }
            },
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);
    ReflectionTestUtils.setField(controller, "ladderConfigRepository", ladderConfigRepository);

    LadderMembership membership = membership(42L, 1L, LadderMembership.State.ACTIVE);
    membership.setRole(LadderMembership.Role.MEMBER);
    when(ladderConfigRepository.findById(42L)).thenReturn(Optional.of(tournament));
    when(ladderMembershipRepository.findByLadderConfigIdAndUserId(42L, 1L))
        .thenReturn(Optional.of(membership));

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
    String view =
        controller.start(
            42L,
            null,
            List.of(1L, 2L, 3L, 4L),
            5,
            "ROTATING_PARTNERS",
            "",
            new MockHttpServletRequest(),
            new ExtendedModelMap(),
            redirectAttributes,
            principal);

    assertThat(view).isEqualTo("redirect:/round-robin/list?ladderId=42&seasonId=77");
    assertThat(redirectAttributes.getFlashAttributes().get("toastMessage"))
        .isEqualTo(
            "Tournament mode requires group admins to start and manage round-robins. Matches logged for this season must match an active round-robin pairing.");
  }

  @Test
  void startAllowsTournamentAdmin() {
    User admin = user(1L, "admin");
    User partner = user(2L, "partner");
    User opponentOne = user(3L, "opponentOne");
    User opponentTwo = user(4L, "opponentTwo");
    CustomUserDetails principal = new CustomUserDetails(admin);

    LadderConfig tournament = new LadderConfig();
    tournament.setId(42L);
    tournament.setTournamentMode(true);

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 77L);

    RoundRobin created = new RoundRobin();
    ReflectionTestUtils.setField(created, "id", 55L);

    RoundRobinController controller =
        new RoundRobinController(
            new RoundRobinService(null, null, null, null, null, null, null) {
              @Override
              public List<User> listMembersForLadder(Long ladderConfigId) {
                return List.of(admin, partner, opponentOne, opponentTwo);
              }

              @Override
              public LadderSeason findSeasonForLadder(Long ladderConfigId) {
                return season;
              }

              @Override
              public RoundRobin createAndStart(
                  Long ladderConfigId,
                  String name,
                  List<Long> participantIds,
                  int rounds,
                  Long createdById,
                  RoundRobin.Format format,
                  List<List<Long>> fixedTeams) {
                return created;
              }
            },
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);
    ReflectionTestUtils.setField(controller, "ladderConfigRepository", ladderConfigRepository);

    LadderMembership membership = membership(42L, 1L, LadderMembership.State.ACTIVE);
    membership.setRole(LadderMembership.Role.ADMIN);
    when(ladderConfigRepository.findById(42L)).thenReturn(Optional.of(tournament));
    when(ladderMembershipRepository.findByLadderConfigIdAndUserId(42L, 1L))
        .thenReturn(Optional.of(membership));

    String view =
        controller.start(
            42L,
            null,
            List.of(1L, 2L, 3L, 4L),
            5,
            "ROTATING_PARTNERS",
            "",
            new MockHttpServletRequest(),
            new ExtendedModelMap(),
            new RedirectAttributesModelMap(),
            principal);

    assertThat(view).isEqualTo("redirect:/round-robin/view/55");
  }

  @Test
  void startRejectsSessionMemberWhoIsNotSessionOwner() {
    User member = user(1L, "member");
    User owner = user(9L, "owner");
    User partner = user(2L, "partner");
    User opponentOne = user(3L, "opponentOne");
    User opponentTwo = user(4L, "opponentTwo");
    CustomUserDetails principal = new CustomUserDetails(member);

    LadderConfig session = new LadderConfig();
    session.setId(42L);
    session.setType(LadderConfig.Type.SESSION);
    session.setOwnerUserId(owner.getId());

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 77L);

    RoundRobinController controller =
        new RoundRobinController(
            new RoundRobinService(null, null, null, null, null, null, null) {
              @Override
              public List<User> listMembersForLadder(Long ladderConfigId) {
                return List.of(member, partner, opponentOne, opponentTwo);
              }

              @Override
              public LadderSeason findSeasonForLadder(Long ladderConfigId) {
                return season;
              }
            },
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);
    ReflectionTestUtils.setField(controller, "ladderConfigRepository", ladderConfigRepository);

    when(ladderConfigRepository.findById(42L)).thenReturn(Optional.of(session));

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
    String view =
        controller.start(
            42L,
            null,
            List.of(1L, 2L, 3L, 4L),
            5,
            "ROTATING_PARTNERS",
            "",
            new MockHttpServletRequest(),
            new ExtendedModelMap(),
            redirectAttributes,
            principal);

    assertThat(view).isEqualTo("redirect:/round-robin/list?ladderId=42");
    assertThat(redirectAttributes.getFlashAttributes().get("toastMessage"))
        .isEqualTo("Only the session starter can start round-robins from this session.");
  }

  @Test
  void startAllowsSessionOwner() {
    User owner = user(1L, "owner");
    User partner = user(2L, "partner");
    User opponentOne = user(3L, "opponentOne");
    User opponentTwo = user(4L, "opponentTwo");
    CustomUserDetails principal = new CustomUserDetails(owner);

    LadderConfig session = new LadderConfig();
    session.setId(42L);
    session.setType(LadderConfig.Type.SESSION);
    session.setOwnerUserId(owner.getId());

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 77L);

    RoundRobin created = new RoundRobin();
    ReflectionTestUtils.setField(created, "id", 55L);

    RoundRobinController controller =
        new RoundRobinController(
            new RoundRobinService(null, null, null, null, null, null, null) {
              @Override
              public List<User> listMembersForLadder(Long ladderConfigId) {
                return List.of(owner, partner, opponentOne, opponentTwo);
              }

              @Override
              public LadderSeason findSeasonForLadder(Long ladderConfigId) {
                return season;
              }

              @Override
              public RoundRobin createAndStart(
                  Long ladderConfigId,
                  String name,
                  List<Long> participantIds,
                  int rounds,
                  Long createdById,
                  RoundRobin.Format format,
                  List<List<Long>> fixedTeams) {
                return created;
              }
            },
            stubAccessService(null, false),
            ladderMembershipRepository,
            matchConfirmationService);
    ReflectionTestUtils.setField(controller, "ladderConfigRepository", ladderConfigRepository);

    when(ladderConfigRepository.findById(42L)).thenReturn(Optional.of(session));

    String view =
        controller.start(
            42L,
            null,
            List.of(1L, 2L, 3L, 4L),
            5,
            "ROTATING_PARTNERS",
            "",
            new MockHttpServletRequest(),
            new ExtendedModelMap(),
            new RedirectAttributesModelMap(),
            principal);

    assertThat(view).isEqualTo("redirect:/round-robin/view/55");
  }

  private RoundRobinService stubRoundRobinService(RoundRobin roundRobin) {
    return new RoundRobinService(null, null, null, null, null, null, null) {
      @Override
      public List<User> listMembersForLadder(Long ladderConfigId) {
        throw new AssertionError("listMembersForLadder should not be reached");
      }

      @Override
      public com.w3llspring.fhpb.web.model.LadderSeason findSeasonForLadder(Long ladderConfigId) {
        throw new AssertionError("findSeasonForLadder should not be reached");
      }

      @Override
      public RoundRobin getRoundRobin(Long id) {
        return roundRobin;
      }

      @Override
      public List<RoundRobinEntry> getEntriesForRound(Long rrId, int roundNumber) {
        throw new AssertionError("getEntriesForRound should not be reached");
      }

      @Override
      public org.springframework.data.domain.Page<RoundRobin> listForLadderSeason(
          Long ladderConfigId, Long seasonId, int page, int size) {
        throw new AssertionError("listForLadderSeason should not be reached");
      }
    };
  }

  private LadderAccessService stubAccessService(User deniedUser, boolean deny) {
    return new LadderAccessService(null, null) {
      @Override
      public void requireMember(Long seasonId, User user) {
        if (deny && user != null && deniedUser != null && deniedUser.getId().equals(user.getId())) {
          throw new SecurityException("You must be a ladder member to view this.");
        }
      }
    };
  }

  private User user(Long id, String nickName) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickName);
    user.setEmail(nickName + "@test.local");
    return user;
  }

  private LadderMembership membership(Long ladderId, Long userId, LadderMembership.State state) {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(ladderId);

    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(ladder);
    membership.setUserId(userId);
    membership.setState(state);
    membership.setRole(LadderMembership.Role.MEMBER);
    return membership;
  }
}
