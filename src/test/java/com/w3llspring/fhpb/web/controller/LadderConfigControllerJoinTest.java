package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.competition.LadderConfigController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationOperations;
import com.w3llspring.fhpb.web.service.competition.SessionJoinRequestService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
class LadderConfigControllerJoinTest {

  @Mock private LadderConfigRepository configs;
  @Mock private LadderMembershipRepository membershipRepo;
  @Mock private GroupAdministrationOperations groupAdministration;
  @Mock private SessionJoinRequestService sessionJoinRequests;

  private LadderConfigController controller;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    controller =
        new LadderConfigController(
            null,
            null,
            null,
            groupAdministration,
            configs,
            null,
            membershipRepo,
            null,
            null,
            null,
            null,
            20);
    ReflectionTestUtils.setField(controller, "sessionJoinRequestService", sessionJoinRequests);
  }

  @Test
  void joinFormPrefillsInviteButDoesNotAutoJoin() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Practice Group");
    cfg.setType(LadderConfig.Type.STANDARD);
    when(configs.findByInviteCode("DINK-7")).thenReturn(Optional.of(cfg));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of());

    User user = new User();
    user.setId(7L);
    user.setNickName("Tester");
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.joinForm("dink-7", null, false, auth, model);

    assertThat(view).isEqualTo("auth/join");
    assertThat(model.get("prefillInviteCode")).isEqualTo("dink-7");
    verify(groupAdministration, never()).joinByInvite(anyString(), anyLong());
  }

  @Test
  void joinFormSessionContextSetsSafeDefaultsBeforeInviteLookup() {
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.joinForm(null, "/competition/sessions", false, null, model);

    assertThat(view).isEqualTo("auth/join");
    assertThat(model.get("returnToPath")).isEqualTo("/competition/sessions");
    assertThat(model.get("sessionJoinContext")).isEqualTo(Boolean.TRUE);
    assertThat(model.get("sessionApprovalMode")).isEqualTo(Boolean.FALSE);
    assertThat(model.get("inviteTargetTitle")).isNull();
    verify(configs, never()).findByInviteCode(anyString());
  }

  @Test
  void joinFormSessionContextPrefillsSegmentedCodePickerFields() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Open Session");
    cfg.setType(LadderConfig.Type.SESSION);
    when(configs.findByInviteCode(anyString()))
        .thenAnswer(
            invocation ->
                "MINT-COURT-42".equals(invocation.getArgument(0, String.class))
                    ? Optional.of(cfg)
                    : Optional.empty());

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.joinForm("mint court 42", "/competition/sessions", false, null, model);

    assertThat(view).isEqualTo("auth/join");
    assertThat(model.get("prefillInviteCodeWordOne")).isEqualTo("MINT");
    assertThat(model.get("prefillInviteCodeWordTwo")).isEqualTo("COURT");
    assertThat(model.get("prefillInviteCodeNumber")).isEqualTo("42");
    verify(configs).findByInviteCode("MINT-COURT-42");
  }

  @Test
  void joinFormAutoJoinsSessionInviteWhenRequested() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Open Session");
    cfg.setType(LadderConfig.Type.SESSION);
    when(configs.findByInviteCode("DINK-7")).thenReturn(Optional.of(cfg));
    when(groupAdministration.joinByInvite("DINK-7", 7L)).thenReturn(cfg);

    User user = new User();
    user.setId(7L);
    user.setNickName("Tester");
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.joinForm("dink-7", null, true, auth, model);

    assertThat(view).isEqualTo("redirect:/groups/42?joined=1");
    verify(groupAdministration).joinByInvite("DINK-7", 7L);
  }

  @Test
  void joinFormAutoJoinFlagDoesNotAutoJoinStandardInvite() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Practice Group");
    cfg.setType(LadderConfig.Type.STANDARD);
    when(configs.findByInviteCode("DINK-7")).thenReturn(Optional.of(cfg));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of());

    User user = new User();
    user.setId(7L);
    user.setNickName("Tester");
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.joinForm("dink-7", null, true, auth, model);

    assertThat(view).isEqualTo("auth/join");
    verify(groupAdministration, never()).joinByInvite(anyString(), anyLong());
  }

  @Test
  void joinRedirectsStandardInviteToPrivateGroupHub() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Practice Group");
    cfg.setType(LadderConfig.Type.STANDARD);
    when(configs.findByInviteCode("DINK-7")).thenReturn(Optional.of(cfg));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of());
    when(groupAdministration.joinByInvite("DINK-7", 7L)).thenReturn(cfg);

    User user = new User();
    user.setId(7L);
    user.setNickName("Tester");
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    String view = controller.join("dink-7", null, auth, new RedirectAttributesModelMap());

    assertThat(view).isEqualTo("redirect:/private-groups/42?joined=1");
    verify(groupAdministration).joinByInvite("DINK-7", 7L);
  }

  @Test
  void joinRedirectsSessionInviteToWaitingPageWhenApprovalIsRequired() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setTitle("Open Session");
    cfg.setType(LadderConfig.Type.SESSION);
    when(configs.findByInviteCode("DINK-7")).thenReturn(Optional.of(cfg));
    when(sessionJoinRequests.submitByInvite("DINK-7", 7L))
        .thenReturn(
            new SessionJoinRequestService.SubmissionOutcome(
                SessionJoinRequestService.SubmissionState.PENDING_APPROVAL, 42L, 99L));

    User user = new User();
    user.setId(7L);
    user.setNickName("Tester");
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    String view = controller.join("dink-7", "/competition/sessions", auth, new RedirectAttributesModelMap());

    assertThat(view).isEqualTo("redirect:/groups/join-requests/99");
    verify(sessionJoinRequests).submitByInvite("DINK-7", 7L);
    verify(groupAdministration, never()).joinByInvite(anyString(), anyLong());
  }
}
