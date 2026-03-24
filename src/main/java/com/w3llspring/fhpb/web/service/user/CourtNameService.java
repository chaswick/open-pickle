package com.w3llspring.fhpb.web.service.user;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Lookup helper for player names used on court. Aggregates profile nickname plus global or
 * ladder-scoped aliases.
 */
public interface CourtNameService {

  /**
   * Resolve court-friendly names for the provided users.
   *
   * @param userIds users to resolve (ignored when empty)
   * @param ladderConfigId optional ladder scope; null restricts to global names
   * @return map keyed by user id with ordered unique names
   */
  Map<Long, Set<String>> gatherCourtNamesForUsers(Collection<Long> userIds, Long ladderConfigId);

  /** Convenience shortcut for the single-user case. */
  Set<String> gatherCourtNamesForUser(long userId, Long ladderConfigId);
}
