package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.matchworkflow.MatchStateTransitionService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class MatchStateTransitionConcurrencyIntegrationTest {

  @Autowired private UserRepository userRepository;

  @Autowired private LadderConfigRepository configRepository;

  @Autowired private LadderSeasonRepository seasonRepository;

  @Autowired private MatchRepository matchRepository;

  @Autowired private MatchConfirmationService matchConfirmationService;

  @Autowired private MatchStateTransitionService matchStateTransitionService;

  @Autowired private PlatformTransactionManager transactionManager;

  private User admin;
  private User opponent;
  private LadderSeason season;
  private Match match;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    admin = saveUser("match-admin-" + suffix + "@test.local", "madmin" + suffix);
    opponent = saveUser("match-opp-" + suffix + "@test.local", "mopp" + suffix);

    LadderConfig ladder = new LadderConfig();
    ladder.setTitle("Match Concurrency " + suffix);
    ladder.setOwnerUserId(admin.getId());
    ladder.setInviteCode("mc-" + suffix);
    ladder.setMode(LadderConfig.Mode.MANUAL);
    ladder.setSecurityLevel(LadderSecurity.STANDARD);
    ladder = configRepository.save(ladder);

    LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
    season = new LadderSeason();
    season.setLadderConfig(ladder);
    season.setName("Season " + suffix);
    season.setStartDate(todayUtc);
    season.setEndDate(todayUtc.plusWeeks(4));
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartedAt(Instant.now());
    season.setStartedByUserId(admin.getId());
    season = seasonRepository.save(season);

    match = new Match();
    match.setSeason(season);
    match.setLoggedBy(admin);
    match.setA1(admin);
    match.setB1(opponent);
    match.setScoreA(11);
    match.setScoreB(7);
    match.setState(MatchState.PROVISIONAL);
    match = matchRepository.saveAndFlush(match);

    matchConfirmationService.createRequests(match);
  }

  @Nested
  @DisplayName("Concurrent confirm and edit paths")
  class ConcurrentConfirmAndEditPaths {

    @Test
    void stale_admin_edit_is_rejected_when_opponent_confirms_first() throws Exception {
      TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
      txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

      Match staleSnapshot = matchRepository.findByIdWithUsers(match.getId()).orElseThrow();
      CyclicBarrier barrier = new CyclicBarrier(2);
      ExecutorService executor = Executors.newFixedThreadPool(2);

      try {
        Future<Throwable> editFuture =
            executor.submit(
                () -> {
                  try {
                    txTemplate.executeWithoutResult(
                        status -> {
                          User txAdmin = userRepository.findById(admin.getId()).orElseThrow();
                          User txOpponent = userRepository.findById(opponent.getId()).orElseThrow();

                          awaitBarrier(barrier);
                          sleepQuietly(150);

                          matchStateTransitionService.editMatch(
                              new MatchStateTransitionService.EditMatchCommand(
                                  match.getId(),
                                  txAdmin,
                                  true,
                                  staleSnapshot.getVersion(),
                                  txAdmin,
                                  staleSnapshot.isA1Guest(),
                                  null,
                                  false,
                                  txOpponent,
                                  staleSnapshot.isB1Guest(),
                                  null,
                                  false,
                                  21,
                                  18));
                        });
                    return null;
                  } catch (Throwable t) {
                    return t;
                  }
                });

        Future<Throwable> confirmFuture =
            executor.submit(
                () -> {
                  try {
                    txTemplate.executeWithoutResult(
                        status -> {
                          awaitBarrier(barrier);
                          matchConfirmationService.confirmMatch(match.getId(), opponent.getId());
                        });
                    return null;
                  } catch (Throwable t) {
                    return t;
                  }
                });

        assertThat(confirmFuture.get(10, TimeUnit.SECONDS)).isNull();
        assertThat(editFuture.get(10, TimeUnit.SECONDS))
            .isInstanceOf(OptimisticLockingFailureException.class)
            .hasMessageContaining("updated by someone else");
      } finally {
        executor.shutdownNow();
      }

      Match reloaded = matchRepository.findByIdWithUsers(match.getId()).orElseThrow();
      assertThat(reloaded.getState()).isEqualTo(MatchState.CONFIRMED);
      assertThat(reloaded.getScoreA()).isEqualTo(11);
      assertThat(reloaded.getScoreB()).isEqualTo(7);
      assertThat(reloaded.getEditedBy()).isNull();
      assertThat(reloaded.getEditedAt()).isNull();
    }

    @Test
    void stale_detached_match_cannot_overwrite_confirmed_state() {
      Match staleSnapshot = matchRepository.findByIdWithUsers(match.getId()).orElseThrow();

      TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
      txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      txTemplate.executeWithoutResult(
          status -> matchConfirmationService.confirmMatch(match.getId(), opponent.getId()));

      staleSnapshot.setScoreA(21);
      staleSnapshot.setScoreB(18);
      staleSnapshot.setEditedBy(admin);
      staleSnapshot.setEditedAt(Instant.now());

      assertThatThrownBy(
              () ->
                  txTemplate.executeWithoutResult(
                      status -> matchRepository.saveAndFlush(staleSnapshot)))
          .isInstanceOf(OptimisticLockingFailureException.class);

      Match reloaded = matchRepository.findByIdWithUsers(match.getId()).orElseThrow();
      assertThat(reloaded.getState()).isEqualTo(MatchState.CONFIRMED);
      assertThat(reloaded.getScoreA()).isEqualTo(11);
      assertThat(reloaded.getScoreB()).isEqualTo(7);
    }
  }

  @Nested
  @DisplayName("Edit validation")
  class EditValidation {

    @Test
    void no_op_edit_is_rejected() {
      Match fresh = matchRepository.findByIdWithUsers(match.getId()).orElseThrow();

      assertThatThrownBy(
              () ->
                  matchStateTransitionService.editMatch(
                      new MatchStateTransitionService.EditMatchCommand(
                          fresh.getId(),
                          opponent,
                          false,
                          fresh.getVersion(),
                          admin,
                          fresh.isA1Guest(),
                          fresh.getA2(),
                          fresh.isA2Guest(),
                          opponent,
                          fresh.isB1Guest(),
                          fresh.getB2(),
                          fresh.isB2Guest(),
                          fresh.getScoreA(),
                          fresh.getScoreB())))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No changes were made");

      Match reloaded = matchRepository.findByIdWithUsers(match.getId()).orElseThrow();
      assertThat(reloaded.getEditedBy()).isNull();
      assertThat(reloaded.getEditedAt()).isNull();
      assertThat(reloaded.getScoreA()).isEqualTo(11);
      assertThat(reloaded.getScoreB()).isEqualTo(7);
    }
  }

  @Nested
  @DisplayName("Overdue scheduler races")
  class OverdueSchedulerRaces {

    @Test
    void auto_confirm_overdue_does_not_nullify_match_confirmed_while_scheduler_waits_on_lock()
        throws Exception {
      match.setCreatedAt(Instant.now().minus(Duration.ofHours(72)));
      matchRepository.saveAndFlush(match);

      TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
      txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

      CyclicBarrier barrier = new CyclicBarrier(2);
      ExecutorService executor = Executors.newFixedThreadPool(2);

      try {
        Future<Throwable> confirmFuture =
            executor.submit(
                () -> {
                  try {
                    txTemplate.executeWithoutResult(
                        status -> {
                          matchRepository.findByIdWithUsersForUpdate(match.getId()).orElseThrow();
                          awaitBarrier(barrier);
                          sleepQuietly(250);
                          matchConfirmationService.confirmMatch(match.getId(), opponent.getId());
                        });
                    return null;
                  } catch (Throwable t) {
                    return t;
                  }
                });

        Future<Throwable> schedulerFuture =
            executor.submit(
                () -> {
                  try {
                    awaitBarrier(barrier);
                    matchConfirmationService.autoConfirmOverdue();
                    return null;
                  } catch (Throwable t) {
                    return t;
                  }
                });

        assertThat(confirmFuture.get(10, TimeUnit.SECONDS)).isNull();
        assertThat(schedulerFuture.get(10, TimeUnit.SECONDS)).isNull();
      } finally {
        executor.shutdownNow();
      }

      Match reloaded = matchRepository.findByIdWithUsers(match.getId()).orElseThrow();
      assertThat(reloaded.getState()).isEqualTo(MatchState.CONFIRMED);
      assertThat(reloaded.getScoreA()).isEqualTo(11);
      assertThat(reloaded.getScoreB()).isEqualTo(7);
    }
  }

  private User saveUser(String email, String nickName) {
    User user = new User();
    user.setEmail(email);
    user.setNickName(nickName);
    user.setPassword("pw");
    return userRepository.save(user);
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while coordinating concurrent match transition test.", ex);
    } catch (BrokenBarrierException | TimeoutException ex) {
      throw new IllegalStateException("Failed to coordinate concurrent match transition test.", ex);
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while coordinating concurrent match transition test.", ex);
    }
  }
}
