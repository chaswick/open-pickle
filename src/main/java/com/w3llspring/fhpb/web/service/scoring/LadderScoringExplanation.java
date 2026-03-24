package com.w3llspring.fhpb.web.service.scoring;

import java.util.ArrayList;
import java.util.List;

public final class LadderScoringExplanation {
  private final List<String> steps;

  public LadderScoringExplanation(List<String> steps) {
    this.steps = steps == null ? List.of() : List.copyOf(steps);
  }

  public static LadderScoringExplanation empty() {
    return new LadderScoringExplanation(List.of());
  }

  public List<String> getSteps() {
    return steps;
  }

  public LadderScoringExplanation appendStep(String step) {
    if (step == null || step.isBlank()) {
      return this;
    }
    List<String> updated = new ArrayList<>(steps);
    updated.add(step);
    return new LadderScoringExplanation(updated);
  }
}
