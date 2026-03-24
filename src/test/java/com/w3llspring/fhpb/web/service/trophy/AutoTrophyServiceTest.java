package com.w3llspring.fhpb.web.service.trophy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.TrophyCatalogEntryRepository;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyArt;
import com.w3llspring.fhpb.web.model.TrophyCatalogEntry;
import com.w3llspring.fhpb.web.model.TrophyRarity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AutoTrophyServiceTest {

  @Mock private TrophyRepository trophyRepository;

  @Mock private TrophyCatalogEntryRepository trophyCatalogEntryRepository;

  @Mock private LadderStandingRepository ladderStandingRepository;

  @Captor private ArgumentCaptor<List<Trophy>> trophyListCaptor;

  private AutoTrophyService service;
  private RecordingStoryModeService storyModeService;

  @BeforeEach
  void setUp() {
    storyModeService = new RecordingStoryModeService();
    service =
        new AutoTrophyService(
            trophyCatalogEntryRepository,
            trophyRepository,
            ladderStandingRepository,
            storyModeService);
  }

  @Test
  void generateSeasonTrophies_addsMissingTemplateEvenWhenSeasonAlreadyHasTrophies() {
    LadderSeason season = seasonWithId(28L, LocalDate.of(2026, 2, 20));

    TrophyCatalogEntry templateA =
        defaultTemplate("Play 3 matches", "matches_played >= 3", "seed-a", 0);
    TrophyCatalogEntry templateB =
        defaultTemplate("Three day streak", "distinct_match_days >= 3", "seed-b", 1);
    Trophy existingA = seasonTrophy(season, "Play 3 matches", "matches_played >= 3", "seed-a");

    when(trophyCatalogEntryRepository
            .findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc())
        .thenReturn(List.of(templateA, templateB));
    when(trophyCatalogEntryRepository.findByDefaultTemplateTrueAndSeasonOrderByDisplayOrderAscIdAsc(
            season))
        .thenReturn(List.of());
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(existingA));
    when(trophyRepository.findBySlug(anyString())).thenReturn(Optional.empty());

    service.generateSeasonTrophies(season);

    verify(trophyRepository).saveAll(trophyListCaptor.capture());
    List<Trophy> persisted = trophyListCaptor.getValue();
    assertThat(persisted).hasSize(1);
    Trophy generated = persisted.get(0);
    assertThat(generated.getSeason()).isEqualTo(season);
    assertThat(generated.getGenerationSeed()).isEqualTo("seed-b");
    assertThat(generated.getUnlockExpression()).isEqualTo("distinct_match_days >= 3");
  }

  @Test
  void generateSeasonTrophies_skipsWhenTemplatesAlreadyExistInSeason() {
    LadderSeason season = seasonWithId(28L, LocalDate.of(2026, 2, 20));

    TrophyCatalogEntry templateA =
        defaultTemplate("Play 3 matches", "matches_played >= 3", "seed-a", 0);
    TrophyCatalogEntry templateB =
        defaultTemplate("Three day streak", "distinct_match_days >= 3", "seed-b", 1);
    Trophy existingA = seasonTrophy(season, "Play 3 matches", "matches_played >= 3", "seed-a");
    Trophy existingB =
        seasonTrophy(season, "Three day streak", "distinct_match_days >= 3", "seed-b");

    when(trophyCatalogEntryRepository
            .findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc())
        .thenReturn(List.of(templateA, templateB));
    when(trophyCatalogEntryRepository.findByDefaultTemplateTrueAndSeasonOrderByDisplayOrderAscIdAsc(
            season))
        .thenReturn(List.of());
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(existingA, existingB));

    service.generateSeasonTrophies(season);

    verify(trophyRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void generateSeasonTrophies_reusesTemplateArtReference() {
    LadderSeason season = seasonWithId(28L, LocalDate.of(2026, 2, 20));

    TrophyCatalogEntry template =
        defaultTemplate("Holiday Badge", "matches_played_on_03_17 >= 1", "seed-art", 0);
    TrophyArt art = new TrophyArt();
    ReflectionTestUtils.setField(art, "id", 44L);
    template.setArt(art);

    when(trophyCatalogEntryRepository
            .findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc())
        .thenReturn(List.of(template));
    when(trophyCatalogEntryRepository.findByDefaultTemplateTrueAndSeasonOrderByDisplayOrderAscIdAsc(
            season))
        .thenReturn(List.of());
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season)).thenReturn(List.of());
    when(trophyRepository.findBySlug(anyString())).thenReturn(Optional.empty());

    service.generateSeasonTrophies(season);

    verify(trophyRepository).saveAll(trophyListCaptor.capture());
    List<Trophy> persisted = trophyListCaptor.getValue();
    assertThat(persisted).hasSize(1);
    assertThat(persisted.get(0).getArt()).isSameAs(art);
    assertThat(persisted.get(0).getArtId()).isEqualTo(44L);
  }

  @Test
  void generateSeasonTrophies_stillEnsuresStoryTrackersWhenNoTemplatesExist() {
    LadderSeason season = seasonWithId(28L, LocalDate.of(2026, 2, 20));
    season.setStoryModeEnabled(true);

    service.generateSeasonTrophies(season);

    verify(trophyRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    assertThat(storyModeService.ensured).containsExactly(season);
  }

  @Test
  void generateSeasonTrophies_storySeasonSkipsDefaultTemplateGeneration() {
    LadderSeason season = seasonWithId(31L, LocalDate.of(2026, 3, 1));
    season.setStoryModeEnabled(true);

    service.generateSeasonTrophies(season);

    verify(trophyCatalogEntryRepository, never())
        .findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc();
    verify(trophyRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    assertThat(storyModeService.ensured).containsExactly(season);
  }

  @Test
  void generateSeasonTrophies_activeSeasonSkipsBandFinishTemplates() {
    LadderSeason season = seasonWithId(32L, LocalDate.of(2026, 3, 8));
    season.setState(LadderSeason.State.ACTIVE);

    TrophyCatalogEntry regular =
        defaultTemplate("Play 3 matches", "matches_played >= 3", "seed-a", 0);
    TrophyCatalogEntry bandFinish =
        defaultTemplate("Diamond Division Finish", "final_band_index == 1", "seed-band", 1);

    when(trophyCatalogEntryRepository
            .findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc())
        .thenReturn(List.of(regular, bandFinish));
    when(trophyCatalogEntryRepository.findByDefaultTemplateTrueAndSeasonOrderByDisplayOrderAscIdAsc(
            season))
        .thenReturn(List.of());
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season)).thenReturn(List.of());
    when(trophyRepository.findBySlug(anyString())).thenReturn(Optional.empty());

    service.generateSeasonTrophies(season);

    verify(trophyRepository).saveAll(trophyListCaptor.capture());
    assertThat(trophyListCaptor.getValue())
        .extracting(Trophy::getGenerationSeed)
        .containsExactly("seed-a");
  }

  @Test
  void generateSeasonTrophies_endedSeasonAddsOnlyBandTemplatesForFinalBandCount() {
    LadderSeason season = seasonWithId(33L, LocalDate.of(2026, 3, 8));
    season.setState(LadderSeason.State.ENDED);

    TrophyCatalogEntry diamond =
        defaultTemplate("Diamond Division Finish", "final_band_index == 1", "seed-diamond", 0);
    TrophyCatalogEntry goldThreeBand =
        defaultTemplate("Gold Division Finish", "final_band_index == 1", "seed-gold-b3", 1);
    TrophyCatalogEntry goldFiveBand =
        defaultTemplate("Gold Division Finish", "final_band_index == 3", "seed-gold-b5", 2);

    when(trophyCatalogEntryRepository
            .findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc())
        .thenReturn(List.of(diamond, goldThreeBand, goldFiveBand));
    when(trophyCatalogEntryRepository.findByDefaultTemplateTrueAndSeasonOrderByDisplayOrderAscIdAsc(
            season))
        .thenReturn(List.of());
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season)).thenReturn(List.of());
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(
            List.of(
                standing(1),
                standing(2),
                standing(3),
                standing(4),
                standing(5),
                standing(6),
                standing(7),
                standing(8),
                standing(9),
                standing(10)));
    when(trophyRepository.findBySlug(anyString())).thenReturn(Optional.empty());

    service.generateSeasonTrophies(season);

    verify(trophyRepository).saveAll(trophyListCaptor.capture());
    assertThat(trophyListCaptor.getValue())
        .extracting(Trophy::getGenerationSeed)
        .containsExactly("seed-gold-b3");
  }

  @Test
  void generateSeasonTrophies_legacyBandTemplateUsesCanonicalSeasonSizeLabel() {
    LadderSeason season = seasonWithId(35L, LocalDate.of(2026, 3, 8));
    season.setName("Spring");
    season.setState(LadderSeason.State.ENDED);

    TrophyCatalogEntry legacyTopFiveBand =
        defaultTemplate("Crown Division Finish", "final_band_index == 1", "seed-diamond", 0);

    when(trophyCatalogEntryRepository
            .findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc())
        .thenReturn(List.of(legacyTopFiveBand));
    when(trophyCatalogEntryRepository.findByDefaultTemplateTrueAndSeasonOrderByDisplayOrderAscIdAsc(
            season))
        .thenReturn(List.of());
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season)).thenReturn(List.of());
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(
            List.of(
                standing(1),
                standing(2),
                standing(3),
                standing(4),
                standing(5),
                standing(6),
                standing(7),
                standing(8),
                standing(9),
                standing(10),
                standing(11),
                standing(12),
                standing(13),
                standing(14),
                standing(15),
                standing(16),
                standing(17),
                standing(18),
                standing(19)));
    when(trophyRepository.findBySlug(anyString())).thenReturn(Optional.empty());

    service.generateSeasonTrophies(season);

    verify(trophyRepository).saveAll(trophyListCaptor.capture());
    Trophy generated = trophyListCaptor.getValue().get(0);
    assertThat(generated.getTitle()).isEqualTo("Diamond Division Finish");
    assertThat(generated.getUnlockCondition()).isEqualTo("Finish the season in Diamond Division");
    assertThat(generated.getSummary()).isEqualTo("Band finish accolade for the Spring season.");
  }

  @Test
  void generateSeasonTrophies_skipsRetiredOpenDivisionTemplate() {
    LadderSeason season = seasonWithId(34L, LocalDate.of(2026, 3, 8));
    season.setState(LadderSeason.State.ENDED);

    TrophyCatalogEntry retiredOpen =
        defaultTemplate("Open Division Finish", "final_band_index == 1", "seed-open", 0);

    when(trophyCatalogEntryRepository
            .findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc())
        .thenReturn(List.of(retiredOpen));
    when(trophyCatalogEntryRepository.findByDefaultTemplateTrueAndSeasonOrderByDisplayOrderAscIdAsc(
            season))
        .thenReturn(List.of());
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season)).thenReturn(List.of());
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(List.of(standing(1), standing(2), standing(3), standing(4)));

    service.generateSeasonTrophies(season);

    verify(trophyRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
  }

  private TrophyCatalogEntry defaultTemplate(
      String title, String expression, String seed, int displayOrder) {
    TrophyCatalogEntry trophy = new TrophyCatalogEntry();
    trophy.setTitle(title);
    trophy.setSummary(title + " summary");
    trophy.setUnlockCondition(title + " condition");
    trophy.setUnlockExpression(expression);
    trophy.setGenerationSeed(seed);
    trophy.setDefaultTemplate(true);
    trophy.setDisplayOrder(displayOrder);
    trophy.setRarity(TrophyRarity.COMMON);
    return trophy;
  }

  private Trophy seasonTrophy(LadderSeason season, String title, String expression, String seed) {
    Trophy trophy = new Trophy();
    trophy.setSeason(season);
    trophy.setTitle(title);
    trophy.setSummary(title + " summary");
    trophy.setUnlockCondition(title + " condition");
    trophy.setUnlockExpression(expression);
    trophy.setGenerationSeed(seed);
    trophy.setRarity(TrophyRarity.COMMON);
    return trophy;
  }

  private LadderStanding standing(int rank) {
    LadderStanding standing = new LadderStanding();
    standing.setRank(rank);
    return standing;
  }

  private LadderSeason seasonWithId(Long id, LocalDate startDate) {
    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", id);
    season.setStartDate(startDate);
    season.setEndDate(startDate.plusWeeks(6).minusDays(1));
    return season;
  }

  private static class RecordingStoryModeService
      extends com.w3llspring.fhpb.web.service.StoryModeService {
    private final java.util.List<LadderSeason> ensured = new java.util.ArrayList<>();

    private RecordingStoryModeService() {
      super(null, null, null, null, null, null);
    }

    @Override
    public void ensureTrackers(LadderSeason season) {
      ensured.add(season);
    }
  }
}
