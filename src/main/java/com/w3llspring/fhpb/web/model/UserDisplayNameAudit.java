package com.w3llspring.fhpb.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
    name = "user_display_name_audit",
    indexes = {
      @Index(
          name = "idx_user_display_name_audit_ladder_changed_at",
          columnList = "ladder_config_id, changed_at"),
      @Index(
          name = "idx_user_display_name_audit_user_changed_at",
          columnList = "user_id, changed_at")
    })
public class UserDisplayNameAudit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "ladder_config_id")
  private Long ladderConfigId;

  @Column(name = "changed_by_user_id", nullable = false)
  private Long changedByUserId;

  @Column(name = "old_display_name", nullable = false, length = User.MAX_NICKNAME_LENGTH)
  private String oldDisplayName;

  @Column(name = "new_display_name", nullable = false, length = User.MAX_NICKNAME_LENGTH)
  private String newDisplayName;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt = Instant.now();

  @PrePersist
  private void prePersist() {
    if (changedAt == null) {
      changedAt = Instant.now();
    }
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getLadderConfigId() {
    return ladderConfigId;
  }

  public void setLadderConfigId(Long ladderConfigId) {
    this.ladderConfigId = ladderConfigId;
  }

  public Long getChangedByUserId() {
    return changedByUserId;
  }

  public void setChangedByUserId(Long changedByUserId) {
    this.changedByUserId = changedByUserId;
  }

  public String getOldDisplayName() {
    return oldDisplayName;
  }

  public void setOldDisplayName(String oldDisplayName) {
    this.oldDisplayName = oldDisplayName;
  }

  public String getNewDisplayName() {
    return newDisplayName;
  }

  public void setNewDisplayName(String newDisplayName) {
    this.newDisplayName = newDisplayName;
  }

  public Instant getChangedAt() {
    return changedAt;
  }

  public void setChangedAt(Instant changedAt) {
    this.changedAt = changedAt;
  }
}
