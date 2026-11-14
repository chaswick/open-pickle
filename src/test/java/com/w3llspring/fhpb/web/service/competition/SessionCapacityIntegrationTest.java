package com.w3llspring.fhpb.web.service.competition;

import static org.assertj.core.api.Assertions.*;

import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional
class SessionCapacityIntegrationTest {
  @Autowired LadderConfigRepository configs;
  @Autowired LadderMembershipRepository members;
  @Autowired SessionJoinRequestRepository rows;
  @Autowired UserRepository users;
  @Autowired SessionJoinRequestService joins;
  @Autowired GroupAdministrationService groups;
  @Autowired SessionCapacityService capacity;
  @Autowired PlatformTransactionManager transactions;

  @Test
  void inviteAdmissionAlsoExcludesTheNonPlayingHostFromTheLimit() {
    LadderConfig session = session(1);
    session.setSessionHostPlaying(false);
    configs.saveAndFlush(session);
    User first = user();
    User second = user();
    groups.joinByInvite(session.getInviteCode(), first.getId());
    assertActive(session, first);
    assertThatThrownBy(() -> groups.joinByInvite(session.getInviteCode(), second.getId()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("full");
  }

  @Test
  void nonPlayingHostDoesNotConsumeCapacityOrDisplaceWaitlistedPlayers() {
    LadderConfig session = session(1);
    session.setSessionHostPlaying(false);
    configs.saveAndFlush(session);
    User first = user();
    User second = user();
    assertThat(direct(session, first).state())
        .isEqualTo(SessionJoinRequestService.SubmissionState.ALREADY_MEMBER);
    var waiting = direct(session, second);
    assertThat(waiting.state()).isEqualTo(SessionJoinRequestService.SubmissionState.WAITLISTED);
    assertThat(joins.listActiveMembers(session.getId(), session.getOwnerUserId()))
        .extracting(SessionJoinRequestService.ActiveSessionMemberView::userId)
        .containsExactly(first.getId());
    leave(session, first);
    assertActive(session, second);
    assertThat(joins.listActiveMembers(session.getId(), session.getOwnerUserId()))
        .extracting(SessionJoinRequestService.ActiveSessionMemberView::userId)
        .containsExactly(second.getId());
    groups.requireAdmin(session, session.getOwnerUserId());
    capacity.updateLimit(session.getId(), session.getOwnerUserId(), 2);
    assertThat(session.getSessionMemberLimit()).isEqualTo(2);
    var host =
        members
            .findByLadderConfigIdAndUserId(session.getId(), session.getOwnerUserId())
            .orElseThrow();
    groups.leaveMember(session.getId(), session.getOwnerUserId(), host.getId());
    assertThat(session.getStatus()).isEqualTo(LadderConfig.Status.ARCHIVED);
  }

  @Test
  void departuresPromoteInOrderAndRepeatedInvitesKeepTheirPlace() {
    LadderConfig session = session(2);
    User player = user();
    User first = user();
    User second = user();
    assertThat(direct(session, player).state())
        .isEqualTo(SessionJoinRequestService.SubmissionState.ALREADY_MEMBER);
    var waiting = direct(session, first);
    var later = direct(session, second);
    assertThat(direct(session, first).requestId()).isEqualTo(waiting.requestId());
    assertThat(joins.getStatusForRequester(waiting.requestId(), first.getId()).pending()).isTrue();
    leave(session, player);
    assertActive(session, first);
    assertThat(joins.getStatusForRequester(waiting.requestId(), first.getId()).redirectUrl())
        .contains("/groups/" + session.getId());
    assertThat(rows.findById(later.requestId()).orElseThrow().getStatus())
        .isEqualTo(SessionJoinRequest.Status.WAITLISTED);
    leave(session, first);
    assertActive(session, second);
    assertThat(active(session)).hasSize(2);
  }

  @Test
  void approvalQueuesFullSessionsAndSurvivesInviteExpiryOrRotation() {
    LadderConfig session = session(1);
    User first = user();
    var outcome = joins.submitByInvite(session.getInviteCode(), first.getId());
    assertThat(rows.findById(outcome.requestId()).orElseThrow().getStatus())
        .isEqualTo(SessionJoinRequest.Status.PENDING);
    assertThat(
            joins.approve(session.getId(), outcome.requestId(), session.getOwnerUserId()).status())
        .isEqualTo(SessionJoinRequest.Status.WAITLISTED);
    // A waitlist place outlives the original request timeout while the session remains open.
    var waiting = rows.findById(outcome.requestId()).orElseThrow();
    waiting.setExpiresAt(Instant.now().minusSeconds(1));
    rows.saveAndFlush(waiting);
    assertThat(joins.getStatusForRequester(waiting.getId(), first.getId()).status())
        .isEqualTo(SessionJoinRequest.Status.WAITLISTED);
    session.setInviteCode(null);
    session.setLastInviteChangeAt(Instant.now().minusSeconds(3600));
    configs.saveAndFlush(session);
    capacity.updateLimit(session.getId(), session.getOwnerUserId(), 2);
    assertActive(session, first);
  }

  @Test
  void raisingLimitFillsSeatsAndLoweringCannotDisplaceJoinedPlayers() {
    LadderConfig session = session(1);
    User first = user();
    User second = user();
    User third = user();
    direct(session, first);
    direct(session, second);
    capacity.updateLimit(session.getId(), session.getOwnerUserId(), 3);
    assertThat(active(session)).hasSize(3);
    assertActive(session, first);
    assertActive(session, second);
    var waiting = direct(session, third);
    assertThatThrownBy(() -> capacity.updateLimit(session.getId(), session.getOwnerUserId(), 2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("3 players already joined");
    assertThat(session.getSessionMemberLimit()).isEqualTo(3);
    assertThat(active(session)).hasSize(3);
    assertThat(rows.findById(waiting.requestId()).orElseThrow().getStatus())
        .isEqualTo(SessionJoinRequest.Status.WAITLISTED);
  }

  @Test
  void lowerLimitUsesOnlyJoinedPlayersAndExcludesTheWaitlist() {
    LadderConfig session = session(4);
    User first = user();
    User waiting = user();
    direct(session, first);
    capacity.updateLimit(session.getId(), session.getOwnerUserId(), 2);
    direct(session, waiting);
    // The two joined players include the host; the waiting player doesn't raise the minimum.
    capacity.updateLimit(session.getId(), session.getOwnerUserId(), 2);
    assertThat(session.getSessionMemberLimit()).isEqualTo(2);
    assertThat(active(session)).hasSize(2);
  }

  @Test
  void hostRemovalFillsTheVacancyAndCancelledPlayersAreSkipped() {
    LadderConfig session = session(2);
    User player = user();
    User first = user();
    User second = user();
    direct(session, player);
    var waiting = direct(session, first);
    direct(session, second);
    joins.cancel(waiting.requestId(), first.getId());
    groups.removeSessionMember(
        session.getId(), session.getOwnerUserId(), membership(session, player).getId());
    assertActive(session, second);
    assertThat(members.findByLadderConfigIdAndUserId(session.getId(), first.getId())).isEmpty();
  }

  @Test
  void endingTheSessionDoesNotAdmitWaitingPlayers() {
    LadderConfig session = session(1);
    User player = user();
    var waiting = direct(session, player);
    var host =
        members
            .findByLadderConfigIdAndUserId(session.getId(), session.getOwnerUserId())
            .orElseThrow();
    groups.leaveMember(session.getId(), session.getOwnerUserId(), host.getId());
    assertThat(active(session)).isEmpty();
    assertThat(joins.getStatusForRequester(waiting.requestId(), player.getId()).status())
        .isEqualTo(SessionJoinRequest.Status.EXPIRED);
  }

  @Test
  void onlyTheHostCanSetAValidLimit() {
    LadderConfig session = session(2);
    User player = user();
    direct(session, player);
    assertThatThrownBy(() -> capacity.updateLimit(session.getId(), player.getId(), 3))
        .isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> capacity.updateLimit(session.getId(), session.getOwnerUserId(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> capacity.updateLimit(session.getId(), session.getOwnerUserId(), 33))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(session.getSessionMemberLimit()).isEqualTo(2);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void simultaneousJoinsCannotTakeTheSameFinalSeat() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(transactions);
    LadderConfig session = tx.execute(status -> session(2));
    User first = tx.execute(status -> user());
    User second = tx.execute(status -> user());
    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      Callable<SessionJoinRequestService.SubmissionOutcome> one =
          () -> {
            ready.countDown();
            start.await();
            return direct(session, first);
          };
      Callable<SessionJoinRequestService.SubmissionOutcome> two =
          () -> {
            ready.countDown();
            start.await();
            return direct(session, second);
          };
      var a = workers.submit(one);
      var b = workers.submit(two);
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(a.get(10, TimeUnit.SECONDS).state(), b.get(10, TimeUnit.SECONDS).state()))
          .containsExactlyInAnyOrder(
              SessionJoinRequestService.SubmissionState.ALREADY_MEMBER,
              SessionJoinRequestService.SubmissionState.WAITLISTED);
      int activeCount = tx.execute(status -> active(session).size());
      assertThat(activeCount).isEqualTo(2);
    } finally {
      tx.executeWithoutResult(
          status -> {
            rows.deleteAll(
                rows.findByLadderConfigIdAndStatusOrderByResolvedAtAscIdAsc(
                    session.getId(), SessionJoinRequest.Status.WAITLISTED));
            rows.deleteAll(
                rows.findByLadderConfigIdAndStatusOrderByResolvedAtAscIdAsc(
                    session.getId(), SessionJoinRequest.Status.APPROVED));
            members.deleteAll(active(session));
            configs.deleteById(session.getId());
            users.deleteAllById(List.of(first.getId(), second.getId(), session.getOwnerUserId()));
          });
    }
  }

  private User user() {
    User user = new User();
    user.setEmail(UUID.randomUUID().toString().substring(0, 16) + "@capacity.test");
    user.setNickName("Player");
    user.setPassword("unused");
    return users.saveAndFlush(user);
  }

  private LadderConfig session(int limit) {
    User host = user();
    LadderConfig session = new LadderConfig();
    session.setType(LadderConfig.Type.SESSION);
    session.setTitle("Capacity test");
    session.setOwnerUserId(host.getId());
    session.setSessionMemberLimit(limit);
    session.setExpiresAt(Instant.now().plusSeconds(3600));
    session.setInviteCode(UUID.randomUUID().toString().substring(0, 16).toUpperCase());
    configs.saveAndFlush(session);
    LadderMembership owner = new LadderMembership();
    owner.setLadderConfig(session);
    owner.setUserId(host.getId());
    owner.setRole(LadderMembership.Role.ADMIN);
    members.saveAndFlush(owner);
    return session;
  }

  private SessionJoinRequestService.SubmissionOutcome direct(LadderConfig session, User user) {
    return joins.joinDirectByInvite(session.getInviteCode(), user.getId());
  }

  private List<LadderMembership> active(LadderConfig session) {
    return members.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
        session.getId(), LadderMembership.State.ACTIVE);
  }

  private LadderMembership membership(LadderConfig session, User user) {
    return members.findByLadderConfigIdAndUserId(session.getId(), user.getId()).orElseThrow();
  }

  private void leave(LadderConfig session, User user) {
    groups.leaveMember(session.getId(), user.getId(), membership(session, user).getId());
  }

  private void assertActive(LadderConfig session, User user) {
    assertThat(membership(session, user).getState()).isEqualTo(LadderMembership.State.ACTIVE);
  }
}
