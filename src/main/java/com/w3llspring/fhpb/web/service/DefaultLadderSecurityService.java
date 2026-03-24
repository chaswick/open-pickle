package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.User;
import org.springframework.stereotype.Service;

@Service
public class DefaultLadderSecurityService implements LadderSecurityService {

  public DefaultLadderSecurityService() {
    // Per-user passphrase storage removed; no-op service.
  }

  @Override
  public boolean validateMatchLogging(LadderConfig ladder, User user, String passphrase) {
    // Per-user passphrase validation removed. Always allow match logging here;
    // ladder-level confirmation flows are handled elsewhere.
    return true;
  }

  @Override
  public String regeneratePassphrase(User user) {
    // No per-user passphrase to regenerate.
    return null;
  }
}
