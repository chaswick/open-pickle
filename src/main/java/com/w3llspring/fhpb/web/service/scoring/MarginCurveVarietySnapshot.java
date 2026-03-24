package com.w3llspring.fhpb.web.service.scoring;

record MarginCurveVarietySnapshot(
    int sampleSize,
    int uniqueSlots,
    int repeatFloor,
    int totalSeatOpportunities,
    int fullBonusTarget,
    int comparableMatchCount,
    double multiplier) {
  static MarginCurveVarietySnapshot neutral() {
    return new MarginCurveVarietySnapshot(0, 0, 0, 0, 0, 0, 1.0d);
  }
}
