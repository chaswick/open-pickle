package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderRatingChangeRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderRatingChange;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CoachToneLadderImprovementAdvisorTest {

  @Mock private LadderSeasonRepository seasonRepo;

  @Mock private LadderRatingChangeRepository ratingChangeRepo;

  private CoachToneLadderImprovementAdvisor advisor;

  @BeforeEach
  void setUp() {
    advisor =
        new CoachToneLadderImprovementAdvisor(
            seasonRepo,
            ratingChangeRepo,
            new com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms(
                List.of(
                    new com.w3llspring.fhpb.web.service.scoring
                        .MarginCurveV1LadderScoringAlgorithm(),
                    new com.w3llspring.fhpb.web.service.scoring
                        .BalancedV1LadderScoringAlgorithm())));
  }

  @Test
  void buildAdvice_returnsRecentExplainedRatingChanges() {
    User me = user(1L, "Me");
    LadderSeason season = new LadderSeason();
    LadderRatingChange change = new LadderRatingChange();
    change.setOccurredAt(Instant.parse("2026-03-10T15:00:00Z"));
    change.setSummary("Won 11-5: +6 rating (1000 -> 1006)");
    change.setDetails(
        String.join(
            "\n",
            "The 11-5 scoreline set the base swing at 6 because the win was 38% dominant across all points played.",
            "There were no guest discounts, so the full 6-point swing stayed in play.",
            "You were on the winning side, so the final result was +6."));

    when(ratingChangeRepo.findRecentBySeasonAndUser(
            eq(season), eq(me.getId()), any(Pageable.class)))
        .thenReturn(List.of(change));

    LadderImprovementAdvisor.Advice advice = advisor.buildAdvice(me, null, season);

    assertThat(advice.getQuickSummary()).contains("simpler than Elo or DUPR");
    assertThat(advice.getDetailedSummary()).contains("latest rating change");
    assertThat(advice.getActionTips())
        .anyMatch(tip -> tip.contains("stop feeding them easy points"))
        .anyMatch(tip -> tip.contains("opponent rating does not matter"));
    assertThat(advice.getRecentChanges()).hasSize(1);
    assertThat(advice.getRecentChanges().get(0).getOccurredAt())
        .isEqualTo(Instant.parse("2026-03-10T15:00:00Z"));
    assertThat(advice.getRecentChanges().get(0).getSummary())
        .isEqualTo("Won 11-5: +6 rating (1000 -> 1006)");
    assertThat(advice.getRecentChanges().get(0).getExplanationSteps())
        .contains("You were on the winning side, so the final result was +6.");
  }

  private static User user(Long id, String nickname) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickname);
    user.setEmail(nickname.toLowerCase() + "@example.com");
    return user;
  }
}
