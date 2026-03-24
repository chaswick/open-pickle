package com.w3llspring.fhpb.web.service.scoring;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.Match;
import java.time.Duration;

public interface LadderScoringAlgorithm {

  LadderConfig.ScoringAlgorithm key();

  LadderScoringProfile profile();

  Duration historyWindow();

  int computeBaseStep(Match match);

  double computeGuestScale(Match match);

  LadderScoringResult score(LadderScoringRequest request);
}
