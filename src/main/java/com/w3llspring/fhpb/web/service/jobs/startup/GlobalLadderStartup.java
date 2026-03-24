package com.w3llspring.fhpb.web.service.jobs.startup;

import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.service.GlobalLadderBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class GlobalLadderStartup {

  private static final Logger log = LoggerFactory.getLogger(GlobalLadderStartup.class);

  private final GlobalLadderBootstrapService bootstrapService;

  public GlobalLadderStartup(GlobalLadderBootstrapService bootstrapService) {
    this.bootstrapService = bootstrapService;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(200)
  public void onReady() {
    try (BackgroundJobLogContext ignored =
        BackgroundJobLogContext.open("startup-global-ladder-bootstrap")) {
      if (!bootstrapService.isEnabled()) {
        log.info("Global ladder bootstrap disabled; skipping initialization.");
        return;
      }
      log.info("Global ladder bootstrap enabled; ensuring configuration is in place.");
      bootstrapService.initializeIfNeeded();
    }
  }
}
