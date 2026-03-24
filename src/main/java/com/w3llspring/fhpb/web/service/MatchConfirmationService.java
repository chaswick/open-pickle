package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import java.util.List;

public interface MatchConfirmationService {
  void createRequests(Match match);

  /** Returns true if this is the first losing-side MANUAL confirmation for the match */
  boolean confirmMatch(long matchId, long userId);

  default boolean confirmMatch(long matchId, long userId, Long expectedVersion) {
    return confirmMatch(matchId, userId);
  }

  default void disputeMatch(long matchId, long userId, String note) {
    throw new UnsupportedOperationException(
        "Match disputes are not supported by this implementation.");
  }

  default void disputeMatch(long matchId, long userId, String note, Long expectedVersion) {
    disputeMatch(matchId, userId, note);
  }

  List<MatchConfirmation> pendingForUser(long userId);

  void autoConfirmOverdue();

  /**
   * Rebuild pending confirmation requests for a specific match. Deletes existing pending rows for
   * the match and re-creates them based on the current match state (editor overrides, logger rules,
   * etc.).
   */
  void rebuildConfirmationRequests(Match match);
}
