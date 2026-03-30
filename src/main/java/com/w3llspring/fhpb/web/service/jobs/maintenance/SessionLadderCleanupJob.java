package com.w3llspring.fhpb.web.service.jobs.maintenance;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.service.competition.SessionLifecycleService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionLadderCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(SessionLadderCleanupJob.class);

  private final LadderConfigRepository ladderConfigRepository;
  private final SessionLifecycleService sessionLifecycleService;

  public SessionLadderCleanupJob(
      LadderConfigRepository ladderConfigRepository,
      SessionLifecycleService sessionLifecycleService) {
    this.ladderConfigRepository = ladderConfigRepository;
    this.sessionLifecycleService = sessionLifecycleService;
  }

  @Scheduled(cron = "${fhpb.session.cleanup.cron:0 */30 * * * *}")
  @Transactional
  public void archiveExpiredSessions() {
    try (BackgroundJobLogContext ignored = BackgroundJobLogContext.open("session-ladder-cleanup")) {
      Instant now = Instant.now();
      List<LadderConfig> expiredSessions =
          ladderConfigRepository.findByTypeAndStatusAndExpiresAtBefore(
              LadderConfig.Type.SESSION, LadderConfig.Status.ACTIVE, now);
      if (expiredSessions.isEmpty()) {
        return;
      }

      for (LadderConfig session : expiredSessions) {
        if (session == null || session.getId() == null) {
          continue;
        }
        if (sessionLifecycleService.archiveSession(session.getId(), now)) {
          log.info("Archived expired match session {}", session.getId());
        }
      }
    }
  }
}
