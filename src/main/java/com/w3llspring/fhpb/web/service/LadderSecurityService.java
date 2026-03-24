package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.User;

/**
 * Legacy service interface retained for compatibility. Per-user passphrase enforcement is
 * deprecated and no longer blocks match logging.
 */
public interface LadderSecurityService {

  /** Always permissive in current architecture; confirmation rules are enforced elsewhere. */
  boolean validateMatchLogging(LadderConfig ladder, User user, String passphrase);

  /** Passphrase gating is disabled. Returns false for all ladders. */
  default boolean isPassphraseRequired(LadderConfig ladder) {
    return false;
  }

  /** Deprecated no-op in current architecture. */
  String regeneratePassphrase(User user);
}
