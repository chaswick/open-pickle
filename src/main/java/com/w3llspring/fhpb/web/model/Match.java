package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "matches")
public class Match {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private long version;

  @Column(name = "played_at", nullable = false)
  private Instant playedAt = Instant.now();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Transient private boolean playedAtExplicitlySet = false;

  @Transient private boolean createdAtExplicitlySet = false;

  public Match() {
    Instant now = Instant.now();
    this.playedAt = now;
    this.createdAt = now;
  }

  // Team A (submitter designates ordering)
  @ManyToOne(optional = true)
  @JoinColumn(name = "a1_id")
  private User a1;

  @ManyToOne(optional = true)
  @JoinColumn(name = "a2_id")
  private User a2;

  @Column(name = "a1_guest", nullable = false)
  private boolean a1Guest;

  @Column(name = "a1_delta")
  private Integer a1Delta;

  @Column(name = "a2_guest", nullable = false)
  private boolean a2Guest;

  @Column(name = "a2_delta")
  private Integer a2Delta;

  // Team B
  @ManyToOne(optional = true)
  @JoinColumn(name = "b1_id")
  private User b1;

  @ManyToOne(optional = true)
  @JoinColumn(name = "b2_id")
  private User b2;

  @Column(name = "b1_guest", nullable = false)
  private boolean b1Guest;

  @Column(name = "b1_delta")
  private Integer b1Delta;

  @Column(name = "b2_guest", nullable = false)
  private boolean b2Guest;

  @Column(name = "b2_delta")
  private Integer b2Delta;

  @Column(name = "score_a", nullable = false)
  private int scoreA;

  @Column(name = "score_b", nullable = false)
  private int scoreB;

  @ManyToOne(optional = true)
  @JoinColumn(name = "cosigned_by_id")
  private User cosignedBy;

  @ManyToOne(optional = true)
  @JoinColumn(name = "logged_by_id")
  private User loggedBy;

  @ManyToOne(optional = true)
  @JoinColumn(name = "edited_by_id")
  private User editedBy;

  @Column(name = "edited_at")
  private Instant editedAt;

  @ManyToOne(optional = true)
  @JoinColumn(name = "disputed_by_id")
  private User disputedBy;

  @Column(name = "disputed_at")
  private Instant disputedAt;

