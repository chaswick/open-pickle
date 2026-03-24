package com.w3llspring.fhpb.web.controller;

import com.w3llspring.fhpb.web.controller.match.MatchConfirmationController;
import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchRowModelBuilder;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.RecentDuplicateMatchWarningService;
import com.w3llspring.fhpb.web.service.ConfirmedMatchNullificationService;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;

public class MatchConfirmationControllerTest {

    private MatchConfirmationController controller;
    private MatchConfirmationService confirmationService;
    private MatchRepository matchRepo;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
        confirmationService = mock(MatchConfirmationService.class);
        matchRepo = mock(MatchRepository.class);
        controller = new MatchConfirmationController(confirmationService, matchRepo);
    }

    @Nested
    @DisplayName("Confirm endpoint")
    class ConfirmEndpoint {

        @Test
        void requires_authentication() {
            ResponseEntity<String> resp = controller.confirmMatch(1L, 1L, null);
            assertEquals(401, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("Authentication required"));
        }

        @Test
        void returns_404_when_missing() {
            User u = new User(); u.setId(5L);
            CustomUserDetails principal = new CustomUserDetails(u);
            when(matchRepo.findByIdWithUsers(10L)).thenReturn(Optional.empty());

            ResponseEntity<String> resp = controller.confirmMatch(10L, 1L, principal);

            assertEquals(404, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("Match not found"));
        }

        @Test
        void returns_success_when_confirmation_succeeds() throws Exception {
            User u = new User(); u.setId(7L);
            CustomUserDetails principal = new CustomUserDetails(u);
            Match m = new Match();
            setVersion(m, 3L);
            when(matchRepo.findByIdWithUsers(11L)).thenReturn(Optional.of(m));
            when(confirmationService.confirmMatch(11L, 7L, 3L)).thenReturn(true);

            ResponseEntity<String> resp = controller.confirmMatch(11L, 3L, principal);

            assertEquals(200, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("\"success\":true"));
        }

        @Test
        void surfaces_bad_request_from_service() throws Exception {
            User u = new User(); u.setId(9L);
            CustomUserDetails principal = new CustomUserDetails(u);
            Match m = new Match();
            setVersion(m, 4L);
            when(matchRepo.findByIdWithUsers(12L)).thenReturn(Optional.of(m));
            when(confirmationService.confirmMatch(12L, 9L, 4L)).thenThrow(new IllegalArgumentException("invalid"));

            ResponseEntity<String> resp = controller.confirmMatch(12L, 4L, principal);

            assertEquals(400, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("invalid"));
        }

        @Test
        void surfaces_conflict_from_service() throws Exception {
            User u = new User(); u.setId(10L);
            CustomUserDetails principal = new CustomUserDetails(u);
            Match m = new Match();
            setVersion(m, 5L);
            when(matchRepo.findByIdWithUsers(13L)).thenReturn(Optional.of(m));
            when(confirmationService.confirmMatch(13L, 10L, 5L))
                    .thenThrow(new IllegalStateException("already confirmed"));

            ResponseEntity<String> resp = controller.confirmMatch(13L, 5L, principal);

            assertEquals(409, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("already confirmed"));
        }

        @Test
        void surfaces_forbidden_from_service() {
            User u = new User(); u.setId(14L);
            CustomUserDetails principal = new CustomUserDetails(u);
            Match m = new Match();
            setVersion(m, 6L);
            when(matchRepo.findByIdWithUsers(14L)).thenReturn(Optional.of(m));
            when(confirmationService.confirmMatch(14L, 14L, 6L))
                    .thenThrow(new SecurityException("Competition lockout for the rest of this season."));

            ResponseEntity<String> resp = controller.confirmMatch(14L, 6L, principal);

            assertEquals(403, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("Competition lockout"));
        }

        @Test
        void rejects_stale_expected_version_before_service() {
            User u = new User(); u.setId(15L);
            CustomUserDetails principal = new CustomUserDetails(u);
            Match m = new Match();
            setVersion(m, 7L);
            when(matchRepo.findByIdWithUsers(15L)).thenReturn(Optional.of(m));

            ResponseEntity<String> resp = controller.confirmMatch(15L, 6L, principal);

            assertEquals(409, resp.getStatusCode().value());
            verify(confirmationService, never()).confirmMatch(anyLong(), anyLong(), any());
        }

        @Test
        void recalculates_only_on_transition_to_confirmed() throws Exception {
            User u = new User(); u.setId(42L);
            CustomUserDetails principal = new CustomUserDetails(u);

            Match before = new Match();
            before.setState(MatchState.PROVISIONAL);
            setVersion(before, 7L);
            Match after = new Match();
            after.setState(MatchState.CONFIRMED);
            setVersion(after, 8L);

            when(matchRepo.findByIdWithUsers(20L)).thenReturn(Optional.of(before), Optional.of(after));
            when(confirmationService.confirmMatch(20L, 42L, 7L)).thenReturn(true);

            TrackingLadderV2Service ladderV2Service = new TrackingLadderV2Service();
            StaticMatchRowModelBuilder matchRowModelBuilder = new StaticMatchRowModelBuilder();
            FixedLadderAccessService ladderAccessService = new FixedLadderAccessService(false);
            MatchConfirmationController local = new MatchConfirmationController(
                    confirmationService,
                    matchRepo,
                    matchRowModelBuilder,
                    ladderV2Service,
                    ladderAccessService
            );

            ResponseEntity<String> resp = local.confirmMatch(20L, 7L, principal);
            assertEquals(200, resp.getStatusCode().value());
            assertSame(after, ladderV2Service.appliedMatch);
        }

        @Test
        void awardsTrophies_when_match_transitions_to_confirmed() throws Exception {
            User u = new User(); u.setId(52L);
            CustomUserDetails principal = new CustomUserDetails(u);

            Match before = new Match();
            before.setState(MatchState.PROVISIONAL);
            setVersion(before, 11L);
            Match after = new Match();
            after.setState(MatchState.CONFIRMED);
            setVersion(after, 12L);

            when(matchRepo.findByIdWithUsers(22L)).thenReturn(Optional.of(before), Optional.of(after));
            when(confirmationService.confirmMatch(22L, 52L, 11L)).thenReturn(true);

            TrackingLadderV2Service ladderV2Service = new TrackingLadderV2Service();
            StaticMatchRowModelBuilder matchRowModelBuilder = new StaticMatchRowModelBuilder();
            FixedLadderAccessService ladderAccessService = new FixedLadderAccessService(false);
            TrackingTrophyAwardService trophyAwardService = new TrackingTrophyAwardService();
            MatchConfirmationController local = new MatchConfirmationController(
                    confirmationService,
                    matchRepo,
                    matchRowModelBuilder,
                    ladderV2Service,
                    ladderAccessService
            );
            ReflectionTestUtils.setField(local, "trophyAwardService", trophyAwardService);

            ResponseEntity<String> resp = local.confirmMatch(22L, 11L, principal);

            assertEquals(200, resp.getStatusCode().value());
            assertSame(after, trophyAwardService.evaluatedMatch);
        }

        @Test
        void does_not_recalculate_when_match_was_already_confirmed() throws Exception {
            User u = new User(); u.setId(43L);
            CustomUserDetails principal = new CustomUserDetails(u);

            Match alreadyConfirmed = new Match();
            alreadyConfirmed.setState(MatchState.CONFIRMED);
            setVersion(alreadyConfirmed, 9L);

            when(matchRepo.findByIdWithUsers(21L)).thenReturn(Optional.of(alreadyConfirmed));
            when(confirmationService.confirmMatch(21L, 43L, 9L)).thenReturn(false);

            TrackingLadderV2Service ladderV2Service = new TrackingLadderV2Service();
            StaticMatchRowModelBuilder matchRowModelBuilder = new StaticMatchRowModelBuilder();
            FixedLadderAccessService ladderAccessService = new FixedLadderAccessService(false);
            MatchConfirmationController local = new MatchConfirmationController(
                    confirmationService,
                    matchRepo,
                    matchRowModelBuilder,
                    ladderV2Service,
                    ladderAccessService
            );

            ResponseEntity<String> resp = local.confirmMatch(21L, 9L, principal);
            assertEquals(200, resp.getStatusCode().value());
            assertNull(ladderV2Service.appliedMatch);
            verify(matchRepo, times(2)).findByIdWithUsers(21L);
        }

        @Test
        void surfaces_duplicate_confirm_warning_before_confirming() {
            User confirmer = new User(); confirmer.setId(25L); confirmer.setNickName("confirmer");
            User partner = new User(); partner.setId(26L); partner.setNickName("partner");
            User logger = new User(); logger.setId(27L); logger.setNickName("logger");
            CustomUserDetails principal = new CustomUserDetails(confirmer);

            LadderSeason season = new LadderSeason();
            setId(season, 81L);

            Match current = new Match();
            setId(current, 50L);
            setVersion(current, 7L);
            current.setSeason(season);
            current.setState(MatchState.PROVISIONAL);
            current.setA1(logger);
            current.setA2(partner);
            current.setB1(confirmer);
            current.setScoreA(11);
            current.setScoreB(8);
            current.setA1Guest(false);
            current.setA2Guest(false);
            current.setB1Guest(false);
            current.setB2Guest(true);

            Match duplicate = new Match();
            setId(duplicate, 90L);
            duplicate.setSeason(season);
            duplicate.setState(MatchState.CONFIRMED);
            duplicate.setLoggedBy(logger);
            duplicate.setCreatedAt(java.time.Instant.now().minusSeconds(20));
            duplicate.setA1(logger);
            duplicate.setA2(partner);
            duplicate.setB1(confirmer);
            duplicate.setScoreA(11);
            duplicate.setScoreB(8);
            duplicate.setA1Guest(false);
            duplicate.setA2Guest(false);
            duplicate.setB1Guest(false);
            duplicate.setB2Guest(true);

            MatchConfirmation confirmation = new MatchConfirmation();
            confirmation.setMatch(duplicate);
            confirmation.setPlayer(partner);
            confirmation.setTeam("A");
            confirmation.setConfirmedAt(java.time.Instant.now().minusSeconds(10));

            MatchConfirmation loggerConfirmation = new MatchConfirmation();
            loggerConfirmation.setMatch(duplicate);
            loggerConfirmation.setPlayer(logger);
            loggerConfirmation.setTeam("A");
            loggerConfirmation.setConfirmedAt(java.time.Instant.now().minusSeconds(12));

            MatchConfirmationRepository confirmationRepository = mock(MatchConfirmationRepository.class);
            RecentDuplicateMatchWarningService warningService =
                    new RecentDuplicateMatchWarningService(matchRepo, confirmationRepository, 120);
            ReflectionTestUtils.setField(controller, "recentDuplicateMatchWarningService", warningService);

            when(matchRepo.findByIdWithUsers(50L)).thenReturn(Optional.of(current));
            when(matchRepo.findByCreatedAtInRange(any(), any())).thenReturn(java.util.List.of(duplicate));
            when(confirmationRepository.findByMatchIdIn(java.util.List.of(90L)))
                    .thenReturn(java.util.List.of(loggerConfirmation, confirmation));

            ResponseEntity<String> resp = controller.confirmMatch(50L, 7L, principal);

            assertEquals(409, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("\"warningCode\":\"duplicateConfirmedMatch\""));
            assertTrue(body.contains("\"duplicateWarningMatchId\":90"));
            assertTrue(body.contains("logger already logged this match and partner confirmed it."));
            assertFalse(body.contains("logger already logged this match and logger and partner confirmed it."));
            assertTrue(body.contains("Are you sure you want to confirm this one?"));
            verify(confirmationService, never()).confirmMatch(anyLong(), anyLong(), any());
        }

        @Test
        void allows_acknowledged_duplicate_confirm_warning_to_proceed() {
            User confirmer = new User(); confirmer.setId(35L); confirmer.setNickName("confirmer");
            User partner = new User(); partner.setId(36L); partner.setNickName("partner");
            User logger = new User(); logger.setId(37L); logger.setNickName("logger");
            CustomUserDetails principal = new CustomUserDetails(confirmer);

            LadderSeason season = new LadderSeason();
            setId(season, 82L);

            Match current = new Match();
            setId(current, 60L);
            setVersion(current, 8L);
            current.setSeason(season);
            current.setState(MatchState.PROVISIONAL);
            current.setA1(logger);
            current.setA2(partner);
            current.setB1(confirmer);
            current.setScoreA(11);
            current.setScoreB(7);
            current.setA1Guest(false);
            current.setA2Guest(false);
            current.setB1Guest(false);
            current.setB2Guest(true);

            Match duplicate = new Match();
            setId(duplicate, 91L);
            duplicate.setSeason(season);
            duplicate.setState(MatchState.CONFIRMED);
            duplicate.setLoggedBy(logger);
            duplicate.setCreatedAt(java.time.Instant.now().minusSeconds(15));
            duplicate.setA1(logger);
            duplicate.setA2(partner);
            duplicate.setB1(confirmer);
            duplicate.setScoreA(11);
            duplicate.setScoreB(7);
            duplicate.setA1Guest(false);
            duplicate.setA2Guest(false);
            duplicate.setB1Guest(false);
            duplicate.setB2Guest(true);

            MatchConfirmation confirmation = new MatchConfirmation();
            confirmation.setMatch(duplicate);
            confirmation.setPlayer(partner);
            confirmation.setTeam("A");
            confirmation.setConfirmedAt(java.time.Instant.now().minusSeconds(5));

            MatchConfirmationRepository confirmationRepository = mock(MatchConfirmationRepository.class);
            RecentDuplicateMatchWarningService warningService =
                    new RecentDuplicateMatchWarningService(matchRepo, confirmationRepository, 120);
            ReflectionTestUtils.setField(controller, "recentDuplicateMatchWarningService", warningService);

            when(matchRepo.findByIdWithUsers(60L)).thenReturn(Optional.of(current));
            when(matchRepo.findByCreatedAtInRange(any(), any())).thenReturn(java.util.List.of(duplicate));
            when(confirmationRepository.findByMatchIdIn(java.util.List.of(91L))).thenReturn(java.util.List.of(confirmation));
            when(confirmationService.confirmMatch(60L, 35L, 8L)).thenReturn(true);

            ResponseEntity<String> resp = controller.confirmMatch(60L, 8L, 91L, principal);

            assertEquals(200, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("\"success\":true"));
            verify(confirmationService).confirmMatch(60L, 35L, 8L);
        }
    }

    @Nested
    @DisplayName("Dispute endpoint")
    class DisputeEndpoint {

        @Test
        void requires_authentication() {
            ResponseEntity<String> resp = controller.disputeMatch(30L, null, 1L, null);
            assertEquals(401, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("Authentication required"));
        }

        @Test
        void returns_success_when_dispute_succeeds() {
            User u = new User(); u.setId(44L);
            CustomUserDetails principal = new CustomUserDetails(u);
            Match m = new Match();
            setVersion(m, 10L);
            when(matchRepo.findByIdWithUsers(31L)).thenReturn(Optional.of(m));
            doNothing().when(confirmationService).disputeMatch(31L, 44L, null, 10L);

            ResponseEntity<String> resp = controller.disputeMatch(31L, null, 10L, principal);

            assertEquals(200, resp.getStatusCode().value());
            verify(confirmationService).disputeMatch(31L, 44L, null, 10L);
        }

        @Test
        void surfaces_conflict_from_service() {
            User u = new User(); u.setId(45L);
            CustomUserDetails principal = new CustomUserDetails(u);
            Match m = new Match();
            setVersion(m, 11L);
            when(matchRepo.findByIdWithUsers(32L)).thenReturn(Optional.of(m));
            doThrow(new IllegalStateException("under review")).when(confirmationService).disputeMatch(32L, 45L, null, 11L);

            ResponseEntity<String> resp = controller.disputeMatch(32L, null, 11L, principal);

            assertEquals(409, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("under review"));
        }

        @Test
        void surfaces_forbidden_from_service() {
            User u = new User(); u.setId(46L);
            CustomUserDetails principal = new CustomUserDetails(u);
            Match m = new Match();
            setVersion(m, 12L);
            when(matchRepo.findByIdWithUsers(33L)).thenReturn(Optional.of(m));
            doThrow(new SecurityException("Competition lockout for the rest of this season."))
                    .when(confirmationService).disputeMatch(33L, 46L, null, 12L);

            ResponseEntity<String> resp = controller.disputeMatch(33L, null, 12L, principal);

            assertEquals(403, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("Competition lockout"));
        }

        @Test
        void rejects_missing_expected_version_before_service() {
            User u = new User(); u.setId(47L);
            CustomUserDetails principal = new CustomUserDetails(u);
            Match m = new Match();
            setVersion(m, 13L);
            when(matchRepo.findByIdWithUsers(34L)).thenReturn(Optional.of(m));

            ResponseEntity<String> resp = controller.disputeMatch(34L, null, null, principal);

            assertEquals(409, resp.getStatusCode().value());
            verify(confirmationService, never()).disputeMatch(anyLong(), anyLong(), any(), any());
        }
    }

    @Nested
    @DisplayName("Confirmed nullify endpoint")
    class ConfirmedNullifyEndpoint {

        @Test
        void requires_authentication() {
            ResponseEntity<String> resp = controller.requestConfirmedNullification(70L, 1L, null);
            assertEquals(401, resp.getStatusCode().value());
            assertTrue(java.util.Objects.requireNonNull(resp.getBody()).contains("Authentication required"));
        }

        @Test
        void returns_success_when_request_succeeds() {
            User u = new User(); u.setId(71L);
            CustomUserDetails principal = new CustomUserDetails(u);
            Match m = new Match();
            setVersion(m, 21L);
            when(matchRepo.findByIdWithUsers(71L)).thenReturn(Optional.of(m));

            ConfirmedMatchNullificationService nullificationService = mock(ConfirmedMatchNullificationService.class);
            ReflectionTestUtils.setField(controller, "confirmedMatchNullificationService", nullificationService);
            when(nullificationService.requestNullification(71L, 71L, 21L))
                    .thenReturn(new ConfirmedMatchNullificationService.NullificationRequestResult(false, "Removal requested."));

            ResponseEntity<String> resp = controller.requestConfirmedNullification(71L, 21L, principal);

            assertEquals(200, resp.getStatusCode().value());
            String body = java.util.Objects.requireNonNull(resp.getBody());
            assertTrue(body.contains("\"success\":true"));
            assertTrue(body.contains("Removal requested."));
        }

        @Test
        void surfaces_conflict_from_service() {
            User u = new User(); u.setId(72L);
            CustomUserDetails principal = new CustomUserDetails(u);
            Match m = new Match();
            setVersion(m, 22L);
            when(matchRepo.findByIdWithUsers(72L)).thenReturn(Optional.of(m));

            ConfirmedMatchNullificationService nullificationService = mock(ConfirmedMatchNullificationService.class);
            ReflectionTestUtils.setField(controller, "confirmedMatchNullificationService", nullificationService);
            when(nullificationService.requestNullification(72L, 72L, 22L))
                    .thenThrow(new IllegalStateException("Your team already requested removal for this match."));

            ResponseEntity<String> resp = controller.requestConfirmedNullification(72L, 22L, principal);

            assertEquals(409, resp.getStatusCode().value());
            assertTrue(java.util.Objects.requireNonNull(resp.getBody()).contains("already requested removal"));
        }
    }

    @Nested
    @DisplayName("Match fragment access")
    class MatchFragmentAccess {

        @Test
        void rejects_nonparticipant_nonadmin() {
            User viewer = new User();
            viewer.setId(30L);
            CustomUserDetails principal = new CustomUserDetails(viewer);

            Match match = new Match();
            LadderSeason season = new LadderSeason();
            setId(season, 55L);
            match.setSeason(season);

            StaticMatchRowModelBuilder matchRowModelBuilder = new StaticMatchRowModelBuilder();
            FixedLadderAccessService ladderAccessService = new FixedLadderAccessService(false);
            MatchConfirmationController local = new MatchConfirmationController(
                    confirmationService,
                    matchRepo,
                    matchRowModelBuilder,
                    null,
                    ladderAccessService
            );

            when(matchRepo.findByIdWithUsers(99L)).thenReturn(Optional.of(match));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> local.matchFragment(99L, new ExtendedModelMap(), principal));

            assertEquals(403, ex.getStatusCode().value());
            assertFalse(matchRowModelBuilder.called);
        }

        @Test
        void allows_participant() {
            User viewer = new User();
            viewer.setId(31L);
            CustomUserDetails principal = new CustomUserDetails(viewer);

            Match match = new Match();
            setId(match, 100L);
            match.setA1(viewer);
            LadderSeason season = new LadderSeason();
            setId(season, 56L);
            match.setSeason(season);

            StaticMatchRowModelBuilder matchRowModelBuilder = new StaticMatchRowModelBuilder();
            MatchConfirmationController local = new MatchConfirmationController(
                    confirmationService,
                    matchRepo,
                    matchRowModelBuilder,
                    null,
                    new FixedLadderAccessService(false)
            );

            when(matchRepo.findByIdWithUsers(100L)).thenReturn(Optional.of(match));

            String view = local.matchFragment(100L, new ExtendedModelMap(), principal);

            assertEquals("fragments/matchRow :: matchRow", view);
            assertTrue(matchRowModelBuilder.called);
            assertSame(viewer, matchRowModelBuilder.lastViewer);
            assertEquals(1, matchRowModelBuilder.lastMatches.size());
            assertSame(match, matchRowModelBuilder.lastMatches.get(0));
        }
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

    private static void setVersion(Match match, long version) {
        ReflectionTestUtils.setField(match, "version", version);
    }

    private static final class TrackingLadderV2Service extends LadderV2Service {
        private Match appliedMatch;

        private TrackingLadderV2Service() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public void applyMatch(Match match) {
            appliedMatch = match;
        }
    }

    private static final class TrackingTrophyAwardService extends TrophyAwardService {
        private Match evaluatedMatch;

        private TrackingTrophyAwardService() {
            super(null, null, null, null, null, null, null, new com.w3llspring.fhpb.web.service.trophy.TrophyArtRealizer());
        }

        @Override
        public void evaluateMatch(Match match) {
            evaluatedMatch = match;
        }
    }

    private static final class StaticMatchRowModelBuilder extends MatchRowModelBuilder {
        private boolean called;
        private User lastViewer;
        private java.util.List<Match> lastMatches = java.util.List.of();

        private StaticMatchRowModelBuilder() {
            super(null, null, null);
        }

        @Override
        public com.w3llspring.fhpb.web.service.MatchRowModel buildFor(User viewer, java.util.List<Match> matches) {
            called = true;
            lastViewer = viewer;
            lastMatches = matches;
            return new com.w3llspring.fhpb.web.service.MatchRowModel(
                    java.util.Set.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of());
        }
    }

    private static final class FixedLadderAccessService extends LadderAccessService {
        private final boolean seasonAdmin;

        private FixedLadderAccessService(boolean seasonAdmin) {
            super(null, null);
            this.seasonAdmin = seasonAdmin;
        }

        @Override
        public boolean isSeasonAdmin(Long seasonId, User user) {
            return seasonAdmin;
        }
    }
}
