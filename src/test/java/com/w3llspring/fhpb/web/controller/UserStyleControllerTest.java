package com.w3llspring.fhpb.web.controller;

import com.w3llspring.fhpb.web.controller.account.UserStyleController;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.w3llspring.fhpb.web.controller.advice.SecurityExceptionHandler;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.UserCourtNameRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.db.UserStyleRepository;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.db.UserTrophyRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyArt;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserTrophy;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.user.CourtNameSimilarityService;
import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import com.w3llspring.fhpb.web.service.user.UserAccountSettingsService;
import com.w3llspring.fhpb.web.service.user.UserIdentityService;
import com.w3llspring.fhpb.web.service.user.UserIdentityService.DisplayNameChangeResult;

@ExtendWith(MockitoExtension.class)
class UserStyleControllerTest {

    @Mock
    private UserStyleRepository userStyleRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private MatchRepository matchRepo;

    @Mock
    private UserCourtNameRepository userCourtNameRepo;

    @Mock
    private LadderMembershipRepository ladderMembershipRepository;

    @Mock
    private UserTrophyRepository userTrophyRepository;

    @Mock
    private TrophyRepository trophyRepository;

    @Mock
    private DisplayNameModerationService displayNameModerationService;

    @Mock
    private MatchConfirmationRepository matchConfirmationRepository;

    @Mock
    private MatchConfirmationService matchConfirmationService;

