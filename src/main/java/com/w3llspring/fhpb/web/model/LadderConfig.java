package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ladder_config")
public class LadderConfig {

  public static final int MAX_ROLLING_EVERY_COUNT = 12;
  public static final int MAX_TITLE_LENGTH = 80;

  public enum Status {
    ACTIVE,
    ARCHIVED
  }

  public enum Type {
    STANDARD,
    COMPETITION,
    SESSION
  }

  /** Season mode: rolling cadence or manual start/stop */
  public enum Mode {
    ROLLING,
    MANUAL
  }

  /** Rolling cadence unit */
  public enum CadenceUnit {
    WEEKS,
    MONTHS
  }

  /** Standings scoring algorithm */
  public enum ScoringAlgorithm {
    MARGIN_CURVE_V1,
    BALANCED_V1
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 120)
  private String title;

  // Config owner; also becomes ADMIN member on create
  @Column(nullable = false)
  private Long ownerUserId;

  // Verbal invite to join this ladder (config-wide)
  @Column(nullable = true, unique = true, length = 64)
  private String inviteCode;

  @Column(name = "last_invite_change_at")
  private Instant lastInviteChangeAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status = Status.ACTIVE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Type type = Type.STANDARD;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "pending_deletion", nullable = false)
  private boolean pendingDeletion = false;

  @Column(name = "pending_deletion_at")
  private Instant pendingDeletionAt;

  @Column(name = "pending_deletion_by_user_id")
  private Long pendingDeletionByUserId;

  @Column(name = "target_season_id")
  private Long targetSeasonId;

  @Column(name = "expires_at")
  private Instant expiresAt;

  /** === New: Season mode + rolling cadence and anti-spam knob === */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Mode mode = Mode.ROLLING;

  /** Rolling cadence: "Every X [unit]" (only used when mode==ROLLING) */
  @Column(nullable = false)
  private int rollingEveryCount = 6; // e.g., 6 weeks (default)

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CadenceUnit rollingEveryUnit = CadenceUnit.WEEKS;

  /** Max season transitions (start/end) allowed in any rolling 24h window */
  @Column(nullable = false)
  private int maxTransitionsPerDay = 3;

  /** Last time a season was created (for rate limiting) */
  @Column(nullable = true)
  private Instant lastSeasonCreatedAt;

  /** Security level for match logging */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private LadderSecurity securityLevel = LadderSecurity.STANDARD;

  /**
   * When true (and security mode is SELF_CONFIRM), matches where one team is entirely guests are
   * allowed as personal records and can be confirmed, but are excluded from standings.
   */
  @Column(name = "allow_guest_only_personal_matches", nullable = false)
  private boolean allowGuestOnlyPersonalMatches = false;

  /**
   * When enabled, the latest ended season rating in this ladder becomes each player's starting
   * rating in the next season.
   */
  @Column(name = "carry_over_previous_rating", nullable = false)
  private boolean carryOverPreviousRating = false;

  @Column(name = "story_mode_default_enabled", nullable = false)
  private boolean storyModeDefaultEnabled = false;

  @Column(name = "tournament_mode", nullable = false)
  private boolean tournamentMode = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "scoring_algorithm", nullable = false, length = 32)
  private ScoringAlgorithm scoringAlgorithm = ScoringAlgorithm.MARGIN_CURVE_V1;

  /** Optimistic locking for race safety on config edits / mode switches */
  @Version private long version;

  @PreUpdate
  public void touch() {
    this.updatedAt = Instant.now();
  }

  // === Getters / Setters ===

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Long getOwnerUserId() {
    return ownerUserId;
  }

  public void setOwnerUserId(Long ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  public String getInviteCode() {
    return inviteCode;
  }

  public void setInviteCode(String inviteCode) {
    this.inviteCode = inviteCode;
  }

  public Instant getLastInviteChangeAt() {
    return lastInviteChangeAt;
  }

  public void setLastInviteChangeAt(Instant lastInviteChangeAt) {
    this.lastInviteChangeAt = lastInviteChangeAt;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public boolean isPendingDeletion() {
    return pendingDeletion;
  }

  public void setPendingDeletion(boolean pendingDeletion) {
    this.pendingDeletion = pendingDeletion;
  }

  public Instant getPendingDeletionAt() {
    return pendingDeletionAt;
  }

  public void setPendingDeletionAt(Instant pendingDeletionAt) {
    this.pendingDeletionAt = pendingDeletionAt;
  }

  public Long getPendingDeletionByUserId() {
    return pendingDeletionByUserId;
  }

  public void setPendingDeletionByUserId(Long pendingDeletionByUserId) {
    this.pendingDeletionByUserId = pendingDeletionByUserId;
  }

  public Long getTargetSeasonId() {
    return targetSeasonId;
  }

  public void setTargetSeasonId(Long targetSeasonId) {
    this.targetSeasonId = targetSeasonId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public int getRollingEveryCount() {
    return rollingEveryCount;
  }

  public void setRollingEveryCount(int rollingEveryCount) {
    this.rollingEveryCount = rollingEveryCount;
  }

  public CadenceUnit getRollingEveryUnit() {
    return rollingEveryUnit;
  }

  public void setRollingEveryUnit(CadenceUnit rollingEveryUnit) {
    this.rollingEveryUnit = rollingEveryUnit;
  }

  public int getMaxTransitionsPerDay() {
    return maxTransitionsPerDay;
  }

  public void setMaxTransitionsPerDay(int maxTransitionsPerDay) {
    this.maxTransitionsPerDay = maxTransitionsPerDay;
  }

  public Instant getLastSeasonCreatedAt() {
    return lastSeasonCreatedAt;
  }

  public void setLastSeasonCreatedAt(Instant lastSeasonCreatedAt) {
    this.lastSeasonCreatedAt = lastSeasonCreatedAt;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  public LadderSecurity getSecurityLevel() {
    return securityLevel;
  }

  public void setSecurityLevel(LadderSecurity securityLevel) {
    this.securityLevel = securityLevel;
  }

  public boolean isAllowGuestOnlyPersonalMatches() {
    return allowGuestOnlyPersonalMatches;
  }

  public void setAllowGuestOnlyPersonalMatches(boolean allowGuestOnlyPersonalMatches) {
    this.allowGuestOnlyPersonalMatches = allowGuestOnlyPersonalMatches;
  }

  public boolean isCarryOverPreviousRating() {
    return carryOverPreviousRating;
  }

  public void setCarryOverPreviousRating(boolean carryOverPreviousRating) {
    this.carryOverPreviousRating = carryOverPreviousRating;
  }

  public boolean isStoryModeDefaultEnabled() {
    return storyModeDefaultEnabled;
  }

  public void setStoryModeDefaultEnabled(boolean storyModeDefaultEnabled) {
    this.storyModeDefaultEnabled = storyModeDefaultEnabled;
  }

  public boolean isTournamentMode() {
    return tournamentMode;
  }

  public void setTournamentMode(boolean tournamentMode) {
    this.tournamentMode = tournamentMode;
  }

  public ScoringAlgorithm getScoringAlgorithm() {
    return scoringAlgorithm;
  }

  public void setScoringAlgorithm(ScoringAlgorithm scoringAlgorithm) {
    this.scoringAlgorithm = scoringAlgorithm;
  }

  @Transient
  public boolean isSessionType() {
    return type == Type.SESSION;
  }

  @Transient
  public boolean isCompetitionType() {
    return type == Type.COMPETITION;
  }
}
