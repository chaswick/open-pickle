package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.LadderMatchLinkRepository;
import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchNullificationRequestRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.LadderMatchLink;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchNullificationRequest;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchDashboardServiceTest {

    private LadderMatchLinkRepository linkRepo;
    private MatchRepository matchRepo;
    private MatchConfirmationRepository confirmationRepository;
    private MatchNullificationRequestRepository nullificationRequestRepository;
    private MatchConfirmationService confirmationService;
    private AtomicReference<List<Long>> capturedMatchIds;
    private AtomicReference<Set<Long>> capturedConfirmableIds;

    @BeforeEach
    void setUp() {
        linkRepo = mock(LadderMatchLinkRepository.class);
        matchRepo = mock(MatchRepository.class);
        confirmationRepository = mock(MatchConfirmationRepository.class);
        nullificationRequestRepository = mock(MatchNullificationRequestRepository.class);
        confirmationService = mock(MatchConfirmationService.class);
        capturedMatchIds = new AtomicReference<>(List.of());
        capturedConfirmableIds = new AtomicReference<>(Set.of());
    }

    @Test
    void buildPendingForUser_ordersConfirmableMatchesBeforeWaitingOnOpponents() {
        User viewer = user(7L, "Viewer");
        User opponent = user(8L, "Opponent");

        Match confirmable = match(100L, viewer, opponent, Instant.parse("2026-03-16T18:00:00Z"));
        Match waiting = match(200L, viewer, opponent, Instant.parse("2026-03-16T19:00:00Z"));

        MatchConfirmation pending = new MatchConfirmation();
        pending.setMatch(confirmable);
        pending.setPlayer(viewer);
        pending.setTeam("A");

        MatchConfirmation viewerConfirmedWaiting = confirmed(waiting, viewer, "A");

        when(confirmationService.pendingForUser(7L)).thenReturn(List.of(pending));
        when(matchRepo.findByParticipantWithUsers(viewer)).thenReturn(List.of(confirmable, waiting));
        when(confirmationRepository.findByMatchIdIn(List.of(100L, 200L))).thenReturn(List.of(viewerConfirmedWaiting));
        when(linkRepo.findByMatchIds(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            List<LadderMatchLink> links = new ArrayList<>();
            if (ids.contains(100L)) {
                links.add(link(confirmable));
            }
            if (ids.contains(200L)) {
                links.add(link(waiting));
            }
            return links;
        });

        MatchRowModel baseModel = new MatchRowModel(
                Set.of(100L),
                Map.of(),
                Map.of(),
                Map.of(100L, true),
                Map.of(),
                Map.of(100L, false, 200L, false),
                Map.of());
        MatchDashboardService service = service(baseModel);

        MatchDashboardService.DashboardModel dashboard = service.buildPendingForUser(viewer);

        assertThat(dashboard.links()).extracting(link -> link.getMatch().getId())
                .containsExactly(100L, 200L);
        assertThat(dashboard.matchRowModel().getConfirmableMatchIds()).containsExactly(100L);
        assertThat(dashboard.matchRowModel().getWaitingOnOpponentByMatchId())
                .containsEntry(200L, true);
        assertThat(capturedMatchIds.get()).containsExactly(100L, 200L);
        assertThat(capturedConfirmableIds.get()).containsExactly(100L);
    }

    @Test
    void buildPendingForUser_returnsWaitingMatchesEvenWhenNothingNeedsMyConfirmation() {
        User viewer = user(7L, "Viewer");
        User opponent = user(8L, "Opponent");

        Match waiting = match(300L, viewer, opponent, Instant.parse("2026-03-16T19:00:00Z"));
        MatchConfirmation viewerConfirmedWaiting = confirmed(waiting, viewer, "A");

        when(confirmationService.pendingForUser(7L)).thenReturn(List.of());
        when(matchRepo.findByParticipantWithUsers(viewer)).thenReturn(List.of(waiting));
        when(confirmationRepository.findByMatchIdIn(List.of(300L))).thenReturn(List.of(viewerConfirmedWaiting));
        when(linkRepo.findByMatchIds(anyList())).thenReturn(List.of(link(waiting)));

        MatchRowModel baseModel = new MatchRowModel(
                Set.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(300L, false),
                Map.of());
        MatchDashboardService service = service(baseModel);

        MatchDashboardService.DashboardModel dashboard = service.buildPendingForUser(viewer);

        assertThat(dashboard.links()).extracting(link -> link.getMatch().getId())
                .containsExactly(300L);
        assertThat(dashboard.matchRowModel().getWaitingOnOpponentByMatchId())
                .containsEntry(300L, true);
    }

    @Test
    void buildPendingForUser_ignoresOrphanedConfirmationRowsWhenDetectingWaitingState() {
        User viewer = user(7L, "Viewer");
        User opponent = user(8L, "Opponent");
        User removedPlayer = user(99L, "Removed");

        Match match = match(400L, viewer, opponent, Instant.parse("2026-03-16T19:00:00Z"));
        MatchConfirmation orphaned = confirmed(match, removedPlayer, "A");

        when(confirmationService.pendingForUser(7L)).thenReturn(List.of());
        when(matchRepo.findByParticipantWithUsers(viewer)).thenReturn(List.of(match));
        when(confirmationRepository.findByMatchIdIn(List.of(400L))).thenReturn(List.of(orphaned));

        MatchRowModel baseModel = new MatchRowModel(
                Set.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(400L, false),
                Map.of());
        MatchDashboardService service = service(baseModel);

        MatchDashboardService.DashboardModel dashboard = service.buildPendingForUser(viewer);

        assertThat(dashboard.links()).isEmpty();
        assertThat(dashboard.matchRowModel().getWaitingOnOpponentByMatchId()).isEmpty();
    }

    @Test
    void buildPendingForUser_includes_confirmed_match_when_waiting_on_bilateral_removal() {
        User viewer = user(7L, "Viewer");
        User opponent = user(8L, "Opponent");

        Match confirmed = match(500L, viewer, opponent, Instant.parse("2026-03-16T19:00:00Z"));
        confirmed.setState(MatchState.CONFIRMED);

        MatchNullificationRequest request = new MatchNullificationRequest();
        request.setMatch(confirmed);
        request.setPlayer(opponent);
        request.setTeam("B");
        request.setRequestedAt(Instant.parse("2026-03-16T20:00:00Z"));
        request.setExpiresAt(Instant.parse("2026-03-18T20:00:00Z"));

        when(confirmationService.pendingForUser(7L)).thenReturn(List.of());
        when(matchRepo.findByParticipantWithUsers(viewer)).thenReturn(List.of(confirmed));
        when(confirmationRepository.findByMatchIdIn(List.of(500L))).thenReturn(List.of());
        when(nullificationRequestRepository.findActiveByMatchIdIn(anyList(), any()))
                .thenReturn(List.of(request));
        when(linkRepo.findByMatchIds(anyList())).thenReturn(List.of(link(confirmed)));

        MatchRowModel baseModel = new MatchRowModel(
                Set.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(500L, false),
                Map.of(),
                Map.of(),
                Map.of(500L, true),
                Map.of());
        MatchDashboardService service = service(baseModel);

        MatchDashboardService.DashboardModel dashboard = service.buildPendingForUser(viewer);

        assertThat(dashboard.links()).extracting(link -> link.getMatch().getId()).containsExactly(500L);
    }

    private MatchDashboardService service(MatchRowModel baseModel) {
        MatchRowModelBuilder builder = new MatchRowModelBuilder(null, null, null) {
            @Override
            public MatchRowModel buildFor(User viewer, List<Match> matches, Set<Long> precomputedPendingMatchIds) {
                capturedMatchIds.set(matches.stream().map(Match::getId).toList());
                capturedConfirmableIds.set(new LinkedHashSet<>(precomputedPendingMatchIds));
                return baseModel;
            }
        };
        return new MatchDashboardService(
                linkRepo,
                matchRepo,
                confirmationRepository,
                nullificationRequestRepository,
                confirmationService,
                builder);
    }

    private Match match(Long id, User viewer, User opponent, Instant playedAt) {
        Match match = new Match();
        ReflectionTestUtils.setField(match, "id", id);
        match.setState(MatchState.PROVISIONAL);
        match.setA1(viewer);
        match.setB1(opponent);
        match.setPlayedAt(playedAt);
        return match;
    }

    private MatchConfirmation confirmed(Match match, User player, String team) {
        MatchConfirmation confirmation = new MatchConfirmation();
        confirmation.setMatch(match);
        confirmation.setPlayer(player);
        confirmation.setTeam(team);
        confirmation.setConfirmedAt(Instant.parse("2026-03-16T20:00:00Z"));
        return confirmation;
    }

    private LadderMatchLink link(Match match) {
        LadderMatchLink link = new LadderMatchLink();
        link.setMatch(match);
        return link;
    }

    private User user(Long id, String nickName) {
        User user = new User();
        user.setId(id);
        user.setNickName(nickName);
        return user;
    }
}
