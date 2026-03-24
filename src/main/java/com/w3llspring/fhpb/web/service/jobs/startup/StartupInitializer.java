package com.w3llspring.fhpb.web.service.jobs.startup;

import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.service.user.UserIdentityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupInitializer {
  private static final Logger log = LoggerFactory.getLogger(StartupInitializer.class);
  private final UserIdentityService userIdentityService;

  public StartupInitializer(UserIdentityService userIdentityService) {
    this.userIdentityService = userIdentityService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ensurePositions() {
    try (BackgroundJobLogContext ignored =
        BackgroundJobLogContext.open("startup-identity-backfill")) {
      int assignedCodes = userIdentityService.backfillMissingPublicCodes();
      userIdentityService.logBackfillSummary(assignedCodes);
      log.debug(
          "Skipping global band position bootstrap; season-scoped rows are created on demand.");
    }
  }
}
