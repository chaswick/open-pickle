package com.w3llspring.fhpb.web.service.trophy;

import java.time.LocalDate;

public class TrophyGenerationRequest {

  private final String seasonName;
  private final LocalDate startDate;
  private final LocalDate endDate;
  private final int desiredCount;

  public TrophyGenerationRequest(
      String seasonName, LocalDate startDate, LocalDate endDate, int desiredCount) {
    this.seasonName = seasonName;
    this.startDate = startDate;
    this.endDate = endDate;
    this.desiredCount = desiredCount;
  }

  public String getSeasonName() {
    return seasonName;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public int getDesiredCount() {
    return desiredCount;
  }
}
