package com.w3llspring.fhpb.web.service.matchlog;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.service.CompetitionAutoModerationService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MatchValidationService {

  private static final int DEFAULT_MIN_WINNING_SCORE = 11;
  private static final int DEFAULT_MAX_SCORE = 35;
  private static final int DEFAULT_MIN_WINNING_MARGIN = 1;

  private final LadderSeasonRepository seasonRepository;
  private final LadderMembershipRepository membershipRepository;
  private final int minWinningScore;
  private final int maxScore;
  private final int minWinningMargin;
  private CompetitionAutoModerationService competitionAutoModerationService;

  @Autowired
  public MatchValidationService(
      LadderSeasonRepository seasonRepository,
      LadderMembershipRepository membershipRepository,
      CompetitionAutoModerationService competitionAutoModerationService,
      @Value("${fhpb.match-log.score-validation.min-winning-score:11}") int minWinningScore,
      @Value("${fhpb.match-log.score-validation.max-score:35}") int maxScore,
      @Value("${fhpb.match-log.score-validation.min-margin:1}") int minWinningMargin) {
    this.seasonRepository = seasonRepository;
    this.membershipRepository = membershipRepository;
    this.competitionAutoModerationService = competitionAutoModerationService;
    this.minWinningScore = Math.max(1, minWinningScore);
    this.maxScore = Math.max(this.minWinningScore, maxScore);
    this.minWinningMargin = Math.max(1, minWinningMargin);
  }

  public MatchValidationService(
      LadderSeasonRepository seasonRepository,
      LadderMembershipRepository membershipRepository,
      @Value("${fhpb.match-log.score-validation.min-winning-score:11}") int minWinningScore,
      @Value("${fhpb.match-log.score-validation.max-score:35}") int maxScore,
      @Value("${fhpb.match-log.score-validation.min-margin:1}") int minWinningMargin) {
    this(seasonRepository, membershipRepository, null, minWinningScore, maxScore, minWinningMargin);
  }

  public MatchValidationService(
      LadderSeasonRepository seasonRepository, LadderMembershipRepository membershipRepository) {
    this(
        seasonRepository,
        membershipRepository,
        null,
        DEFAULT_MIN_WINNING_SCORE,
        DEFAULT_MAX_SCORE,
        DEFAULT_MIN_WINNING_MARGIN);
  }

  public MatchValidationResult validate(MatchValidationRequest request) {
    if (request == null) {
      return MatchValidationResult.invalid(
          Collections.singletonList("Invalid match validation request."));
    }

    List<String> errors = new ArrayList<>();
    LadderSeason season = request.getSeason();

    boolean ladderHasMembership = season != null && season.getLadderConfig() != null;
    Set<Long> eligibleMemberIds = request.getEligibleMemberIds();
    if (eligibleMemberIds == null) {
      if (season != null && season.getLadderConfig() != null && season.getId() != null) {
        eligibleMemberIds = resolveEligibleMemberUserIdsForSeason(season.getId());
      } else {
        eligibleMemberIds = Collections.emptySet();
      }
    }

    List<PlayerSlot> allSlots = request.getAllSlots();

    if (ladderHasMembership) {
      for (PlayerSlot slot : allSlots) {
        if (!slot.isRequireMemberCheck()) {
          continue;
        }
        if (slot.isGuest()) {
          if (!slot.isGuestAllowed()) {
            errors.add(slot.buildMemberErrorMessage());
          }
          continue;
        }
        Long userId = slot.getUserId();
        if (userId == null || !eligibleMemberIds.contains(userId)) {
          errors.add(slot.buildMemberErrorMessage());
        }
      }

      PlayerSlot opponentOne = request.getOpponentOneSlot();
      PlayerSlot opponentTwo = request.getOpponentTwoSlot();
      boolean opponentOneGuest = opponentOne == null || opponentOne.isGuest();
      boolean opponentTwoGuest = opponentTwo == null || opponentTwo.isGuest();

      if (request.isRequireOpponentMember() && opponentOneGuest && opponentTwoGuest) {
        errors.add("Opponent team: at least one player must be a ladder member");
      }

      if (!opponentOneGuest && !opponentTwoGuest && opponentOne != null && opponentTwo != null) {
        Long oppOneId = opponentOne.getUserId();
        Long oppTwoId = opponentTwo.getUserId();
        if (oppOneId != null && oppOneId.equals(oppTwoId)) {
          errors.add("Opponent team: players must be two different members");
        }
      }
    }

    if (request.isCheckDuplicatePlayers()) {
      Set<Long> seen = new LinkedHashSet<>();
      Set<Long> duplicates = new LinkedHashSet<>();
      for (PlayerSlot slot : allSlots) {
        if (slot.isGuest()) {
          continue;
        }
        if (!slot.isIncludeInDuplicateCheck()) {
          continue;
        }
        Long id = slot.getUserId();
        if (id == null) {
          continue;
        }
        if (!seen.add(id)) {
          duplicates.add(id);
        }
      }
      if (!duplicates.isEmpty()) {
        String duplicatesStr =
            duplicates.stream().map(String::valueOf).collect(Collectors.joining(", "));
        errors.add("Duplicate players detected: " + duplicatesStr);
      }
    }

    return errors.isEmpty() ? MatchValidationResult.valid() : MatchValidationResult.invalid(errors);
  }

  public ScoreValidationResult validateScore(Integer scoreA, Integer scoreB) {
    if (scoreA == null || scoreB == null) {
      return ScoreValidationResult.invalid("Both teams must have a score entered.");
    }
    return validateScore(scoreA.intValue(), scoreB.intValue());
  }

  /**
   * Validate a match score against configurable sanity limits. Defaults are intentionally broad:
   * winner must reach at least 11, scores cannot tie, scores must be non-negative, and no score may
   * exceed 35. The winning margin is configurable and defaults to 1, which allows alternative
   * formats while still rejecting ties and impossible negatives.
   */
  public ScoreValidationResult validateScore(int scoreA, int scoreB) {
    if (scoreA < 0 || scoreB < 0) {
      return ScoreValidationResult.invalid("Scores must be zero or greater.");
    }
    if (scoreA == scoreB) {
      return ScoreValidationResult.invalid("Match scores cannot be tied.");
    }

    int winnerScore = Math.max(scoreA, scoreB);
    int loserScore = Math.min(scoreA, scoreB);
    if (winnerScore < minWinningScore) {
      return ScoreValidationResult.invalid(
          "Winning team must score at least " + minWinningScore + " points.");
    }
    if (winnerScore > maxScore || loserScore > maxScore) {
      return ScoreValidationResult.invalid("Scores cannot exceed " + maxScore + " points.");
    }
    if (minWinningMargin > 1 && (winnerScore - loserScore) < minWinningMargin) {
      return ScoreValidationResult.invalid(
          "Winning team must lead by at least "
              + minWinningMargin
              + " "
              + pointLabel(minWinningMargin)
              + ".");
    }

    return ScoreValidationResult.valid();
  }

  public boolean isValidPickleballScore(int scoreA, int scoreB) {
    return validateScore(scoreA, scoreB).isValid();
  }

  public Set<Long> resolveEligibleMemberUserIdsForSeason(Long seasonId) {
    if (seasonId == null) {
      return null;
    }
    LadderSeason season = seasonRepository.findById(seasonId).orElse(null);
    if (season == null || season.getLadderConfig() == null) {
      return Collections.emptySet();
    }
    Long ladderConfigId = season.getLadderConfig().getId();
    if (ladderConfigId == null) {
      return Collections.emptySet();
    }

    Set<Long> eligibleIds =
        membershipRepository
            .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                ladderConfigId, LadderMembership.State.ACTIVE)
            .stream()
            .map(LadderMembership::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    if (competitionAutoModerationService != null) {
      eligibleIds = competitionAutoModerationService.filterEligibleUserIds(season, eligibleIds);
    }
    return eligibleIds;
  }

  public static class MatchValidationRequest {
    private LadderSeason season;
    private Set<Long> eligibleMemberIds;
    private PlayerSlot reporterSlot;
    private PlayerSlot partnerSlot;
    private PlayerSlot opponentOneSlot;
    private PlayerSlot opponentTwoSlot;
    private boolean requireOpponentMember = true;
    private boolean checkDuplicatePlayers = true;

    public LadderSeason getSeason() {
      return season;
    }

    public void setSeason(LadderSeason season) {
      this.season = season;
    }

    public Set<Long> getEligibleMemberIds() {
      return eligibleMemberIds;
    }

    public void setEligibleMemberIds(Set<Long> eligibleMemberIds) {
      this.eligibleMemberIds = eligibleMemberIds;
    }

    public PlayerSlot getReporterSlot() {
      return reporterSlot;
    }

    public void setReporterSlot(PlayerSlot reporterSlot) {
      this.reporterSlot = reporterSlot;
    }

    public PlayerSlot getPartnerSlot() {
      return partnerSlot;
    }

    public void setPartnerSlot(PlayerSlot partnerSlot) {
      this.partnerSlot = partnerSlot;
    }

    public PlayerSlot getOpponentOneSlot() {
      return opponentOneSlot;
    }

    public void setOpponentOneSlot(PlayerSlot opponentOneSlot) {
      this.opponentOneSlot = opponentOneSlot;
    }

    public PlayerSlot getOpponentTwoSlot() {
      return opponentTwoSlot;
    }

    public void setOpponentTwoSlot(PlayerSlot opponentTwoSlot) {
      this.opponentTwoSlot = opponentTwoSlot;
    }

    public boolean isRequireOpponentMember() {
      return requireOpponentMember;
    }

    public void setRequireOpponentMember(boolean requireOpponentMember) {
      this.requireOpponentMember = requireOpponentMember;
    }

    public boolean isCheckDuplicatePlayers() {
      return checkDuplicatePlayers;
    }

    public void setCheckDuplicatePlayers(boolean checkDuplicatePlayers) {
      this.checkDuplicatePlayers = checkDuplicatePlayers;
    }

    private List<PlayerSlot> getAllSlots() {
      return Stream.of(reporterSlot, partnerSlot, opponentOneSlot, opponentTwoSlot)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    }
  }

  public static class PlayerSlot {
    private final Long userId;
    private final boolean guest;
    private final boolean guestAllowed;
    private final boolean requireMemberCheck;
    private final String label;
    private final String guestSuggestion;
    private final boolean includeInDuplicateCheck;

    private PlayerSlot(Builder builder) {
      this.userId = builder.userId;
      this.guest = builder.guest;
      this.guestAllowed = builder.guestAllowed;
      this.requireMemberCheck = builder.requireMemberCheck;
      this.label = builder.label;
      this.guestSuggestion = builder.guestSuggestion;
      this.includeInDuplicateCheck = builder.includeInDuplicateCheck;
    }

    public static Builder builder(String label) {
      return new Builder(label);
    }

    public Long getUserId() {
      return userId;
    }

    public boolean isGuest() {
      return guest;
    }

    public boolean isGuestAllowed() {
      return guestAllowed;
    }

    public boolean isRequireMemberCheck() {
      return requireMemberCheck;
    }

    public String getLabel() {
      return label;
    }

    public boolean isIncludeInDuplicateCheck() {
      return includeInDuplicateCheck;
    }

    private String buildMemberErrorMessage() {
      String base = label + ": must be a member of this ladder for the selected season";
      if (guestSuggestion != null && !guestSuggestion.isBlank()) {
        return base + " (or " + guestSuggestion + ")";
      }
      return base;
    }

    public static class Builder {
      private final String label;
      private Long userId;
      private boolean guest;
      private boolean guestAllowed = true;
      private boolean requireMemberCheck = true;
      private String guestSuggestion;
      private boolean includeInDuplicateCheck = true;

      private Builder(String label) {
        this.label = label;
      }

      public Builder userId(Long userId) {
        this.userId = userId;
        return this;
      }

      public Builder guest(boolean guest) {
        this.guest = guest;
        return this;
      }

      public Builder guestAllowed(boolean guestAllowed) {
        this.guestAllowed = guestAllowed;
        return this;
      }

      public Builder requireMemberCheck(boolean requireMemberCheck) {
        this.requireMemberCheck = requireMemberCheck;
        return this;
      }

      public Builder guestSuggestion(String guestSuggestion) {
        this.guestSuggestion = guestSuggestion;
        return this;
      }

      public Builder includeInDuplicateCheck(boolean includeInDuplicateCheck) {
        this.includeInDuplicateCheck = includeInDuplicateCheck;
        return this;
      }

      public PlayerSlot build() {
        return new PlayerSlot(this);
      }
    }
  }

  public static class MatchValidationResult {
    private final boolean valid;
    private final List<String> errors;

    private MatchValidationResult(boolean valid, List<String> errors) {
      this.valid = valid;
      this.errors = errors;
    }

    public static MatchValidationResult valid() {
      return new MatchValidationResult(true, Collections.emptyList());
    }

    public static MatchValidationResult invalid(List<String> errors) {
      return new MatchValidationResult(false, errors == null ? Collections.emptyList() : errors);
    }

    public boolean isValid() {
      return valid;
    }

    public List<String> getErrors() {
      return errors;
    }
  }

  public static class ScoreValidationResult {
    private final boolean valid;
    private final String errorMessage;

    private ScoreValidationResult(boolean valid, String errorMessage) {
      this.valid = valid;
      this.errorMessage = errorMessage;
    }

    public static ScoreValidationResult valid() {
      return new ScoreValidationResult(true, null);
    }

    public static ScoreValidationResult invalid(String errorMessage) {
      return new ScoreValidationResult(false, errorMessage);
    }

    public boolean isValid() {
      return valid;
    }

    public String getErrorMessage() {
      return errorMessage;
    }
  }

  private static String pointLabel(int count) {
    return count == 1 ? "point" : "points";
  }
}
