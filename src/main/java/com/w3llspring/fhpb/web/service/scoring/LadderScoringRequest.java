package com.w3llspring.fhpb.web.service.scoring;

import com.w3llspring.fhpb.web.model.BandPosition;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Match;
import java.util.List;
import java.util.Map;

public record LadderScoringRequest(
    Match match,
    Map<Long, LadderStanding> standingsByUser,
    Map<Long, BandPosition> bandByUser,
    Map<Integer, Integer> topQuartileLimit,
    int maxBandIndex,
    Long seasonId,
    Map<Long, Double> trustWeights,
    List<Match> history) {}
