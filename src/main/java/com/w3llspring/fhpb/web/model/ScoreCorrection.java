package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "score_corrections")
public class ScoreCorrection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "ladder_config_id")
  private Long ladderConfigId;

  // 'A' or 'B' to indicate which team's score was corrected
  @Column(name = "score_field")
  private String scoreField;

  @Column(name = "interpreted_value")
  private Integer interpretedValue;

  @Column(name = "corrected_value")
  private Integer correctedValue;

  @Column(name = "count", nullable = false)
  private int count = 0;

  @Column(name = "last_confirmed_at")
  private Instant lastConfirmedAt;

  public Long getId() {
    return id;
  }

  public Long getLadderConfigId() {
    return ladderConfigId;
  }

  public void setLadderConfigId(Long ladderConfigId) {
    this.ladderConfigId = ladderConfigId;
  }

  public String getScoreField() {
    return scoreField;
  }

  public void setScoreField(String scoreField) {
    this.scoreField = scoreField;
  }

  public Integer getInterpretedValue() {
    return interpretedValue;
  }

  public void setInterpretedValue(Integer interpretedValue) {
    this.interpretedValue = interpretedValue;
  }

  public Integer getCorrectedValue() {
    return correctedValue;
  }

  public void setCorrectedValue(Integer correctedValue) {
    this.correctedValue = correctedValue;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  public Instant getLastConfirmedAt() {
    return lastConfirmedAt;
  }

  public void setLastConfirmedAt(Instant lastConfirmedAt) {
    this.lastConfirmedAt = lastConfirmedAt;
  }
}