    private UserStyleController controller;
    private RecordingUserIdentityService userIdentityService;
    private RecordingUserAccountSettingsService userAccountSettingsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new UserStyleController();
        userIdentityService = new RecordingUserIdentityService();
        userAccountSettingsService = new RecordingUserAccountSettingsService();
        ReflectionTestUtils.setField(controller, "userStyleRepo", userStyleRepo);
        ReflectionTestUtils.setField(controller, "userRepo", userRepo);
        ReflectionTestUtils.setField(controller, "matchRepo", matchRepo);
        ReflectionTestUtils.setField(controller, "userCourtNameRepo", userCourtNameRepo);
        ReflectionTestUtils.setField(controller, "ladderMembershipRepository", ladderMembershipRepository);
        ReflectionTestUtils.setField(controller, "userTrophyRepository", userTrophyRepository);
        ReflectionTestUtils.setField(controller, "trophyRepository", trophyRepository);
        ReflectionTestUtils.setField(controller, "displayNameModerationService", displayNameModerationService);
        ReflectionTestUtils.setField(controller, "courtNameSimilarityService", new CourtNameSimilarityService());
        ReflectionTestUtils.setField(controller, "matchRowModelBuilder",
                new com.w3llspring.fhpb.web.service.MatchRowModelBuilder(
                        matchConfirmationService,
                        matchConfirmationRepository,
                        new LadderAccessService(null, null)));
        ReflectionTestUtils.setField(controller, "userIdentityService", userIdentityService);
        ReflectionTestUtils.setField(controller, "userAccountSettingsService", userAccountSettingsService);
        lenient().when(userRepo.findById(anyLong())).thenReturn(Optional.empty());
        lenient().when(matchRepo.countParticipantMatchesIncludingNullified(any())).thenReturn(0L);
        lenient().when(matchConfirmationRepository.findByMatchIdIn(any())).thenReturn(Collections.emptyList());
        lenient().when(matchConfirmationService.pendingForUser(anyLong())).thenReturn(Collections.emptyList());
        lenient().when(trophyRepository.findByBadgeSelectableByAllTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(Collections.emptyList());
        lenient().when(userTrophyRepository.findByUserIdWithTrophyAndSeasonOrderBySeasonStartDateDesc(anyLong()))
                .thenReturn(Collections.emptyList());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new SecurityExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userDetailsReturnsNotFoundWhenUsersDoNotShareActiveLadder() throws Exception {
        User viewer = user(1L, "viewer");
        User owner = user(2L, "owner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of()));

        when(userRepo.findByPublicCode(owner.getPublicCode())).thenReturn(Optional.of(owner));
        when(ladderMembershipRepository.findByUserIdAndState(1L, LadderMembership.State.ACTIVE))
                .thenReturn(List.of(membership(11L, 1L)));
        when(ladderMembershipRepository.findByUserIdAndState(2L, LadderMembership.State.ACTIVE))
                .thenReturn(List.of(membership(22L, 2L)));

        assertThatThrownBy(() -> controller.viewUser(owner.getPublicCode(), new ExtendedModelMap()))
                .isInstanceOf(SecurityException.class)
                .hasMessage("User details unavailable.");

        verify(userStyleRepo, never()).findUserStyles(any());
        verify(matchRepo, never()).findParticipantMatchIdsOrderByPlayedAtDescIncludingNullified(any(), any());
    }

    @Test
    void userDetailsAllowsUsersWhoShareActiveLadder() throws Exception {
        User viewer = user(1L, "viewer");
        User owner = user(2L, "owner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of()));

        when(userRepo.findByPublicCode(owner.getPublicCode())).thenReturn(Optional.of(owner));
        when(ladderMembershipRepository.findByUserIdAndState(1L, LadderMembership.State.ACTIVE))
                .thenReturn(List.of(membership(11L, 1L)));
        when(ladderMembershipRepository.findByUserIdAndState(2L, LadderMembership.State.ACTIVE))
                .thenReturn(List.of(membership(11L, 2L)));
        when(userStyleRepo.findUserStyles(2L)).thenReturn(Collections.emptyList());
        when(userStyleRepo.findIfVoted(2L, 1L)).thenReturn(0);

        var result = mockMvc.perform(get("/account").param("member", owner.getPublicCode()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getViewName()).isEqualTo("auth/userDetails");
        assertThat(result.getModelAndView().getModel().get("queryName")).isEqualTo("owner");
        assertThat(result.getModelAndView().getModel().get("memberCode")).isEqualTo(owner.getPublicCode());
        assertThat(result.getModelAndView().getModel()).doesNotContainKey("showRecentMatches");
        assertThat(result.getModelAndView().getModel().get("accountPageTitle")).isEqualTo("owner's Account");
        assertThat(result.getModelAndView().getModel().get("accountPageSubtitle"))
                .isEqualTo("");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Recent matches");
        verify(matchRepo, never()).findParticipantMatchIdsOrderByPlayedAtDescIncludingNullified(any(), any());
    }

    @Test
    void userDetailsReturnsNotFoundWhenMemberCodeDoesNotExist() throws Exception {
        User viewer = user(1L, "viewer");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of()));

        when(userRepo.findByPublicCode("PB-missing")).thenReturn(Optional.empty());
        when(userRepo.findById(1L)).thenReturn(Optional.of(viewer));

        mockMvc.perform(get("/account").param("member", "PB-missing"))
                .andExpect(status().isNotFound());

        verify(userStyleRepo, never()).findUserStyles(any());
        verify(matchRepo, never()).findParticipantMatchIdsOrderByPlayedAtDescIncludingNullified(any(), any());
    }

    @Test
    void userDetailsRejectsBannedMembershipAsSharedAccess() {
        User viewer = user(1L, "viewer");
        User owner = user(2L, "owner");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of()));

        when(userRepo.findByPublicCode(owner.getPublicCode())).thenReturn(Optional.of(owner));
        when(ladderMembershipRepository.findByUserIdAndState(1L, LadderMembership.State.ACTIVE))
                .thenReturn(List.of(membership(11L, 1L)));
        when(ladderMembershipRepository.findByUserIdAndState(2L, LadderMembership.State.ACTIVE))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> controller.viewUser(owner.getPublicCode(), new ExtendedModelMap()))
                .isInstanceOf(SecurityException.class)
                .hasMessage("User details unavailable.");

