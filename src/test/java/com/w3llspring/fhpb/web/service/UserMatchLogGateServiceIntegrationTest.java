package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
class UserMatchLogGateServiceIntegrationTest {

  @Autowired private UserRepository userRepository;

  @Autowired private UserMatchLogGateService userMatchLogGateService;

  @Autowired private PlatformTransactionManager transactionManager;

  private User user;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    user = new User();
    user.setEmail("gate-" + suffix + "@test.local");
    user.setNickName("gate" + suffix);
    user.setPassword("pw");
    user.setLastMatchLoggedAt(Instant.now().minus(Duration.ofMinutes(1)));
    user.setConsecutiveMatchLogs(2);
    user = userRepository.saveAndFlush(user);
  }

  @Test
  void reserveMatchLogging_blocksSecondConcurrentAttemptAfterFirstConsumesThirdQuickLog()
      throws Exception {
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    Instant gateTime = Instant.parse("2026-03-17T15:00:00Z");
    user.setLastMatchLoggedAt(gateTime.minus(Duration.ofMinutes(1)));
    user.setConsecutiveMatchLogs(2);
    userRepository.saveAndFlush(user);

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicBoolean firstAllowed = new AtomicBoolean(false);
    AtomicBoolean secondAllowed = new AtomicBoolean(true);
    AtomicReference<String> secondMessage = new AtomicReference<>();

    try {
      Future<Throwable> firstFuture =
          executor.submit(
              () -> {
                try {
                  txTemplate.executeWithoutResult(
                      status -> {
                        UserMatchLogGateService.MatchLogGateResult result =
                            userMatchLogGateService.reserveMatchLogging(
                                user.getId(), gateTime, true);
                        firstAllowed.set(result.allowed());
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
                        UserMatchLogGateService.MatchLogGateResult result =
                            userMatchLogGateService.reserveMatchLogging(
                                user.getId(), gateTime, true);
                        secondAllowed.set(result.allowed());
                        secondMessage.set(result.message());
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
    assertThat(firstAllowed.get()).isTrue();
    assertThat(secondAllowed.get()).isFalse();
    assertThat(secondMessage.get())
        .contains("Please wait 5 more minutes before logging another match.");
    assertThat(reloaded.getLastMatchLoggedAt()).isEqualTo(gateTime);
    assertThat(reloaded.getConsecutiveMatchLogs()).isEqualTo(3);
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while coordinating concurrent match-log gate test.", ex);
    } catch (BrokenBarrierException | TimeoutException ex) {
      throw new IllegalStateException("Failed to coordinate concurrent match-log gate test.", ex);
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while holding match-log gate lock.", ex);
    }
  }
}
