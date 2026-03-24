package com.w3llspring.fhpb.web.model;

import java.util.List;

public class RoundRobinStanding {

  private Long userId;
  private String nickName;
  private int wins;
  private int losses;
  private int gamesPlayed;
  private int pointsFor;
  private int pointsAgainst;
  private List<BadgeView> badgeViews = List.of();

  public RoundRobinStanding(Long userId, String nickName) {
    this.userId = userId;
    this.nickName = nickName;
  }

  public Long getUserId() {
    return userId;
  }

  public String getNickName() {
    return nickName;
  }

  public int getWins() {
    return wins;
  }

  public void incWins() {
    wins++;
  }

  public int getLosses() {
    return losses;
  }

  public void incLosses() {
    losses++;
  }

  public int getGamesPlayed() {
    return gamesPlayed;
  }

  public void incGamesPlayed() {
    gamesPlayed++;
  }

  public int getPointsFor() {
    return pointsFor;
  }

  public void addPointsFor(int p) {
    pointsFor += p;
  }

  public int getPointsAgainst() {
    return pointsAgainst;
  }

  public void addPointsAgainst(int p) {
    pointsAgainst += p;
  }

  public int getPoints() {
    // For round-robin/pickleball, use Points For as the points metric
    return pointsFor;
  }

  public int getPointDiff() {
    return pointsFor - pointsAgainst;
  }

  public List<BadgeView> getBadgeViews() {
    return badgeViews;
  }

  public void setBadgeViews(List<BadgeView> badgeViews) {
    this.badgeViews = badgeViews != null ? List.copyOf(badgeViews) : List.of();
  }
}
