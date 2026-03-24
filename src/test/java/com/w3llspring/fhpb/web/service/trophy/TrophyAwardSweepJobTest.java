package com.w3llspring.fhpb.web.service.trophy;

import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.service.jobs.trophy.TrophyAwardSweepJob;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TrophyAwardSweepJobTest {

  @Mock private LadderSeasonRepository seasonRepository;

  private RecordingTrophyAwardService trophyAwardService;
  private RecordingAutoTrophyService autoTrophyService;

  private TrophyAwardSweepJob job;

  @BeforeEach
  void setUp() {
    trophyAwardService = new RecordingTrophyAwardService();
    autoTrophyService = new RecordingAutoTrophyService();
    job = new TrophyAwardSweepJob(seasonRepository, trophyAwardService, autoTrophyService, 30);
  }

  @Test
  void runActiveSweep_backfillsSeasonTrophiesBeforeEvaluation() {
    LadderSeason active = seasonWithId(28L, LadderSeason.State.ACTIVE, LocalDate.now(), null);
    when(seasonRepository.findByStateOrderByStartDateDesc(LadderSeason.State.ACTIVE))
        .thenReturn(List.of(active));

    job.runActiveSweep();

    org.assertj.core.api.Assertions.assertThat(autoTrophyService.generated).containsExactly(active);
    org.assertj.core.api.Assertions.assertThat(trophyAwardService.evaluated)
        .containsExactly(active);
  }

  @Test
  void runRecentEndedSweep_skipsSeasonsOutsideRetentionWindow() {
    Instant now = Instant.now();
    LadderSeason recent =
        seasonWithId(
            30L,
            LadderSeason.State.ENDED,
            LocalDate.now().minusDays(5),
            now.minusSeconds(5 * 24 * 3600));
    LadderSeason stale =
        seasonWithId(
            10L,
            LadderSeason.State.ENDED,
            LocalDate.now().minusDays(90),
            now.minusSeconds(90 * 24 * 3600));

    when(seasonRepository.findByStateOrderByStartDateDesc(LadderSeason.State.ENDED))
        .thenReturn(List.of(recent, stale));

    job.runRecentEndedSweep();

    org.assertj.core.api.Assertions.assertThat(autoTrophyService.generated).containsExactly(recent);
    org.assertj.core.api.Assertions.assertThat(trophyAwardService.evaluated)
        .containsExactly(recent);
  }

  private LadderSeason seasonWithId(
      Long id, LadderSeason.State state, LocalDate endDate, Instant endedAt) {
    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", id);
    season.setState(state);
    LocalDate start = endDate.minusWeeks(6).plusDays(1);
    season.setStartDate(start);
    season.setEndDate(endDate);
    season.setEndedAt(endedAt);
    return season;
  }

  private static class RecordingTrophyAwardService extends TrophyAwardService {
    private final List<LadderSeason> evaluated = new ArrayList<>();

    private RecordingTrophyAwardService() {
      super(null, null, null, null, null, null, null, null);
    }

    @Override
    public int evaluateSeasonSweep(LadderSeason season) {
      evaluated.add(season);
      return 0;
    }
  }

  private static class RecordingAutoTrophyService extends AutoTrophyService {
    private final List<LadderSeason> generated = new ArrayList<>();

    private RecordingAutoTrophyService() {
      super(null, null, null, null);
    }

    @Override
    public void generateSeasonTrophies(LadderSeason season) {
      generated.add(season);
    }
  }
}
