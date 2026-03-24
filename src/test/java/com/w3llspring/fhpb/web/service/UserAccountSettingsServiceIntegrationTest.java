package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.user.UserAccountSettingsService;
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
class UserAccountSettingsServiceIntegrationTest {

  @Autowired private UserRepository userRepository;

  @Autowired private UserAccountSettingsService userAccountSettingsService;

  @Autowired private PlatformTransactionManager transactionManager;

  private User user;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    user = new User();
    user.setEmail("acct-" + suffix + "@test.local");
    user.setNickName("acct" + suffix);
    user.setPassword("pw");
    user = userRepository.saveAndFlush(user);
  }

  @Test
  void concurrentTimeZoneAndTermsUpdates_preserveBothFields() throws Exception {
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();
    Instant acknowledgedAt = Instant.parse("2026-03-17T18:30:00Z");

    try {
      Future<?> timeZoneFuture =
          executor.submit(
              () -> {
                try {
                  txTemplate.executeWithoutResult(
                      status -> {
                        awaitBarrier(barrier);
                        userAccountSettingsService.updateTimeZone(
                            user.getId(), "America/Los_Angeles");
                        sleepQuietly(250);
                      });
                } catch (Throwable t) {
                  firstFailure.set(t);
                }
              });
      Future<?> termsFuture =
          executor.submit(
              () -> {
                try {
                  txTemplate.executeWithoutResult(
                      status -> {
                        awaitBarrier(barrier);
                        userAccountSettingsService.acknowledgeTerms(user.getId(), acknowledgedAt);
                      });
                } catch (Throwable t) {
                  secondFailure.set(t);
                }
              });

      timeZoneFuture.get(10, TimeUnit.SECONDS);
      termsFuture.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(firstFailure.get()).isNull();
    assertThat(secondFailure.get()).isNull();

    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.getTimeZone()).isEqualTo("America/Los_Angeles");
    assertThat(reloaded.getAcknowledgedTermsAt()).isEqualTo(acknowledgedAt);
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while coordinating concurrent user settings test.", ex);
    } catch (BrokenBarrierException | TimeoutException ex) {
      throw new IllegalStateException("Failed to coordinate concurrent user settings test.", ex);
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while holding user settings lock.", ex);
    }
  }
}
