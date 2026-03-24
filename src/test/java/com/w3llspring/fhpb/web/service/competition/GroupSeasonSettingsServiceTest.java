package com.w3llspring.fhpb.web.service.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.service.InviteChangeCooldownException;
import com.w3llspring.fhpb.web.service.SeasonCarryOverService;
import com.w3llspring.fhpb.web.service.SeasonTransitionService;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupSeasonSettingsServiceTest {

  @Mock private LadderConfigRepository configs;
  @Mock private LadderSeasonRepository seasons;
  @Mock private GroupAdministrationService groupAdministrationService;
  @Mock private SeasonTransitionService transitionService;
  @Mock private SeasonCarryOverService seasonCarryOverService;
  @Mock private RoundRobinService roundRobinService;
  @Mock private StoryModeService storyModeService;

  private GroupSeasonSettingsService service;

  @BeforeEach
  void setUp() {
    service =
        new GroupSeasonSettingsService(
            configs,
            seasons,
            groupAdministrationService,
            transitionService,
            seasonCarryOverService,
            roundRobinService,
            storyModeService);
  }

  @Test
  void updateSettings_ignoresStoryModeRequestForTournamentMode() {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(9L);
    ladder.setTournamentMode(true);
    ladder.setMode(LadderConfig.Mode.MANUAL);
    ladder.setSecurityLevel(LadderSecurity.STANDARD);
    ladder.setStoryModeDefaultEnabled(true);

    LadderSeason season = new LadderSeason();
    season.setState(LadderSeason.State.ACTIVE);
    season.setStoryModeEnabled(true);

    when(configs.lockById(9L)).thenReturn(ladder);
    when(configs.saveAndFlush(ladder)).thenReturn(ladder);
    when(seasons.findActive(9L)).thenReturn(Optional.empty());
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(9L)).thenReturn(List.of(season));
    when(storyModeService.isFeatureEnabled()).thenReturn(true);
    doNothing().when(groupAdministrationService).requireAdmin(ladder, 7L);
    when(groupAdministrationService.syncInviteAvailability(ladder, 7L, false)).thenReturn(ladder);

    GroupSeasonSettingsService.UpdateOutcome outcome =
        service.updateSettings(
            9L,
            7L,
            LadderConfig.Mode.MANUAL,
            LadderSecurity.STANDARD,
            false,
            true,
            true,
            false,
            null,
            null);

    assertThat(outcome.redirectPath()).isEqualTo("/groups/9");
    assertThat(outcome.toastLevel()).isEqualTo("light");
    assertThat(ladder.isCarryOverPreviousRating()).isTrue();
    assertThat(ladder.isStoryModeDefaultEnabled()).isFalse();
    assertThat(season.isStoryModeEnabled()).isFalse();
    verify(configs).saveAndFlush(ladder);
    verify(seasons).save(season);
  }

  @Test
  void updateSettings_returnsWarningWhenInviteCooldownBlocksChange() {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(12L);
    ladder.setMode(LadderConfig.Mode.MANUAL);
    ladder.setSecurityLevel(LadderSecurity.STANDARD);
    ladder.setInviteCode("LIVE-INVITE");

    when(configs.lockById(12L)).thenReturn(ladder);
    doNothing().when(groupAdministrationService).requireAdmin(ladder, 7L);
    when(groupAdministrationService.syncInviteAvailability(ladder, 7L, false))
        .thenThrow(
            new InviteChangeCooldownException(
                "Invite changes are on cooldown. Try again in 30 seconds.",
                Instant.parse("2026-03-20T12:00:30Z")));

    GroupSeasonSettingsService.UpdateOutcome outcome =
        service.updateSettings(
            12L,
            7L,
            LadderConfig.Mode.MANUAL,
            LadderSecurity.STANDARD,
            false,
            false,
            false,
            false,
            null,
            null);

    assertThat(outcome.redirectPath()).isEqualTo("/groups/12");
    assertThat(outcome.toastLevel()).isEqualTo("warning");
    assertThat(outcome.toastMessage())
        .isEqualTo("Invite changes are on cooldown. Try again in 30 seconds.");
    verify(configs, never()).saveAndFlush(ladder);
  }
}
