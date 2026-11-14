package com.w3llspring.fhpb.web.service.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.SessionJoinRequestRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.SessionJoinRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionCapacityService {
  private final LadderConfigRepository configs;
  private final LadderMembershipRepository memberships;
  private final SessionJoinRequestRepository requests;
  private final EntityManager entities;

  public SessionCapacityService(
      LadderConfigRepository configs,
      LadderMembershipRepository memberships,
      SessionJoinRequestRepository requests,
      EntityManager entities) {
    this.configs = configs;
    this.memberships = memberships;
    this.requests = requests;
    this.entities = entities;
  }

  /** Reload values that may have been read before waiting for the session lock. */
  public void refreshLockedSession(LadderConfig session) {
    entities.refresh(session, LockModeType.PESSIMISTIC_WRITE);
  }

  public void refreshLockedRequest(SessionJoinRequest request) {
    entities.refresh(request, LockModeType.PESSIMISTIC_WRITE);
  }

  /** The caller holds the session row lock, shared by all roster mutations. */
  public void admitOrWaitlist(SessionJoinRequest request, Long approvedBy) {
    request.setStatus(SessionJoinRequest.Status.WAITLISTED);
    request.setResolvedAt(Instant.now());
    request.setResolvedByUserId(approvedBy);
    // Approval lasts for this session, independently of the short invite/request lifetime.
    request.setExpiresAt(
        request.getLadderConfig().getExpiresAt() != null
            ? request.getLadderConfig().getExpiresAt()
            : Instant.parse("9999-12-31T00:00:00Z"));
    requests.saveAndFlush(request);
    fillVacancies(request.getLadderConfig());
  }

  /** Fill seats in the same transaction as a departure or capacity change. */
  public void fillVacancies(LadderConfig session) {
    if (!session.isSessionType()
        || session.getStatus() != LadderConfig.Status.ACTIVE
        || (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(Instant.now())))
      return;
    int available =
        session.getSessionMemberLimit()
            - (int)
                memberships.lockActiveMembers(session.getId()).stream()
                    .filter(member -> session.isSessionPlayer(member.getUserId()))
                    .count();
    if (available <= 0) return;
    for (SessionJoinRequest request :
        requests.findByLadderConfigIdAndStatusOrderByResolvedAtAscIdAsc(
            session.getId(), SessionJoinRequest.Status.WAITLISTED)) {
      LadderMembership member =
          memberships.lockMember(session.getId(), request.getRequesterUserId()).orElse(null);
      if (member != null) entities.refresh(member, LockModeType.PESSIMISTIC_WRITE);
      if (member != null && member.getState() == LadderMembership.State.BANNED) {
        request.setStatus(SessionJoinRequest.Status.DENIED);
      } else {
        boolean alreadyActive =
            member != null && member.getState() == LadderMembership.State.ACTIVE;
        if (!alreadyActive) {
          if (member == null) {
            member = new LadderMembership();
            member.setLadderConfig(session);
            member.setUserId(request.getRequesterUserId());
          }
          member.setState(LadderMembership.State.ACTIVE);
          member.setJoinedAt(Instant.now());
          member.setLeftAt(null);
          memberships.save(member);
          available--;
        }
        request.setStatus(SessionJoinRequest.Status.APPROVED);
      }
      request.setResolvedAt(Instant.now());
      requests.save(request);
      if (available == 0) break;
    }
  }

  @Transactional
  public void updateLimit(Long sessionId, Long hostUserId, int limit) {
    LadderConfig session = configs.lockById(sessionId);
    if (session == null || !session.isSessionType())
      throw new IllegalArgumentException("Session not found");
    refreshLockedSession(session);
    if (!java.util.Objects.equals(session.getOwnerUserId(), hostUserId))
      throw new SecurityException("Only the host can change the player limit.");
    if (session.getStatus() != LadderConfig.Status.ACTIVE
        || (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(Instant.now())))
      throw new IllegalStateException("This session has ended.");
    long joinedPlayers =
        memberships.lockActiveMembers(sessionId).stream()
            .filter(member -> session.isSessionPlayer(member.getUserId()))
            .count();
    if (limit < joinedPlayers) {
      throw new IllegalArgumentException(
          "The player limit cannot be lower than the "
              + joinedPlayers
              + " players already joined.");
    }
    session.setSessionMemberLimit(limit);
    configs.save(session);
    fillVacancies(session);
  }
}
