package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.w3llspring.fhpb.web.controller.match.VoiceMatchLogController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for match logging and standings updates.
 *
 * <p>This test verifies the critical flow: 1. Users log matches via the experimental match log
 * endpoint 2. Standings are automatically updated 3. When matches are nullified, standings revert
 * correctly
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@AutoConfigureMockMvc
public class StandingsUpdateIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private LadderConfigRepository configRepository;

  @Autowired private LadderSeasonRepository seasonRepository;

  @Autowired private MatchRepository matchRepository;

  @Autowired private LadderStandingRepository standingRepository;

  @Autowired private LadderMembershipRepository membershipRepository;

  @Autowired private jakarta.persistence.EntityManager entityManager;

  private User admin;
  private User player1;
  private User player2;
  private User player3;
  private User player4;
  private LadderConfig ladder;
  private LadderSeason season;

  private MatchConfirmationService matchConfirmationService;

  @Autowired
  private void setMatchConfirmationService(MatchConfirmationService service) {
    this.matchConfirmationService = service;
  }

  @Autowired private LadderV2Service ladderV2Service;

  private void setupTest() {
    seedTestData();
    // Set up authentication context for test requests AFTER seedTestData
    // so that admin user has been persisted and has an ID
    CustomUserDetails userDetails = new CustomUserDetails(admin);
    Collection<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
    Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private void seedTestData() {
    matchRepository.deleteAll();
    standingRepository.deleteAll();
    membershipRepository.deleteAll();
    seasonRepository.deleteAll();
    configRepository.deleteAll();
    userRepository.deleteAll();

    admin = new User();
    admin.setEmail("admin@test.local");
    admin.setNickName("AdminUser");
    admin.setPassword("pw");
    admin.setAdmin(true); // Mark as admin so ROLE_ADMIN is granted
    admin.setAcknowledgedTermsAt(Instant.now());
    admin = userRepository.save(admin);

    player1 = new User();
    player1.setEmail("player1@test.local");
    player1.setNickName("Alice");
    player1.setPassword("pw");
    player1.setAcknowledgedTermsAt(Instant.now());
    player1 = userRepository.save(player1);

    player2 = new User();
    player2.setEmail("player2@test.local");
    player2.setNickName("Bob");
    player2.setPassword("pw");
    player2.setAcknowledgedTermsAt(Instant.now());
    player2 = userRepository.save(player2);

    player3 = new User();
    player3.setEmail("player3@test.local");
    player3.setNickName("Charlie");
    player3.setPassword("pw");
    player3.setAcknowledgedTermsAt(Instant.now());
    player3 = userRepository.save(player3);

    player4 = new User();
    player4.setEmail("player4@test.local");
    player4.setNickName("Diana");
    player4.setPassword("pw");
    player4.setAcknowledgedTermsAt(Instant.now());
    player4 = userRepository.save(player4);

    ladder = new LadderConfig();
    ladder.setTitle("Test Ladder");
    ladder.setOwnerUserId(admin.getId());
    ladder.setStatus(LadderConfig.Status.ACTIVE);
    ladder = configRepository.save(ladder);

    season = new LadderSeason();
    season.setLadderConfig(ladder);
    season.setName("Test Season");
    season.setStartDate(LocalDate.now().minusDays(30));
    season.setEndDate(LocalDate.now().plusDays(30));
    season.setState(LadderSeason.State.ACTIVE);
    season = seasonRepository.save(season);

    // Register all players (and admin) as active members of the ladder
    registerMember(admin);
    registerMember(player1);
    registerMember(player2);
    registerMember(player3);
    registerMember(player4);
  }

  private void registerMember(User user) {
    LadderMembership membership = new LadderMembership();
    membership.setUserId(user.getId());
    membership.setLadderConfig(ladder);
    if (ladder.getOwnerUserId() != null && ladder.getOwnerUserId().equals(user.getId())) {
      membership.setRole(LadderMembership.Role.ADMIN);
    }
    membership.setState(LadderMembership.State.ACTIVE);
    LadderMembership saved = membershipRepository.save(membership);
    // Flush to ensure it's immediately persisted
    membershipRepository.flush();
  }

  @BeforeEach
  void setUp() {
    // Data setup is done at the beginning of each test method
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private VoiceMatchLogController.ConfirmRequest createMatchRequest(
      List<Long> teamAUserIds, List<Long> teamBUserIds, int scoreA, int scoreB) {
    VoiceMatchLogController.ConfirmRequest request = new VoiceMatchLogController.ConfirmRequest();
    request.setSeasonId(season.getId());
    request.setTeamAUserIds(teamAUserIds);
    request.setTeamBUserIds(teamBUserIds);
    request.setScoreTeamA(scoreA);
    request.setScoreTeamB(scoreB);
    request.setPlayedAtEpochMillis(System.currentTimeMillis());
    request.setTranscript("Test match log");
    request.setConfidenceScore(95);
    return request;
  }

  private static class MatchResponse {
    public Long matchId;

    public MatchResponse(Long matchId) {
      this.matchId = matchId;
    }

    public Long getMatchId() {
      return matchId;
    }
  }

  private MatchResponse logMatch(VoiceMatchLogController.ConfirmRequest request) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String requestJson = mapper.writeValueAsString(request);
    CustomUserDetails userDetails = new CustomUserDetails(admin);
    String responseJson =
        mockMvc
            .perform(
                post("/voice-match-log/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson)
                    .with(user(userDetails))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(responseJson);
    Long matchId = node.get("matchId").asLong();
    return new MatchResponse(matchId);
  }

  private void nullifyMatch(Long matchId) throws Exception {
    Match current =
        matchRepository
            .findById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    CustomUserDetails userDetails = new CustomUserDetails(admin);
    mockMvc
        .perform(
            post("/matches/" + matchId + "/nullify")
                .contentType(MediaType.APPLICATION_JSON)
                .param("expectedVersion", String.valueOf(current.getVersion()))
                .with(user(userDetails))
                .with(csrf()))
        .andExpect(status().isOk());
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);
    ladderV2Service.recalcSeasonStandings(season);
  }

  private void confirmMatch(Long matchId) {
    Match match =
        matchRepository
            .findById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    List<Long> candidateConfirmers = new ArrayList<>();
    if (match.getA1() != null && match.getA1().getId() != null) {
      candidateConfirmers.add(match.getA1().getId());
    } else if (match.getA2() != null && match.getA2().getId() != null) {
      candidateConfirmers.add(match.getA2().getId());
    }
    if (match.getB1() != null && match.getB1().getId() != null) {
      candidateConfirmers.add(match.getB1().getId());
    } else if (match.getB2() != null && match.getB2().getId() != null) {
      candidateConfirmers.add(match.getB2().getId());
    }

    for (Long confirmerId : candidateConfirmers) {
      try {
        matchConfirmationService.confirmMatch(matchId, confirmerId);
      } catch (IllegalStateException ex) {
        // Helper is intentionally idempotent across both teams.
        // A stale second click after the match is already confirmed is fine here.
      }
    }

    entityManager.flush();
    entityManager.clear();
    Match confirmed =
        matchRepository
            .findById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    if (confirmed.getState() == MatchState.CONFIRMED) {
      season = seasonRepository.findById(confirmed.getSeason().getId()).orElse(season);
      ladderV2Service.recalcSeasonStandings(season);
    }
  }

  private Map<Long, Integer> getPlayerPoints() {
    List<LadderStanding> standings = standingRepository.findBySeasonOrderByRankNoAsc(season);
    return standings.stream()
        .collect(Collectors.toMap(s -> s.getUser().getId(), LadderStanding::getPoints));
  }

  // Test methods below

  @Test
  @Transactional
  @DisplayName("Standings reflect impact of wins, losses, and streaks")
  void shouldReflectWinLossStreakImpactOnStandings() throws Exception {
    setupTest();
    ladder.setSecurityLevel(com.w3llspring.fhpb.web.model.LadderSecurity.STANDARD);
    configRepository.save(ladder);
    season.setLadderConfig(ladder);
    seasonRepository.save(season);

    // Initial: all players, no matches
    List<LadderStanding> standings = standingRepository.findBySeasonOrderByRankNoAsc(season);
    assertThat(standings).isEmpty();

    // 1. Admin & player1 defeat player2 & player3
    VoiceMatchLogController.ConfirmRequest match1 =
        createMatchRequest(
            List.of(admin.getId(), player1.getId()),
            List.of(player2.getId(), player3.getId()),
            11,
            5);
    MatchResponse resp1 = logMatch(match1);
    confirmMatch(resp1.getMatchId());
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);
    standings = standingRepository.findBySeasonOrderByRankNoAsc(season);
    Map<Long, Integer> after1 = getPlayerPoints();

    // 2. player2 & player3 defeat admin & player1 (revenge)
    VoiceMatchLogController.ConfirmRequest match2 =
        createMatchRequest(
            List.of(player2.getId(), player3.getId()),
            List.of(admin.getId(), player1.getId()),
            11,
            7);
    MatchResponse resp2 = logMatch(match2);
    confirmMatch(resp2.getMatchId());
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);
    Map<Long, Integer> after2 = getPlayerPoints();

    // 3. player4 & admin defeat player1 & player2 (admin win streak starts)
    VoiceMatchLogController.ConfirmRequest match3 =
        createMatchRequest(
            List.of(player4.getId(), admin.getId()),
            List.of(player1.getId(), player2.getId()),
            11,
            9);
    MatchResponse resp3 = logMatch(match3);
    confirmMatch(resp3.getMatchId());
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);
    Map<Long, Integer> after3 = getPlayerPoints();

    // 4. admin & player3 defeat player4 & player1 (admin win streak continues)
    VoiceMatchLogController.ConfirmRequest match4 =
        createMatchRequest(
            List.of(admin.getId(), player3.getId()),
            List.of(player4.getId(), player1.getId()),
            11,
            8);
    MatchResponse resp4 = logMatch(match4);
    confirmMatch(resp4.getMatchId());
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);
    Map<Long, Integer> after4 = getPlayerPoints();

    // 5. player1 & player2 defeat admin & player4 (admin win streak broken)
    VoiceMatchLogController.ConfirmRequest match5 =
        createMatchRequest(
            List.of(player1.getId(), player2.getId()),
            List.of(admin.getId(), player4.getId()),
            11,
            6);
    MatchResponse resp5 = logMatch(match5);
    confirmMatch(resp5.getMatchId());
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);
    Map<Long, Integer> after5 = getPlayerPoints();

    // Output for baseline (can be checked in logs or by debugging)
    System.out.println("After 1: " + after1);
    System.out.println("After 2: " + after2);
    System.out.println("After 3: " + after3);
    System.out.println("After 4: " + after4);
    System.out.println("After 5: " + after5);

    // Assert that points/rankings change as expected (baseline, adjust as needed)
    // Example: after admin's win streak, admin should be ranked higher than after a loss
    assertThat(after3.get(admin.getId())).isGreaterThan(after2.get(admin.getId()));
    assertThat(after4.get(admin.getId())).isGreaterThan(after3.get(admin.getId()));
    assertThat(after5.get(admin.getId())).isLessThan(after4.get(admin.getId()));
  }

  @Test
  @Transactional
  @DisplayName("Standings algorithm should rank winners above losers")
  void shouldRespectStandingsAlgorithm() throws Exception {
    setupTest();

    // Log match: admin+player2 beat player3+player4 11-9
    VoiceMatchLogController.ConfirmRequest request =
        createMatchRequest(
            List.of(admin.getId(), player2.getId()),
            List.of(player3.getId(), player4.getId()),
            11,
            9);
    MatchResponse response = logMatch(request);
    confirmMatch(response.getMatchId());

    // Flush and clear session to see standings
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);

    List<LadderStanding> standings = standingRepository.findBySeasonOrderByRankNoAsc(season);
    assertThat(standings).hasSize(4);

    // Create a map for easy lookup
    Map<Long, LadderStanding> standingsByUserId =
        standings.stream().collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

    LadderStanding standAdmin = standingsByUserId.get(admin.getId());
    LadderStanding stand2 = standingsByUserId.get(player2.getId());
    LadderStanding stand3 = standingsByUserId.get(player3.getId());
    LadderStanding stand4 = standingsByUserId.get(player4.getId());

    // Winners should have equal or better points than losers
    assertThat(standAdmin.getPoints()).isGreaterThanOrEqualTo(stand3.getPoints());
    assertThat(standAdmin.getPoints()).isGreaterThanOrEqualTo(stand4.getPoints());
    assertThat(stand2.getPoints()).isGreaterThanOrEqualTo(stand3.getPoints());
    assertThat(stand2.getPoints()).isGreaterThanOrEqualTo(stand4.getPoints());

    // Verify rankings are assigned and ordered correctly
    assertThat(standAdmin.getRank()).isLessThanOrEqualTo(stand3.getRank());
    assertThat(standAdmin.getRank()).isLessThanOrEqualTo(stand4.getRank());
  }

  @Test
  @Transactional
  @DisplayName("Standings recalculation after nullification should produce correct rankings")
  void shouldRecalculateStandingsAccuratelyAfterNullification() throws Exception {
    setupTest();

    // Log 3 matches
    Long m1 =
        logMatch(
                createMatchRequest(
                    List.of(admin.getId(), player2.getId()),
                    List.of(player3.getId(), player4.getId()),
                    11,
                    9))
            .getMatchId();
    confirmMatch(m1);

    // Match 2: admin+player3 beat player2+player4
    Long m2 =
        logMatch(
                createMatchRequest(
                    List.of(admin.getId(), player3.getId()),
                    List.of(player2.getId(), player4.getId()),
                    11,
                    8))
            .getMatchId();
    confirmMatch(m2);

    // Match 3: admin+player4 beat player2+player3
    Long m3 =
        logMatch(
                createMatchRequest(
                    List.of(admin.getId(), player4.getId()),
                    List.of(player2.getId(), player3.getId()),
                    11,
                    7))
            .getMatchId();
    confirmMatch(m3);

    // Flush to ensure all confirmed matches are visible
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);

    // Get standings before nullification
    List<LadderStanding> standingsBefore =
        new ArrayList<>(standingRepository.findBySeasonOrderByRankNoAsc(season));

    // Capture admin's points (should be highest - won all 3 matches)
    int adminPointsBefore =
        standingsBefore.stream()
            .filter(s -> s.getUser().getId().equals(admin.getId()))
            .map(LadderStanding::getPoints)
            .findFirst()
            .orElse(0);

    assertThat(adminPointsBefore).isGreaterThan(0);

    // Nullify the second match
    nullifyMatch(m2);

    // Recalculate standings
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);

    List<LadderStanding> standingsAfter = standingRepository.findBySeasonOrderByRankNoAsc(season);

    // Admin should still exist (has 2 remaining matches: m1 and m3)
    LadderStanding adminStandingAfter =
        standingsAfter.stream()
            .filter(s -> s.getUser().getId().equals(admin.getId()))
            .findFirst()
            .orElse(null);

    assertThat(adminStandingAfter).isNotNull();
    assertThat(adminStandingAfter.getPoints()).isLessThan(adminPointsBefore);

    // All standings should still have valid ranks
    for (LadderStanding standing : standingsAfter) {
      assertThat(standing.getRank()).isGreaterThan(0);
    }
  }

  @Test
  @Transactional
  @DisplayName("Player should be removed from standings when all their matches are nullified")
  void shouldRemovePlayerFromStandingsWhenAllMatchesNullified() throws Exception {
    setupTest();

    // Admin plays 1 match only
    Long matchId =
        logMatch(createMatchRequest(List.of(admin.getId()), List.of(player2.getId()), 11, 5))
            .getMatchId();
    confirmMatch(matchId);

    // Flush to see standings
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);

    // Verify admin is in standings
    List<LadderStanding> standingsBefore = standingRepository.findBySeasonOrderByRankNoAsc(season);
    assertThat(standingsBefore).hasSize(2);

    List<Long> playerIdsInStandingsBefore =
        standingsBefore.stream().map(s -> s.getUser().getId()).collect(Collectors.toList());
    assertThat(playerIdsInStandingsBefore).contains(admin.getId());

    // Nullify the only match
    nullifyMatch(matchId);

    // After nullification, admin should NOT be in standings anymore
    // (since they have no confirmed matches)
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);

    List<LadderStanding> standingsAfter = standingRepository.findBySeasonOrderByRankNoAsc(season);

    // Admin should be removed from standings
    List<Long> playerIdsInStandingsAfter =
        standingsAfter.stream().map(s -> s.getUser().getId()).collect(Collectors.toList());
    assertThat(playerIdsInStandingsAfter)
        .as("Player with no confirmed matches should be removed from standings")
        .doesNotContain(admin.getId());
  }

  @Test
  @Transactional
  @DisplayName(
      "Partial match nullification should only remove player if they have no other matches")
  void shouldOnlyRemovePlayerIfNoRemainingMatches() throws Exception {
    setupTest();

    // Admin plays 2 matches
    Long m1 =
        logMatch(createMatchRequest(List.of(admin.getId()), List.of(player2.getId()), 11, 5))
            .getMatchId();
    confirmMatch(m1);

    Long m2 =
        logMatch(createMatchRequest(List.of(admin.getId()), List.of(player3.getId()), 11, 6))
            .getMatchId();
    confirmMatch(m2);

    // Flush to see standings
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);

    // Verify admin is in standings after both matches
    List<LadderStanding> standingsAfter2Matches =
        standingRepository.findBySeasonOrderByRankNoAsc(season);
    List<Long> playerIdsAfter2 =
        standingsAfter2Matches.stream().map(s -> s.getUser().getId()).collect(Collectors.toList());
    assertThat(playerIdsAfter2).contains(admin.getId());

    // Nullify first match
    nullifyMatch(m1);

    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);

    // Admin should STILL be in standings (has m2 remaining)
    List<LadderStanding> standingsAfterFirstNullify =
        standingRepository.findBySeasonOrderByRankNoAsc(season);
    List<Long> playerIdsAfterFirstNullify =
        standingsAfterFirstNullify.stream()
            .map(s -> s.getUser().getId())
            .collect(Collectors.toList());
    assertThat(playerIdsAfterFirstNullify)
        .as("Player with remaining matches should still be in standings")
        .contains(admin.getId());

    // Nullify second match (last remaining)
    nullifyMatch(m2);

    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);

    // NOW admin should be removed
    List<LadderStanding> standingsAfterSecondNullify =
        standingRepository.findBySeasonOrderByRankNoAsc(season);
    List<Long> playerIdsAfterSecondNullify =
        standingsAfterSecondNullify.stream()
            .map(s -> s.getUser().getId())
            .collect(Collectors.toList());
    assertThat(playerIdsAfterSecondNullify)
        .as("Player with no remaining matches should be removed from standings")
        .doesNotContain(admin.getId());
  }

  @Test
  @Transactional
  @DisplayName("Algorithm should correctly handle multiple matches and produce consistent rankings")
  void shouldProduceConsistentRankingsAcrossMultipleMatches() throws Exception {
    setupTest();

    // Scenario: Multiple matches with complex win patterns
    // Match 1: admin+player2 beat player3+player4 11-9
    Long m1 =
        logMatch(
                createMatchRequest(
                    List.of(admin.getId(), player2.getId()),
                    List.of(player3.getId(), player4.getId()),
                    11,
                    9))
            .getMatchId();
    confirmMatch(m1);

    // Match 2: admin+player3 beat player2+player4 11-8
    Long m2 =
        logMatch(
                createMatchRequest(
                    List.of(admin.getId(), player3.getId()),
                    List.of(player2.getId(), player4.getId()),
                    11,
                    8))
            .getMatchId();
    confirmMatch(m2);

    // Match 3: player2+player4 beat admin+player3 11-7
    Long m3 =
        logMatch(
                createMatchRequest(
                    List.of(player2.getId(), player4.getId()),
                    List.of(admin.getId(), player3.getId()),
                    11,
                    7))
            .getMatchId();
    confirmMatch(m3);

    // Flush to see all standings
    entityManager.flush();
    entityManager.clear();
    season = seasonRepository.findById(season.getId()).orElse(season);

    List<LadderStanding> standings = standingRepository.findBySeasonOrderByRankNoAsc(season);
    assertThat(standings).hasSize(4);

    // Verify ranks are unique and sequential
    List<Integer> ranks =
        standings.stream().map(LadderStanding::getRank).collect(Collectors.toList());
    assertThat(ranks).containsExactly(1, 2, 3, 4);

    // Verify higher rank = more points (descending order)
    for (int i = 0; i < standings.size() - 1; i++) {
      assertThat(standings.get(i).getPoints())
          .as("Rank %d should have >= points as rank %d", i + 1, i + 2)
          .isGreaterThanOrEqualTo(standings.get(i + 1).getPoints());
    }

    // All players should be present
    List<Long> playerIds =
        standings.stream().map(s -> s.getUser().getId()).collect(Collectors.toList());
    assertThat(playerIds)
        .containsExactlyInAnyOrder(
            admin.getId(), player2.getId(), player3.getId(), player4.getId());
  }

  @Test
  @DisplayName("Margin curve recalc reports elapsed time for a 101-match season replay")
  void shouldReportMarginCurveRecalcTimingForHundredMatchSeason() {
    setupTest();

    ladder.setScoringAlgorithm(LadderConfig.ScoringAlgorithm.MARGIN_CURVE_V1);
    configRepository.saveAndFlush(ladder);
    season.setLadderConfig(ladder);
    seasonRepository.saveAndFlush(season);

    List<User> players = List.of(admin, player1, player2, player3, player4);
    Instant baseTime = Instant.parse("2026-03-01T12:00:00Z");

    for (int i = 0; i < 100; i++) {
      User winner = players.get(i % players.size());
      User loser = players.get((i + 1 + (i / players.size())) % players.size());
      int loserScore =
          switch (i % 5) {
            case 0 -> 0;
            case 1 -> 4;
            case 2 -> 6;
            case 3 -> 8;
            default -> 9;
          };
      saveConfirmedSinglesMatch(
          winner, loser, 11, loserScore, baseTime.plus(i, ChronoUnit.MINUTES));
    }

    Match latestMatch =
        saveConfirmedSinglesMatch(player2, player4, 11, 5, baseTime.plus(100, ChronoUnit.MINUTES));

    season = seasonRepository.findById(season.getId()).orElseThrow();

    long startedAt = System.nanoTime();
    ladderV2Service.recalcSeasonStandings(season);
    long elapsedNanos = System.nanoTime() - startedAt;
    double elapsedMs = elapsedNanos / 1_000_000.0d;

    season = seasonRepository.findById(season.getId()).orElseThrow();

    Match recalculatedLatest = matchRepository.findById(latestMatch.getId()).orElseThrow();
    List<LadderStanding> standings = standingRepository.findBySeasonOrderByRankNoAsc(season);

    System.out.printf(
        "MarginCurveV1 recalc for %d confirmed matches took %.2f ms.%n", 101, elapsedMs);

    assertThat(matchRepository.findConfirmedForSeasonChrono(season)).hasSize(101);
    assertThat(standings).isNotEmpty();
    assertThat(recalculatedLatest.getA1Delta()).isNotNull();
    assertThat(recalculatedLatest.getB1Delta()).isNotNull();
  }

  // ===== Helper Methods =====

  private Match saveConfirmedSinglesMatch(
      User playerA, User playerB, int scoreA, int scoreB, Instant playedAt) {
    Match match = new Match();
    match.setSeason(season);
    match.setState(MatchState.CONFIRMED);
    match.setA1(playerA);
    match.setB1(playerB);
    match.setA1Guest(false);
    match.setB1Guest(false);
    match.setA2Guest(true);
    match.setB2Guest(true);
    match.setScoreA(scoreA);
    match.setScoreB(scoreB);
    match.setPlayedAt(playedAt);
    match.setCreatedAt(playedAt);
    match.setLoggedBy(playerA);
    return matchRepository.saveAndFlush(match);
  }
}
