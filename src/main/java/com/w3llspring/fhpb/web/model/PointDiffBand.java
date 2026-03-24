package com.w3llspring.fhpb.web.model;

public enum PointDiffBand {
  ONE,
  TWO_TO_THREE,
  FOUR_PLUS;

  public int baseStep() {
    switch (this) {
      case ONE:
        return 1;
      case TWO_TO_THREE:
        return 2;
      case FOUR_PLUS:
        return 3;
      default:
        return 1;
    }
  }
}
