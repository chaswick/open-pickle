package com.w3llspring.fhpb.web.service.trophy;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TrophyAwardServiceLazyLoadingIntegrationTest {

  @Autowired private LadderConfigRepository ladderConfigRepository;

  @Autowired private LadderSeasonRepository ladderSeasonRepository;

  @Autowired private TrophyAwardService trophyAwardService;

  @Autowired private EntityManager entityManager;

  @Test
  void evaluateSeasonSweepReloadsDetachedSeasonBeforeCheckingSecurityLevel() {
    LadderConfig config = new LadderConfig();
    config.setTitle("Detached Season Ladder");
    config.setOwnerUserId(1L);
    config.setInviteCode("DETACHED-SWEEP");
    config.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    config = ladderConfigRepository.saveAndFlush(config);

    LadderSeason season = new LadderSeason();
    season.setLadderConfig(config);
    season.setName("Detached Season");
    season.setStartDate(LocalDate.now().minusDays(7));
    season.setEndDate(LocalDate.now().plusDays(7));
    season.setState(LadderSeason.State.ACTIVE);
    ladderSeasonRepository.saveAndFlush(season);

    entityManager.clear();

    List<LadderSeason> detachedSeasons =
        ladderSeasonRepository.findByStateOrderByStartDateDesc(LadderSeason.State.ACTIVE);
    assertThat(detachedSeasons).isNotEmpty();

    int awarded = trophyAwardService.evaluateSeasonSweep(detachedSeasons.get(0));

    assertThat(awarded).isZero();
  }
}
