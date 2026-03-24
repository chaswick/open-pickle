package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "user_trophy",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_trophy_pair",
          columnNames = {"user_id", "trophy_id"})
    })
public class UserTrophy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_user_trophy_user"))
  private User user;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(
      name = "trophy_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_user_trophy_trophy"))
  private Trophy trophy;

  @Column(name = "award_count", nullable = false)
  private int awardCount = 1;

  @Column(name = "first_awarded_at")
  private Instant firstAwardedAt;

  @Column(name = "awarded_at", nullable = false)
  private Instant awardedAt;

  @Column(name = "last_awarded_at")
  private Instant lastAwardedAt;

  @Column(name = "awarded_reason", length = 512)
  private String awardedReason;

  @Column(name = "metadata", length = 512)
  private String metadata;

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Trophy getTrophy() {
    return trophy;
  }

  public void setTrophy(Trophy trophy) {
    this.trophy = trophy;
  }

  public int getAwardCount() {
    return awardCount;
  }

  public void setAwardCount(int awardCount) {
    this.awardCount = Math.max(0, awardCount);
  }

  public Instant getFirstAwardedAt() {
    return firstAwardedAt;
  }

  public void setFirstAwardedAt(Instant firstAwardedAt) {
    this.firstAwardedAt = firstAwardedAt;
  }

  public Instant getAwardedAt() {
    return awardedAt;
  }

  public void setAwardedAt(Instant awardedAt) {
    this.awardedAt = awardedAt;
  }

  public Instant getLastAwardedAt() {
    return lastAwardedAt;
  }

  public void setLastAwardedAt(Instant lastAwardedAt) {
    this.lastAwardedAt = lastAwardedAt;
  }

  public String getAwardedReason() {
    return awardedReason;
  }

  public void setAwardedReason(String awardedReason) {
    this.awardedReason = awardedReason;
  }

  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(String metadata) {
    this.metadata = metadata;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (awardCount <= 0) {
      awardCount = 1;
    }
    if (firstAwardedAt == null) {
      firstAwardedAt = awardedAt != null ? awardedAt : now;
    }
    if (awardedAt == null) {
      awardedAt = firstAwardedAt;
    }
    if (lastAwardedAt == null) {
      lastAwardedAt = awardedAt;
    }
  }
}