  @Column(name = "dispute_note", columnDefinition = "TEXT")
  private String disputeNote;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MatchState state = MatchState.PROVISIONAL;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "season_id", nullable = false)
  private LadderSeason season;

  @ManyToOne(optional = true, fetch = FetchType.LAZY)
  @JoinColumn(name = "source_session_config_id")
  private LadderConfig sourceSessionConfig;

  // Voice/text interpretation metadata for ML training and debugging
  @Column(name = "transcript", columnDefinition = "TEXT")
  private String transcript;

  @Column(name = "confidence_score")
  private Integer confidenceScore;

  @Column(name = "confirmation_locked", nullable = false)
  private boolean confirmationLocked = false;

  @Column(name = "sportsmanship_rebate_applied", nullable = false)
  private boolean sportsmanshipRebateApplied = false;

  @Column(name = "score_estimated", nullable = false)
  private boolean scoreEstimated = false;

  @Column(name = "verification_notes", columnDefinition = "TEXT")
  private String verificationNotes;

  @Column(name = "exclude_from_standings", nullable = false)
  private boolean excludeFromStandings = false;

  // User self-correction flag for ML training
  // When true: user manually corrected this match after initial logging
  // Enables per-user interpretation training: transcript → original data → corrected data
  @Column(name = "user_corrected", nullable = false)
  private boolean userCorrected = false;

  @PrePersist
  private void touchCreatedAt() {
    synchronizeTimestampsForCreate();
  }

  public void synchronizeTimestampsForCreate() {
    if (playedAtExplicitlySet && !createdAtExplicitlySet) {
      createdAt = playedAt;
      return;
    }
    if (createdAtExplicitlySet && !playedAtExplicitlySet) {
      playedAt = createdAt;
      return;
    }
    if (playedAt == null && createdAt == null) {
      Instant now = Instant.now();
      playedAt = now;
      createdAt = now;
      return;
    }
    if (playedAt == null) {
      playedAt = createdAt;
      return;
    }
    if (createdAt == null) {
      createdAt = playedAt;
    }
  }

  public Long getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }

  public Instant getPlayedAt() {
    return playedAt;
  }

  public void setPlayedAt(Instant playedAt) {
    this.playedAt = playedAt;
    if (!createdAtExplicitlySet) {
      this.createdAt = playedAt;
    }
    this.playedAtExplicitlySet = true;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
    if (!playedAtExplicitlySet) {
      this.playedAt = createdAt;
    }
    this.createdAtExplicitlySet = true;
  }

  public User getA1() {
    return a1;
  }

  public void setA1(User a1) {
    this.a1 = a1;
  }

  public User getA2() {
    return a2;
  }

  public void setA2(User a2) {
    this.a2 = a2;
  }

  public boolean isA1Guest() {
    return a1Guest;
  }

  public void setA1Guest(boolean a1Guest) {
    this.a1Guest = a1Guest;
  }

  public Integer getA1Delta() {
    return a1Delta;
  }

  public void setA1Delta(Integer a1Delta) {
    this.a1Delta = a1Delta;
  }

  public boolean isA2Guest() {
    return a2Guest;
  }

  public void setA2Guest(boolean a2Guest) {
    this.a2Guest = a2Guest;
  }

  public Integer getA2Delta() {
    return a2Delta;
  }

  public void setA2Delta(Integer a2Delta) {
    this.a2Delta = a2Delta;
  }

  public User getB1() {
    return b1;
  }

  public void setB1(User b1) {
    this.b1 = b1;
  }

  public User getB2() {
    return b2;
  }

  public void setB2(User b2) {
    this.b2 = b2;
  }

  public boolean isB1Guest() {
    return b1Guest;
  }

  public void setB1Guest(boolean b1Guest) {
    this.b1Guest = b1Guest;
  }

  public Integer getB1Delta() {
    return b1Delta;
  }

  public void setB1Delta(Integer b1Delta) {
    this.b1Delta = b1Delta;
  }

  public boolean isB2Guest() {
    return b2Guest;
  }

  public void setB2Guest(boolean b2Guest) {
    this.b2Guest = b2Guest;
  }

  public Integer getB2Delta() {
    return b2Delta;
  }

  public void setB2Delta(Integer b2Delta) {
    this.b2Delta = b2Delta;
  }

  public int getScoreA() {
    return scoreA;
  }

  public void setScoreA(int scoreA) {
    this.scoreA = scoreA;
  }

  public int getScoreB() {
    return scoreB;
  }

  public void setScoreB(int scoreB) {
    this.scoreB = scoreB;
  }

  public User getCosignedBy() {
    return cosignedBy;
  }

  public void setCosignedBy(User cosignedBy) {
    this.cosignedBy = cosignedBy;
  }

  public User getLoggedBy() {
    return loggedBy;
  }

  public void setLoggedBy(User loggedBy) {
    this.loggedBy = loggedBy;
  }

  public User getEditedBy() {
    return editedBy;
  }

  public void setEditedBy(User editedBy) {
    this.editedBy = editedBy;
  }

  public Instant getEditedAt() {
    return editedAt;
  }

  public void setEditedAt(Instant editedAt) {
    this.editedAt = editedAt;
  }

  public User getDisputedBy() {
    return disputedBy;
  }

  public void setDisputedBy(User disputedBy) {
    this.disputedBy = disputedBy;
  }

  public Instant getDisputedAt() {
    return disputedAt;
  }

  public void setDisputedAt(Instant disputedAt) {
    this.disputedAt = disputedAt;
  }

  public String getDisputeNote() {
    return disputeNote;
  }

  public void setDisputeNote(String disputeNote) {
    this.disputeNote = disputeNote;
  }

  public MatchState getState() {
    return state;
  }

  public void setState(MatchState state) {
    this.state = state;
  }

  public LadderSeason getSeason() {
    return season;
  }

  public void setSeason(LadderSeason season) {
    this.season = season;
  }

  public LadderConfig getSourceSessionConfig() {
    return sourceSessionConfig;
  }

  public void setSourceSessionConfig(LadderConfig sourceSessionConfig) {
    this.sourceSessionConfig = sourceSessionConfig;
  }

  public String getTranscript() {
    return transcript;
  }

  public void setTranscript(String transcript) {
    this.transcript = transcript;
  }

  public Integer getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(Integer confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public boolean isConfirmationLocked() {
    return confirmationLocked;
  }

  public void setConfirmationLocked(boolean confirmationLocked) {
    this.confirmationLocked = confirmationLocked;
  }

  public boolean isSportsmanshipRebateApplied() {
    return sportsmanshipRebateApplied;
  }

  public void setSportsmanshipRebateApplied(boolean sportsmanshipRebateApplied) {
    this.sportsmanshipRebateApplied = sportsmanshipRebateApplied;
  }

  public boolean isScoreEstimated() {
    return scoreEstimated;
  }

  public void setScoreEstimated(boolean scoreEstimated) {
    this.scoreEstimated = scoreEstimated;
  }

  public String getVerificationNotes() {
    return verificationNotes;
  }

  public void setVerificationNotes(String verificationNotes) {
    this.verificationNotes = verificationNotes;
  }

  public boolean isExcludeFromStandings() {
    return excludeFromStandings;
  }

  public void setExcludeFromStandings(boolean excludeFromStandings) {
    this.excludeFromStandings = excludeFromStandings;
  }

  public boolean isUserCorrected() {
    return userCorrected;
  }

  public void setUserCorrected(boolean userCorrected) {
    this.userCorrected = userCorrected;
  }

  @Transient
  public boolean isTeamAWinner() {
    return scoreA > scoreB;
  }

  @Transient
  public int getPointDifferential() {
    return Math.abs(scoreA - scoreB);
  }

  @Transient
  public int getGuestCount() {
    int count = 0;
    if (a1Guest) count++;
    if (a2Guest) count++;
    if (b1Guest) count++;
    if (b2Guest) count++;
    return count;
  }

  /** Returns true if this match can be nullified (is not already nullified). */
  @Transient
  public boolean isNullifiable() {
    return state != MatchState.NULLIFIED;
  }

  @Transient
  public boolean isDisputed() {
    return state == MatchState.FLAGGED
        || disputedBy != null
        || disputedAt != null
        || (disputeNote != null && !disputeNote.isBlank());
  }

  /**
   * Returns true if this match is editable by the given user.
   *
   * <p>NOTE: This method does NOT check ladder admin status, as that requires a database query.
   * Controllers/services should check ladder admin status separately:
   *
   * <p>boolean canEdit = match.isEditableBy(user) ||
   * ladderAccessService.isSeasonAdmin(match.getSeason().getId(), user);
   *
   * <p>Returns the default non-admin edit rule for this match.
   *
   * <p>Controllers/services may still allow ladder admins to edit in states where regular users
   * cannot.
   */
  @Transient
  public boolean isEditableBy(User user) {
    return MatchWorkflowRules.canNonAdminEdit(this, user);
  }

  /**
   * Returns true if this match has both opponents as guests for a given user. This means the match
   * is a "personal record only" that won't affect ladder standings.
   */
  @Transient
  public boolean hasBothOpponentsAsGuests(User user) {
    if (user == null || user.getId() == null) {
      return false;
    }
    Long userId = user.getId();

    // Check which team the user is on
    boolean onTeamA =
        (a1 != null && userId.equals(a1.getId())) || (a2 != null && userId.equals(a2.getId()));
    boolean onTeamB =
        (b1 != null && userId.equals(b1.getId())) || (b2 != null && userId.equals(b2.getId()));

    if (onTeamA) {
      // User is on Team A, check if both Team B players are guests
      return b1Guest && b2Guest;
    } else if (onTeamB) {
      // User is on Team B, check if both Team A players are guests
      return a1Guest && a2Guest;
    }

    return false;
  }

  /**
   * Returns true when exactly one side has real players and the opposing side is entirely guests.
   * Matches in this shape can be treated as personal records for the real-player side.
   */
  @Transient
  public boolean hasGuestOnlyOpposingTeam() {
    int teamAReal = 0;
    int teamBReal = 0;

    if (!a1Guest && a1 != null && a1.getId() != null) {
      teamAReal++;
    }
    if (!a2Guest && a2 != null && a2.getId() != null) {
      teamAReal++;
    }
    if (!b1Guest && b1 != null && b1.getId() != null) {
      teamBReal++;
    }
    if (!b2Guest && b2 != null && b2.getId() != null) {
      teamBReal++;
    }

    return (teamAReal > 0 && teamBReal == 0) || (teamBReal > 0 && teamAReal == 0);
  }

  /**
   * Returns true if this match can be deleted/nullified by the given user. Only the original match
   * logger (creator) can delete a match.
   *
   * <p>NOTE: This method does NOT check ladder admin status, as that requires a database query.
   * Controllers/services should check ladder admin status separately.
   */
  @Transient
  public boolean isDeletableBy(User user) {
    if (user == null || user.getId() == null) {
      return false;
    }

    // Can't delete nullified matches
    if (state == MatchState.NULLIFIED) {
      return false;
    }

    // Can't delete confirmed matches
    if (state == MatchState.CONFIRMED) {
      return false;
    }

    if (state == MatchState.FLAGGED) {
      return false;
    }

    if (confirmationLocked) {
      return false;
    }

    // Only the original logger can delete
    if (loggedBy == null || loggedBy.getId() == null) {
      return false;
    }

    return user.getId().equals(loggedBy.getId());
  }
}
