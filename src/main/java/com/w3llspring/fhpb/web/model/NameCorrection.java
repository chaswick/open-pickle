package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "name_corrections",
    indexes = {
      @Index(name = "nc_token_idx", columnList = "token_normalized"),
      @Index(name = "nc_ladder_idx", columnList = "ladder_config_id"),
      @Index(name = "nc_phonetic_idx", columnList = "phonetic_key")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uc_token_ladder_user",
          columnNames = {"token_normalized", "ladder_config_id", "user_id"})
    })
public class NameCorrection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "token_normalized", nullable = false, length = 200)
  private String tokenNormalized;

  @Column(name = "ladder_config_id")
  private Long ladderConfigId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "reporter_user_id")
  private Long reporterUserId;

  @Column(name = "phonetic_key", length = 32)
  private String phoneticKey;

  @Column(name = "count", nullable = false)
  private Integer count = 0;

  @Column(name = "last_confirmed_at")
  private Instant lastConfirmedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTokenNormalized() {
    return tokenNormalized;
  }

  public void setTokenNormalized(String tokenNormalized) {
    this.tokenNormalized = tokenNormalized;
  }

  public Long getLadderConfigId() {
    return ladderConfigId;
  }

  public void setLadderConfigId(Long ladderConfigId) {
    this.ladderConfigId = ladderConfigId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getReporterUserId() {
    return reporterUserId;
  }

  public void setReporterUserId(Long reporterUserId) {
    this.reporterUserId = reporterUserId;
  }

  public String getPhoneticKey() {
    return phoneticKey;
  }

  public void setPhoneticKey(String phoneticKey) {
    this.phoneticKey = phoneticKey;
  }

  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  public Instant getLastConfirmedAt() {
    return lastConfirmedAt;
  }

  public void setLastConfirmedAt(Instant lastConfirmedAt) {
    this.lastConfirmedAt = lastConfirmedAt;
  }
}
