package com.w3llspring.fhpb.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "user_onboarding_marker",
    indexes = {
      @Index(name = "idx_user_onboarding_marker_key", columnList = "marker_key, completed_at")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_onboarding_marker_user_key",
          columnNames = {"user_id", "marker_key"})
    })
public class UserOnboardingMarker {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "marker_key", nullable = false, length = 64)
  private String markerKey;

  @Column(name = "completed_at", nullable = false)
  private Instant completedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getMarkerKey() {
    return markerKey;
  }

  public void setMarkerKey(String markerKey) {
    this.markerKey = markerKey;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
