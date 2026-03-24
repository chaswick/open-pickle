package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.NameCorrection;
import java.time.Instant;

public interface NameCorrectionRepositoryCustom {

  /**
   * Atomically increment (or create) a NameCorrection row for the given token/ladder/user. Returns
   * the persisted entity after increment. This method takes a pessimistic lock when reading an
   * existing row to avoid lost updates.
   */
  NameCorrection incrementCorrectionCount(
      Long ladderConfigId,
      String tokenNormalized,
      String phoneticKey,
      Long userId,
      Long reporterUserId,
      int delta,
      Instant lastConfirmedAt);
}
