package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "ladder_season",
    indexes = {@Index(name = "ix_ladder_state", columnList = "ladder_config_id,state")})
public class LadderSeason {

  public static final int MAX_NAME_LENGTH = 80;

  public enum State {
    SCHEDULED,
    ACTIVE,
    ENDED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "ladder_config_id")
  private LadderConfig ladderConfig;

  @Column(nullable = false)
  private String name;

  // ===== Existing date range fields retained for UI/reporting =====
  // Inclusive
  @Column(nullable = false)
  private LocalDate startDate;

  // Inclusive or last-day marker
  @Column(nullable = false)
  private LocalDate endDate;

  // ===== New: state + precise timestamps + audit =====

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private State state = State.ACTIVE;

  /** Precise moment the season was started (UTC) */
  @Column(nullable = false)
  private Instant startedAt = Instant.now();

  /** Precise moment the season ended (UTC), null while ACTIVE */
  @Column private Instant endedAt;

  /** Who started/ended (for audit/history) */
  @Column private Long startedByUserId;

  @Column private Long endedByUserId;

  @Column(name = "story_mode_enabled", nullable = false)
  private boolean storyModeEnabled = false;

  /**
   * Number of active standings recalculation jobs currently running for this season. A value
   * greater than zero means standings are mid-refresh.
   */
  @Column(nullable = false)
  private Integer standingsRecalcInFlight = 0;

  /** Most recent timestamp a standings recalculation was started. */
  @Column private Instant standingsRecalcLastStartedAt;

  /** Most recent timestamp a standings recalculation finished. */
  @Column private Instant standingsRecalcLastFinishedAt;

  /** Optimistic locking to avoid racey double transitions */
  @Version private long version;

  // === Getters / Setters ===

  public Long getId() {
    return id;
  }

  public LadderConfig getLadderConfig() {
    return ladderConfig;
  }

  public void setLadderConfig(LadderConfig ladderConfig) {
    this.ladderConfig = ladderConfig;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public State getState() {
    return state;
  }

  public void setState(State state) {
    this.state = state;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public void setEndedAt(Instant endedAt) {
    this.endedAt = endedAt;
  }

  public Long getStartedByUserId() {
    return startedByUserId;
  }

  public void setStartedByUserId(Long startedByUserId) {
    this.startedByUserId = startedByUserId;
  }

  public Long getEndedByUserId() {
    return endedByUserId;
  }

  public void setEndedByUserId(Long endedByUserId) {
    this.endedByUserId = endedByUserId;
  }

  public boolean isStoryModeEnabled() {
    return storyModeEnabled;
  }

  public void setStoryModeEnabled(boolean storyModeEnabled) {
    this.storyModeEnabled = storyModeEnabled;
  }

  public Integer getStandingsRecalcInFlight() {
    return standingsRecalcInFlight;
  }

  public void setStandingsRecalcInFlight(Integer standingsRecalcInFlight) {
    this.standingsRecalcInFlight = standingsRecalcInFlight;
  }

  public Instant getStandingsRecalcLastStartedAt() {
    return standingsRecalcLastStartedAt;
  }

  public void setStandingsRecalcLastStartedAt(Instant standingsRecalcLastStartedAt) {
    this.standingsRecalcLastStartedAt = standingsRecalcLastStartedAt;
  }

  public Instant getStandingsRecalcLastFinishedAt() {
    return standingsRecalcLastFinishedAt;
  }

  public void setStandingsRecalcLastFinishedAt(Instant standingsRecalcLastFinishedAt) {
    this.standingsRecalcLastFinishedAt = standingsRecalcLastFinishedAt;
  }

  @Transient
  public boolean isStandingsRecalcInProgress() {
    return standingsRecalcInFlight != null && standingsRecalcInFlight > 0;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
