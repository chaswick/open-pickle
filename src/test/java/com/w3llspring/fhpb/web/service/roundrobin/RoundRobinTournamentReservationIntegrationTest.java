package com.w3llspring.fhpb.web.service.roundrobin;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.RoundRobinEntryRepository;
import com.w3llspring.fhpb.web.db.RoundRobinRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.RoundRobin;
import com.w3llspring.fhpb.web.model.RoundRobinEntry;
import com.w3llspring.fhpb.web.model.User;
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
class RoundRobinTournamentReservationIntegrationTest {

  @Autowired private UserRepository userRepository;

  @Autowired private LadderConfigRepository configRepository;

  @Autowired private LadderSeasonRepository seasonRepository;

  @Autowired private RoundRobinRepository roundRobinRepository;

  @Autowired private RoundRobinEntryRepository roundRobinEntryRepository;

  @Autowired private MatchRepository matchRepository;

  @Autowired private RoundRobinService roundRobinService;

  @Autowired private PlatformTransactionManager transactionManager;

  private User admin;
  private User player1;
  private User player2;
  private User player3;
  private User player4;
  private LadderSeason season;
  private RoundRobinEntry entry;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    admin = saveUser("rr-admin-" + suffix + "@test.local", "rradm" + suffix);
    player1 = saveUser("rr-p1-" + suffix + "@test.local", "rrp1" + suffix);
    player2 = saveUser("rr-p2-" + suffix + "@test.local", "rrp2" + suffix);
    player3 = saveUser("rr-p3-" + suffix + "@test.local", "rrp3" + suffix);
    player4 = saveUser("rr-p4-" + suffix + "@test.local", "rrp4" + suffix);

    LadderConfig ladder = new LadderConfig();
    ladder.setTitle("Tournament " + suffix);
    ladder.setOwnerUserId(admin.getId());
    ladder.setInviteCode("rr-" + suffix);
    ladder.setMode(LadderConfig.Mode.MANUAL);
    ladder.setSecurityLevel(LadderSecurity.STANDARD);
    ladder.setTournamentMode(true);
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

    RoundRobin roundRobin = new RoundRobin();
    roundRobin.setSeason(season);
    roundRobin.setCurrentRound(1);
    roundRobin.setName("RR " + suffix);
    roundRobin.setCreatedBy(admin);
    roundRobin = roundRobinRepository.save(roundRobin);

    entry = new RoundRobinEntry();
    entry.setRoundRobin(roundRobin);
    entry.setRoundNumber(1);
    entry.setA1(player1);
    entry.setA2(player2);
    entry.setB1(player3);
    entry.setB2(player4);
    entry = roundRobinEntryRepository.save(entry);
  }

  @Test
  void reserveTournamentEntry_allowsOnlyOneConcurrentMatchLinkForSameEntry() throws Exception {
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicReference<Long> firstMatchId = new AtomicReference<>();
    AtomicReference<Long> secondMatchId = new AtomicReference<>();
    AtomicReference<String> secondFailureMessage = new AtomicReference<>();

    try {
      Future<Throwable> firstFuture =
          executor.submit(
              () -> {
                try {
                  txTemplate.executeWithoutResult(
                      status -> {
                        LadderSeason txSeason =
                            seasonRepository.findById(season.getId()).orElseThrow();
                        User txPlayer1 = userRepository.findById(player1.getId()).orElseThrow();
                        User txPlayer2 = userRepository.findById(player2.getId()).orElseThrow();
                        User txPlayer3 = userRepository.findById(player3.getId()).orElseThrow();
                        User txPlayer4 = userRepository.findById(player4.getId()).orElseThrow();
                        User txAdmin = userRepository.findById(admin.getId()).orElseThrow();

                        RoundRobinEntry reserved =
                            roundRobinService.reserveTournamentEntry(
                                txSeason,
                                entry.getId(),
                                txPlayer1,
                                txPlayer2,
                                txPlayer3,
                                txPlayer4);

                        awaitBarrier(barrier);
                        sleepQuietly(250);

                        Match saved =
                            matchRepository.saveAndFlush(
                                newMatch(
                                    txSeason, txAdmin, txPlayer1, txPlayer2, txPlayer3, txPlayer4,
                                    21, 15));
                        roundRobinService.linkEntryToMatch(reserved, saved.getId());
                        firstMatchId.set(saved.getId());
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
                        LadderSeason txSeason =
                            seasonRepository.findById(season.getId()).orElseThrow();
                        User txPlayer1 = userRepository.findById(player1.getId()).orElseThrow();
                        User txPlayer2 = userRepository.findById(player2.getId()).orElseThrow();
                        User txPlayer3 = userRepository.findById(player3.getId()).orElseThrow();
                        User txPlayer4 = userRepository.findById(player4.getId()).orElseThrow();
                        User txAdmin = userRepository.findById(admin.getId()).orElseThrow();

                        awaitBarrier(barrier);

                        try {
                          RoundRobinEntry reserved =
                              roundRobinService.reserveTournamentEntry(
                                  txSeason,
                                  entry.getId(),
                                  txPlayer1,
                                  txPlayer2,
                                  txPlayer3,
                                  txPlayer4);

                          Match saved =
                              matchRepository.saveAndFlush(
                                  newMatch(
                                      txSeason, txAdmin, txPlayer1, txPlayer2, txPlayer3, txPlayer4,
                                      21, 13));
                          roundRobinService.linkEntryToMatch(reserved, saved.getId());
                          secondMatchId.set(saved.getId());
                        } catch (RoundRobinModificationException ex) {
                          secondFailureMessage.set(ex.getMessage());
                          status.setRollbackOnly();
                        }
                      });
                  return null;
                } catch (Throwable t) {
                  return t;
                }
              });

      Throwable firstFailure = firstFuture.get(10, TimeUnit.SECONDS);
      Throwable secondFailure = secondFuture.get(10, TimeUnit.SECONDS);

      assertThat(firstFailure).isNull();
      assertThat(secondFailure).isNull();
      assertThat(firstMatchId.get()).isNotNull();
      assertThat(secondMatchId.get()).isNull();
      assertThat(secondFailureMessage.get()).contains("already has a logged result");

      LadderSeason reloadedSeason = seasonRepository.findById(season.getId()).orElseThrow();
      RoundRobinEntry reloadedEntry =
          roundRobinEntryRepository.findById(entry.getId()).orElseThrow();

      assertThat(matchRepository.findBySeason(reloadedSeason)).hasSize(1);
      assertThat(reloadedEntry.getMatchId()).isEqualTo(firstMatchId.get());
    } finally {
      executor.shutdownNow();
    }
  }

  private User saveUser(String email, String nickName) {
    User user = new User();
    user.setEmail(email);
    user.setNickName(nickName);
    user.setPassword("pw");
    return userRepository.save(user);
  }

  private Match newMatch(
      LadderSeason season,
      User loggedBy,
      User a1,
      User a2,
      User b1,
      User b2,
      int scoreA,
      int scoreB) {
    Match match = new Match();
    match.setSeason(season);
    match.setLoggedBy(loggedBy);
    match.setA1(a1);
    match.setA2(a2);
    match.setB1(b1);
    match.setB2(b2);
    match.setScoreA(scoreA);
    match.setScoreB(scoreB);
    match.setState(MatchState.PROVISIONAL);
    return match;
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while coordinating concurrent reservation test.", ex);
    } catch (BrokenBarrierException | TimeoutException ex) {
      throw new IllegalStateException("Failed to coordinate concurrent reservation test.", ex);
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while holding tournament reservation lock.", ex);
    }
  }
}
