package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchNullificationRequestRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchNullificationRequest;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchRowModelBuilderTest {

    private MatchConfirmationService matchConfirmationService;
    private MatchConfirmationRepository matchConfirmationRepository;
    private MatchNullificationRequestRepository matchNullificationRequestRepository;
    private LadderAccessService ladderAccessService;
    private MatchRowModelBuilder builder;
    private Set<Long> adminSeasonIds;

    @BeforeEach
    void setUp() {
        matchConfirmationService = mock(MatchConfirmationService.class);
        matchConfirmationRepository = mock(MatchConfirmationRepository.class);
        matchNullificationRequestRepository = mock(MatchNullificationRequestRepository.class);
        adminSeasonIds = new HashSet<>();
        ladderAccessService = new LadderAccessService(null, null) {
            @Override
            public boolean isSeasonAdmin(Long seasonId, User user) {
                if (seasonId == null || user == null || user.getId() == null) {
                    return false;
                }
                return adminSeasonIds.contains(seasonId);
            }
        };
        builder = new MatchRowModelBuilder(
                matchConfirmationService,
                matchConfirmationRepository,
                ladderAccessService,
                matchNullificationRequestRepository);

        when(matchConfirmationRepository.findByMatchIdIn(anyCollection())).thenReturn(List.of());
        when(matchNullificationRequestRepository.findActiveByMatchIdIn(anyCollection(), any())).thenReturn(List.of());
    }

    @Nested
    @DisplayName("Confirmation summary presentation")
    class ConfirmationSummaryPresentation {

        @Test
        void builds_confirmer_pending_and_editable_maps() {
            User viewer = new User();
            viewer.setId(1L);
            viewer.setNickName("Viewer");

            Match m1 = new Match();
            m1.setState(MatchState.CONFIRMED);
            setId(m1, 11L);

            Match m2 = new Match();
            m2.setState(MatchState.PROVISIONAL);
            m2.setA1(viewer);
            setId(m2, 22L);

            MatchConfirmation c1 = new MatchConfirmation();
            c1.setMatch(m1);
            User alice = new User();
            alice.setId(99L);
            alice.setNickName("Alice");
            c1.setPlayer(alice);
            c1.setConfirmedAt(Instant.now());
            c1.setCasualModeAutoConfirmed(true);

            when(matchConfirmationRepository.findByMatchIdIn(anyCollection())).thenReturn(List.of(c1));

            MatchConfirmation pending = new MatchConfirmation();
            pending.setMatch(m2);
            pending.setPlayer(viewer);
            when(matchConfirmationService.pendingForUser(1L)).thenReturn(List.of(pending));

            MatchRowModel model = builder.buildFor(viewer, List.of(m1, m2));

            Map<Long, String> confirmer = model.getConfirmerByMatchId();
            assertEquals("Alice", confirmer.get(11L));
            assertTrue(model.getCasualAutoConfirmedByMatchId().get(11L));

            Map<Long, Boolean> pendingBy = model.getPendingByMatchId();
            assertTrue(pendingBy.containsKey(22L));
            assertTrue(pendingBy.get(22L));

            Set<Long> confirmable = model.getConfirmableMatchIds();
            assertTrue(confirmable.contains(22L));

            Map<Long, Boolean> editable = model.getEditableByMatchId();
            assertTrue(editable.get(22L));
            assertFalse(editable.get(11L));
        }

        @Test
        void joins_confirmed_representatives_from_both_teams() {
            User viewer = new User();
            viewer.setId(2L);

            Match m = new Match();
            m.setState(MatchState.CONFIRMED);
            setId(m, 33L);

            MatchConfirmation older = new MatchConfirmation();
            older.setMatch(m);
            User bob = new User();
            bob.setId(3L);
            bob.setNickName("Bob");
            older.setPlayer(bob);
            older.setTeam("A");
            older.setConfirmedAt(Instant.now().minusSeconds(60));

            MatchConfirmation newer = new MatchConfirmation();
            newer.setMatch(m);
            User carol = new User();
            carol.setId(4L);
            carol.setNickName("Carol");
            newer.setPlayer(carol);
            newer.setTeam("B");
            newer.setConfirmedAt(Instant.now());

            when(matchConfirmationRepository.findByMatchIdIn(anyCollection())).thenReturn(List.of(older, newer));
            when(matchConfirmationService.pendingForUser(2L)).thenReturn(List.of());

            MatchRowModel model = builder.buildFor(viewer, List.of(m));
            Map<Long, String> confirmer = model.getConfirmerByMatchId();
            assertEquals("Bob and Carol", confirmer.get(33L));
            assertFalse(model.getCasualAutoConfirmedByMatchId().containsKey(33L));
        }

        @Test
        void confirmed_match_with_no_confirmations_produces_no_confirmer_entry() {
            User viewer = new User();
            viewer.setId(5L);

            Match m = new Match();
            m.setState(MatchState.CONFIRMED);
            setId(m, 44L);

            when(matchConfirmationService.pendingForUser(5L)).thenReturn(List.of());

            MatchRowModel model = builder.buildFor(viewer, List.of(m));
            Map<Long, String> confirmer = model.getConfirmerByMatchId();
            assertFalse(confirmer.containsKey(44L));
        }

        @Test
        void confirmed_match_exposes_requestable_or_approvable_nullify_state_for_participants() {
            User viewer = new User();
            viewer.setId(50L);
            viewer.setNickName("Viewer");
            User opponent = new User();
            opponent.setId(51L);
            opponent.setNickName("Opponent");

            Match requestable = new Match();
            requestable.setState(MatchState.CONFIRMED);
            requestable.setA1(viewer);
            requestable.setB1(opponent);
            setId(requestable, 60L);

            Match approvable = new Match();
            approvable.setState(MatchState.CONFIRMED);
            approvable.setA1(viewer);
            approvable.setB1(opponent);
            setId(approvable, 61L);

            MatchNullificationRequest request = new MatchNullificationRequest();
            request.setMatch(approvable);
            request.setPlayer(opponent);
            request.setTeam("B");
            request.setRequestedAt(Instant.now());
            request.setExpiresAt(Instant.now().plusSeconds(3600));

            when(matchConfirmationService.pendingForUser(50L)).thenReturn(List.of());
            when(matchNullificationRequestRepository.findActiveByMatchIdIn(anyCollection(), any()))
                    .thenReturn(List.of(request));

            MatchRowModel model = builder.buildFor(viewer, List.of(requestable, approvable));

            assertTrue(model.getNullifyRequestableByMatchId().get(60L));
            assertTrue(model.getNullifyApprovableByMatchId().get(61L));
            assertFalse(model.getNullifyWaitingOnOpponentByMatchId().containsKey(60L));
        }

        @Test
        void handles_null_or_empty_match_lists_gracefully() {
            User viewer = new User();
            viewer.setId(6L);
            when(matchConfirmationService.pendingForUser(6L)).thenReturn(List.of());

            MatchRowModel modelEmpty = builder.buildFor(viewer, List.of());
            assertTrue(modelEmpty.getConfirmableMatchIds().isEmpty());
            assertTrue(modelEmpty.getConfirmerByMatchId().isEmpty());
            assertTrue(modelEmpty.getCasualAutoConfirmedByMatchId().isEmpty());
            assertTrue(modelEmpty.getPendingByMatchId().isEmpty());
            assertTrue(modelEmpty.getEditableByMatchId().isEmpty());

            MatchRowModel modelNull = builder.buildFor(viewer, null);
            assertTrue(modelNull.getConfirmableMatchIds().isEmpty());
            assertTrue(modelNull.getConfirmerByMatchId().isEmpty());
            assertTrue(modelNull.getCasualAutoConfirmedByMatchId().isEmpty());
            assertTrue(modelNull.getPendingByMatchId().isEmpty());
            assertTrue(modelNull.getEditableByMatchId().isEmpty());
        }
    }

    @Nested
    @DisplayName("Editability rules")
    class EditabilityRules {

        @Test
        void regular_viewers_are_blocked_by_confirmation_lock_or_self_confirm_rules() {
            User viewer = new User();
            viewer.setId(7L);
            viewer.setNickName("Viewer");
            User opponent = new User();
            opponent.setId(8L);
            opponent.setNickName("Opponent");

            Match m = new Match();
            m.setState(MatchState.PROVISIONAL);
            m.setA1(viewer);
            m.setB1(opponent);
            m.setLoggedBy(viewer);
            setId(m, 55L);

            m.setConfirmationLocked(true);
            when(matchConfirmationService.pendingForUser(7L)).thenReturn(List.of());
            MatchRowModel modelLocked = builder.buildFor(viewer, List.of(m));
            assertFalse(modelLocked.getEditableByMatchId().get(55L));

            m.setConfirmationLocked(false);
            LadderConfig cfg = new LadderConfig();
            cfg.setSecurityLevel(LadderSecurity.STANDARD);
            LadderSeason season = new LadderSeason();
            season.setLadderConfig(cfg);
            m.setSeason(season);

            MatchRowModel modelSec = builder.buildFor(viewer, List.of(m));
            assertFalse(modelSec.getEditableByMatchId().get(55L));

            cfg.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
            MatchRowModel modelSelfConfirm = builder.buildFor(viewer, List.of(m));
            assertTrue(modelSelfConfirm.getEditableByMatchId().get(55L));
        }

        @Test
        void pending_opponent_can_edit_standard_match() {
            User logger = new User();
            logger.setId(9L);
            logger.setNickName("Logger");
            User viewer = new User();
            viewer.setId(10L);
            viewer.setNickName("Opponent");

            LadderConfig cfg = new LadderConfig();
            cfg.setSecurityLevel(LadderSecurity.STANDARD);
            LadderSeason season = new LadderSeason();
            season.setLadderConfig(cfg);

            Match match = new Match();
            match.setState(MatchState.PROVISIONAL);
            match.setSeason(season);
            match.setLoggedBy(logger);
            match.setA1(logger);
            match.setB1(viewer);
            setId(match, 56L);

            when(matchConfirmationService.pendingForUser(10L)).thenReturn(List.of());

            MatchRowModel model = builder.buildFor(viewer, List.of(match));

            assertTrue(model.getEditableByMatchId().get(56L));
        }

        @Test
        void private_group_admin_can_edit_confirmed_match() {
            User adminViewer = new User();
            adminViewer.setId(100L);

            LadderConfig cfg = new LadderConfig();
            cfg.setType(LadderConfig.Type.STANDARD);
            LadderSeason season = new LadderSeason();
            setId(season, 200L);
            season.setLadderConfig(cfg);

            Match confirmed = new Match();
            confirmed.setState(MatchState.CONFIRMED);
            confirmed.setSeason(season);
            setId(confirmed, 300L);

            when(matchConfirmationService.pendingForUser(100L)).thenReturn(List.of());
            adminSeasonIds.add(200L);

            MatchRowModel model = builder.buildFor(adminViewer, List.of(confirmed));
            assertTrue(model.getEditableByMatchId().get(300L));
        }

        @Test
        void non_site_admin_does_not_see_confirmed_competition_match_as_editable_even_with_admin_override_flag() {
            User sessionAdminViewer = new User();
            sessionAdminViewer.setId(101L);

            LadderConfig competitionCfg = new LadderConfig();
            competitionCfg.setType(LadderConfig.Type.COMPETITION);
            LadderSeason season = new LadderSeason();
            setId(season, 201L);
            season.setLadderConfig(competitionCfg);

            Match confirmed = new Match();
            confirmed.setState(MatchState.CONFIRMED);
            confirmed.setSeason(season);
            setId(confirmed, 301L);

            when(matchConfirmationService.pendingForUser(101L)).thenReturn(List.of());
            adminSeasonIds.add(201L);

            MatchRowModel model = builder.buildFor(sessionAdminViewer, List.of(confirmed));
            assertFalse(model.getEditableByMatchId().get(301L));
        }

        @Test
        void flagged_match_is_not_editable_for_regular_participant() {
            User viewer = new User();
            viewer.setId(110L);

            Match flagged = new Match();
            flagged.setState(MatchState.FLAGGED);
            flagged.setA1(viewer);
            setId(flagged, 310L);

            when(matchConfirmationService.pendingForUser(110L)).thenReturn(List.of());

            MatchRowModel model = builder.buildFor(viewer, List.of(flagged));

            assertFalse(model.getEditableByMatchId().get(310L));
            assertFalse(model.getDeletableByMatchId().get(310L));
        }

        @Test
        void season_admin_lookup_is_cached_per_season_within_one_build() {
            AtomicInteger adminChecks = new AtomicInteger();
            LadderAccessService countingAccessService = new LadderAccessService(null, null) {
                @Override
                public boolean isSeasonAdmin(Long seasonId, User user) {
                    adminChecks.incrementAndGet();
                    return true;
                }
            };
            MatchRowModelBuilder cachingBuilder = new MatchRowModelBuilder(
                    matchConfirmationService,
                    matchConfirmationRepository,
                    countingAccessService);

            User adminViewer = new User();
            adminViewer.setId(102L);

            LadderConfig cfg = new LadderConfig();
            cfg.setSecurityLevel(LadderSecurity.STANDARD);
            LadderSeason season = new LadderSeason();
            setId(season, 201L);
            season.setLadderConfig(cfg);

            Match first = new Match();
            first.setState(MatchState.PROVISIONAL);
            first.setSeason(season);
            setId(first, 301L);

            Match second = new Match();
            second.setState(MatchState.CONFIRMED);
            second.setSeason(season);
            setId(second, 302L);

            when(matchConfirmationService.pendingForUser(102L)).thenReturn(List.of());

            MatchRowModel model = cachingBuilder.buildFor(adminViewer, List.of(first, second));

            assertTrue(model.getEditableByMatchId().get(301L));
            assertTrue(model.getEditableByMatchId().get(302L));
            assertEquals(1, adminChecks.get());
        }
    }

    private void setId(Object obj, long id) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(obj, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
