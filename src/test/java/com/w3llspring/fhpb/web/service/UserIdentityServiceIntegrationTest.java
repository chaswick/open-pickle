package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.db.UserDisplayNameAuditRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.user.UserIdentityService;
import com.w3llspring.fhpb.web.service.user.UserIdentityService.DisplayNameChangeStatus;
import java.time.Duration;
import java.time.Instant;
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
class UserIdentityServiceIntegrationTest {

  @Autowired private UserRepository userRepository;

  @Autowired private UserDisplayNameAuditRepository userDisplayNameAuditRepository;

  @Autowired private UserIdentityService userIdentityService;

  @Autowired private PlatformTransactionManager transactionManager;

  private User user;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    user = new User();
    user.setEmail("rename-" + suffix + "@test.local");
    user.setNickName("rename" + suffix);
    user.setPassword("pw");
    user = userRepository.saveAndFlush(user);
  }

  @Test
  void concurrentRenameRequestsForSameUser_allowFirstAndBlockSecondOnCooldown() throws Exception {
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    Instant renameAt = Instant.parse("2026-03-17T18:00:00Z");
    Duration cooldown = Duration.ofHours(24);
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicReference<DisplayNameChangeStatus> firstStatus = new AtomicReference<>();
    AtomicReference<DisplayNameChangeStatus> secondStatus = new AtomicReference<>();
    AtomicReference<Instant> secondAllowedAt = new AtomicReference<>();

    try {
      Future<Throwable> firstFuture =
          executor.submit(
              () -> {
                try {
                  txTemplate.executeWithoutResult(
                      status -> {
                        var result =
                            userIdentityService.changeDisplayName(
                                user.getId(), "RenamedOne", user.getId(), renameAt, cooldown);
                        firstStatus.set(result.status());
                        awaitBarrier(barrier);
                        sleepQuietly(250);
                      });
                  return null;
                } catch (Throwable t) {
                  return t;
                }
              });

      Future<Throwable> secondFuture =
          executor.submit(
              () -> {
                try {
                  txTemplate.executeWithoutResult(
                      status -> {
                        awaitBarrier(barrier);
                        var result =
                            userIdentityService.changeDisplayName(
                                user.getId(),
                                "RenamedTwo",
                                user.getId(),
                                renameAt.plusSeconds(5),
                                cooldown);
                        secondStatus.set(result.status());
                        secondAllowedAt.set(result.allowedAt());
                      });
                  return null;
                } catch (Throwable t) {
                  return t;
                }
              });

      assertThat(firstFuture.get(10, TimeUnit.SECONDS)).isNull();
      assertThat(secondFuture.get(10, TimeUnit.SECONDS)).isNull();
    } finally {
      executor.shutdownNow();
    }

    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(firstStatus.get()).isEqualTo(DisplayNameChangeStatus.CHANGED);
    assertThat(secondStatus.get()).isEqualTo(DisplayNameChangeStatus.COOLDOWN);
    assertThat(secondAllowedAt.get()).isEqualTo(renameAt.plus(cooldown));
    assertThat(reloaded.getNickName()).isEqualTo("RenamedOne");
    assertThat(reloaded.getLastDisplayNameChangeAt()).isEqualTo(renameAt);
    assertThat(userDisplayNameAuditRepository.findAll()).hasSize(1);
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while coordinating concurrent display-name test.", ex);
    } catch (BrokenBarrierException | TimeoutException ex) {
      throw new IllegalStateException("Failed to coordinate concurrent display-name test.", ex);
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while holding display-name lock.", ex);
    }
  }
}
