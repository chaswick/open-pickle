package com.w3llspring.fhpb.web.service.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.service.InviteChangeCooldownException;
import com.w3llspring.fhpb.web.service.SeasonCarryOverService;
import com.w3llspring.fhpb.web.service.SeasonTransitionService;
import com.w3llspring.fhpb.web.service.SeasonTransitionWindow;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupSeasonSettingsService {

  private final LadderConfigRepository configs;
  private final LadderSeasonRepository seasons;
  private final GroupAdministrationService groupAdministrationService;
  private final SeasonTransitionService transitionSvc;
  private final SeasonCarryOverService seasonCarryOverService;
  private final RoundRobinService roundRobinService;
  private final StoryModeService storyModeService;

  public GroupSeasonSettingsService(
      LadderConfigRepository configs,
      LadderSeasonRepository seasons,
      GroupAdministrationService groupAdministrationService,
      SeasonTransitionService transitionSvc,
      SeasonCarryOverService seasonCarryOverService,
      RoundRobinService roundRobinService,
      StoryModeService storyModeService) {
    this.configs = configs;
    this.seasons = seasons;
    this.groupAdministrationService = groupAdministrationService;
    this.transitionSvc = transitionSvc;
    this.seasonCarryOverService = seasonCarryOverService;
    this.roundRobinService = roundRobinService;
    this.storyModeService = storyModeService;
  }

  @Transactional
  public UpdateOutcome updateSettings(
      Long ladderId,
      Long requesterUserId,
      LadderConfig.Mode mode,
      LadderSecurity securityLevel,
      boolean allowGuestOnlyPersonalMatches,
      boolean carryOverPreviousRating,
      boolean storyModeDefaultEnabled,
      boolean invitesEnabled,
      Integer count,
      LadderConfig.CadenceUnit unit) {

    Integer normalizedCount = normalizeRollingEveryCount(count);
    LadderConfig ladder = requireLockedLadder(ladderId);

    try {
      groupAdministrationService.requireAdmin(ladder, requesterUserId);
    } catch (SecurityException ex) {
      return groupRedirect(ladderId, "Admin required to change settings.", "danger");
    }

    if (ladder.isCompetitionType()) {
      return new UpdateOutcome(
          "/competition",
          "Competition settings are managed from the system competition page.",
          "warning");
    }
    if (ladder.isSessionType()) {
      return groupRedirect(
          ladderId, "Match sessions use fixed settings and cannot be edited.", "warning");
    }

    if (ladder.isTournamentMode()) {
      if (mode != LadderConfig.Mode.MANUAL
          || LadderSecurity.normalize(securityLevel).isSelfConfirm()
          || allowGuestOnlyPersonalMatches) {
        return groupRedirect(
            ladderId,
            "Tournament mode locks season mode to Manual and match confirmation to Standard.",
            "warning");
      }
      mode = LadderConfig.Mode.MANUAL;
      securityLevel = LadderSecurity.STANDARD;
      allowGuestOnlyPersonalMatches = false;
      storyModeDefaultEnabled = false;
    }

    boolean storyModeFeatureEnabled = storyModeFeatureEnabled();
    boolean effectiveStoryModeDefaultEnabled = storyModeFeatureEnabled && storyModeDefaultEnabled;

    try {
      groupAdministrationService.syncInviteAvailability(ladder, requesterUserId, invitesEnabled);
    } catch (InviteChangeCooldownException ex) {
      return groupRedirect(ladderId, ex.getMessage(), "warning");
    }

    ladder.setCarryOverPreviousRating(carryOverPreviousRating);
    if (ladder.isTournamentMode()) {
      ladder.setStoryModeDefaultEnabled(false);
    } else if (storyModeFeatureEnabled) {
      ladder.setStoryModeDefaultEnabled(effectiveStoryModeDefaultEnabled);
    }

    Optional<LadderSeason> activeSeasonOpt = seasons.findActive(ladderId);
    boolean modeChanged = ladder.getMode() != mode;
    boolean cadenceChanged =
        mode == LadderConfig.Mode.ROLLING
            && ((normalizedCount != null && !normalizedCount.equals(ladder.getRollingEveryCount()))
                || (unit != null && unit != ladder.getRollingEveryUnit()));

    if (modeChanged && activeSeasonOpt.isPresent()) {
      LadderSeason activeSeason = activeSeasonOpt.get();

      if (ladder.getMode() == LadderConfig.Mode.ROLLING && mode == LadderConfig.Mode.MANUAL) {
        SeasonTransitionWindow transitionWindow = transitionSvc.canCreateSeason(ladder);
        if (!transitionWindow.isAllowed()) {
          String countdown = transitionSvc.formatCountdown(transitionWindow);
          return groupRedirect(
              ladderId,
              "Cannot switch to manual mode - you've already created a season today. "
                  + "Switching now would end your rolling season but prevent you from starting a manual season. "
                  + (countdown.isEmpty()
                      ? "Try again tomorrow."
                      : "You can switch modes in " + countdown + "."),
              "warning");
        }
        endSeason(activeSeason, requesterUserId);
      } else if (ladder.getMode() == LadderConfig.Mode.MANUAL
          && mode == LadderConfig.Mode.ROLLING) {
        endSeason(activeSeason, requesterUserId);

        SeasonTransitionWindow transitionWindow = transitionSvc.canCreateSeason(ladder);
        if (!transitionWindow.isAllowed()) {
          String countdown = transitionSvc.formatCountdown(transitionWindow);
          return groupRedirect(
              ladderId,
              "Cannot switch to rolling mode - season creation limit reached. "
                  + (countdown.isEmpty()
                      ? "Try again tomorrow."
                      : "Next season creation available in " + countdown + "."),
              "warning");
        }

        LadderSeason newRollingSeason = createNewRollingSeason(ladder, normalizedCount, unit);
        LadderSeason savedNewRollingSeason = seasons.save(newRollingSeason);
        seasonCarryOverService.seedSeasonFromCarryOverIfEnabled(savedNewRollingSeason);
        if (savedNewRollingSeason.isStoryModeEnabled()) {
          storyModeService.ensureTrackers(savedNewRollingSeason);
        }
      }
    }

    if (cadenceChanged && activeSeasonOpt.isPresent() && mode == LadderConfig.Mode.ROLLING) {
      LadderSeason activeSeason = activeSeasonOpt.get();
      Instant currentPlannedEnd = computePlannedEndInstant(activeSeason.getStartedAt(), ladder);
      Instant newPlannedEnd =
          computePlannedEndInstant(
              activeSeason.getStartedAt(),
              normalizedCount != null ? normalizedCount : ladder.getRollingEveryCount(),
              unit != null ? unit : ladder.getRollingEveryUnit());

      if (newPlannedEnd.isBefore(currentPlannedEnd)) {
        SeasonTransitionWindow transitionWindow = transitionSvc.canCreateSeason(ladder);
        if (!transitionWindow.isAllowed()) {
          String countdown = transitionSvc.formatCountdown(transitionWindow);
          return groupRedirect(
              ladderId,
              "Cannot change cadence - you've already created a season today. "
                  + "This cadence change would end the current season early but prevent you from starting a new one. "
                  + (countdown.isEmpty()
                      ? "Try again tomorrow."
                      : "You can change cadence in " + countdown + "."),
              "warning");
        }

        endSeason(activeSeason, requesterUserId);
        LadderSeason newRollingSeason = createNewRollingSeason(ladder, normalizedCount, unit);
        LadderSeason savedNewRollingSeason = seasons.save(newRollingSeason);
        seasonCarryOverService.seedSeasonFromCarryOverIfEnabled(savedNewRollingSeason);
        if (savedNewRollingSeason.isStoryModeEnabled()) {
          storyModeService.ensureTrackers(savedNewRollingSeason);
        }
      } else if (!newPlannedEnd.equals(currentPlannedEnd)) {
        activeSeason.setEndDate(newPlannedEnd.atZone(ZoneOffset.UTC).toLocalDate());
        seasons.save(activeSeason);
      }
    }

    ladder.setMode(mode);
    if (mode == LadderConfig.Mode.ROLLING) {
      if (normalizedCount != null) {
        ladder.setRollingEveryCount(normalizedCount);
      }
      if (unit != null) {
        ladder.setRollingEveryUnit(unit);
      }
    }

    ladder.setSecurityLevel(securityLevel);
    ladder.setAllowGuestOnlyPersonalMatches(
        LadderSecurity.normalize(securityLevel).isSelfConfirm() && allowGuestOnlyPersonalMatches);

    configs.saveAndFlush(ladder);
    if (storyModeFeatureEnabled || ladder.isTournamentMode()) {
      for (LadderSeason season : seasons.findByLadderConfigIdOrderByStartDateDesc(ladderId)) {
        if (season.getState() == LadderSeason.State.ENDED) {
          continue;
        }
        season.setStoryModeEnabled(effectiveStoryModeDefaultEnabled);
        seasons.save(season);
        if (effectiveStoryModeDefaultEnabled) {
          storyModeService.ensureTrackers(season);
        }
      }
    }

    return groupRedirect(ladderId, "Settings updated successfully.", "light");
  }

  private UpdateOutcome groupRedirect(Long ladderId, String message, String level) {
    return new UpdateOutcome("/groups/" + ladderId, message, level);
  }

  private LadderConfig requireLockedLadder(Long ladderId) {
    LadderConfig ladder = configs.lockById(ladderId);
    if (ladder == null) {
      throw new IllegalArgumentException("Group not found");
    }
    return ladder;
  }

  private void endSeason(LadderSeason season, Long endedByUserId) {
    season.setState(LadderSeason.State.ENDED);
    season.setEndedAt(Instant.now());
    season.setEndedByUserId(endedByUserId);
    season.setEndDate(LocalDate.now(ZoneOffset.UTC));
    seasons.save(season);
    roundRobinService.endOpenRoundRobinsForSeason(season);
  }

  private LadderSeason createNewRollingSeason(
      LadderConfig ladder, Integer count, LadderConfig.CadenceUnit unit) {
    int rollingCount = count != null ? count : ladder.getRollingEveryCount();
    LadderConfig.CadenceUnit rollingUnit = unit != null ? unit : ladder.getRollingEveryUnit();

    Instant startInstant = Instant.now();
    ZonedDateTime startZ = startInstant.atZone(ZoneOffset.UTC);
    ZonedDateTime endZ = addCadence(startZ, rollingCount, rollingUnit);

    LadderSeason season = new LadderSeason();
    season.setLadderConfig(ladder);
    season.setState(LadderSeason.State.ACTIVE);
    season.setStartedAt(startInstant);
    season.setStartedByUserId(ladder.getOwnerUserId());
    season.setName(defaultSeasonLabel(startZ.toLocalDate()));
    season.setStartDate(startZ.toLocalDate());
    season.setEndDate(endZ.toLocalDate());
    season.setStoryModeEnabled(storyModeFeatureEnabled() && ladder.isStoryModeDefaultEnabled());

    ladder.setLastSeasonCreatedAt(startInstant);
    return season;
  }

  private String defaultSeasonLabel(LocalDate startDate) {
    if (startDate == null) {
      return "Season";
    }
    return "Season - " + startDate;
  }

  private Instant computePlannedEndInstant(Instant startedAt, LadderConfig ladder) {
    return computePlannedEndInstant(
        startedAt, ladder.getRollingEveryCount(), ladder.getRollingEveryUnit());
  }

  private Instant computePlannedEndInstant(
      Instant startedAt, int count, LadderConfig.CadenceUnit unit) {
    ZonedDateTime startZ = startedAt.atZone(ZoneOffset.UTC);
    return addCadence(startZ, count, unit).toInstant();
  }

  private ZonedDateTime addCadence(ZonedDateTime start, int count, LadderConfig.CadenceUnit unit) {
    return unit == LadderConfig.CadenceUnit.WEEKS
        ? start.plusWeeks(count)
        : start.plusMonths(count);
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

  private boolean storyModeFeatureEnabled() {
    return storyModeService == null || storyModeService.isFeatureEnabled();
  }

  public record UpdateOutcome(String redirectPath, String toastMessage, String toastLevel) {}
}
