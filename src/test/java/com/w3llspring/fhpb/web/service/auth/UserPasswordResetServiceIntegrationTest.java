package com.w3llspring.fhpb.web.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.auth.UserPasswordResetService.PasswordResetResult;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserPasswordResetServiceIntegrationTest {

  @Autowired private UserRepository userRepository;

  @Autowired private UserPasswordResetService userPasswordResetService;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private BCryptPasswordEncoder passwordEncoder;

  private User user;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    user = new User();
    user.setEmail("reset-" + suffix + "@test.local");
    user.setNickName("reset" + suffix);
    user.setPassword(passwordEncoder.encode("Orig1nalPass!"));
    user = userRepository.saveAndFlush(user);
  }

  @Test
  void sequentialForgotPasswordRequestsReuseTheActiveToken() {
    Instant firstExpiry = Instant.now().plusSeconds(3600);
    Instant secondExpiry = firstExpiry.plusSeconds(300);

    User firstIssued =
        userPasswordResetService.issueResetPasswordToken(user.getEmail(), "token-a", firstExpiry);
    User secondIssued =
        userPasswordResetService.issueResetPasswordToken(user.getEmail(), "token-b", secondExpiry);

    assertThat(firstIssued).isNotNull();
    assertThat(secondIssued).isNotNull();
    assertThat(firstIssued.getResetPasswordToken()).isEqualTo("token-a");
    assertThat(secondIssued.getResetPasswordToken()).isEqualTo("token-a");

    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.getResetPasswordToken()).isEqualTo("token-a");
    assertThat(reloaded.getResetPasswordTokenExpiresAt())
        .isBetween(firstExpiry.minusMillis(1), firstExpiry.plusMillis(1));
  }

  @Test
  void concurrentForgotPasswordRequestsLeaveSingleActiveToken() throws Exception {
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    CountDownLatch firstIssued = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicReference<String> firstToken = new AtomicReference<>();
    AtomicReference<String> secondToken = new AtomicReference<>();
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();
    Instant firstExpiry = Instant.now().plusSeconds(3600);
    Instant secondExpiry = firstExpiry.plusSeconds(300);

    try {
      Future<?> firstFuture =
          executor.submit(
              () -> {
                try {
                  txTemplate.executeWithoutResult(
                      status -> {
                        User issued =
                            userPasswordResetService.issueResetPasswordToken(
                                user.getEmail(), "token-a", firstExpiry);
                        firstToken.set(issued != null ? issued.getResetPasswordToken() : null);
                        firstIssued.countDown();
                        sleepQuietly(250);
                      });
                } catch (Throwable t) {
                  firstFailure.set(t);
                }
              });

      Future<?> secondFuture =
          executor.submit(
              () -> {
                try {
                  awaitLatch(firstIssued, "password reset issuance");
                  txTemplate.executeWithoutResult(
                      status -> {
                        User issued =
                            userPasswordResetService.issueResetPasswordToken(
                                user.getEmail(), "token-b", secondExpiry);
                        secondToken.set(issued != null ? issued.getResetPasswordToken() : null);
                      });
                } catch (Throwable t) {
                  secondFailure.set(t);
                }
              });

      firstFuture.get(10, TimeUnit.SECONDS);
      secondFuture.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(firstFailure.get()).isNull();
    assertThat(secondFailure.get()).isNull();
    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(firstToken.get()).isEqualTo("token-a");
    assertThat(secondToken.get()).isEqualTo(reloaded.getResetPasswordToken());
    assertThat(reloaded.getResetPasswordToken()).isIn("token-a", "token-b");
    if ("token-a".equals(reloaded.getResetPasswordToken())) {
      assertThat(reloaded.getResetPasswordTokenExpiresAt())
          .isBetween(firstExpiry.minusMillis(1), firstExpiry.plusMillis(1));
    } else {
      assertThat(reloaded.getResetPasswordTokenExpiresAt())
          .isBetween(secondExpiry.minusMillis(1), secondExpiry.plusMillis(1));
    }
  }

  @Test
  void concurrentResetSubmissionsOnlyAllowTheFirstTokenConsumer() throws Exception {
    Instant expiresAt = Instant.now().plusSeconds(3600);
    user.setResetPasswordToken("shared-token");
    user.setResetPasswordTokenExpiresAt(expiresAt);
    user = userRepository.saveAndFlush(user);

    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    CountDownLatch firstConsumed = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicReference<PasswordResetResult> firstResult = new AtomicReference<>();
    AtomicReference<PasswordResetResult> secondResult = new AtomicReference<>();
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();

    try {
      Future<?> firstFuture =
          executor.submit(
              () -> {
                try {
                  txTemplate.executeWithoutResult(
                      status -> {
                        firstResult.set(
                            userPasswordResetService.resetPassword(
                                "shared-token", "FirstPass123!"));
                        firstConsumed.countDown();
                        sleepQuietly(250);
                      });
                } catch (Throwable t) {
                  firstFailure.set(t);
                }
              });

      Future<?> secondFuture =
          executor.submit(
              () -> {
                try {
                  awaitLatch(firstConsumed, "password reset consumption");
                  txTemplate.executeWithoutResult(
                      status -> {
                        secondResult.set(
                            userPasswordResetService.resetPassword(
                                "shared-token", "SecondPass123!"));
                      });
                } catch (Throwable t) {
                  secondFailure.set(t);
                }
              });

      firstFuture.get(10, TimeUnit.SECONDS);
      secondFuture.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(firstFailure.get()).isNull();
    assertThat(secondFailure.get()).isNull();
    assertThat(firstResult.get()).isEqualTo(PasswordResetResult.UPDATED);
    assertThat(secondResult.get()).isEqualTo(PasswordResetResult.INVALID_TOKEN);

    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.getResetPasswordToken()).isNull();
    assertThat(reloaded.getResetPasswordTokenExpiresAt()).isNull();
    assertThat(passwordEncoder.matches("FirstPass123!", reloaded.getPassword())).isTrue();
    assertThat(passwordEncoder.matches("SecondPass123!", reloaded.getPassword())).isFalse();
  }

  private static void awaitLatch(CountDownLatch latch, String operation) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException(
            "Timed out while coordinating concurrent " + operation + " test.");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while coordinating concurrent " + operation + " test.", ex);
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while holding password reset lock.", ex);
    }
  }
}
