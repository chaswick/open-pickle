package com.w3llspring.fhpb.web.service.trophy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.TrophyCatalogEntryRepository;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.db.UserTrophyRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyCatalogEntry;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserTrophy;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TrophyCatalogServiceTest {

  @Mock private LadderSeasonRepository seasonRepository;

  @Mock private TrophyRepository trophyRepository;

  @Mock private TrophyCatalogEntryRepository trophyCatalogEntryRepository;

  @Mock private UserTrophyRepository userTrophyRepository;

  @Mock private LadderMembershipRepository membershipRepository;

  @Mock private LadderStandingRepository ladderStandingRepository;

  private TrophyCatalogService service;

  @BeforeEach
  void setUp() {
    service =
        new TrophyCatalogService(
            seasonRepository,
            trophyCatalogEntryRepository,
            trophyRepository,
            userTrophyRepository,
            membershipRepository,
            ladderStandingRepository,
            null);
  }

  @Test
  void fetchSeasonCatalog_showsEarnedCountForRepeatableTrophyWithoutInflatingOwnerCount() {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(8L);
    ladder.setTitle("Story Ladder");

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 55L);
    season.setLadderConfig(ladder);
    season.setName("Spring Story");
    season.setStartDate(LocalDate.of(2026, 3, 1));
    season.setEndDate(LocalDate.of(2026, 4, 12));
    season.setState(LadderSeason.State.ACTIVE);

    Trophy trophy = new Trophy();
    ReflectionTestUtils.setField(trophy, "id", 21L);
    trophy.setSeason(season);
    trophy.setTitle("You helped Pat find his keys");
    trophy.setSummary("Key helper");
    trophy.setUnlockCondition("Help Pat find the keys.");
    trophy.setRepeatable(true);

    User viewer = new User();
    ReflectionTestUtils.setField(viewer, "id", 77L);
    viewer.setNickName("Viewer");

    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(ladder);
    membership.setUserId(77L);
    membership.setState(LadderMembership.State.ACTIVE);

    User otherUser = new User();
    ReflectionTestUtils.setField(otherUser, "id", 88L);
    otherUser.setNickName("Other");

    when(seasonRepository.findById(55L)).thenReturn(Optional.of(season));
    when(membershipRepository.findByUserIdAndState(77L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership));
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(trophy));
    when(trophyCatalogEntryRepository
            .findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc())
        .thenReturn(List.of());
    when(trophyCatalogEntryRepository.findByDefaultTemplateTrueAndSeasonOrderByDisplayOrderAscIdAsc(
            season))
        .thenReturn(List.of());
    when(userTrophyRepository.findByUserAndTrophyIn(viewer, List.of(trophy)))
        .thenReturn(List.of(grant(viewer, trophy, 3)));
    when(userTrophyRepository.countOwnersByTrophyIn(List.of(trophy)))
        .thenReturn(List.of(ownerCount(21L, 2L)));

    TrophyCatalog catalog = service.fetchSeasonCatalog(viewer, 55L).orElseThrow();
    TrophyCardModel card = catalog.getCards().get(0);

    assertThat(card.isOwnedByUser()).isTrue();
    assertThat(card.isRepeatable()).isTrue();
    assertThat(card.getEarnedCount()).isEqualTo(3);
    assertThat(card.getOwnerCount()).isEqualTo(2);
  }

  @Test
  void fetchSeasonCatalog_storySeasonShowsOnlyStoryTrackers() {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(8L);
    ladder.setTitle("Story Ladder");

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 56L);
    season.setLadderConfig(ladder);
    season.setName("Spring Story");
    season.setStartDate(LocalDate.of(2026, 3, 1));
    season.setEndDate(LocalDate.of(2026, 4, 12));
    season.setState(LadderSeason.State.ACTIVE);
    season.setStoryModeEnabled(true);

    Trophy regular = new Trophy();
    ReflectionTestUtils.setField(regular, "id", 21L);
    regular.setSeason(season);
    regular.setTitle("Play 3 matches");
    regular.setSummary("Regular trophy");
    regular.setUnlockCondition("Play 3 matches.");

    Trophy story = new Trophy();
    ReflectionTestUtils.setField(story, "id", 22L);
    story.setSeason(season);
    story.setTitle("Pat gets off the couch");
    story.setSummary("Story trophy");
    story.setUnlockCondition("Reach the story threshold.");
    story.setStoryModeTracker(true);

    User viewer = new User();
    ReflectionTestUtils.setField(viewer, "id", 77L);
    viewer.setNickName("Viewer");

    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(ladder);
    membership.setUserId(77L);
    membership.setState(LadderMembership.State.ACTIVE);

    when(seasonRepository.findById(56L)).thenReturn(Optional.of(season));
    when(membershipRepository.findByUserIdAndState(77L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership));
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(regular, story));
    when(userTrophyRepository.findByUserAndTrophyIn(viewer, List.of(story))).thenReturn(List.of());
    when(userTrophyRepository.countOwnersByTrophyIn(List.of(story))).thenReturn(List.of());

    TrophyCatalog catalog = service.fetchSeasonCatalog(viewer, 56L).orElseThrow();

    assertThat(catalog.getCards())
        .extracting(TrophyCardModel::getTitle)
        .containsExactly("Pat gets off the couch");
  }

  @Test
  void fetchSeasonCatalog_activeSeasonOmitsBandFinishDefaultTemplates() {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(9L);
    ladder.setTitle("Competition");

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 57L);
    season.setLadderConfig(ladder);
    season.setName("Spring Ladder");
    season.setStartDate(LocalDate.of(2026, 3, 1));
    season.setEndDate(LocalDate.of(2026, 4, 12));
    season.setState(LadderSeason.State.ACTIVE);

    TrophyCatalogEntry regular = new TrophyCatalogEntry();
    ReflectionTestUtils.setField(regular, "id", 31L);
    regular.setTitle("Play 3 matches");
    regular.setSummary("Regular trophy");
    regular.setUnlockCondition("Play 3 matches.");
    regular.setGenerationSeed("seed-regular");

    TrophyCatalogEntry bandFinish = new TrophyCatalogEntry();
    ReflectionTestUtils.setField(bandFinish, "id", 32L);
    bandFinish.setTitle("Diamond Division Finish");
    bandFinish.setSummary("Band finish");
    bandFinish.setUnlockCondition("Finish the season in Diamond Division");
    bandFinish.setUnlockExpression("final_band_index == 1");
    bandFinish.setGenerationSeed("seed-band");

    User viewer = new User();
    ReflectionTestUtils.setField(viewer, "id", 77L);
    viewer.setNickName("Viewer");

    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(ladder);
    membership.setUserId(77L);
    membership.setState(LadderMembership.State.ACTIVE);

    when(seasonRepository.findById(57L)).thenReturn(Optional.of(season));
    when(membershipRepository.findByUserIdAndState(77L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership));
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season)).thenReturn(List.of());
    when(trophyCatalogEntryRepository
            .findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc())
        .thenReturn(List.of(regular, bandFinish));
    when(trophyCatalogEntryRepository.findByDefaultTemplateTrueAndSeasonOrderByDisplayOrderAscIdAsc(
            season))
        .thenReturn(List.of());

    TrophyCatalog catalog = service.fetchSeasonCatalog(viewer, 57L).orElseThrow();

    assertThat(catalog.getCards())
        .extracting(TrophyCardModel::getTitle)
        .containsExactly("Play 3 matches");
  }

  @Test
  void fetchSeasonCatalog_relabelsLegacyBandFinishTrophyToCanonicalSeasonSizeName() {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(10L);
    ladder.setTitle("Competition");

    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 58L);
    season.setLadderConfig(ladder);
    season.setName("Spring Ladder");
    season.setStartDate(LocalDate.of(2026, 3, 1));
    season.setEndDate(LocalDate.of(2026, 4, 12));
    season.setState(LadderSeason.State.ENDED);

    Trophy bandFinish = new Trophy();
    ReflectionTestUtils.setField(bandFinish, "id", 41L);
    bandFinish.setSeason(season);
    bandFinish.setTitle("Crown Division Finish");
    bandFinish.setSummary("Legacy summary");
    bandFinish.setUnlockCondition("Finish the season in Crown Division");
    bandFinish.setUnlockExpression("final_band_index == 1");

    when(seasonRepository.findById(58L)).thenReturn(Optional.of(season));
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(bandFinish));
    when(userTrophyRepository.countOwnersByTrophyIn(List.of(bandFinish))).thenReturn(List.of());

    TrophyCatalog catalog = service.fetchSeasonCatalog(null, 58L).orElseThrow();

    assertThat(catalog.getCards())
        .extracting(TrophyCardModel::getTitle, TrophyCardModel::getUnlockCondition)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "Diamond Division Finish", "Finish the season in Diamond Division"));
  }

  private UserTrophy grant(User user, Trophy trophy, int awardCount) {
    UserTrophy userTrophy = new UserTrophy();
    userTrophy.setUser(user);
    userTrophy.setTrophy(trophy);
    userTrophy.setAwardCount(awardCount);
    return userTrophy;
  }

  private UserTrophyRepository.TrophyOwnerCount ownerCount(Long trophyId, long owners) {
    return new UserTrophyRepository.TrophyOwnerCount() {
      @Override
      public Long getTrophyId() {
        return trophyId;
      }

      @Override
      public long getOwners() {
        return owners;
      }
    };
  }
}
