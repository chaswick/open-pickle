package com.w3llspring.fhpb.web.service.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.PlayLocationCheckInRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.PlayLocationCheckIn;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.util.SessionInviteCodeSupport;
import com.w3llspring.fhpb.web.util.UserPublicName;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SessionNearbyShareService {

  public record NearbyShareStatusView(
      Long sessionId, Long locationId, String locationName, boolean sharingActive, String message) {}

  public record NearbySessionsView(String locationName, List<NearbySessionView> sessions) {}

  public record NearbySessionView(
      Long sessionId,
      String sessionTitle,
      String ownerDisplayName,
      String locationName,
      long activeMemberCount) {}

  private final LadderConfigRepository configs;
  private final LadderMembershipRepository memberships;
  private final PlayLocationCheckInRepository checkIns;
  private final UserRepository userRepo;
  private final GroupAdministrationService groupAdministration;

  @Value("${fhpb.sessions.invite-active-seconds:1800}")
  private long sessionInviteActiveSeconds = 1800L;

  public SessionNearbyShareService(
      LadderConfigRepository configs,
      LadderMembershipRepository memberships,
      PlayLocationCheckInRepository checkIns,
      UserRepository userRepo,
      GroupAdministrationService groupAdministration) {
    this.configs = configs;
    this.memberships = memberships;
    this.checkIns = checkIns;
    this.userRepo = userRepo;
    this.groupAdministration = groupAdministration;
  }

  @Transactional
  public NearbyShareStatusView enableUsingActiveCheckIn(Long sessionId, Long adminUserId) {
    Instant now = Instant.now();
    LadderConfig session = lockSession(sessionId);
    requireActiveSession(session, now);
    groupAdministration.requireAdmin(session, adminUserId);

    PlayLocationCheckIn activeCheckIn = requireActiveCheckIn(adminUserId, now);
    groupAdministration.syncInviteAvailability(session, adminUserId, true);
    session.setNearbyShareLocationId(activeCheckIn.getLocation().getId());
    String locationName = resolveOptionalLocationName(activeCheckIn);
    session.setNearbyShareLocationName(locationName);
    LadderConfig saved = configs.save(session);
    return new NearbyShareStatusView(
        saved.getId(),
        saved.getNearbyShareLocationId(),
        saved.getNearbyShareLocationName(),
        hasActiveNearbySharing(saved, now),
        StringUtils.hasText(saved.getNearbyShareLocationName())
            ? "Nearby sharing is on for " + saved.getNearbyShareLocationName() + "."
            : "Nearby sharing is on for your current court.");
  }

  @Transactional(readOnly = true)
  public NearbySessionsView listForRequesterFromActiveCheckIn(Long requesterUserId) {
    Instant now = Instant.now();
    PlayLocationCheckIn activeCheckIn = requireActiveCheckIn(requesterUserId, now);
    Long locationId = activeCheckIn.getLocation() != null ? activeCheckIn.getLocation().getId() : null;
    if (locationId == null) {
      throw new IllegalStateException("Check in first to look for nearby sessions.");
    }

    List<LadderConfig> sessions =
        configs
            .findByTypeAndStatusAndNearbyShareLocationIdAndInviteCodeIsNotNullAndExpiresAtAfterOrderByUpdatedAtDesc(
                LadderConfig.Type.SESSION, LadderConfig.Status.ACTIVE, locationId, now)
            .stream()
            .filter(session -> hasActiveNearbySharing(session, now))
            .toList();

    Map<Long, User> owners =
        userRepo.findAllById(
                sessions.stream()
                    .map(LadderConfig::getOwnerUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(User::getId, user -> user));

    List<NearbySessionView> views =
        sessions.stream()
            .map(
                session ->
                    new NearbySessionView(
                        session.getId(),
                        session.getTitle(),
                        UserPublicName.forUser(owners.get(session.getOwnerUserId())),
                        resolveLocationName(session, activeCheckIn),
                        memberships
                            .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                                session.getId(), LadderMembership.State.ACTIVE)
                            .size()))
            .toList();

    return new NearbySessionsView(resolveOptionalLocationName(activeCheckIn), views);
  }

  @Transactional(readOnly = true)
  public void requireSessionVisibleToRequester(Long sessionId, Long requesterUserId) {
    Instant now = Instant.now();
    PlayLocationCheckIn activeCheckIn = requireActiveCheckIn(requesterUserId, now);
    Long requesterLocationId =
        activeCheckIn.getLocation() != null ? activeCheckIn.getLocation().getId() : null;
    if (requesterLocationId == null) {
      throw new IllegalStateException("Check in first to look for nearby sessions.");
    }

    LadderConfig session =
        configs
            .findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    if (!session.isSessionType()) {
      throw new IllegalArgumentException("Session not found");
    }
    if (!hasActiveNearbySharing(session, now)) {
      throw new IllegalStateException("This session is not discoverable nearby right now.");
    }
    if (!Objects.equals(session.getNearbyShareLocationId(), requesterLocationId)) {
      throw new IllegalStateException("This session is not discoverable from your current check-in.");
    }
  }

  public boolean hasActiveNearbySharing(LadderConfig session, Instant now) {
    if (session == null
        || !session.isSessionType()
        || session.getNearbyShareLocationId() == null
        || session.getStatus() != LadderConfig.Status.ACTIVE
        || session.getExpiresAt() == null
        || !session.getExpiresAt().isAfter(now)) {
      return false;
    }
    return SessionInviteCodeSupport.isCurrentlyActive(
        session.getInviteCode(),
        session.getLastInviteChangeAt(),
        sessionInviteActiveSeconds,
        now != null ? now : Instant.now());
  }

  private LadderConfig lockSession(Long sessionId) {
    LadderConfig session = configs.lockById(sessionId);
    if (session == null || !session.isSessionType()) {
      throw new IllegalArgumentException("Session not found");
    }
    return session;
  }

  private void requireActiveSession(LadderConfig session, Instant now) {
    if (session.getStatus() != LadderConfig.Status.ACTIVE) {
      throw new IllegalStateException("This match session is no longer active.");
    }
    if (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(now)) {
      session.setStatus(LadderConfig.Status.ARCHIVED);
      session.setInviteCode(null);
      configs.save(session);
      throw new IllegalStateException("This match session has expired.");
    }
  }

  private PlayLocationCheckIn requireActiveCheckIn(Long userId, Instant now) {
    return checkIns
        .findActiveWithLocationByUserId(userId, now)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Check in first to use nearby session sharing."));
  }

  private String resolveOptionalLocationName(PlayLocationCheckIn activeCheckIn) {
    if (activeCheckIn != null && StringUtils.hasText(activeCheckIn.getDisplayName())) {
      return activeCheckIn.getDisplayName().trim();
    }
    return null;
  }

  private String resolveLocationName(LadderConfig session, PlayLocationCheckIn fallbackCheckIn) {
    if (session != null && StringUtils.hasText(session.getNearbyShareLocationName())) {
      return session.getNearbyShareLocationName().trim();
    }
    String fallbackName = resolveOptionalLocationName(fallbackCheckIn);
    return StringUtils.hasText(fallbackName) ? fallbackName : null;
  }
}
