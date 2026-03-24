package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.w3llspring.fhpb.web.model.BadgeView;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.scoring.BalancedV1LadderScoringAlgorithm;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms;
import com.w3llspring.fhpb.web.service.scoring.MarginCurveV1LadderScoringAlgorithm;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LadderV2BadgeRowsTest {

  @Test
  void buildDisplayRowsIncludesBadgeViewsFromUserSlots() {
    LadderV2Service service =
        new LadderV2Service(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new SeasonNameGenerator(),
            new LadderScoringAlgorithms(
                List.of(
                    new MarginCurveV1LadderScoringAlgorithm(),
                    new BalancedV1LadderScoringAlgorithm())),
            null,
            null,
            null,
            null);

    Trophy badgeTrophy = new Trophy();
    ReflectionTestUtils.setField(badgeTrophy, "id", 901L);
    badgeTrophy.setTitle("Platinum Finish");

    User player = new User();
    player.setId(77L);
    player.setNickName("Badge Player");
    player.setBadgeSlot1Trophy(badgeTrophy);

    LadderStanding standing = new LadderStanding();
    standing.setUser(player);
    standing.setDisplayName("Badge Player");
    standing.setPoints(42);
    standing.setRank(1);

    List<LadderV2Service.LadderRow> rows = service.buildDisplayRows(List.of(standing));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).badgeViews)
        .extracting(BadgeView::getImageUrl, BadgeView::getLabel)
        .containsExactly(tuple("/trophies/badge/901", "Platinum Finish"));
  }
}
