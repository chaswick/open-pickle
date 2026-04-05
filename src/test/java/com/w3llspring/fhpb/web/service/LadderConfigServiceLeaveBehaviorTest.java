package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationService;
import com.w3llspring.fhpb.web.service.competition.SessionLifecycleService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LadderConfigServiceLeaveBehaviorTest {

  @Mock private LadderConfigRepository configRepo;
  @Mock private LadderSeasonRepository seasonRepo;
  @Mock private LadderMembershipRepository membershipRepo;
  @Mock private UserRepository userRepo;
  @Mock private RoundRobinService roundRobinService;

  private LadderInviteGenerator inviteGenerator;
  private SeasonNameGenerator seasonNameGenerator;

  private LadderConfigService service;
  private GroupAdministrationService groupAdministrationService;
  private SessionLifecycleService sessionLifecycleService;

  @BeforeEach
  void setUp() {
    inviteGenerator =
        new LadderInviteGenerator() {
          @Override
          public String generate() {
            return "test-invite";
          }
        };
    seasonNameGenerator = new SeasonNameGenerator();

    service =
        new LadderConfigService(
            configRepo,
            seasonRepo,
            membershipRepo,
            inviteGenerator,
            10,
            userRepo,
            seasonNameGenerator);
    sessionLifecycleService = new SessionLifecycleService(configRepo, membershipRepo, roundRobinService);
    groupAdministrationService =
        new GroupAdministrationService(
            configRepo,
            membershipRepo,
            inviteGenerator,
            userRepo,
            sessionLifecycleService,
            20,
            30L,
            "admin@test.com");
  }

  @Test
  void generateUniqueInviteCodeRetriesWhenGeneratorHitsExistingCode() {
    AtomicInteger calls = new AtomicInteger();
    inviteGenerator =
        new LadderInviteGenerator() {
          @Override
          public String generate() {
            return calls.getAndIncrement() == 0 ? "duplicate-invite" : "fresh-invite";
          }
        };
    service =
        new LadderConfigService(
            configRepo,
            seasonRepo,
            membershipRepo,
            inviteGenerator,
            10,
            userRepo,
            seasonNameGenerator);

    LadderConfig existing = new LadderConfig();
    existing.setInviteCode("DUPLICATE-INVITE");
    when(configRepo.findByInviteCode("DUPLICATE-INVITE")).thenReturn(Optional.of(existing));
    when(configRepo.findByInviteCode("FRESH-INVITE")).thenReturn(Optional.empty());

    assertThat(service.generateUniqueInviteCode()).isEqualTo("FRESH-INVITE");
  }

  @Test
  void ownerLeavingMarksLadderPendingDeletion() {
    Long configId = 10L;
    Long ownerId = 100L;
    Long membershipId = 500L;

    LadderConfig cfg = new LadderConfig();
    cfg.setId(configId);
    cfg.setOwnerUserId(ownerId);

    LadderMembership ownerMembership = new LadderMembership();
    ownerMembership.setId(membershipId);
    ownerMembership.setLadderConfig(cfg);
    ownerMembership.setUserId(ownerId);
    ownerMembership.setRole(LadderMembership.Role.ADMIN);
    ownerMembership.setState(LadderMembership.State.ACTIVE);

    when(membershipRepo.findById(membershipId)).thenReturn(Optional.of(ownerMembership));

    groupAdministrationService.leaveMember(configId, ownerId, membershipId);

    verify(membershipRepo).save(ownerMembership);
    verify(configRepo).save(cfg);
    assertThat(ownerMembership.getState()).isEqualTo(LadderMembership.State.LEFT);
    assertThat(ownerMembership.getLeftAt()).isNotNull();
    assertThat(cfg.isPendingDeletion()).isTrue();
    assertThat(cfg.getPendingDeletionByUserId()).isEqualTo(ownerId);
    assertThat(cfg.getPendingDeletionAt()).isNotNull();
  }

  @Test
  void nonOwnerLeavingDoesNotMarkPendingDeletion() {
    Long configId = 11L;
    Long ownerId = 101L;
    Long memberId = 201L;
    Long membershipId = 501L;

    LadderConfig cfg = new LadderConfig();
    cfg.setId(configId);
    cfg.setOwnerUserId(ownerId);

    LadderMembership member = new LadderMembership();
    member.setId(membershipId);
    member.setLadderConfig(cfg);
    member.setUserId(memberId);
    member.setRole(LadderMembership.Role.ADMIN);
    member.setState(LadderMembership.State.ACTIVE);

    when(membershipRepo.findById(membershipId)).thenReturn(Optional.of(member));

    groupAdministrationService.leaveMember(configId, memberId, membershipId);

    verify(membershipRepo).save(member);
    verify(configRepo, never()).save(cfg);
    assertThat(member.getState()).isEqualTo(LadderMembership.State.LEFT);
    assertThat(member.getLeftAt()).isNotNull();
    assertThat(cfg.isPendingDeletion()).isFalse();
  }

  @Test
  void sessionOwnerLeavingArchivesSessionUsingCleanupBehavior() {
    Long configId = 14L;
    Long ownerId = 501L;
    Long membershipId = 801L;
    Long otherMembershipId = 802L;
    Long otherUserId = 777L;

    LadderConfig cfg = new LadderConfig();
    cfg.setId(configId);
    cfg.setOwnerUserId(ownerId);
    cfg.setType(LadderConfig.Type.SESSION);
    cfg.setInviteCode("MINT-COURT-42");
    cfg.setStatus(LadderConfig.Status.ACTIVE);

    LadderMembership ownerMembership = new LadderMembership();
    ownerMembership.setId(membershipId);
    ownerMembership.setLadderConfig(cfg);
    ownerMembership.setUserId(ownerId);
    ownerMembership.setRole(LadderMembership.Role.ADMIN);
    ownerMembership.setState(LadderMembership.State.ACTIVE);

    LadderMembership otherMembership = new LadderMembership();
    otherMembership.setId(otherMembershipId);
    otherMembership.setLadderConfig(cfg);
    otherMembership.setUserId(otherUserId);
    otherMembership.setRole(LadderMembership.Role.MEMBER);
    otherMembership.setState(LadderMembership.State.ACTIVE);

    when(membershipRepo.findById(membershipId)).thenReturn(Optional.of(ownerMembership));
    when(configRepo.lockById(configId)).thenReturn(cfg);
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            configId, LadderMembership.State.ACTIVE))
        .thenReturn(java.util.List.of(ownerMembership, otherMembership));

    groupAdministrationService.leaveMember(configId, ownerId, membershipId);

    verify(membershipRepo).save(ownerMembership);
    verify(membershipRepo).save(otherMembership);
    verify(configRepo).save(cfg);
    assertThat(ownerMembership.getState()).isEqualTo(LadderMembership.State.LEFT);
    assertThat(ownerMembership.getLeftAt()).isNotNull();
    assertThat(otherMembership.getState()).isEqualTo(LadderMembership.State.LEFT);
    assertThat(otherMembership.getLeftAt()).isNotNull();
    assertThat(cfg.isPendingDeletion()).isFalse();
    assertThat(cfg.getStatus()).isEqualTo(LadderConfig.Status.ARCHIVED);
    assertThat(cfg.getInviteCode()).isNull();
  }

  @Test
  void banMember_onSessionRemovesPlayerInsteadOfBanning() {
    Long configId = 12L;
    Long adminId = 301L;
    Long memberId = 302L;
    Long adminMembershipId = 601L;
    Long memberMembershipId = 602L;

    LadderConfig cfg = new LadderConfig();
    cfg.setId(configId);
    cfg.setOwnerUserId(adminId);
    cfg.setType(LadderConfig.Type.SESSION);

    LadderMembership adminMembership = new LadderMembership();
    adminMembership.setId(adminMembershipId);
    adminMembership.setLadderConfig(cfg);
    adminMembership.setUserId(adminId);
    adminMembership.setRole(LadderMembership.Role.ADMIN);
    adminMembership.setState(LadderMembership.State.ACTIVE);

    LadderMembership memberMembership = new LadderMembership();
    memberMembership.setId(memberMembershipId);
    memberMembership.setLadderConfig(cfg);
    memberMembership.setUserId(memberId);
    memberMembership.setRole(LadderMembership.Role.MEMBER);
    memberMembership.setState(LadderMembership.State.ACTIVE);

    when(membershipRepo.findByLadderConfigIdAndUserId(configId, adminId))
        .thenReturn(Optional.of(adminMembership));
    when(configRepo.findById(configId)).thenReturn(Optional.of(cfg));
    when(membershipRepo.findById(memberMembershipId)).thenReturn(Optional.of(memberMembership));

    groupAdministrationService.banMember(configId, adminId, memberMembershipId);

    verify(membershipRepo).save(memberMembership);
    assertThat(memberMembership.getState()).isEqualTo(LadderMembership.State.LEFT);
    assertThat(memberMembership.getLeftAt()).isNotNull();
  }

  @Test
  void banMember_onStandardLadderStillMarksBanned() {
    Long configId = 13L;
    Long adminId = 401L;
    Long memberId = 402L;
    Long adminMembershipId = 701L;
    Long memberMembershipId = 702L;

    LadderConfig cfg = new LadderConfig();
    cfg.setId(configId);
    cfg.setOwnerUserId(adminId);
    cfg.setType(LadderConfig.Type.STANDARD);

    LadderMembership adminMembership = new LadderMembership();
    adminMembership.setId(adminMembershipId);
    adminMembership.setLadderConfig(cfg);
    adminMembership.setUserId(adminId);
    adminMembership.setRole(LadderMembership.Role.ADMIN);
    adminMembership.setState(LadderMembership.State.ACTIVE);

    LadderMembership memberMembership = new LadderMembership();
    memberMembership.setId(memberMembershipId);
    memberMembership.setLadderConfig(cfg);
    memberMembership.setUserId(memberId);
    memberMembership.setRole(LadderMembership.Role.MEMBER);
    memberMembership.setState(LadderMembership.State.ACTIVE);

    when(membershipRepo.findByLadderConfigIdAndUserId(configId, adminId))
        .thenReturn(Optional.of(adminMembership));
    when(configRepo.findById(configId)).thenReturn(Optional.of(cfg));
    when(membershipRepo.findById(memberMembershipId)).thenReturn(Optional.of(memberMembership));

    groupAdministrationService.banMember(configId, adminId, memberMembershipId);

    verify(membershipRepo).save(memberMembership);
    assertThat(memberMembership.getState()).isEqualTo(LadderMembership.State.BANNED);
    assertThat(memberMembership.getLeftAt()).isNotNull();
  }

  @Test
  void banMember_onCompetitionRequiresConfiguredSiteWideAdmin() {
    Long configId = 15L;
    Long adminId = 501L;
    Long memberId = 502L;
    Long memberMembershipId = 802L;

    LadderConfig cfg = new LadderConfig();
    cfg.setId(configId);
    cfg.setOwnerUserId(999L);
    cfg.setType(LadderConfig.Type.COMPETITION);

    User nonSiteWideAdmin = new User();
    nonSiteWideAdmin.setId(adminId);
    nonSiteWideAdmin.setEmail("other-admin@test.com");

    LadderMembership memberMembership = new LadderMembership();
    memberMembership.setId(memberMembershipId);
    memberMembership.setLadderConfig(cfg);
    memberMembership.setUserId(memberId);
    memberMembership.setRole(LadderMembership.Role.MEMBER);
    memberMembership.setState(LadderMembership.State.ACTIVE);

    when(configRepo.findById(configId)).thenReturn(Optional.of(cfg));
    when(userRepo.findById(adminId)).thenReturn(Optional.of(nonSiteWideAdmin));

    assertThatThrownBy(
            () -> groupAdministrationService.banMember(configId, adminId, memberMembershipId))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("site-wide admin");

    verify(membershipRepo, never()).findById(memberMembershipId);
    verify(membershipRepo, never()).save(memberMembership);
  }

  @Test
  void createRollingGroup_startsImmediatelyUsingUtcNow() {
    Instant before = Instant.now();
    LocalDate requestedFutureStart = LocalDate.now(ZoneOffset.UTC).plusDays(2);

    when(configRepo.countByOwnerUserIdAndTypeNot(42L, LadderConfig.Type.SESSION)).thenReturn(0L);
    when(configRepo.findByInviteCode(any(String.class))).thenReturn(Optional.empty());
    when(configRepo.save(any(LadderConfig.class)))
        .thenAnswer(
            invocation -> {
              LadderConfig cfg = invocation.getArgument(0);
              cfg.setId(123L);
              return cfg;
            });
    when(seasonRepo.save(any(LadderSeason.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.createConfigAndSeason(
        42L,
        "UTC Rolling",
        requestedFutureStart,
        requestedFutureStart.plusWeeks(6),
        null,
        LadderConfig.Mode.ROLLING,
        6,
        LadderConfig.CadenceUnit.WEEKS,
        LadderSecurity.STANDARD,
        false,
        false);

    Instant after = Instant.now();

    org.mockito.ArgumentCaptor<LadderSeason> seasonCaptor =
        org.mockito.ArgumentCaptor.forClass(LadderSeason.class);
    verify(seasonRepo).save(seasonCaptor.capture());
    LadderSeason savedSeason = seasonCaptor.getValue();

    assertThat(savedSeason.getState()).isEqualTo(LadderSeason.State.ACTIVE);
    assertThat(savedSeason.getStartedAt()).isBetween(before, after);
    assertThat(savedSeason.getStartDate())
        .isEqualTo(LocalDate.ofInstant(savedSeason.getStartedAt(), ZoneOffset.UTC));
    assertThat(savedSeason.getStartDate()).isBefore(requestedFutureStart);
    assertThat(savedSeason.getName()).isNotBlank();
  }

  @Test
  void createManualGroup_startsImmediatelyUsingUtcNow() {
    Instant before = Instant.now();
    LocalDate requestedFutureStart = LocalDate.now(ZoneOffset.UTC).plusDays(3);
    LocalDate requestedFutureEnd = requestedFutureStart.plusWeeks(8);

    when(configRepo.countByOwnerUserIdAndTypeNot(42L, LadderConfig.Type.SESSION)).thenReturn(0L);
    when(configRepo.findByInviteCode(any(String.class))).thenReturn(Optional.empty());
    when(configRepo.save(any(LadderConfig.class)))
        .thenAnswer(
            invocation -> {
              LadderConfig cfg = invocation.getArgument(0);
              cfg.setId(123L);
              return cfg;
            });
    when(seasonRepo.save(any(LadderSeason.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.createConfigAndSeason(
        42L,
        "UTC Manual",
        requestedFutureStart,
        requestedFutureEnd,
        null,
        LadderConfig.Mode.MANUAL,
        6,
        LadderConfig.CadenceUnit.WEEKS,
        LadderSecurity.STANDARD,
        false,
        false);

    Instant after = Instant.now();

    org.mockito.ArgumentCaptor<LadderSeason> seasonCaptor =
        org.mockito.ArgumentCaptor.forClass(LadderSeason.class);
    verify(seasonRepo).save(seasonCaptor.capture());
    LadderSeason savedSeason = seasonCaptor.getValue();

    assertThat(savedSeason.getState()).isEqualTo(LadderSeason.State.ACTIVE);
    assertThat(savedSeason.getStartedAt()).isBetween(before, after);
    assertThat(savedSeason.getStartDate())
        .isEqualTo(LocalDate.ofInstant(savedSeason.getStartedAt(), ZoneOffset.UTC));
    assertThat(savedSeason.getStartDate()).isBefore(requestedFutureStart);
    assertThat(savedSeason.getEndDate()).isEqualTo(requestedFutureEnd);
    assertThat(savedSeason.getName()).isNotBlank();
  }

  @Test
  void createSessionConfig_createsTemporarySessionWithoutOwningSeason() {
    LadderSeason competitionSeason = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(competitionSeason, "id", 77L);

    when(configRepo.countByOwnerUserIdAndTypeAndStatusAndExpiresAtAfter(
            any(Long.class),
            any(LadderConfig.Type.class),
            any(LadderConfig.Status.class),
            any(Instant.class)))
        .thenReturn(0L);
    when(configRepo.findByInviteCode(any(String.class))).thenReturn(Optional.empty());
    when(configRepo.save(any(LadderConfig.class)))
        .thenAnswer(
            invocation -> {
              LadderConfig cfg = invocation.getArgument(0);
              cfg.setId(222L);
              return cfg;
            });

    LadderConfig created = service.createSessionConfig(42L, "Saturday Session", competitionSeason);

    assertThat(created.getId()).isEqualTo(222L);
    assertThat(created.getType()).isEqualTo(LadderConfig.Type.SESSION);
    assertThat(created.getTargetSeasonId()).isEqualTo(77L);
    assertThat(created.getExpiresAt()).isAfter(Instant.now());
    verify(seasonRepo, never()).save(any(LadderSeason.class));
    verify(membershipRepo).save(any(LadderMembership.class));
  }

  @Test
  void createSessionConfig_autoNamesSessionWhenTitleMissing() {
    LadderSeason competitionSeason = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(competitionSeason, "id", 77L);

    User owner = new User();
    owner.setId(42L);
    owner.setNickName("Tester");

    when(userRepo.findById(42L)).thenReturn(Optional.of(owner));
    when(configRepo.countByOwnerUserIdAndTypeAndStatusAndExpiresAtAfter(
            any(Long.class),
            any(LadderConfig.Type.class),
            any(LadderConfig.Status.class),
            any(Instant.class)))
        .thenReturn(0L);
    when(configRepo.findByInviteCode(any(String.class))).thenReturn(Optional.empty());
    when(configRepo.save(any(LadderConfig.class)))
        .thenAnswer(
            invocation -> {
              LadderConfig cfg = invocation.getArgument(0);
              cfg.setId(333L);
              return cfg;
            });

    LadderConfig created = service.createSessionConfig(42L, "   ", competitionSeason);

    assertThat(created.getTitle()).isEqualTo("Tester's Session");
  }

  @Test
  void createSessionConfig_rejectsWhenUserHasCreatedTooManySessionsInLast24Hours() {
    LadderSeason competitionSeason = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(competitionSeason, "id", 77L);

    when(configRepo.countByOwnerUserIdAndTypeAndStatusAndExpiresAtAfter(
            any(Long.class),
            any(LadderConfig.Type.class),
            any(LadderConfig.Status.class),
            any(Instant.class)))
        .thenReturn(0L);
    when(configRepo.countByOwnerUserIdAndTypeAndCreatedAtAfter(
            any(Long.class), any(LadderConfig.Type.class), any(Instant.class)))
        .thenReturn(10L);

    assertThatThrownBy(() -> service.createSessionConfig(42L, "Saturday Session", competitionSeason))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("You can start at most 10 sessions in 24 hours");

    verify(configRepo, never()).save(any(LadderConfig.class));
    verify(membershipRepo, never()).save(any(LadderMembership.class));
  }

  @Test
  void regenInviteCode_rejectsWhenInviteCooldownIsActive() {
    Long configId = 16L;
    Long requesterId = 601L;

    LadderConfig cfg = new LadderConfig();
    cfg.setId(configId);
    cfg.setOwnerUserId(requesterId);
    cfg.setInviteCode("LIVE-INVITE");
    cfg.setLastInviteChangeAt(Instant.now().minusSeconds(5));

    LadderMembership requesterMembership = new LadderMembership();
    requesterMembership.setId(901L);
    requesterMembership.setLadderConfig(cfg);
    requesterMembership.setUserId(requesterId);
    requesterMembership.setRole(LadderMembership.Role.ADMIN);
    requesterMembership.setState(LadderMembership.State.ACTIVE);

    when(configRepo.lockById(configId)).thenReturn(cfg);
    when(membershipRepo.findByLadderConfigIdAndUserId(configId, requesterId))
        .thenReturn(Optional.of(requesterMembership));

    assertThatThrownBy(() -> groupAdministrationService.regenInviteCode(configId, requesterId))
        .isInstanceOf(InviteChangeCooldownException.class)
        .hasMessageContaining("Invite changes are on cooldown");

    verify(configRepo, never()).save(cfg);
  }

  @Test
  void disableInviteCode_clearsInviteAndTracksChangeTime() {
    Long configId = 17L;
    Long requesterId = 701L;

    LadderConfig cfg = new LadderConfig();
    cfg.setId(configId);
    cfg.setOwnerUserId(requesterId);
    cfg.setInviteCode("LIVE-INVITE");

    LadderMembership requesterMembership = new LadderMembership();
    requesterMembership.setId(902L);
    requesterMembership.setLadderConfig(cfg);
    requesterMembership.setUserId(requesterId);
    requesterMembership.setRole(LadderMembership.Role.ADMIN);
    requesterMembership.setState(LadderMembership.State.ACTIVE);

    when(configRepo.lockById(configId)).thenReturn(cfg);
    when(membershipRepo.findByLadderConfigIdAndUserId(configId, requesterId))
        .thenReturn(Optional.of(requesterMembership));

    groupAdministrationService.disableInviteCode(configId, requesterId);

    verify(configRepo).save(cfg);
    assertThat(cfg.getInviteCode()).isNull();
    assertThat(cfg.getLastInviteChangeAt()).isNotNull();
  }

  @Test
  void regenInviteCode_allowsImmediateReenableAfterManualDisable() {
    Long configId = 18L;
    Long requesterId = 702L;

    LadderConfig cfg = new LadderConfig();
    cfg.setId(configId);
    cfg.setOwnerUserId(requesterId);
    cfg.setInviteCode("LIVE-INVITE");
    cfg.setLastInviteChangeAt(Instant.now().minusSeconds(60));

    LadderMembership requesterMembership = new LadderMembership();
    requesterMembership.setId(903L);
    requesterMembership.setLadderConfig(cfg);
    requesterMembership.setUserId(requesterId);
    requesterMembership.setRole(LadderMembership.Role.ADMIN);
    requesterMembership.setState(LadderMembership.State.ACTIVE);

    when(configRepo.lockById(configId)).thenReturn(cfg);
    when(membershipRepo.findByLadderConfigIdAndUserId(configId, requesterId))
        .thenReturn(Optional.of(requesterMembership));
    when(configRepo.findByInviteCode("TEST-INVITE")).thenReturn(Optional.empty());

    groupAdministrationService.disableInviteCode(configId, requesterId);
    groupAdministrationService.regenInviteCode(configId, requesterId);

    assertThat(cfg.getInviteCode()).isEqualTo("TEST-INVITE");
    verify(configRepo, times(2)).save(cfg);
  }

  @Test
  void demoteFromAdmin_rejectsRemovingLastActiveAdmin() {
    Long configId = 19L;
    Long requesterId = 801L;
    Long targetMembershipId = 903L;

    LadderConfig cfg = new LadderConfig();
    cfg.setId(configId);
    cfg.setOwnerUserId(999L);
    cfg.setType(LadderConfig.Type.STANDARD);

    LadderMembership requesterMembership = new LadderMembership();
    requesterMembership.setId(904L);
    requesterMembership.setLadderConfig(cfg);
    requesterMembership.setUserId(requesterId);
    requesterMembership.setRole(LadderMembership.Role.ADMIN);
    requesterMembership.setState(LadderMembership.State.ACTIVE);

    LadderMembership targetMembership = new LadderMembership();
    targetMembership.setId(targetMembershipId);
    targetMembership.setLadderConfig(cfg);
    targetMembership.setUserId(802L);
    targetMembership.setRole(LadderMembership.Role.ADMIN);
    targetMembership.setState(LadderMembership.State.ACTIVE);

    when(configRepo.lockById(configId)).thenReturn(cfg);
    when(membershipRepo.findByLadderConfigIdAndUserId(configId, requesterId))
        .thenReturn(Optional.of(requesterMembership));
    when(membershipRepo.findById(targetMembershipId)).thenReturn(Optional.of(targetMembership));
    when(membershipRepo.countByLadderConfigIdAndRoleAndState(
            configId, LadderMembership.Role.ADMIN, LadderMembership.State.ACTIVE))
        .thenReturn(1L);

    assertThatThrownBy(
            () ->
                groupAdministrationService.demoteFromAdmin(
                    configId, requesterId, targetMembershipId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("At least one active admin is required");

    verify(membershipRepo, never()).save(targetMembership);
  }
}
