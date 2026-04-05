package com.w3llspring.fhpb.web.service.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionLifecycleService {

  private final LadderConfigRepository configs;
  private final LadderMembershipRepository memberships;
  private final RoundRobinService roundRobinService;

  public SessionLifecycleService(
      LadderConfigRepository configs,
      LadderMembershipRepository memberships,
      RoundRobinService roundRobinService) {
    this.configs = configs;
    this.memberships = memberships;
    this.roundRobinService = roundRobinService;
  }

  @Transactional
  public boolean archiveSession(Long sessionId, Instant archivedAt) {
    LadderConfig session = configs.lockById(sessionId);
    if (session == null) {
      throw new IllegalArgumentException("Session not found");
    }
    if (!session.isSessionType()) {
      throw new IllegalStateException("Only match sessions can be archived this way.");
    }
    if (session.getStatus() == LadderConfig.Status.ARCHIVED) {
      return false;
    }

    Instant effectiveArchivedAt = archivedAt != null ? archivedAt : Instant.now();
    session.setStatus(LadderConfig.Status.ARCHIVED);
    session.setInviteCode(null);
    configs.save(session);

    List<LadderMembership> activeMembers =
        memberships.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            session.getId(), LadderMembership.State.ACTIVE);
    for (LadderMembership membership : activeMembers) {
      if (membership == null || membership.getState() == LadderMembership.State.LEFT) {
        continue;
      }
      membership.setState(LadderMembership.State.LEFT);
      membership.setLeftAt(effectiveArchivedAt);
      memberships.save(membership);
    }
    roundRobinService.endOpenRoundRobinsForSession(session);
    return true;
  }
}
