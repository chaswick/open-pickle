package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LadderAccessServiceTest {

  @Mock private LadderSeasonRepository seasonRepo;

  @Mock private LadderMembershipRepository membershipRepo;

  private LadderAccessService service;

  @BeforeEach
  void setUp() {
    service = new LadderAccessService(seasonRepo, membershipRepo);
  }

  @Test
  void ownerCountsAsSeasonAdminWithoutAdminMembership() {
    Long seasonId = 10L;
    Long ladderId = 20L;
    Long ownerId = 30L;

    LadderConfig config = new LadderConfig();
    config.setId(ladderId);
    config.setOwnerUserId(ownerId);

    LadderSeason season = new LadderSeason();
    season.setLadderConfig(config);

    User owner = new User();
    owner.setId(ownerId);

    when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));

    assertThat(service.isSeasonAdmin(seasonId, owner)).isTrue();
    assertThatNoException().isThrownBy(() -> service.requireAdmin(seasonId, owner));
  }

  @Test
  void nonOwnerNeedsActiveAdminMembership() {
    Long seasonId = 11L;
    Long ladderId = 21L;
    Long ownerId = 31L;
    Long userId = 41L;

    LadderConfig config = new LadderConfig();
    config.setId(ladderId);
    config.setOwnerUserId(ownerId);

    LadderSeason season = new LadderSeason();
    season.setLadderConfig(config);

    User user = new User();
    user.setId(userId);

    LadderMembership adminMembership = new LadderMembership();
    adminMembership.setRole(LadderMembership.Role.ADMIN);
    adminMembership.setState(LadderMembership.State.ACTIVE);

    when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
    when(membershipRepo.findByLadderConfigIdAndUserId(ladderId, userId))
        .thenReturn(Optional.of(adminMembership));

    assertThat(service.isSeasonAdmin(seasonId, user)).isTrue();

    adminMembership.setRole(LadderMembership.Role.MEMBER);
    assertThat(service.isSeasonAdmin(seasonId, user)).isFalse();
    assertThatThrownBy(() -> service.requireAdmin(seasonId, user))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("Admin privileges required");
  }

  @Test
  void returnsFalseWhenSeasonHasNoLadderConfig() {
    Long seasonId = 12L;
    User user = new User();
    user.setId(50L);

    LadderSeason season = new LadderSeason();
    when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));

    assertThat(service.isSeasonAdmin(seasonId, user)).isFalse();
    verifyNoInteractions(membershipRepo);
  }
}
