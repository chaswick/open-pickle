package com.w3llspring.fhpb.web.service.user;

import java.util.Optional;

/** Central hook for checking display names against moderation rules. */
public interface DisplayNameModerationService {

  /**
   * Inspect the provided display name, returning a human-friendly explanation when it violates
   * moderation guidelines.
   *
   * @param displayName the candidate name (may be null)
   * @return empty when the name is acceptable; otherwise the rejection reason
   */
  Optional<String> explainViolation(String displayName);
}
