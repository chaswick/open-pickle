package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.GroupTrophyRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.db.UserTrophyRepository;
import com.w3llspring.fhpb.web.model.GroupTrophy;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyEvaluationScope;
import com.w3llspring.fhpb.web.model.TrophyRarity;
import com.w3llspring.fhpb.web.model.TrophyStatus;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserTrophy;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryModeService {

  @Value("${fhpb.features.story-mode.enabled:true}")
  private boolean storyModeFeatureEnabled = true;

  public static final String TASK_LAUNDRY = "laundry";
  public static final String TASK_KEYS = "keys";
  public static final String TASK_SNACKS = "snacks";

  private static final Pattern COMPARISON_PATTERN =
      Pattern.compile("([a-zA-Z0-9_]+)\\s*(>=|<=|==|!=|>|<)\\s*([\\-0-9.]+)");

  private static final List<StageBlueprint> STAGES =
      List.of(
          new StageBlueprint(
              "Couch",
              "Pat is still on the couch",
              "The paddle bag is nearby, but Pat is still negotiating with the blanket.",
              "bi bi-cup-hot"),
          new StageBlueprint(
              "Gear Up",
              "Pat finally starts getting ready",
              "Shoes are on, the paddle bag is open, and the energy level is no longer tragic.",
              "bi bi-bag-check"),
          new StageBlueprint(
              "Search",
              "Pat is digging through the house",
              "Now it is a full scavenger hunt: clean gear, missing keys, and one more excuse to stall.",
              "bi bi-search"),
          new StageBlueprint(
              "Door",
              "Pat heads out the door",
              "Momentum is real. Pat leaves the house and starts the trip to the courts.",
              "bi bi-door-open"),
          new StageBlueprint(
              "Courts",
              "Pat finds the game",
              "The courts are in sight and Pat is scanning for open paddles, familiar faces, and next-up energy.",
              "bi bi-geo-alt"),
          new StageBlueprint(
              "Home",
              "Pat heads home with a story",
              "The games are done, the sweat is earned, and now Pat gets to head home talking about the good points.",
              "bi bi-house-heart"));

  private static final List<MainTrackerBlueprint> MAIN_TRACKERS =
      List.of(
          new MainTrackerBlueprint(
              "main_off_couch", "Pat gets off the couch", TrophyRarity.COMMON, 100, 1),
          new MainTrackerBlueprint(
              "main_gear_up", "Pat gets geared up", TrophyRarity.UNCOMMON, 101, 2),
          new MainTrackerBlueprint(
              "main_find_essentials", "Pat finds the essentials", TrophyRarity.UNCOMMON, 102, 3),
          new MainTrackerBlueprint(
              "main_find_game", "Pat finds the game", TrophyRarity.RARE, 103, 4),
          new MainTrackerBlueprint(
              "main_home_again", "Pat makes it home", TrophyRarity.EPIC, 104, 5));

  private static final List<String> COMPLETED_CHAPTERS_PAST =
      List.of(
          "got off the couch",
          "got geared up",
          "found the essentials",
          "found the game",
          "made it home again");

  private static final List<String> COMPLETED_CHAPTERS_PRESENT_PERFECT =
      List.of(
          "gotten off the couch",
          "gotten geared up",
          "found the essentials",
          "found the game",
          "made it home again");

  private static final List<SideTrackerBlueprint> SIDE_TRACKERS =
      List.of(
          new SideTrackerBlueprint(
              TASK_LAUNDRY,
              "Pat's Laundry",
              TrophyRarity.COMMON,
              120,
              "story_laundry_loads",
              "story_matches_played",
              6),
          new SideTrackerBlueprint(
              TASK_KEYS,
              "Pat's Keys",
              TrophyRarity.UNCOMMON,
              121,
              "story_keys_found",
              "story_keys_found",
              1),
          new SideTrackerBlueprint(
              TASK_SNACKS,
              "Pat's Snack Bag",
              TrophyRarity.COMMON,
              122,
              "story_snack_runs",
              "story_points_scored",
              100));

  private final TrophyRepository trophyRepository;
  private final GroupTrophyRepository groupTrophyRepository;
  private final UserTrophyRepository userTrophyRepository;
  private final LadderMembershipRepository membershipRepository;
  private final LadderSeasonRepository seasonRepository;
  private final TrophyAwardService trophyAwardService;

  public StoryModeService(
      TrophyRepository trophyRepository,
      GroupTrophyRepository groupTrophyRepository,
      UserTrophyRepository userTrophyRepository,
      LadderMembershipRepository membershipRepository,
      LadderSeasonRepository seasonRepository,
      TrophyAwardService trophyAwardService) {
    this.trophyRepository = trophyRepository;
    this.groupTrophyRepository = groupTrophyRepository;
    this.userTrophyRepository = userTrophyRepository;
    this.membershipRepository = membershipRepository;
    this.seasonRepository = seasonRepository;
    this.trophyAwardService = trophyAwardService;
  }

  @Transactional
  public void ensureTrackers(LadderSeason season) {
    if (!storyModeFeatureEnabled
        || season == null
        || season.getId() == null
        || !season.isStoryModeEnabled()) {
      return;
    }
    int activeMembers = activeMemberCount(season);
    Map<String, Trophy> existingByKey = new LinkedHashMap<>();
    for (Trophy trophy :
        trophyRepository.findBySeasonAndStoryModeTrackerTrueOrderByDisplayOrderAscIdAsc(season)) {
      if (trophy.getStoryModeKey() != null) {
        existingByKey.put(trophy.getStoryModeKey(), trophy);
      }
    }

    List<Trophy> toSave = new ArrayList<>();
    for (TrackerSpec spec : buildTrackerSpecs(season, activeMembers)) {
      Trophy trophy = existingByKey.get(spec.key);
      if (trophy == null) {
        trophy = new Trophy();
        trophy.setSeason(season);
        trophy.setSlug(buildTrackerSlug(season, spec.key));
      }
      trophy.setTitle(spec.title);
      trophy.setSummary(spec.summary);
      trophy.setUnlockCondition(spec.unlockCondition);
      trophy.setUnlockExpression(spec.unlockExpression);
      trophy.setRarity(spec.rarity);
      trophy.setStatus(TrophyStatus.GENERATED);
      trophy.setStoryModeTracker(true);
      trophy.setStoryModeKey(spec.key);
      trophy.setGenerationSeed(trackerSeed(spec.key));
      trophy.setDisplayOrder(spec.displayOrder);
      trophy.setEvaluationScope(TrophyEvaluationScope.GROUP);
      trophy.setRepeatable(spec.repeatable);
      toSave.add(trophy);
    }
    if (!toSave.isEmpty()) {
      trophyRepository.saveAll(toSave);
    }
  }

  @Transactional(readOnly = true)
  public StoryPageModel buildPage(LadderSeason season, User viewer) {
    if (!storyModeFeatureEnabled || season == null || !season.isStoryModeEnabled()) {
      return StoryPageModel.disabled();
    }
    StorySnapshot snapshot = snapshot(season, viewer);
    StageBlueprint currentStage = STAGES.get(snapshot.currentStageIndex);
    List<StageNode> stages = new ArrayList<>();
    for (int i = 0; i < STAGES.size(); i++) {
      StageBlueprint stage = STAGES.get(i);
      stages.add(
          new StageNode(
              i + 1,
              stage.shortLabel,
              stage.title,
              stage.iconClass,
              i < snapshot.currentStageIndex,
              i == snapshot.currentStageIndex,
              i > snapshot.currentStageIndex));
    }
    String progressLabel =
        snapshot.finishedJourney
            ? "Pat made the full trip."
            : (snapshot.seasonEnded
                ? "The season paused in chapter "
                    + (snapshot.currentStageIndex + 1)
                    + " of "
                    + STAGES.size()
                    + "."
                : String.format(
                    Locale.ENGLISH, "%.0f%% to the next chapter", snapshot.focusPercent));
    return new StoryPageModel(
        true,
        "Story Mode is a playful alternate format layered on top of normal match logging. Match history stays real; the story trophies just reinterpret the same season data.",
        currentStage.title,
        currentStage.body,
        progressLabel,
        snapshot.focusPercent,
        snapshot.seasonEnded,
        snapshot.finishedJourney,
        snapshot.travelMode,
        snapshot.mood,
        snapshot.summaryLine,
        snapshot.recaps,
        stages,
        snapshot.mainGoals,
        snapshot.sideTasks);
  }

  @Transactional(readOnly = true)
  public StoryCommunityStats buildCommunityStats(int recentLimit) {
    if (!storyModeFeatureEnabled) {
      return new StoryCommunityStats(0, 0, List.of());
    }
    int limit = Math.max(1, recentLimit);
    int finishedCount = 0;
    List<StorySeasonResult> recent = new ArrayList<>();
    for (LadderSeason season :
        seasonRepository.findByStoryModeEnabledTrueAndStateOrderByTransitionDesc(
            LadderSeason.State.ENDED)) {
      StorySnapshot snapshot = snapshot(season, null);
      if (snapshot.finishedJourney) {
        finishedCount++;
        if (recent.size() < limit) {
          recent.add(
              new StorySeasonResult(
                  season.getLadderConfig() != null ? season.getLadderConfig().getTitle() : "Group",
                  season.getName(),
                  season.getEndedAt() != null ? season.getEndedAt() : season.getStartedAt(),
                  snapshot.travelMode,
                  snapshot.mood,
                  snapshot.totalMatches,
                  snapshot.contributorCount,
                  snapshot.totalPoints,
                  snapshot.laundryCount,
                  snapshot.keysCount,
                  snapshot.snacksCount,
                  snapshot.recaps.isEmpty() ? "" : snapshot.recaps.get(0)));
        }
      }
    }
    return new StoryCommunityStats(
        finishedCount,
        seasonRepository
            .findByStoryModeEnabledTrueAndStateOrderByTransitionDesc(LadderSeason.State.ENDED)
            .size(),
        recent);
  }

  public boolean isFeatureEnabled() {
    return storyModeFeatureEnabled;
  }

  private StorySnapshot snapshot(LadderSeason season, User viewer) {
    int activeMembers = activeMemberCount(season);
    Map<String, TrackerSpec> specsByKey = new LinkedHashMap<>();
    for (TrackerSpec spec : buildTrackerSpecs(season, activeMembers)) {
      specsByKey.put(spec.key, spec);
    }

    List<Trophy> trackers =
        trophyRepository.findBySeasonAndStoryModeTrackerTrueOrderByDisplayOrderAscIdAsc(season);
    Map<String, GroupTrophy> groupAwardsByKey = new LinkedHashMap<>();
    if (!trackers.isEmpty()) {
      for (GroupTrophy award : groupTrophyRepository.findBySeasonAndTrophyIn(season, trackers)) {
        if (award.getTrophy() != null && award.getTrophy().getStoryModeKey() != null) {
          groupAwardsByKey.put(award.getTrophy().getStoryModeKey(), award);
        }
      }
    }

    Map<String, UserTrophy> viewerAwardsByKey = new LinkedHashMap<>();
    if (viewer != null && viewer.getId() != null && !trackers.isEmpty()) {
      for (UserTrophy award : userTrophyRepository.findByUserAndTrophyIn(viewer, trackers)) {
        if (award.getTrophy() != null && award.getTrophy().getStoryModeKey() != null) {
          viewerAwardsByKey.put(award.getTrophy().getStoryModeKey(), award);
        }
      }
    }

    Map<String, Double> metrics = trophyAwardService.buildGroupMetrics(season);
    int totalMatches = metricInt(metrics, "story_matches_played");
    int contributorCount = metricInt(metrics, "story_unique_players");
    int totalPoints = metricInt(metrics, "story_points_scored");
    int laundryCount = metricInt(metrics, "story_laundry_loads");
    int keysCount = metricInt(metrics, "story_keys_found");
    int snacksCount = metricInt(metrics, "story_snack_runs");

    int currentStageIndex = 0;
    for (MainTrackerBlueprint tracker : MAIN_TRACKERS) {
      if (groupAwardsByKey.containsKey(tracker.key)) {
        currentStageIndex++;
      } else {
        break;
      }
    }
    currentStageIndex = Math.min(currentStageIndex, STAGES.size() - 1);
    boolean finishedJourney = currentStageIndex == STAGES.size() - 1;

    GoalBundle goals = buildMainGoalBundle(metrics, specsByKey, groupAwardsByKey, finishedJourney);
    boolean seasonEnded = season.getState() == LadderSeason.State.ENDED;
    String travelMode = resolveTravelMode(keysCount, finishedJourney, seasonEnded);
    String mood = resolveMoodLabel(laundryCount > 0, snacksCount > 0, finishedJourney, seasonEnded);

    return new StorySnapshot(
        totalMatches,
        contributorCount,
        totalPoints,
        laundryCount,
        keysCount,
        snacksCount,
        currentStageIndex,
        finishedJourney,
        seasonEnded,
        travelMode,
        mood,
        "The group has logged "
            + totalMatches
            + " counted matches, with "
            + contributorCount
            + " contributors and "
            + totalPoints
            + " total points.",
        goals.progressPercent,
        goals.goalViews,
        buildRecaps(
            season,
            finishedJourney,
            currentStageIndex,
            totalMatches,
            contributorCount,
            totalPoints,
            laundryCount,
            keysCount,
            snacksCount,
            goals.goalViews),
        buildSideTasks(
            specsByKey,
            viewer != null && viewer.getId() != null,
            viewerAwardsByKey,
            groupAwardsByKey,
            metrics));
  }

  private GoalBundle buildMainGoalBundle(
      Map<String, Double> metrics,
      Map<String, TrackerSpec> specsByKey,
      Map<String, GroupTrophy> groupAwardsByKey,
      boolean finishedJourney) {
    if (finishedJourney) {
      TrackerSpec spec = specsByKey.get(MAIN_TRACKERS.get(MAIN_TRACKERS.size() - 1).key);
      return new GoalBundle(100d, buildGoalViews(spec, metrics, 100d));
    }
    for (MainTrackerBlueprint tracker : MAIN_TRACKERS) {
      if (!groupAwardsByKey.containsKey(tracker.key)) {
        TrackerSpec spec = specsByKey.get(tracker.key);
        List<GoalView> goals = buildGoalViews(spec, metrics, -1d);
        return new GoalBundle(
            average(goals.stream().mapToDouble(goal -> goal.progressPercent).toArray()), goals);
      }
    }
    return new GoalBundle(0d, List.of());
  }

  private List<GoalView> buildGoalViews(
      TrackerSpec spec, Map<String, Double> metrics, double forcePercent) {
    if (spec == null) {
      return List.of();
    }
    return List.of(
        goalView("Counted matches", "story_matches_played", "matches", spec, metrics, forcePercent),
        goalView(
            "Group contributors", "story_unique_players", "players", spec, metrics, forcePercent),
        goalView("Points scored", "story_points_scored", "points", spec, metrics, forcePercent));
  }

  private GoalView goalView(
      String label,
      String metricKey,
      String unitLabel,
      TrackerSpec spec,
      Map<String, Double> metrics,
      double forcePercent) {
    int currentValue = metricInt(metrics, metricKey);
    int targetValue = targetFor(spec.unlockExpression, metricKey);
    double progressPercent = forcePercent >= 0d ? forcePercent : percent(currentValue, targetValue);
    return new GoalView(
        label,
        currentValue,
        targetValue,
        currentValue >= targetValue,
        progressPercent,
        unitLabel,
        "Added so far: " + currentValue + " " + unitLabel + ".");
  }

  private List<SideTaskView> buildSideTasks(
      Map<String, TrackerSpec> specsByKey,
      boolean viewerPresent,
      Map<String, UserTrophy> viewerAwardsByKey,
      Map<String, GroupTrophy> groupAwardsByKey,
      Map<String, Double> metrics) {
    List<SideTaskView> views = new ArrayList<>();
    for (SideTrackerBlueprint tracker : SIDE_TRACKERS) {
      TrackerSpec spec = specsByKey.get(tracker.key);
      UserTrophy viewerAward = viewerAwardsByKey.get(tracker.key);
      GroupTrophy groupAward = groupAwardsByKey.get(tracker.key);
      int groupAwardCount = metricInt(metrics, tracker.awardMetricKey);
      int progressCount =
          repeatableProgressValue(
              metricInt(metrics, tracker.progressMetricKey), tracker.progressTarget);
      int viewerCount = viewerAward != null ? viewerAward.getAwardCount() : 0;
      double progressPercent = percent(progressCount, tracker.progressTarget);
      views.add(
          new SideTaskView(
              tracker.key,
              spec.title,
              spec.summary,
              spec.unlockCondition,
              progressCount,
              tracker.progressTarget,
              groupAward != null
                  ? Math.max(groupAward.getAwardCount(), groupAwardCount)
                  : groupAwardCount,
              viewerCount,
              progressPercent,
              viewerCount > 0,
              groupAwardCount > 0
                  ? earnedCountText(groupAwardCount)
                  : String.format(
                      Locale.ENGLISH, "%.0f%% toward the next trophy.", progressPercent),
              viewerPresent ? "You have added " + viewerCount + " so far." : null));
    }
    return views;
  }

  private List<String> buildRecaps(
      LadderSeason season,
      boolean finishedJourney,
      int currentStageIndex,
      int totalMatches,
      int contributorCount,
      int totalPoints,
      int laundryCount,
      int keysCount,
      int snacksCount,
      List<GoalView> currentGoals) {
    boolean seasonEnded = season.getState() == LadderSeason.State.ENDED;
    List<String> lines = new ArrayList<>();
    lines.add(chapterSummaryLine(finishedJourney, seasonEnded, currentStageIndex));
    lines.add(
        chapterProgressLine(
            finishedJourney,
            seasonEnded,
            currentStageIndex,
            totalMatches,
            contributorCount,
            totalPoints,
            currentGoals));
    lines.add(routeBranchLine(keysCount, finishedJourney, seasonEnded));
    String sideTaskLine =
        sideTaskBranchLine(laundryCount, snacksCount, finishedJourney, seasonEnded);
    if (sideTaskLine != null) {
      lines.add(sideTaskLine);
    }
    return lines;
  }

  private String resolveMood(
      boolean laundryUnlocked,
      boolean snacksUnlocked,
      boolean finishedJourney,
      boolean seasonEnded) {
    if (laundryUnlocked && snacksUnlocked) {
      return finishedJourney ? "organized and unstoppable" : "surprisingly organized already";
    }
    if (snacksUnlocked) {
      return finishedJourney ? "well-fed and smug" : "fueled up";
    }
    if (laundryUnlocked) {
      return finishedJourney ? "prepared for once" : "more ready than expected";
    }
    if (seasonEnded && !finishedJourney) {
      return "a little chaotic but still optimistic";
    }
    return "a little chaotic";
  }

  private String resolveTravelMode(int keysCount, boolean finishedJourney, boolean seasonEnded) {
    if (keysCount > 0) {
      return finishedJourney || seasonEnded ? "Drive" : "Drive unlocked";
    }
    return finishedJourney || seasonEnded ? "Bike" : "Bike unless keys turn up";
  }

  private String resolveMoodLabel(
      boolean laundryUnlocked,
      boolean snacksUnlocked,
      boolean finishedJourney,
      boolean seasonEnded) {
    if (finishedJourney || seasonEnded) {
      return resolveMood(laundryUnlocked, snacksUnlocked, finishedJourney, seasonEnded);
    }
    if (laundryUnlocked && snacksUnlocked) {
      return "organized so far";
    }
    if (snacksUnlocked) {
      return "fueled up so far";
    }
    if (laundryUnlocked) {
      return "looking more prepared";
    }
    return "still chaotic";
  }

  private String chapterSummaryLine(
      boolean finishedJourney, boolean seasonEnded, int currentStageIndex) {
    if (finishedJourney) {
      return "Pat " + joinNarrative(COMPLETED_CHAPTERS_PAST) + ".";
    }
    if (currentStageIndex <= 0) {
      return seasonEnded
          ? "The season ended in the prologue, with Pat still on the couch."
          : "Prologue: Pat is still on the couch, and the outing has not really started yet.";
    }
    String completed =
        joinNarrative(COMPLETED_CHAPTERS_PRESENT_PERFECT.subList(0, currentStageIndex));
    if (seasonEnded) {
      return "Pat has "
          + completed
          + ", but the season ended during \""
          + STAGES.get(currentStageIndex).shortLabel
          + "\".";
    }
    return "So far Pat has " + completed + ".";
  }

  private String chapterProgressLine(
      boolean finishedJourney,
      boolean seasonEnded,
      int currentStageIndex,
      int totalMatches,
      int contributorCount,
      int totalPoints,
      List<GoalView> currentGoals) {
    if (finishedJourney) {
      return "The full trip took "
          + totalMatches
          + " counted matches, "
          + contributorCount
          + " contributors, and "
          + totalPoints
          + " total points.";
    }
    if (currentGoals == null || currentGoals.isEmpty()) {
      return "The group has logged "
          + totalMatches
          + " counted matches, with "
          + contributorCount
          + " contributors and "
          + totalPoints
          + " total points.";
    }
    String progress =
        currentGoals.stream()
            .map(goal -> goal.currentValue + " / " + goal.targetValue + " " + goal.unitLabel)
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    if (seasonEnded) {
      return "The season stopped with " + progress + " toward the next chapter.";
    }
    if (currentStageIndex <= 0) {
      return "To get Pat moving, the group currently has " + progress + ".";
    }
    return "The \""
        + STAGES.get(currentStageIndex).shortLabel
        + "\" chapter is underway with "
        + progress
        + ".";
  }

  private String routeBranchLine(int keysCount, boolean finishedJourney, boolean seasonEnded) {
    if (keysCount > 0) {
      return finishedJourney || seasonEnded
          ? "The keys turned up, so Pat could drive instead of bike."
          : "The keys have turned up, so driving is unlocked.";
    }
    return finishedJourney || seasonEnded
        ? "No keys turned up, so biking stayed the fallback route."
        : "No keys yet, so Pat may need to bike. Find 1 key to unlock driving.";
  }

  private String sideTaskBranchLine(
      int laundryCount, int snacksCount, boolean finishedJourney, boolean seasonEnded) {
    List<String> notes = new ArrayList<>();
    if (laundryCount > 0) {
      notes.add(
          "Laundry has happened "
              + laundryCount
              + " time"
              + (laundryCount == 1 ? "" : "s")
              + ", so Pat already looks more prepared");
    } else if (finishedJourney || seasonEnded) {
      notes.add("no laundry ever landed, so Pat never looked especially prepared");
    }
    if (snacksCount > 0) {
      notes.add(
          (finishedJourney || seasonEnded)
              ? "snack runs happened "
                  + snacksCount
                  + " time"
                  + (snacksCount == 1 ? "" : "s")
                  + ", which helped the finish feel "
                  + resolveMood(laundryCount > 0, true, finishedJourney, seasonEnded)
              : "snack runs are already covered, which should help the ending mood");
    } else if (finishedJourney || seasonEnded) {
      notes.add(
          "without snack runs, the finish stayed "
              + resolveMood(laundryCount > 0, false, finishedJourney, seasonEnded));
    }
    if (notes.isEmpty()) {
      return null;
    }
    return capitalize(notes.get(0))
        + (notes.size() > 1 ? ". " + capitalize(notes.get(1)) : "")
        + ".";
  }

  private String joinNarrative(List<String> phrases) {
    if (phrases == null || phrases.isEmpty()) {
      return "";
    }
    if (phrases.size() == 1) {
      return phrases.get(0);
    }
    if (phrases.size() == 2) {
      return phrases.get(0) + " and " + phrases.get(1);
    }
    return String.join(", ", phrases.subList(0, phrases.size() - 1))
        + ", and "
        + phrases.get(phrases.size() - 1);
  }

  private String capitalize(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  private List<TrackerSpec> buildTrackerSpecs(LadderSeason season, int activeMembers) {
    List<TrackerSpec> specs = new ArrayList<>();
    for (MainTrackerBlueprint tracker : MAIN_TRACKERS) {
      int matchesTarget = matchTarget(tracker.stageIndex, activeMembers);
      int contributorTarget = contributorTarget(tracker.stageIndex, activeMembers);
      int pointsTarget = pointTarget(tracker.stageIndex, activeMembers);
      specs.add(
          new TrackerSpec(
              tracker.key,
              tracker.title,
              "Main chapter milestone for the season. This group trophy moves Pat to the next chapter in the story.",
              "Unlock by reaching "
                  + matchesTarget
                  + " counted matches, "
                  + contributorTarget
                  + " contributors, and "
                  + pointsTarget
                  + " total points.",
              "story_matches_played >= "
                  + matchesTarget
                  + " && story_unique_players >= "
                  + contributorTarget
                  + " && story_points_scored >= "
                  + pointsTarget,
              tracker.rarity,
              tracker.displayOrder,
              false));
    }
    for (SideTrackerBlueprint tracker : SIDE_TRACKERS) {
      specs.add(
          new TrackerSpec(
              tracker.key,
              tracker.title,
              sideSummary(tracker.key),
              sideUnlockCondition(tracker.key),
              tracker.awardMetricKey + " >= 1",
              tracker.rarity,
              tracker.displayOrder,
              true));
    }
    return specs;
  }

  private String sideUnlockCondition(String key) {
    return switch (key) {
      case TASK_LAUNDRY -> "Play 6 matches to do one load of laundry.";
      case TASK_KEYS -> "Each unique contributor finds one key.";
      case TASK_SNACKS -> "Score 100 total points to make one snack run.";
      default -> "Reach the listed condition to earn the next trophy.";
    };
  }

  private String sideSummary(String key) {
    return switch (key) {
      case TASK_LAUNDRY ->
          "Help Pat look more prepared (and stay out of trouble) by doing laundry.";
      case TASK_KEYS -> "Help Pat keep track of the essentials by finding keys.";
      case TASK_SNACKS -> "Help Pat stay fueled by putting together a snack run.";
      default -> "Optional story side track.";
    };
  }

  private int repeatableProgressValue(int totalValue, int targetValue) {
    if (targetValue <= 0) {
      return totalValue;
    }
    if (totalValue <= 0) {
      return 0;
    }
    int remainder = totalValue % targetValue;
    return remainder == 0 ? targetValue : remainder;
  }

  private String earnedCountText(int awardCount) {
    return "Earned " + awardCount + " time" + (awardCount == 1 ? "" : "s") + " by the group.";
  }

  private int activeMemberCount(LadderSeason season) {
    if (season == null
        || season.getLadderConfig() == null
        || season.getLadderConfig().getId() == null) {
      return 0;
    }
    return membershipRepository
        .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            season.getLadderConfig().getId(), LadderMembership.State.ACTIVE)
        .size();
  }

  private int contributorTarget(int stageIndex, int activeMembers) {
    int[] base = {0, 2, 3, 4, 5, 6};
    int[] percent = {0, 35, 45, 55, 65, 75};
    int scaled =
        activeMembers <= 0
            ? base[stageIndex]
            : (int) Math.ceil((activeMembers * percent[stageIndex]) / 100d);
    int target = Math.max(base[stageIndex], scaled);
    return activeMembers > 0 ? Math.min(target, Math.max(activeMembers, base[stageIndex])) : target;
  }

  private int matchTarget(int stageIndex, int activeMembers) {
    int[] base = {0, 4, 10, 18, 28, 40};
    return base[stageIndex] + Math.max(0, activeMembers - 6) * Math.max(0, stageIndex - 1);
  }

  private int pointTarget(int stageIndex, int activeMembers) {
    int[] base = {0, 60, 180, 360, 560, 820};
    return base[stageIndex] + Math.max(0, activeMembers - 6) * stageIndex * 20;
  }

  private int metricInt(Map<String, Double> metrics, String key) {
    return (int) Math.floor(metrics.getOrDefault(key, 0d));
  }

  private int targetFor(String expression, String metricKey) {
    if (expression == null || expression.isBlank()) {
      return 0;
    }
    for (String clause : expression.split("&&")) {
      Matcher matcher = COMPARISON_PATTERN.matcher(clause.trim());
      if (matcher.matches() && metricKey.equalsIgnoreCase(matcher.group(1))) {
        return (int) Math.round(Double.parseDouble(matcher.group(3)));
      }
    }
    return 0;
  }

  private double percent(int current, int target) {
    return target <= 0 ? 100d : Math.min(100d, (100d * current) / target);
  }

  private double average(double... values) {
    if (values == null || values.length == 0) {
      return 0d;
    }
    double total = 0d;
    for (double value : values) {
      total += value;
    }
    return total / values.length;
  }

  private String buildTrackerSlug(LadderSeason season, String key) {
    String base =
        (season.getName() != null ? season.getName() : "season")
            + "-"
            + (season.getId() != null ? season.getId() : "story")
            + "-"
            + key
            + "-story";
    return Normalizer.normalize(base, Normalizer.Form.NFD)
        .replaceAll("[^\\p{ASCII}]", "")
        .replaceAll("[^a-zA-Z0-9]+", "-")
        .replaceAll("-+", "-")
        .toLowerCase(Locale.ENGLISH)
        .replaceAll("^-", "")
        .replaceAll("-$", "");
  }

  private String trackerSeed(String key) {
    return "story-mode-tracker:" + key + ":v2";
  }

  private record GoalBundle(double progressPercent, List<GoalView> goalViews) {}

  private record StorySnapshot(
      int totalMatches,
      int contributorCount,
      int totalPoints,
      int laundryCount,
      int keysCount,
      int snacksCount,
      int currentStageIndex,
      boolean finishedJourney,
      boolean seasonEnded,
      String travelMode,
      String mood,
      String summaryLine,
      double focusPercent,
      List<GoalView> mainGoals,
      List<String> recaps,
      List<SideTaskView> sideTasks) {}

  private record StageBlueprint(String shortLabel, String title, String body, String iconClass) {}

  private record MainTrackerBlueprint(
      String key, String title, TrophyRarity rarity, int displayOrder, int stageIndex) {}

  private record SideTrackerBlueprint(
      String key,
      String title,
      TrophyRarity rarity,
      int displayOrder,
      String awardMetricKey,
      String progressMetricKey,
      int progressTarget) {}

  private record TrackerSpec(
      String key,
      String title,
      String summary,
      String unlockCondition,
      String unlockExpression,
      TrophyRarity rarity,
      int displayOrder,
      boolean repeatable) {}

  public static final class StoryPageModel {
    public final boolean enabled;
    public final String warning;
    public final String sceneTitle;
    public final String sceneBody;
    public final String progressLabel;
    public final double progressPercent;
    public final boolean seasonEnded;
    public final boolean finishedJourney;
    public final String travelMode;
    public final String mood;
    public final String summaryLine;
    public final List<String> recapLines;
    public final List<StageNode> stages;
    public final List<GoalView> mainGoals;
    public final List<SideTaskView> sideTasks;

    private StoryPageModel(
        boolean enabled,
        String warning,
        String sceneTitle,
        String sceneBody,
        String progressLabel,
        double progressPercent,
        boolean seasonEnded,
        boolean finishedJourney,
        String travelMode,
        String mood,
        String summaryLine,
        List<String> recapLines,
        List<StageNode> stages,
        List<GoalView> mainGoals,
        List<SideTaskView> sideTasks) {
      this.enabled = enabled;
      this.warning = warning;
      this.sceneTitle = sceneTitle;
      this.sceneBody = sceneBody;
      this.progressLabel = progressLabel;
      this.progressPercent = progressPercent;
      this.seasonEnded = seasonEnded;
      this.finishedJourney = finishedJourney;
      this.travelMode = travelMode;
      this.mood = mood;
      this.summaryLine = summaryLine;
      this.recapLines = recapLines;
      this.stages = stages;
      this.mainGoals = mainGoals;
      this.sideTasks = sideTasks;
    }

    public static StoryPageModel disabled() {
      return new StoryPageModel(
          false, null, null, null, null, 0d, false, false, null, null, null, List.of(), List.of(),
          List.of(), List.of());
    }
  }

  public static final class StageNode {
    public final int stageNumber;
    public final String shortLabel;
    public final String title;
    public final String iconClass;
    public final boolean completed;
    public final boolean current;
    public final boolean upcoming;

    public StageNode(
        int stageNumber,
        String shortLabel,
        String title,
        String iconClass,
        boolean completed,
        boolean current,
        boolean upcoming) {
      this.stageNumber = stageNumber;
      this.shortLabel = shortLabel;
      this.title = title;
      this.iconClass = iconClass;
      this.completed = completed;
      this.current = current;
      this.upcoming = upcoming;
    }
  }

  public static final class GoalView {
    public final String label;
    public final int currentValue;
    public final int targetValue;
    public final boolean complete;
    public final double progressPercent;
    public final String unitLabel;
    public final String progressText;

    public GoalView(
        String label,
        int currentValue,
        int targetValue,
        boolean complete,
        double progressPercent,
        String unitLabel,
        String progressText) {
      this.label = label;
      this.currentValue = currentValue;
      this.targetValue = targetValue;
      this.complete = complete;
      this.progressPercent = progressPercent;
      this.unitLabel = unitLabel;
      this.progressText = progressText;
    }
  }

  public static final class SideTaskView {
    public final String taskKey;
    public final String title;
    public final String summary;
    public final String unlockCondition;
    public final int progressCount;
    public final int targetCount;
    public final int awardCount;
    public final int viewerCount;
    public final double progressPercent;
    public final boolean viewerHelped;
    public final String statusText;
    public final String viewerContributionText;

    public SideTaskView(
        String taskKey,
        String title,
        String summary,
        String unlockCondition,
        int progressCount,
        int targetCount,
        int awardCount,
        int viewerCount,
        double progressPercent,
        boolean viewerHelped,
        String statusText,
        String viewerContributionText) {
      this.taskKey = taskKey;
      this.title = title;
      this.summary = summary;
      this.unlockCondition = unlockCondition;
      this.progressCount = progressCount;
      this.targetCount = targetCount;
      this.awardCount = awardCount;
      this.viewerCount = viewerCount;
      this.progressPercent = progressPercent;
      this.viewerHelped = viewerHelped;
      this.statusText = statusText;
      this.viewerContributionText = viewerContributionText;
    }
  }

  public static final class StoryCommunityStats {
    public final int finishedCount;
    public final int endedStorySeasons;
    public final List<StorySeasonResult> recentResults;

    public StoryCommunityStats(
        int finishedCount, int endedStorySeasons, List<StorySeasonResult> recentResults) {
      this.finishedCount = finishedCount;
      this.endedStorySeasons = endedStorySeasons;
      this.recentResults = recentResults;
    }
  }

  public static final class StorySeasonResult {
    public final String ladderTitle;
    public final String seasonName;
    public final Instant endedAt;
    public final String travelMode;
    public final String mood;
    public final int totalMatches;
    public final int contributorCount;
    public final int totalPoints;
    public final int laundryCount;
    public final int keysCount;
    public final int snacksCount;
    public final String summary;

    public StorySeasonResult(
        String ladderTitle,
        String seasonName,
        Instant endedAt,
        String travelMode,
        String mood,
        int totalMatches,
        int contributorCount,
        int totalPoints,
        int laundryCount,
        int keysCount,
        int snacksCount,
        String summary) {
      this.ladderTitle = ladderTitle;
      this.seasonName = seasonName;
      this.endedAt = endedAt;
      this.travelMode = travelMode;
      this.mood = mood;
      this.totalMatches = totalMatches;
      this.contributorCount = contributorCount;
      this.totalPoints = totalPoints;
      this.laundryCount = laundryCount;
      this.keysCount = keysCount;
      this.snacksCount = snacksCount;
      this.summary = summary;
    }
  }
}
