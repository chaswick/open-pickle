package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LadderConfigServiceSessionLaunchTest {

  private LadderMembershipRepository memberships;
  private LadderConfigService service;

  @BeforeEach
  void setUp() {
    memberships = mock(LadderMembershipRepository.class);
    service = new LadderConfigService(null, null, memberships, null, 10, null, null);
  }

  @Test
  void findReusableSessionConfigPrefersOwnedSessionOverJoinedSession() {
    LadderConfig joinedSession = sessionConfig(20L, 999L, Instant.now().minusSeconds(30));
    LadderMembership joinedMembership = membership(joinedSession, 7L, Instant.now());

    LadderConfig ownedSession = sessionConfig(21L, 7L, Instant.now().minusSeconds(3600));
    LadderMembership ownedMembership =
        membership(ownedSession, 7L, Instant.now().minusSeconds(3600));

    when(memberships.findByUserIdAndState(7L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(joinedMembership, ownedMembership));

    LadderConfig reusable = service.findReusableSessionConfig(7L);

    assertThat(reusable).isSameAs(ownedSession);
  }

  @Test
  void findReusableSessionConfigReturnsNullWhenUserOnlyJoinedSessions() {
    LadderConfig joinedSession = sessionConfig(22L, 999L, Instant.now().minusSeconds(30));
    LadderMembership joinedMembership = membership(joinedSession, 7L, Instant.now());

    when(memberships.findByUserIdAndState(7L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(joinedMembership));

    LadderConfig reusable = service.findReusableSessionConfig(7L);
    LadderConfigService.SessionLaunchState launchState = service.resolveSessionLaunchState(7L);

    assertThat(reusable).isNull();
    assertThat(launchState.preferredSession()).isSameAs(joinedSession);
    assertThat(launchState.chooserRequired()).isTrue();
    assertThat(launchState.hasOwnedSession()).isFalse();
  }

  private LadderConfig sessionConfig(Long id, Long ownerUserId, Instant createdAt) {
    LadderConfig config = new LadderConfig();
    config.setId(id);
    config.setType(LadderConfig.Type.SESSION);
    config.setStatus(LadderConfig.Status.ACTIVE);
    config.setOwnerUserId(ownerUserId);
    config.setCreatedAt(createdAt);
    config.setExpiresAt(Instant.now().plusSeconds(3600));
    return config;
  }

  private LadderMembership membership(LadderConfig config, Long userId, Instant joinedAt) {
    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(config);
    membership.setUserId(userId);
    membership.setState(LadderMembership.State.ACTIVE);
    membership.setJoinedAt(joinedAt);
    return membership;
  }
}
