package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.CompetitionSuspiciousMatchFlagRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.CompetitionSuspiciousMatchFlag;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithm;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitionSuspiciousMatchDetector {

  private static final Duration MIN_MATCH_GAP = Duration.ofMinutes(10);
  private static final int MAXIMIZED_COMPONENT_THRESHOLD = 3;
  private static final int RECENT_VARIETY_MATCHES = 5;
  private static final double FULL_VARIETY_THRESHOLD = 0.80d;
  private static final int MAX_STREAK_BEFORE_MATCH = 4;
  private static final int POD_LOOKBACK_MATCHES = 8;
  private static final int POD_MIN_MATCHES = 6;
  private static final int POD_MAX_UNIQUE_OTHERS = 3;
  private static final int RAPID_PATTERN_LOOKBACK_MATCHES = 7;
  private static final int RAPID_PATTERN_REQUIRED_SHORT_GAPS = 3;
  private static final int RAPID_PATTERN_REQUIRED_PLAYERS = 2;
  private static final int REPEATED_PATTERN_SAMPLE_SIZE = 6;
  private static final double REPEATED_PATTERN_RATIO = 0.75d;
  private static final int DEFAULT_REVIEW_LIMIT = 15;
  private static final double TOP_STANDINGS_RATIO = 0.10d;
  private static final int TOP_STANDINGS_MIN_CUTOFF = 5;
  private static final int TOP_STANDINGS_MAX_CUTOFF = 25;

  private final CompetitionSuspiciousMatchFlagRepository flagRepository;
  private final MatchRepository matchRepository;
  private final LadderStandingRepository standingRepository;
  private final LadderScoringAlgorithms scoringAlgorithms;

  public CompetitionSuspiciousMatchDetector(
      CompetitionSuspiciousMatchFlagRepository flagRepository,
      MatchRepository matchRepository,
      LadderStandingRepository standingRepository,
      LadderScoringAlgorithms scoringAlgorithms) {
    this.flagRepository = flagRepository;
    this.matchRepository = matchRepository;
    this.standingRepository = standingRepository;
    this.scoringAlgorithms = scoringAlgorithms;
  }

  @Transactional
  public void reviewConfirmedCompetitionMatch(Match match) {
    if (!isEligibleCompetitionMatch(match)) {
      clearFlags(match);
      return;
    }

    LadderSeason season = match.getSeason();
    StandingLeverage standingLeverage = resolveStandingLeverage(season, match);
    if (!standingLeverage.isNearTop()) {
      clearFlags(match);
      return;
    }

    List<Match> seasonMatches =
        ensureCurrentMatchPresent(matchRepository.findConfirmedForSeasonChrono(season), match);
    int currentIndex = indexOfMatch(seasonMatches, match);
    List<Match> priorMatches =
        currentIndex <= 0 ? List.of() : List.copyOf(seasonMatches.subList(0, currentIndex));
    Map<Long, Integer> matchIndexById = new LinkedHashMap<>();
    for (int i = 0; i < seasonMatches.size(); i++) {
      Match seasonMatch = seasonMatches.get(i);
      if (seasonMatch != null && seasonMatch.getId() != null) {
        matchIndexById.put(seasonMatch.getId(), i);
      }
    }

    List<CompetitionSuspiciousMatchFlag> flags = new ArrayList<>();
    OptimizedMatchAssessment optimizedAssessment = assessOptimizedDelta(match, priorMatches);
    if (optimizedAssessment.suspicious()) {
      flags.add(
          buildFlag(
              season,
              match,
              CompetitionSuspiciousMatchFlag.ReasonCode.MAXIMIZED_DELTA,
              65 + (optimizedAssessment.componentCount() * 5),
              "This result stacked "
                  + optimizedAssessment.componentCount()
                  + " rating-boost components.",
              appendStandingContext(
                  "Triggered components: "
                      + String.join(", ", optimizedAssessment.components())
                      + ".",
                  standingLeverage)));
    }

    CompetitionSuspiciousMatchFlag repeatedFlag =
        buildRepeatedMaximizationFlag(
            season, match, optimizedAssessment, seasonMatches, matchIndexById, standingLeverage);
    if (repeatedFlag != null) {
      flags.add(repeatedFlag);
    }

    CompetitionSuspiciousMatchFlag rapidTurnaroundFlag =
        buildRapidTurnaroundFlag(season, match, priorMatches, standingLeverage);
    if (rapidTurnaroundFlag != null) {
      flags.add(rapidTurnaroundFlag);
    }

    CompetitionSuspiciousMatchFlag closedPodFlag =
        buildClosedPodFlag(season, match, priorMatches, standingLeverage);
    if (closedPodFlag != null) {
      flags.add(closedPodFlag);
    }

    flagRepository.deleteByMatch(match);
    if (!flags.isEmpty()) {
      flagRepository.saveAll(flags);
    }
  }

  @Transactional
  public void clearFlags(Match match) {
    if (match != null) {
      flagRepository.deleteByMatch(match);
    }
  }

  @Transactional(readOnly = true)
  public List<AdminReviewRow> findRecentReviewRows(LadderSeason season, int limit) {
    if (season == null || season.getId() == null) {
      return List.of();
    }
    int resolvedLimit = Math.max(1, limit);
    List<CompetitionSuspiciousMatchFlag> flags =
        flagRepository.findRecentBySeason(
            season, PageRequest.of(0, Math.max(resolvedLimit * 4, DEFAULT_REVIEW_LIMIT * 4)));
    if (flags.isEmpty()) {
      return List.of();
    }

    Map<Long, List<CompetitionSuspiciousMatchFlag>> flagsByMatchId = new LinkedHashMap<>();
    Set<Long> matchIds = new LinkedHashSet<>();
    for (CompetitionSuspiciousMatchFlag flag : flags) {
      Match flaggedMatch = flag.getMatch();
      if (flaggedMatch == null || flaggedMatch.getId() == null) {
        continue;
      }
      flagsByMatchId.computeIfAbsent(flaggedMatch.getId(), ignored -> new ArrayList<>()).add(flag);
      matchIds.add(flaggedMatch.getId());
    }
    Map<Long, Match> matchesById =
        matchRepository.findAllByIdInWithUsers(matchIds).stream()
            .filter(match -> match.getId() != null)
            .collect(
                Collectors.toMap(
                    Match::getId, match -> match, (left, right) -> left, LinkedHashMap::new));

    return flagsByMatchId.entrySet().stream()
        .map(entry -> toReviewRow(matchesById.get(entry.getKey()), entry.getValue()))
        .filter(Objects::nonNull)
        .sorted(
            Comparator.comparing(AdminReviewRow::highestSeverity)
                .reversed()
                .thenComparing(
                    AdminReviewRow::playedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                    AdminReviewRow::matchId, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(resolvedLimit)
        .collect(Collectors.toList());
  }

  private AdminReviewRow toReviewRow(Match match, List<CompetitionSuspiciousMatchFlag> flags) {
    if (match == null || match.getId() == null || flags == null || flags.isEmpty()) {
      return null;
    }
    List<AdminReasonRow> reasons =
        flags.stream()
            .sorted(
                Comparator.comparingInt(CompetitionSuspiciousMatchFlag::getSeverity)
                    .reversed()
                    .thenComparing(
                        CompetitionSuspiciousMatchFlag::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
            .map(
                flag ->
                    new AdminReasonRow(
                        labelFor(flag.getReasonCode()),
                        flag.getSummary(),
                        flag.getDetails(),
                        flag.getSeverity()))
            .collect(Collectors.toList());
    int highestSeverity = reasons.stream().mapToInt(AdminReasonRow::severity).max().orElse(0);
    return new AdminReviewRow(
        match.getId(),
        matchTime(match),
        matchupLabel(match),
        scoreline(match),
        highestSeverity,
        reasons);
  }

  private StandingLeverage resolveStandingLeverage(LadderSeason season, Match match) {
    if (season == null || season.getId() == null || standingRepository == null || match == null) {
      return StandingLeverage.none();
    }
    List<LadderStanding> standings = standingRepository.findBySeasonOrderByRankNoAsc(season);
    if (standings == null || standings.isEmpty()) {
      return StandingLeverage.none();
    }

    int totalPlayers = standings.size();
    int cutoff =
        Math.min(
            totalPlayers,
            Math.min(
                TOP_STANDINGS_MAX_CUTOFF,
                Math.max(
                    TOP_STANDINGS_MIN_CUTOFF,
                    (int) Math.ceil(totalPlayers * TOP_STANDINGS_RATIO))));
    Map<Long, LadderStanding> standingByUserId =
        standings.stream()
            .filter(standing -> standing.getUser() != null && standing.getUser().getId() != null)
            .collect(
                Collectors.toMap(
                    standing -> standing.getUser().getId(),
                    standing -> standing,
                    (left, right) -> left));

    LinkedHashMap<Long, User> relevantUsers = new LinkedHashMap<>();
    for (User winner : collectWinningRealParticipants(match)) {
      relevantUsers.putIfAbsent(winner.getId(), winner);
    }
    User logger = match.getLoggedBy();
    if (logger != null && logger.getId() != null) {
      relevantUsers.putIfAbsent(logger.getId(), logger);
    }

    List<StandingImpact> impacts =
        relevantUsers.values().stream()
            .map(
                user -> {
                  LadderStanding standing = standingByUserId.get(user.getId());
                  if (standing == null || standing.getRank() <= 0 || standing.getRank() > cutoff) {
                    return null;
                  }
                  return new StandingImpact(user, standing.getRank());
                })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(StandingImpact::rank))
            .collect(Collectors.toList());
    if (impacts.isEmpty()) {
      return StandingLeverage.none();
    }
    return new StandingLeverage(true, cutoff, totalPlayers, impacts);
  }

  private String appendStandingContext(String baseDetails, StandingLeverage standingLeverage) {
    if (standingLeverage == null || !standingLeverage.isNearTop()) {
      return baseDetails;
    }
    String standingText =
        standingLeverage.topImpacts().stream()
            .map(impact -> displayName(impact.user()) + " is rank #" + impact.rank())
            .collect(Collectors.joining(", "));
    String suffix =
        "Top-standings impact: "
            + standingText
            + " (top review cutoff #"
            + standingLeverage.cutoffRank()
            + " of "
            + standingLeverage.totalPlayers()
            + ").";
    if (baseDetails == null || baseDetails.isBlank()) {
      return suffix;
    }
    return baseDetails + " " + suffix;
  }

  private CompetitionSuspiciousMatchFlag buildRepeatedMaximizationFlag(
      LadderSeason season,
      Match match,
      OptimizedMatchAssessment currentAssessment,
      List<Match> seasonMatches,
      Map<Long, Integer> matchIndexById,
      StandingLeverage standingLeverage) {
    if (!currentAssessment.suspicious()) {
      return null;
    }
    User logger = match.getLoggedBy();
    if (logger == null || logger.getId() == null) {
      return null;
    }

    List<Match> recentLoggedMatches =
        seasonMatches.stream()
            .filter(seasonMatch -> sameUser(seasonMatch.getLoggedBy(), logger))
            .filter(seasonMatch -> matchTime(seasonMatch).compareTo(matchTime(match)) <= 0)
            .collect(Collectors.toList());
    if (recentLoggedMatches.size() < REPEATED_PATTERN_SAMPLE_SIZE) {
      return null;
    }

    int startIndex = Math.max(0, recentLoggedMatches.size() - REPEATED_PATTERN_SAMPLE_SIZE);
    List<Match> sample = recentLoggedMatches.subList(startIndex, recentLoggedMatches.size());
    int suspiciousCount = 0;
    for (Match loggedMatch : sample) {
      Integer loggedIndex =
          loggedMatch.getId() != null ? matchIndexById.get(loggedMatch.getId()) : null;
      List<Match> priorHistory =
          (loggedIndex == null || loggedIndex <= 0)
              ? List.of()
              : List.copyOf(seasonMatches.subList(0, loggedIndex));
      if (assessOptimizedDelta(loggedMatch, priorHistory).suspicious()) {
        suspiciousCount++;
      }
    }

    double ratio = sample.isEmpty() ? 0.0d : ((double) suspiciousCount) / sample.size();
    if (ratio < REPEATED_PATTERN_RATIO) {
      return null;
    }

    String loggerName = displayName(logger);
    String percent = String.format(Locale.US, "%.0f%%", ratio * 100.0d);
    return buildFlag(
        season,
        match,
        CompetitionSuspiciousMatchFlag.ReasonCode.REPEATED_MAXIMIZED_DELTA_PATTERN,
        85,
        loggerName + " repeatedly logs highly optimized competition wins.",
        appendStandingContext(
            suspiciousCount
                + " of the last "
                + sample.size()
                + " confirmed competition matches logged by "
                + loggerName
                + " met the maximized-delta threshold ("
                + percent
                + ").",
            standingLeverage));
  }

  private CompetitionSuspiciousMatchFlag buildRapidTurnaroundFlag(
      LadderSeason season,
      Match match,
      List<Match> priorMatches,
      StandingLeverage standingLeverage) {
    List<Match> historyWithCurrent = new ArrayList<>(priorMatches);
    historyWithCurrent.add(match);

    List<PlayerRapidPattern> persistentPlayers = new ArrayList<>();
    for (User participant : collectRealParticipants(match)) {
      PlayerRapidPattern pattern =
          assessRapidPatternForPlayer(participant, historyWithCurrent, match);
      if (pattern != null) {
        persistentPlayers.add(pattern);
      }
    }
    LoggerRapidPattern loggerPattern = assessRapidPatternForLogger(match, historyWithCurrent);

    if (loggerPattern == null && persistentPlayers.size() < RAPID_PATTERN_REQUIRED_PLAYERS) {
      return null;
    }

    persistentPlayers.sort(
        Comparator.comparingInt(PlayerRapidPattern::rapidGapCount)
            .reversed()
            .thenComparing(pattern -> displayName(pattern.player())));
    String summary;
    List<String> details = new ArrayList<>();
    int severity = 82;
    if (loggerPattern != null) {
      summary =
          "Persistent sub-10-minute timing pattern across recent matches logged by "
              + displayName(loggerPattern.logger())
              + ".";
      details.add(
          displayName(loggerPattern.logger())
              + " logged "
              + loggerPattern.rapidGapCount()
              + " sub-10-minute gaps in the last "
              + loggerPattern.sampleMatchCount()
              + " logged matches, including a "
              + loggerPattern.currentGap().toMinutes()
              + "-minute gap since match #"
              + safeMatchId(loggerPattern.previousMatchId())
              + ".");
      severity = 88;
    } else {
      summary = "Persistent sub-10-minute timing pattern across the current player group.";
    }
    persistentPlayers.forEach(
        pattern ->
            details.add(
                displayName(pattern.player())
                    + " had "
                    + pattern.rapidGapCount()
                    + " sub-10-minute gaps in the last "
                    + pattern.sampleMatchCount()
                    + " matches, including a "
                    + pattern.currentGap().toMinutes()
                    + "-minute gap since match #"
                    + safeMatchId(pattern.previousMatchId())
                    + "."));
    return buildFlag(
        season,
        match,
        CompetitionSuspiciousMatchFlag.ReasonCode.RAPID_TURNAROUND,
        severity,
        summary,
        appendStandingContext(String.join("; ", details), standingLeverage));
  }

  private CompetitionSuspiciousMatchFlag buildClosedPodFlag(
      LadderSeason season,
      Match match,
      List<Match> priorMatches,
      StandingLeverage standingLeverage) {
    List<User> realParticipants = collectRealParticipants(match);
    if (realParticipants.size() < 2) {
      return null;
    }

    List<PlayerPod> closedPods = new ArrayList<>();
    for (User participant : realParticipants) {
      List<Match> recentMatches =
          recentMatchesForPlayer(participant, priorMatches, POD_LOOKBACK_MATCHES);
      if (recentMatches.size() < POD_MIN_MATCHES) {
        continue;
      }
      Set<Long> uniqueOthers = new LinkedHashSet<>();
      for (Match recentMatch : recentMatches) {
        uniqueOthers.addAll(otherRealParticipantIds(recentMatch, participant));
      }
      if (uniqueOthers.size() <= POD_MAX_UNIQUE_OTHERS) {
        closedPods.add(new PlayerPod(participant, recentMatches.size(), uniqueOthers.size()));
      }
    }
    if (closedPods.size() < Math.min(2, realParticipants.size())) {
      return null;
    }

    closedPods.sort(
        Comparator.comparingInt(PlayerPod::uniqueOthers)
            .thenComparing(pod -> displayName(pod.player())));
    String details =
        closedPods.stream()
            .map(
                pod ->
                    displayName(pod.player())
                        + " saw only "
                        + pod.uniqueOthers()
                        + " unique real opponents/partners across the last "
                        + pod.sampleSize()
                        + " matches")
            .collect(Collectors.joining("; "));
    return buildFlag(
        season,
        match,
        CompetitionSuspiciousMatchFlag.ReasonCode.CLOSED_PLAYER_POD,
        70 + Math.min(15, closedPods.size() * 5),
        "Current participants are playing inside an unusually small repeated pod.",
        appendStandingContext(details, standingLeverage));
  }

  private OptimizedMatchAssessment assessOptimizedDelta(Match match, List<Match> priorMatches) {
    if (match == null) {
      return OptimizedMatchAssessment.notSuspicious();
    }

    LadderScoringAlgorithm algorithm = scoringAlgorithms.resolve(match.getSeason());
    List<String> components = new ArrayList<>();
    int baseStep = algorithm.computeBaseStep(match);
    if (isNearMaxBaseStep(algorithm, baseStep)) {
      components.add("near-max scoreline");
    }
    if (algorithm.computeGuestScale(match) >= 0.99d && match.getGuestCount() == 0) {
      components.add("no guest scaling");
    }

    List<User> winners = collectWinningRealParticipants(match);
    if (winners.stream().anyMatch(winner -> hasNearFullVariety(winner, priorMatches))) {
      components.add("near-full recent variety");
    }
    int strongestPreMatchStreak =
        winners.stream()
            .mapToInt(winner -> computePreMatchWinStreak(winner, priorMatches))
            .max()
            .orElse(0);
    if (strongestPreMatchStreak >= MAX_STREAK_BEFORE_MATCH) {
      components.add("hot streak");
    }

    return new OptimizedMatchAssessment(
        components.size() >= MAXIMIZED_COMPONENT_THRESHOLD,
        components.size(),
        List.copyOf(components));
  }

  private boolean hasNearFullVariety(User winner, List<Match> priorMatches) {
    if (winner == null || winner.getId() == null) {
      return false;
    }
    List<Match> recentMatches =
        recentMatchesForPlayer(winner, priorMatches, RECENT_VARIETY_MATCHES);
    if (recentMatches.size() < 3) {
      return false;
    }

    Set<String> uniqueSlots = new LinkedHashSet<>();
    int seatOpportunities = 0;
    for (Match recentMatch : recentMatches) {
      User partner = partnerFor(recentMatch, winner);
      if (partner != null && partner.getId() != null) {
        seatOpportunities++;
        uniqueSlots.add("P:" + partner.getId());
      }
      for (User opponent : opponentsFor(recentMatch, winner)) {
        if (opponent == null || opponent.getId() == null) {
          continue;
        }
        seatOpportunities++;
        uniqueSlots.add("O:" + opponent.getId());
      }
    }
    return seatOpportunities > 0
        && (uniqueSlots.size() / (double) seatOpportunities) >= FULL_VARIETY_THRESHOLD;
  }

  private int computePreMatchWinStreak(User player, List<Match> priorMatches) {
    int streak = 0;
    for (Match recentMatch : recentMatchesForPlayer(player, priorMatches, POD_LOOKBACK_MATCHES)) {
      if (!Boolean.TRUE.equals(didPlayerWin(recentMatch, player))) {
        break;
      }
      streak++;
    }
    return streak;
  }

  private List<Match> recentMatchesForPlayer(User player, List<Match> matches, int limit) {
    if (player == null || player.getId() == null || matches == null || matches.isEmpty()) {
      return List.of();
    }
    return matches.stream()
        .filter(match -> participates(match, player))
        .sorted(
            Comparator.comparing(this::matchTime)
                .reversed()
                .thenComparing(Match::getId, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(limit)
        .collect(Collectors.toList());
  }

  private PlayerRapidPattern assessRapidPatternForPlayer(
      User player, List<Match> matches, Match currentMatch) {
    List<Match> recentMatches =
        recentMatchesForPlayer(player, matches, RAPID_PATTERN_LOOKBACK_MATCHES);
    RapidPattern pattern = assessRapidPattern(recentMatches, currentMatch);
    if (pattern == null) {
      return null;
    }
    return new PlayerRapidPattern(
        player,
        pattern.rapidGapCount(),
        pattern.sampleMatchCount(),
        pattern.currentGap(),
        pattern.previousMatchId());
  }

  private LoggerRapidPattern assessRapidPatternForLogger(Match match, List<Match> matches) {
    User logger = match != null ? match.getLoggedBy() : null;
    if (logger == null || logger.getId() == null || matches == null || matches.isEmpty()) {
      return null;
    }
    List<Match> recentLoggedMatches =
        matches.stream()
            .filter(candidate -> sameUser(candidate.getLoggedBy(), logger))
            .sorted(
                Comparator.comparing(this::matchTime)
                    .reversed()
                    .thenComparing(Match::getId, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(RAPID_PATTERN_LOOKBACK_MATCHES)
            .collect(Collectors.toList());
    RapidPattern pattern = assessRapidPattern(recentLoggedMatches, match);
    if (pattern == null) {
      return null;
    }
    return new LoggerRapidPattern(
        logger,
        pattern.rapidGapCount(),
        pattern.sampleMatchCount(),
        pattern.currentGap(),
        pattern.previousMatchId());
  }

  private RapidPattern assessRapidPattern(List<Match> recentMatches, Match currentMatch) {
    if (recentMatches == null || recentMatches.size() < RAPID_PATTERN_REQUIRED_SHORT_GAPS + 1) {
      return null;
    }

    List<Match> chronological = new ArrayList<>(recentMatches);
    chronological.sort(
        Comparator.comparing(this::matchTime)
            .thenComparing(Match::getId, Comparator.nullsLast(Comparator.naturalOrder())));

    int rapidGapCount = 0;
    Duration currentGap = null;
    Long previousMatchId = null;
    for (int i = 1; i < chronological.size(); i++) {
      Match previous = chronological.get(i - 1);
      Match candidate = chronological.get(i);
      Duration gap = Duration.between(matchTime(previous), matchTime(candidate));
      if (!gap.isNegative() && gap.compareTo(MIN_MATCH_GAP) < 0) {
        rapidGapCount++;
        if (currentMatch != null
            && candidate.getId() != null
            && currentMatch.getId() != null
            && Objects.equals(candidate.getId(), currentMatch.getId())) {
          currentGap = gap;
          previousMatchId = previous.getId();
        }
      }
    }

    if (currentGap == null || rapidGapCount < RAPID_PATTERN_REQUIRED_SHORT_GAPS) {
      return null;
    }
    return new RapidPattern(rapidGapCount, chronological.size(), currentGap, previousMatchId);
  }

  private List<User> collectRealParticipants(Match match) {
    LinkedHashMap<Long, User> participants = new LinkedHashMap<>();
    addRealParticipant(
        participants, match != null ? match.getA1() : null, match != null && match.isA1Guest());
    addRealParticipant(
        participants, match != null ? match.getA2() : null, match != null && match.isA2Guest());
    addRealParticipant(
        participants, match != null ? match.getB1() : null, match != null && match.isB1Guest());
    addRealParticipant(
        participants, match != null ? match.getB2() : null, match != null && match.isB2Guest());
    return new ArrayList<>(participants.values());
  }

  private List<User> collectWinningRealParticipants(Match match) {
    if (match == null || match.getScoreA() == match.getScoreB()) {
      return List.of();
    }
    List<User> winners = new ArrayList<>();
    boolean teamAWon = match.getScoreA() > match.getScoreB();
    if (teamAWon) {
      addWinner(winners, match.getA1(), match.isA1Guest());
      addWinner(winners, match.getA2(), match.isA2Guest());
    } else {
      addWinner(winners, match.getB1(), match.isB1Guest());
      addWinner(winners, match.getB2(), match.isB2Guest());
    }
    return winners;
  }

  private void addRealParticipant(Map<Long, User> participants, User player, boolean guest) {
    if (!guest && player != null && player.getId() != null) {
      participants.putIfAbsent(player.getId(), player);
    }
  }

  private void addWinner(Collection<User> winners, User player, boolean guest) {
    if (!guest && player != null && player.getId() != null) {
      winners.add(player);
    }
  }

  private boolean participates(Match match, User player) {
    if (match == null || player == null || player.getId() == null) {
      return false;
    }
    return sameUser(match.getA1(), player)
        || sameUser(match.getA2(), player)
        || sameUser(match.getB1(), player)
        || sameUser(match.getB2(), player);
  }

  private Set<Long> otherRealParticipantIds(Match match, User player) {
    return collectRealParticipants(match).stream()
        .filter(other -> !sameUser(other, player))
        .map(User::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private User partnerFor(Match match, User player) {
    if (sameUser(match != null ? match.getA1() : null, player)) {
      return match.isA2Guest() ? null : match.getA2();
    }
    if (sameUser(match != null ? match.getA2() : null, player)) {
      return match.isA1Guest() ? null : match.getA1();
    }
    if (sameUser(match != null ? match.getB1() : null, player)) {
      return match.isB2Guest() ? null : match.getB2();
    }
    if (sameUser(match != null ? match.getB2() : null, player)) {
      return match.isB1Guest() ? null : match.getB1();
    }
    return null;
  }

  private List<User> opponentsFor(Match match, User player) {
    List<User> opponents = new ArrayList<>();
    if (sameUser(match != null ? match.getA1() : null, player)
        || sameUser(match != null ? match.getA2() : null, player)) {
      addOpponent(opponents, match.getB1(), match.isB1Guest());
      addOpponent(opponents, match.getB2(), match.isB2Guest());
    } else if (sameUser(match != null ? match.getB1() : null, player)
        || sameUser(match != null ? match.getB2() : null, player)) {
      addOpponent(opponents, match.getA1(), match.isA1Guest());
      addOpponent(opponents, match.getA2(), match.isA2Guest());
    }
    return opponents;
  }

  private void addOpponent(List<User> opponents, User candidate, boolean guest) {
    if (!guest && candidate != null && candidate.getId() != null) {
      opponents.add(candidate);
    }
  }

  private Boolean didPlayerWin(Match match, User player) {
    if (match == null
        || player == null
        || player.getId() == null
        || match.getScoreA() == match.getScoreB()) {
      return null;
    }
    if (sameUser(match.getA1(), player) || sameUser(match.getA2(), player)) {
      return match.getScoreA() > match.getScoreB();
    }
    if (sameUser(match.getB1(), player) || sameUser(match.getB2(), player)) {
      return match.getScoreB() > match.getScoreA();
    }
    return null;
  }

  private boolean sameUser(User left, User right) {
    return left != null
        && right != null
        && left.getId() != null
        && Objects.equals(left.getId(), right.getId());
  }

  private boolean isEligibleCompetitionMatch(Match match) {
    if (match == null
        || match.getId() == null
        || match.getSeason() == null
        || match.getSeason().getLadderConfig() == null) {
      return false;
    }
    return match.getState() == MatchState.CONFIRMED
        && match.getSeason().getLadderConfig().isCompetitionType()
        && !match.isExcludeFromStandings()
        && !match.hasGuestOnlyOpposingTeam();
  }

  private List<Match> ensureCurrentMatchPresent(List<Match> matches, Match current) {
    List<Match> seasonMatches = matches == null ? new ArrayList<>() : new ArrayList<>(matches);
    boolean present =
        seasonMatches.stream()
            .anyMatch(
                match ->
                    match != null
                        && current != null
                        && Objects.equals(match.getId(), current.getId()));
    if (!present && current != null) {
      seasonMatches.add(current);
      seasonMatches.sort(
          Comparator.comparing(this::matchTime)
              .thenComparing(Match::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    }
    return seasonMatches;
  }

  private int indexOfMatch(List<Match> matches, Match match) {
    if (matches == null || match == null || match.getId() == null) {
      return -1;
    }
    for (int i = 0; i < matches.size(); i++) {
      Match candidate = matches.get(i);
      if (candidate != null && Objects.equals(candidate.getId(), match.getId())) {
        return i;
      }
    }
    return -1;
  }

  private boolean isNearMaxBaseStep(LadderScoringAlgorithm algorithm, int baseStep) {
    if (algorithm == null) {
      return baseStep >= 14;
    }
    return switch (algorithm.key()) {
      case MARGIN_CURVE_V1 -> baseStep >= 15;
      case BALANCED_V1 -> baseStep >= 14;
    };
  }

  private CompetitionSuspiciousMatchFlag buildFlag(
      LadderSeason season,
      Match match,
      CompetitionSuspiciousMatchFlag.ReasonCode reasonCode,
      int severity,
      String summary,
      String details) {
    CompetitionSuspiciousMatchFlag flag = new CompetitionSuspiciousMatchFlag();
    flag.setSeason(season);
    flag.setMatch(match);
    flag.setReasonCode(reasonCode);
    flag.setSeverity(severity);
    flag.setSummary(summary);
    flag.setDetails(details);
    flag.setCreatedAt(Instant.now());
    return flag;
  }

  private String labelFor(CompetitionSuspiciousMatchFlag.ReasonCode reasonCode) {
    if (reasonCode == null) {
      return "Review";
    }
    return switch (reasonCode) {
      case MAXIMIZED_DELTA -> "Delta Maximization";
      case REPEATED_MAXIMIZED_DELTA_PATTERN -> "Repeated Maximization";
      case RAPID_TURNAROUND -> "Too Fast";
      case CLOSED_PLAYER_POD -> "Closed Pod";
    };
  }

  private String matchupLabel(Match match) {
    boolean teamAWon = match != null && match.getScoreA() > match.getScoreB();
    String winners =
        teamAWon
            ? teamLabel(match.getA1(), match.isA1Guest(), match.getA2(), match.isA2Guest())
            : teamLabel(match.getB1(), match.isB1Guest(), match.getB2(), match.isB2Guest());
    String losers =
        teamAWon
            ? teamLabel(match.getB1(), match.isB1Guest(), match.getB2(), match.isB2Guest())
            : teamLabel(match.getA1(), match.isA1Guest(), match.getA2(), match.isA2Guest());
    return winners + " def. " + losers;
  }

  private String scoreline(Match match) {
    if (match == null) {
      return "";
    }
    boolean teamAWon = match.getScoreA() > match.getScoreB();
    int winnerScore = teamAWon ? match.getScoreA() : match.getScoreB();
    int loserScore = teamAWon ? match.getScoreB() : match.getScoreA();
    return winnerScore + "-" + loserScore;
  }

  private String teamLabel(User p1, boolean guest1, User p2, boolean guest2) {
    List<String> names = new ArrayList<>();
    if (!guest1 && p1 != null) {
      names.add(displayName(p1));
    }
    if (!guest2 && p2 != null) {
      names.add(displayName(p2));
    }
    return names.isEmpty() ? "Guest Squad" : String.join(" & ", names);
  }

  private String displayName(User user) {
    if (user == null) {
      return "Guest";
    }
    return com.w3llspring.fhpb.web.util.UserPublicName.forUser(user);
  }

  private String safeMatchId(Long matchId) {
    return matchId != null ? Long.toString(matchId) : "?";
  }

  private Instant matchTime(Match match) {
    if (match == null) {
      return Instant.EPOCH;
    }
    if (match.getPlayedAt() != null) {
      return match.getPlayedAt();
    }
    if (match.getCreatedAt() != null) {
      return match.getCreatedAt();
    }
    return Instant.EPOCH;
  }

  public record AdminReviewRow(
      Long matchId,
      Instant playedAt,
      String matchup,
      String scoreline,
      int highestSeverity,
      List<AdminReasonRow> reasons) {}

  public record AdminReasonRow(String label, String summary, String details, int severity) {}

  private record OptimizedMatchAssessment(
      boolean suspicious, int componentCount, List<String> components) {
    private static OptimizedMatchAssessment notSuspicious() {
      return new OptimizedMatchAssessment(false, 0, List.of());
    }
  }

  private record RapidPattern(
      int rapidGapCount, int sampleMatchCount, Duration currentGap, Long previousMatchId) {}

  private record PlayerRapidPattern(
      User player,
      int rapidGapCount,
      int sampleMatchCount,
      Duration currentGap,
      Long previousMatchId) {}

  private record LoggerRapidPattern(
      User logger,
      int rapidGapCount,
      int sampleMatchCount,
      Duration currentGap,
      Long previousMatchId) {}

  private record PlayerPod(User player, int sampleSize, int uniqueOthers) {}

  private record StandingImpact(User user, int rank) {}

  private record StandingLeverage(
      boolean isNearTop, int cutoffRank, int totalPlayers, List<StandingImpact> topImpacts) {
    private static StandingLeverage none() {
      return new StandingLeverage(false, 0, 0, List.of());
    }
  }
}
