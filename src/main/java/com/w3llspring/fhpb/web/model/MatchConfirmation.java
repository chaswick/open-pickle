package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "match_confirmation",
    uniqueConstraints = @UniqueConstraint(columnNames = {"match_id", "player_id"}))
public class MatchConfirmation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "match_id", nullable = false)
  private Match match;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "player_id", nullable = false)
  private User player;

  @Column(name = "team", nullable = false)
  private String team; // 'A' or 'B'

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false)
  private ConfirmationMethod method = ConfirmationMethod.MANUAL;

  @Column(name = "casual_mode_auto_confirmed", nullable = false)
  private boolean casualModeAutoConfirmed = false;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @PrePersist
  private void prePersist() {
    if (createdAt == null) createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  private void preUpdate() {
    updatedAt = Instant.now();
  }

  public enum ConfirmationMethod {
    MANUAL,
    AUTO
  }

  // Getters/setters
  public Long getId() {
    return id;
  }

  public Match getMatch() {
    return match;
  }

  public void setMatch(Match match) {
    this.match = match;
  }

  public User getPlayer() {
    return player;
  }

  public void setPlayer(User player) {
    this.player = player;
  }

  public String getTeam() {
    return team;
  }

  public void setTeam(String team) {
    this.team = team;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public void setConfirmedAt(Instant confirmedAt) {
    this.confirmedAt = confirmedAt;
  }

  public ConfirmationMethod getMethod() {
    return method;
  }

  public void setMethod(ConfirmationMethod method) {
    this.method = method;
  }

  public boolean isCasualModeAutoConfirmed() {
    return casualModeAutoConfirmed;
  }

  public void setCasualModeAutoConfirmed(boolean casualModeAutoConfirmed) {
    this.casualModeAutoConfirmed = casualModeAutoConfirmed;
  }
}
