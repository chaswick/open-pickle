package com.w3llspring.fhpb.web.service.scoring;

import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.util.List;

interface MarginCurveVarietyCalculator {
  MarginCurveVarietySnapshot calculate(List<Match> recentMatches, User user);
}
