package com.w3llspring.fhpb.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "ladder_rating_change",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"match_id", "user_id"})})
public class LadderRatingChange {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "season_id", nullable = false)
  private LadderSeason season;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "match_id", nullable = false)
  private Match match;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();

  @Column(name = "rating_before", nullable = false)
  private int ratingBefore;

  @Column(name = "rating_delta", nullable = false)
  private int ratingDelta;

  @Column(name = "rating_after", nullable = false)
  private int ratingAfter;

  @Column(name = "summary", nullable = false, length = 255)
  private String summary = "";

  @Column(name = "details", columnDefinition = "TEXT")
  private String details;

  public Long getId() {
    return id;
  }

  public LadderSeason getSeason() {
    return season;
  }

  public void setSeason(LadderSeason season) {
    this.season = season;
  }

  public Match getMatch() {
    return match;
  }

  public void setMatch(Match match) {
    this.match = match;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public int getRatingBefore() {
    return ratingBefore;
  }

  public void setRatingBefore(int ratingBefore) {
    this.ratingBefore = ratingBefore;
  }

  public int getRatingDelta() {
    return ratingDelta;
  }

  public void setRatingDelta(int ratingDelta) {
    this.ratingDelta = ratingDelta;
  }

  public int getRatingAfter() {
    return ratingAfter;
  }

  public void setRatingAfter(int ratingAfter) {
    this.ratingAfter = ratingAfter;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }
}
