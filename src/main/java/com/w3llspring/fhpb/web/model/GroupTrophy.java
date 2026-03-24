package com.w3llspring.fhpb.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "group_trophy",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_group_trophy_pair",
          columnNames = {"season_id", "trophy_id"})
    })
public class GroupTrophy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(
      name = "season_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_group_trophy_season"))
  private LadderSeason season;

  @ManyToOne(optional = false)
  @JoinColumn(
      name = "trophy_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_group_trophy_trophy"))
  private Trophy trophy;

  @Column(name = "award_count", nullable = false)
  private int awardCount = 1;

  @Column(name = "first_awarded_at", nullable = false)
  private Instant firstAwardedAt;

  @Column(name = "awarded_at", nullable = false)
  private Instant awardedAt;

  @Column(name = "last_awarded_at", nullable = false)
  private Instant lastAwardedAt;

  @Column(name = "awarded_reason", length = 512)
  private String awardedReason;

  @Column(name = "award_match_ids", columnDefinition = "TEXT")
  private String awardMatchIds;

  public Long getId() {
    return id;
  }

  public LadderSeason getSeason() {
    return season;
  }

  public void setSeason(LadderSeason season) {
    this.season = season;
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

  public String getAwardMatchIds() {
    return awardMatchIds;
  }

  public void setAwardMatchIds(String awardMatchIds) {
    this.awardMatchIds = awardMatchIds;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (firstAwardedAt == null) {
      firstAwardedAt = now;
    }
    if (lastAwardedAt == null) {
      lastAwardedAt = firstAwardedAt;
    }
    if (awardedAt == null) {
      awardedAt = lastAwardedAt;
    }
    if (awardCount <= 0) {
      awardCount = 1;
    }
  }

  @PreUpdate
  void onUpdate() {
    if (firstAwardedAt == null) {
      firstAwardedAt = Instant.now();
    }
    if (lastAwardedAt == null) {
      lastAwardedAt = awardedAt != null ? awardedAt : firstAwardedAt;
    }
    if (awardedAt == null) {
      awardedAt = lastAwardedAt;
    }
    if (awardCount < 0) {
      awardCount = 0;
    }
  }
}
