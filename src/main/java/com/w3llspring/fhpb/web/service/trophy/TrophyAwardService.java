package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.db.BandPositionRepository;
import com.w3llspring.fhpb.web.db.GroupTrophyRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.db.UserTrophyRepository;
import com.w3llspring.fhpb.web.model.BandPosition;
import com.w3llspring.fhpb.web.model.GroupTrophy;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyEvaluationScope;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserTrophy;
import com.w3llspring.fhpb.web.service.StoryModeService;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrophyAwardService {

  private static final Logger log = LoggerFactory.getLogger(TrophyAwardService.class);
  private static final ZoneId LADDER_ZONE = ZoneId.of("America/New_York");
  private static final Pattern COMPARISON_PATTERN =
      Pattern.compile("([a-zA-Z0-9_]+)\\s*(>=|<=|==|!=|>|<)\\s*([\\-0-9.]+)");

  private final LadderSeasonRepository seasonRepository;
  private final TrophyRepository trophyRepository;
  private final UserTrophyRepository userTrophyRepository;
  private final GroupTrophyRepository groupTrophyRepository;
  private final MatchRepository matchRepository;
  private final LadderStandingRepository ladderStandingRepository;
  private final BandPositionRepository bandPositionRepository;
  private final TrophyArtRealizer trophyArtRealizer;

  @Value("${fhpb.features.story-mode.enabled:true}")
  private boolean storyModeFeatureEnabled = true;

  public TrophyAwardService(
      LadderSeasonRepository seasonRepository,
      TrophyRepository trophyRepository,
      UserTrophyRepository userTrophyRepository,
      GroupTrophyRepository groupTrophyRepository,
      MatchRepository matchRepository,
      LadderStandingRepository ladderStandingRepository,
      BandPositionRepository bandPositionRepository,
      TrophyArtRealizer trophyArtRealizer) {
    this.seasonRepository = seasonRepository;
    this.trophyRepository = trophyRepository;
    this.userTrophyRepository = userTrophyRepository;
    this.groupTrophyRepository = groupTrophyRepository;
    this.matchRepository = matchRepository;
    this.ladderStandingRepository = ladderStandingRepository;
    this.bandPositionRepository = bandPositionRepository;
    this.trophyArtRealizer = trophyArtRealizer;
  }

  @Transactional
  public void evaluateMatch(Match match) {
    if (match == null || match.getId() == null) {
      return;
    }
    if (!countsTowardTrophies(match)) {
      log.debug(
          "Skipping trophy evaluation for match {} because it does not qualify for competitive trophies.",
          match.getId());
      return;
    }
    LadderSeason season = resolveSeason(match);
    if (season == null) {
      log.debug(
          "No ladder season found for match {} played at {}", match.getId(), match.getPlayedAt());
      return;
    }
    if (!supportsCompetitiveTrophies(season)) {
      log.debug(
          "Skipping trophy evaluation for match {} because season {} does not use opponent confirmation.",
          match.getId(),
          season.getId());
      return;
    }
    List<Trophy> trophies = trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season);
    if (trophies.isEmpty()) {
      return;
    }

    List<Match> confirmedMatches = confirmedMatches(season);
    Map<Long, UserStats> statsByUser = computeSeasonStats(season, confirmedMatches);
    GroupStats groupStats = computeGroupStats(confirmedMatches);
    Set<User> participants = involvedPlayers(match);
    for (User user : participants) {
      if (user == null || user.getId() == null) {
        continue;
      }
      UserStats stats =
          statsByUser.getOrDefault(user.getId(), UserStats.empty(resolveUserZone(user)));
      for (Trophy trophy : trophies) {
        if (isDisabledStoryTracker(trophy)) {
          continue;
        }
        if (scopeOf(trophy) != TrophyEvaluationScope.USER) {
          continue;
        }
        if (trophy.getUnlockExpression() == null || trophy.getUnlockExpression().isBlank()) {
          continue;
        }
        if (!trophy.isRepeatable() && alreadyAwarded(user, trophy)) {
          continue;
        }
        if (isAtClaimLimit(trophy)) {
          log.debug(
              "Skipping trophy '{}' for user {} because max claims reached.",
              trophy.getTitle(),
              user.getNickName());
          continue;
        }
        if (evaluateExpression(trophy.getUnlockExpression(), stats)) {
          if (trophy.isRepeatable()) {
            UserAwardReplay replay = replayRepeatableUserAward(trophy, user, confirmedMatches);
            syncRepeatableUserAward(user, trophy, replay);
          } else {
            awardTrophy(user, trophy, match);
          }
        }
      }
    }

    for (Trophy trophy : trophies) {
      if (isDisabledStoryTracker(trophy)) {
        continue;
      }
      if (scopeOf(trophy) != TrophyEvaluationScope.GROUP) {
        continue;
      }
      if (trophy.getUnlockExpression() == null || trophy.getUnlockExpression().isBlank()) {
        continue;
      }
      if (!evaluateExpression(trophy.getUnlockExpression(), groupStats)) {
        continue;
      }
      if (isAtClaimLimit(trophy)) {
        continue;
      }
      GroupAwardReplay replay = replayGroupAward(trophy, season, confirmedMatches);
      syncGroupAward(season, trophy, replay);
    }
  }

  private LadderSeason resolveSeason(Match match) {
    if (match == null) {
      return null;
    }
    LadderSeason targetSeason = resolveSourceSessionTargetSeason(match.getSourceSessionConfig());
    if (targetSeason != null) {
      return targetSeason;
    }

    LadderSeason directSeason = loadSeasonWithConfig(match.getSeason());
    if (directSeason != null) {
      return directSeason;
    }

    return resolveSeason(match.getPlayedAt() != null ? match.getPlayedAt() : match.getCreatedAt());
  }

  private LadderSeason resolveSeason(Instant playedAt) {
    if (playedAt == null) {
      return seasonRepository.findTopByOrderByStartDateDesc().orElse(null);
    }
    LocalDate date = playedAt.atZone(LADDER_ZONE).toLocalDate();
    return seasonRepository
        .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(date, date)
        .orElseGet(() -> seasonRepository.findTopByOrderByStartDateDesc().orElse(null));
  }

  private Map<Long, UserStats> computeSeasonStats(LadderSeason season) {
    return computeSeasonStats(season, confirmedMatches(season));
  }

  private Map<Long, UserStats> computeSeasonStats(LadderSeason season, List<Match> matches) {
    Map<Long, UserStats> stats = new HashMap<>();

    for (Match match : matches) {
      processMatch(stats, match);
    }

    Map<Long, LadderStanding> standingByUser =
        ladderStandingRepository.findBySeasonOrderByRankNoAsc(season).stream()
            .filter(standing -> standing.getUser() != null && standing.getUser().getId() != null)
            .collect(
                Collectors.toMap(
                    standing -> standing.getUser().getId(), standing -> standing, (a, b) -> a));
    int totalCompetitors = standingByUser.size();

    Map<Long, BandPosition> bandByUser =
        bandPositionRepository.findBySeason(season).stream()
            .filter(bp -> bp.getUser() != null && bp.getUser().getId() != null)
            .collect(Collectors.toMap(bp -> bp.getUser().getId(), bp -> bp, (a, b) -> a));

    standingByUser
        .keySet()
        .forEach(userId -> stats.computeIfAbsent(userId, id -> new UserStats(id, LADDER_ZONE)));
    Map<Long, PlayedGroupPlacement> playedGroupByUser =
        buildPlayedGroupPlacements(stats, standingByUser);

    stats
        .values()
        .forEach(
            userStats -> {
              LadderStanding standing = standingByUser.get(userStats.getUserId());
              BandPosition bandPosition = bandByUser.get(userStats.getUserId());
              userStats.finalizeStats(
                  standing,
                  bandPosition,
                  totalCompetitors,
                  playedGroupByUser.get(userStats.getUserId()));
            });
    return stats;
  }

  public Map<String, Double> buildGroupMetrics(LadderSeason season) {
    if (season == null || season.getId() == null) {
      return Collections.emptyMap();
    }
    return computeGroupStats(confirmedMatches(season)).metricsView();
  }

  @Transactional
  public void awardSeasonFinale(Long seasonId) {
    LadderSeason season;
    if (seasonId != null) {
      season = seasonRepository.findById(seasonId).orElse(null);
    } else {
      season = seasonRepository.findTopByOrderByStartDateDesc().orElse(null);
    }
    awardSeasonFinale(season);
  }

  /**
   * Awards trophies that rely on final season standings. Should be invoked after the season is
   * marked ENDED so that ladder standings and band placements reflect the closing state.
   */
  @Transactional
  public void awardSeasonFinale(LadderSeason season) {
    if (season == null) {
      log.warn("Cannot award season finale trophies because season is null.");
      return;
    }
    if (!supportsCompetitiveTrophies(season)) {
      log.debug(
          "Skipping season finale trophies for season {} because it does not use opponent confirmation.",
          season.getId());
      return;
    }
    List<Trophy> trophies = trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season);
    if (trophies.isEmpty()) {
      return;
    }

    Map<Long, UserStats> statsByUser = computeSeasonStats(season);

    Map<Long, LadderStanding> standingByUser =
        ladderStandingRepository.findBySeasonOrderByRankNoAsc(season).stream()
            .filter(standing -> standing.getUser() != null && standing.getUser().getId() != null)
            .collect(
                Collectors.toMap(
                    standing -> standing.getUser().getId(), Function.identity(), (a, b) -> a));
    int totalCompetitors = standingByUser.size();

    Map<Long, BandPosition> bandByUser =
        bandPositionRepository.findBySeason(season).stream()
            .filter(bp -> bp.getUser() != null && bp.getUser().getId() != null)
            .collect(
                Collectors.toMap(bp -> bp.getUser().getId(), Function.identity(), (a, b) -> a));

    standingByUser
        .keySet()
        .forEach(
            userId -> statsByUser.computeIfAbsent(userId, id -> new UserStats(id, LADDER_ZONE)));
    Map<Long, PlayedGroupPlacement> finalPlayedGroupByUser =
        buildPlayedGroupPlacements(statsByUser, standingByUser);

    statsByUser
        .values()
        .forEach(
            userStats -> {
              LadderStanding standing = standingByUser.get(userStats.getUserId());
              BandPosition bandPosition = bandByUser.get(userStats.getUserId());
              userStats.applyFinalMetrics(
                  standing,
                  bandPosition,
                  totalCompetitors,
                  finalPlayedGroupByUser.get(userStats.getUserId()));
            });

    Map<Long, User> usersById =
        standingByUser.values().stream()
            .map(LadderStanding::getUser)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

    String reason = buildSeasonCloseReason(season);
    for (Trophy trophy : trophies) {
      if (isDisabledStoryTracker(trophy)) {
        continue;
      }
      if (scopeOf(trophy) != TrophyEvaluationScope.USER) {
        continue;
      }
      if (trophy.getUnlockExpression() == null || trophy.getUnlockExpression().isBlank()) {
        continue;
      }
      if (trophy.isRepeatable()) {
        continue;
      }
      for (Map.Entry<Long, UserStats> entry : statsByUser.entrySet()) {
        User user = usersById.get(entry.getKey());
        if (user == null || user.getId() == null) {
          continue;
        }
        if (alreadyAwarded(user, trophy)) {
          continue;
        }
        if (isAtClaimLimit(trophy)) {
          break;
        }
        if (evaluateExpression(trophy.getUnlockExpression(), entry.getValue())) {
          createOrIncrementUserAward(user, trophy, 1, null, null, reason);
          log.info(
              "Awarded trophy '{}' to user {} via season finale.",
              trophy.getTitle(),
              user.getNickName());
          if (isAtClaimLimit(trophy)) {
            break;
          }
        }
      }
    }
  }

  /**
   * Re-evaluates all trophies for a season against current confirmed-match history. Intended for
   * scheduled sweeps so trophy additions/removals and match edits/nullifications converge without
   * waiting for the next confirmed match event.
   *
   * @return number of newly awarded trophies in this sweep
   */
  @Transactional
  public int evaluateSeasonSweep(LadderSeason season) {
    if (season == null || season.getId() == null) {
      return 0;
    }
    season = loadSeasonWithConfig(season);
    if (!supportsCompetitiveTrophies(season)) {
      log.debug(
          "Skipping trophy sweep for season {} because it does not use opponent confirmation.",
          season.getId());
      return 0;
    }
    List<Trophy> trophies = trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season);
    if (trophies.isEmpty()) {
      return 0;
    }

    List<Match> confirmedMatches = confirmedMatches(season);
    Map<Long, UserStats> statsByUser = computeSeasonStats(season, confirmedMatches);

    Map<Long, LadderStanding> standingByUser =
        ladderStandingRepository.findBySeasonOrderByRankNoAsc(season).stream()
            .filter(standing -> standing.getUser() != null && standing.getUser().getId() != null)
            .collect(
                Collectors.toMap(
                    standing -> standing.getUser().getId(), Function.identity(), (a, b) -> a));
    int totalCompetitors = standingByUser.size();

    Map<Long, BandPosition> bandByUser =
        bandPositionRepository.findBySeason(season).stream()
            .filter(bp -> bp.getUser() != null && bp.getUser().getId() != null)
            .collect(
                Collectors.toMap(bp -> bp.getUser().getId(), Function.identity(), (a, b) -> a));

    // Season-end metrics should only be considered once the season has actually ended.
    if (season.getState() == LadderSeason.State.ENDED) {
      standingByUser
          .keySet()
          .forEach(
              userId -> statsByUser.computeIfAbsent(userId, id -> new UserStats(id, LADDER_ZONE)));
      Map<Long, PlayedGroupPlacement> finalPlayedGroupByUser =
          buildPlayedGroupPlacements(statsByUser, standingByUser);
      statsByUser
          .values()
          .forEach(
              userStats -> {
                LadderStanding standing = standingByUser.get(userStats.getUserId());
                BandPosition bandPosition = bandByUser.get(userStats.getUserId());
                userStats.applyFinalMetrics(
                    standing,
                    bandPosition,
                    totalCompetitors,
                    finalPlayedGroupByUser.get(userStats.getUserId()));
              });
    }

    Map<Long, User> usersById = collectSeasonUsers(confirmedMatches, standingByUser.values());
    List<Long> sortedUserIds = new ArrayList<>(statsByUser.keySet());
    Collections.sort(sortedUserIds);

    int awardedCount = 0;
    int revokedCount = 0;
    String reason = buildScheduledSweepReason(season);
    for (Trophy trophy : trophies) {
      if (isDisabledStoryTracker(trophy)) {
        continue;
      }
      if (trophy.getUnlockExpression() == null || trophy.getUnlockExpression().isBlank()) {
        continue;
      }

      if (scopeOf(trophy) == TrophyEvaluationScope.GROUP) {
        GroupAwardReplay replay = replayGroupAward(trophy, season, confirmedMatches);
        AwardSyncResult result = syncGroupAward(season, trophy, replay);
        awardedCount += result.awardedDelta();
        revokedCount += result.revokedDelta();
        continue;
      }

      List<UserTrophy> existingGrants = userTrophyRepository.findByTrophy(trophy);
      Map<Long, UserTrophy> grantByUserId =
          existingGrants.stream()
              .filter(ut -> ut.getUser() != null && ut.getUser().getId() != null)
              .collect(
                  Collectors.toMap(
                      ut -> ut.getUser().getId(), Function.identity(), (left, right) -> left));

      List<Long> candidateUserIds = new ArrayList<>(sortedUserIds);
      for (Long ownerId : grantByUserId.keySet()) {
        if (!statsByUser.containsKey(ownerId)) {
          candidateUserIds.add(ownerId);
        }
      }
      Collections.sort(candidateUserIds);

      for (Long userId : candidateUserIds) {
        UserTrophy existingGrant = grantByUserId.get(userId);
        User user = usersById.get(userId);
        if (user == null && existingGrant != null) {
          user = existingGrant.getUser();
        }
        if (user == null || user.getId() == null) {
          continue;
        }

        if (trophy.isRepeatable()) {
          UserAwardReplay replay = replayRepeatableUserAward(trophy, user, confirmedMatches);
          AwardSyncResult result = syncRepeatableUserAward(user, trophy, replay);
          awardedCount += result.awardedDelta();
          revokedCount += result.revokedDelta();
          continue;
        }

        UserStats stats = statsByUser.getOrDefault(userId, UserStats.empty(resolveUserZone(user)));
        boolean qualifies = evaluateExpression(trophy.getUnlockExpression(), stats);

        if (existingGrant != null && !qualifies) {
          userTrophyRepository.delete(existingGrant);
          revokedCount++;
          continue;
        }

        if (existingGrant == null && qualifies && !isAtClaimLimit(trophy)) {
          createOrIncrementUserAward(user, trophy, 1, null, null, reason);
          awardedCount++;
        }
      }
    }
    if (awardedCount > 0 || revokedCount > 0) {
      log.info(
          "Season trophy sweep for '{}' awarded {} and revoked {} grants.",
          season.getName(),
          awardedCount,
          revokedCount);
    }
    return awardedCount;
  }

  private List<Match> confirmedMatches(LadderSeason season) {
    if (season == null) {
      return List.of();
    }
    return seasonMatchHistory(season).stream()
        .filter(this::countsTowardTrophies)
        .filter(match -> matchesResolvedSeason(match, season))
        .collect(Collectors.toList());
  }

  private List<Match> seasonMatchHistory(LadderSeason season) {
    if (season == null) {
      return List.of();
    }
    List<Match> matches = List.of();
    if (season.getId() != null) {
      matches =
          matchRepository.findBySeasonOrSourceSessionTargetSeasonOrderByPlayedAtAsc(
              season, season.getId());
    }
    if (matches == null || matches.isEmpty()) {
      matches = matchRepository.findBySeasonOrderByPlayedAtAsc(season);
    }
    return matches != null ? matches : List.of();
  }

  private boolean matchesResolvedSeason(Match match, LadderSeason expectedSeason) {
    if (match == null || expectedSeason == null || expectedSeason.getId() == null) {
      return false;
    }
    LadderSeason resolvedSeason = resolveSeason(match);
    return resolvedSeason != null && Objects.equals(resolvedSeason.getId(), expectedSeason.getId());
  }

  private Map<Long, User> collectSeasonUsers(
      List<Match> matches, Collection<LadderStanding> standings) {
    Map<Long, User> usersById = new HashMap<>();
    if (standings != null) {
      for (LadderStanding standing : standings) {
        if (standing == null || standing.getUser() == null || standing.getUser().getId() == null) {
          continue;
        }
        usersById.putIfAbsent(standing.getUser().getId(), standing.getUser());
      }
    }

    for (Match match : matches) {
      if (!countsTowardTrophies(match)) {
        continue;
      }
      for (User user : involvedPlayers(match)) {
        if (user == null || user.getId() == null) {
          continue;
        }
        usersById.putIfAbsent(user.getId(), user);
      }
    }
    return usersById;
  }

  private String buildScheduledSweepReason(LadderSeason season) {
    return String.format(Locale.ENGLISH, "Scheduled trophy sweep for %s", season.getName());
  }

  private TrophyEvaluationScope scopeOf(Trophy trophy) {
    if (trophy == null || trophy.getEvaluationScope() == null) {
      return TrophyEvaluationScope.USER;
    }
    return trophy.getEvaluationScope();
  }

  private AwardSyncResult syncRepeatableUserAward(
      User user, Trophy trophy, UserAwardReplay replay) {
    if (user == null || user.getId() == null || trophy == null) {
      return AwardSyncResult.none();
    }
    UserTrophy existing = userTrophyRepository.findByUserAndTrophy(user, trophy).orElse(null);
    int existingCount = existing != null ? existing.getAwardCount() : 0;
    int desiredCount = replay.awardCount();
    if (desiredCount <= 0) {
      if (existing != null) {
        userTrophyRepository.delete(existing);
        return new AwardSyncResult(0, existingCount);
      }
      return AwardSyncResult.none();
    }
    if (existing == null && isAtClaimLimit(trophy)) {
      return AwardSyncResult.none();
    }
    createOrIncrementUserAward(
        user,
        trophy,
        desiredCount - existingCount,
        replay.firstAwardedAt(),
        replay.lastAwardedAt(),
        replay.reason());
    return new AwardSyncResult(
        Math.max(0, desiredCount - existingCount), Math.max(0, existingCount - desiredCount));
  }

  private void createOrIncrementUserAward(
      User user,
      Trophy trophy,
      int awardDelta,
      Instant firstAwardedAt,
      Instant lastAwardedAt,
      String reason) {
    if (user == null || user.getId() == null || trophy == null) {
      return;
    }
    UserTrophy grant = userTrophyRepository.findByUserAndTrophy(user, trophy).orElse(null);
    boolean creating = grant == null;
    if (creating) {
      if (awardDelta <= 0 || isAtClaimLimit(trophy)) {
        return;
      }
      grant = new UserTrophy();
      grant.setUser(user);
      grant.setTrophy(trophy);
      grant.setAwardCount(awardDelta);
    } else if (awardDelta != 0) {
      grant.setAwardCount(Math.max(0, grant.getAwardCount() + awardDelta));
    }

    if (grant.getAwardCount() <= 0) {
      userTrophyRepository.delete(grant);
      return;
    }

    trophyArtRealizer.ensureImage(trophy);
    Instant resolvedFirst =
        firstAwardedAt != null
            ? firstAwardedAt
            : (grant.getFirstAwardedAt() != null ? grant.getFirstAwardedAt() : Instant.now());
    Instant resolvedLast =
        lastAwardedAt != null
            ? lastAwardedAt
            : (grant.getLastAwardedAt() != null ? grant.getLastAwardedAt() : resolvedFirst);
    grant.setFirstAwardedAt(resolvedFirst);
    grant.setLastAwardedAt(resolvedLast);
    grant.setAwardedAt(resolvedLast);
    if (reason != null && !reason.isBlank()) {
      grant.setAwardedReason(reason);
    }
    userTrophyRepository.save(grant);
  }

  private AwardSyncResult syncGroupAward(
      LadderSeason season, Trophy trophy, GroupAwardReplay replay) {
    if (season == null || trophy == null) {
      return AwardSyncResult.none();
    }
    GroupTrophy existing = groupTrophyRepository.findBySeasonAndTrophy(season, trophy).orElse(null);
    int existingCount = existing != null ? existing.getAwardCount() : 0;
    int desiredCount = replay.awardCount();
    if (desiredCount <= 0) {
      syncGroupContributorAwards(trophy, Collections.emptyMap());
      if (existing != null) {
        groupTrophyRepository.delete(existing);
        return new AwardSyncResult(0, existingCount);
      }
      return AwardSyncResult.none();
    }
    if (existing == null && isAtClaimLimit(trophy)) {
      return AwardSyncResult.none();
    }

    if (existing == null) {
      existing = new GroupTrophy();
      existing.setSeason(season);
      existing.setTrophy(trophy);
    }

    existing.setAwardCount(desiredCount);
    existing.setFirstAwardedAt(replay.firstAwardedAt());
    existing.setLastAwardedAt(replay.lastAwardedAt());
    existing.setAwardedAt(replay.lastAwardedAt());
    existing.setAwardedReason(replay.reason());
    existing.setAwardMatchIds(replay.firstAwardMatchIds());
    groupTrophyRepository.save(existing);

    int awardedDelta = Math.max(0, desiredCount - existingCount);
    int revokedDelta = Math.max(0, existingCount - desiredCount);
    syncGroupContributorAwards(trophy, replay.contributorAwards());
    return new AwardSyncResult(awardedDelta, revokedDelta);
  }

  private void syncGroupContributorAwards(
      Trophy trophy, Map<Long, ContributorAward> desiredContributors) {
    List<UserTrophy> existingAwards = userTrophyRepository.findByTrophy(trophy);
    Map<Long, UserTrophy> awardsByUserId =
        existingAwards.stream()
            .filter(award -> award.getUser() != null && award.getUser().getId() != null)
            .collect(
                Collectors.toMap(
                    award -> award.getUser().getId(), Function.identity(), (left, right) -> left));

    for (Map.Entry<Long, UserTrophy> entry : awardsByUserId.entrySet()) {
      if (!desiredContributors.containsKey(entry.getKey())) {
        userTrophyRepository.delete(entry.getValue());
      }
    }

    if (desiredContributors.isEmpty()) {
      return;
    }

    for (ContributorAward contributorAward : desiredContributors.values()) {
      if (contributorAward == null
          || contributorAward.user() == null
          || contributorAward.user().getId() == null) {
        continue;
      }
      User contributor = contributorAward.user();
      UserTrophy existing = awardsByUserId.get(contributor.getId());
      int currentCount = existing != null ? existing.getAwardCount() : 0;
      createOrIncrementUserAward(
          contributor,
          trophy,
          contributorAward.awardCount() - currentCount,
          existing != null ? existing.getFirstAwardedAt() : contributorAward.firstAwardedAt(),
          contributorAward.lastAwardedAt(),
          contributorAward.reason());
    }
  }

  private UserAwardReplay replayRepeatableUserAward(
      Trophy trophy, User user, List<Match> confirmedMatches) {
    if (trophy == null
        || user == null
        || user.getId() == null
        || confirmedMatches == null
        || confirmedMatches.isEmpty()) {
      return UserAwardReplay.empty();
    }

    Map<Long, UserStats> rollingStats = new HashMap<>();
    int count = 0;
    Instant firstAwardedAt = null;
    Instant lastAwardedAt = null;
    String reason = null;
    for (Match match : confirmedMatches) {
      processMatch(rollingStats, match);
      if (!matchContainsUser(match, user.getId())) {
        continue;
      }
      UserStats stats =
          rollingStats.getOrDefault(user.getId(), UserStats.empty(resolveUserZone(user)));
      stats.finalizeStats(null, null, 0, null);
      if (!evaluateExpression(trophy.getUnlockExpression(), stats)) {
        continue;
      }
      count++;
      Instant awardedAt = eventTime(match);
      if (firstAwardedAt == null) {
        firstAwardedAt = awardedAt;
      }
      lastAwardedAt = awardedAt;
      reason = buildReason(match, user);
      if (!trophy.isRepeatable()) {
        break;
      }
    }
    return new UserAwardReplay(count, firstAwardedAt, lastAwardedAt, reason);
  }

  private GroupAwardReplay replayGroupAward(
      Trophy trophy, LadderSeason season, List<Match> confirmedMatches) {
    if (trophy == null
        || season == null
        || confirmedMatches == null
        || confirmedMatches.isEmpty()) {
      return GroupAwardReplay.empty();
    }

    if (trophy.isRepeatable() && trophy.isStoryModeTracker()) {
      GroupAwardReplay replay = replayRepeatableStoryGroupAward(trophy, confirmedMatches);
      if (replay != null) {
        return replay;
      }
    }

    GroupStats rollingStats = new GroupStats();
    List<Match> contributingMatches = new ArrayList<>();
    int count = 0;
    Instant firstAwardedAt = null;
    Instant lastAwardedAt = null;
    String firstAwardMatchIds = null;
    String reason = null;
    Map<Long, ContributorAwardProgress> contributorProgress = new LinkedHashMap<>();

    for (Match match : confirmedMatches) {
      processGroupMatch(rollingStats, match);
      contributingMatches.add(match);
      if (!evaluateExpression(trophy.getUnlockExpression(), rollingStats)) {
        continue;
      }
      count++;
      Instant awardedAt = eventTime(match);
      if (firstAwardedAt == null) {
        firstAwardedAt = awardedAt;
        firstAwardMatchIds = joinMatchIds(contributingMatches);
      }
      lastAwardedAt = awardedAt;
      reason = buildGroupReason(match);
      incrementContributorAwards(
          contributorProgress, collectContributors(contributingMatches), awardedAt, reason);
      if (!trophy.isRepeatable()) {
        break;
      }
    }

    if (count <= 0) {
      return GroupAwardReplay.empty();
    }
    return new GroupAwardReplay(
        count,
        firstAwardedAt,
        lastAwardedAt,
        reason,
        firstAwardMatchIds,
        finalizeContributorAwards(contributorProgress));
  }

  private GroupAwardReplay replayRepeatableStoryGroupAward(
      Trophy trophy, List<Match> confirmedMatches) {
    String storyModeKey = trophy.getStoryModeKey();
    if (storyModeKey == null) {
      return null;
    }
    return switch (storyModeKey) {
      case StoryModeService.TASK_LAUNDRY -> replayRepeatableStoryLaundryAward(confirmedMatches);
      case StoryModeService.TASK_KEYS -> replayRepeatableStoryKeysAward(confirmedMatches);
      case StoryModeService.TASK_SNACKS -> replayRepeatableStorySnacksAward(confirmedMatches);
      default -> null;
    };
  }

  private GroupAwardReplay replayRepeatableStoryLaundryAward(List<Match> confirmedMatches) {
    int cycleMatchCount = 0;
    int awardCount = 0;
    Instant firstAwardedAt = null;
    Instant lastAwardedAt = null;
    String firstAwardMatchIds = null;
    String reason = null;
    List<Match> cycleMatches = new ArrayList<>();
    Map<Long, User> cycleContributors = new LinkedHashMap<>();
    Map<Long, ContributorAwardProgress> contributorProgress = new LinkedHashMap<>();

    for (Match match : confirmedMatches) {
      if (!countsTowardStory(match)) {
        continue;
      }
      cycleMatchCount++;
      cycleMatches.add(match);
      addContributors(cycleContributors, involvedPlayers(match));
      if (cycleMatchCount < 6) {
        continue;
      }

      awardCount++;
      Instant awardedAt = eventTime(match);
      if (firstAwardedAt == null) {
        firstAwardedAt = awardedAt;
        firstAwardMatchIds = joinMatchIds(cycleMatches);
      }
      lastAwardedAt = awardedAt;
      reason = buildGroupReason(match);
      incrementContributorAwards(contributorProgress, cycleContributors, awardedAt, reason);

      cycleMatchCount = 0;
      cycleMatches = new ArrayList<>();
      cycleContributors = new LinkedHashMap<>();
    }

    if (awardCount <= 0) {
      return GroupAwardReplay.empty();
    }
    return new GroupAwardReplay(
        awardCount,
        firstAwardedAt,
        lastAwardedAt,
        reason,
        firstAwardMatchIds,
        finalizeContributorAwards(contributorProgress));
  }

  private GroupAwardReplay replayRepeatableStoryKeysAward(List<Match> confirmedMatches) {
    int awardCount = 0;
    Instant firstAwardedAt = null;
    Instant lastAwardedAt = null;
    String firstAwardMatchIds = null;
    String reason = null;
    Set<Long> seenContributorIds = new HashSet<>();
    Map<Long, ContributorAwardProgress> contributorProgress = new LinkedHashMap<>();

    for (Match match : confirmedMatches) {
      if (!countsTowardStory(match)) {
        continue;
      }
      Map<Long, User> newContributors = new LinkedHashMap<>();
      for (User user : involvedPlayers(match)) {
        if (user == null || user.getId() == null || !seenContributorIds.add(user.getId())) {
          continue;
        }
        newContributors.put(user.getId(), user);
      }
      if (newContributors.isEmpty()) {
        continue;
      }

      Instant awardedAt = eventTime(match);
      if (firstAwardedAt == null) {
        firstAwardedAt = awardedAt;
        firstAwardMatchIds = joinMatchIds(List.of(match));
      }
      lastAwardedAt = awardedAt;
      reason = buildGroupReason(match);
      awardCount += newContributors.size();
      incrementContributorAwards(contributorProgress, newContributors, awardedAt, reason);
    }

    if (awardCount <= 0) {
      return GroupAwardReplay.empty();
    }
    return new GroupAwardReplay(
        awardCount,
        firstAwardedAt,
        lastAwardedAt,
        reason,
        firstAwardMatchIds,
        finalizeContributorAwards(contributorProgress));
  }

  private GroupAwardReplay replayRepeatableStorySnacksAward(List<Match> confirmedMatches) {
    int cyclePoints = 0;
    int awardCount = 0;
    Instant firstAwardedAt = null;
    Instant lastAwardedAt = null;
    String firstAwardMatchIds = null;
    String reason = null;
    List<Match> cycleMatches = new ArrayList<>();
    Map<Long, User> cycleContributors = new LinkedHashMap<>();
    Map<Long, ContributorAwardProgress> contributorProgress = new LinkedHashMap<>();

    for (Match match : confirmedMatches) {
      if (!countsTowardStory(match)) {
        continue;
      }
      cyclePoints += Math.max(0, match.getScoreA()) + Math.max(0, match.getScoreB());
      cycleMatches.add(match);
      addContributors(cycleContributors, involvedPlayers(match));

      while (cyclePoints >= 100) {
        awardCount++;
        Instant awardedAt = eventTime(match);
        if (firstAwardedAt == null) {
          firstAwardedAt = awardedAt;
          firstAwardMatchIds = joinMatchIds(cycleMatches);
        }
        lastAwardedAt = awardedAt;
        reason = buildGroupReason(match);
        incrementContributorAwards(contributorProgress, cycleContributors, awardedAt, reason);

        cyclePoints -= 100;
        if (cyclePoints > 0) {
          cycleMatches = new ArrayList<>();
          cycleMatches.add(match);
          cycleContributors = new LinkedHashMap<>();
          addContributors(cycleContributors, involvedPlayers(match));
        } else {
          cycleMatches = new ArrayList<>();
          cycleContributors = new LinkedHashMap<>();
        }
      }
    }

    if (awardCount <= 0) {
      return GroupAwardReplay.empty();
    }
    return new GroupAwardReplay(
        awardCount,
        firstAwardedAt,
        lastAwardedAt,
        reason,
        firstAwardMatchIds,
        finalizeContributorAwards(contributorProgress));
  }

  private GroupStats computeGroupStats(List<Match> matches) {
    GroupStats stats = new GroupStats();
    for (Match match : matches) {
      processGroupMatch(stats, match);
    }
    stats.finalizeMetrics();
    return stats;
  }

  private void processGroupMatch(GroupStats stats, Match match) {
    if (stats == null || match == null) {
      return;
    }
    stats.increment("group_matches_played");
    stats.addPoints(
        "group_points_scored", Math.max(0, match.getScoreA()) + Math.max(0, match.getScoreB()));
    stats.addMatchDay(match.getPlayedAt(), false);

    Set<User> participants = involvedPlayers(match);
    for (User participant : participants) {
      stats.addContributor(participant, false);
    }
    if (participants.size() >= 4) {
      stats.increment("group_doubles_matches");
    }

    if (countsTowardStory(match)) {
      stats.increment("story_matches_played");
      stats.addPoints(
          "story_points_scored", Math.max(0, match.getScoreA()) + Math.max(0, match.getScoreB()));
      stats.addMatchDay(match.getPlayedAt(), true);
      for (User participant : participants) {
        stats.addContributor(participant, true);
      }
      if (participants.size() >= 4) {
        stats.increment("story_doubles_matches");
      }
    }
  }

  private void processMatch(Map<Long, UserStats> stats, Match match) {
    List<PlayerSlot> slots = PlayerSlot.fromMatch(match);
    boolean teamAWon = match.getScoreA() > match.getScoreB();
    for (PlayerSlot slot : slots) {
      User user = slot.user;
      if (user == null || user.getId() == null || slot.guest) {
        continue;
      }
      UserStats userStats =
          stats.computeIfAbsent(
              user.getId(), id -> new UserStats(user.getId(), resolveUserZone(user)));
      userStats.increment("matches_played");
      userStats.addMatchDay(match.getPlayedAt());

      boolean won =
          (slot.team == PlayerSlot.Team.A && teamAWon)
              || (slot.team == PlayerSlot.Team.B && !teamAWon);
      if (won) {
        userStats.increment("matches_won");
      } else {
        userStats.increment("matches_lost");
      }
      userStats.addResult(match.getPlayedAt(), won);

      Set<Long> opponentIds =
          slot.opponents.stream()
              .map(participant -> participant.user)
              .filter(Objects::nonNull)
              .map(User::getId)
              .filter(Objects::nonNull)
              .collect(Collectors.toCollection(LinkedHashSet::new));
      opponentIds.forEach(userStats::addOpponent);

      Set<Long> partnerIds =
          slot.partners.stream()
              .map(participant -> participant.user)
              .filter(Objects::nonNull)
              .map(User::getId)
              .filter(Objects::nonNull)
              .collect(Collectors.toCollection(LinkedHashSet::new));
      partnerIds.forEach(userStats::addPartner);

      boolean hadGuestPartner =
          slot.partners.stream()
              .anyMatch(
                  partner ->
                      partner.guest
                          && (partner.user == null
                              || !Objects.equals(partner.user.getId(), user.getId())));
      boolean hasLadderPartner =
          slot.partners.stream().anyMatch(partner -> !partner.guest && partner.user != null);
      boolean facedTwoGuestOpponents =
          slot.opponents.size() == 2
              && slot.opponents.stream().allMatch(opponent -> opponent.guest);
      if (!won && facedTwoGuestOpponents && hasLadderPartner && !hadGuestPartner) {
        userStats.increment("losses_with_ladder_partner_vs_two_guests");
      }

      userStats.recordMatch(
          new UserStats.MatchDetail(
              match.getPlayedAt(),
              userStats.getUserZone(),
              won,
              partnerIds,
              opponentIds,
              hadGuestPartner,
              slot.teamScore,
              slot.opponentScore));
    }

    User cosigner = match.getCosignedBy();
    if (cosigner != null && cosigner.getId() != null) {
      UserStats cosignStats =
          stats.computeIfAbsent(
              cosigner.getId(), id -> new UserStats(id, resolveUserZone(cosigner)));
      cosignStats.increment("matches_cosigned");
    }
  }

  private Map<Long, PlayedGroupPlacement> buildPlayedGroupPlacements(
      Map<Long, UserStats> statsByUser, Map<Long, LadderStanding> standingByUser) {
    if (standingByUser == null || standingByUser.isEmpty()) {
      return Collections.emptyMap();
    }

    List<LadderStanding> orderedStandings = new ArrayList<>(standingByUser.values());
    orderedStandings.sort(Comparator.comparingInt(LadderStanding::getRank));

    Set<Long> rankedUserIds = standingByUser.keySet();
    Map<Long, PlayedGroupPlacement> placements = new HashMap<>();
    for (Long userId : rankedUserIds) {
      if (userId == null) {
        continue;
      }
      LinkedHashSet<Long> groupUserIds = new LinkedHashSet<>();
      groupUserIds.add(userId);

      UserStats stats = statsByUser.get(userId);
      if (stats != null) {
        for (Long opponentId : stats.opponents) {
          if (opponentId != null && rankedUserIds.contains(opponentId)) {
            groupUserIds.add(opponentId);
          }
        }
      }

      int groupRank = 0;
      int position = 0;
      for (LadderStanding standing : orderedStandings) {
        if (standing == null || standing.getUser() == null || standing.getUser().getId() == null) {
          continue;
        }
        if (!groupUserIds.contains(standing.getUser().getId())) {
          continue;
        }
        position++;
        if (Objects.equals(standing.getUser().getId(), userId)) {
          groupRank = position;
          break;
        }
      }

      int groupSize = groupUserIds.size();
      double percentile =
          groupRank > 0 && groupSize > 0 ? (double) groupRank / (double) groupSize : 1d;
      placements.put(userId, new PlayedGroupPlacement(groupRank, groupSize, percentile));
    }
    return placements;
  }

  private Set<User> involvedPlayers(Match match) {
    Set<User> users = new HashSet<>();
    if (!match.isA1Guest() && match.getA1() != null) users.add(match.getA1());
    if (!match.isA2Guest() && match.getA2() != null) users.add(match.getA2());
    if (!match.isB1Guest() && match.getB1() != null) users.add(match.getB1());
    if (!match.isB2Guest() && match.getB2() != null) users.add(match.getB2());
    return users;
  }

  private boolean matchContainsUser(Match match, Long userId) {
    if (match == null || userId == null) {
      return false;
    }
    return involvedPlayers(match).stream()
        .map(User::getId)
        .filter(Objects::nonNull)
        .anyMatch(userId::equals);
  }

  private Map<Long, User> collectContributors(List<Match> matches) {
    Map<Long, User> contributors = new LinkedHashMap<>();
    for (Match match : matches) {
      addContributors(contributors, involvedPlayers(match));
    }
    return contributors;
  }

  private void addContributors(Map<Long, User> contributors, Collection<User> users) {
    if (contributors == null || users == null) {
      return;
    }
    for (User user : users) {
      if (user != null && user.getId() != null) {
        contributors.putIfAbsent(user.getId(), user);
      }
    }
  }

  private void incrementContributorAwards(
      Map<Long, ContributorAwardProgress> contributorProgress,
      Map<Long, User> contributors,
      Instant awardedAt,
      String reason) {
    if (contributorProgress == null || contributors == null || contributors.isEmpty()) {
      return;
    }
    for (User contributor : contributors.values()) {
      if (contributor == null || contributor.getId() == null) {
        continue;
      }
      ContributorAwardProgress progress =
          contributorProgress.computeIfAbsent(
              contributor.getId(), id -> new ContributorAwardProgress(contributor));
      progress.increment(awardedAt, reason);
    }
  }

  private Map<Long, ContributorAward> finalizeContributorAwards(
      Map<Long, ContributorAwardProgress> contributorProgress) {
    if (contributorProgress == null || contributorProgress.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<Long, ContributorAward> awards = new LinkedHashMap<>();
    for (Map.Entry<Long, ContributorAwardProgress> entry : contributorProgress.entrySet()) {
      awards.put(entry.getKey(), entry.getValue().freeze());
    }
    return awards;
  }

  private String joinMatchIds(List<Match> matches) {
    return matches.stream()
        .map(Match::getId)
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .collect(Collectors.joining(","));
  }

  private Instant eventTime(Match match) {
    return match != null && match.getPlayedAt() != null ? match.getPlayedAt() : Instant.now();
  }

  private boolean evaluateExpression(String expression, MetricSource stats) {
    String trimmed = expression.trim();
    if (trimmed.isEmpty()) {
      return false;
    }
    for (String orClause : splitBy(trimmed, "||")) {
      boolean andSatisfied = true;
      for (String andClause : splitBy(orClause, "&&")) {
        String clause = andClause.trim();
        if (!evaluateComparison(clause, stats)) {
          andSatisfied = false;
          break;
        }
      }
      if (andSatisfied) {
        return true;
      }
    }
    return false;
  }

  private List<String> splitBy(String expression, String delimiter) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i <= expression.length() - delimiter.length(); i++) {
      char c = expression.charAt(i);
      if (c == '(') depth++;
      if (c == ')') depth--;
      if (depth == 0 && expression.startsWith(delimiter, i)) {
        parts.add(expression.substring(start, i));
        start = i + delimiter.length();
      }
    }
    parts.add(expression.substring(start));
    return parts;
  }

  private boolean evaluateComparison(String clause, MetricSource stats) {
    Matcher matcher = COMPARISON_PATTERN.matcher(clause.trim());
    if (!matcher.matches()) {
      log.warn("Unable to parse trophy unlock expression clause '{}'.", clause);
      return false;
    }
    String metric = matcher.group(1).toLowerCase(Locale.ENGLISH);
    String operator = matcher.group(2);
    double value;
    try {
      value = Double.parseDouble(matcher.group(3));
    } catch (NumberFormatException ex) {
      log.warn("Invalid numeric value in trophy unlock expression clause '{}'.", clause);
      return false;
    }
    double actual = stats.getMetric(metric);
    switch (operator) {
      case ">=":
        return actual >= value;
      case "<=":
        return actual <= value;
      case ">":
        return actual > value;
      case "<":
        return actual < value;
      case "==":
        return actual == value;
      case "!=":
        return actual != value;
      default:
        return false;
    }
  }

  private void awardTrophy(User user, Trophy trophy, Match match) {
    createOrIncrementUserAward(
        user, trophy, 1, eventTime(match), eventTime(match), buildReason(match, user));
    log.info(
        "Awarded trophy '{}' to user {} (match {}).",
        trophy.getTitle(),
        user.getNickName(),
        match.getId());
  }

  private boolean alreadyAwarded(User user, Trophy trophy) {
    return trophy != null
        && !trophy.isRepeatable()
        && userTrophyRepository.existsByUserAndTrophy(user, trophy);
  }

  private String buildReason(Match match, User user) {
    StringBuilder sb = new StringBuilder("Logged match ");
    sb.append('#').append(match.getId());
    if (match.getPlayedAt() != null) {
      sb.append(" on ");
      sb.append(
          DateTimeFormatter.ISO_LOCAL_DATE.format(
              match.getPlayedAt().atZone(resolveUserZone(user)).toLocalDate()));
    }
    return sb.toString();
  }

  private String buildGroupReason(Match match) {
    StringBuilder sb = new StringBuilder("Group threshold crossed via match ");
    sb.append('#').append(match.getId());
    if (match.getPlayedAt() != null) {
      sb.append(" on ");
      sb.append(
          DateTimeFormatter.ISO_LOCAL_DATE.format(
              match.getPlayedAt().atZone(LADDER_ZONE).toLocalDate()));
    }
    return sb.toString();
  }

  private boolean countsTowardTrophies(Match match) {
    return match != null
        && match.getState() == MatchState.CONFIRMED
        && !isEffectivelyExcludedFromTrophies(match);
  }

  private boolean countsTowardStory(Match match) {
    return countsTowardTrophies(match);
  }

  private boolean isEffectivelyExcludedFromTrophies(Match match) {
    if (match == null || match.isExcludeFromStandings()) {
      return true;
    }
    LadderSeason season = resolveSeason(match);
    if (!supportsCompetitiveTrophies(season)) {
      return true;
    }
    LadderConfig cfg = season != null ? season.getLadderConfig() : null;
    boolean allowGuestOnlyPersonalRecords =
        cfg != null
            && cfg.isAllowGuestOnlyPersonalMatches()
            && cfg.getSecurityLevel() != null
            && LadderSecurity.normalize(cfg.getSecurityLevel()).isSelfConfirm();
    return allowGuestOnlyPersonalRecords && match.hasGuestOnlyOpposingTeam();
  }

  private boolean supportsCompetitiveTrophies(LadderSeason season) {
    LadderSeason loadedSeason = loadSeasonWithConfig(season);
    LadderConfig cfg = loadedSeason != null ? loadedSeason.getLadderConfig() : null;
    LadderSecurity security = cfg != null ? cfg.getSecurityLevel() : null;
    return security == null || LadderSecurity.normalize(security).requiresOpponentConfirmation();
  }

  private LadderSeason loadSeasonWithConfig(LadderSeason season) {
    if (season == null || season.getId() == null) {
      return season;
    }
    LadderConfig ladderConfig = season.getLadderConfig();
    if (ladderConfig != null && Hibernate.isInitialized(ladderConfig)) {
      return season;
    }
    return seasonRepository.findByIdWithLadderConfig(season.getId()).orElse(season);
  }

  private LadderSeason resolveSourceSessionTargetSeason(LadderConfig sourceSessionConfig) {
    if (sourceSessionConfig == null
        || !sourceSessionConfig.isSessionType()
        || sourceSessionConfig.getTargetSeasonId() == null) {
      return null;
    }
    return seasonRepository
        .findByIdWithLadderConfig(sourceSessionConfig.getTargetSeasonId())
        .or(() -> seasonRepository.findById(sourceSessionConfig.getTargetSeasonId()))
        .orElse(null);
  }

  private ZoneId resolveUserZone(User user) {
    if (user == null) {
      return LADDER_ZONE;
    }
    String tz = user.getTimeZone();
    if (tz == null || tz.isBlank()) {
      return LADDER_ZONE;
    }
    try {
      return ZoneId.of(tz.trim());
    } catch (Exception ignored) {
      return LADDER_ZONE;
    }
  }

  private String buildSeasonCloseReason(LadderSeason season) {
    return String.format(Locale.ENGLISH, "Final standings for %s", season.getName());
  }

  private boolean isAtClaimLimit(Trophy trophy) {
    if (trophy == null || !trophy.isLimited()) {
      return false;
    }
    Integer maxClaims = trophy.getMaxClaims();
    if (maxClaims == null || maxClaims <= 0) {
      return false;
    }
    long currentOwners =
        scopeOf(trophy) == TrophyEvaluationScope.GROUP
            ? groupTrophyRepository.countByTrophy(trophy)
            : userTrophyRepository.countByTrophy(trophy);
    return currentOwners >= maxClaims;
  }

  private boolean isDisabledStoryTracker(Trophy trophy) {
    return !storyModeFeatureEnabled && trophy != null && trophy.isStoryModeTracker();
  }

  private interface MetricSource {
    double getMetric(String key);
  }

  private record AwardSyncResult(int awardedDelta, int revokedDelta) {
    private static AwardSyncResult none() {
      return new AwardSyncResult(0, 0);
    }
  }

  private record UserAwardReplay(
      int awardCount, Instant firstAwardedAt, Instant lastAwardedAt, String reason) {
    private static UserAwardReplay empty() {
      return new UserAwardReplay(0, null, null, null);
    }
  }

  private record GroupAwardReplay(
      int awardCount,
      Instant firstAwardedAt,
      Instant lastAwardedAt,
      String reason,
      String firstAwardMatchIds,
      Map<Long, ContributorAward> contributorAwards) {
    private static GroupAwardReplay empty() {
      return new GroupAwardReplay(0, null, null, null, null, Collections.emptyMap());
    }
  }

  private record ContributorAward(
      User user, int awardCount, Instant firstAwardedAt, Instant lastAwardedAt, String reason) {}

  private record PlayedGroupPlacement(int rank, int size, double percentile) {
    private boolean leader() {
      return rank == 1 && size > 0;
    }

    private boolean last() {
      return size > 0 && rank == size;
    }
  }

  private static final class ContributorAwardProgress {
    private final User user;
    private int awardCount;
    private Instant firstAwardedAt;
    private Instant lastAwardedAt;
    private String reason;

    private ContributorAwardProgress(User user) {
      this.user = user;
    }

    private void increment(Instant awardedAt, String reason) {
      awardCount++;
      if (firstAwardedAt == null) {
        firstAwardedAt = awardedAt;
      }
      lastAwardedAt = awardedAt;
      if (reason != null && !reason.isBlank()) {
        this.reason = reason;
      }
    }

    private ContributorAward freeze() {
      return new ContributorAward(user, awardCount, firstAwardedAt, lastAwardedAt, reason);
    }
  }

  private static class GroupStats implements MetricSource {
    private final Map<String, Double> metrics = new HashMap<>();
    private final Set<Long> groupContributors = new HashSet<>();
    private final Set<Long> storyContributors = new HashSet<>();
    private final Set<LocalDate> groupMatchDays = new HashSet<>();
    private final Set<LocalDate> storyMatchDays = new HashSet<>();
    private boolean finalized;

    void increment(String key) {
      metrics.merge(key.toLowerCase(Locale.ENGLISH), 1d, Double::sum);
    }

    void addPoints(String key, int value) {
      metrics.merge(key.toLowerCase(Locale.ENGLISH), (double) Math.max(0, value), Double::sum);
    }

    void addContributor(User user, boolean story) {
      if (user == null || user.getId() == null) {
        return;
      }
      if (story) {
        storyContributors.add(user.getId());
      } else {
        groupContributors.add(user.getId());
      }
    }

    void addMatchDay(Instant playedAt, boolean story) {
      if (playedAt == null) {
        return;
      }
      LocalDate day = playedAt.atZone(LADDER_ZONE).toLocalDate();
      if (story) {
        storyMatchDays.add(day);
      } else {
        groupMatchDays.add(day);
      }
    }

    void finalizeMetrics() {
      if (finalized) {
        return;
      }
      finalized = true;
      metrics.putIfAbsent("group_matches_played", 0d);
      metrics.putIfAbsent("group_points_scored", 0d);
      metrics.putIfAbsent("group_doubles_matches", 0d);
      metrics.put("group_unique_players", (double) groupContributors.size());
      metrics.put("group_distinct_match_days", (double) groupMatchDays.size());

      metrics.putIfAbsent("story_matches_played", 0d);
      metrics.putIfAbsent("story_points_scored", 0d);
      metrics.putIfAbsent("story_doubles_matches", 0d);
      metrics.put("story_unique_players", (double) storyContributors.size());
      metrics.put("story_distinct_match_days", (double) storyMatchDays.size());
      metrics.put(
          "story_laundry_loads", Math.floor(metrics.getOrDefault("story_matches_played", 0d) / 6d));
      metrics.put("story_keys_found", metrics.getOrDefault("story_unique_players", 0d));
      metrics.put(
          "story_snack_runs", Math.floor(metrics.getOrDefault("story_points_scored", 0d) / 100d));
    }

    Map<String, Double> metricsView() {
      finalizeMetrics();
      return Collections.unmodifiableMap(metrics);
    }

    @Override
    public double getMetric(String key) {
      finalizeMetrics();
      return metrics.getOrDefault(key.toLowerCase(Locale.ENGLISH), 0d);
    }
  }

  private static class PlayerSlot {
    enum Team {
      A,
      B
    }

    final User user;
    final boolean guest;
    final Team team;
    final List<Participant> partners;
    final List<Participant> opponents;
    final int teamScore;
    final int opponentScore;

    private PlayerSlot(
        User user,
        boolean guest,
        Team team,
        List<Participant> partners,
        List<Participant> opponents,
        int teamScore,
        int opponentScore) {
      this.user = user;
      this.guest = guest;
      this.team = team;
      this.partners = partners;
      this.opponents = opponents;
      this.teamScore = teamScore;
      this.opponentScore = opponentScore;
    }

    static List<PlayerSlot> fromMatch(Match match) {
      List<PlayerSlot> slots = new ArrayList<>();
      slots.add(
          new PlayerSlot(
              match.getA1(),
              match.isA1Guest(),
              Team.A,
              participants(participant(match.getA2(), match.isA2Guest())),
              participants(
                  participant(match.getB1(), match.isB1Guest()),
                  participant(match.getB2(), match.isB2Guest())),
              match.getScoreA(),
              match.getScoreB()));
      slots.add(
          new PlayerSlot(
              match.getA2(),
              match.isA2Guest(),
              Team.A,
              participants(participant(match.getA1(), match.isA1Guest())),
              participants(
                  participant(match.getB1(), match.isB1Guest()),
                  participant(match.getB2(), match.isB2Guest())),
              match.getScoreA(),
              match.getScoreB()));
      slots.add(
          new PlayerSlot(
              match.getB1(),
              match.isB1Guest(),
              Team.B,
              participants(participant(match.getB2(), match.isB2Guest())),
              participants(
                  participant(match.getA1(), match.isA1Guest()),
                  participant(match.getA2(), match.isA2Guest())),
              match.getScoreB(),
              match.getScoreA()));
      slots.add(
          new PlayerSlot(
              match.getB2(),
              match.isB2Guest(),
              Team.B,
              participants(participant(match.getB1(), match.isB1Guest())),
              participants(
                  participant(match.getA1(), match.isA1Guest()),
                  participant(match.getA2(), match.isA2Guest())),
              match.getScoreB(),
              match.getScoreA()));
      return slots;
    }

    private static List<Participant> participants(Participant... participants) {
      return Arrays.stream(participants)
          .filter(Objects::nonNull)
          .collect(
              Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    private static Participant participant(User user, boolean guest) {
      if (user == null && !guest) {
        return null;
      }
      return new Participant(user, guest);
    }

    private static class Participant {
      final User user;
      final boolean guest;

      private Participant(User user, boolean guest) {
        this.user = user;
        this.guest = guest;
      }
    }
  }

  private static class UserStats implements MetricSource {
    private final long userId;
    private final ZoneId userZone;
    private final Map<String, Double> metrics = new HashMap<>();
    private final Set<Long> opponents = new HashSet<>();
    private final Set<Long> partners = new HashSet<>();
    private final Set<LocalDate> matchDays = new HashSet<>();
    private final List<MatchOutcome> outcomes = new ArrayList<>();
    private final List<MatchDetail> matchDetails = new ArrayList<>();
    private boolean derivedMetricsComputed;

    private UserStats(long userId, ZoneId userZone) {
      this.userId = userId;
      this.userZone = userZone == null ? LADDER_ZONE : userZone;
    }

    static UserStats empty(ZoneId userZone) {
      UserStats stats = new UserStats(-1, userZone);
      stats.finalizeStats(null, null, 0, null);
      return stats;
    }

    void increment(String key) {
      metrics.merge(key.toLowerCase(Locale.ENGLISH), 1d, Double::sum);
    }

    void addOpponent(Long opponentId) {
      opponents.add(opponentId);
    }

    void addPartner(Long partnerId) {
      partners.add(partnerId);
    }

    void addMatchDay(Instant instant) {
      if (instant != null) {
        matchDays.add(instant.atZone(userZone).toLocalDate());
      }
    }

    void addResult(Instant playedAt, boolean win) {
      outcomes.add(new MatchOutcome(playedAt, win));
    }

    void recordMatch(MatchDetail detail) {
      if (detail != null) {
        matchDetails.add(detail);
      }
    }

    long getUserId() {
      return userId;
    }

    ZoneId getUserZone() {
      return userZone;
    }

    void finalizeStats(
        LadderStanding standing,
        BandPosition bandPosition,
        int totalCompetitors,
        PlayedGroupPlacement playedGroupPlacement) {
      computeDerivedMetrics();
      metrics.put("unique_opponents", (double) opponents.size());
      metrics.put("unique_partners", (double) partners.size());
      metrics.put("distinct_match_days", (double) matchDays.size());
      metrics.putIfAbsent("matches_played", 0d);
      metrics.putIfAbsent("matches_won", 0d);
      metrics.putIfAbsent("matches_lost", 0d);
      // Backward-compatible alias: older trophy expressions may refer to "sets_dropped"
      // (even though this app does not model multi-set matches).
      metrics.put("sets_dropped", metrics.getOrDefault("matches_lost", 0d));
      metrics.put("consecutive_wins", (double) longestWinStreak());
      metrics.putIfAbsent("matches_cosigned", 0d);

      if (standing != null) {
        metrics.put("current_rank", (double) standing.getRank());
        metrics.put("is_overall_leader", standing.getRank() == 1 ? 1d : 0d);
        metrics.put(
            "is_overall_last",
            totalCompetitors > 0 && standing.getRank() == totalCompetitors ? 1d : 0d);
      } else {
        metrics.put("current_rank", 0d);
        metrics.put("is_overall_leader", 0d);
        metrics.put("is_overall_last", 0d);
      }

      metrics.put("current_total_competitors", (double) Math.max(totalCompetitors, 0));
      if (standing != null && totalCompetitors > 0) {
        metrics.put("current_percentile", (double) standing.getRank() / (double) totalCompetitors);
      } else {
        metrics.putIfAbsent("current_percentile", 1d);
      }

      if (bandPosition != null) {
        double bandIndex = bandPosition.getBandIndex() != null ? bandPosition.getBandIndex() : 0;
        double bandRank =
            bandPosition.getPositionInBand() != null ? bandPosition.getPositionInBand() : 0;
        metrics.put("current_band_index", bandIndex);
        metrics.put("current_band_rank", bandRank);
        metrics.put("is_band_champion", bandRank == 1 ? 1d : 0d);
      } else {
        metrics.put("current_band_index", 0d);
        metrics.put("current_band_rank", 0d);
        metrics.put("is_band_champion", 0d);
      }

      applyPlacementMetrics("current", playedGroupPlacement);
    }

    void applyFinalMetrics(
        LadderStanding standing,
        BandPosition bandPosition,
        int totalCompetitors,
        PlayedGroupPlacement playedGroupPlacement) {
      computeDerivedMetrics();
      if (standing != null) {
        metrics.put("final_rank", (double) standing.getRank());
        metrics.put("is_final_overall_leader", standing.getRank() == 1 ? 1d : 0d);
        metrics.put(
            "is_final_overall_last",
            totalCompetitors > 0 && standing.getRank() == totalCompetitors ? 1d : 0d);
      } else {
        metrics.put("final_rank", 0d);
        metrics.put("is_final_overall_leader", 0d);
        metrics.put("is_final_overall_last", 0d);
      }

      metrics.put("final_total_competitors", (double) Math.max(totalCompetitors, 0));
      if (standing != null && totalCompetitors > 0) {
        metrics.put("final_percentile", (double) standing.getRank() / (double) totalCompetitors);
      } else {
        metrics.putIfAbsent("final_percentile", 1d);
      }

      if (bandPosition != null) {
        double bandIndex = bandPosition.getBandIndex() != null ? bandPosition.getBandIndex() : 0;
        double bandRank =
            bandPosition.getPositionInBand() != null ? bandPosition.getPositionInBand() : 0;
        metrics.put("final_band_index", bandIndex);
        metrics.put("final_band_rank", bandRank);
        metrics.put("is_final_band_champion", bandRank == 1 ? 1d : 0d);
      } else {
        metrics.put("final_band_index", 0d);
        metrics.put("final_band_rank", 0d);
        metrics.put("is_final_band_champion", 0d);
      }

      applyPlacementMetrics("final", playedGroupPlacement);
    }

    @Override
    public double getMetric(String key) {
      return metrics.getOrDefault(key.toLowerCase(Locale.ENGLISH), 0d);
    }

    private int longestWinStreak() {
      if (outcomes.isEmpty()) {
        return 0;
      }
      outcomes.sort(Comparator.comparing(o -> o.playedAt));
      int max = 0;
      int current = 0;
      for (MatchOutcome outcome : outcomes) {
        if (outcome.win) {
          current++;
          max = Math.max(max, current);
        } else {
          current = 0;
        }
      }
      return max;
    }

    private static class MatchOutcome {
      private final Instant playedAt;
      private final boolean win;

      private MatchOutcome(Instant playedAt, boolean win) {
        this.playedAt = playedAt;
        this.win = win;
      }
    }

    private void computeDerivedMetrics() {
      if (derivedMetricsComputed) {
        return;
      }
      derivedMetricsComputed = true;

      ensureMetricDefaults();

      if (matchDetails.isEmpty()) {
        return;
      }

      List<MatchDetail> sorted = new ArrayList<>(matchDetails);
      sorted.sort(
          Comparator.comparing(
              MatchDetail::playedAt, Comparator.nullsLast(Comparator.naturalOrder())));

      Map<LocalDate, Integer> matchesPerDay = new HashMap<>();
      Map<LocalDate, Map<Long, Integer>> partnerWinsByDay = new HashMap<>();
      Map<Long, Integer> opponentEncounterCounts = new HashMap<>();
      Map<Long, Integer> partnerMatchCounts = new HashMap<>();
      Set<Long> partnersWonWith = new HashSet<>();
      Map<Long, Integer> opponentWinStreak = new HashMap<>();

      int guestPartnerMatches = 0;
      int winsByMarginTwo = 0;
      int winsShutout = 0;
      int matchesOnThursday = 0;
      int bestOpponentWinStreak = 0;
      int beatLastPartnerCount = 0;
      int alternatingPartnerWinBest = 0;
      int alternatingPartnerWinCurrent = 0;
      Set<Long> previousWinPartners = null;
      Set<Long> lastPartnerIds = Collections.emptySet();

      int longestLossStreak = 0;
      int currentLossStreak = 0;
      int lossStreakBroken = 0;

      for (MatchDetail detail : sorted) {
        LocalDate day = detail.playedDate();
        if (day != null) {
          matchesPerDay.merge(day, 1, Integer::sum);
          metrics.merge(calendarDayMetric(day), 1d, Double::sum);
          if (day.getDayOfWeek() == DayOfWeek.THURSDAY) {
            matchesOnThursday++;
          }
        }

        if (detail.hadGuestPartner) {
          guestPartnerMatches++;
        }

        for (Long partnerId : detail.partnerIds) {
          partnerMatchCounts.merge(partnerId, 1, Integer::sum);
        }

        for (Long opponentId : detail.opponentIds) {
          opponentEncounterCounts.merge(opponentId, 1, Integer::sum);
        }

        if (detail.win) {
          if (detail.partnerIds.isEmpty()) {
            alternatingPartnerWinCurrent = 0;
            previousWinPartners = null;
          } else {
            partnersWonWith.addAll(detail.partnerIds);
            if (previousWinPartners == null) {
              alternatingPartnerWinCurrent = 1;
            } else if (!previousWinPartners.equals(detail.partnerIds)) {
              alternatingPartnerWinCurrent =
                  alternatingPartnerWinCurrent == 0 ? 2 : alternatingPartnerWinCurrent + 1;
            } else {
              alternatingPartnerWinCurrent = 1;
            }
            previousWinPartners = detail.partnerIds;
            alternatingPartnerWinBest =
                Math.max(alternatingPartnerWinBest, alternatingPartnerWinCurrent);
          }

          if (Math.abs(detail.teamScore - detail.opponentScore) == 2) {
            winsByMarginTwo++;
          }
          if (detail.opponentScore == 0) {
            winsShutout++;
          }

          if (day != null && !detail.partnerIds.isEmpty()) {
            Map<Long, Integer> dayWins =
                partnerWinsByDay.computeIfAbsent(day, d -> new HashMap<>());
            for (Long partnerId : detail.partnerIds) {
              dayWins.merge(partnerId, 1, Integer::sum);
            }
          }

          for (Long opponentId : detail.opponentIds) {
            int streak = opponentWinStreak.getOrDefault(opponentId, 0) + 1;
            opponentWinStreak.put(opponentId, streak);
            bestOpponentWinStreak = Math.max(bestOpponentWinStreak, streak);
          }

          if (currentLossStreak > 0) {
            lossStreakBroken = Math.max(lossStreakBroken, currentLossStreak);
            currentLossStreak = 0;
          }
        } else {
          alternatingPartnerWinCurrent = 0;
          previousWinPartners = null;
          currentLossStreak++;
          longestLossStreak = Math.max(longestLossStreak, currentLossStreak);
          for (Long opponentId : detail.opponentIds) {
            opponentWinStreak.remove(opponentId);
          }
        }

        if (!lastPartnerIds.isEmpty()
            && detail.win
            && !Collections.disjoint(lastPartnerIds, detail.opponentIds)) {
          beatLastPartnerCount++;
        }

        lastPartnerIds = detail.partnerIds;
      }

      longestLossStreak = Math.max(longestLossStreak, currentLossStreak);

      metrics.put("guest_partner_matches", (double) guestPartnerMatches);
      metrics.put("wins_by_margin_2", (double) winsByMarginTwo);
      metrics.put("shutout_wins", (double) winsShutout);
      metrics.put("matches_played_on_thursday", (double) matchesOnThursday);
      metrics.put(
          "repeat_opponent_matches",
          opponentEncounterCounts.values().stream()
              .max(Integer::compareTo)
              .orElse(0)
              .doubleValue());
      metrics.put("repeat_opponent_wins_streak", (double) bestOpponentWinStreak);
      metrics.put("unique_partners_won_with", (double) partnersWonWith.size());
      metrics.put("beat_last_partner_next_match", (double) beatLastPartnerCount);
      metrics.put(
          "matches_with_primary_partner",
          partnerMatchCounts.values().stream().max(Integer::compareTo).orElse(0).doubleValue());
      metrics.put(
          "same_day_partner_back_to_back_wins",
          partnerWinsByDay.values().stream()
              .flatMap(map -> map.values().stream())
              .max(Integer::compareTo)
              .orElse(0)
              .doubleValue());
      metrics.put("alternating_partner_win_streak", (double) alternatingPartnerWinBest);
      metrics.put("consecutive_losses", (double) longestLossStreak);
      metrics.put("consecutive_losses_before_win", (double) lossStreakBroken);
      metrics.put("losing_streak_broken", (double) lossStreakBroken);

      int bestWeekendTotal = computeBestWeekendTotal(matchesPerDay);
      metrics.put("weekend_sat_sun_matches", (double) bestWeekendTotal);

      GapRecovery recovery = computeGapRecovery(matchesPerDay);
      metrics.put("days_since_last_match", (double) recovery.daysGap);
      metrics.put("matches_played_today", (double) recovery.matchesAfterGap);
    }

    private static String calendarDayMetric(LocalDate day) {
      return String.format(
          Locale.ENGLISH, "matches_played_on_%02d_%02d", day.getMonthValue(), day.getDayOfMonth());
    }

    private void applyPlacementMetrics(String prefix, PlayedGroupPlacement placement) {
      if (prefix == null || prefix.isBlank()) {
        return;
      }
      String normalizedPrefix = prefix.toLowerCase(Locale.ENGLISH);
      if (placement != null) {
        metrics.put(normalizedPrefix + "_played_group_rank", (double) placement.rank());
        metrics.put(normalizedPrefix + "_played_group_size", (double) placement.size());
        metrics.put(normalizedPrefix + "_played_group_percentile", placement.percentile());
        metrics.put(
            "is_" + normalizedPrefix + "_played_group_leader", placement.leader() ? 1d : 0d);
        metrics.put("is_" + normalizedPrefix + "_played_group_last", placement.last() ? 1d : 0d);
        return;
      }
      metrics.put(normalizedPrefix + "_played_group_rank", 0d);
      metrics.put(normalizedPrefix + "_played_group_size", 0d);
      metrics.put(normalizedPrefix + "_played_group_percentile", 1d);
      metrics.put("is_" + normalizedPrefix + "_played_group_leader", 0d);
      metrics.put("is_" + normalizedPrefix + "_played_group_last", 0d);
    }

    private void ensureMetricDefaults() {
      metrics.putIfAbsent("guest_partner_matches", 0d);
      metrics.putIfAbsent("wins_by_margin_2", 0d);
      metrics.putIfAbsent("shutout_wins", 0d);
      metrics.putIfAbsent("matches_played_on_thursday", 0d);
      metrics.putIfAbsent("repeat_opponent_matches", 0d);
      metrics.putIfAbsent("repeat_opponent_wins_streak", 0d);
      metrics.putIfAbsent("unique_partners_won_with", 0d);
      metrics.putIfAbsent("beat_last_partner_next_match", 0d);
      metrics.putIfAbsent("matches_with_primary_partner", 0d);
      metrics.putIfAbsent("same_day_partner_back_to_back_wins", 0d);
      metrics.putIfAbsent("alternating_partner_win_streak", 0d);
      metrics.putIfAbsent("consecutive_losses", 0d);
      metrics.putIfAbsent("consecutive_losses_before_win", 0d);
      metrics.putIfAbsent("losing_streak_broken", 0d);
      metrics.putIfAbsent("losses_with_ladder_partner_vs_two_guests", 0d);
      metrics.putIfAbsent("weekend_sat_sun_matches", 0d);
      metrics.putIfAbsent("days_since_last_match", 0d);
      metrics.putIfAbsent("matches_played_today", 0d);
    }

    private int computeBestWeekendTotal(Map<LocalDate, Integer> matchesPerDay) {
      if (matchesPerDay.isEmpty()) {
        return 0;
      }
      int best = 0;
      for (Map.Entry<LocalDate, Integer> entry : matchesPerDay.entrySet()) {
        if (entry.getKey().getDayOfWeek() == DayOfWeek.SATURDAY) {
          LocalDate sunday = entry.getKey().plusDays(1);
          Integer sundayCount = matchesPerDay.get(sunday);
          if (sundayCount != null && sundayCount > 0 && entry.getValue() > 0) {
            int total = entry.getValue() + sundayCount;
            best = Math.max(best, total);
          }
        }
      }
      return best;
    }

    private GapRecovery computeGapRecovery(Map<LocalDate, Integer> matchesPerDay) {
      if (matchesPerDay.isEmpty()) {
        return new GapRecovery(0, 0);
      }
      List<LocalDate> days = new ArrayList<>(matchesPerDay.keySet());
      days.sort(Comparator.naturalOrder());

      int bestMatches = 0;
      int bestGap = 0;
      LocalDate previous = null;
      for (LocalDate day : days) {
        if (previous != null) {
          long gap = ChronoUnit.DAYS.between(previous, day);
          if (gap >= 7) {
            int matchesToday = matchesPerDay.getOrDefault(day, 0);
            if (matchesToday >= 3) {
              if (matchesToday > bestMatches || (matchesToday == bestMatches && gap > bestGap)) {
                bestMatches = matchesToday;
                bestGap = (int) gap;
              }
            }
          }
        }
        previous = day;
      }
      return new GapRecovery(bestGap, bestMatches);
    }

    private static class GapRecovery {
      final int daysGap;
      final int matchesAfterGap;

      private GapRecovery(int daysGap, int matchesAfterGap) {
        this.daysGap = daysGap;
        this.matchesAfterGap = matchesAfterGap;
      }
    }

    private static class MatchDetail {
      private final Instant playedAt;
      private final ZoneId zone;
      private final boolean win;
      private final Set<Long> partnerIds;
      private final Set<Long> opponentIds;
      private final boolean hadGuestPartner;
      private final int teamScore;
      private final int opponentScore;

      private MatchDetail(
          Instant playedAt,
          ZoneId zone,
          boolean win,
          Set<Long> partnerIds,
          Set<Long> opponentIds,
          boolean hadGuestPartner,
          int teamScore,
          int opponentScore) {
        this.playedAt = playedAt;
        this.zone = zone == null ? LADDER_ZONE : zone;
        this.win = win;
        this.partnerIds =
            partnerIds == null || partnerIds.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(partnerIds));
        this.opponentIds =
            opponentIds == null || opponentIds.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(opponentIds));
        this.hadGuestPartner = hadGuestPartner;
        this.teamScore = teamScore;
        this.opponentScore = opponentScore;
      }

      private Instant playedAt() {
        return playedAt;
      }

      private LocalDate playedDate() {
        if (playedAt == null) {
          return null;
        }
        return playedAt.atZone(zone).toLocalDate();
      }
    }
  }
}
