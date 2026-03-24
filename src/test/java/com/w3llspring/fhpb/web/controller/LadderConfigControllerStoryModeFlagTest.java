package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.competition.LadderConfigController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.InviteChangeCooldownException;
import com.w3llspring.fhpb.web.service.LadderConfigService;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationOperations;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class LadderConfigControllerStoryModeFlagTest {

  @Mock private LadderConfigRepository configs;

  @Mock private LadderSeasonRepository seasons;

  @Mock private LadderMembershipRepository membershipRepo;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
  }

  @Test
  void create_ignoresStoryModeRequestWhenFeatureFlagIsDisabled() {
    StoryModeService disabledStoryMode =
        new StoryModeService(null, null, null, null, null, null) {
          @Override
          public boolean isFeatureEnabled() {
            return false;
          }
        };
    AtomicBoolean capturedStoryModeDefault = new AtomicBoolean(true);
    LadderConfig created = new LadderConfig();
    created.setId(21L);
    LadderConfigService service =
        new LadderConfigService(null, null, null, null, 10, null, null) {
          @Override
          public boolean hasReachedLimit(Long ownerUserId) {
            return false;
          }

          @Override
          public LadderConfig createConfigAndSeason(
              Long ownerUserId,
              String title,
              LocalDate seasonStart,
              LocalDate seasonEnd,
              String seasonName,
              LadderConfig.Mode mode,
              Integer rollingEveryCount,
              LadderConfig.CadenceUnit rollingEveryUnit,
              LadderSecurity securityLevel,
              boolean allowGuestOnlyPersonalMatches,
              boolean storyModeDefaultEnabled) {
            capturedStoryModeDefault.set(storyModeDefaultEnabled);
            return created;
          }
        };
    LadderConfigController controller =
        new LadderConfigController(
            null,
            null,
            service,
            mock(GroupAdministrationOperations.class),
            configs,
            seasons,
            membershipRepo,
            null,
            null,
            null,
            disabledStoryMode,
            20);

    User user = new User();
    user.setId(7L);
    user.setNickName("Tester");
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    LadderSeason season = new LadderSeason();
    season.setStoryModeEnabled(false);

    when(configs.lockById(21L)).thenReturn(created);
    when(configs.saveAndFlush(created)).thenReturn(created);
    when(seasons.findTopByLadderConfigIdOrderByStartDateDesc(21L)).thenReturn(Optional.of(season));

    controller.create(
        auth,
        "Story Off",
        LocalDate.now(),
        null,
        null,
        LadderConfig.Mode.ROLLING,
        6,
        LadderConfig.CadenceUnit.WEEKS,
        null,
        LadderSecurity.STANDARD,
        false,
        true,
        new RedirectAttributesModelMap());

    assertThat(capturedStoryModeDefault.get()).isFalse();
    verify(configs).saveAndFlush(created);
    verify(seasons).save(season);
  }

  @Test
  void create_ignoresStoryModeRequestForTournamentMode() {
    AtomicBoolean capturedStoryModeDefault = new AtomicBoolean(true);
    LadderConfig created = new LadderConfig();
    created.setId(22L);
    LadderConfigService service =
        new LadderConfigService(null, null, null, null, 10, null, null) {
          @Override
          public boolean hasReachedLimit(Long ownerUserId) {
            return false;
          }

          @Override
          public LadderConfig createConfigAndSeason(
              Long ownerUserId,
              String title,
              LocalDate seasonStart,
              LocalDate seasonEnd,
              String seasonName,
              LadderConfig.Mode mode,
              Integer rollingEveryCount,
              LadderConfig.CadenceUnit rollingEveryUnit,
              LadderSecurity securityLevel,
              boolean allowGuestOnlyPersonalMatches,
              boolean storyModeDefaultEnabled) {
            capturedStoryModeDefault.set(storyModeDefaultEnabled);
            return created;
          }
        };
    LadderConfigController controller =
        new LadderConfigController(
            null,
            null,
            service,
            mock(GroupAdministrationOperations.class),
            configs,
            seasons,
            membershipRepo,
            null,
            null,
            null,
            null,
            20);

    User user = new User();
    user.setId(7L);
    user.setNickName("Tester");
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    LadderSeason season = new LadderSeason();
    season.setStoryModeEnabled(true);

    when(configs.lockById(22L)).thenReturn(created);
    when(configs.saveAndFlush(created)).thenReturn(created);
    when(seasons.findTopByLadderConfigIdOrderByStartDateDesc(22L)).thenReturn(Optional.of(season));

    controller.create(
        auth,
        LadderConfig.Type.STANDARD,
        null,
        true,
        "Tournament Story Off",
        LocalDate.now(),
        null,
        null,
        LadderConfig.Mode.ROLLING,
        6,
        LadderConfig.CadenceUnit.WEEKS,
        null,
        LadderSecurity.SELF_CONFIRM,
        true,
        true,
        new RedirectAttributesModelMap());

    assertThat(capturedStoryModeDefault.get()).isFalse();
    assertThat(created.isTournamentMode()).isTrue();
    assertThat(created.getMode()).isEqualTo(LadderConfig.Mode.MANUAL);
    assertThat(created.getSecurityLevel()).isEqualTo(LadderSecurity.STANDARD);
    assertThat(created.isAllowGuestOnlyPersonalMatches()).isFalse();
    assertThat(created.isStoryModeDefaultEnabled()).isFalse();
    assertThat(season.isStoryModeEnabled()).isFalse();
    verify(configs).saveAndFlush(created);
    verify(seasons).save(season);
  }

  @Test
  void regenInvite_returnsWarningWhenInviteCooldownBlocksChange() {
    GroupAdministrationOperations groupAdministration = mock(GroupAdministrationOperations.class);
    when(groupAdministration.regenInviteCode(12L, 7L))
        .thenThrow(
            new InviteChangeCooldownException(
                "Invite changes are on cooldown. Try again in 30 seconds.",
                Instant.parse("2026-03-20T12:00:30Z")));
    LadderConfigController controller =
        new LadderConfigController(
            null,
            null,
            null,
            groupAdministration,
            configs,
            seasons,
            membershipRepo,
            null,
            null,
            null,
            null,
            20);

    User user = new User();
    user.setId(7L);
    user.setNickName("Tester");
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
    String view = controller.regenInvite(12L, auth, redirect);

    assertThat(view).isEqualTo("redirect:/groups/12");
    assertThat(redirect.getFlashAttributes().get("toastLevel")).isEqualTo("warning");
    assertThat(redirect.getFlashAttributes().get("toastMessage"))
        .isEqualTo("Invite changes are on cooldown. Try again in 30 seconds.");
  }
}
