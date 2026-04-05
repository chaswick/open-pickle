package com.w3llspring.fhpb.web.service.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionLifecycleServiceTest {

  @Mock private LadderConfigRepository configRepo;
  @Mock private LadderMembershipRepository membershipRepo;
  @Mock private RoundRobinService roundRobinService;

  private SessionLifecycleService sessionLifecycleService;

  @BeforeEach
  void setUp() {
    sessionLifecycleService = new SessionLifecycleService(configRepo, membershipRepo, roundRobinService);
  }

  @Test
  void archiveSession_clearsInviteAndRemovesActiveMembers() {
    Long sessionId = 42L;
    Instant archivedAt = Instant.parse("2026-03-30T16:15:00Z");

    LadderConfig session = new LadderConfig();
    session.setId(sessionId);
    session.setType(LadderConfig.Type.SESSION);
    session.setStatus(LadderConfig.Status.ACTIVE);
    session.setInviteCode("MINT-COURT-42");

    LadderMembership ownerMembership = new LadderMembership();
    ownerMembership.setId(101L);
    ownerMembership.setLadderConfig(session);
    ownerMembership.setUserId(7L);
    ownerMembership.setState(LadderMembership.State.ACTIVE);

    LadderMembership memberMembership = new LadderMembership();
    memberMembership.setId(102L);
    memberMembership.setLadderConfig(session);
    memberMembership.setUserId(8L);
    memberMembership.setState(LadderMembership.State.ACTIVE);

    when(configRepo.lockById(sessionId)).thenReturn(session);
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            sessionId, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(ownerMembership, memberMembership));

    boolean archived = sessionLifecycleService.archiveSession(sessionId, archivedAt);

    assertThat(archived).isTrue();
    assertThat(session.getStatus()).isEqualTo(LadderConfig.Status.ARCHIVED);
    assertThat(session.getInviteCode()).isNull();
    assertThat(ownerMembership.getState()).isEqualTo(LadderMembership.State.LEFT);
    assertThat(ownerMembership.getLeftAt()).isEqualTo(archivedAt);
    assertThat(memberMembership.getState()).isEqualTo(LadderMembership.State.LEFT);
    assertThat(memberMembership.getLeftAt()).isEqualTo(archivedAt);
    verify(configRepo).save(session);
    verify(membershipRepo).save(ownerMembership);
    verify(membershipRepo).save(memberMembership);
    verify(roundRobinService).endOpenRoundRobinsForSession(session);
  }

  @Test
  void archiveSession_noopsWhenAlreadyArchived() {
    Long sessionId = 77L;

    LadderConfig session = new LadderConfig();
    session.setId(sessionId);
    session.setType(LadderConfig.Type.SESSION);
    session.setStatus(LadderConfig.Status.ARCHIVED);
    session.setInviteCode("OLD-CODE");

    when(configRepo.lockById(sessionId)).thenReturn(session);

    boolean archived = sessionLifecycleService.archiveSession(sessionId, Instant.now());

    assertThat(archived).isFalse();
    assertThat(session.getInviteCode()).isEqualTo("OLD-CODE");
    verify(configRepo, never()).save(session);
    verify(membershipRepo, never()).findByLadderConfigIdAndStateOrderByJoinedAtAsc(
        sessionId, LadderMembership.State.ACTIVE);
    verifyNoInteractions(roundRobinService);
  }

  @Test
  void archiveSession_rejectsNonSessionConfigs() {
    Long ladderId = 88L;

    LadderConfig ladder = new LadderConfig();
    ladder.setId(ladderId);
    ladder.setType(LadderConfig.Type.STANDARD);
    ladder.setStatus(LadderConfig.Status.ACTIVE);

    when(configRepo.lockById(ladderId)).thenReturn(ladder);

    assertThatThrownBy(() -> sessionLifecycleService.archiveSession(ladderId, Instant.now()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("match sessions");
    verifyNoInteractions(roundRobinService);
  }
}
