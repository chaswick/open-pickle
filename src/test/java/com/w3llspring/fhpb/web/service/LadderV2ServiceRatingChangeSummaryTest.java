package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.model.LadderRatingChange;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringExplanation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LadderV2ServiceRatingChangeSummaryTest {

  private final LadderV2Service service =
      new LadderV2Service(
          null, null, null, null, null, null, null, null, null, null, null, null, null, null);

  @Test
  void buildRatingChangeSummary_includesPartnerOpponentsAndMatchIdForDoubles() {
    User me = user(1L, "Me");
    User partner = user(2L, "Casey");
    User opponentOne = user(3L, "Drew");
    User opponentTwo = user(4L, "Evan");

    Match match = new Match();
    ReflectionTestUtils.setField(match, "id", 29L);
    match.setA1(me);
    match.setA2(partner);
    match.setB1(opponentOne);
    match.setB2(opponentTwo);
    match.setScoreA(11);
    match.setScoreB(8);

    String summary =
        ReflectionTestUtils.invokeMethod(
            service, "buildRatingChangeSummary", match, me, 3, 1010, 1013);

    assertThat(summary)
        .isEqualTo(
            "Won 11-8 with Casey against Drew and Evan (match #29): +3 rating (1010 -> 1013)");
  }

  @Test
  void buildRatingChangeSummary_omitsPartnerClauseForSingles() {
    User me = user(1L, "Me");
    User opponent = user(3L, "Drew");

    Match match = new Match();
    ReflectionTestUtils.setField(match, "id", 30L);
    match.setA1(me);
    match.setB1(opponent);
    match.setScoreA(8);
    match.setScoreB(11);

    String summary =
        ReflectionTestUtils.invokeMethod(
            service, "buildRatingChangeSummary", match, me, -2, 1013, 1011);

    assertThat(summary).isEqualTo("Lost 8-11 against Drew (match #30): -2 rating (1013 -> 1011)");
  }

  @Test
  @SuppressWarnings("unchecked")
  void buildRatingChanges_persistsDetailsOnlyForActualAdjustments() {
    User me = user(1L, "Me");
    User opponent = user(2L, "Drew");

    LadderSeason season = new LadderSeason();
    Match match = new Match();
    match.setA1(me);
    match.setB1(opponent);
    match.setScoreA(11);
    match.setScoreB(8);

    LadderStanding standing = new LadderStanding();
    standing.setUser(me);
    standing.setPoints(12);

    List<LadderRatingChange> changes =
        (List<LadderRatingChange>)
            ReflectionTestUtils.invokeMethod(
                service,
                "buildRatingChanges",
                season,
                match,
                Map.of(me, 3),
                Map.of(
                    me.getId(), new LadderScoringExplanation(List.of("Base step.", "Final +3.")),
                    opponent.getId(), new LadderScoringExplanation(List.of("Unused explanation."))),
                Map.of(me.getId(), standing));

    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).getUser()).isEqualTo(me);
    assertThat(changes.get(0).getRatingDelta()).isEqualTo(3);
    assertThat(changes.get(0).getDetails()).isEqualTo("Base step.\nFinal +3.");
  }

  private static User user(Long id, String nickname) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickname);
    user.setEmail(nickname.toLowerCase() + "@example.com");
    return user;
  }
}
