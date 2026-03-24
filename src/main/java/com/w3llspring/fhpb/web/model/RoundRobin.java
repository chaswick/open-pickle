package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "round_robin")
public class RoundRobin {

  public enum Format {
    ROTATING_PARTNERS,
    FIXED_TEAMS;

    public static Format fromParam(String value) {
      if (value == null || value.isBlank()) {
        return ROTATING_PARTNERS;
      }
      try {
        return Format.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return ROTATING_PARTNERS;
      }
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "season_id", nullable = false)
  private LadderSeason season;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_config_id")
  private LadderConfig sessionConfig;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "current_round", nullable = false)
  private int currentRound = 1;

  @Column(name = "name")
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "format", nullable = false, length = 32)
  private Format format = Format.ROTATING_PARTNERS;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private User createdBy;

  public Long getId() {
    return id;
  }

  public LadderSeason getSeason() {
    return season;
  }

  public void setSeason(LadderSeason season) {
    this.season = season;
  }

  public LadderConfig getSessionConfig() {
    return sessionConfig;
  }

  public void setSessionConfig(LadderConfig sessionConfig) {
    this.sessionConfig = sessionConfig;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public int getCurrentRound() {
    return currentRound;
  }

  public void setCurrentRound(int currentRound) {
    this.currentRound = currentRound;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Format getFormat() {
    return format;
  }

  public void setFormat(Format format) {
    this.format = format != null ? format : Format.ROTATING_PARTNERS;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  @Transient
  public boolean isFixedTeamsFormat() {
    return format == Format.FIXED_TEAMS;
  }
}
