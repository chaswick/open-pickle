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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "ladder_meetup_rsvp",
    uniqueConstraints = @UniqueConstraint(columnNames = {"slot_id", "user_id"}))
public class LadderMeetupRsvp {

  public enum Status {
    IN,
    MAYBE,
    CANT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "slot_id")
  private LadderMeetupSlot slot;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 8)
  private Status status;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Long getId() {
    return id;
  }

  public LadderMeetupSlot getSlot() {
    return slot;
  }

  public void setSlot(LadderMeetupSlot slot) {
    this.slot = slot;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
