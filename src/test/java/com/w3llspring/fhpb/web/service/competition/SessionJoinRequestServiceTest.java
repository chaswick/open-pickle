package com.w3llspring.fhpb.web.service.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.SessionJoinRequestRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.SessionJoinRequest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionJoinRequestServiceTest {

  @Mock private LadderConfigRepository configs;
  @Mock private LadderMembershipRepository memberships;
  @Mock private SessionJoinRequestRepository requests;
  @Mock private UserRepository userRepo;
  @Mock private GroupAdministrationService groupAdministration;

  private SessionJoinRequestService service;

  @BeforeEach
  void setUp() {
    service =
        new SessionJoinRequestService(
            configs, memberships, requests, userRepo, groupAdministration, 300L);
  }

  @Test
  void submitByInvite_createsPendingApprovalRequest() {
    LadderConfig session = session(42L, "DINK-7");
    when(configs.findByInviteCode("DINK-7")).thenReturn(Optional.of(session));
    when(configs.lockById(42L)).thenReturn(session);
    when(memberships.findByLadderConfigIdAndUserId(42L, 7L)).thenReturn(Optional.empty());
    when(requests.findByLadderConfigIdAndRequesterUserId(42L, 7L)).thenReturn(Optional.empty());
    when(requests.save(any(SessionJoinRequest.class)))
        .thenAnswer(
            invocation -> {
              SessionJoinRequest request = invocation.getArgument(0);
              request.setId(100L);
              return request;
            });

    SessionJoinRequestService.SubmissionOutcome outcome = service.submitByInvite("dink-7", 7L);

    assertThat(outcome.state())
        .isEqualTo(SessionJoinRequestService.SubmissionState.PENDING_APPROVAL);
    assertThat(outcome.sessionId()).isEqualTo(42L);
    assertThat(outcome.requestId()).isEqualTo(100L);
  }

  @Test
  void submitByInvite_acceptsSessionCodeTypedWithSpaces() {
    LadderConfig session = session(42L, "MINT-COURT-42");
    when(configs.findByInviteCode(anyString()))
        .thenAnswer(
            invocation ->
                "MINT-COURT-42".equals(invocation.getArgument(0, String.class))
                    ? Optional.of(session)
                    : Optional.empty());
    when(configs.lockById(42L)).thenReturn(session);
    when(memberships.findByLadderConfigIdAndUserId(42L, 7L)).thenReturn(Optional.empty());
    when(requests.findByLadderConfigIdAndRequesterUserId(42L, 7L)).thenReturn(Optional.empty());
    when(requests.save(any(SessionJoinRequest.class)))
        .thenAnswer(
            invocation -> {
              SessionJoinRequest request = invocation.getArgument(0);
              request.setId(100L);
              return request;
            });

    SessionJoinRequestService.SubmissionOutcome outcome =
        service.submitByInvite(" mint court 42 ", 7L);

    assertThat(outcome.state())
        .isEqualTo(SessionJoinRequestService.SubmissionState.PENDING_APPROVAL);
    assertThat(outcome.sessionId()).isEqualTo(42L);
    assertThat(outcome.requestId()).isEqualTo(100L);
    verify(configs).findByInviteCode("MINT-COURT-42");
  }

  @Test
  void submitByInvite_returnsAlreadyMemberWhenMembershipIsActive() {
    LadderConfig session = session(42L, "DINK-7");
    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(session);
    membership.setUserId(7L);
    membership.setState(LadderMembership.State.ACTIVE);

    when(configs.findByInviteCode("DINK-7")).thenReturn(Optional.of(session));
    when(configs.lockById(42L)).thenReturn(session);
    when(memberships.findByLadderConfigIdAndUserId(42L, 7L)).thenReturn(Optional.of(membership));

    SessionJoinRequestService.SubmissionOutcome outcome = service.submitByInvite("dink-7", 7L);

    assertThat(outcome.state()).isEqualTo(SessionJoinRequestService.SubmissionState.ALREADY_MEMBER);
    assertThat(outcome.sessionId()).isEqualTo(42L);
    assertThat(outcome.requestId()).isNull();
    verify(requests, never()).save(any(SessionJoinRequest.class));
  }

  @Test
  void submitByInvite_rejectsSessionCodeWhenInviteWindowHasElapsed() {
    LadderConfig session = session(42L, "MINT-COURT-42");
    session.setLastInviteChangeAt(Instant.now().minusSeconds(86_400));

    when(configs.findByInviteCode("MINT-COURT-42")).thenReturn(Optional.of(session));
    when(configs.lockById(42L)).thenReturn(session);

    assertThatThrownBy(() -> service.submitByInvite("mint-court-42", 7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no longer active");

    assertThat(session.getInviteCode()).isNull();
    verify(configs).save(session);
    verify(requests, never()).save(any(SessionJoinRequest.class));
  }

  @Test
  void submitBySessionId_createsPendingApprovalRequestFromCurrentInvite() {
    LadderConfig session = session(42L, "MINT-COURT-42");
    when(configs.lockById(42L)).thenReturn(session);
    when(memberships.findByLadderConfigIdAndUserId(42L, 7L)).thenReturn(Optional.empty());
    when(requests.findByLadderConfigIdAndRequesterUserId(42L, 7L)).thenReturn(Optional.empty());
    when(requests.save(any(SessionJoinRequest.class)))
        .thenAnswer(
            invocation -> {
              SessionJoinRequest request = invocation.getArgument(0);
              request.setId(101L);
              return request;
            });

    SessionJoinRequestService.SubmissionOutcome outcome = service.submitBySessionId(42L, 7L);

    assertThat(outcome.state())
        .isEqualTo(SessionJoinRequestService.SubmissionState.PENDING_APPROVAL);
    assertThat(outcome.sessionId()).isEqualTo(42L);
    assertThat(outcome.requestId()).isEqualTo(101L);
    verify(configs, never()).findByInviteCode(anyString());
  }

  @Test
  void approve_marksRequestApprovedAfterJoiningMember() {
    LadderConfig session = session(42L, "DINK-7");
    SessionJoinRequest request = pendingRequest(100L, session, 8L, "DINK-7");

    when(configs.lockById(42L)).thenReturn(session);
    when(requests.lockById(100L)).thenReturn(Optional.of(request));
    when(memberships.findByLadderConfigIdAndUserId(42L, 8L)).thenReturn(Optional.empty());
    when(groupAdministration.joinByInvite("DINK-7", 8L)).thenReturn(session);
    when(requests.save(any(SessionJoinRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SessionJoinRequestService.ReviewOutcome outcome = service.approve(42L, 100L, 7L);

    assertThat(outcome.status()).isEqualTo(SessionJoinRequest.Status.APPROVED);
    assertThat(request.getStatus()).isEqualTo(SessionJoinRequest.Status.APPROVED);
    assertThat(request.getResolvedByUserId()).isEqualTo(7L);
    verify(groupAdministration).requireAdmin(session, 7L);
    verify(groupAdministration).joinByInvite("DINK-7", 8L);
  }

  @Test
  void approve_keepsRequestPendingWhenSessionIsFull() {
    LadderConfig session = session(42L, "DINK-7");
    SessionJoinRequest request = pendingRequest(100L, session, 8L, "DINK-7");

    when(configs.lockById(42L)).thenReturn(session);
    when(requests.lockById(100L)).thenReturn(Optional.of(request));
    when(memberships.findByLadderConfigIdAndUserId(42L, 8L)).thenReturn(Optional.empty());
    when(groupAdministration.joinByInvite("DINK-7", 8L))
        .thenThrow(new IllegalStateException("Sorry, that group is full."));

    SessionJoinRequestService.ReviewOutcome outcome = service.approve(42L, 100L, 7L);

    assertThat(outcome.status()).isEqualTo(SessionJoinRequest.Status.PENDING);
    assertThat(outcome.message()).contains("Session is full right now");
    assertThat(request.getStatus()).isEqualTo(SessionJoinRequest.Status.PENDING);
  }

  private LadderConfig session(Long id, String inviteCode) {
    LadderConfig session = new LadderConfig();
    session.setId(id);
    session.setType(LadderConfig.Type.SESSION);
    session.setTitle("Saturday Open Session");
    session.setInviteCode(inviteCode);
    session.setStatus(LadderConfig.Status.ACTIVE);
    session.setCreatedAt(Instant.now().minusSeconds(60));
    return session;
  }

  private SessionJoinRequest pendingRequest(
      Long id, LadderConfig session, Long requesterUserId, String inviteCode) {
    SessionJoinRequest request = new SessionJoinRequest();
    request.setId(id);
    request.setLadderConfig(session);
    request.setRequesterUserId(requesterUserId);
    request.setInviteCodeSnapshot(inviteCode);
    request.setStatus(SessionJoinRequest.Status.PENDING);
    request.setRequestedAt(Instant.now().minusSeconds(10));
    request.setExpiresAt(Instant.now().plusSeconds(300));
    return request;
  }
}
