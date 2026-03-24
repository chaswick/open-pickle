package com.w3llspring.fhpb.web.service.roundrobin;

import java.util.List;

/**
 * Strategy interface for generating round-robin schedules from a list of participant ids.
 * Implementations should return a list of rounds, where each round is a list of MatchSpec.
 */
public interface RoundRobinScheduler {

  class MatchSpec {
    public Long a1;
    public Long a2;
    public Long b1;
    public Long b2;
    public boolean bye;

    public MatchSpec() {}

    public MatchSpec(Long a1, Long a2, Long b1, Long b2, boolean bye) {
      this.a1 = a1;
      this.a2 = a2;
      this.b1 = b1;
      this.b2 = b2;
      this.bye = bye;
    }
  }

  /**
   * Generate schedule rounds from participant ids. Each inner list is one round.
   *
   * @param participantIds list of participant user ids
   * @param priorPairCounts map of historical pair counts (participantId -> otherParticipantId ->
   *     count) implementations may treat this as optional and treat missing entries as zero.
   * @param rounds number of rounds to generate; if &lt;= 0 implementations may choose a reasonable
   *     default
   */
  default List<List<MatchSpec>> generate(
      List<Long> participantIds,
      java.util.Map<Long, java.util.Map<Long, Integer>> priorPairCounts,
      int rounds) {
    return generateWithLog(participantIds, priorPairCounts, rounds, null);
  }

  default GenerationResult generateWithExplanation(
      List<Long> participantIds,
      java.util.Map<Long, java.util.Map<Long, Integer>> priorPairCounts,
      int rounds) {
    GenerationResult result = new GenerationResult();
    result.schedule = generateWithLog(participantIds, priorPairCounts, rounds, result.explanations);
    return result;
  }

  List<List<MatchSpec>> generateWithLog(
      List<Long> participantIds,
      java.util.Map<Long, java.util.Map<Long, Integer>> priorPairCounts,
      int rounds,
      java.util.List<String> explanationLog);

  class GenerationResult {
    public List<List<MatchSpec>> schedule;
    public List<String> explanations = new java.util.ArrayList<>();
  }
}
