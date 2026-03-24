package com.w3llspring.fhpb.web.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MarginCurveV1LadderScoringAlgorithmTest {

  private final MarginCurveV1LadderScoringAlgorithm algorithm =
      new MarginCurveV1LadderScoringAlgorithm();

  @Test
  void computeBaseStep_rewardsShutoutsMuchMoreThanCloseGamesAcrossFormats() {
    assertThat(algorithm.computeBaseStep(match(11, 0))).isEqualTo(16);
    assertThat(algorithm.computeBaseStep(match(9, 0))).isEqualTo(16);
    assertThat(algorithm.computeBaseStep(match(11, 5))).isEqualTo(6);
    assertThat(algorithm.computeBaseStep(match(11, 9))).isEqualTo(3);
    assertThat(algorithm.computeBaseStep(match(12, 10))).isEqualTo(3);
    assertThat(algorithm.computeBaseStep(match(22, 20))).isEqualTo(2);
  }

  @Test
  void score_appliesSymmetricDeltaToEligiblePlayers() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    Match match = match(11, 5);
    match.setA1(a1);
    match.setB1(b1);

    LadderScoringResult result = algorithm.score(request(match));

    assertThat(result.deltaFor(a1)).isEqualTo(6);
    assertThat(result.deltaFor(b1)).isEqualTo(-6);
    assertThat(result.explanationFor(a1).getSteps())
        .contains(
            "The 11-5 scoreline set the initial rating change at 6 because it was a wide score gap between the teams. Narrow gaps move ratings a little, wide gaps move them more, and huge gaps move them the most.");
    assertThat(result.explanationFor(a1).getSteps())
        .contains("There was no guest involvement, so the full 6-point change remained.");
    assertThat(result.explanationFor(a1).getSteps())
        .contains(
            "Variety adjustment stayed neutral because there were no prior ladder matches in the recent sample.");
    assertThat(result.explanationFor(a1).getSteps())
        .contains("No streak bonus applied because your current win streak is 1.");
  }

  @Test
  void score_returnsEmptyForExcludedMatches() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    Match match = match(11, 5);
    match.setA1(a1);
    match.setB1(b1);
    match.setExcludeFromStandings(true);

    LadderScoringResult result = algorithm.score(request(match));

    assertThat(result.getAdjustments()).isEmpty();
  }

  @Test
  void score_scalesGuestHeavyMatchesDown() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    Match match = match(11, 5);
    match.setA1(a1);
    match.setB1(b1);
    match.setA2Guest(true);

    LadderScoringResult result = algorithm.score(request(match));

    assertThat(result.getGuestScale()).isEqualTo(0.6);
    assertThat(result.deltaFor(a1)).isEqualTo(4);
    assertThat(result.deltaFor(b1)).isEqualTo(-4);
  }

  @Test
  void score_keepsVarietyNeutralWhenOnlyOneMeaningfulRecentMatchExists() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    Match current = match(11, 5);
    current.setA1(a1);
    current.setB1(b1);
    current.setCreatedAt(Instant.parse("2026-03-11T12:00:00Z"));

    List<Match> history = List.of(singlesMatch(11, 8, a1, user(11L, "O1"), "2026-03-10T12:00:00Z"));

    LadderScoringResult result = algorithm.score(request(current, history));

    assertThat(result.deltaFor(a1)).isEqualTo(6);
    assertThat(result.explanationFor(a1).getSteps())
        .contains(
            "Variety adjustment stayed neutral because there was only 1 recent match to compare for variety.");
  }

  @Test
  void score_canDoubleDeltaForPlayersWithHighRecentVariety() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    Match current = match(11, 5);
    current.setA1(a1);
    current.setB1(b1);
    current.setCreatedAt(Instant.parse("2026-03-11T12:00:00Z"));

    List<Match> history =
        List.of(
            singlesMatch(8, 11, a1, user(11L, "O1"), "2026-03-10T12:00:00Z"),
            singlesMatch(11, 9, a1, user(12L, "O2"), "2026-03-09T12:00:00Z"),
            singlesMatch(11, 6, a1, user(13L, "O3"), "2026-03-08T12:00:00Z"),
            singlesMatch(11, 7, a1, user(14L, "O4"), "2026-03-07T12:00:00Z"),
            singlesMatch(11, 5, a1, user(15L, "O5"), "2026-03-06T12:00:00Z"));

    LadderScoringResult result = algorithm.score(request(current, history));

    assertThat(result.deltaFor(a1)).isEqualTo(12);
    assertThat(result.deltaFor(b1)).isEqualTo(-6);
    assertThat(result.explanationFor(a1).getSteps())
        .anyMatch(step -> step.contains("variety added 100%"));
  }

  @Test
  void score_keepsRepeatedSamePodInTheRepeatFloorWhenRolesNeverChange() {
    User me = user(1L, "Me");
    User opponent = user(2L, "Opponent");
    User partner = user(11L, "Partner");
    User oppOne = user(12L, "OppOne");
    User oppTwo = user(13L, "OppTwo");
    Match current = match(11, 5);
    current.setA1(me);
    current.setB1(opponent);
    current.setCreatedAt(Instant.parse("2026-03-11T12:00:00Z"));

    List<Match> history =
        List.of(
            doublesMatch(me, partner, oppOne, oppTwo, 7, 11, "2026-03-10T12:00:00Z"),
            doublesMatch(me, partner, oppOne, oppTwo, 11, 8, "2026-03-09T12:00:00Z"),
            doublesMatch(me, partner, oppOne, oppTwo, 11, 7, "2026-03-08T12:00:00Z"));

    LadderScoringResult result = algorithm.score(request(current, history));

    assertThat(result.deltaFor(me)).isEqualTo(6);
    assertThat(result.explanationFor(me).getSteps())
        .anyMatch(
            step ->
                step.contains(
                    "filled 3 unique real partner/opponent slots across 9 possible partner/opponent seats"))
        .anyMatch(step -> step.contains("variety added 0%"));
  }

  @Test
  void score_countsRoleRotationWithinTheSamePodAsAdditionalVariety() {
    User me = user(1L, "Me");
    User opponent = user(2L, "Opponent");
    User a = user(11L, "A");
    User b = user(12L, "B");
    User c = user(13L, "C");
    Match current = match(11, 5);
    current.setA1(me);
    current.setB1(opponent);
    current.setCreatedAt(Instant.parse("2026-03-11T12:00:00Z"));

    List<Match> history =
        List.of(
            doublesMatch(me, a, b, c, 7, 11, "2026-03-10T12:00:00Z"),
            doublesMatch(me, c, a, b, 11, 8, "2026-03-09T12:00:00Z"),
            doublesMatch(me, b, a, c, 11, 7, "2026-03-08T12:00:00Z"));

    LadderScoringResult result = algorithm.score(request(current, history));

    assertThat(result.deltaFor(me)).isEqualTo(11);
    assertThat(result.explanationFor(me).getSteps())
        .anyMatch(
            step ->
                step.contains(
                    "filled 6 unique real partner/opponent slots across 9 possible partner/opponent seats"))
        .anyMatch(step -> step.contains("Credit for 'full variety' kicks in around 7 seats"))
        .anyMatch(step -> step.contains("variety added 91%"));
  }

  @Test
  void score_doesNotIncreaseLossesForPlayersWithHighRecentVariety() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    Match current = match(11, 5);
    current.setA1(a1);
    current.setB1(b1);
    current.setCreatedAt(Instant.parse("2026-03-11T12:00:00Z"));

    List<Match> history =
        List.of(
            singlesMatch(8, 11, b1, user(11L, "O1"), "2026-03-10T12:00:00Z"),
            singlesMatch(11, 9, b1, user(12L, "O2"), "2026-03-09T12:00:00Z"),
            singlesMatch(11, 6, b1, user(13L, "O3"), "2026-03-08T12:00:00Z"),
            singlesMatch(11, 7, b1, user(14L, "O4"), "2026-03-07T12:00:00Z"),
            singlesMatch(11, 5, b1, user(15L, "O5"), "2026-03-06T12:00:00Z"));

    LadderScoringResult result = algorithm.score(request(current, history));

    assertThat(result.deltaFor(b1)).isEqualTo(-6);
    assertThat(result.explanationFor(b1).getSteps())
        .contains(
            "Recent variety was measured from your last 5 matches, but it did not boost this result because variety only adds upside on wins.");
  }

  @Test
  void score_reachesFullVarietyBonusBeforeTheoreticalMaximum() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    Match current = match(11, 5);
    current.setA1(a1);
    current.setB1(b1);
    current.setCreatedAt(Instant.parse("2026-03-11T12:00:00Z"));

    List<Match> history =
        List.of(
            doublesMatch(
                a1,
                user(101L, "P1"),
                user(102L, "O1"),
                user(103L, "O2"),
                7,
                11,
                "2026-03-10T12:00:00Z"),
            doublesMatch(
                a1,
                user(104L, "P2"),
                user(105L, "O3"),
                user(106L, "O4"),
                11,
                7,
                "2026-03-09T12:00:00Z"),
            doublesMatch(
                a1,
                user(107L, "P3"),
                user(108L, "O5"),
                user(109L, "O6"),
                11,
                7,
                "2026-03-08T12:00:00Z"),
            doublesMatch(
                a1,
                user(110L, "P4"),
                user(111L, "O7"),
                user(112L, "O8"),
                11,
                7,
                "2026-03-07T12:00:00Z"),
            doublesMatch(
                a1,
                user(101L, "P1"),
                user(102L, "O1"),
                user(113L, "O9"),
                11,
                7,
                "2026-03-06T12:00:00Z"));

    LadderScoringResult result = algorithm.score(request(current, history));

    assertThat(result.deltaFor(a1)).isEqualTo(12);
    assertThat(result.explanationFor(a1).getSteps())
        .anyMatch(step -> step.contains("Credit for 'full variety' kicks in around 11"));
    assertThat(result.explanationFor(a1).getSteps())
        .anyMatch(step -> step.contains("variety added 100%"));
  }

  @Test
  void score_keepsGuestSeatsInVarietyTargetInsteadOfLoweringTheBar() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    Match current = match(11, 5);
    current.setA1(a1);
    current.setB1(b1);
    current.setCreatedAt(Instant.parse("2026-03-11T12:00:00Z"));

    Match mostRecent =
        doublesMatchWithGuest(a1, user(101L, "P1"), user(102L, "O1"), "2026-03-10T12:00:00Z");
    mostRecent.setScoreA(7);
    mostRecent.setScoreB(11);
    Match second =
        doublesMatchWithGuest(a1, user(103L, "P2"), user(104L, "O2"), "2026-03-09T12:00:00Z");
    Match third =
        doublesMatchWithGuest(a1, user(105L, "P3"), user(106L, "O3"), "2026-03-08T12:00:00Z");

    List<Match> history = List.of(mostRecent, second, third);

    LadderScoringResult result = algorithm.score(request(current, history));

    assertThat(result.deltaFor(a1)).isEqualTo(11);
    assertThat(result.explanationFor(a1).getSteps())
        .anyMatch(
            step ->
                step.contains(
                    "filled 6 unique real partner/opponent slots across 9 possible partner/opponent seats"))
        .anyMatch(step -> step.contains("Credit for 'full variety' kicks in around 7 seats"))
        .anyMatch(step -> step.contains("variety added 91%"));
  }

  @Test
  void score_appliesStreakBonusAtThreeWins() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    User repeatOpponent = user(11L, "O1");
    Match current = match(11, 0);
    current.setA1(a1);
    current.setB1(b1);
    current.setCreatedAt(Instant.parse("2026-03-11T12:00:00Z"));

    List<Match> history =
        List.of(
            singlesMatch(11, 7, a1, repeatOpponent, "2026-03-10T12:00:00Z"),
            singlesMatch(11, 8, a1, repeatOpponent, "2026-03-09T12:00:00Z"));

    LadderScoringResult result = algorithm.score(request(current, history));

    assertThat(result.deltaFor(a1)).isEqualTo(24);
    assertThat(result.deltaFor(b1)).isEqualTo(-16);
    assertThat(result.explanationFor(a1).getSteps())
        .anyMatch(step -> step.contains("current win streak reached 3"));
    assertThat(result.explanationFor(a1).getSteps())
        .anyMatch(step -> step.contains("streak bonus added 50%"))
        .anyMatch(
            step -> step.contains("moved the rating change from 16 to 24 before final rounding."));
  }

  @Test
  void score_explanation_keepsIntermediateValuesConsistentWithFinalRoundedDelta() {
    User a1 = user(1L, "A1");
    User b1 = user(2L, "B1");
    User repeatOpponent = user(11L, "O1");
    Match current = match(11, 9);
    current.setA1(a1);
    current.setB1(b1);
    current.setA2Guest(true);
    current.setB2Guest(true);
    current.setCreatedAt(Instant.parse("2026-03-11T12:00:00Z"));

    List<Match> history =
        List.of(
            singlesMatch(11, 7, a1, repeatOpponent, "2026-03-10T12:00:00Z"),
            singlesMatch(11, 8, a1, repeatOpponent, "2026-03-09T12:00:00Z"),
            singlesMatch(11, 9, a1, repeatOpponent, "2026-03-08T12:00:00Z"),
            singlesMatch(11, 5, a1, repeatOpponent, "2026-03-07T12:00:00Z"));

    LadderScoringResult result = algorithm.score(request(current, history));

    assertThat(result.deltaFor(a1)).isEqualTo(2);
    assertThat(result.explanationFor(a1).getSteps())
        .contains("Guest involvement scaled that change to 40%, reducing it from 3 to 1.2.")
        .contains(
            "Across your last 4 matches, you filled 1 unique real partner/opponent slots across 4 possible partner/opponent seats. Credit for 'full variety' kicks in around 3 seats, so variety added 0% and adjusted the change from 1.2 to 1.2.")
        .contains(
            "Your current win streak reached 5, so the streak bonus added 100% and moved the rating change from 1.2 to 2.4 before final rounding.")
        .contains("You were on the winning side, so the final rating change was +2.");
  }

  private static LadderScoringRequest request(Match match) {
    return request(match, List.of());
  }

  private static LadderScoringRequest request(Match match, List<Match> history) {
    return new LadderScoringRequest(
        match, Map.of(), Map.of(), Map.of(), 1, null, Map.of(), history);
  }

  private static Match match(int scoreA, int scoreB) {
    Match match = new Match();
    match.setScoreA(scoreA);
    match.setScoreB(scoreB);
    return match;
  }

  private static Match singlesMatch(int scoreA, int scoreB, User a1, User b1, String createdAt) {
    Match match = match(scoreA, scoreB);
    match.setA1(a1);
    match.setB1(b1);
    match.setCreatedAt(Instant.parse(createdAt));
    return match;
  }

  private static Match doublesMatch(
      User a1, User a2, User b1, User b2, int scoreA, int scoreB, String createdAt) {
    Match match = match(scoreA, scoreB);
    match.setA1(a1);
    match.setA2(a2);
    match.setB1(b1);
    match.setB2(b2);
    match.setCreatedAt(Instant.parse(createdAt));
    return match;
  }

  private static Match doublesMatchWithGuest(User a1, User a2, User b1, String createdAt) {
    Match match = match(11, 7);
    match.setA1(a1);
    match.setA2(a2);
    match.setB1(b1);
    match.setB2Guest(true);
    match.setCreatedAt(Instant.parse(createdAt));
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
