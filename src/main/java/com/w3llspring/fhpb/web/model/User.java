package com.w3llspring.fhpb.web.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.w3llspring.fhpb.web.service.user.UserPublicCodeGenerator;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
  public static final int MAX_EMAIL_LENGTH = 45;

  /** TODO: Add error / validation messages */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore // SECURITY: Prevent email from being serialized to JSON/frontend
  @Column(nullable = false, unique = true, length = MAX_EMAIL_LENGTH)
  private String email;

  public static final int MAX_NICKNAME_LENGTH = 24;

  @Column(nullable = false, unique = true, length = MAX_NICKNAME_LENGTH)
  private String nickName;

  @JsonIgnore
  @Column(name = "public_code", unique = true, length = 17)
  private String publicCode;

  @Transient // This field is not persisted, just used during registration
  private String courtNamesInput;

  @OneToMany(
      mappedBy = "user",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private Set<UserCourtName> courtNames = new LinkedHashSet<>();

  @JsonIgnore // SECURITY: Never expose password hashes
  @Column(nullable = false, length = 64)
  private String password;

  @JsonIgnore // SECURITY: Never expose password reset tokens
  @Column(name = "reset_password_token", length = 30)
  private String resetPasswordToken;

  @JsonIgnore
  @Column(name = "reset_password_token_expires_at")
  private Instant resetPasswordTokenExpiresAt;

  @Column private Boolean isAdmin = false;

  public boolean isAdmin() {
    if (isAdmin == null) {
      isAdmin = false;
    }
    return isAdmin;
  }

  public void setAdmin(boolean isAdmin) {
    this.isAdmin = isAdmin;
  }

  public String getResetPasswordToken() {
    return resetPasswordToken;
  }

  public Instant getResetPasswordTokenExpiresAt() {
    return resetPasswordTokenExpiresAt;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public void setResetPasswordToken(String token) {
    this.resetPasswordToken = token;
  }

  public void setResetPasswordTokenExpiresAt(Instant resetPasswordTokenExpiresAt) {
    this.resetPasswordTokenExpiresAt = resetPasswordTokenExpiresAt;
  }

  public String getNickName() {
    return nickName;
  }

  public void setNickName(String nickName) {
    this.nickName = nickName;
  }

  public String getPublicCode() {
    return publicCode;
  }

  public void setPublicCode(String publicCode) {
    this.publicCode = publicCode;
  }

  public Set<UserCourtName> getCourtNames() {
    return courtNames;
  }

  public void setCourtNames(Set<UserCourtName> courtNames) {
    this.courtNames = courtNames != null ? courtNames : new LinkedHashSet<>();
  }

  // getters and setters are not shown

  private Integer failedPassphraseAttempts;
  private java.time.Instant passphraseTimeoutUntil;

  @Column(name = "max_owned_ladders")
  private Integer maxOwnedLadders;

  @Column(name = "last_match_logged_at")
  private Instant lastMatchLoggedAt;

  @JsonIgnore
  @Column(name = "last_display_name_change_at")
  private Instant lastDisplayNameChangeAt;

  @Column(name = "consecutive_match_logs")
  private Integer consecutiveMatchLogs;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Column(name = "acknowledged_terms_at")
  private Instant acknowledgedTermsAt;

  @Column(name = "registered_at", nullable = true, updatable = false)
  private Instant registeredAt;

  @Column(name = "meetups_email_opt_in", nullable = false)
  private Boolean meetupsEmailOptIn = false;

  @Column(name = "meetups_email_last_sent_at")
  private Instant meetupsEmailLastSentAt;

  @Column(name = "meetups_email_pending", nullable = false)
  private Boolean meetupsEmailPending = false;

  @Column(name = "meetups_email_daily_sent_count")
  private Integer meetupsEmailDailySentCount;

  @Column(name = "meetups_email_daily_sent_day")
  private LocalDate meetupsEmailDailySentDay;

  @Column(name = "app_ui_enabled", nullable = false)
  private Boolean appUiEnabled = true;

  @Column(name = "time_zone", length = 64)
  private String timeZone;

  @Column(name = "competition_safe_display_name", length = 32)
  private String competitionSafeDisplayName;

  @Column(name = "competition_safe_display_name_active", nullable = false)
  private Boolean competitionSafeDisplayNameActive = false;

  @Column(name = "competition_safe_display_name_basis", length = 64)
  private String competitionSafeDisplayNameBasis;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "badge_slot_1_trophy_id",
      foreignKey = @ForeignKey(name = "fk_users_badge_slot_1_trophy"))
  private Trophy badgeSlot1Trophy;

  @Column(name = "badge_slot_1_trophy_id", insertable = false, updatable = false)
  private Long badgeSlot1TrophyId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "badge_slot_2_trophy_id",
      foreignKey = @ForeignKey(name = "fk_users_badge_slot_2_trophy"))
  private Trophy badgeSlot2Trophy;

  @Column(name = "badge_slot_2_trophy_id", insertable = false, updatable = false)
  private Long badgeSlot2TrophyId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "badge_slot_3_trophy_id",
      foreignKey = @ForeignKey(name = "fk_users_badge_slot_3_trophy"))
  private Trophy badgeSlot3Trophy;

  @Column(name = "badge_slot_3_trophy_id", insertable = false, updatable = false)
  private Long badgeSlot3TrophyId;

  @Transient private boolean acceptTerms;

  public int getFailedPassphraseAttempts() {
    return failedPassphraseAttempts == null ? 0 : failedPassphraseAttempts;
  }

  public void setFailedPassphraseAttempts(Integer attempts) {
    this.failedPassphraseAttempts = attempts;
  }

  public java.time.Instant getPassphraseTimeoutUntil() {
    return passphraseTimeoutUntil;
  }

  public void setPassphraseTimeoutUntil(java.time.Instant t) {
    this.passphraseTimeoutUntil = t;
  }

  public Integer getMaxOwnedLadders() {
    return maxOwnedLadders;
  }

  public void setMaxOwnedLadders(Integer maxOwnedLadders) {
    this.maxOwnedLadders = maxOwnedLadders;
  }

  public int resolveMaxOwnedLadders(int fallback) {
    return maxOwnedLadders != null ? maxOwnedLadders : fallback;
  }

  public String getCourtNamesInput() {
    return courtNamesInput;
  }

  public void setCourtNamesInput(String courtNamesInput) {
    this.courtNamesInput = courtNamesInput;
  }

  public Instant getLastMatchLoggedAt() {
    return lastMatchLoggedAt;
  }

  public void setLastMatchLoggedAt(Instant lastMatchLoggedAt) {
    this.lastMatchLoggedAt = lastMatchLoggedAt;
  }

  public Instant getLastDisplayNameChangeAt() {
    return lastDisplayNameChangeAt;
  }

  public void setLastDisplayNameChangeAt(Instant lastDisplayNameChangeAt) {
    this.lastDisplayNameChangeAt = lastDisplayNameChangeAt;
  }

  public Integer getConsecutiveMatchLogs() {
    return consecutiveMatchLogs != null ? consecutiveMatchLogs : 0;
  }

  public void setConsecutiveMatchLogs(Integer consecutiveMatchLogs) {
    this.consecutiveMatchLogs = consecutiveMatchLogs;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(Instant lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }

  public Instant getAcknowledgedTermsAt() {
    return acknowledgedTermsAt;
  }

  public void setAcknowledgedTermsAt(Instant acknowledgedTermsAt) {
    this.acknowledgedTermsAt = acknowledgedTermsAt;
  }

  public Instant getRegisteredAt() {
    return registeredAt;
  }

  public void setRegisteredAt(Instant registeredAt) {
    this.registeredAt = registeredAt;
  }

  public boolean isMeetupsEmailOptIn() {
    return meetupsEmailOptIn != null && meetupsEmailOptIn;
  }

  public void setMeetupsEmailOptIn(Boolean meetupsEmailOptIn) {
    this.meetupsEmailOptIn = meetupsEmailOptIn;
  }

  public Instant getMeetupsEmailLastSentAt() {
    return meetupsEmailLastSentAt;
  }

  public void setMeetupsEmailLastSentAt(Instant meetupsEmailLastSentAt) {
    this.meetupsEmailLastSentAt = meetupsEmailLastSentAt;
  }

  public boolean isMeetupsEmailPending() {
    return meetupsEmailPending != null && meetupsEmailPending;
  }

  public void setMeetupsEmailPending(Boolean meetupsEmailPending) {
    this.meetupsEmailPending = meetupsEmailPending;
  }

  public Integer getMeetupsEmailDailySentCount() {
    return meetupsEmailDailySentCount;
  }

  public void setMeetupsEmailDailySentCount(Integer meetupsEmailDailySentCount) {
    this.meetupsEmailDailySentCount = meetupsEmailDailySentCount;
  }

  public LocalDate getMeetupsEmailDailySentDay() {
    return meetupsEmailDailySentDay;
  }

  public void setMeetupsEmailDailySentDay(LocalDate meetupsEmailDailySentDay) {
    this.meetupsEmailDailySentDay = meetupsEmailDailySentDay;
  }

  public boolean isAppUiEnabled() {
    return appUiEnabled != null && appUiEnabled;
  }

  public void setAppUiEnabled(Boolean appUiEnabled) {
    this.appUiEnabled = appUiEnabled;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public String getCompetitionSafeDisplayName() {
    return competitionSafeDisplayName;
  }

  public void setCompetitionSafeDisplayName(String competitionSafeDisplayName) {
    this.competitionSafeDisplayName = competitionSafeDisplayName;
  }

  public boolean isCompetitionSafeDisplayNameActive() {
    return competitionSafeDisplayNameActive != null && competitionSafeDisplayNameActive;
  }

  public void setCompetitionSafeDisplayNameActive(Boolean competitionSafeDisplayNameActive) {
    this.competitionSafeDisplayNameActive = competitionSafeDisplayNameActive;
  }

  public String getCompetitionSafeDisplayNameBasis() {
    return competitionSafeDisplayNameBasis;
  }

  public void setCompetitionSafeDisplayNameBasis(String competitionSafeDisplayNameBasis) {
    this.competitionSafeDisplayNameBasis = competitionSafeDisplayNameBasis;
  }

  public Trophy getBadgeSlot1Trophy() {
    return badgeSlot1Trophy;
  }

  public void setBadgeSlot1Trophy(Trophy badgeSlot1Trophy) {
    this.badgeSlot1Trophy = badgeSlot1Trophy;
    this.badgeSlot1TrophyId = badgeSlot1Trophy != null ? badgeSlot1Trophy.getId() : null;
  }

  public Long getBadgeSlot1TrophyId() {
    if (badgeSlot1TrophyId != null) {
      return badgeSlot1TrophyId;
    }
    return badgeSlot1Trophy != null ? badgeSlot1Trophy.getId() : null;
  }

  public Trophy getBadgeSlot2Trophy() {
    return badgeSlot2Trophy;
  }

  public void setBadgeSlot2Trophy(Trophy badgeSlot2Trophy) {
    this.badgeSlot2Trophy = badgeSlot2Trophy;
    this.badgeSlot2TrophyId = badgeSlot2Trophy != null ? badgeSlot2Trophy.getId() : null;
  }

  public Long getBadgeSlot2TrophyId() {
    if (badgeSlot2TrophyId != null) {
      return badgeSlot2TrophyId;
    }
    return badgeSlot2Trophy != null ? badgeSlot2Trophy.getId() : null;
  }

  public Trophy getBadgeSlot3Trophy() {
    return badgeSlot3Trophy;
  }

  public void setBadgeSlot3Trophy(Trophy badgeSlot3Trophy) {
    this.badgeSlot3Trophy = badgeSlot3Trophy;
    this.badgeSlot3TrophyId = badgeSlot3Trophy != null ? badgeSlot3Trophy.getId() : null;
  }

  public Long getBadgeSlot3TrophyId() {
    if (badgeSlot3TrophyId != null) {
      return badgeSlot3TrophyId;
    }
    return badgeSlot3Trophy != null ? badgeSlot3Trophy.getId() : null;
  }

  public boolean isAcceptTerms() {
    return acceptTerms;
  }

  public void setAcceptTerms(boolean acceptTerms) {
    this.acceptTerms = acceptTerms;
  }

  @PrePersist
  @PreUpdate
  private void ensurePublicCode() {
    if (publicCode == null || publicCode.isBlank()) {
      publicCode = UserPublicCodeGenerator.nextCode();
    }
  }
}
