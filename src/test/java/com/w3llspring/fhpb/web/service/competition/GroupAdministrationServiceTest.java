package com.w3llspring.fhpb.web.service.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.service.LadderInviteGenerator;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupAdministrationServiceTest {

  @Mock private LadderConfigRepository configs;
  @Mock private LadderMembershipRepository memberships;
  @Mock private LadderInviteGenerator generator;
  @Mock private UserRepository userRepo;
  @Mock private SessionLifecycleService sessionLifecycleService;

  @ParameterizedTest
  @CsvSource({
    "SESSION, 20, false, true",
    "SESSION, 31, false, true",
    "SESSION, 31, true, true",
    "SESSION, 32, false, false",
    "SESSION, 32, true, false",
    "STANDARD, 19, false, true",
    "STANDARD, 20, false, false",
    "COMPETITION, 32, false, true"
  })
  void joiningUsesTheSessionLimitForNewAndReturningPlayers(
      LadderConfig.Type type, int activeCount, boolean returning, boolean allowed) {
    GroupAdministrationService service =
        new GroupAdministrationService(
            configs, memberships, generator, userRepo, sessionLifecycleService, 20, 30L, "");
    LadderConfig cfg = new LadderConfig();
    cfg.setId(42L);
    cfg.setType(type);
    cfg.setStatus(LadderConfig.Status.ACTIVE);
    when(configs.findByInviteCode(anyString())).thenReturn(Optional.of(cfg));
    when(configs.lockById(42L)).thenReturn(cfg);
    LadderMembership previous = new LadderMembership();
    previous.setLadderConfig(cfg);
    previous.setUserId(99L);
    previous.setState(LadderMembership.State.LEFT);
    when(memberships.findByLadderConfigIdAndUserId(42L, 99L))
        .thenReturn(returning ? Optional.of(previous) : Optional.empty());
    if (type != LadderConfig.Type.COMPETITION) {
      when(memberships.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              42L, LadderMembership.State.ACTIVE))
          .thenReturn(Collections.nCopies(activeCount, new LadderMembership()));
    }
    if (allowed) {
      assertThat(service.joinByInvite("friend-code", 99L)).isSameAs(cfg);
      ArgumentCaptor<LadderMembership> saved = ArgumentCaptor.forClass(LadderMembership.class);
      verify(memberships).save(saved.capture());
      assertThat(saved.getValue().getUserId()).isEqualTo(99L);
      assertThat(saved.getValue().getState()).isEqualTo(LadderMembership.State.ACTIVE);
      if (returning) assertThat(saved.getValue()).isSameAs(previous);
    } else {
      assertThatThrownBy(() -> service.joinByInvite("friend-code", 99L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Sorry, that group is full.");
      verify(memberships, never()).save(any());
    }
  }

  @Test
  void requireActiveMember_acceptsUserPresentInActiveRoster() {
    GroupAdministrationService service =
        new GroupAdministrationService(
            configs, memberships, generator, userRepo, sessionLifecycleService, 20, 30L, "");

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
            configs, memberships, generator, userRepo, sessionLifecycleService, 20, 30L, "");

    when(memberships.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.requireActiveMember(42L, 7L))
        .isInstanceOf(SecurityException.class)
        .hasMessage("Not active in ladder");
  }
}
