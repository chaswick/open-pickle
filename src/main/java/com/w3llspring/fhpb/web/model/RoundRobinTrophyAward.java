package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

/** One contribution to a player's trophy count, attributable to a particular run. */
@Entity
@Table(
    name = "round_robin_trophy_award",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_rr_trophy_award",
            columnNames = {"round_robin_id", "trophy_id", "user_id"}))
public class RoundRobinTrophyAward {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "round_robin_id", nullable = false)
  @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
  private RoundRobin roundRobin;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "trophy_id", nullable = false)
  @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
  private Trophy trophy;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
  private User user;

  @Column(name = "awarded_at", nullable = false)
  private Instant awardedAt;

  @Column(length = 512, nullable = false)
  private String reason;

  public Long getId() {
    return id;
  }

  public RoundRobin getRoundRobin() {
    return roundRobin;
  }

  public void setRoundRobin(RoundRobin value) {
    roundRobin = value;
  }

  public Trophy getTrophy() {
    return trophy;
  }

  public void setTrophy(Trophy value) {
    trophy = value;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User value) {
    user = value;
  }

  public Instant getAwardedAt() {
    return awardedAt;
  }

  public void setAwardedAt(Instant value) {
    awardedAt = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    reason = value;
  }
}
