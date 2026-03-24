package com.w3llspring.fhpb.web.service.meetups;

import com.w3llspring.fhpb.web.db.LadderMeetupRsvpRepository;
import com.w3llspring.fhpb.web.model.LadderMeetupRsvp;
import com.w3llspring.fhpb.web.service.push.PushNotificationService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MeetupCanceledNotificationsListener {

  private static final Logger log =
      LoggerFactory.getLogger(MeetupCanceledNotificationsListener.class);

  private final LadderMeetupRsvpRepository rsvpRepo;
  private final PushNotificationService pushNotifications;

  public MeetupCanceledNotificationsListener(
      LadderMeetupRsvpRepository rsvpRepo, PushNotificationService pushNotifications) {
    this.rsvpRepo = rsvpRepo;
    this.pushNotifications = pushNotifications;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMeetupCanceled(MeetupCanceledEvent event) {
    if (event == null || event.slotId() == null || event.cancelerUserId() == null) {
      return;
    }

    try {
      List<Long> userIds =
          rsvpRepo.findUserIdsBySlotIdAndStatusIn(
              event.slotId(), List.of(LadderMeetupRsvp.Status.IN, LadderMeetupRsvp.Status.MAYBE));
      if (userIds == null || userIds.isEmpty()) {
        return;
      }

      for (Long targetUserId : userIds) {
        if (targetUserId == null) continue;
        if (targetUserId.equals(event.cancelerUserId())) continue;
        pushNotifications.sendPlayPlanCanceled(targetUserId, event.ladderTitle(), event.startsAt());
      }
    } catch (Exception ex) {
      log.debug(
          "[meetups] async cancel push failed slotId={} cancelerUserId={}",
          event.slotId(),
          event.cancelerUserId(),
          ex);
    }
  }
}
