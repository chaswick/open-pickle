package com.w3llspring.fhpb.web.service.matchlog;

import java.util.ArrayList;
import java.util.List;

/**
 * Result returned from {@link SpokenMatchInterpreter}.
 *
 * <p>ALWAYS_LOG_PHASE2: This class represents pure interpretation data without validation. The
 * interpreter's job is to extract all information it can from the transcript and assign confidence
 * levels. Validation of whether a match is "complete" enough to save is the responsibility of the
 * controller/service layer, not the interpreter.
 */
public class SpokenMatchInterpretation {

  private String transcript;
  private Integer scoreTeamA;
  private Integer scoreTeamB;
  private Integer winningTeamIndex;
  private final List<Team> teams = new ArrayList<>();
  private final List<String> warnings = new ArrayList<>();
  // Indicates that the numeric score tokens in the original transcript were
  // provided in winner-first order (e.g. "I lost to Dave 11 5" -> 11 is Dave's score).
  // When true, learning should treat swapped interpreted scores differently.
  private boolean scoreOrderReversed = false;

  public String getTranscript() {
    return transcript;
  }

  public void setTranscript(String transcript) {
    this.transcript = transcript;
  }

  public Integer getScoreTeamA() {
    return scoreTeamA;
  }

  public void setScoreTeamA(Integer scoreTeamA) {
    this.scoreTeamA = scoreTeamA;
  }

  public Integer getScoreTeamB() {
    return scoreTeamB;
  }

  public void setScoreTeamB(Integer scoreTeamB) {
    this.scoreTeamB = scoreTeamB;
  }

  public Integer getWinningTeamIndex() {
    return winningTeamIndex;
  }

  public void setWinningTeamIndex(Integer winningTeamIndex) {
    this.winningTeamIndex = winningTeamIndex;
  }

  public List<Team> getTeams() {
    return teams;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public void addWarning(String warning) {
    if (warning != null && !warning.isBlank()) {
      warnings.add(warning);
    }
  }

  public Team addTeam(int index, boolean winner) {
    Team team = new Team();
    team.setIndex(index);
    team.setWinner(winner);
    teams.add(team);
    return team;
  }

  public boolean isScoreOrderReversed() {
    return scoreOrderReversed;
  }

  public void setScoreOrderReversed(boolean scoreOrderReversed) {
    this.scoreOrderReversed = scoreOrderReversed;
  }

  public static class Team {
    private int index;
    private boolean winner;
    private final List<PlayerResolution> players = new ArrayList<>();

    public int getIndex() {
      return index;
    }

    public void setIndex(int index) {
      this.index = index;
    }

    public boolean isWinner() {
      return winner;
    }

    public void setWinner(boolean winner) {
      this.winner = winner;
    }

    public List<PlayerResolution> getPlayers() {
      return players;
    }

    public PlayerResolution addPlayer() {
      PlayerResolution resolution = new PlayerResolution();
      players.add(resolution);
      return resolution;
    }
  }

  public static class PlayerResolution {
    private String token;
    private Long matchedUserId;
    private String matchedName;
    private String matchedAlias;
    private double confidence;
    private boolean needsReview;
    private final List<PlayerAlternative> alternatives = new ArrayList<>();

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }

    public Long getMatchedUserId() {
      return matchedUserId;
    }

    public void setMatchedUserId(Long matchedUserId) {
      this.matchedUserId = matchedUserId;
    }

    public String getMatchedName() {
      return matchedName;
    }

    public void setMatchedName(String matchedName) {
      this.matchedName = matchedName;
    }

    public String getMatchedAlias() {
      return matchedAlias;
    }

    public void setMatchedAlias(String matchedAlias) {
      this.matchedAlias = matchedAlias;
    }

    public double getConfidence() {
      return confidence;
    }

    public void setConfidence(double confidence) {
      this.confidence = confidence;
    }

    public boolean isNeedsReview() {
      return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
      this.needsReview = needsReview;
    }

    public List<PlayerAlternative> getAlternatives() {
      return alternatives;
    }

    public void addAlternative(Long userId, String alias, String name, double confidence) {
      PlayerAlternative alt = new PlayerAlternative();
      alt.setUserId(userId);
      alt.setAlias(alias);
      alt.setName(name);
      alt.setConfidence(confidence);
      alternatives.add(alt);
    }
  }

  public static class PlayerAlternative {
    private Long userId;
    private String alias;
    private String name;
    private double confidence;

    public Long getUserId() {
      return userId;
    }

    public void setUserId(Long userId) {
      this.userId = userId;
    }

    public String getAlias() {
      return alias;
    }

    public void setAlias(String alias) {
      this.alias = alias;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public double getConfidence() {
      return confidence;
    }

    public void setConfidence(double confidence) {
      this.confidence = confidence;
    }
  }
}
