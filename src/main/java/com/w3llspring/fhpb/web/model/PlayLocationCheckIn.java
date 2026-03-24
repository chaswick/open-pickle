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
import java.time.Instant;

@Entity
@Table(name = "play_location_check_in")
public class PlayLocationCheckIn {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "location_id", nullable = false)
  private PlayLocation location;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "display_name", nullable = false, length = PlayLocationAlias.MAX_NAME_LENGTH)
  private String displayName;

  @Column(name = "checked_in_at", nullable = false, updatable = false)
  private Instant checkedInAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @PrePersist
  void onCreate() {
    if (checkedInAt == null) {
      checkedInAt = Instant.now();
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

  public Instant getCheckedInAt() {
    return checkedInAt;
  }

  public void setCheckedInAt(Instant checkedInAt) {
    this.checkedInAt = checkedInAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public void setEndedAt(Instant endedAt) {
    this.endedAt = endedAt;
  }
}
