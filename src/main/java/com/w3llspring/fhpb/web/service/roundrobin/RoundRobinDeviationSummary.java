package com.w3llspring.fhpb.web.service.roundrobin;

import java.util.List;
import java.util.Map;

public class RoundRobinDeviationSummary {

  private final int participantCount;
  private final int completedRounds;
  private final int futureRounds;
  private final Map<String, Integer> matchesByPlayer; // player display name -> match count so far
  private final List<String> underservedPlayers; // players who need more matches
  private final List<String> overservedPlayers; // players with extra matches
  private final String suggestedFix; // simple one-sentence fix
  private final boolean hasImbalance;

  // Legacy fields for backward compatibility
  @Deprecated private final List<RoundDeviation> roundDetails;

  public RoundRobinDeviationSummary(
      int participantCount,
      int completedRounds,
      int futureRounds,
      Map<String, Integer> matchesByPlayer,
      List<String> underservedPlayers,
      List<String> overservedPlayers,
      String suggestedFix) {
    this.participantCount = participantCount;
    this.completedRounds = completedRounds;
    this.futureRounds = futureRounds;
    this.matchesByPlayer = matchesByPlayer == null ? Map.of() : Map.copyOf(matchesByPlayer);
    List<String> underserved =
        underservedPlayers == null ? List.of() : List.copyOf(underservedPlayers);
    List<String> overserved =
        overservedPlayers == null ? List.of() : List.copyOf(overservedPlayers);
    this.underservedPlayers = underserved;
    this.overservedPlayers = overserved;
    this.suggestedFix = suggestedFix;
    this.hasImbalance = !underserved.isEmpty() || !overserved.isEmpty();
    this.roundDetails = List.of(); // deprecated
  }

  public int getParticipantCount() {
    return participantCount;
  }

  public int getCompletedRounds() {
    return completedRounds;
  }

  public int getFutureRounds() {
    return futureRounds;
  }

  public Map<String, Integer> getMatchesByPlayer() {
    return matchesByPlayer;
  }

  public List<String> getUnderservedPlayers() {
    return underservedPlayers;
  }

  public List<String> getOverservedPlayers() {
    return overservedPlayers;
  }

  public String getSuggestedFix() {
    return suggestedFix;
  }

  public boolean hasImbalance() {
    return hasImbalance;
  }

  // Legacy compatibility methods
  @Deprecated
  public int getExpectedRounds() {
    return completedRounds + futureRounds;
  }

  @Deprecated
  public int getActualRounds() {
    return completedRounds + futureRounds;
  }

  @Deprecated
  public boolean isTruncatedSchedule() {
    return false;
  }

  @Deprecated
  public List<RoundDeviation> getRoundDetails() {
    return roundDetails;
  }

  @Deprecated
  public List<String> getRecommendations() {
    return suggestedFix != null ? List.of(suggestedFix) : List.of();
  }

  @Deprecated
  public boolean hasAnyIssues() {
    return hasImbalance;
  }

  public static RoundRobinDeviationSummary empty() {
    return new RoundRobinDeviationSummary(0, 0, 0, Map.of(), List.of(), List.of(), null);
  }

  public static class RoundDeviation {
    private final int roundNumber;
    private final List<String> playersWithMultipleMatches;
    private final List<String> playersMissingMatches;
    private final List<String> byePlayers;
    private final List<String> structuralIssues;

    public RoundDeviation(
        int roundNumber,
        List<String> playersWithMultipleMatches,
        List<String> playersMissingMatches,
        List<String> byePlayers,
        List<String> structuralIssues) {
      this.roundNumber = roundNumber;
      this.playersWithMultipleMatches =
          playersWithMultipleMatches == null ? List.of() : List.copyOf(playersWithMultipleMatches);
      this.playersMissingMatches =
          playersMissingMatches == null ? List.of() : List.copyOf(playersMissingMatches);
      this.byePlayers = byePlayers == null ? List.of() : List.copyOf(byePlayers);
      this.structuralIssues = structuralIssues == null ? List.of() : List.copyOf(structuralIssues);
    }

    public int getRoundNumber() {
      return roundNumber;
    }

    public List<String> getPlayersWithMultipleMatches() {
      return playersWithMultipleMatches;
    }

    public List<String> getPlayersMissingMatches() {
      return playersMissingMatches;
    }

    public List<String> getByePlayers() {
      return byePlayers;
    }

    public List<String> getStructuralIssues() {
      return structuralIssues;
    }

    public boolean hasIssues() {
      return !(playersWithMultipleMatches.isEmpty()
          && playersMissingMatches.isEmpty()
          && structuralIssues.isEmpty());
    }
  }
}
