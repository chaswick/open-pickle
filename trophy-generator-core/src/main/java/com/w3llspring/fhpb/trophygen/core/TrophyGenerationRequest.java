package com.w3llspring.fhpb.trophygen.core;

import java.time.LocalDate;

public class TrophyGenerationRequest {
  private final String seasonName;
  private final LocalDate seasonStart;
  private final LocalDate seasonEnd;
  private final int desiredCount;

  public TrophyGenerationRequest(
      String seasonName, LocalDate seasonStart, LocalDate seasonEnd, int desiredCount) {
    this.seasonName = seasonName;
    this.seasonStart = seasonStart;
    this.seasonEnd = seasonEnd;
    this.desiredCount = desiredCount;
  }

  public String getSeasonName() {
    return seasonName;
  }

  public LocalDate getSeasonStart() {
    return seasonStart;
  }

  public LocalDate getSeasonEnd() {
    return seasonEnd;
  }

  public int getDesiredCount() {
    return desiredCount;
  }
}
