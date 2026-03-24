package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "ladder_membership",
    uniqueConstraints = @UniqueConstraint(columnNames = {"ladder_config_id", "user_id"}))
public class LadderMembership {

  public enum Role {
    MEMBER,
    ADMIN
  }

  public enum State {
    ACTIVE,
    LEFT,
    BANNED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "ladder_config_id")
  private LadderConfig ladderConfig;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private Role role = Role.MEMBER;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private State state = State.ACTIVE;

  @Column(nullable = false)
  private Instant joinedAt = Instant.now();

  private Instant leftAt;

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

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public State getState() {
    return state;
  }

  public void setState(State state) {
    this.state = state;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public void setJoinedAt(Instant joinedAt) {
    this.joinedAt = joinedAt;
  }

  public Instant getLeftAt() {
    return leftAt;
  }

  public void setLeftAt(Instant leftAt) {
    this.leftAt = leftAt;
  }

  // getters/setters …
}
