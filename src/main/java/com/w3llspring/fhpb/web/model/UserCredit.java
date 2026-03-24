package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_credit")
public class UserCredit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "amount", nullable = false)
  private double amount;

  @Column(name = "reason", length = 128)
  private String reason;

  @Column(name = "match_id")
  private Long matchId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public double getAmount() {
    return amount;
  }

  public void setAmount(double amount) {
    this.amount = amount;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Long getMatchId() {
    return matchId;
  }

  public void setMatchId(Long matchId) {
    this.matchId = matchId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
