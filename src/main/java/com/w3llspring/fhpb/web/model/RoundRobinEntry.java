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

  // Stable places in the schedule survive replacements. Player/name fields remain snapshots
  // for each game, so changing an unplayed game never rewrites an earlier result.
  @Column(name = "a1_slot_id")
  private Long a1SlotId;

  @Column(name = "a2_slot_id")
  private Long a2SlotId;

  @Column(name = "b1_slot_id")
  private Long b1SlotId;

  @Column(name = "b2_slot_id")
  private Long b2SlotId;

  @Column(name = "a1_guest_name", length = 80)
  private String a1GuestName;

  @Column(name = "a2_guest_name", length = 80)
  private String a2GuestName;

  @Column(name = "b1_guest_name", length = 80)
  private String b1GuestName;

  @Column(name = "b2_guest_name", length = 80)
  private String b2GuestName;

  public enum Position {
    A1,
    A2,
    B1,
    B2
  }

  public User player(Position position) {
    return switch (position) {
      case A1 -> a1;
      case A2 -> a2;
      case B1 -> b1;
      case B2 -> b2;
    };
  }

  public Long slotId(Position position) {
    Long stored =
        switch (position) {
          case A1 -> a1SlotId;
          case A2 -> a2SlotId;
          case B1 -> b1SlotId;
          case B2 -> b2SlotId;
        };
    User player = player(position);
    return stored != null ? stored : player != null ? player.getId() : null;
  }

  public String guestName(Position position) {
    return switch (position) {
      case A1 -> a1GuestName;
      case A2 -> a2GuestName;
      case B1 -> b1GuestName;
      case B2 -> b2GuestName;
    };
  }

  public void replace(Position position, Long slotId, User player, String guestName) {
    switch (position) {
      case A1 -> {
        a1SlotId = slotId;
        a1 = player;
        a1GuestName = guestName;
      }
      case A2 -> {
        a2SlotId = slotId;
        a2 = player;
        a2GuestName = guestName;
      }
      case B1 -> {
        b1SlotId = slotId;
        b1 = player;
        b1GuestName = guestName;
      }
      case B2 -> {
        b2SlotId = slotId;
        b2 = player;
        b2GuestName = guestName;
      }
    }
  }

  @PrePersist
  public void initializeSlots() {
    for (Position position : Position.values()) {
      replace(position, slotId(position), player(position), guestName(position));
    }
  }

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
