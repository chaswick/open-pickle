package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.GroupTrophyRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.db.UserTrophyRepository;
import com.w3llspring.fhpb.web.model.GroupTrophy;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyEvaluationScope;
import com.w3llspring.fhpb.web.model.TrophyRarity;
import com.w3llspring.fhpb.web.model.TrophyStatus;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserTrophy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoryModeServiceTest {

  @Mock private TrophyRepository trophyRepository;
  @Mock private GroupTrophyRepository groupTrophyRepository;
  @Mock private UserTrophyRepository userTrophyRepository;
  @Mock private LadderMembershipRepository membershipRepository;
  @Mock private LadderSeasonRepository seasonRepository;
  @Captor private ArgumentCaptor<List<Trophy>> trophyCaptor;

  private StoryModeService service;
  private RecordingTrophyAwardService trophyAwardService;

  @BeforeEach
  void setUp() {
    trophyAwardService = new RecordingTrophyAwardService();
    service =
        new StoryModeService(
            trophyRepository,
            groupTrophyRepository,
            userTrophyRepository,
            membershipRepository,
            seasonRepository,
            trophyAwardService);
  }

  @Test
  void ensureTrackers_createsGroupScopedStoryTrophiesWithExpressions() {
    LadderSeason season = storySeason();
    when(trophyRepository.findBySeasonAndStoryModeTrackerTrueOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of());
    when(membershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            22L, LadderMembership.State.ACTIVE))
        .thenReturn(activeMemberships(season.getLadderConfig(), 6));

    service.ensureTrackers(season);

    verify(trophyRepository).saveAll(trophyCaptor.capture());
    List<Trophy> saved = trophyCaptor.getValue();
    assertThat(saved).hasSize(8);
    assertThat(saved).allMatch(Trophy::isStoryModeTracker);
    assertThat(saved).allMatch(t -> t.getEvaluationScope() == TrophyEvaluationScope.GROUP);
    assertThat(saved)
        .allMatch(t -> t.getUnlockExpression() != null && !t.getUnlockExpression().isBlank());
    assertThat(saved.stream().filter(Trophy::isRepeatable).map(Trophy::getStoryModeKey).toList())
        .containsExactlyInAnyOrder(
            StoryModeService.TASK_LAUNDRY,
            StoryModeService.TASK_KEYS,
            StoryModeService.TASK_SNACKS);
  }

  @Test
  void buildPage_readsStoryStateFromAwardedGroupTrophies() {
    LadderSeason season = storySeason();
    User viewer = user(1L, "Viewer");
    List<Trophy> trackers = trackers(season);

    when(trophyRepository.findBySeasonAndStoryModeTrackerTrueOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(trackers);
    when(membershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            22L, LadderMembership.State.ACTIVE))
        .thenReturn(activeMemberships(season.getLadderConfig(), 6));
    when(groupTrophyRepository.findBySeasonAndTrophyIn(season, trackers))
        .thenReturn(
            List.of(
                groupAward(season, trackers.get(0)),
                groupAward(season, trackers.get(1)),
                groupAward(season, trackers.get(2)),
                groupAward(season, trackers.get(3)),
                groupAward(season, trackers.get(4)),
                groupAward(season, trackers.get(5)),
                groupAward(season, trackers.get(6)),
                groupAward(season, trackers.get(7))));
    when(userTrophyRepository.findByUserAndTrophyIn(viewer, trackers))
        .thenReturn(List.of(userAward(viewer, trackers.get(6), 1)));
    trophyAwardService.metrics = metrics(40, 6, 840, 6, 6, 8);

    StoryModeService.StoryPageModel page = service.buildPage(season, viewer);

    assertThat(page.enabled).isTrue();
    assertThat(page.finishedJourney).isTrue();
    assertThat(page.travelMode).isEqualTo("Drive");
    assertThat(page.mood).isEqualTo("organized and unstoppable");
    assertThat(page.progressLabel).isEqualTo("Pat made the full trip.");
    assertThat(page.summaryLine).contains("The group has logged 40 counted matches");
    assertThat(page.recapLines.get(0)).startsWith("Pat got off the couch");
    assertThat(page.sideTasks).hasSize(3);
    assertThat(page.sideTasks.get(1).viewerHelped).isTrue();
    assertThat(page.sideTasks.get(0).summary)
        .isEqualTo("Help Pat look more prepared (and stay out of trouble) by doing laundry.");
    assertThat(page.sideTasks.get(0).unlockCondition)
        .isEqualTo("Play 6 matches to do one load of laundry.");
    assertThat(page.sideTasks.get(0).awardCount).isEqualTo(6);
    assertThat(page.mainGoals.get(0).progressText).startsWith("Added so far:");
  }

  @Test
  void buildPage_returnsDisabledWhenFeatureFlagIsOff() {
    ReflectionTestUtils.setField(service, "storyModeFeatureEnabled", false);

    StoryModeService.StoryPageModel page = service.buildPage(storySeason(), user(1L, "Viewer"));

    assertThat(page.enabled).isFalse();
  }

  @Test
  void buildPage_marksFutureChaptersAsUpcoming() {
    LadderSeason season = storySeason();
    List<Trophy> trackers = trackers(season);
    User viewer = user(1L, "Viewer");

    when(trophyRepository.findBySeasonAndStoryModeTrackerTrueOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(trackers);
    when(membershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            22L, LadderMembership.State.ACTIVE))
        .thenReturn(activeMemberships(season.getLadderConfig(), 6));
    when(groupTrophyRepository.findBySeasonAndTrophyIn(season, trackers))
        .thenReturn(List.of(groupAward(season, trackers.get(0))));
    when(userTrophyRepository.findByUserAndTrophyIn(viewer, trackers)).thenReturn(List.of());
    trophyAwardService.metrics = metrics(4, 2, 60, 0, 2, 0);

    StoryModeService.StoryPageModel page = service.buildPage(season, viewer);

    assertThat(page.stages).hasSize(6);
    assertThat(page.stages.get(0).completed).isTrue();
    assertThat(page.stages.get(1).current).isTrue();
    assertThat(page.stages.get(2).upcoming).isTrue();
    assertThat(page.stages.get(2).shortLabel).isEqualTo("Search");
    assertThat(page.travelMode).isEqualTo("Drive unlocked");
    assertThat(page.recapLines)
        .anyMatch(line -> line.contains("The keys have turned up, so driving is unlocked."));
    assertThat(page.recapLines).noneMatch(line -> line.contains("chose to bike"));
  }

  @Test
  void buildPage_usesPrologueAndConditionalRouteBeforeKeysAreFound() {
    LadderSeason season = storySeason();
    List<Trophy> trackers = trackers(season);
    User viewer = user(1L, "Viewer");

    when(trophyRepository.findBySeasonAndStoryModeTrackerTrueOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(trackers);
    when(membershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            22L, LadderMembership.State.ACTIVE))
        .thenReturn(activeMemberships(season.getLadderConfig(), 6));
    when(groupTrophyRepository.findBySeasonAndTrophyIn(season, trackers)).thenReturn(List.of());
    when(userTrophyRepository.findByUserAndTrophyIn(viewer, trackers)).thenReturn(List.of());
    trophyAwardService.metrics = metrics(0, 0, 0, 0, 0, 0);

    StoryModeService.StoryPageModel page = service.buildPage(season, viewer);

    assertThat(page.travelMode).isEqualTo("Bike unless keys turn up");
    assertThat(page.mood).isEqualTo("still chaotic");
    assertThat(page.recapLines.get(0))
        .isEqualTo(
            "Prologue: Pat is still on the couch, and the outing has not really started yet.");
    assertThat(page.recapLines)
        .anyMatch(
            line ->
                line.contains(
                    "No keys yet, so Pat may need to bike. Find 1 key to unlock driving."));
  }

  private LadderSeason storySeason() {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(22L);
    ladder.setTitle("Story Group");
    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", 44L);
    season.setLadderConfig(ladder);
    season.setName("Pat Season");
    season.setState(LadderSeason.State.ACTIVE);
    season.setStoryModeEnabled(true);
    return season;
  }

  private List<LadderMembership> activeMemberships(LadderConfig ladder, int count) {
    List<LadderMembership> memberships = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      LadderMembership membership = new LadderMembership();
      membership.setLadderConfig(ladder);
      membership.setUserId((long) (i + 1));
      membership.setState(LadderMembership.State.ACTIVE);
      memberships.add(membership);
    }
    return memberships;
  }

  private List<Trophy> trackers(LadderSeason season) {
    List<Trophy> trackers = new ArrayList<>();
    trackers.add(
        tracker(
            season,
            1L,
            "main_off_couch",
            "Pat gets off the couch",
            "story_matches_played >= 4 && story_unique_players >= 2 && story_points_scored >= 60"));
    trackers.add(
        tracker(
            season,
            2L,
            "main_gear_up",
            "Pat gets geared up",
            "story_matches_played >= 10 && story_unique_players >= 3 && story_points_scored >= 180"));
    trackers.add(
        tracker(
            season,
            3L,
            "main_find_essentials",
            "Pat finds the essentials",
            "story_matches_played >= 18 && story_unique_players >= 4 && story_points_scored >= 360"));
    trackers.add(
        tracker(
            season,
            4L,
            "main_find_game",
            "Pat finds the game",
            "story_matches_played >= 28 && story_unique_players >= 5 && story_points_scored >= 560"));
    trackers.add(
        tracker(
            season,
            5L,
            "main_home_again",
            "Pat makes it home",
            "story_matches_played >= 40 && story_unique_players >= 6 && story_points_scored >= 820"));
    trackers.add(
        tracker(
            season,
            6L,
            StoryModeService.TASK_LAUNDRY,
            "Pat's Laundry",
            "story_laundry_loads >= 1"));
    trackers.add(
        tracker(season, 7L, StoryModeService.TASK_KEYS, "Pat's Keys", "story_keys_found >= 1"));
    trackers.add(
        tracker(
            season, 8L, StoryModeService.TASK_SNACKS, "Pat's Snack Bag", "story_snack_runs >= 1"));
    return trackers;
  }

  private Trophy tracker(
      LadderSeason season, Long id, String key, String title, String expression) {
    Trophy trophy = new Trophy();
    ReflectionTestUtils.setField(trophy, "id", id);
    trophy.setSeason(season);
    trophy.setStoryModeTracker(true);
    trophy.setStoryModeKey(key);
    trophy.setTitle(title);
    trophy.setSummary(title + " summary");
    trophy.setUnlockCondition(title + " condition");
    trophy.setUnlockExpression(expression);
    trophy.setRarity(TrophyRarity.COMMON);
    trophy.setStatus(TrophyStatus.GENERATED);
    trophy.setEvaluationScope(TrophyEvaluationScope.GROUP);
    return trophy;
  }

  private GroupTrophy groupAward(LadderSeason season, Trophy trophy) {
    GroupTrophy award = new GroupTrophy();
    award.setSeason(season);
    award.setTrophy(trophy);
    award.setAwardCount(1);
    award.setFirstAwardedAt(Instant.now());
    award.setAwardedAt(Instant.now());
    award.setLastAwardedAt(Instant.now());
    return award;
  }

  private UserTrophy userAward(User user, Trophy trophy, int count) {
    UserTrophy award = new UserTrophy();
    award.setUser(user);
    award.setTrophy(trophy);
    award.setAwardCount(count);
    return award;
  }

  private Map<String, Double> metrics(
      int matches, int players, int points, int laundry, int keys, int snacks) {
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("story_matches_played", (double) matches);
    metrics.put("story_unique_players", (double) players);
    metrics.put("story_points_scored", (double) points);
    metrics.put("story_laundry_loads", (double) laundry);
    metrics.put("story_keys_found", (double) keys);
    metrics.put("story_snack_runs", (double) snacks);
    return metrics;
  }

  private User user(Long id, String name) {
    User user = new User();
    user.setId(id);
    user.setNickName(name);
    return user;
  }

  private static final class RecordingTrophyAwardService
      extends com.w3llspring.fhpb.web.service.trophy.TrophyAwardService {
    private Map<String, Double> metrics = Map.of();

    private RecordingTrophyAwardService() {
      super(null, null, null, null, null, null, null, null);
    }

    @Override
    public Map<String, Double> buildGroupMetrics(LadderSeason season) {
      return metrics;
    }
  }
}