        verify(userStyleRepo, never()).findUserStyles(any());
        verify(matchRepo, never()).findParticipantMatchIdsOrderByPlayedAtDescIncludingNullified(any(), any());
    }

    @Test
    void userDetailsAllowsSelfWithoutSharedLadder() throws Exception {
        User viewer = user(1L, "viewer");
        viewer.setPublicCode("PB-sage-ace-247");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of()));

        when(userRepo.findById(1L)).thenReturn(Optional.of(viewer));
        when(userStyleRepo.findUserStyles(1L)).thenReturn(Collections.emptyList());
        when(userStyleRepo.findIfVoted(1L, 1L)).thenReturn(0);
        when(userCourtNameRepo.findByUser_Id(1L)).thenReturn(Collections.emptyList());
        when(ladderMembershipRepository.findByUserIdAndState(1L, LadderMembership.State.ACTIVE))
                .thenReturn(Collections.emptyList());

        var result = mockMvc.perform(get("/account"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getViewName()).isEqualTo("auth/userDetails");
        assertThat(result.getModelAndView().getModel().get("canEditDisplayName")).isEqualTo(true);
        assertThat(result.getModelAndView().getModel().get("profilePublicCode")).isEqualTo("PB-sage-ace-247");
        assertThat(result.getModelAndView().getModel().get("memberCode")).isEqualTo("PB-sage-ace-247");
        assertThat(result.getModelAndView().getModel()).doesNotContainKey("showRecentMatches");
        assertThat(result.getModelAndView().getModel().get("accountPageTitle")).isEqualTo("viewer's Account");
        assertThat(result.getModelAndView().getModel().get("accountPageSubtitle"))
                .isEqualTo("Update viewer's profile, names, and preferences.");
    }

    @Test
    void accountPageFallsBackToAuthenticatedUserIdWhenSessionNicknameIsStale() throws Exception {
        User sessionUser = user(1L, "Test");
        User dbUser = user(1L, "AdminCharlie");
        dbUser.setPublicCode("PB-sage-ace-247");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(sessionUser), null, List.of()));

        when(userRepo.findById(1L)).thenReturn(Optional.of(dbUser));
        when(userStyleRepo.findUserStyles(1L)).thenReturn(Collections.emptyList());
        when(userStyleRepo.findIfVoted(1L, 1L)).thenReturn(0);
        when(userCourtNameRepo.findByUser_Id(1L)).thenReturn(Collections.emptyList());
        when(ladderMembershipRepository.findByUserIdAndState(1L, LadderMembership.State.ACTIVE))
                .thenReturn(Collections.emptyList());

        var result = mockMvc.perform(get("/account"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getModel().get("queryName")).isEqualTo("AdminCharlie");
        assertThat(result.getModelAndView().getModel().get("canEditDisplayName")).isEqualTo(true);
        assertThat(result.getModelAndView().getModel().get("userName")).isEqualTo("AdminCharlie");
        assertThat(result.getModelAndView().getModel().get("accountPageTitle")).isEqualTo("AdminCharlie's Account");
        assertThat(result.getModelAndView().getModel().get("accountPageSubtitle"))
                .isEqualTo("Update AdminCharlie's profile, names, and preferences.");
        assertThat(sessionUser.getNickName()).isEqualTo("AdminCharlie");
    }

    @Test
    void userDetailsSortsHydratedRecentMatchesNewestFirst() throws Exception {
        User viewer = user(1L, "viewer");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of()));

        var older = new com.w3llspring.fhpb.web.model.Match();
        older.setPlayedAt(Instant.parse("2026-03-01T10:00:00Z"));
        setId(older, 10L);

        var newer = new com.w3llspring.fhpb.web.model.Match();
        newer.setPlayedAt(Instant.parse("2026-03-02T10:00:00Z"));
        setId(newer, 20L);

        when(userRepo.findById(1L)).thenReturn(Optional.of(viewer));
        when(userStyleRepo.findUserStyles(1L)).thenReturn(Collections.emptyList());
        when(matchRepo.countParticipantMatchesIncludingNullified(viewer)).thenReturn(2L);
        when(matchRepo.findParticipantMatchIdsOrderByPlayedAtDescIncludingNullified(any(), any())).thenReturn(List.of(20L, 10L));
        when(matchRepo.findAllByIdInWithUsers(List.of(20L, 10L))).thenReturn(List.of(older, newer));
        when(userStyleRepo.findIfVoted(1L, 1L)).thenReturn(0);
        when(userCourtNameRepo.findByUser_Id(1L)).thenReturn(Collections.emptyList());
        when(ladderMembershipRepository.findByUserIdAndState(1L, LadderMembership.State.ACTIVE))
                .thenReturn(Collections.emptyList());

        var result = mockMvc.perform(get("/account"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        var recentMatches = (List<com.w3llspring.fhpb.web.model.Match>) result.getModelAndView()
                .getModel().get("recentMatches");
        assertThat(recentMatches).extracting(com.w3llspring.fhpb.web.model.Match::getId)
                .containsExactly(20L, 10L);
    }

    @Test
    void userDetailsPaginatesRecentMatchesOnRequestedAccountPage() throws Exception {
        User viewer = user(1L, "viewer");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of()));

        var pageTwoMatch = new com.w3llspring.fhpb.web.model.Match();
        pageTwoMatch.setPlayedAt(Instant.parse("2026-03-03T10:00:00Z"));
        setId(pageTwoMatch, 90L);

        when(userRepo.findById(1L)).thenReturn(Optional.of(viewer));
        when(userStyleRepo.findUserStyles(1L)).thenReturn(Collections.emptyList());
        when(matchRepo.countParticipantMatchesIncludingNullified(viewer)).thenReturn(9L);
        when(matchRepo.findParticipantMatchIdsOrderByPlayedAtDescIncludingNullified(any(), any())).thenReturn(List.of(90L));
        when(matchRepo.findAllByIdInWithUsers(List.of(90L))).thenReturn(List.of(pageTwoMatch));
        when(userStyleRepo.findIfVoted(1L, 1L)).thenReturn(0);
        when(userCourtNameRepo.findByUser_Id(1L)).thenReturn(Collections.emptyList());
        when(ladderMembershipRepository.findByUserIdAndState(1L, LadderMembership.State.ACTIVE))
                .thenReturn(Collections.emptyList());

        var result = mockMvc.perform(get("/account").param("recentMatchesPage", "1"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getModel().get("recentMatchesPage")).isEqualTo(1);
        assertThat(result.getModelAndView().getModel().get("recentMatchesTotalPages")).isEqualTo(2);
        assertThat(result.getModelAndView().getModel().get("recentMatchesPageNumbers")).isEqualTo(List.of(0, 1));
        @SuppressWarnings("unchecked")
        var recentMatches = (List<com.w3llspring.fhpb.web.model.Match>) result.getModelAndView()
                .getModel().get("recentMatches");
        assertThat(recentMatches).extracting(com.w3llspring.fhpb.web.model.Match::getId)
                .containsExactly(90L);
    }

    @Test
    void changeDisplayNameRejectsRenameDuringCooldownWindow() {
        User currentUser = user(1L, "viewer");
        currentUser.setLastDisplayNameChangeAt(Instant.now().minusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(currentUser), null, List.of()));

        ReflectionTestUtils.setField(controller, "displayNameChangeCooldownSeconds", 3600L);

        when(displayNameModerationService.explainViolation("renamed")).thenReturn(Optional.empty());
        when(userRepo.findById(1L)).thenReturn(Optional.of(currentUser));
        userIdentityService.nextResult = DisplayNameChangeResult.cooldown(
                currentUser,
                Instant.now().plusSeconds(3300));

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.changeDisplayName("viewer", "renamed", redirectAttributes);

        assertThat(view).isEqualTo("redirect:/account");
        assertThat(redirectAttributes.getFlashAttributes().get("toastLevel")).isEqualTo("warning");
        assertThat(String.valueOf(redirectAttributes.getFlashAttributes().get("toastMessage")))
                .contains("You can change your display name again in");
        assertThat(userIdentityService.called).isTrue();
    }

    @Test
    void changeDisplayNameDelegatesToIdentityServiceAndUpdatesPrincipal() {
        User currentUser = user(1L, "viewer");
        User managedUser = user(1L, "viewer");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(currentUser), null, List.of()));

        when(displayNameModerationService.explainViolation("Renamed")).thenReturn(Optional.empty());
        when(userRepo.findById(1L)).thenReturn(Optional.of(managedUser));
        managedUser.setNickName("Renamed");
        managedUser.setLastDisplayNameChangeAt(Instant.parse("2026-03-13T15:00:00Z"));
        userIdentityService.nextResult = DisplayNameChangeResult.changed(managedUser, Instant.parse("2026-03-13T15:00:00Z"));

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.changeDisplayName("viewer", "Renamed", redirectAttributes);

        assertThat(view).isEqualTo("redirect:/account");
        assertThat(userIdentityService.called).isTrue();
        assertThat(userIdentityService.userId).isEqualTo(1L);
        assertThat(userIdentityService.newDisplayName).isEqualTo("Renamed");
        assertThat(userIdentityService.changedByUserId).isEqualTo(1L);
        assertThat(userIdentityService.changedAt).isNotNull();
        assertThat(currentUser.getNickName()).isEqualTo("Renamed");
        assertThat(currentUser.getLastDisplayNameChangeAt()).isNotNull();
        assertThat(redirectAttributes.getFlashAttributes().get("toastLevel")).isEqualTo("success");
        assertThat(redirectAttributes.getFlashAttributes().get("toastMessage")).isEqualTo("Display name updated.");
    }

    @Test
    void changeDisplayNameRejectsUnsupportedCharacters() {
        User currentUser = user(1L, "viewer");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(currentUser), null, List.of()));

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.changeDisplayName("viewer", "<script>", redirectAttributes);

        assertThat(view).isEqualTo("redirect:/account");
        assertThat(redirectAttributes.getFlashAttributes().get("toastLevel")).isEqualTo("danger");
        assertThat(redirectAttributes.getFlashAttributes().get("toastMessage"))
                .isEqualTo("Display name contains unsupported characters.");
        assertThat(userIdentityService.called).isFalse();
    }

    @Test
    void updateBadgesPersistsOwnedBadgeSelections() {
        User currentUser = user(1L, "viewer");
        User managedUser = user(1L, "viewer");
        Trophy ownedTrophy = trophy(44L, "Season Champion");
        Trophy oldBadge = trophy(12L, "Old Badge");
        UserTrophy userTrophy = new UserTrophy();
        userTrophy.setUser(managedUser);
        userTrophy.setTrophy(ownedTrophy);
        managedUser.setBadgeSlot2Trophy(oldBadge);
        managedUser.setBadgeSlot3Trophy(oldBadge);
        currentUser.setBadgeSlot2Trophy(oldBadge);
        currentUser.setBadgeSlot3Trophy(oldBadge);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(currentUser), null, List.of()));

        when(userTrophyRepository.findByUserIdWithTrophyAndSeasonOrderBySeasonStartDateDesc(1L))
                .thenReturn(List.of(userTrophy));
        managedUser.setBadgeSlot1Trophy(ownedTrophy);
        managedUser.setBadgeSlot2Trophy(null);
        managedUser.setBadgeSlot3Trophy(null);
        userAccountSettingsService.nextUser = managedUser;

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.updateBadges("44", redirectAttributes);

        assertThat(view).isEqualTo("redirect:/account");
        assertThat(managedUser.getBadgeSlot1TrophyId()).isEqualTo(44L);
        assertThat(managedUser.getBadgeSlot2TrophyId()).isNull();
        assertThat(managedUser.getBadgeSlot3TrophyId()).isNull();
        assertThat(currentUser.getBadgeSlot1TrophyId()).isEqualTo(44L);
        assertThat(currentUser.getBadgeSlot2TrophyId()).isNull();
        assertThat(currentUser.getBadgeSlot3TrophyId()).isNull();
        assertThat(redirectAttributes.getFlashAttributes().get("toastLevel")).isEqualTo("success");
        assertThat(redirectAttributes.getFlashAttributes().get("toastMessage")).isEqualTo("Name badge updated.");
        assertThat(userAccountSettingsService.badgeUserId).isEqualTo(1L);
        assertThat(userAccountSettingsService.badgeSlot1Trophy).isEqualTo(ownedTrophy);
    }

    @Test
    void updateBadgesAjaxPersistsOwnedBadgeSelectionsWithoutRedirect() {
        User currentUser = user(1L, "viewer");
        User managedUser = user(1L, "viewer");
        Trophy ownedTrophy = trophy(44L, "Season Champion");
        UserTrophy userTrophy = new UserTrophy();
        userTrophy.setUser(managedUser);
        userTrophy.setTrophy(ownedTrophy);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(currentUser), null, List.of()));

        when(userTrophyRepository.findByUserIdWithTrophyAndSeasonOrderBySeasonStartDateDesc(1L))
                .thenReturn(List.of(userTrophy));
        managedUser.setBadgeSlot1Trophy(ownedTrophy);
        userAccountSettingsService.nextUser = managedUser;

        var response = controller.updateBadgesAjax("44");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().level()).isEqualTo("success");
        assertThat(response.getBody().message()).isEqualTo("Name badge updated.");
        assertThat(response.getBody().redirectUrl()).isNull();
        assertThat(currentUser.getBadgeSlot1TrophyId()).isEqualTo(44L);
        assertThat(userAccountSettingsService.badgeUserId).isEqualTo(1L);
        assertThat(userAccountSettingsService.badgeSlot1Trophy).isEqualTo(ownedTrophy);
    }

    @Test
    void updateBadgesPersistsAlwaysAvailableBadgeSelections() {
        User currentUser = user(1L, "viewer");
        User managedUser = user(1L, "viewer");
        Trophy profileBadge = alwaysAvailableBadge(77L, "Test Badge", "Country flag");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(currentUser), null, List.of()));

        when(trophyRepository.findByBadgeSelectableByAllTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(profileBadge));
        managedUser.setBadgeSlot1Trophy(profileBadge);
        userAccountSettingsService.nextUser = managedUser;

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.updateBadges("77", redirectAttributes);

        assertThat(view).isEqualTo("redirect:/account");
        assertThat(redirectAttributes.getFlashAttributes().get("toastLevel")).isEqualTo("success");
        assertThat(redirectAttributes.getFlashAttributes().get("toastMessage")).isEqualTo("Name badge updated.");
        assertThat(currentUser.getBadgeSlot1TrophyId()).isEqualTo(77L);
        assertThat(userAccountSettingsService.badgeSlot1Trophy).isEqualTo(profileBadge);
    }

    @Test
    void updateBadgesRejectsTrophiesTheUserDoesNotOwn() {
        User currentUser = user(1L, "viewer");
        User managedUser = user(1L, "viewer");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(currentUser), null, List.of()));

        when(userTrophyRepository.findByUserIdWithTrophyAndSeasonOrderBySeasonStartDateDesc(1L))
                .thenReturn(Collections.emptyList());

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.updateBadges("99", redirectAttributes);

        assertThat(view).isEqualTo("redirect:/account");
        assertThat(redirectAttributes.getFlashAttributes().get("toastLevel")).isEqualTo("danger");
        assertThat(redirectAttributes.getFlashAttributes().get("toastMessage"))
                .isEqualTo("You can only equip a badge you've unlocked or one that's always available.");
        assertThat(userAccountSettingsService.badgeUserId).isNull();
    }

    @Test
    void updateBadgesAjaxRejectsTrophiesTheUserDoesNotOwn() {
        User currentUser = user(1L, "viewer");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(currentUser), null, List.of()));

        when(userTrophyRepository.findByUserIdWithTrophyAndSeasonOrderBySeasonStartDateDesc(1L))
                .thenReturn(Collections.emptyList());

        var response = controller.updateBadgesAjax("99");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().level()).isEqualTo("danger");
        assertThat(response.getBody().message())
                .isEqualTo("You can only equip a badge you've unlocked or one that's always available.");
        assertThat(response.getBody().redirectUrl()).isNull();
        assertThat(userAccountSettingsService.badgeUserId).isNull();
    }

    @Test
    void viewUserIncludesAlwaysAvailableBadgesInBadgePickerModel() {
        User currentUser = user(1L, "viewer");
        Trophy profileBadge = alwaysAvailableBadge(77L, "Test Badge", "Country flag");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(currentUser), null, List.of()));

        when(userRepo.findById(1L)).thenReturn(Optional.of(currentUser));
        when(trophyRepository.findByBadgeSelectableByAllTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(profileBadge));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.viewUser(null, model);

        assertThat(view).isEqualTo("auth/userDetails");
        List<?> badgeOptions = (List<?>) model.get("badgeOptions");
        assertThat(badgeOptions).hasSize(1);
        Object option = badgeOptions.get(0);
        assertThat(ReflectionTestUtils.getField(option, "badgeLabel")).isEqualTo("Test Badge");
        assertThat(ReflectionTestUtils.getField(option, "groupLabel")).isEqualTo("Country flag");
        assertThat(ReflectionTestUtils.getField(option, "awardedLabel")).isEqualTo("Always available");
    }

    private User user(Long id, String nickName) {
        User user = new User();
        user.setId(id);
        user.setNickName(nickName);
        user.setEmail(nickName + "@test.local");
        user.setPublicCode("PB-" + nickName);
        return user;
    }

    private void setId(com.w3llspring.fhpb.web.model.Match match, Long id) {
        ReflectionTestUtils.setField(match, "id", id);
    }

    private LadderMembership membership(Long ladderId, Long userId) {
        LadderConfig ladder = new LadderConfig();
        ladder.setId(ladderId);

        LadderMembership membership = new LadderMembership();
        membership.setLadderConfig(ladder);
        membership.setUserId(userId);
        membership.setState(LadderMembership.State.ACTIVE);
        return membership;
    }

    private Trophy trophy(Long id, String title) {
        Trophy trophy = new Trophy();
        ReflectionTestUtils.setField(trophy, "id", id);
        trophy.setTitle(title);
        trophy.setArt(new TrophyArt());
        LadderSeason season = new LadderSeason();
        season.setName("Spring Story");
        trophy.setSeason(season);
        return trophy;
    }

    private Trophy alwaysAvailableBadge(Long id, String title, String summary) {
        Trophy trophy = new Trophy();
        ReflectionTestUtils.setField(trophy, "id", id);
        trophy.setTitle(title);
        trophy.setSummary(summary);
        trophy.setBadgeSelectableByAll(true);
        trophy.setBadgeArt(new TrophyArt());
        return trophy;
    }

    private static final class RecordingUserIdentityService extends UserIdentityService {
        private boolean called;
        private Long userId;
        private String newDisplayName;
        private Long changedByUserId;
        private Instant changedAt;
        private Duration cooldown;
        private DisplayNameChangeResult nextResult;

        private RecordingUserIdentityService() {
            super(null, null, null, null);
        }

        @Override
        public DisplayNameChangeResult changeDisplayName(Long userId,
                String newDisplayName,
                Long changedByUserId,
                Instant changedAt,
                Duration cooldown) {
            this.called = true;
            this.userId = userId;
            this.newDisplayName = newDisplayName;
            this.changedByUserId = changedByUserId;
            this.changedAt = changedAt;
            this.cooldown = cooldown;
            if (nextResult != null) {
                return nextResult;
            }
            User user = new User();
            user.setId(userId);
            user.setNickName(newDisplayName);
            user.setLastDisplayNameChangeAt(changedAt);
            return DisplayNameChangeResult.changed(user, changedAt);
        }
    }

    private static final class RecordingUserAccountSettingsService extends UserAccountSettingsService {
        private Long badgeUserId;
        private Trophy badgeSlot1Trophy;
        private User nextUser;

        private RecordingUserAccountSettingsService() {
            super(null);
        }

        @Override
        public User updateBadgeSlot1(Long userId, Trophy badgeSlot1Trophy) {
            this.badgeUserId = userId;
            this.badgeSlot1Trophy = badgeSlot1Trophy;
            if (nextUser != null) {
                return nextUser;
            }
            User user = new User();
            user.setId(userId);
            user.setBadgeSlot1Trophy(badgeSlot1Trophy);
            user.setBadgeSlot2Trophy(null);
            user.setBadgeSlot3Trophy(null);
            return user;
        }
    }
}
