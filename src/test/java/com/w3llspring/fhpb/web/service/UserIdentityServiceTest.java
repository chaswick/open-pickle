package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserDisplayNameAuditRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserDisplayNameAudit;
import com.w3llspring.fhpb.web.service.user.UserIdentityService;
import com.w3llspring.fhpb.web.service.user.UserIdentityService.DisplayNameChangeStatus;
import com.w3llspring.fhpb.web.service.user.UserPublicCodeGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserIdentityServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private LadderMembershipRepository ladderMembershipRepository;

  @Mock private UserDisplayNameAuditRepository userDisplayNameAuditRepository;

  private RecordingLadderV2Service ladderV2Service;
  private RecordingCompetitionDisplayNameModerationService competitionDisplayNameModerationService;

  private UserIdentityService service;

  @BeforeEach
  void setUp() {
    ladderV2Service = new RecordingLadderV2Service();
    competitionDisplayNameModerationService =
        new RecordingCompetitionDisplayNameModerationService();
    service =
        new UserIdentityService(
            userRepository,
            ladderMembershipRepository,
            userDisplayNameAuditRepository,
            ladderV2Service);
    ReflectionTestUtils.setField(
        service,
        "competitionDisplayNameModerationService",
        competitionDisplayNameModerationService);
  }

  @Test
  void changeDisplayNameWritesLadderScopedAuditRows() {
    User user = new User();
    user.setId(11L);
    user.setNickName("OldName");

    Instant changedAt = Instant.parse("2026-03-13T15:00:00Z");
    when(userRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(user));
    when(userRepository.findByNickName("NewName")).thenReturn(null);
    when(ladderMembershipRepository.findByUserIdAndState(11L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership(101L), membership(202L)));

    var result = service.changeDisplayName(11L, "NewName", 11L, changedAt, Duration.ofHours(24));

    assertThat(result.status()).isEqualTo(DisplayNameChangeStatus.CHANGED);
    assertThat(result.user()).isSameAs(user);
    verify(userRepository).saveAndFlush(user);
    assertThat(ladderV2Service.refreshedUser).isSameAs(user);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<UserDisplayNameAudit>> captor = ArgumentCaptor.forClass(List.class);
    verify(userDisplayNameAuditRepository).saveAll(captor.capture());

    List<UserDisplayNameAudit> audits = captor.getValue();
    assertThat(audits).hasSize(2);
    assertThat(audits)
        .extracting(UserDisplayNameAudit::getLadderConfigId)
        .containsExactly(101L, 202L);
    assertThat(audits).extracting(UserDisplayNameAudit::getOldDisplayName).containsOnly("OldName");
    assertThat(audits).extracting(UserDisplayNameAudit::getNewDisplayName).containsOnly("NewName");
    assertThat(audits).extracting(UserDisplayNameAudit::getChangedAt).containsOnly(changedAt);
    assertThat(user.getNickName()).isEqualTo("NewName");
    assertThat(user.getLastDisplayNameChangeAt()).isEqualTo(changedAt);
    assertThat(competitionDisplayNameModerationService.clearedUser).isSameAs(user);
    assertThat(competitionDisplayNameModerationService.oldDisplayName).isEqualTo("OldName");
    assertThat(competitionDisplayNameModerationService.newDisplayName).isEqualTo("NewName");
  }

  @Test
  void changeDisplayNameReturnsCooldownWhenUserRecentlyChangedName() {
    User user = new User();
    user.setId(11L);
    user.setNickName("OldName");
    user.setLastDisplayNameChangeAt(Instant.parse("2026-03-13T14:45:00Z"));

    Instant changedAt = Instant.parse("2026-03-13T15:00:00Z");
    when(userRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(user));

    var result = service.changeDisplayName(11L, "NewName", 11L, changedAt, Duration.ofHours(1));

    assertThat(result.status()).isEqualTo(DisplayNameChangeStatus.COOLDOWN);
    assertThat(result.allowedAt()).isEqualTo(Instant.parse("2026-03-13T15:45:00Z"));
    verify(userRepository, never()).saveAndFlush(any(User.class));
    verify(userDisplayNameAuditRepository, never()).save(any(UserDisplayNameAudit.class));
    verify(userDisplayNameAuditRepository, never()).saveAll(any());
  }

  @Test
  void changeDisplayNameReturnsTakenWhenAnotherUserAlreadyUsesDesiredName() {
    User user = new User();
    user.setId(11L);
    user.setNickName("OldName");

    User conflictingUser = new User();
    conflictingUser.setId(22L);
    conflictingUser.setNickName("NewName");

    Instant changedAt = Instant.parse("2026-03-13T15:00:00Z");
    when(userRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(user));
    when(userRepository.findByNickName("NewName")).thenReturn(conflictingUser);

    var result = service.changeDisplayName(11L, "NewName", 11L, changedAt, Duration.ofHours(1));

    assertThat(result.status()).isEqualTo(DisplayNameChangeStatus.TAKEN);
    verify(userRepository, never()).saveAndFlush(any(User.class));
    verify(ladderMembershipRepository, never()).findByUserIdAndState(anyLong(), any());
  }

  @Test
  void backfillMissingPublicCodesAssignsCodesAndReturnsCount() {
    User first = new User();
    first.setId(1L);
    first.setNickName("First");

    User second = new User();
    second.setId(2L);
    second.setNickName("Second");

    when(userRepository.findByPublicCodeIsNull(PageRequest.of(0, 100)))
        .thenReturn(List.of(first, second), List.of());
    when(userRepository.existsByPublicCode(any())).thenReturn(false);

    int assigned = service.backfillMissingPublicCodes();

    assertThat(assigned).isEqualTo(2);
    assertThat(UserPublicCodeGenerator.isCurrentFormat(first.getPublicCode())).isTrue();
    assertThat(UserPublicCodeGenerator.isCurrentFormat(second.getPublicCode())).isTrue();
    assertThat(first.getPublicCode()).isNotEqualTo(second.getPublicCode());
    verify(userRepository).saveAll(any());
    verify(userRepository).flush();
  }

  private LadderMembership membership(Long ladderId) {
    LadderConfig ladderConfig = new LadderConfig();
    ladderConfig.setId(ladderId);

    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(ladderConfig);
    membership.setState(LadderMembership.State.ACTIVE);
    return membership;
  }

  private static final class RecordingLadderV2Service extends LadderV2Service {
    private User refreshedUser;

    private RecordingLadderV2Service() {
      super(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          new com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms(
              java.util.List.of(
                  new com.w3llspring.fhpb.web.service.scoring.MarginCurveV1LadderScoringAlgorithm(),
                  new com.w3llspring.fhpb.web.service.scoring.BalancedV1LadderScoringAlgorithm())),
          null,
          null,
          null,
          null);
    }

    @Override
    public void refreshDisplayNameArtifacts(User user) {
      this.refreshedUser = user;
    }
  }

  private static final class RecordingCompetitionDisplayNameModerationService
      extends CompetitionDisplayNameModerationService {
    private User clearedUser;
    private String oldDisplayName;
    private String newDisplayName;

    private RecordingCompetitionDisplayNameModerationService() {
      super(null, null);
    }

    @Override
    public void clearOverrideIfSubstantialRename(
        User user, String oldDisplayName, String newDisplayName) {
      this.clearedUser = user;
      this.oldDisplayName = oldDisplayName;
      this.newDisplayName = newDisplayName;
    }
  }
}
