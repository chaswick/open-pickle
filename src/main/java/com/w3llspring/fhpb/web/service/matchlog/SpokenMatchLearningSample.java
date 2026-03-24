package com.w3llspring.fhpb.web.service.matchlog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Captures a troublesome spoken transcript so the system can learn from it later (e.g., adding
 * aliases, tweaking heuristics, or building analytics).
 */
public class SpokenMatchLearningSample {

  private String transcript;
  private Long ladderConfigId;
  private Long seasonId;
  private Long currentUserId;
  private boolean complete;
  private boolean scoreMissing;
  private boolean userAdjusted;
  private final List<String> warnings = new ArrayList<>();
  private final List<PlayerIssue> playerIssues = new ArrayList<>();
  private final List<Long> finalTeamAUserIds = new ArrayList<>();
  private final List<Long> finalTeamBUserIds = new ArrayList<>();

  public String getTranscript() {
    return transcript;
  }

  public void setTranscript(String transcript) {
    this.transcript = transcript;
  }

  public Long getLadderConfigId() {
    return ladderConfigId;
  }

  public void setLadderConfigId(Long ladderConfigId) {
    this.ladderConfigId = ladderConfigId;
  }

  public Long getSeasonId() {
    return seasonId;
  }

  public void setSeasonId(Long seasonId) {
    this.seasonId = seasonId;
  }

  public Long getCurrentUserId() {
    return currentUserId;
  }

  public void setCurrentUserId(Long currentUserId) {
    this.currentUserId = currentUserId;
  }

  public boolean isComplete() {
    return complete;
  }

  public void setComplete(boolean complete) {
    this.complete = complete;
  }

  public boolean isScoreMissing() {
    return scoreMissing;
  }

  public void setScoreMissing(boolean scoreMissing) {
    this.scoreMissing = scoreMissing;
  }

  public boolean isUserAdjusted() {
    return userAdjusted;
  }

  public void setUserAdjusted(boolean userAdjusted) {
    this.userAdjusted = userAdjusted;
  }

  public List<Long> getFinalTeamAUserIds() {
    return Collections.unmodifiableList(finalTeamAUserIds);
  }

  public void setFinalTeamAUserIds(List<Long> teamAUserIds) {
    this.finalTeamAUserIds.clear();
    if (teamAUserIds != null) {
      this.finalTeamAUserIds.addAll(teamAUserIds);
    }
  }

  public List<Long> getFinalTeamBUserIds() {
    return Collections.unmodifiableList(finalTeamBUserIds);
  }

  public void setFinalTeamBUserIds(List<Long> teamBUserIds) {
    this.finalTeamBUserIds.clear();
    if (teamBUserIds != null) {
      this.finalTeamBUserIds.addAll(teamBUserIds);
    }
  }

  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  public void setWarnings(List<String> warnings) {
    this.warnings.clear();
    if (warnings != null) {
      for (String warning : warnings) {
        if (warning != null && !warning.isBlank()) {
          this.warnings.add(warning);
        }
      }
    }
  }

  public List<PlayerIssue> getPlayerIssues() {
    return Collections.unmodifiableList(playerIssues);
  }

  public PlayerIssue addPlayerIssue() {
    PlayerIssue issue = new PlayerIssue();
    playerIssues.add(issue);
    return issue;
  }

  /** Signals whether this sample contains anything worth storing. */
  public boolean shouldRecord() {
    return scoreMissing
        || userAdjusted
        || !warnings.isEmpty()
        || !playerIssues.isEmpty()
        || !complete;
  }

  public static class PlayerIssue {
    private int teamIndex;
    private String token;
    private Long matchedUserId;
    private String matchedName;
    private String aliasUsed;
    private double confidence;
    private boolean needsReview;

    public int getTeamIndex() {
      return teamIndex;
    }

    public void setTeamIndex(int teamIndex) {
      this.teamIndex = teamIndex;
    }

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

    public String getAliasUsed() {
      return aliasUsed;
    }

    public void setAliasUsed(String aliasUsed) {
      this.aliasUsed = aliasUsed;
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
  }
}
