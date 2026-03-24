package com.w3llspring.fhpb.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "competition_suspicious_match_flag",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uc_comp_suspicious_match_reason",
          columnNames = {"match_id", "reason_code"})
    },
    indexes = {
      @Index(name = "idx_comp_suspicious_season_created", columnList = "season_id, created_at"),
      @Index(name = "idx_comp_suspicious_match", columnList = "match_id")
    })
public class CompetitionSuspiciousMatchFlag {

  public enum ReasonCode {
    MAXIMIZED_DELTA,
    REPEATED_MAXIMIZED_DELTA_PATTERN,
    RAPID_TURNAROUND,
    CLOSED_PLAYER_POD
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "season_id", nullable = false)
  private LadderSeason season;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "match_id", nullable = false)
  private Match match;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", nullable = false, length = 64)
  private ReasonCode reasonCode;

  @Column(nullable = false)
  private int severity;

  @Column(nullable = false, length = 255)
  private String summary = "";

  @Column(columnDefinition = "TEXT")
  private String details;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public LadderSeason getSeason() {
    return season;
  }

  public void setSeason(LadderSeason season) {
    this.season = season;
  }

  public Match getMatch() {
    return match;
  }

  public void setMatch(Match match) {
    this.match = match;
  }

  public ReasonCode getReasonCode() {
    return reasonCode;
  }

  public void setReasonCode(ReasonCode reasonCode) {
    this.reasonCode = reasonCode;
  }

  public int getSeverity() {
    return severity;
  }

  public void setSeverity(int severity) {
    this.severity = severity;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
