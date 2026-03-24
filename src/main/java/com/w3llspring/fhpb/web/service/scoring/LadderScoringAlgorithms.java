package com.w3llspring.fhpb.web.service.scoring;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class LadderScoringAlgorithms {
  private final Map<LadderConfig.ScoringAlgorithm, LadderScoringAlgorithm> algorithms;
  private final LadderScoringAlgorithm defaultAlgorithm;

  public LadderScoringAlgorithms(List<LadderScoringAlgorithm> algorithms) {
    EnumMap<LadderConfig.ScoringAlgorithm, LadderScoringAlgorithm> mapped =
        new EnumMap<>(LadderConfig.ScoringAlgorithm.class);
    for (LadderScoringAlgorithm algorithm : algorithms) {
      LadderScoringAlgorithm previous = mapped.put(algorithm.key(), algorithm);
      if (previous != null) {
        throw new IllegalStateException("Duplicate ladder scoring algorithm: " + algorithm.key());
      }
    }
    this.algorithms = Collections.unmodifiableMap(mapped);
    this.defaultAlgorithm =
        Objects.requireNonNull(
            mapped.get(LadderConfig.ScoringAlgorithm.MARGIN_CURVE_V1),
            "MARGIN_CURVE_V1 scoring algorithm must be registered");
  }

  public LadderScoringAlgorithm resolve(LadderSeason season) {
    return resolve(season != null ? season.getLadderConfig() : null);
  }

  public LadderScoringAlgorithm resolve(LadderConfig config) {
    if (config == null || config.getScoringAlgorithm() == null) {
      return defaultAlgorithm;
    }
    return algorithms.getOrDefault(config.getScoringAlgorithm(), defaultAlgorithm);
  }

  public LadderScoringAlgorithm defaultAlgorithm() {
    return defaultAlgorithm;
  }
}
