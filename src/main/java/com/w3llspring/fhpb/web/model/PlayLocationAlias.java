package com.w3llspring.fhpb.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "play_location_alias",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"location_id", "user_id", "normalized_name"}))
public class PlayLocationAlias {

  public static final int MAX_NAME_LENGTH = 80;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "location_id", nullable = false)
  private PlayLocation location;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "display_name", nullable = false, length = MAX_NAME_LENGTH)
  private String displayName;

  @Column(name = "normalized_name", nullable = false, length = MAX_NAME_LENGTH)
  private String normalizedName;

  @Column(name = "phonetic_key", length = 40)
  private String phoneticKey;

  @Column(name = "usage_count", nullable = false)
  private int usageCount = 1;

  @Column(name = "first_used_at", nullable = false, updatable = false)
  private Instant firstUsedAt;

  @Column(name = "last_used_at", nullable = false)
  private Instant lastUsedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (firstUsedAt == null) {
      firstUsedAt = now;
    }
    if (lastUsedAt == null) {
      lastUsedAt = firstUsedAt;
    }
    if (usageCount < 1) {
      usageCount = 1;
    }
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public PlayLocation getLocation() {
    return location;
  }

  public void setLocation(PlayLocation location) {
    this.location = location;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getNormalizedName() {
    return normalizedName;
  }

  public void setNormalizedName(String normalizedName) {
    this.normalizedName = normalizedName;
  }

  public String getPhoneticKey() {
    return phoneticKey;
  }

  public void setPhoneticKey(String phoneticKey) {
    this.phoneticKey = phoneticKey;
  }

  public int getUsageCount() {
    return usageCount;
  }

  public void setUsageCount(int usageCount) {
    this.usageCount = usageCount;
  }

  public Instant getFirstUsedAt() {
    return firstUsedAt;
  }

  public void setFirstUsedAt(Instant firstUsedAt) {
    this.firstUsedAt = firstUsedAt;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(Instant lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }
}
