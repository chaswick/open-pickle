package com.w3llspring.fhpb.web.service.competition;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.service.LadderInviteGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupAdministrationServiceTest {

  @Mock private LadderConfigRepository configs;
  @Mock private LadderMembershipRepository memberships;
  @Mock private LadderInviteGenerator generator;
  @Mock private UserRepository userRepo;
  @Mock private SessionLifecycleService sessionLifecycleService;

  @Test
  void requireActiveMember_acceptsUserPresentInActiveRoster() {
    GroupAdministrationService service =
        new GroupAdministrationService(
            configs,
            memberships,
            generator,
            userRepo,
            sessionLifecycleService,
            20,
            30L,
            "");

    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);

    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(cfg);
    membership.setUserId(7L);
    membership.setState(LadderMembership.State.ACTIVE);

    when(memberships.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership));

    assertThatCode(() -> service.requireActiveMember(42L, 7L)).doesNotThrowAnyException();
  }

  @Test
  void requireActiveMember_rejectsUserMissingFromActiveRoster() {
    GroupAdministrationService service =
        new GroupAdministrationService(
            configs,
            memberships,
            generator,
            userRepo,
            sessionLifecycleService,
            20,
            30L,
            "");

    when(memberships.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.requireActiveMember(42L, 7L))
        .isInstanceOf(SecurityException.class)
        .hasMessage("Not active in ladder");
  }
}
