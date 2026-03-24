package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "interpretation_event",
    indexes = {
      @Index(name = "ie_event_uuid_idx", columnList = "event_uuid"),
      @Index(name = "ie_ladder_idx", columnList = "ladder_config_id")
    })
public class InterpretationEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_uuid", nullable = false, unique = true, length = 36)
  private String eventUuid;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "ladder_config_id")
  private Long ladderConfigId;

  @Column(name = "season_id")
  private Long seasonId;

  @Column(name = "current_user_id")
  private Long currentUserId;

  @Lob
  // Ensure Hibernate maps this LOB to MEDIUMTEXT so MySQL column is large enough
  @Column(name = "interpretation_json", columnDefinition = "MEDIUMTEXT")
  private String interpretationJson;

  @Lob
  @Column(name = "transcript", columnDefinition = "MEDIUMTEXT")
  private String transcript;

  @Column(name = "auto_submitted")
  private Boolean autoSubmitted = Boolean.FALSE;

  @Column(name = "match_id")
  private Long matchId;

  @Column(name = "interpreter_version")
  private String interpreterVersion;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEventUuid() {
    return eventUuid;
  }

  public void setEventUuid(String eventUuid) {
    this.eventUuid = eventUuid;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
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

  public String getInterpretationJson() {
    return interpretationJson;
  }

  public void setInterpretationJson(String interpretationJson) {
    this.interpretationJson = interpretationJson;
  }

  public String getTranscript() {
    return transcript;
  }

  public void setTranscript(String transcript) {
    this.transcript = transcript;
  }

  public Boolean getAutoSubmitted() {
    return autoSubmitted;
  }

  public void setAutoSubmitted(Boolean autoSubmitted) {
    this.autoSubmitted = autoSubmitted;
  }

  public Long getMatchId() {
    return matchId;
  }

  public void setMatchId(Long matchId) {
    this.matchId = matchId;
  }

  public String getInterpreterVersion() {
    return interpreterVersion;
  }

  public void setInterpreterVersion(String interpreterVersion) {
    this.interpreterVersion = interpreterVersion;
  }
}
