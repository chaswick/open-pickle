package com.w3llspring.fhpb.web.controller;

import com.w3llspring.fhpb.web.controller.match.UserMatchLogController;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.test.util.ReflectionTestUtils;

import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.MatchRowModel;
import com.w3llspring.fhpb.web.service.MatchRowModelBuilder;
import com.w3llspring.fhpb.web.service.matchentry.MatchEntryContextService;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;

@ExtendWith(MockitoExtension.class)
class UserMatchLogControllerTest {

    @Mock
    private MatchRepository matchRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private CourtNameService courtNameService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    }

    @Test
    void recentMatchesRejectsDifferentAuthenticatedUser() {
        User viewer = user(1L, "viewer");
        User owner = user(2L, "owner");
        var auth = new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());
        UserMatchLogController controller = controller();

        when(userRepo.findByPublicCode(owner.getPublicCode())).thenReturn(Optional.of(owner));
        assertThatThrownBy(() -> controller.recentMatches(owner.getPublicCode(), 0, 10, new ExtendedModelMap(), auth))
                .isInstanceOf(SecurityException.class)
                .hasMessage("User match log unavailable.");

        verify(matchRepo, never()).findParticipantMatchIdsOrderByPlayedAtDescIncludingNullified(any(User.class), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void recentMatchesAllowsProfileOwner() {
        User owner = user(2L, "owner");
        var auth = new UsernamePasswordAuthenticationToken(new CustomUserDetails(owner), null, List.of());
        UserMatchLogController controller = controller();

        when(userRepo.findByPublicCode(owner.getPublicCode())).thenReturn(Optional.of(owner));
        when(matchRepo.countParticipantMatchesIncludingNullified(owner)).thenReturn(0L);
        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.recentMatches(owner.getPublicCode(), 0, 10, model, auth);

        assertThat(view).isEqualTo("fragments/userRecentMatches");
        assertThat(model.get("memberCode")).isEqualTo(owner.getPublicCode());
        assertThat(model.get("canEditDisplayName")).isEqualTo(true);
        assertThat(model.get("recentMatches")).isEqualTo(List.of());
    }

    @Test
    void recentMatchesIncludesNullifiedMatchesInHistory() {
        User owner = user(2L, "owner");
        var auth = new UsernamePasswordAuthenticationToken(new CustomUserDetails(owner), null, List.of());
        UserMatchLogController controller = controller();

        Match nullified = new Match();
        ReflectionTestUtils.setField(nullified, "id", 11L);
        nullified.setPlayedAt(java.time.Instant.parse("2026-03-20T14:00:00Z"));
        nullified.setState(MatchState.NULLIFIED);

        when(userRepo.findByPublicCode(owner.getPublicCode())).thenReturn(Optional.of(owner));
        when(matchRepo.countParticipantMatchesIncludingNullified(owner)).thenReturn(1L);
        when(matchRepo.findParticipantMatchIdsOrderByPlayedAtDescIncludingNullified(any(User.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(11L));
        when(matchRepo.findAllByIdInWithUsers(List.of(11L))).thenReturn(List.of(nullified));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.recentMatches(owner.getPublicCode(), 0, 10, model, auth);

        assertThat(view).isEqualTo("fragments/userRecentMatches");
        @SuppressWarnings("unchecked")
        List<Match> recentMatches = (List<Match>) model.get("recentMatches");
        assertThat(recentMatches).hasSize(1);
        assertThat(recentMatches.get(0).getState()).isEqualTo(MatchState.NULLIFIED);
    }

    @Test
    void exportCsvAllowsProfileOwnerByPublicCode() {
        User owner = user(2L, "owner");
        var auth = new UsernamePasswordAuthenticationToken(new CustomUserDetails(owner), null, List.of());
        UserMatchLogController controller = controller();

        Match match = new Match();
        ReflectionTestUtils.setField(match, "id", 10L);
        match.setState(MatchState.CONFIRMED);
        match.setScoreA(11);
        match.setScoreB(7);
        match.setA1(owner);
        match.setA1Guest(false);
        match.setA2Guest(true);
        match.setB1Guest(true);
        match.setB2Guest(true);

        when(userRepo.findByPublicCode(owner.getPublicCode())).thenReturn(Optional.of(owner));
        when(matchRepo.findByParticipantOrderByPlayedAtDescIncludingNullified(owner)).thenReturn(List.of(match));

        var response = controller.exportCsv(owner.getPublicCode(), auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains(owner.getPublicCode());
        assertThat(response.getBody())
                .contains("a1,a1_code")
                .contains("owner,PB-owner");
    }

    @Test
    void exportCsvRejectsDifferentAuthenticatedUser() {
        User viewer = user(1L, "viewer");
        User owner = user(2L, "owner");
        var auth = new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());
        UserMatchLogController controller = controller();

        when(userRepo.findByPublicCode(owner.getPublicCode())).thenReturn(Optional.of(owner));
        var response = controller.exportCsv(owner.getPublicCode(), auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void exportCsvNeutralizesSpreadsheetFormulas() {
        User owner = user(2L, "owner");
        var auth = new UsernamePasswordAuthenticationToken(new CustomUserDetails(owner), null, List.of());
        UserMatchLogController controller = controller();

        Match match = new Match();
        ReflectionTestUtils.setField(match, "id", 10L);
        match.setState(MatchState.CONFIRMED);
        match.setScoreA(11);
        match.setScoreB(9);
        User formulaName = new User();
        formulaName.setNickName("@malicious");
        formulaName.setPublicCode("PB-formula");
        match.setA1(formulaName);
        match.setA1Guest(false);
        match.setA2Guest(true);
        match.setB1Guest(true);
        match.setB2Guest(true);

        when(userRepo.findByPublicCode(owner.getPublicCode())).thenReturn(Optional.of(owner));
        when(matchRepo.findByParticipantOrderByPlayedAtDescIncludingNullified(owner)).thenReturn(List.of(match));

        var response = controller.exportCsv(owner.getPublicCode(), auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("'@malicious")
                .contains("PB-formula");
    }

    private UserMatchLogController controller() {
        return new UserMatchLogController(
                matchRepo,
                userRepo,
                new MatchRowModelBuilder(null, null, null) {
                    @Override
                    public MatchRowModel buildFor(User viewer, List<Match> matches) {
                        return new MatchRowModel(java.util.Set.of(), java.util.Map.of(), java.util.Map.of(),
                                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
                    }
                },
                new MatchEntryContextService(courtNameService));
    }

    private User user(Long id, String nickName) {
        User user = new User();
        user.setId(id);
        user.setNickName(nickName);
        user.setEmail(nickName + "@test.local");
        user.setPublicCode("PB-" + nickName);
        return user;
    }

}
