package com.w3llspring.fhpb.web.service.meetups;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.service.push.PushNotificationService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MeetupCreatedNotificationsListener {

  private static final Logger log =
      LoggerFactory.getLogger(MeetupCreatedNotificationsListener.class);

  private final MeetupsEmailDigestService meetupsEmailDigests;
  private final LadderMembershipRepository membershipRepo;
  private final PushNotificationService pushNotifications;

  public MeetupCreatedNotificationsListener(
      MeetupsEmailDigestService meetupsEmailDigests,
      LadderMembershipRepository membershipRepo,
      PushNotificationService pushNotifications) {
    this.meetupsEmailDigests = meetupsEmailDigests;
    this.membershipRepo = membershipRepo;
    this.pushNotifications = pushNotifications;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMeetupCreated(MeetupCreatedEvent event) {
    if (event == null || event.ladderId() == null || event.creatorUserId() == null) {
      return;
    }

    Long ladderId = event.ladderId();
    Long creatorUserId = event.creatorUserId();

    // Email work: mark pending + possible immediate digest sends (cooldown rules inside service).
    try {
      meetupsEmailDigests.markPendingForLadderMembersExceptCreator(ladderId, creatorUserId);
    } catch (Exception ex) {
      log.debug(
          "[meetups] async markPending failed ladderId={} creatorUserId={}",
          ladderId,
          creatorUserId,
          ex);
    }

    // Push work: immediate push, independent of email cooldown.
    try {
      List<LadderMembership> members =
          membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              ladderId, LadderMembership.State.ACTIVE);
      if (members == null || members.isEmpty()) {
        return;
      }
      for (LadderMembership m : members) {
        if (m == null) continue;
        Long targetUserId = m.getUserId();
        if (targetUserId == null) continue;
        if (targetUserId.equals(creatorUserId)) continue;
        pushNotifications.sendNewPlayPlan(targetUserId, event.ladderTitle(), event.startsAt());
      }
    } catch (Exception ex) {
      log.debug(
          "[meetups] async push failed ladderId={} creatorUserId={}", ladderId, creatorUserId, ex);
    }
  }
}
