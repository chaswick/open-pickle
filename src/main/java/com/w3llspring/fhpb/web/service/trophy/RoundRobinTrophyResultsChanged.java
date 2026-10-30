package com.w3llspring.fhpb.web.service.trophy;

/** Deferred until commit so assignment links and the final result are visible together. */
public record RoundRobinTrophyResultsChanged(Long matchId, Long runId, Long deletedSeasonId) {}
