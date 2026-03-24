package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.w3llspring.fhpb.web.db.BandPositionRepository;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMatchLinkRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.BandPosition;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.trophy.AutoTrophyService;
import com.w3llspring.fhpb.web.util.UserPublicName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for LadderV2Service.recalcSeasonStandings() method.
 *
 * <p>Tests the critical logic that removes standings for players with no confirmed matches,
 * especially the edge case where a player has 0 points but still has confirmed matches.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LadderV2ServiceRecalcTest {

  @Mock private LadderStandingRepository standingRepo;

  @Mock private MatchRepository matchRepo;

  @Mock private LadderSeasonRepository seasonRepo;

  @Mock private LadderMatchLinkRepository linkRepo;

  @Mock private BandPositionRepository bandRepo;

  @Mock private AutoTrophyService autoTrophyService;

  @Mock private LadderConfigRepository configRepo;

  @Mock private com.w3llspring.fhpb.web.service.MatchConfirmationService matchConfirmationService;

  @Mock private PlayerTrustService playerTrustService;

  @Mock private SeasonCarryOverService seasonCarryOverService;

  @Mock private com.w3llspring.fhpb.web.db.LadderRatingChangeRepository ratingChangeRepo;

  @Mock private SeasonStandingsRecalcTracker recalcTracker;

  @Mock private ApplicationEventPublisher eventPublisher;

  @Captor private ArgumentCaptor<List<LadderStanding>> standingListCaptor;

  private LadderV2Service service;
  private LadderSeason season;
  private User player1;
  private User player2;
  private User player3;

  @BeforeEach
  void setUp() {
    // Create test season using ReflectionTestUtils to set the ID
    season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 1L);

    // Create test users
    player1 = new User();
    ReflectionTestUtils.setField(player1, "id", 100L);
    player1.setNickName("Alice");

    player2 = new User();
    ReflectionTestUtils.setField(player2, "id", 200L);
    player2.setNickName("Bob");

    player3 = new User();
    ReflectionTestUtils.setField(player3, "id", 300L);
    player3.setNickName("Charlie");

    // Create service with mocked dependencies
    SeasonNameGenerator seasonNameGenerator = new SeasonNameGenerator();
    service =
        new LadderV2Service(
            standingRepo,
            seasonRepo,
            linkRepo,
            bandRepo,
            matchRepo,
            autoTrophyService,
            configRepo,
            matchConfirmationService,
            seasonNameGenerator,
            new com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms(
                java.util.List.of(
                    new com.w3llspring.fhpb.web.service.scoring
                        .MarginCurveV1LadderScoringAlgorithm(),
                    new com.w3llspring.fhpb.web.service.scoring
                        .BalancedV1LadderScoringAlgorithm())),
            ratingChangeRepo,
            seasonCarryOverService,
            recalcTracker,
            eventPublisher);

    // Setup default mock behaviors
    when(bandRepo.findBySeason(any(LadderSeason.class))).thenReturn(List.of());
    when(seasonCarryOverService.resolveCarryOverSeeds(any(LadderSeason.class)))
        .thenReturn(Map.of());
    when(linkRepo.findByMatch(any(Match.class))).thenReturn(Optional.empty());
    when(linkRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void recalcSeasonStandings_shouldRemoveStandingWhenPlayerHasNoConfirmedMatches() {
    // GIVEN: A standing exists for a player who has no confirmed matches
    LadderStanding standingWithNoMatches = createStanding(player1, 0);
    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standingWithNoMatches));

    // No confirmed matches for any player
    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of());

    // WHEN: recalcSeasonStandings is called
    service.recalcSeasonStandings(season);

    // THEN: The standing should be deleted
    verify(standingRepo).deleteAll(standingListCaptor.capture());
    List<LadderStanding> deletedStandings = standingListCaptor.getValue();

    assertThat(deletedStandings).hasSize(1);
    assertThat(deletedStandings.get(0).getUser().getId()).isEqualTo(player1.getId());
    verify(standingRepo).flush();
  }

  @Test
  void
      recalcSeasonStandings_shouldNotRemoveStandingWhenPlayerHasZeroPointsButHasConfirmedMatches() {
    // GIVEN: A standing with 0 points BUT the player has confirmed matches
    // This is the critical edge case - player lost more than they won, resulting in 0 points
    LadderStanding standingWithZeroPoints = createStanding(player1, 0);

    // Player1 has confirmed matches (even though they resulted in 0 points)
    Match match1 = createMatch(player1, player2, MatchState.CONFIRMED, 5, 11); // loss
    Match match2 = createMatch(player1, player3, MatchState.CONFIRMED, 7, 11); // loss

    // Mock the repository calls - standings will be reset to 0 and then recalculated
    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standingWithZeroPoints)) // Initial load
        .thenReturn(List.of(standingWithZeroPoints)); // Second load for deletion check

    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of(match1, match2));

    // Mock saveAll to return what was passed in
    when(standingRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // WHEN: recalcSeasonStandings is called
    service.recalcSeasonStandings(season);

    // THEN: The standing should NOT be deleted (player has confirmed matches)
    // deleteAll() should NEVER be called because toDelete list is empty (guarded by if
    // (!toDelete.isEmpty()))
    verify(standingRepo, never()).deleteAll(any());
  }

  @Test
  void recalcSeasonStandings_shouldPreserveCarryOverPlayersWithoutCurrentMatches() {
    LadderStanding carriedStanding = createStanding(player1, 0);
    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(carriedStanding))
        .thenReturn(List.of(carriedStanding));
    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of());
    when(standingRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    when(seasonCarryOverService.resolveCarryOverSeeds(season))
        .thenReturn(
            Map.of(
                player1.getId(),
                new SeasonCarryOverService.CarryOverSeed(player1, player1.getNickName(), 42)));

    service.recalcSeasonStandings(season);

    assertThat(carriedStanding.getPoints()).isEqualTo(42);
    verify(standingRepo, never()).deleteAll(any());
  }

  @Test
  void recalcSeasonStandings_shouldRemoveOnlyStandingsWithoutMatches() {
    // GIVEN: Three standings - player1 and player2 have matches, player3 does not
    LadderStanding standing1 = createStanding(player1, 10);
    LadderStanding standing2 = createStanding(player2, 5);
    LadderStanding standing3 = createStanding(player3, 5); // no matches - should be deleted

    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standing1, standing2, standing3)) // Initial load
        .thenReturn(List.of(standing1, standing2, standing3)); // Second load for deletion check

    // Match between player1 and player2 - both have confirmed matches
    Match match1 = createMatch(player1, player2, MatchState.CONFIRMED, 11, 5);
    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of(match1));

    when(standingRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // WHEN: recalcSeasonStandings is called
    service.recalcSeasonStandings(season);

    // THEN: Only standing for player3 should be deleted (player3 has no matches)
    verify(standingRepo).deleteAll(standingListCaptor.capture());
    List<LadderStanding> deletedStandings = standingListCaptor.getValue();

    assertThat(deletedStandings).hasSize(1);
    assertThat(deletedStandings.get(0).getUser().getId()).isEqualTo(player3.getId());
  }

  @Test
  void recalcSeasonStandings_shouldHandleGuestPlayers() {
    // GIVEN: A match where one player is a guest
    LadderStanding standing1 = createStanding(player1, 10);
    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standing1)) // Initial load
        .thenReturn(List.of(standing1)); // Second load for deletion check

    // Match with player1 vs guest player
    Match match = createMatch(player1, player2, MatchState.CONFIRMED, 11, 5);
    match.setB1Guest(true); // player2 is a guest
    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of(match));

    when(standingRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // WHEN: recalcSeasonStandings is called
    service.recalcSeasonStandings(season);

    // THEN: Only player1's standing should be kept (guest player ignored)
    // deleteAll should NOT be called because toDelete is empty (player1 has matches)
    verify(standingRepo, never()).deleteAll(any());
  }

  @Test
  void recalcSeasonStandings_shouldIgnoreMatchesExcludedFromStandings() {
    // GIVEN: Player has a standing, but only excluded confirmed matches
    LadderStanding standing1 = createStanding(player1, 10);
    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standing1))
        .thenReturn(List.of(standing1));

    Match excludedMatch = createMatch(player1, player2, MatchState.CONFIRMED, 11, 5);
    excludedMatch.setExcludeFromStandings(true);
    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of(excludedMatch));

    when(standingRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // WHEN: Recalculation runs
    service.recalcSeasonStandings(season);

    // THEN: Standing is removed because excluded matches should not count as participation
    verify(standingRepo).deleteAll(standingListCaptor.capture());
    List<LadderStanding> deletedStandings = standingListCaptor.getValue();
    assertThat(deletedStandings).hasSize(1);
    assertThat(deletedStandings.get(0).getUser().getId()).isEqualTo(player1.getId());
  }

  @Test
  void recalcSeasonStandings_shouldHandleNullSeason() {
    // GIVEN: null season

    // WHEN: recalcSeasonStandings is called with null
    service.recalcSeasonStandings(null);

    // THEN: Should return early without any repository calls
    verify(standingRepo, never()).findBySeasonOrderByRankNoAsc(any());
    verify(matchRepo, never()).findConfirmedForSeasonChrono(any());
  }

  @Test
  void recalcSeasonStandings_shouldReloadSeasonWithLadderConfigInsideTransaction() {
    LadderSeason detachedSeason = new LadderSeason();
    ReflectionTestUtils.setField(detachedSeason, "id", 1L);

    LadderSeason hydratedSeason = new LadderSeason();
    ReflectionTestUtils.setField(hydratedSeason, "id", 1L);
    LadderConfig cfg = new LadderConfig();
    cfg.setScoringAlgorithm(LadderConfig.ScoringAlgorithm.BALANCED_V1);
    hydratedSeason.setLadderConfig(cfg);

    when(seasonRepo.findByIdWithLadderConfig(1L)).thenReturn(Optional.of(hydratedSeason));
    when(standingRepo.findBySeasonOrderByRankNoAsc(hydratedSeason))
        .thenReturn(List.of())
        .thenReturn(List.of());
    when(matchRepo.findConfirmedForSeasonChrono(hydratedSeason)).thenReturn(List.of());

    assertDoesNotThrow(() -> service.recalcSeasonStandings(detachedSeason));

    verify(seasonRepo).findByIdWithLadderConfig(1L);
    verify(standingRepo, atLeastOnce()).findBySeasonOrderByRankNoAsc(hydratedSeason);
    verify(matchRepo).findConfirmedForSeasonChrono(hydratedSeason);
  }

  @Test
  void recalcSeasonStandings_shouldHandleAllPlayersInDoubles() {
    // GIVEN: A doubles match with 4 players
    User player4 = new User();
    player4.setId(400L);
    player4.setNickName("Dana");

    LadderStanding standing1 = createStanding(player1, 10);
    LadderStanding standing2 = createStanding(player2, 10);
    LadderStanding standing3 = createStanding(player3, 5);
    LadderStanding standing4 = createStanding(player4, 5);

    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standing1, standing2, standing3, standing4)) // Initial load
        .thenReturn(
            List.of(standing1, standing2, standing3, standing4)); // Second load for deletion check

    // Doubles match: player1 + player2 vs player3 + player4
    Match doublesMatch = new Match();
    doublesMatch.setSeason(season);
    doublesMatch.setState(MatchState.CONFIRMED);
    doublesMatch.setA1(player1);
    doublesMatch.setA2(player2);
    doublesMatch.setB1(player3);
    doublesMatch.setB2(player4);
    doublesMatch.setA1Guest(false);
    doublesMatch.setA2Guest(false);
    doublesMatch.setB1Guest(false);
    doublesMatch.setB2Guest(false);
    doublesMatch.setScoreA(11);
    doublesMatch.setScoreB(5);

    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of(doublesMatch));

    when(standingRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // WHEN: recalcSeasonStandings is called
    service.recalcSeasonStandings(season);

    // THEN: No standings should be deleted (all 4 players participated)
    // deleteAll should NOT be called because toDelete is empty (all players have matches)
    verify(standingRepo, never()).deleteAll(any());
  }

  @Test
  void recalcSeasonStandings_shouldSurviveBandPositionDuplicateInsertRace() {
    // GIVEN: one standing that will require creating/updating a band position
    LadderStanding standing = createStanding(player1, 10);
    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standing)) // initial load/reset
        .thenReturn(List.of(standing)); // deletion-check load

    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of());
    when(standingRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // Simulate race:
    // 1) lookup empty -> attempt create
    // 2) create fails with duplicate key because another worker created it first
    // 3) re-read now finds existing row and recalc continues
    BandPosition existing = new BandPosition();
    existing.setUser(player1);
    existing.setBandIndex(1);
    existing.setPositionInBand(1);
    existing.setDailyMomentum(0);

    when(bandRepo.findBySeasonAndUser(season, player1))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existing));
    when(bandRepo.saveAndFlush(any(BandPosition.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate user"));
    when(bandRepo.save(any(BandPosition.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // WHEN / THEN: recalc should recover and complete
    assertDoesNotThrow(() -> service.recalcSeasonStandings(season));
    verify(bandRepo).saveAndFlush(any(BandPosition.class));
    verify(bandRepo).save(existing);
  }

  @Test
  void recalcSeasonStandings_shouldRebuildDisplayFormFromReplay() {
    LadderStanding standing1 = createStanding(player1, 0);
    LadderStanding standing2 = createStanding(player2, 0);
    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standing1, standing2))
        .thenReturn(List.of(standing1, standing2));
    when(standingRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Match match = createMatch(player1, player2, MatchState.CONFIRMED, 11, 5);
    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of(match));

    List<BandPosition> seasonBandPositions = new ArrayList<>();
    when(bandRepo.findBySeason(season))
        .thenAnswer(invocation -> new ArrayList<>(seasonBandPositions));
    when(bandRepo.findBySeasonAndUser(eq(season), any(User.class)))
        .thenAnswer(invocation -> findBandPosition(seasonBandPositions, invocation.getArgument(1)));
    when(bandRepo.findBySeasonAndUserIdIn(eq(season), anyCollection()))
        .thenAnswer(
            invocation -> findBandPositions(seasonBandPositions, invocation.getArgument(1)));
    when(bandRepo.saveAndFlush(any(BandPosition.class)))
        .thenAnswer(
            invocation -> {
              BandPosition saved = invocation.getArgument(0);
              upsertBandPosition(seasonBandPositions, saved);
              return saved;
            });
    when(bandRepo.save(any(BandPosition.class)))
        .thenAnswer(
            invocation -> {
              BandPosition saved = invocation.getArgument(0);
              upsertBandPosition(seasonBandPositions, saved);
              return saved;
            });

    service.recalcSeasonStandings(season);

    List<LadderV2Service.LadderRow> rows = service.buildDisplayRows(List.of(standing1, standing2));
    LadderV2Service.LadderRow winnerRow =
        rows.stream().filter(row -> player1.getId().equals(row.userId)).findFirst().orElseThrow();
    LadderV2Service.LadderRow loserRow =
        rows.stream().filter(row -> player2.getId().equals(row.userId)).findFirst().orElseThrow();

    assertThat(winnerRow.momentum).isPositive();
    assertThat(loserRow.momentum).isNegative();
  }

  @Test
  void applyMatch_shouldQueueRecalcForConfirmedMatchEvenWhenExcluded() {
    Match confirmed = createMatch(player1, player2, MatchState.CONFIRMED, 11, 5);
    confirmed.setSeason(season);
    confirmed.setExcludeFromStandings(true);

    service.applyMatch(confirmed);

    verify(eventPublisher).publishEvent(any(SeasonStandingsRecalcRequestedEvent.class));
  }

  @Test
  void recalcSeasonStandings_shouldUseFallbackDisplayNameWhenNicknameMissing() {
    User unnamed = new User();
    ReflectionTestUtils.setField(unnamed, "id", 400L);
    unnamed.setNickName(null);
    unnamed.setEmail("unnamed@test.local");

    LadderStanding standing1 = createStanding(player1, 0);
    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standing1))
        .thenReturn(List.of(standing1));

    Match match = createMatch(player1, unnamed, MatchState.CONFIRMED, 5, 11);
    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of(match));
    when(standingRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.recalcSeasonStandings(season);

    verify(standingRepo, atLeastOnce()).saveAll(standingListCaptor.capture());
    boolean fallbackUsed =
        standingListCaptor.getAllValues().stream()
            .flatMap(List::stream)
            .anyMatch(
                saved ->
                    saved.getUser() != null
                        && Long.valueOf(400L).equals(saved.getUser().getId())
                        && UserPublicName.FALLBACK.equals(saved.getDisplayName()));

    assertThat(fallbackUsed).isTrue();
  }

  @Test
  void recalcSeasonStandings_shouldBackfillExcludeFlagForLegacyPersonalRecordMatches() {
    LadderConfig cfg = new LadderConfig();
    cfg.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    cfg.setAllowGuestOnlyPersonalMatches(true);
    season.setLadderConfig(cfg);

    LadderStanding standing1 = createStanding(player1, 10);
    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standing1))
        .thenReturn(List.of(standing1));

    Match legacyPersonal = new Match();
    legacyPersonal.setSeason(season);
    legacyPersonal.setState(MatchState.CONFIRMED);
    legacyPersonal.setA1(player1);
    legacyPersonal.setA1Guest(false);
    legacyPersonal.setA2Guest(true);
    legacyPersonal.setB1Guest(true);
    legacyPersonal.setB2Guest(true);
    legacyPersonal.setScoreA(11);
    legacyPersonal.setScoreB(5);
    legacyPersonal.setExcludeFromStandings(false);

    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of(legacyPersonal));
    when(matchRepo.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(standingRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.recalcSeasonStandings(season);

    verify(matchRepo, atLeastOnce()).save(legacyPersonal);
    verify(standingRepo).deleteAll(standingListCaptor.capture());
    assertThat(legacyPersonal.isExcludeFromStandings()).isTrue();
    assertThat(standingListCaptor.getValue()).hasSize(1);
  }

  @Test
  void recalcSeasonStandings_shouldIgnoreCrossSeasonHistoryWhenScoring() {
    LadderStanding standing1 = createStanding(player1, 0);
    LadderStanding standing2 = createStanding(player2, 0);
    when(standingRepo.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standing1, standing2))
        .thenReturn(List.of(standing1, standing2));
    when(standingRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Match current = createMatch(player1, player2, MatchState.CONFIRMED, 11, 5);
    when(matchRepo.findConfirmedForSeasonChrono(season)).thenReturn(List.of(current));

    User other1 = new User();
    ReflectionTestUtils.setField(other1, "id", 401L);
    other1.setNickName("Other1");
    User other2 = new User();
    ReflectionTestUtils.setField(other2, "id", 402L);
    other2.setNickName("Other2");
    User other3 = new User();
    ReflectionTestUtils.setField(other3, "id", 403L);
    other3.setNickName("Other3");
    User other4 = new User();
    ReflectionTestUtils.setField(other4, "id", 404L);
    other4.setNickName("Other4");
    User other5 = new User();
    ReflectionTestUtils.setField(other5, "id", 405L);
    other5.setNickName("Other5");

    List<Match> crossSeasonHistory =
        List.of(
            createHistoricalMatch(player1, other1, 8, 11),
            createHistoricalMatch(player1, other2, 11, 9),
            createHistoricalMatch(player1, other3, 11, 6),
            createHistoricalMatch(player1, other4, 11, 7),
            createHistoricalMatch(player1, other5, 11, 5));

    when(matchRepo.findRecentPlayedMatchesForPlayersInSeason(
            eq(season), anyCollection(), any(), any()))
        .thenReturn(List.of());
    when(matchRepo.findRecentPlayedMatchesForPlayers(anyCollection(), any(), any()))
        .thenReturn(crossSeasonHistory);

    service.recalcSeasonStandings(season);

    assertThat(current.getA1Delta()).isEqualTo(2);
    assertThat(current.getB1Delta()).isEqualTo(-2);
    verify(matchRepo, never()).findRecentPlayedMatchesForPlayers(anyCollection(), any(), any());
    verify(matchRepo)
        .findRecentPlayedMatchesForPlayersInSeason(eq(season), anyCollection(), any(), any());
  }

  /** Helper method to create a LadderStanding for testing */
  private LadderStanding createStanding(User user, int points) {
    LadderStanding standing = new LadderStanding();
    standing.setSeason(season);
    standing.setUser(user);
    standing.setDisplayName(user.getNickName());
    standing.setPoints(points);
    ReflectionTestUtils.setField(standing, "rankNo", 0);
    return standing;
  }

  /** Helper method to create a Match for testing */
  private Match createMatch(User playerA, User playerB, MatchState state, int scoreA, int scoreB) {
    Match match = new Match();
    match.setSeason(season);
    match.setState(state);
    match.setA1(playerA);
    match.setB1(playerB);
    match.setA1Guest(false);
    match.setB1Guest(false);
    match.setA2Guest(true); // singles match
    match.setB2Guest(true); // singles match
    match.setScoreA(scoreA);
    match.setScoreB(scoreB);
    return match;
  }

  private Match createHistoricalMatch(User playerA, User playerB, int scoreA, int scoreB) {
    Match match = createMatch(playerA, playerB, MatchState.CONFIRMED, scoreA, scoreB);
    LadderSeason otherSeason = new LadderSeason();
    ReflectionTestUtils.setField(otherSeason, "id", 99L);
    match.setSeason(otherSeason);
    return match;
  }

  private Optional<BandPosition> findBandPosition(
      List<BandPosition> seasonBandPositions, User user) {
    if (user == null || user.getId() == null) {
      return Optional.empty();
    }
    return seasonBandPositions.stream()
        .filter(
            bp ->
                bp.getUser() != null
                    && bp.getUser().getId() != null
                    && bp.getUser().getId().equals(user.getId()))
        .findFirst();
  }

  private List<BandPosition> findBandPositions(
      List<BandPosition> seasonBandPositions, Collection<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return List.of();
    }
    return seasonBandPositions.stream()
        .filter(
            bp ->
                bp.getUser() != null
                    && bp.getUser().getId() != null
                    && userIds.contains(bp.getUser().getId()))
        .toList();
  }

  private void upsertBandPosition(List<BandPosition> seasonBandPositions, BandPosition saved) {
    if (saved == null || saved.getUser() == null || saved.getUser().getId() == null) {
      return;
    }
    seasonBandPositions.removeIf(
        existing ->
            existing.getUser() != null
                && existing.getUser().getId() != null
                && existing.getUser().getId().equals(saved.getUser().getId()));
    seasonBandPositions.add(saved);
  }
}
