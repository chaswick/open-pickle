package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.CompetitionDisplayNameReportRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompetitionDisplayNameModerationServiceTest {

  @Mock private CompetitionDisplayNameReportRepository reportRepository;

  @Mock private UserRepository userRepository;

  private CompetitionDisplayNameModerationService service;

  @BeforeEach
  void setUp() {
    service = new CompetitionDisplayNameModerationService(reportRepository, userRepository);
    ReflectionTestUtils.setField(service, "reportThreshold", 3);
  }

  @Test
  void reportDisplayNameActivatesOverrideAtThreshold() {
    User reporter = user(1L, "Reporter", "PB-REP001");
    User target = user(2L, "Questionable", "PB-TAR456");

    when(reportRepository.existsByReporterUserIdAndTargetUserId(1L, 2L)).thenReturn(false);
    when(reportRepository.countByTargetUserId(2L)).thenReturn(3L);

    CompetitionDisplayNameModerationService.ReportOutcome outcome =
        service.reportDisplayName(reporter, target);

    assertThat(outcome)
        .isEqualTo(CompetitionDisplayNameModerationService.ReportOutcome.AUTO_HIDDEN);
    assertThat(target.isCompetitionSafeDisplayNameActive()).isTrue();
    assertThat(target.getCompetitionSafeDisplayName()).isEqualTo("Player TAR456");
    assertThat(target.getCompetitionSafeDisplayNameBasis()).isEqualTo("questionable");
    verify(reportRepository).save(org.mockito.ArgumentMatchers.any());
    verify(userRepository).save(target);
  }

  @Test
  void clearOverrideIfSubstantialRenameResetsOverrideAndReports() {
    User target = user(22L, "OldBadName", "PB-OLD022");
    target.setCompetitionSafeDisplayName("Player OLD022");
    target.setCompetitionSafeDisplayNameActive(true);
    target.setCompetitionSafeDisplayNameBasis("oldbadname");

    service.clearOverrideIfSubstantialRename(target, "OldBadName", "SunnyKitchen");

    assertThat(target.isCompetitionSafeDisplayNameActive()).isFalse();
    assertThat(target.getCompetitionSafeDisplayName()).isNull();
    assertThat(target.getCompetitionSafeDisplayNameBasis()).isNull();
    verify(userRepository).save(target);
    verify(reportRepository).deleteByTargetUserId(22L);
  }

  @Test
  void applyCompetitionDisplayNamesOnlyTouchesActiveOverrides() {
    User safeUser = user(7L, "OriginalName", "PB-SAFE77");
    safeUser.setCompetitionSafeDisplayName("Player SAFE77");
    safeUser.setCompetitionSafeDisplayNameActive(true);

    com.w3llspring.fhpb.web.model.LadderStanding standing =
        new com.w3llspring.fhpb.web.model.LadderStanding();
    standing.setUser(safeUser);

    LadderV2Service.LadderRow row = new LadderV2Service.LadderRow();
    row.userId = 7L;
    row.displayName = "OriginalName";

    service.applyCompetitionDisplayNames(List.of(row), List.of(standing));

    assertThat(row.displayName).isEqualTo("Player SAFE77");
    assertThat(row.competitionSafeDisplayNameActive).isTrue();
  }

  private User user(Long id, String nickName, String publicCode) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickName);
    user.setPublicCode(publicCode);
    return user;
  }
}
