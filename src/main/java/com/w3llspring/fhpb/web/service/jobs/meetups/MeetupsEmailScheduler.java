package com.w3llspring.fhpb.web.service.jobs.meetups;

import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.service.meetups.MeetupsEmailDigestService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MeetupsEmailScheduler {

  private final MeetupsEmailDigestService digests;

  public MeetupsEmailScheduler(MeetupsEmailDigestService digests) {
    this.digests = digests;
  }

  @Scheduled(cron = "0 */10 * * * *")
  public void sendPending() {
    try (BackgroundJobLogContext ignored = BackgroundJobLogContext.open("meetups-email-digest")) {
      digests.trySendPendingDigests();
    }
  }
}
