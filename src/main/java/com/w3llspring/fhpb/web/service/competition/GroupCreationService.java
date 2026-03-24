package com.w3llspring.fhpb.web.service.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.service.LadderConfigService;
import com.w3llspring.fhpb.web.service.StoryModeService;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class GroupCreationService {

  private final LadderConfigService ladderConfigService;
  private final LadderConfigRepository configs;
  private final LadderSeasonRepository seasons;
  private final StoryModeService storyModeService;

  public GroupCreationService(
      LadderConfigService ladderConfigService,
      LadderConfigRepository configs,
      LadderSeasonRepository seasons,
      StoryModeService storyModeService) {
    this.ladderConfigService = ladderConfigService;
    this.configs = configs;
    this.seasons = seasons;
    this.storyModeService = storyModeService;
  }

  public GroupCreationOutcome createGroup(Long currentUserId, GroupCreateRequest request) {
    boolean effectiveTournamentMode = request.tournamentMode();
    LadderConfig.Mode mode = request.mode();
    LadderSecurity securityLevel = request.securityLevel();
    boolean allowGuestOnlyPersonalMatches = request.allowGuestOnlyPersonalMatches();
    boolean storyModeDefaultEnabled = request.storyModeDefaultEnabled();

    if (effectiveTournamentMode) {
      mode = LadderConfig.Mode.MANUAL;
      securityLevel = LadderSecurity.STANDARD;
      allowGuestOnlyPersonalMatches = false;
      storyModeDefaultEnabled = false;
    }

    if (ladderConfigService.hasReachedLimit(currentUserId)) {
      int allowed = ladderConfigService.allowedLaddersForUser(currentUserId);
      return GroupCreationOutcome.failure(
          String.format(
              "You can create at most %d ladders. Archive or delete one before creating another.",
              allowed),
          "danger");
    }

    Integer rollingEveryCount = request.rollingEveryCount();
    if (rollingEveryCount == null || rollingEveryCount < 1) {
      rollingEveryCount = 6;
    }
    rollingEveryCount = normalizeRollingEveryCount(rollingEveryCount);
    LadderConfig.CadenceUnit rollingEveryUnit =
        request.rollingEveryUnit() != null
            ? request.rollingEveryUnit()
            : LadderConfig.CadenceUnit.WEEKS;

    LocalDate seasonEnd = request.seasonEnd();
    if (seasonEnd == null) {
      if (mode == LadderConfig.Mode.ROLLING) {
        seasonEnd =
            rollingEveryUnit == LadderConfig.CadenceUnit.WEEKS
                ? request.seasonStart().plusWeeks(rollingEveryCount)
                : request.seasonStart().plusMonths(rollingEveryCount);
      } else {
        seasonEnd = request.seasonStart().plusYears(90);
      }
    }

    boolean effectiveStoryModeDefaultEnabled = storyModeFeatureEnabled() && storyModeDefaultEnabled;

    final LadderConfig createdConfig;
    try {
      createdConfig =
          ladderConfigService.createConfigAndSeason(
              currentUserId,
              request.title(),
              request.seasonStart(),
              seasonEnd,
              request.seasonName(),
              mode,
              rollingEveryCount,
              rollingEveryUnit,
              securityLevel,
              allowGuestOnlyPersonalMatches,
              effectiveStoryModeDefaultEnabled);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      return GroupCreationOutcome.failure(
          ex.getMessage() != null ? ex.getMessage() : "Unable to create ladder.", "danger");
    }

    LadderConfig locked = configs.lockById(createdConfig.getId());
    locked.setMode(mode);
    if (mode == LadderConfig.Mode.ROLLING) {
      locked.setRollingEveryCount(rollingEveryCount);
      locked.setRollingEveryUnit(rollingEveryUnit);
    }
    locked.setTournamentMode(effectiveTournamentMode);
    if (effectiveTournamentMode) {
      locked.setMode(LadderConfig.Mode.MANUAL);
      locked.setSecurityLevel(LadderSecurity.STANDARD);
      locked.setAllowGuestOnlyPersonalMatches(false);
    }
    locked.setStoryModeDefaultEnabled(effectiveStoryModeDefaultEnabled);
    configs.saveAndFlush(locked);
    seasons
        .findTopByLadderConfigIdOrderByStartDateDesc(locked.getId())
        .ifPresent(
            season -> {
              season.setStoryModeEnabled(effectiveStoryModeDefaultEnabled);
              seasons.save(season);
              if (effectiveStoryModeDefaultEnabled && storyModeService != null) {
                storyModeService.ensureTrackers(season);
              }
            });

    return GroupCreationOutcome.success(locked.getId(), "Ladder created.", "light", false);
  }

  public GroupCreationOutcome createOrReuseSession(
      Long currentUserId, String title, LadderSeason competitionSeason) {
    LadderConfig existingSession = ladderConfigService.findReusableSessionConfig(currentUserId);
    if (existingSession != null) {
      return GroupCreationOutcome.success(existingSession.getId(), null, null, true);
    }
    if (competitionSeason == null) {
      return GroupCreationOutcome.failure(
          "Competition season is unavailable. Try again after it has been created.", "danger");
    }
    try {
      LadderConfig sessionConfig =
          ladderConfigService.createSessionConfig(currentUserId, title, competitionSeason);
      return GroupCreationOutcome.success(
          sessionConfig.getId(), "Match session created.", "light", false);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      return GroupCreationOutcome.failure(
          ex.getMessage() != null ? ex.getMessage() : "Unable to create match session.", "danger");
    }
  }

  private boolean storyModeFeatureEnabled() {
    return storyModeService == null || storyModeService.isFeatureEnabled();
  }

  private Integer normalizeRollingEveryCount(Integer count) {
    if (count == null) {
      return null;
    }
    if (count < 1) {
      return 1;
    }
    return Math.min(count, LadderConfig.MAX_ROLLING_EVERY_COUNT);
  }

  public record GroupCreateRequest(
      String title,
      LocalDate seasonStart,
      LocalDate seasonEnd,
      LadderConfig.Mode mode,
      Integer rollingEveryCount,
      LadderConfig.CadenceUnit rollingEveryUnit,
      String seasonName,
      LadderSecurity securityLevel,
      boolean allowGuestOnlyPersonalMatches,
      boolean storyModeDefaultEnabled,
      boolean tournamentMode) {}

  public record GroupCreationOutcome(
      boolean success,
      Long configId,
      String toastMessage,
      String toastLevel,
      boolean reusedExistingSession) {

    public static GroupCreationOutcome success(
        Long configId, String toastMessage, String toastLevel, boolean reusedExistingSession) {
      return new GroupCreationOutcome(
          true, configId, toastMessage, toastLevel, reusedExistingSession);
    }

    public static GroupCreationOutcome failure(String toastMessage, String toastLevel) {
      return new GroupCreationOutcome(false, null, toastMessage, toastLevel, false);
    }
  }
}
