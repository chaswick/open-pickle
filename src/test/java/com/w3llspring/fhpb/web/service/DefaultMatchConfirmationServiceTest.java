package com.w3llspring.fhpb.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.RoundRobinEntryRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DefaultMatchConfirmationServiceTest {

  private MatchConfirmationRepository confirmRepo;
  private MatchRepository matchRepo;
  private RoundRobinEntryRepository roundRobinEntryRepository;
  private UserRepository userRepo;
  private LadderAccessService ladderAccessService;
  private MatchValidationService matchValidationService;
  private DefaultMatchConfirmationService svc;

  private final Map<Long, Match> matchesById = new LinkedHashMap<>();
  private final Map<Long, User> usersById = new LinkedHashMap<>();
  private final List<MatchConfirmation> storedConfirmations = new ArrayList<>();
  private long nextConfirmationId = 1L;

  @BeforeEach
  void setUp() {
    confirmRepo = mock(MatchConfirmationRepository.class);
    matchRepo = mock(MatchRepository.class);
    roundRobinEntryRepository = mock(RoundRobinEntryRepository.class);
    userRepo = mock(UserRepository.class);
    ladderAccessService =
        new LadderAccessService(null, null) {
          @Override
          public boolean isSeasonAdmin(Long seasonId, User user) {
            return false;
          }
        };
    matchValidationService = new MatchValidationService(null, null);

    configureRepositoryAnswers();

    svc =
        new DefaultMatchConfirmationService(
            confirmRepo,
            matchRepo,
            roundRobinEntryRepository,
            userRepo,
            ladderAccessService,
            matchValidationService,
            null);
  }

  @Nested
  @DisplayName("Confirmation request creation")
  class ConfirmationRequestCreation {

    @Test
    void standard_logging_auto_confirms_logger_team_and_drops_same_team_teammate_pending() {
      User logger = saveUser(10L, "Logger");
      User teammate = saveUser(11L, "Teammate");
      User b1 = saveUser(20L, "Opp1");
      User b2 = saveUser(21L, "Opp2");

      Match match = saveMatch(100L, standardMatch(logger, teammate, b1, b2, logger));

      svc.createRequests(match);

      List<MatchConfirmation> confirmations = confirmationsFor(match);
      assertEquals(3, confirmations.size());
      assertConfirmation(
          confirmations, logger, "A", true, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertNoConfirmation(confirmations, teammate);
      assertConfirmation(
          confirmations, b1, "B", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertConfirmation(
          confirmations, b2, "B", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertEquals(MatchState.PROVISIONAL, match.getState());
    }

    @Test
    void nonparticipant_session_admin_logging_requires_both_teams_to_confirm() {
      User organizer = saveUser(9L, "Organizer");
      User a1 = saveUser(10L, "A1");
      User a2 = saveUser(11L, "A2");
      User b1 = saveUser(20L, "B1");
      User b2 = saveUser(21L, "B2");

      Match match = saveMatch(150L, standardMatch(a1, a2, b1, b2, organizer));

      svc.createRequests(match);

      List<MatchConfirmation> confirmations = confirmationsFor(match);
      assertEquals(4, confirmations.size());
      assertNoConfirmation(confirmations, organizer);
      assertConfirmation(
          confirmations, a1, "A", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertConfirmation(
          confirmations, a2, "A", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertConfirmation(
          confirmations, b1, "B", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertConfirmation(
          confirmations, b2, "B", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertEquals(MatchState.PROVISIONAL, match.getState());
    }

    @Test
    void participant_edit_keeps_editor_team_attested_and_requires_opponents_to_review() {
      User logger = saveUser(10L, "Logger");
      User teammate = saveUser(11L, "Teammate");
      User b1 = saveUser(20L, "Opp1");
      User b2 = saveUser(21L, "Opp2");

      Match match = standardMatch(logger, teammate, b1, b2, logger);
      match.setEditedBy(b1);
      match.setEditedAt(Instant.now());
      match.setUserCorrected(true);
      saveMatch(101L, match);

      svc.createRequests(match);

      List<MatchConfirmation> confirmations = confirmationsFor(match);
      assertEquals(3, confirmations.size());
      assertConfirmation(
          confirmations, logger, "A", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertConfirmation(
          confirmations, teammate, "A", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertConfirmation(
          confirmations, b1, "B", true, MatchConfirmation.ConfirmationMethod.MANUAL, false);
      assertNoConfirmation(confirmations, b2);
      assertEquals(MatchState.PROVISIONAL, match.getState());
    }

    @Test
    void nonparticipant_edit_requires_fresh_pending_rows_for_both_teams() {
      User logger = saveUser(10L, "Logger");
      User teammate = saveUser(11L, "Teammate");
      User b1 = saveUser(20L, "Opp1");
      User b2 = saveUser(21L, "Opp2");
      User organizer = saveUser(99L, "Organizer");

      Match match = standardMatch(logger, teammate, b1, b2, logger);
      match.setEditedBy(organizer);
      match.setEditedAt(Instant.now());
      match.setUserCorrected(true);
      saveMatch(102L, match);

      svc.createRequests(match);

      List<MatchConfirmation> confirmations = confirmationsFor(match);
      assertEquals(4, confirmations.size());
      assertConfirmation(
          confirmations, logger, "A", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertConfirmation(
          confirmations, teammate, "A", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertConfirmation(
          confirmations, b1, "B", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertConfirmation(
          confirmations, b2, "B", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertEquals(MatchState.PROVISIONAL, match.getState());
    }

    @Test
    void self_confirm_mode_auto_confirms_one_representative_per_team_and_marks_match_confirmed() {
      User logger = saveUser(10L, "Logger");
      User teammate = saveUser(11L, "Teammate");
      User b1 = saveUser(20L, "Opp1");
      User b2 = saveUser(21L, "Opp2");

      Match match = standardMatch(logger, teammate, b1, b2, logger);
      match.setSeason(selfConfirmSeason());
      saveMatch(102L, match);

      svc.createRequests(match);

      List<MatchConfirmation> confirmations = confirmationsFor(match);
      assertEquals(2, confirmations.size());
      assertConfirmation(
          confirmations, logger, "A", true, MatchConfirmation.ConfirmationMethod.AUTO, true);
      assertConfirmation(
          confirmations, b1, "B", true, MatchConfirmation.ConfirmationMethod.AUTO, true);
      assertNoConfirmation(confirmations, teammate);
      assertNoConfirmation(confirmations, b2);
      assertEquals(MatchState.CONFIRMED, match.getState());
    }
  }

  @Nested
  @DisplayName("Confirmation flow")
  class ConfirmationFlow {

    @Test
    void match_requires_one_confirmed_player_per_team_before_confirming() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");

      Match match = saveMatch(200L, standardMatch(a1, null, b1, null, admin));
      svc.createRequests(match);

      assertTrue(svc.confirmMatch(200L, 10L));
      assertEquals(MatchState.PROVISIONAL, match.getState());
      assertConfirmation(
          confirmationsFor(match),
          a1,
          "A",
          true,
          MatchConfirmation.ConfirmationMethod.MANUAL,
          false);
      assertConfirmation(
          confirmationsFor(match),
          b1,
          "B",
          false,
          MatchConfirmation.ConfirmationMethod.AUTO,
          false);

      assertTrue(svc.confirmMatch(200L, 20L));
      assertEquals(MatchState.CONFIRMED, match.getState());
      assertConfirmation(
          confirmationsFor(match),
          b1,
          "B",
          true,
          MatchConfirmation.ConfirmationMethod.MANUAL,
          false);
    }

    @Test
    void confirming_player_replaces_same_team_pending_teammate() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User a2 = saveUser(11L, "Amy");
      User b1 = saveUser(20L, "Bob");

      Match match = saveMatch(201L, standardMatch(a1, a2, b1, null, admin));
      svc.createRequests(match);

      assertTrue(svc.confirmMatch(201L, 11L));

      List<MatchConfirmation> confirmations = confirmationsFor(match);
      assertNoConfirmation(confirmations, a1);
      assertConfirmation(
          confirmations, a2, "A", true, MatchConfirmation.ConfirmationMethod.MANUAL, false);
      assertConfirmation(
          confirmations, b1, "B", false, MatchConfirmation.ConfirmationMethod.AUTO, false);
      assertTrue(svc.pendingForUser(10L).isEmpty());
    }

    @Test
    void stale_click_after_teammate_confirmation_is_rejected() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User a2 = saveUser(11L, "Amy");
      User b1 = saveUser(20L, "Bob");

      Match match = saveMatch(202L, standardMatch(a1, a2, b1, null, admin));
      svc.createRequests(match);
      svc.confirmMatch(202L, 11L);

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> svc.confirmMatch(202L, 10L));
      assertTrue(ex.getMessage().contains("already confirmed"));
    }

    @Test
    void pending_for_user_synthesizes_entry_when_confirmation_rows_are_missing() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");

      Match match = saveMatch(203L, standardMatch(a1, null, b1, null, admin));

      List<MatchConfirmation> pending = svc.pendingForUser(10L);

      assertEquals(1, pending.size());
      assertEquals(match.getId(), pending.get(0).getMatch().getId());
      assertEquals("A", pending.get(0).getTeam());
      assertNull(pending.get(0).getConfirmedAt());
    }
  }

  @Nested
  @DisplayName("Dispute flow")
  class DisputeFlow {

    @Test
    void dispute_flags_pending_match_and_removes_it_from_pending_queues() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");

      Match match = saveMatch(204L, standardMatch(a1, null, b1, null, admin));
      svc.createRequests(match);

      svc.disputeMatch(204L, 20L, "Did not play this match");

      assertEquals(MatchState.FLAGGED, match.getState());
      assertEquals(b1.getId(), match.getDisputedBy().getId());
      assertNotNull(match.getDisputedAt());
      assertEquals("Did not play this match", match.getDisputeNote());
      assertTrue(svc.pendingForUser(10L).isEmpty());
      assertTrue(svc.pendingForUser(20L).isEmpty());
      assertFalse(confirmationsFor(match).isEmpty());
    }

    @Test
    void dispute_is_rejected_after_team_has_already_confirmed() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");

      Match match = saveMatch(205L, standardMatch(a1, null, b1, null, admin));
      svc.createRequests(match);
      svc.confirmMatch(205L, 10L);

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> svc.disputeMatch(205L, 10L, null));

      assertTrue(ex.getMessage().contains("team has already confirmed"));
      assertEquals(MatchState.PROVISIONAL, match.getState());
    }
  }

  @Nested
  @DisplayName("Overdue maintenance")
  class OverdueMaintenance {

    @Test
    void auto_confirm_overdue_nullifies_matches_still_missing_one_team_confirmation() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");

      Match match = standardMatch(a1, null, b1, null, admin);
      match.setCreatedAt(Instant.now().minus(Duration.ofHours(72)));
      saveMatch(300L, match);
      svc.createRequests(match);
      svc.confirmMatch(300L, 10L);

      svc.autoConfirmOverdue();

      assertEquals(MatchState.NULLIFIED, match.getState());
      assertEquals(2, confirmationsFor(match).size());
      assertConfirmation(
          confirmationsFor(match),
          a1,
          "A",
          true,
          MatchConfirmation.ConfirmationMethod.MANUAL,
          false);
      assertConfirmation(
          confirmationsFor(match),
          b1,
          "B",
          false,
          MatchConfirmation.ConfirmationMethod.AUTO,
          false);
    }

    @Test
    void auto_confirm_overdue_leaves_disputed_matches_flagged() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");

      Match match = standardMatch(a1, null, b1, null, admin);
      match.setCreatedAt(Instant.now().minus(Duration.ofHours(72)));
      saveMatch(302L, match);
      svc.createRequests(match);
      svc.disputeMatch(302L, 20L, null);

      svc.autoConfirmOverdue();

      assertEquals(MatchState.FLAGGED, match.getState());
      assertEquals(2, confirmationsFor(match).size());
    }

    @Test
    void auto_confirm_overdue_repairs_stuck_provisional_match_when_both_teams_already_confirmed() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");

      Match match = standardMatch(a1, null, b1, null, admin);
      match.setCreatedAt(Instant.now().minus(Duration.ofHours(72)));
      saveMatch(301L, match);

      addStoredConfirmation(
          match,
          a1,
          "A",
          Instant.now().minus(Duration.ofHours(70)),
          MatchConfirmation.ConfirmationMethod.MANUAL);
      addStoredConfirmation(
          match,
          b1,
          "B",
          Instant.now().minus(Duration.ofHours(69)),
          MatchConfirmation.ConfirmationMethod.MANUAL);

      svc.autoConfirmOverdue();

      assertEquals(MatchState.CONFIRMED, match.getState());
      assertEquals(2, confirmationsFor(match).size());
    }

    @Test
    void rebuild_confirmation_requests_does_not_accumulate_rows_or_extend_overdue_window() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");

      Match match = standardMatch(a1, null, b1, null, admin);
      match.setCreatedAt(Instant.now().minus(Duration.ofHours(72)));
      saveMatch(303L, match);
      svc.createRequests(match);

      assertEquals(2, confirmationsFor(match).size());

      svc.rebuildConfirmationRequests(match);
      svc.rebuildConfirmationRequests(match);

      assertEquals(2, confirmationsFor(match).size());

      svc.autoConfirmOverdue();

      assertEquals(MatchState.NULLIFIED, match.getState());
      assertEquals(2, confirmationsFor(match).size());
    }
  }

  @Nested
  @DisplayName("Validation and competition safeguards")
  class ValidationAndCompetitionSafeguards {

    @Test
    void confirm_rejects_invalid_scores_before_writing_confirmation() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");

      Match match = standardMatch(a1, null, b1, null, admin);
      match.setScoreA(36);
      match.setScoreB(34);
      saveMatch(400L, match);

      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> svc.confirmMatch(400L, 10L));

      assertTrue(ex.getMessage().contains("cannot exceed 35 points"));
      assertTrue(confirmationsFor(match).isEmpty());
    }

    @Test
    void confirm_rejects_blocked_competition_player() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");
      Match match = standardMatch(a1, null, b1, null, admin);
      match.setSeason(competitionSeason());
      saveMatch(401L, match);
      svc.createRequests(match);

      ReflectionTestUtils.setField(
          svc,
          "competitionAutoModerationService",
          blockingAutoModerationService(a1, match.getSeason()));

      SecurityException ex =
          assertThrows(SecurityException.class, () -> svc.confirmMatch(401L, 10L));

      assertTrue(ex.getMessage().contains("rest of this season"));
    }

    @Test
    void dispute_rejects_blocked_competition_player() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");
      Match match = standardMatch(a1, null, b1, null, admin);
      match.setSeason(competitionSeason());
      saveMatch(402L, match);
      svc.createRequests(match);

      ReflectionTestUtils.setField(
          svc,
          "competitionAutoModerationService",
          blockingAutoModerationService(b1, match.getSeason()));

      SecurityException ex =
          assertThrows(SecurityException.class, () -> svc.disputeMatch(402L, 20L, null));

      assertTrue(ex.getMessage().contains("rest of this season"));
    }

    @Test
    void pending_queue_hides_competition_matches_for_blocked_players() {
      User admin = saveUser(1L, "Admin");
      User a1 = saveUser(10L, "Alice");
      User b1 = saveUser(20L, "Bob");
      Match match = standardMatch(a1, null, b1, null, admin);
      match.setSeason(competitionSeason());
      saveMatch(403L, match);
      svc.createRequests(match);

      ReflectionTestUtils.setField(
          svc,
          "competitionAutoModerationService",
          blockedStatusAutoModerationService(a1, match.getSeason()));

      assertTrue(svc.pendingForUser(10L).isEmpty());
    }
  }

  private void configureRepositoryAnswers() {
    when(userRepo.findById(anyLong()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(usersById.get(invocation.getArgument(0, Long.class))));

    when(matchRepo.findByIdWithUsersForUpdate(anyLong()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(matchesById.get(invocation.getArgument(0, Long.class))));
    when(matchRepo.findByIdWithUsers(anyLong()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(matchesById.get(invocation.getArgument(0, Long.class))));
    when(matchRepo.save(any(Match.class)))
        .thenAnswer(
            invocation -> {
              Match match = invocation.getArgument(0);
              if (match.getId() == null) {
                ReflectionTestUtils.setField(match, "id", (long) (matchesById.size() + 1));
              }
              matchesById.put(match.getId(), match);
              return match;
            });
    when(matchRepo.findByParticipantWithUsers(any(User.class)))
        .thenAnswer(
            invocation -> {
              User user = invocation.getArgument(0);
              return matchesById.values().stream()
                  .filter(match -> match.getState() != MatchState.NULLIFIED)
                  .filter(match -> isParticipant(match, user))
                  .collect(Collectors.toList());
            });
    when(matchRepo.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            any(Instant.class), any(Instant.class)))
        .thenAnswer(invocation -> new ArrayList<>(matchesById.values()));
    when(matchRepo.findPendingConfirmationCandidateIdsCreatedBefore(any(Instant.class)))
        .thenAnswer(
            invocation -> {
              Instant cutoff = invocation.getArgument(0);
              return matchesById.values().stream()
                  .filter(
                      match ->
                          match.getCreatedAt() != null && !match.getCreatedAt().isAfter(cutoff))
                  .filter(match -> match.getState() != MatchState.CONFIRMED)
                  .filter(match -> match.getState() != MatchState.NULLIFIED)
                  .map(Match::getId)
                  .collect(Collectors.toList());
            });

    when(confirmRepo.findByMatch(any(Match.class)))
        .thenAnswer(invocation -> confirmationsFor(invocation.getArgument(0)));
    when(confirmRepo.findByMatchAndPlayer(any(Match.class), any(User.class)))
        .thenAnswer(
            invocation -> {
              Match match = invocation.getArgument(0);
              User user = invocation.getArgument(1);
              return confirmationsFor(match).stream()
                  .filter(mc -> mc.getPlayer() != null && user != null && user.getId() != null)
                  .filter(mc -> user.getId().equals(mc.getPlayer().getId()))
                  .findFirst();
            });
    when(confirmRepo.findByMatchIdIn(anyCollection()))
        .thenAnswer(
            invocation -> {
              Collection<Long> ids = invocation.getArgument(0);
              return storedConfirmations.stream()
                  .filter(mc -> mc.getMatch() != null && mc.getMatch().getId() != null)
                  .filter(mc -> ids.contains(mc.getMatch().getId()))
                  .sorted(Comparator.comparing(MatchConfirmation::getId))
                  .collect(Collectors.toList());
            });
    when(confirmRepo.save(any(MatchConfirmation.class)))
        .thenAnswer(
            invocation -> {
              MatchConfirmation confirmation = invocation.getArgument(0);
              if (confirmation.getId() == null) {
                ReflectionTestUtils.setField(confirmation, "id", nextConfirmationId++);
              }
              storedConfirmations.removeIf(existing -> sameMatchAndPlayer(existing, confirmation));
              storedConfirmations.add(confirmation);
              return confirmation;
            });

    doAnswer(
            invocation -> {
              MatchConfirmation confirmation = invocation.getArgument(0);
              storedConfirmations.removeIf(
                  existing ->
                      (existing.getId() != null && existing.getId().equals(confirmation.getId()))
                          || sameMatchAndPlayer(existing, confirmation));
              return null;
            })
        .when(confirmRepo)
        .delete(any(MatchConfirmation.class));

    doAnswer(
            invocation -> {
              Match match = invocation.getArgument(0);
              storedConfirmations.removeIf(existing -> sameMatch(existing.getMatch(), match));
              return null;
            })
        .when(confirmRepo)
        .deleteByMatch(any(Match.class));

    when(confirmRepo.deleteByCreatedAtBeforeAndConfirmedAtIsNull(any(Instant.class)))
        .thenAnswer(
            invocation -> {
              Instant cutoff = invocation.getArgument(0);
              int before = storedConfirmations.size();
              storedConfirmations.removeIf(
                  existing ->
                      existing.getConfirmedAt() == null
                          && getCreatedAt(existing) != null
                          && getCreatedAt(existing).isBefore(cutoff));
              return before - storedConfirmations.size();
            });
  }

  private User saveUser(long id, String nickName) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickName);
    user.setEmail(nickName.toLowerCase() + "@test.local");
    usersById.put(id, user);
    return user;
  }

  private Match saveMatch(long id, Match match) {
    ReflectionTestUtils.setField(match, "id", id);
    matchesById.put(id, match);
    return match;
  }

  private Match standardMatch(User a1, User a2, User b1, User b2, User loggedBy) {
    Match match = new Match();
    match.setA1(a1);
    match.setA2(a2);
    match.setB1(b1);
    match.setB2(b2);
    match.setLoggedBy(loggedBy);
    match.setScoreA(11);
    match.setScoreB(7);
    match.setState(MatchState.PROVISIONAL);
    return match;
  }

  private LadderSeason selfConfirmSeason() {
    LadderConfig config = new LadderConfig();
    config.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    LadderSeason season = new LadderSeason();
    season.setLadderConfig(config);
    return season;
  }

  private LadderSeason competitionSeason() {
    LadderConfig config = new LadderConfig();
    config.setType(LadderConfig.Type.COMPETITION);
    LadderSeason season = new LadderSeason();
    season.setLadderConfig(config);
    ReflectionTestUtils.setField(season, "id", 99L);
    return season;
  }

  private CompetitionAutoModerationService blockingAutoModerationService(
      User blockedUser, LadderSeason blockedSeason) {
    return new CompetitionAutoModerationService(
        null, new CompetitionSeasonService(null, null, null), true, 1, 2, 3) {
      @Override
      public void requireNotBlocked(User user, LadderSeason season) {
        if (user == blockedUser && season == blockedSeason) {
          throw new SecurityException("Competition lockout for the rest of this season.");
        }
      }
    };
  }

  private CompetitionAutoModerationService blockedStatusAutoModerationService(
      User blockedUser, LadderSeason blockedSeason) {
    return new CompetitionAutoModerationService(
        null, new CompetitionSeasonService(null, null, null), true, 1, 2, 3) {
      @Override
      public AutoModerationStatus statusForSeason(User user, LadderSeason season) {
        if (user == blockedUser && season == blockedSeason) {
          return AutoModerationStatus.blocked(3);
        }
        return AutoModerationStatus.clear();
      }
    };
  }

  private void addStoredConfirmation(
      Match match,
      User player,
      String team,
      Instant confirmedAt,
      MatchConfirmation.ConfirmationMethod method) {
    MatchConfirmation confirmation = new MatchConfirmation();
    ReflectionTestUtils.setField(confirmation, "id", nextConfirmationId++);
    confirmation.setMatch(match);
    confirmation.setPlayer(player);
    confirmation.setTeam(team);
    confirmation.setConfirmedAt(confirmedAt);
    confirmation.setMethod(method);
    storedConfirmations.add(confirmation);
  }

  private List<MatchConfirmation> confirmationsFor(Match match) {
    return storedConfirmations.stream()
        .filter(mc -> sameMatch(mc.getMatch(), match))
        .sorted(Comparator.comparing(MatchConfirmation::getId))
        .collect(Collectors.toList());
  }

  private void assertConfirmation(
      List<MatchConfirmation> confirmations,
      User player,
      String team,
      boolean confirmed,
      MatchConfirmation.ConfirmationMethod method,
      boolean casualModeAutoConfirmed) {
    MatchConfirmation confirmation =
        confirmations.stream()
            .filter(mc -> mc.getPlayer() != null && player.getId().equals(mc.getPlayer().getId()))
            .findFirst()
            .orElse(null);
    assertNotNull(confirmation, "Expected confirmation for player " + player.getId());
    assertEquals(team, confirmation.getTeam());
    assertEquals(method, confirmation.getMethod());
    assertEquals(casualModeAutoConfirmed, confirmation.isCasualModeAutoConfirmed());
    if (confirmed) {
      assertNotNull(confirmation.getConfirmedAt());
    } else {
      assertNull(confirmation.getConfirmedAt());
    }
  }

  private void assertNoConfirmation(List<MatchConfirmation> confirmations, User player) {
    assertFalse(
        confirmations.stream()
            .anyMatch(
                mc -> mc.getPlayer() != null && player.getId().equals(mc.getPlayer().getId())));
  }

  private boolean sameMatchAndPlayer(MatchConfirmation left, MatchConfirmation right) {
    if (left == null || right == null) {
      return false;
    }
    if (!sameMatch(left.getMatch(), right.getMatch())) {
      return false;
    }
    if (left.getPlayer() == null || left.getPlayer().getId() == null) {
      return false;
    }
    return right.getPlayer() != null
        && right.getPlayer().getId() != null
        && left.getPlayer().getId().equals(right.getPlayer().getId());
  }

  private boolean sameMatch(Match left, Match right) {
    if (left == null || right == null) {
      return false;
    }
    if (left.getId() != null && right.getId() != null) {
      return left.getId().equals(right.getId());
    }
    return left == right;
  }

  private boolean isParticipant(Match match, User user) {
    if (match == null || user == null || user.getId() == null) {
      return false;
    }
    Long userId = user.getId();
    return (match.getA1() != null && userId.equals(match.getA1().getId()))
        || (match.getA2() != null && userId.equals(match.getA2().getId()))
        || (match.getB1() != null && userId.equals(match.getB1().getId()))
        || (match.getB2() != null && userId.equals(match.getB2().getId()));
  }

  private Instant getCreatedAt(MatchConfirmation confirmation) {
    Object value = ReflectionTestUtils.getField(confirmation, "createdAt");
    return value instanceof Instant instant ? instant : null;
  }
}
