package com.w3llspring.fhpb.web.service.meetups;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMeetupRsvpRepository;
import com.w3llspring.fhpb.web.db.LadderMeetupSlotRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.email.EmailService;
import com.w3llspring.fhpb.web.service.push.PushNotificationService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class MeetupsEmailDigestService {

  private static final Logger log = LoggerFactory.getLogger(MeetupsEmailDigestService.class);

  // If a pending plan would start before the cooldown ends, send a consolidated reminder shortly
  // before it starts.
  private static final Duration IMMINENT_SEND_LEAD_TIME = Duration.ofMinutes(30);

  private final Clock clock;
  private final UserRepository userRepo;
  private final LadderMembershipRepository membershipRepo;

  @Autowired private MeetupsEmailDigestUserProcessor userProcessor;

  @Value("${fhpb.features.meetups.enabled:false}")
  private boolean meetupsEnabled;

  public record DebugSendResult(
      boolean meetupsEnabled,
      int pendingUsersFound,
      int usersSent,
      int usersErrored,
      Instant ranAt) {}

  public MeetupsEmailDigestService(
      UserRepository userRepo,
      LadderMembershipRepository membershipRepo,
      LadderMeetupSlotRepository slotRepo,
      LadderMeetupRsvpRepository rsvpRepo,
      LadderConfigRepository ladderConfigRepo,
      EmailService emailService,
      PushNotificationService pushNotificationService,
      MeetupsEmailConfig config,
      MeetupsEmailLinkSigner linkSigner) {
    this.clock = Clock.systemUTC();
    this.userRepo = userRepo;
    this.membershipRepo = membershipRepo;
  }

  public boolean isCurrentUserAdmin() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    User currentUser = AuthenticatedUserSupport.currentUser(authentication);
    return currentUser != null && currentUser.isAdmin();
  }

  public DebugSendResult debugTrySendPending() {
    Instant now = Instant.now(clock);
    List<Long> pendingUserIds = userRepo.findUserIdsWithPendingMeetupsDigest();
    int sent = 0;
    int errored = 0;

    if (!meetupsEnabled) {
      log.warn("[meetups-email] debug send invoked but feature disabled");
      return new DebugSendResult(
          false, pendingUserIds == null ? 0 : pendingUserIds.size(), 0, 0, now);
    }

    if (pendingUserIds != null) {
      for (Long userId : pendingUserIds) {
        if (userId == null) {
          continue;
        }
        try {
          userProcessor.trySendPendingDigest(userId);
          User refreshed = userRepo.findById(userId).orElse(null);
          if (refreshed != null && !refreshed.isMeetupsEmailPending()) {
            sent++;
          }
        } catch (Exception ex) {
          errored++;
          log.warn("[meetups-email] debug send error userId={}", userId, ex);
        }
      }
    }

    return new DebugSendResult(
        true, pendingUserIds == null ? 0 : pendingUserIds.size(), sent, errored, now);
  }

  public void recordOptIn(Long userId, boolean optIn) {
    userProcessor.recordOptIn(userId, optIn);
  }

  public void markPendingForLadderMembersExceptCreator(Long ladderId, Long creatorUserId) {
    if (!meetupsEnabled) {
      log.debug(
          "[meetups-email] skipped (feature disabled) ladderId={} creatorUserId={}",
          ladderId,
          creatorUserId);
      return;
    }

    List<LadderMembership> members =
        membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            ladderId, LadderMembership.State.ACTIVE);
    if (members.isEmpty()) {
      log.debug("[meetups-email] no active members ladderId={}", ladderId);
      return;
    }

    List<Long> userIds =
        members.stream()
            .map(LadderMembership::getUserId)
            .filter(id -> id != null && !id.equals(creatorUserId))
            .distinct()
            .toList();
    if (userIds.isEmpty()) {
      return;
    }

    int eligibleOptedIn = 0;
    int eligibleWithEmail = 0;
    int markedPending = 0;
    List<Long> immediateCandidates = new ArrayList<>();

    for (Long userId : userIds) {
      MeetupsEmailDigestUserProcessor.MarkPendingResult result =
          userProcessor.markPendingIfOptedIn(userId);
      if (!result.optedIn()) {
        continue;
      }
      eligibleOptedIn++;
      if (result.hasEmail()) {
        eligibleWithEmail++;
      }
      markedPending++;
      immediateCandidates.add(userId);
    }

    log.debug(
        "[meetups-email] marked pending ladderId={} creatorUserId={} members={} targets={} optedIn={} withEmail={} pending={} anyImmediate={}",
        ladderId,
        creatorUserId,
        members.size(),
        userIds.size(),
        eligibleOptedIn,
        eligibleWithEmail,
        markedPending,
        !immediateCandidates.isEmpty());

    for (Long userId : immediateCandidates) {
      userProcessor.trySendDigestIfAllowedNow(userId);
    }
  }

  public void trySendPendingDigests() {
    if (!meetupsEnabled) {
      log.debug("[meetups-email] scheduler skipped (feature disabled)");
      return;
    }

    List<Long> pendingUserIds = userRepo.findUserIdsWithPendingMeetupsDigest();
    log.debug(
        "[meetups-email] scheduler tick pendingUsers={}",
        pendingUserIds == null ? 0 : pendingUserIds.size());
    for (Long userId : pendingUserIds) {
      if (userId == null) {
        continue;
      }
      userProcessor.trySendPendingDigest(userId);
    }
  }

  static boolean shouldSendDuringCooldown(
      Instant now, Instant cooldownEndsAt, Instant earliestStartsAt) {
    if (now == null || cooldownEndsAt == null || earliestStartsAt == null) {
      return false;
    }
    if (!earliestStartsAt.isBefore(cooldownEndsAt)) {
      return false;
    }
    Instant reminderTime = earliestStartsAt.minus(IMMINENT_SEND_LEAD_TIME);
    return !now.isBefore(reminderTime);
  }
}
