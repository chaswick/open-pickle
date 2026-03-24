package com.w3llspring.fhpb.web.service.trophy;

import java.time.LocalDate;
import java.util.List;

public class TrophyCatalog {

  private final Long seasonId;
  private final String seasonName;
  private final String ladderConfigTitle;
  private final LocalDate seasonStart;
  private final LocalDate seasonEnd;
  private final List<TrophyCardModel> cards;
  private final boolean currentSeason;
  private final Integer finalRank;
  private final String finalDivision;
  private final Integer finalRating;

  public TrophyCatalog(
      Long seasonId,
      String seasonName,
      String ladderConfigTitle,
      LocalDate seasonStart,
      LocalDate seasonEnd,
      List<TrophyCardModel> cards,
      boolean currentSeason,
      Integer finalRank,
      String finalDivision,
      Integer finalRating) {
    this.seasonId = seasonId;
    this.seasonName = seasonName;
    this.ladderConfigTitle = ladderConfigTitle;
    this.seasonStart = seasonStart;
    this.seasonEnd = seasonEnd;
    this.cards = cards;
    this.currentSeason = currentSeason;
    this.finalRank = finalRank;
    this.finalDivision = finalDivision;
    this.finalRating = finalRating;
  }

  public Long getSeasonId() {
    return seasonId;
  }

  public String getSeasonName() {
    return seasonName;
  }

  public String getLadderConfigTitle() {
    return ladderConfigTitle;
  }

  public LocalDate getSeasonStart() {
    return seasonStart;
  }

  public LocalDate getSeasonEnd() {
    return seasonEnd;
  }

  public List<TrophyCardModel> getCards() {
    return cards;
  }

  public boolean isCurrentSeason() {
    return currentSeason;
  }

  public Integer getFinalRank() {
    return finalRank;
  }

  public String getFinalDivision() {
    return finalDivision;
  }

  public Integer getFinalRating() {
    return finalRating;
  }
}
