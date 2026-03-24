package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;

@Entity
@Table(name = "round_robin_entry")
public class RoundRobinEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "round_robin_id", nullable = false)
  private RoundRobin roundRobin;

  @Column(name = "round_number", nullable = false)
  private int roundNumber;

  // nullable: null means a bye or not yet created
  @Column(name = "match_id")
  private Long matchId;

  @ManyToOne(optional = true)
  @JoinColumn(name = "a1_id")
  private User a1;

  @ManyToOne(optional = true)
  @JoinColumn(name = "a2_id")
  private User a2;

  @ManyToOne(optional = true)
  @JoinColumn(name = "b1_id")
  private User b1;

  @ManyToOne(optional = true)
  @JoinColumn(name = "b2_id")
  private User b2;

  @Column(name = "bye", nullable = false)
  private boolean bye = false;

  public Long getId() {
    return id;
  }

  public RoundRobin getRoundRobin() {
    return roundRobin;
  }

  public void setRoundRobin(RoundRobin roundRobin) {
    this.roundRobin = roundRobin;
  }

  public int getRoundNumber() {
    return roundNumber;
  }

  public void setRoundNumber(int roundNumber) {
    this.roundNumber = roundNumber;
  }

  public Long getMatchId() {
    return matchId;
  }

  public void setMatchId(Long matchId) {
    this.matchId = matchId;
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

  public boolean isBye() {
    return bye;
  }

  public void setBye(boolean bye) {
    this.bye = bye;
  }
}
