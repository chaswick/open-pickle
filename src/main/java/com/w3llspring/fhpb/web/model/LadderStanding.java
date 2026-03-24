package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ladder_standing")
public class LadderStanding {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "season_id", nullable = false)
  private LadderSeason season;

  @ManyToOne(optional = true)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "points", nullable = false)
  private int points;

  // <-- rename the column to avoid MySQL reserved word
  @Column(name = "rank_no", nullable = false)
  private int rankNo;

  // --- getters/setters ---
  public Long getId() {
    return id;
  }

  public LadderSeason getSeason() {
    return season;
  }

  public void setSeason(LadderSeason season) {
    this.season = season;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public int getPoints() {
    return points;
  }

  public void setPoints(int points) {
    this.points = points;
  }

  // keep external API as "rank"
  public int getRank() {
    return rankNo;
  }

  public void setRank(int rank) {
    this.rankNo = rank;
  }
}
