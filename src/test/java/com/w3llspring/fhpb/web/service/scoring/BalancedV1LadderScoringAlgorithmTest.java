package com.w3llspring.fhpb.web.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BalancedV1LadderScoringAlgorithmTest {

  private final BalancedV1LadderScoringAlgorithm algorithm = new BalancedV1LadderScoringAlgorithm();

  @Test
  void score_ignoresIntegrityWeightsInsideTheAlgorithm() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    Match match = match(11, 0);
    match.setA1(a1);
    match.setB1(b1);

    LadderScoringResult result =
        algorithm.score(
            new LadderScoringRequest(
                match,
                Map.of(),
                Map.of(),
                Map.of(),
                1,
                99L,
                Map.of(a1.getId(), 0.5d, b1.getId(), 0.5d),
                List.of()));

    assertThat(result.deltaFor(a1)).isEqualTo(2);
    assertThat(result.deltaFor(b1)).isEqualTo(-2);
    assertThat(result.explanationFor(a1).getSteps())
        .noneMatch(step -> step.contains("Player-integrity weighting"));
  }

  @Test
  void score_returnsEmptyForExcludedMatches() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    Match match = match(11, 5);
    match.setA1(a1);
    match.setB1(b1);
    match.setExcludeFromStandings(true);

    LadderScoringResult result =
        algorithm.score(
            new LadderScoringRequest(
                match, Map.of(), Map.of(), Map.of(), 1, 99L, Map.of(), List.of()));

    assertThat(result.getAdjustments()).isEmpty();
  }

  private static Match match(int scoreA, int scoreB) {
    Match match = new Match();
    match.setScoreA(scoreA);
    match.setScoreB(scoreB);
    return match;
  }

  private static User user(Long id, String nickname) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickname);
    user.setEmail(nickname.toLowerCase() + "@example.com");
    return user;
  }
}
