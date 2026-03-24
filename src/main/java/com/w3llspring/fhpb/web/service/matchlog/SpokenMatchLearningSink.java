package com.w3llspring.fhpb.web.service.matchlog;

/**
 * Strategy hook for persisting or reacting to troublesome spoken-match transcripts so the
 * interpreter can improve over time (e.g., capturing custom aliases, analytics, or training data).
 */
public interface SpokenMatchLearningSink {

  /**
   * Records a learning opportunity. Implementations may persist, enqueue, or otherwise handle the
   * sample.
   */
  void record(SpokenMatchLearningSample sample);

  /**
   * Optional pre-check so implementations can skip heavy work if nothing should be recorded (e.g.,
   * disabled feature flags or storage limits). The default is to always record.
   */
  default boolean supportsSample(SpokenMatchLearningSample sample) {
    return true;
  }
}
