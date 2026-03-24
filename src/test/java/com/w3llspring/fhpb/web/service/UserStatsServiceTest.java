package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.user.UserStatsService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

  @Mock private MatchRepository matchRepository;

  @Mock private LadderMembershipRepository membershipRepository;

  @Mock private LadderStandingRepository standingRepository;

  private UserStatsService service;

  @BeforeEach
  void setUp() {
    service = new UserStatsService(matchRepository, membershipRepository, standingRepository);
  }

  @Test
  void calculateUserStatsBuildsActiveSeasonStatsFromActiveSeasonMatchesOnly() {
    User user = user(1L, "Viewer");
    User partner = user(2L, "Robin");
    User opponentOne = user(3L, "Casey");
    User opponentTwo = user(4L, "Morgan");
    User opponentThree = user(5L, "Jordan");
    User opponentFour = user(6L, "Avery");

    LadderSeason activeSeason = season(LadderSeason.State.ACTIVE);
    LadderSeason endedSeason = season(LadderSeason.State.ENDED);

    Match activeConfirmedDoublesWin =
        doublesMatch(
            user,
            partner,
            opponentOne,
            opponentTwo,
            activeSeason,
            MatchState.CONFIRMED,
            11,
            6,
            Instant.parse("2026-03-10T14:00:00Z"));
    Match activeConfirmedSinglesWin =
        singlesMatch(
            user,
            opponentOne,
            activeSeason,
            MatchState.CONFIRMED,
            11,
            8,
            Instant.parse("2026-03-11T14:00:00Z"));
    Match endedConfirmedLoss =
        doublesMatch(
            user,
            partner,
            opponentThree,
            opponentFour,
            endedSeason,
            MatchState.CONFIRMED,
            8,
            11,
            Instant.parse("2026-02-10T14:00:00Z"));
    Match activeProvisionalLoss =
        doublesMatch(
            user,
            partner,
            opponentThree,
            opponentFour,
            activeSeason,
            MatchState.PROVISIONAL,
            5,
            11,
            Instant.parse("2026-03-12T14:00:00Z"));

    when(matchRepository.findByParticipant(user))
        .thenReturn(
            List.of(
                activeConfirmedDoublesWin,
                activeConfirmedSinglesWin,
                endedConfirmedLoss,
                activeProvisionalLoss));
    when(membershipRepository.findByUserIdAndState(
            user.getId(), com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE))
        .thenReturn(List.of());
    when(standingRepository.findByUser(user)).thenReturn(List.of());

    Map<String, Object> stats = service.calculateUserStats(user);

    assertThat(stats.get("totalMatches")).isEqualTo(3);
    assertThat(stats.get("wins")).isEqualTo(2);
    assertThat(stats.get("losses")).isEqualTo(1);
    assertThat(stats.get("winRate")).isEqualTo("66.7%");
    assertThat(stats.get("favoritePartner")).isEqualTo("Robin");
    assertThat(stats.get("favoritePartnerDetail")).isEqualTo("2 matches");
    assertThat(stats.get("mostBeatenOpponent")).isEqualTo("Casey");
    assertThat(stats.get("mostBeatenOpponentDetail")).isEqualTo("2 wins");

    Map<String, Object> activeSeasonStats = activeSeasonStats(stats);
    assertThat(activeSeasonStats.get("totalMatches")).isEqualTo(2);
    assertThat(activeSeasonStats.get("wins")).isEqualTo(2);
    assertThat(activeSeasonStats.get("losses")).isEqualTo(0);
    assertThat(activeSeasonStats.get("winRate")).isEqualTo("100.0%");
    assertThat(activeSeasonStats.get("pointsFor")).isEqualTo(22);
    assertThat(activeSeasonStats.get("pointsAgainst")).isEqualTo(14);
    assertThat(activeSeasonStats.get("pointDifferential")).isEqualTo("+8");
    assertThat(activeSeasonStats.get("currentStreak")).isEqualTo("2 wins");
    assertThat(activeSeasonStats.get("favoritePartner")).isEqualTo("Robin");
    assertThat(activeSeasonStats.get("favoritePartnerDetail")).isEqualTo("1 match");
    assertThat(activeSeasonStats.get("mostBeatenOpponent")).isEqualTo("Casey");
    assertThat(activeSeasonStats.get("mostBeatenOpponentDetail")).isEqualTo("2 wins");
  }

  @Test
  void calculateUserStatsUsesEmptyActiveSeasonStatsWhenNoConfirmedActiveSeasonMatchesExist() {
    User user = user(1L);
    User partner = user(2L);
    User opponentOne = user(3L);
    User opponentTwo = user(4L);

    LadderSeason endedSeason = season(LadderSeason.State.ENDED);
    Match endedConfirmedWin =
        doublesMatch(
            user,
            partner,
            opponentOne,
            opponentTwo,
            endedSeason,
            MatchState.CONFIRMED,
            11,
            7,
            Instant.parse("2026-01-10T14:00:00Z"));

    when(matchRepository.findByParticipant(user)).thenReturn(List.of(endedConfirmedWin));
    when(membershipRepository.findByUserIdAndState(
            user.getId(), com.w3llspring.fhpb.web.model.LadderMembership.State.ACTIVE))
        .thenReturn(List.of());
    when(standingRepository.findByUser(user)).thenReturn(List.of());

    Map<String, Object> stats = service.calculateUserStats(user);
    Map<String, Object> activeSeasonStats = activeSeasonStats(stats);

    assertThat(stats.get("totalMatches")).isEqualTo(1);
    assertThat(activeSeasonStats.get("totalMatches")).isEqualTo(0);
    assertThat(activeSeasonStats.get("winRate")).isEqualTo("0%");
    assertThat(activeSeasonStats.get("wins")).isEqualTo(0);
    assertThat(activeSeasonStats.get("losses")).isEqualTo(0);
    assertThat(activeSeasonStats.get("favoritePartner")).isEqualTo("None");
    assertThat(activeSeasonStats.get("favoritePartnerDetail")).isEqualTo("");
    assertThat(activeSeasonStats.get("mostBeatenOpponent")).isEqualTo("None");
    assertThat(activeSeasonStats.get("mostBeatenOpponentDetail")).isEqualTo("");
    assertThat(activeSeasonStats.get("currentStreak")).isEqualTo("None");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> activeSeasonStats(Map<String, Object> stats) {
    return (Map<String, Object>) stats.get("activeSeasonStats");
  }

  private Match doublesMatch(
      User a1,
      User a2,
      User b1,
      User b2,
      LadderSeason season,
      MatchState state,
      int scoreA,
      int scoreB,
      Instant playedAt) {
    Match match = new Match();
    match.setA1(a1);
    match.setA2(a2);
    match.setB1(b1);
    match.setB2(b2);
    match.setSeason(season);
    match.setState(state);
    match.setScoreA(scoreA);
    match.setScoreB(scoreB);
    match.setPlayedAt(playedAt);
    return match;
  }

  private Match singlesMatch(
      User a1,
      User b1,
      LadderSeason season,
      MatchState state,
      int scoreA,
      int scoreB,
      Instant playedAt) {
    Match match = new Match();
    match.setA1(a1);
    match.setB1(b1);
    match.setSeason(season);
    match.setState(state);
    match.setScoreA(scoreA);
    match.setScoreB(scoreB);
    match.setPlayedAt(playedAt);
    return match;
  }

  private LadderSeason season(LadderSeason.State state) {
    LadderSeason season = new LadderSeason();
    season.setState(state);
    return season;
  }

  private User user(Long id) {
    return user(id, "User" + id);
  }

  private User user(Long id, String nickName) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickName);
    return user;
  }
}
