package com.w3llspring.fhpb.web.service;

import org.springframework.stereotype.Component;

/**
 * No-op replacement for the legacy per-user passphrase scheduler. The per-user passphrase feature
 * has been deprecated and this component intentionally performs no work to avoid generating DB
 * activity.
 */
@Component
public class PassphraseScheduler {
  public PassphraseScheduler() {
    // no-op
  }

  // Previously regenerated per-user passphrases; now returns null / no-op.
  public Object regenerateFor(Object user) {
    return null;
  }
}
