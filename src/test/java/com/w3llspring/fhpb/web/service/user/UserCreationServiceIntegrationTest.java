package com.w3llspring.fhpb.web.service.user;

import static org.assertj.core.api.Assertions.*;

import com.w3llspring.fhpb.web.db.UserCourtNameRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserCourtName;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserCreationServiceIntegrationTest {
  @Autowired UserRepository users;
  @Autowired UserCourtNameRepository aliases;
  @Autowired PlatformTransactionManager transactions;

  @AfterEach
  void cleanup() {
    aliases.deleteAll();
    users.deleteAll();
  }

  private User registration(String email) {
    User user = new User();
    user.setEmail(email);
    user.setNickName("Alex");
    user.setPassword("encoded-password");
    user.setTimeZone("America/New_York");
    user.setRegisteredAt(Instant.parse("2026-11-10T12:00:00Z"));
    user.setAcknowledgedTermsAt(user.getRegisteredAt());
    user.setMaxOwnedLadders(3);
    UserCourtName alias = new UserCourtName();
    alias.setUser(user);
    alias.setAlias("Alex");
    user.getCourtNames().add(alias);
    return user;
  }

  @Test
  void skipsAnOccupiedCodeWithoutChangingTheExistingAccount() {
    User existing = registration("existing@test.local");
    existing.setPublicCode("PB-ruby-rush-778");
    users.saveAndFlush(existing);
    AtomicInteger attempts = new AtomicInteger();
    UserCreationService service =
        new UserCreationService(
            users,
            transactions,
            () -> attempts.getAndIncrement() == 0 ? existing.getPublicCode() : "calm-ruby-paddle");

    User saved = service.register(registration("new@test.local"));

    assertThat(attempts).hasValue(2);
    assertThat(saved.getPublicCode()).isEqualTo("calm-ruby-paddle");
    assertThat(users.findById(existing.getId()).orElseThrow().getPublicCode())
        .isEqualTo("PB-ruby-rush-778");
    assertThat(saved.getTimeZone()).isEqualTo("America/New_York");
    assertThat(saved.getMaxOwnedLadders()).isEqualTo(3);
    assertThat(saved.getAcknowledgedTermsAt()).isEqualTo(Instant.parse("2026-11-10T12:00:00Z"));
    assertThat(aliases.findByUser_Id(saved.getId()))
        .extracting(UserCourtName::getAlias)
        .containsExactly("Alex");
    assertThat(users.count()).isEqualTo(2);
  }

  @Test
  void concurrentCreationsRetryTheDatabaseCollisionInAFreshTransaction() throws Exception {
    AtomicInteger generated = new AtomicInteger();
    UserCreationService service =
        new UserCreationService(
            users,
            transactions,
            () -> generated.incrementAndGet() <= 2 ? "bright-maple-otter" : "calm-ruby-paddle");
    CyclicBarrier bothCheckedAvailability = new CyclicBarrier(2);
    AtomicInteger callbacks = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures =
          List.of("one@test.local", "two@test.local").stream()
              .map(
                  email ->
                      pool.submit(
                          () ->
                              service.create(
                                  code -> {
                                    callbacks.incrementAndGet();
                                    if (code.equals("bright-maple-otter")) {
                                      try {
                                        bothCheckedAvailability.await(10, TimeUnit.SECONDS);
                                      } catch (Exception ex) {
                                        throw new IllegalStateException(ex);
                                      }
                                    }
                                    User candidate = registration(email);
                                    candidate.setPublicCode(code);
                                    return users.saveAndFlush(candidate);
                                  })))
              .toList();
      User first = futures.get(0).get(20, TimeUnit.SECONDS);
      User second = futures.get(1).get(20, TimeUnit.SECONDS);
      assertThat(first.getPublicCode()).isNotEqualTo(second.getPublicCode());
      assertThat(first.getId()).isNotEqualTo(second.getId());
    }
    assertThat(generated).hasValue(3);
    assertThat(callbacks).hasValue(3);
    assertThat(users.count()).isEqualTo(2);
    assertThat(aliases.count()).isEqualTo(2);
  }

  @Test
  void duplicateEmailsStillFailWithoutRetryingOrCreatingAnotherAccount() {
    User existing = registration("same@test.local");
    existing.setPublicCode("bright-maple-otter");
    users.saveAndFlush(existing);
    AtomicInteger generated = new AtomicInteger();
    UserCreationService service =
        new UserCreationService(
            users,
            transactions,
            () -> {
              generated.incrementAndGet();
              return "calm-ruby-paddle";
            });
    assertThatThrownBy(() -> service.register(registration("same@test.local")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(generated).hasValue(1);
    assertThat(users.count()).isEqualTo(1);
    assertThat(aliases.count()).isEqualTo(1);
  }

  @Test
  void dependentRecordFailureRollsBackTheAccountWithoutRetrying() {
    AtomicInteger generated = new AtomicInteger();
    UserCreationService service =
        new UserCreationService(
            users,
            transactions,
            () -> {
              generated.incrementAndGet();
              return "bright-maple-otter";
            });
    assertThatThrownBy(
            () ->
                service.create(
                    code -> {
                      User candidate = registration("rollback@test.local");
                      candidate.setPublicCode(code);
                      users.saveAndFlush(candidate);
                      UserCourtName invalidAlias = new UserCourtName();
                      invalidAlias.setUser(candidate);
                      return aliases.saveAndFlush(invalidAlias);
                    }))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(generated).hasValue(1);
    assertThat(users.count()).isZero();
    assertThat(aliases.count()).isZero();
  }

  @Test
  void aBrokenGeneratorCannotLoopForever() {
    User existing = registration("existing@test.local");
    existing.setPublicCode("bright-maple-otter");
    users.saveAndFlush(existing);
    AtomicInteger generated = new AtomicInteger();
    UserCreationService service =
        new UserCreationService(
            users,
            transactions,
            () -> {
              generated.incrementAndGet();
              return existing.getPublicCode();
            });
    assertThatThrownBy(() -> service.register(registration("new@test.local")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("member ID");
    assertThat(generated).hasValue(24);
    assertThat(users.count()).isEqualTo(1);
  }
}
