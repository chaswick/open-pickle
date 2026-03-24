package com.w3llspring.fhpb.web.service.scoring;

import com.w3llspring.fhpb.web.model.BandPosition;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class BalancedV1LadderScoringAlgorithm implements LadderScoringAlgorithm {
  private static final double VARIETY_TARGET = 8.0;
  private static final double ADJACENT_WEIGHT = 0.85;
  private static final double DISTANT_WEIGHT = 0.35;
  private static final double ACTIVITY_KICKER = 1.15;
  private static final double BOTTOM_BAND_KICKER = 1.10;
  private static final double STRONG_BOTTOM_WIN_BOOST = 1.05;
  private static final double LOSS_FLOOR_RATIO = 0.75;
  private static final LadderScoringProfile PROFILE =
      new LadderScoringProfile(
          LadderConfig.ScoringAlgorithm.BALANCED_V1,
          "Balanced V1",
          VARIETY_TARGET,
          LOSS_FLOOR_RATIO,
          "This ladder uses Balanced V1: score margin sets the base step, then opponent variety, streaks, "
              + "division matchup guards, and guest scaling shape the final swing. Two-point games start "
              + "at 6, close wins reach 8, standard wins land at 11, blowouts peak at 14, variety tops "
              + "out at 8 weighted opponent points, and losses stay capped near 75% of the base step.");

  @Override
  public LadderConfig.ScoringAlgorithm key() {
    return LadderConfig.ScoringAlgorithm.BALANCED_V1;
  }

  @Override
  public LadderScoringProfile profile() {
    return PROFILE;
  }

  @Override
  public Duration historyWindow() {
    return Duration.ofDays(56);
  }

  @Override
  public LadderScoringResult score(LadderScoringRequest request) {
    Match match = request.match();
    int baseStep = computeBaseStep(match);
    double guestScale = computeGuestScale(match);
    if (isEffectivelyExcludedFromStandings(match)) {
      return LadderScoringResult.empty(baseStep, guestScale);
    }

    List<Participant> active =
        collectParticipants(match).stream()
            .filter(Participant::isEligible)
            .collect(Collectors.toList());
    if (active.isEmpty()) {
      return LadderScoringResult.empty(baseStep, guestScale);
    }

    Map<Long, LadderStanding> standingsByUser =
        request.standingsByUser() == null ? Map.of() : request.standingsByUser();
    Map<Long, BandPosition> bandByUser =
        request.bandByUser() == null ? Map.of() : request.bandByUser();
    Map<Integer, Integer> topQuartileLimit =
        request.topQuartileLimit() == null ? Map.of() : request.topQuartileLimit();
    int maxBandIndex = request.maxBandIndex() > 0 ? request.maxBandIndex() : 1;
    List<Match> history =
        request.history() == null
            ? List.of()
            : request.history().stream()
                .filter(past -> !isEffectivelyExcludedFromStandings(past))
                .collect(Collectors.toList());

    Map<Long, PlayerSnapshot> snapshots = new HashMap<>();
    for (Participant participant : active) {
      PlayerSnapshot snapshot =
          createSnapshot(participant, standingsByUser, bandByUser, maxBandIndex, topQuartileLimit);
      snapshots.put(snapshot.user.getId(), snapshot);
    }

    TeamProfile teamA = TeamProfile.build(Team.A, active, snapshots, maxBandIndex);
    TeamProfile teamB = TeamProfile.build(Team.B, active, snapshots, maxBandIndex);

    computeVarietyMetrics(snapshots, history, match, bandByUser, maxBandIndex, topQuartileLimit);
    computeStreakMetrics(snapshots, history);

    Map<User, Integer> adjustments = new LinkedHashMap<>();
    Map<Long, LadderScoringExplanation> explanationMap = new HashMap<>();
    for (Participant participant : active) {
      PlayerSnapshot snapshot = snapshots.get(participant.user.getId());
      if (snapshot == null) {
        continue;
      }
      TeamProfile selfTeam = participant.team == Team.A ? teamA : teamB;
      TeamProfile oppTeam = participant.team == Team.A ? teamB : teamA;

      double base = baseStep * guestScale;
      double value =
          base
              * snapshot.varietyScale
              * (participant.winner ? snapshot.streakScaleWin : snapshot.streakScaleLoss);
      value *= computeEdgeScale(participant, snapshot, selfTeam, oppTeam, maxBandIndex);

      if (!participant.winner) {
        value *= -1.0;
      }

      value = applyVarietyGateForElites(value, snapshot, baseStep);

      if (participant.winner) {
        if (snapshot.varietyScore >= VARIETY_TARGET && isUpsetWin(snapshot, oppTeam)) {
          value *= ACTIVITY_KICKER;
        }
        value = applyTopBandClamp(value, snapshot, selfTeam, oppTeam, baseStep);
      } else {
        value = applyDemotionGuard(value, snapshot, baseStep);
        value = applyBottomLossGuard(value, snapshot, baseStep);
      }

      value = applyLossFloor(value, baseStep, guestScale);

      int rounded = (int) Math.round(value);
      if (rounded == 0 && Math.abs(value) >= 0.45) {
        rounded = participant.winner ? 1 : -1;
      }
      if (rounded != 0) {
        adjustments.merge(participant.user, rounded, Integer::sum);
      }

      List<String> steps = new ArrayList<>();
      steps.add(String.format("Score margin set the base swing at %d.", baseStep));
      steps.add(
          String.format(
              "Guest scaling set the match at %.0f%% of full value.", guestScale * 100.0d));
      steps.add(
          String.format(
              "Balanced V1 then applied opponent-variety, streak, and matchup confidence rules to reach %+d.",
              rounded));
      explanationMap.put(snapshot.user.getId(), new LadderScoringExplanation(steps));
    }

    return new LadderScoringResult(adjustments, explanationMap, baseStep, guestScale);
  }

  @Override
  public int computeBaseStep(Match match) {
    int diff = Math.max(2, Math.abs(match.getScoreA() - match.getScoreB()));
    if (diff == 2) {
      return 6;
    }
    if (diff <= 4) {
      return 8;
    }
    if (diff <= 10) {
      return 11;
    }
    return 14;
  }

  @Override
  public double computeGuestScale(Match match) {
    int guests = match.getGuestCount();
    if (guests == 0) {
      return 1.0;
    }
    if (guests == 1) {
      return 0.6;
    }
    if (guests == 2) {
      return 0.4;
    }
    return 0.25;
  }

  private PlayerSnapshot createSnapshot(
      Participant participant,
      Map<Long, LadderStanding> standings,
      Map<Long, BandPosition> bandByUser,
      int maxBandIndex,
      Map<Integer, Integer> topQuartileLimit) {
    User user = participant.user;
    long userId = user.getId();
    LadderStanding standing = standings.get(userId);
    int rank = standing != null ? standing.getRank() : Integer.MAX_VALUE;
    BandPosition bp = bandByUser.get(userId);
    int bandIndex = resolveBandIndex(bp, maxBandIndex);
    int position = resolvePositionInBand(bp);
    int quartileThreshold = topQuartileLimit.getOrDefault(bandIndex, 0);
    boolean topQuartile = quartileThreshold > 0 && position > 0 && position <= quartileThreshold;
    boolean topBand = bandIndex == 1;
    boolean bottomBand = bandIndex >= maxBandIndex;
    return new PlayerSnapshot(user, bandIndex, position, rank, topQuartile, topBand, bottomBand);
  }

  private void computeVarietyMetrics(
      Map<Long, PlayerSnapshot> snapshots,
      List<Match> history,
      Match currentMatch,
      Map<Long, BandPosition> bandByUser,
      int maxBandIndex,
      Map<Integer, Integer> topQuartileLimit) {
    for (PlayerSnapshot snapshot : snapshots.values()) {
      Map<Long, Double> weights = new HashMap<>();

      for (Match past : history) {
        Boolean outcome = didPlayerWin(past, snapshot.user);
        if (outcome == null) {
          continue;
        }
        for (User opponent : collectOpponents(past, snapshot.user)) {
          if (opponent == null || opponent.getId() == null) {
            continue;
          }
          double weight =
              computeVarietyWeight(
                  snapshot, opponent, outcome, bandByUser, maxBandIndex, topQuartileLimit);
          weights.merge(opponent.getId(), weight, Math::max);
        }
      }

      Boolean currentOutcome = didPlayerWin(currentMatch, snapshot.user);
      if (currentOutcome != null) {
        for (User opponent : collectOpponents(currentMatch, snapshot.user)) {
          if (opponent == null || opponent.getId() == null) {
            continue;
          }
          double weight =
              computeVarietyWeight(
                  snapshot, opponent, currentOutcome, bandByUser, maxBandIndex, topQuartileLimit);
          weights.merge(opponent.getId(), weight, Math::max);
        }
      }

      double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
      snapshot.varietyScore = total;
      snapshot.varietyScale = Math.min(1.0, total / VARIETY_TARGET);
    }
  }

  private double computeVarietyWeight(
      PlayerSnapshot player,
      User opponent,
      boolean playerWon,
      Map<Long, BandPosition> bandByUser,
      int maxBandIndex,
      Map<Integer, Integer> topQuartileLimit) {
    BandPosition opponentPosition = bandByUser.get(opponent.getId());
    int opponentBand = resolveBandIndex(opponentPosition, maxBandIndex);
    int diff = Math.abs(player.bandIndex - opponentBand);
    double weight;
    if (diff == 0) {
      weight = 1.0;
    } else if (diff == 1) {
      weight = ADJACENT_WEIGHT;
    } else {
      weight = DISTANT_WEIGHT;
    }

    int quartileThreshold = topQuartileLimit.getOrDefault(opponentBand, 0);
    int opponentPositionInBand = resolvePositionInBand(opponentPosition);
    boolean opponentTopQuartile =
        quartileThreshold > 0
            && opponentPositionInBand > 0
            && opponentPositionInBand <= quartileThreshold;

    if (player.topBand && opponentBand == 1 && opponentTopQuartile && playerWon) {
      weight = ADJACENT_WEIGHT;
    } else if (player.bottomBand
        && opponentBand == maxBandIndex
        && opponentTopQuartile
        && playerWon) {
      weight = ADJACENT_WEIGHT;
    }

    return weight;
  }

  private void computeStreakMetrics(Map<Long, PlayerSnapshot> snapshots, List<Match> history) {
    for (PlayerSnapshot snapshot : snapshots.values()) {
      StreakInfo streak = evaluateStreak(history, snapshot.user);
      snapshot.winStreak = streak.winStreak;
      snapshot.lossStreak = streak.lossStreak;
      snapshot.streakScaleWin = computeWinStreakScale(streak.winStreak, snapshot.rank);
      snapshot.streakScaleLoss = computeLossStreakScale(streak.lossStreak);
    }
  }

  private StreakInfo evaluateStreak(List<Match> history, User user) {
    int wins = 0;
    int losses = 0;
    Boolean streakType = null;
    for (Match past : history) {
      Boolean outcome = didPlayerWin(past, user);
      if (outcome == null) {
        continue;
      }
      if (streakType == null) {
        streakType = outcome;
      }
      if (Boolean.TRUE.equals(streakType) && Boolean.TRUE.equals(outcome)) {
        wins++;
      } else if (Boolean.FALSE.equals(streakType) && Boolean.FALSE.equals(outcome)) {
        losses++;
      } else {
        break;
      }
    }
    return new StreakInfo(wins, losses);
  }

  private double computeWinStreakScale(int winStreak, int rank) {
    if (winStreak <= 1) {
      return 1.0;
    }
    int capped = Math.min(winStreak, 5);
    double bonus = 0.08 * (capped - 1);
    double taper = rankBonusTaper(rank);
    return 1.0 + bonus * taper;
  }

  private double computeLossStreakScale(int lossStreak) {
    if (lossStreak <= 1) {
      return 1.0;
    }
    int capped = Math.min(lossStreak, 4);
    double relief = 0.06 * (capped - 1);
    double scale = 1.0 - relief;
    return Math.max(0.7, scale);
  }

  private double rankBonusTaper(int rank) {
    if (rank <= 1) {
      return 0.5;
    }
    if (rank <= 3) {
      return 0.65;
    }
    if (rank <= 5) {
      return 0.75;
    }
    if (rank <= 10) {
      return 0.85;
    }
    return 1.0;
  }

  private double computeEdgeScale(
      Participant participant,
      PlayerSnapshot snapshot,
      TeamProfile selfTeam,
      TeamProfile oppTeam,
      int maxBandIndex) {
    double scale = 1.0;
    if (participant.winner) {
      if (snapshot.bottomBand && isBottomVsBandTwo(selfTeam, oppTeam, maxBandIndex)) {
        scale *= BOTTOM_BAND_KICKER;
      }
      if (snapshot.bottomBand && oppTeam.containsTopQuartile(maxBandIndex)) {
        scale *= STRONG_BOTTOM_WIN_BOOST;
      }
      if (snapshot.bandIndex == 2) {
        boolean credibleOpposition = oppTeam.hasTopBand || oppTeam.hasHighBandTwo;
        if (!credibleOpposition) {
          scale *= 0.85;
        }
      }
    }
    return scale;
  }

  private boolean isBottomVsBandTwo(TeamProfile selfTeam, TeamProfile oppTeam, int maxBandIndex) {
    int bandTwoIndex = Math.max(1, maxBandIndex - 1);
    return !selfTeam.isEmpty()
        && !oppTeam.isEmpty()
        && selfTeam.primaryBand == maxBandIndex
        && oppTeam.primaryBand == bandTwoIndex;
  }

  private double applyVarietyGateForElites(double value, PlayerSnapshot snapshot, int baseStep) {
    if (value > 0 && snapshot.rank <= 5 && snapshot.varietyScale < 0.6) {
      return Math.min(value, baseStep * 0.5);
    }
    return value;
  }

  private boolean isUpsetWin(PlayerSnapshot snapshot, TeamProfile oppTeam) {
    if (oppTeam.isEmpty()) {
      return false;
    }
    return snapshot.bandIndex > oppTeam.primaryBand;
  }

  private double applyTopBandClamp(
      double value,
      PlayerSnapshot snapshot,
      TeamProfile selfTeam,
      TeamProfile oppTeam,
      int baseStep) {
    if (value <= 0) {
      return value;
    }
    if (snapshot.topBand && isTopVsBandTwo(selfTeam, oppTeam)) {
      return Math.min(value, baseStep);
    }
    return value;
  }

  private boolean isTopVsBandTwo(TeamProfile selfTeam, TeamProfile oppTeam) {
    return !selfTeam.isEmpty()
        && !oppTeam.isEmpty()
        && selfTeam.primaryBand == 1
        && oppTeam.primaryBand == 2;
  }

  private double applyDemotionGuard(double value, PlayerSnapshot snapshot, int baseStep) {
    if (value < 0 && snapshot.topBand && snapshot.varietyScale < 0.6) {
      double cap = -baseStep * 0.5;
      return Math.max(value, cap);
    }
    return value;
  }

  private double applyBottomLossGuard(double value, PlayerSnapshot snapshot, int baseStep) {
    if (value < 0 && snapshot.bottomBand) {
      double cap = -baseStep * 0.5;
      return Math.max(value, cap);
    }
    return value;
  }

  private double applyLossFloor(double value, int baseStep, double guestScale) {
    if (value >= 0) {
      return value;
    }
    double floor = -baseStep * guestScale * LOSS_FLOOR_RATIO;
    return Math.max(value, floor);
  }

  private Boolean didPlayerWin(Match match, User user) {
    Team team = identifyTeam(match, user);
    if (team == null) {
      return null;
    }
    boolean teamAWon = match.isTeamAWinner();
    if (team == Team.A) {
      return teamAWon;
    }
    return !teamAWon;
  }

  private List<User> collectOpponents(Match match, User user) {
    Team team = identifyTeam(match, user);
    List<User> opponents = new ArrayList<>(2);
    if (team == Team.A) {
      addIfRealPlayer(opponents, match.getB1(), match.isB1Guest());
      addIfRealPlayer(opponents, match.getB2(), match.isB2Guest());
    } else if (team == Team.B) {
      addIfRealPlayer(opponents, match.getA1(), match.isA1Guest());
      addIfRealPlayer(opponents, match.getA2(), match.isA2Guest());
    }
    return opponents;
  }

  private void addIfRealPlayer(List<User> list, User candidate, boolean guest) {
    if (!guest && candidate != null && candidate.getId() != null) {
      list.add(candidate);
    }
  }

  private Team identifyTeam(Match match, User user) {
    if (user == null || user.getId() == null) {
      return null;
    }
    if (!match.isA1Guest() && sameUser(match.getA1(), user)) {
      return Team.A;
    }
    if (!match.isA2Guest() && sameUser(match.getA2(), user)) {
      return Team.A;
    }
    if (!match.isB1Guest() && sameUser(match.getB1(), user)) {
      return Team.B;
    }
    if (!match.isB2Guest() && sameUser(match.getB2(), user)) {
      return Team.B;
    }
    return null;
  }

  private boolean sameUser(User a, User b) {
    if (a == null || b == null || a.getId() == null || b.getId() == null) {
      return false;
    }
    return a.getId().equals(b.getId());
  }

  private int resolveBandIndex(BandPosition position, int fallback) {
    if (position == null || position.getBandIndex() == null || position.getBandIndex() <= 0) {
      return Math.max(1, fallback);
    }
    return position.getBandIndex();
  }

  private int resolvePositionInBand(BandPosition position) {
    if (position == null
        || position.getPositionInBand() == null
        || position.getPositionInBand() <= 0) {
      return Integer.MAX_VALUE;
    }
    return position.getPositionInBand();
  }

  private boolean isEffectivelyExcludedFromStandings(Match match) {
    if (match == null) {
      return false;
    }
    if (match.isExcludeFromStandings()) {
      return true;
    }
    LadderSeason season = match.getSeason();
    LadderConfig cfg = season != null ? season.getLadderConfig() : null;
    boolean allowGuestOnlyPersonalRecords =
        cfg != null
            && cfg.isAllowGuestOnlyPersonalMatches()
            && cfg.getSecurityLevel() != null
            && com.w3llspring.fhpb.web.model.LadderSecurity.normalize(cfg.getSecurityLevel())
                .isSelfConfirm();
    return allowGuestOnlyPersonalRecords && match.hasGuestOnlyOpposingTeam();
  }

  private List<Participant> collectParticipants(Match match) {
    boolean teamAWon = match.isTeamAWinner();
    List<Participant> participants = new ArrayList<>(4);
    participants.add(new Participant(Team.A, match.getA1(), match.isA1Guest(), teamAWon));
    participants.add(new Participant(Team.A, match.getA2(), match.isA2Guest(), teamAWon));
    participants.add(new Participant(Team.B, match.getB1(), match.isB1Guest(), !teamAWon));
    participants.add(new Participant(Team.B, match.getB2(), match.isB2Guest(), !teamAWon));
    return participants;
  }

  private enum Team {
    A,
    B
  }

  private static final class Participant {
    private final Team team;
    private final User user;
    private final boolean guest;
    private final boolean winner;

    private Participant(Team team, User user, boolean guest, boolean winner) {
      this.team = team;
      this.user = user;
      this.guest = guest;
      this.winner = winner;
    }

    private boolean isEligible() {
      return !guest && user != null && user.getId() != null;
    }
  }

  private static final class PlayerSnapshot {
    private final User user;
    private final int bandIndex;
    private final int positionInBand;
    private final int rank;
    private final boolean topQuartileInBand;
    private final boolean topBand;
    private final boolean bottomBand;
    private double varietyScore = 0.0;
    private double varietyScale = 0.0;
    private int winStreak = 0;
    private int lossStreak = 0;
    private double streakScaleWin = 1.0;
    private double streakScaleLoss = 1.0;

    private PlayerSnapshot(
        User user,
        int bandIndex,
        int positionInBand,
        int rank,
        boolean topQuartileInBand,
        boolean topBand,
        boolean bottomBand) {
      this.user = user;
      this.bandIndex = bandIndex;
      this.positionInBand = positionInBand;
      this.rank = rank;
      this.topQuartileInBand = topQuartileInBand;
      this.topBand = topBand;
      this.bottomBand = bottomBand;
    }
  }

  private static final class TeamProfile {
    private final List<PlayerSnapshot> players;
    private final int primaryBand;
    private final boolean hasTopBand;
    private final boolean hasHighBandTwo;

    private TeamProfile(
        List<PlayerSnapshot> players, int primaryBand, boolean hasTopBand, boolean hasHighBandTwo) {
      this.players = players;
      this.primaryBand = primaryBand;
      this.hasTopBand = hasTopBand;
      this.hasHighBandTwo = hasHighBandTwo;
    }

    private static TeamProfile build(
        Team team,
        List<Participant> participants,
        Map<Long, PlayerSnapshot> snapshots,
        int maxBandIndex) {
      List<PlayerSnapshot> members = new ArrayList<>();
      for (Participant participant : participants) {
        if (participant.team != team) {
          continue;
        }
        PlayerSnapshot snapshot = snapshots.get(participant.user.getId());
        if (snapshot != null) {
          members.add(snapshot);
        }
      }
      if (members.isEmpty()) {
        return new TeamProfile(List.of(), maxBandIndex, false, false);
      }

      double avg = members.stream().mapToInt(s -> s.bandIndex).average().orElse(maxBandIndex);
      int primary = Math.max(1, (int) Math.round(avg));
      boolean hasTop = members.stream().anyMatch(s -> s.bandIndex == 1);
      boolean hasHighBandTwo =
          members.stream().anyMatch(s -> s.bandIndex == 2 && s.topQuartileInBand);

      return new TeamProfile(members, primary, hasTop, hasHighBandTwo);
    }

    private boolean isEmpty() {
      return players.isEmpty();
    }

    private boolean containsTopQuartile(int bandIndex) {
      for (PlayerSnapshot snapshot : players) {
        if (snapshot.bandIndex == bandIndex && snapshot.topQuartileInBand) {
          return true;
        }
      }
      return false;
    }
  }

  private static final class StreakInfo {
    private final int winStreak;
    private final int lossStreak;

    private StreakInfo(int winStreak, int lossStreak) {
      this.winStreak = winStreak;
      this.lossStreak = lossStreak;
    }
  }
}
