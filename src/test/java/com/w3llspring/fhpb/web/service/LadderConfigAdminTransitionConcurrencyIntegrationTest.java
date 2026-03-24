package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationService;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class LadderConfigAdminTransitionConcurrencyIntegrationTest {

  @Autowired private UserRepository userRepository;

  @Autowired private LadderConfigRepository configRepository;

  @Autowired private LadderMembershipRepository membershipRepository;

  @Autowired private GroupAdministrationService groupAdministrationService;

  @Autowired private PlatformTransactionManager transactionManager;

  private LadderConfig ladder;
  private LadderMembership ownerMembership;
  private LadderMembership adminOneMembership;
  private LadderMembership adminTwoMembership;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    User owner = saveUser("ladder-owner-" + suffix + "@test.local", "lcowner" + suffix);
    User adminOne = saveUser("ladder-admin1-" + suffix + "@test.local", "lcadm1" + suffix);
    User adminTwo = saveUser("ladder-admin2-" + suffix + "@test.local", "lcadm2" + suffix);

    ladder = new LadderConfig();
    ladder.setTitle("Admin Concurrency " + suffix);
    ladder.setOwnerUserId(owner.getId());
    ladder.setInviteCode("lc-" + suffix);
    ladder = configRepository.saveAndFlush(ladder);

    ownerMembership =
        saveMembership(
            ladder, owner.getId(), LadderMembership.Role.ADMIN, LadderMembership.State.LEFT);
    ownerMembership.setLeftAt(Instant.now());
    ownerMembership = membershipRepository.saveAndFlush(ownerMembership);

    adminOneMembership =
        saveMembership(
            ladder, adminOne.getId(), LadderMembership.Role.ADMIN, LadderMembership.State.ACTIVE);
    adminTwoMembership =
        saveMembership(
            ladder, adminTwo.getId(), LadderMembership.Role.ADMIN, LadderMembership.State.ACTIVE);
  }

  @Test
  void concurrentSelfDemotions_leaveExactlyOneActiveAdmin() throws Exception {
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();

    try {
      Future<?> firstFuture =
          executor.submit(
              () ->
                  runDemotion(
                      txTemplate,
                      barrier,
                      adminOneMembership.getUserId(),
                      adminOneMembership.getId(),
                      true,
                      firstFailure));
      Future<?> secondFuture =
          executor.submit(
              () ->
                  runDemotion(
                      txTemplate,
                      barrier,
                      adminTwoMembership.getUserId(),
                      adminTwoMembership.getId(),
                      false,
                      secondFailure));

      firstFuture.get(10, TimeUnit.SECONDS);
      secondFuture.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    List<LadderMembership> activeMembers =
        membershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            ladder.getId(), LadderMembership.State.ACTIVE);
    long activeAdminCount =
        activeMembers.stream()
            .filter(membership -> membership.getRole() == LadderMembership.Role.ADMIN)
            .count();

    List<Throwable> failures = Arrays.asList(firstFailure.get(), secondFailure.get());

    assertThat(failures).filteredOn(failure -> failure == null).hasSize(1);
    assertThat(failures).filteredOn(failure -> failure instanceof IllegalStateException).hasSize(1);
    Throwable failure = firstFailure.get() != null ? firstFailure.get() : secondFailure.get();
    assertThat(failure)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("At least one active admin is required");
    assertThat(activeAdminCount).isEqualTo(1L);
    assertThat(activeMembers)
        .filteredOn(membership -> membership.getRole() == LadderMembership.Role.ADMIN)
        .extracting(LadderMembership::getUserId)
        .hasSize(1)
        .containsAnyOf(adminOneMembership.getUserId(), adminTwoMembership.getUserId());
  }

  private void runDemotion(
      TransactionTemplate txTemplate,
      CyclicBarrier barrier,
      Long requesterUserId,
      Long membershipId,
      boolean holdLock,
      AtomicReference<Throwable> failureRef) {
    try {
      txTemplate.executeWithoutResult(
          status -> {
            awaitBarrier(barrier);
            groupAdministrationService.demoteFromAdmin(
                ladder.getId(), requesterUserId, membershipId);
            if (holdLock) {
              sleepQuietly(250);
            }
          });
    } catch (Throwable t) {
      failureRef.set(t);
    }
  }

  private User saveUser(String email, String nickName) {
    User user = new User();
    user.setEmail(email);
    user.setNickName(nickName);
    user.setPassword("pw");
    return userRepository.saveAndFlush(user);
  }

  private LadderMembership saveMembership(
      LadderConfig ladderConfig,
      Long userId,
      LadderMembership.Role role,
      LadderMembership.State state) {
    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(ladderConfig);
    membership.setUserId(userId);
    membership.setRole(role);
    membership.setState(state);
    return membershipRepository.saveAndFlush(membership);
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while coordinating concurrent admin demotion test.", ex);
    } catch (BrokenBarrierException | TimeoutException ex) {
      throw new IllegalStateException("Failed to coordinate concurrent admin demotion test.", ex);
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while holding ladder admin lock.", ex);
    }
  }
}
