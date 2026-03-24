package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;

@Entity
@Table(
    name = "band_positions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_band_positions_season_user",
          columnNames = {"season_id", "user_id"})
    })
public class BandPosition {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "season_id", nullable = false)
  private LadderSeason season;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  private Integer bandIndex = 1;
  private Integer positionInBand = 6;
  private Integer dailyMomentum = 0;

  public Long getId() {
    return id;
  }

  public LadderSeason getSeason() {
    return season;
  }

  public void setSeason(LadderSeason s) {
    this.season = s;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User u) {
    this.user = u;
  }

  public Integer getBandIndex() {
    return bandIndex;
  }

  public void setBandIndex(Integer i) {
    this.bandIndex = i;
  }

  public Integer getPositionInBand() {
    return positionInBand;
  }

  public void setPositionInBand(Integer p) {
    this.positionInBand = p;
  }

  public Integer getDailyMomentum() {
    return dailyMomentum;
  }

  public void setDailyMomentum(Integer d) {
    this.dailyMomentum = d;
  }
}
