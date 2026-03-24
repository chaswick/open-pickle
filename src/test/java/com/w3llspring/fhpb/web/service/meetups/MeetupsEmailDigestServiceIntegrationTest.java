package com.w3llspring.fhpb.web.service.meetups;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMeetupSlotRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMeetupSlot;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.email.EmailService;
import jakarta.mail.MessagingException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(
    properties = {
      "fhpb.features.meetups.enabled=true",
      "fhpb.meetups.email.link-secret=test-meetups-secret",
      "fhpb.public.base-url=https://app.example.com"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class MeetupsEmailDigestServiceIntegrationTest {

  @Autowired private MeetupsEmailDigestService digestService;

  @Autowired private UserRepository userRepository;

  @Autowired private LadderConfigRepository ladderConfigRepository;

  @Autowired private LadderMembershipRepository ladderMembershipRepository;

  @Autowired private LadderMeetupSlotRepository ladderMeetupSlotRepository;

  @Autowired private RecordingEmailService emailService;

  private User owner;
  private User recipient;
  private User secondCreator;
  private LadderConfig ladder;

  @BeforeEach
  void setUp() {
    emailService.reset();

    owner = saveUser("owner");
    recipient = saveUser("recipient");
    recipient.setMeetupsEmailOptIn(true);
    recipient.setMeetupsEmailPending(true);
    recipient = userRepository.saveAndFlush(recipient);

    secondCreator = saveUser("creator");

    ladder = new LadderConfig();
    ladder.setTitle("Meetup Ladder");
    ladder.setOwnerUserId(owner.getId());
    ladder = ladderConfigRepository.saveAndFlush(ladder);

    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(ladder);
    membership.setUserId(recipient.getId());
    membership.setState(LadderMembership.State.ACTIVE);
    ladderMembershipRepository.saveAndFlush(membership);

    createSlot(owner.getId(), Instant.now().plusSeconds(3600));
  }

  @AfterEach
  void tearDown() {
    emailService.releaseFirstSendIfBlocked();
  }

  @Test
  void concurrentSchedulerRunsOnlySendOneDigestAndIncrementCountersOnce() throws Exception {
    emailService.blockFirstSend();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();

    try {
      Future<?> firstFuture =
          executor.submit(
              () -> {
                try {
                  digestService.trySendPendingDigests();
                } catch (Throwable t) {
                  firstFailure.set(t);
                }
              });

      assertThat(emailService.awaitFirstSendStarted()).isTrue();

      Future<?> secondFuture =
          executor.submit(
              () -> {
                try {
                  digestService.trySendPendingDigests();
                } catch (Throwable t) {
                  secondFailure.set(t);
                }
              });

      sleepQuietly(250);
      emailService.releaseFirstSendIfBlocked();

      firstFuture.get(10, TimeUnit.SECONDS);
      secondFuture.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(firstFailure.get()).isNull();
    assertThat(secondFailure.get()).isNull();

    User reloaded = userRepository.findById(recipient.getId()).orElseThrow();
    assertThat(emailService.sentCount()).isEqualTo(1);
    assertThat(reloaded.isMeetupsEmailPending()).isFalse();
    assertThat(reloaded.getMeetupsEmailDailySentCount()).isEqualTo(1);
    assertThat(reloaded.getMeetupsEmailLastSentAt()).isNotNull();
  }

  @Test
  void markPendingDuringInFlightSendPreservesPendingFlagForNewMeetup() throws Exception {
    emailService.blockFirstSend();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicReference<Throwable> sendFailure = new AtomicReference<>();
    AtomicReference<Throwable> pendingFailure = new AtomicReference<>();

    try {
      Future<?> sendFuture =
          executor.submit(
              () -> {
                try {
                  digestService.trySendPendingDigests();
                } catch (Throwable t) {
                  sendFailure.set(t);
                }
              });

      assertThat(emailService.awaitFirstSendStarted()).isTrue();

      createSlot(secondCreator.getId(), Instant.now().plusSeconds(7200));

      Future<?> pendingFuture =
          executor.submit(
              () -> {
                try {
                  digestService.markPendingForLadderMembersExceptCreator(
                      ladder.getId(), secondCreator.getId());
                } catch (Throwable t) {
                  pendingFailure.set(t);
                }
              });

      sleepQuietly(250);
      emailService.releaseFirstSendIfBlocked();

      sendFuture.get(10, TimeUnit.SECONDS);
      pendingFuture.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(sendFailure.get()).isNull();
    assertThat(pendingFailure.get()).isNull();

    User reloaded = userRepository.findById(recipient.getId()).orElseThrow();
    assertThat(emailService.sentCount()).isEqualTo(1);
    assertThat(reloaded.isMeetupsEmailPending()).isTrue();
    assertThat(reloaded.getMeetupsEmailDailySentCount()).isEqualTo(1);
    assertThat(reloaded.getMeetupsEmailLastSentAt()).isNotNull();
  }

  private User saveUser(String prefix) {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    User user = new User();
    user.setEmail(prefix + "-" + suffix + "@test.local");
    user.setNickName(prefix + suffix);
    user.setPassword("pw");
    return userRepository.saveAndFlush(user);
  }

  private void createSlot(Long createdByUserId, Instant startsAt) {
    LadderMeetupSlot slot = new LadderMeetupSlot();
    slot.setLadderConfig(ladder);
    slot.setCreatedByUserId(createdByUserId);
    slot.setStartsAt(startsAt);
    slot.setCreatedAt(Instant.now());
    ladderMeetupSlotRepository.saveAndFlush(slot);
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while coordinating meetup digest race test.", ex);
    }
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    @Primary
    RecordingEmailService recordingEmailService() {
      return new RecordingEmailService();
    }
  }

  static class RecordingEmailService extends EmailService {
    private final AtomicInteger sentCount = new AtomicInteger();
    private volatile CountDownLatch firstSendStarted = new CountDownLatch(0);
    private volatile CountDownLatch releaseFirstSend = new CountDownLatch(0);
    private volatile boolean blockFirstSend;

    RecordingEmailService() {
      super(null);
    }

    void reset() {
      sentCount.set(0);
      firstSendStarted = new CountDownLatch(0);
      releaseFirstSend = new CountDownLatch(0);
      blockFirstSend = false;
    }

    void blockFirstSend() {
      sentCount.set(0);
      firstSendStarted = new CountDownLatch(1);
      releaseFirstSend = new CountDownLatch(1);
      blockFirstSend = true;
    }

    boolean awaitFirstSendStarted() {
      try {
        return firstSendStarted.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while waiting for first meetup email send.", ex);
      }
    }

    void releaseFirstSendIfBlocked() {
      releaseFirstSend.countDown();
    }

    int sentCount() {
      return sentCount.get();
    }

    @Override
    public void sendHtml(
        String to, String subject, String html, java.util.Map<String, String> headers)
        throws MessagingException {
      int current = sentCount.incrementAndGet();
      if (blockFirstSend && current == 1) {
        firstSendStarted.countDown();
        try {
          if (!releaseFirstSend.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out while holding meetup digest send.");
          }
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while holding meetup digest send.", ex);
        } finally {
          blockFirstSend = false;
        }
      }
    }
  }
}
