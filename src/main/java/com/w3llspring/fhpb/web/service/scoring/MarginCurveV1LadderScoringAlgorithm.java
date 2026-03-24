package com.w3llspring.fhpb.web.service.scoring;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MarginCurveV1LadderScoringAlgorithm implements LadderScoringAlgorithm {
  private static final int MIN_BASE_STEP = 2;
  private static final int MAX_BASE_STEP = 16;
  private static final double CURVE_EXPONENT = 1.25;
  private static final int VARIETY_MATCH_LOOKBACK = 5;
  private static final double VARIETY_MAX_BONUS = 1.0;
  private static final double VARIETY_FULL_BONUS_THRESHOLD = 0.7;
  private static final double STREAK_MULTIPLIER_3 = 1.5;
  private static final double STREAK_MULTIPLIER_4 = 1.75;
  private static final double STREAK_MULTIPLIER_5 = 2;
  private static final Duration VARIETY_HISTORY_WINDOW = Duration.ofDays(365);
  private final MarginCurveVarietyCalculator varietyCalculator;
  private static final LadderScoringProfile PROFILE =
      new LadderScoringProfile(
          LadderConfig.ScoringAlgorithm.MARGIN_CURVE_V1,
          "Margin Curve",
          0.0,
          1.0,
          "This ladder starts everyone at a 1000 rating baseline. The scoreline alone sets the swing: "
              + "what matters is how dominant the win was as a share of total points, so 9-0 and 11-0 "
              + "separate hard, while 22-20 barely moves the ladder. A strong recent-variety bonus can "
              + "double winning swings when you fill more unique real partner/opponent slots. Reusing the same "
              + "people in different roles still helps. Guest seats still count against the variety target, "
              + "so full credit kicks in near 70% of recent seat opportunities. That is followed by "
              + "a capped hot-streak bonus for 3, 4, and 5+ wins. Guest-heavy matches still scale down.",
          "This competition is intentionally simpler than Elo or DUPR. It uses score gap, recent variety, and streaks to sort players into broad skill buckets over time instead of trying to predict exact win odds.",
          List.of(
              "If you are climbing, the fastest path is guest-free wins by wide margins while keeping your last five matches spread across different real players.",
              "If you are getting blown out by the same stronger players, stop feeding them easy points and look for closer matchups until your games tighten up.",
              "If you want to keep your rating moving, protect your streak and avoid casual matchups that are likely to turn into heavy losses.",
              "If you want stronger variety credit, rotate both partners and opponents instead of replaying the same small pod.",
              "If someone is still a guest today, you can log the match now and edit it later after they join so the result counts as a real-player matchup instead of a guest seat.",
              "By design, opponent rating does not matter here, so this ladder rewards sustained score control more than prediction-style strength of schedule."));

  public MarginCurveV1LadderScoringAlgorithm() {
    this(
        new RelationshipSlotMarginCurveVarietyCalculator(
            VARIETY_FULL_BONUS_THRESHOLD, VARIETY_MAX_BONUS));
  }

  MarginCurveV1LadderScoringAlgorithm(MarginCurveVarietyCalculator varietyCalculator) {
    this.varietyCalculator = varietyCalculator;
  }

  @Override
  public LadderConfig.ScoringAlgorithm key() {
    return LadderConfig.ScoringAlgorithm.MARGIN_CURVE_V1;
  }

  @Override
  public LadderScoringProfile profile() {
    return PROFILE;
  }

  @Override
  public Duration historyWindow() {
    return VARIETY_HISTORY_WINDOW;
  }

  @Override
  public int computeBaseStep(Match match) {
    if (match == null) {
      return MIN_BASE_STEP;
    }

    int scoreA = Math.max(0, match.getScoreA());
    int scoreB = Math.max(0, match.getScoreB());
    int winnerScore = Math.max(scoreA, scoreB);
    int loserScore = Math.min(scoreA, scoreB);
    int totalPoints = winnerScore + loserScore;
    if (winnerScore <= 0 || totalPoints <= 0) {
      return MIN_BASE_STEP;
    }

    double dominance =
        Math.min(1.0, Math.max(0.0, (winnerScore - loserScore) / (double) totalPoints));
    double curvedDominance = Math.pow(dominance, CURVE_EXPONENT);
    return MIN_BASE_STEP + (int) Math.round((MAX_BASE_STEP - MIN_BASE_STEP) * curvedDominance);
  }

  @Override
  public double computeGuestScale(Match match) {
    if (match == null) {
      return 1.0;
    }
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

  @Override
  public LadderScoringResult score(LadderScoringRequest request) {
    Match match = request.match();
    int baseStep = computeBaseStep(match);
    double guestScale = computeGuestScale(match);
    if (isEffectivelyExcludedFromStandings(match)) {
      return LadderScoringResult.empty(baseStep, guestScale);
    }

    List<Participant> activeParticipants = collectParticipants(match);
    if (activeParticipants.isEmpty()) {
      return LadderScoringResult.empty(baseStep, guestScale);
    }
    if (match == null || match.getScoreA() == match.getScoreB()) {
      return LadderScoringResult.empty(baseStep, guestScale);
    }

    boolean teamAWon = match.getScoreA() > match.getScoreB();
    Map<User, Integer> adjustments = new LinkedHashMap<>();
    Map<Long, LadderScoringExplanation> explanations = new LinkedHashMap<>();
    List<Match> history = request.history() == null ? List.of() : request.history();

    for (Participant participant : activeParticipants) {
      List<Match> recentMatches = recentMatchesForPlayer(participant.user, history, match);
      MarginCurveVarietySnapshot variety =
          varietyCalculator.calculate(recentMatches, participant.user);
      boolean participantWon = participant.team == Team.A ? teamAWon : !teamAWon;
      StreakSnapshot streak =
          computeStreakSnapshot(recentMatches, participant.user, participantWon);
      double varietyMultiplier = participantWon ? variety.multiplier() : 1.0d;
      int scaledDelta =
          roundScaledDelta(baseStep * guestScale * varietyMultiplier * streak.multiplier());
      int signedDelta =
          participant.team == Team.A
              ? (teamAWon ? scaledDelta : -scaledDelta)
              : (teamAWon ? -scaledDelta : scaledDelta);
      adjustments.put(participant.user, signedDelta);

      long userId = participant.user.getId();
      explanations.put(
          userId,
          buildExplanation(
              match,
              participant,
              baseStep,
              guestScale,
              variety,
              streak,
              participantWon,
              signedDelta));
    }

    return new LadderScoringResult(adjustments, explanations, baseStep, guestScale);
  }

  private int roundScaledDelta(double value) {
    int rounded = (int) Math.round(value);
    if (rounded == 0 && value > 0.0) {
      return 1;
    }
    return rounded;
  }

  private LadderScoringExplanation buildExplanation(
      Match match,
      Participant participant,
      int baseStep,
      double guestScale,
      MarginCurveVarietySnapshot variety,
      StreakSnapshot streak,
      boolean participantWon,
      int signedDelta) {
    int selfScore = scoreForTeam(match, participant.team);
    int opponentScore = scoreForTeam(match, participant.team == Team.A ? Team.B : Team.A);
    double guestAdjusted = baseStep * guestScale;
    double varietyAdjusted = participantWon ? guestAdjusted * variety.multiplier() : guestAdjusted;

    List<String> steps = new ArrayList<>();
    steps.add(
        String.format(
            "The %d-%d scoreline set the initial rating change at %d because it was a %s score gap between the teams. Narrow gaps move ratings a little, wide gaps move them more, and huge gaps move them the most.",
            selfScore, opponentScore, baseStep, gapLabel(match)));

    if (guestScale < 1.0d) {
      steps.add(
          String.format(
              "Guest involvement scaled that change to %.0f%%, reducing it from %d to %s.",
              guestScale * 100.0d, baseStep, formatStepValue(guestAdjusted)));
    } else {
      steps.add(
          String.format(
              "There was no guest involvement, so the full %d-point change remained.", baseStep));
    }

    steps.add(varietySummary(variety, guestAdjusted, participantWon));
    steps.add(streakSummary(streak, varietyAdjusted, participantWon));

    if (signedDelta >= 0) {
      steps.add(
          String.format(
              "You were on the winning side, so the final rating change was %+d.", signedDelta));
    } else {
      steps.add(
          String.format(
              "You were on the losing side, so the final rating change was %+d.", signedDelta));
    }
    return new LadderScoringExplanation(steps);
  }

  private String varietySummary(
      MarginCurveVarietySnapshot variety, double guestAdjusted, boolean winner) {
    if (variety.sampleSize() == 0) {
      return "Variety adjustment stayed neutral because there were no prior ladder matches in the recent sample.";
    }
    if (variety.comparableMatchCount() < 2) {
      return String.format(
          "Variety adjustment stayed neutral because there %s only %d recent match%s to compare for variety.",
          variety.comparableMatchCount() == 1 ? "was" : "were",
          variety.comparableMatchCount(),
          variety.comparableMatchCount() == 1 ? "" : "es");
    }
    if (!winner) {
      return String.format(
          "Recent variety was measured from your last %d matches, but it did not boost this result because variety only adds upside on wins.",
          variety.sampleSize());
    }

    double varietyAdjusted = guestAdjusted * variety.multiplier();
    double bonusPercent = (variety.multiplier() - 1.0d) * 100.0d;
    return String.format(
        "Across your last %d matches, you filled %d unique real partner/opponent slots across %d possible partner/opponent seats. Credit for 'full variety' kicks in around %d seats, so variety added %.0f%% and adjusted the change from %s to %s.",
        variety.sampleSize(),
        variety.uniqueSlots(),
        variety.totalSeatOpportunities(),
        variety.fullBonusTarget(),
        bonusPercent,
        formatStepValue(guestAdjusted),
        formatStepValue(varietyAdjusted));
  }

  private String streakSummary(StreakSnapshot streak, double varietyAdjusted, boolean winner) {
    if (!winner) {
      return "You didn't win so no win streak bonus applied.";
    }
    if (streak.winStreak() < 3) {
      return String.format(
          "No streak bonus applied because your current win streak is %d.", streak.winStreak());
    }

    double streakAdjusted = varietyAdjusted * streak.multiplier();
    double bonusPercent = (streak.multiplier() - 1.0d) * 100.0d;
    return String.format(
        "Your current win streak reached %d, so the streak bonus added %.0f%% and moved the rating change from %s to %s before final rounding.",
        streak.winStreak(),
        bonusPercent,
        formatStepValue(varietyAdjusted),
        formatStepValue(streakAdjusted));
  }

  private String formatStepValue(double value) {
    double rounded = Math.rint(value);
    if (Math.abs(value - rounded) < 0.0001d) {
      return Integer.toString((int) rounded);
    }

    String formatted = String.format(Locale.US, "%.2f", value);
    while (formatted.contains(".") && formatted.endsWith("0")) {
      formatted = formatted.substring(0, formatted.length() - 1);
    }
    if (formatted.endsWith(".")) {
      formatted = formatted.substring(0, formatted.length() - 1);
    }
    return formatted;
  }

  private int scoreForTeam(Match match, Team team) {
    if (match == null) {
      return 0;
    }
    return team == Team.A ? match.getScoreA() : match.getScoreB();
  }

  private double dominancePercent(Match match) {
    if (match == null) {
      return 0.0d;
    }
    int totalPoints = Math.max(0, match.getScoreA()) + Math.max(0, match.getScoreB());
    if (totalPoints <= 0) {
      return 0.0d;
    }
    return ((double) Math.abs(match.getScoreA() - match.getScoreB()) / totalPoints) * 100.0d;
  }

  private String gapLabel(Match match) {
    double dominancePercent = dominancePercent(match);
    if (dominancePercent >= 45.0d) {
      return "huge";
    }
    if (dominancePercent >= 20.0d) {
      return "wide";
    }
    return "narrow";
  }

  private List<Participant> collectParticipants(Match match) {
    List<Participant> participants = new ArrayList<>();
    if (match == null) {
      return participants;
    }
    addParticipant(participants, match.getA1(), match.isA1Guest(), Team.A);
    addParticipant(participants, match.getA2(), match.isA2Guest(), Team.A);
    addParticipant(participants, match.getB1(), match.isB1Guest(), Team.B);
    addParticipant(participants, match.getB2(), match.isB2Guest(), Team.B);
    return participants;
  }

  private void addParticipant(List<Participant> participants, User user, boolean guest, Team team) {
    if (user == null || guest || user.getId() == null) {
      return;
    }
    participants.add(new Participant(user, team));
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

  private List<Match> recentMatchesForPlayer(User user, List<Match> history, Match currentMatch) {
    if (user == null || user.getId() == null || history == null || history.isEmpty()) {
      return List.of();
    }

    List<Match> recent = new ArrayList<>(VARIETY_MATCH_LOOKBACK);
    for (Match past : history) {
      if (past == null || isSameMatch(past, currentMatch) || !includesPlayer(past, user)) {
        continue;
      }
      recent.add(past);
      if (recent.size() >= VARIETY_MATCH_LOOKBACK) {
        break;
      }
    }
    return recent;
  }

  private StreakSnapshot computeStreakSnapshot(List<Match> recent, User user, boolean currentWin) {
    if (!currentWin) {
      return new StreakSnapshot(0, 1.0d);
    }

    int streak = 1;
    for (Match past : recent) {
      Boolean pastWin = didPlayerWin(past, user);
      if (!Boolean.TRUE.equals(pastWin)) {
        break;
      }
      streak++;
      if (streak >= 6) {
        break;
      }
    }
    return new StreakSnapshot(streak, streakMultiplier(streak));
  }

  private double streakMultiplier(int winStreak) {
    if (winStreak >= 5) {
      return STREAK_MULTIPLIER_5;
    }
    if (winStreak == 4) {
      return STREAK_MULTIPLIER_4;
    }
    if (winStreak == 3) {
      return STREAK_MULTIPLIER_3;
    }
    return 1.0d;
  }

  private boolean includesPlayer(Match match, User user) {
    return identifyTeam(match, user) != null;
  }

  private Boolean didPlayerWin(Match match, User user) {
    Team team = identifyTeam(match, user);
    if (team == null || match == null) {
      return null;
    }
    return team == Team.A ? match.isTeamAWinner() : !match.isTeamAWinner();
  }

  private Team identifyTeam(Match match, User user) {
    if (match == null || user == null || user.getId() == null) {
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

  private boolean sameUser(User left, User right) {
    if (left == null || right == null || left.getId() == null || right.getId() == null) {
      return false;
    }
    return left.getId().equals(right.getId());
  }

  private boolean isSameMatch(Match left, Match right) {
    if (left == null || right == null) {
      return false;
    }
    if (left == right) {
      return true;
    }
    return left.getId() != null && left.getId().equals(right.getId());
  }

  private enum Team {
    A,
    B
  }

  private record Participant(User user, Team team) {}

  private record StreakSnapshot(int winStreak, double multiplier) {}
}
