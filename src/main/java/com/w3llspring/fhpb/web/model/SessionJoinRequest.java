package com.w3llspring.fhpb.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(
    name = "session_join_request",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"ladder_config_id", "requester_user_id"}))
public class SessionJoinRequest {

  public enum Status {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "ladder_config_id")
  private LadderConfig ladderConfig;

  @Column(name = "requester_user_id", nullable = false)
  private Long requesterUserId;

  @Column(name = "invite_code_snapshot", nullable = false, length = 64)
  private String inviteCodeSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status = Status.PENDING;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt = Instant.now();

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt = Instant.now();

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolved_by_user_id")
  private Long resolvedByUserId;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version private long version;

  @PrePersist
  @PreUpdate
  public void touch() {
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public LadderConfig getLadderConfig() {
    return ladderConfig;
  }

  public void setLadderConfig(LadderConfig ladderConfig) {
    this.ladderConfig = ladderConfig;
  }

  public Long getRequesterUserId() {
    return requesterUserId;
  }

  public void setRequesterUserId(Long requesterUserId) {
    this.requesterUserId = requesterUserId;
  }

  public String getInviteCodeSnapshot() {
    return inviteCodeSnapshot;
  }

  public void setInviteCodeSnapshot(String inviteCodeSnapshot) {
    this.inviteCodeSnapshot = inviteCodeSnapshot;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public void setRequestedAt(Instant requestedAt) {
    this.requestedAt = requestedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public Long getResolvedByUserId() {
    return resolvedByUserId;
  }

  public void setResolvedByUserId(Long resolvedByUserId) {
    this.resolvedByUserId = resolvedByUserId;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
