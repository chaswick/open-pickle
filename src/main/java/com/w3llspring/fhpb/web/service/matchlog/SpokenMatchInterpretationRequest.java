package com.w3llspring.fhpb.web.service.matchlog;

/** Encapsulates the context needed to interpret a spoken match summary. */
public class SpokenMatchInterpretationRequest {

  private String transcript;
  private Long ladderConfigId;
  private Long seasonId;
  private Long currentUserId;

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
}
