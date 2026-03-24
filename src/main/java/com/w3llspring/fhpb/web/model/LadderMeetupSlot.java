package com.w3llspring.fhpb.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ladder_meetup_slot")
public class LadderMeetupSlot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "ladder_config_id")
  private LadderConfig ladderConfig;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "location_code", length = 2)
  private String locationCode;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "canceled_at")
  private Instant canceledAt;

  public Long getId() {
    return id;
  }

  public LadderConfig getLadderConfig() {
    return ladderConfig;
  }

  public void setLadderConfig(LadderConfig ladderConfig) {
    this.ladderConfig = ladderConfig;
  }

  public Long getCreatedByUserId() {
    return createdByUserId;
  }

  public void setCreatedByUserId(Long createdByUserId) {
    this.createdByUserId = createdByUserId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(Instant startsAt) {
    this.startsAt = startsAt;
  }

  public String getLocationCode() {
    return locationCode;
  }

  public void setLocationCode(String locationCode) {
    this.locationCode = locationCode;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getCanceledAt() {
    return canceledAt;
  }

  public void setCanceledAt(Instant canceledAt) {
    this.canceledAt = canceledAt;
  }
}
