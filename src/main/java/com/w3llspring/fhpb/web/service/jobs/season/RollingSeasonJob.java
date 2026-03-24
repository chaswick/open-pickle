package com.w3llspring.fhpb.web.service.jobs.season;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.service.SeasonCarryOverService;
import com.w3llspring.fhpb.web.service.SeasonNameGenerator;
import com.w3llspring.fhpb.web.service.SeasonTransitionService;
import com.w3llspring.fhpb.web.service.SeasonTransitionWindow;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RollingSeasonJob {

  private static final Logger log = LoggerFactory.getLogger(RollingSeasonJob.class);
  private static final ZoneId SEASON_ZONE = ZoneOffset.UTC;

  private final LadderConfigRepository ladderRepo;
  private final LadderSeasonRepository seasonRepo;
  private final TransactionTemplate tx; // <— explicit TX wrapper
  private final SeasonNameGenerator seasonNameGenerator;
  private final SeasonTransitionService transitionSvc;
  private final SeasonCarryOverService seasonCarryOverService;
  private final RoundRobinService roundRobinService;
  private final StoryModeService storyModeService;

  public RollingSeasonJob(
      LadderConfigRepository ladderRepo,
      LadderSeasonRepository seasonRepo,
      PlatformTransactionManager txManager,
      SeasonNameGenerator seasonNameGenerator,
      SeasonTransitionService transitionSvc,
      SeasonCarryOverService seasonCarryOverService,
      RoundRobinService roundRobinService,
      StoryModeService storyModeService) {
    this.ladderRepo = ladderRepo;
    this.seasonRepo = seasonRepo;
    this.tx = new TransactionTemplate(txManager);
    this.seasonNameGenerator = seasonNameGenerator;
    this.transitionSvc = transitionSvc;
    this.seasonCarryOverService = seasonCarryOverService;
    this.roundRobinService = roundRobinService;
    this.storyModeService = storyModeService;
  }

  @Scheduled(cron = "0 10 * * * *") // hourly at :10
  public void run() {
    try (BackgroundJobLogContext ignored =
        BackgroundJobLogContext.open("rolling-season-maintenance")) {
      List<LadderConfig> rolling = ladderRepo.findByMode(LadderConfig.Mode.ROLLING);
      if (rolling.isEmpty()) return;

      for (LadderConfig l : rolling) {
        try {
          // Each ladder handled inside its own REQUIRED transaction
          tx.execute(
              status -> {
                maintainRollingTx(l.getId());
                return null;
              });
        } catch (Exception ex) {
          log.warn("RollingSeasonJob error for ladderId={}: {}", l.getId(), ex.getMessage(), ex);
        }
      }
    }
  }

  /** Entire method runs inside a TX via TransactionTemplate in run() */
  private void maintainRollingTx(Long ladderId) {
    // Serialize per ladder via DB lock (requires an active TX)
    LadderConfig ladder = ladderRepo.lockById(ladderId);

    if (ladder.getMode() != LadderConfig.Mode.ROLLING) return;

    Optional<LadderSeason> activeOpt = seasonRepo.findActive(ladderId);

    if (!activeOpt.isPresent()) {
      Optional<LadderSeason> scheduledOpt =
          seasonRepo.findTopByLadderConfigIdAndStateOrderByStartDateAsc(
              ladderId, LadderSeason.State.SCHEDULED);

      if (scheduledOpt.isPresent()) {
        LadderSeason scheduled = scheduledOpt.get();
        Instant plannedStart = resolveScheduledStartInstant(scheduled);
        if (!Instant.now().isBefore(plannedStart)) {
          activateScheduledSeason(ladder, scheduled, plannedStart);
          seasonRepo.save(scheduled);
          seasonCarryOverService.seedSeasonFromCarryOverIfEnabled(scheduled);
          log.info("Activated scheduled season for ladderId={} at {}", ladderId, plannedStart);
        }
        return;
      }

      // Check season creation rate limit before creating a new season
      SeasonTransitionWindow tw = transitionSvc.canCreateSeason(ladder);
      if (!tw.isAllowed()) {
        log.info(
            "Skipping automatic season creation for ladderId={} - rate limit reached. Next allowed: {}",
            ladderId,
            tw.getNextAllowedAt());
        return;
      }

      LadderSeason created = createSeasonAtInstant(ladder, Instant.now());
      // Update the ladder's last season creation timestamp
      ladder.setLastSeasonCreatedAt(created.getStartedAt());
      LadderSeason savedCreated = seasonRepo.save(created);
      seasonCarryOverService.seedSeasonFromCarryOverIfEnabled(savedCreated);
      if (savedCreated.isStoryModeEnabled()) {
        storyModeService.ensureTrackers(savedCreated);
      }
      log.info(
          "Started new rolling season for ladderId={} starting {}",
          ladderId,
          created.getStartedAt());
      return;
    }

    LadderSeason active = activeOpt.get();
    Instant plannedEnd = computePlannedEndInstant(active.getStartedAt(), ladder);

    if (Instant.now().isAfter(plannedEnd)) {
      // End current at boundary
      closeSeasonAtBoundary(active, plannedEnd);
      int endedRoundRobins = roundRobinService.endOpenRoundRobinsForSeason(active);
      seasonRepo.save(active);

      // Check season creation rate limit before creating next season
      SeasonTransitionWindow tw = transitionSvc.canCreateSeason(ladder);
      if (!tw.isAllowed()) {
        log.info(
            "Skipping automatic season rollover for ladderId={} - rate limit reached. Next allowed: {}",
            ladderId,
            tw.getNextAllowedAt());
        return;
      }

      // Start next at boundary
      LadderSeason next = createSeasonAtInstant(ladder, plannedEnd);
      // Update the ladder's last season creation timestamp
      ladder.setLastSeasonCreatedAt(next.getStartedAt());
      LadderSeason savedNext = seasonRepo.save(next);
      seasonCarryOverService.seedSeasonFromCarryOverIfEnabled(savedNext);
      if (savedNext.isStoryModeEnabled()) {
        storyModeService.ensureTrackers(savedNext);
      }

      log.info(
          "Rolled season for ladderId={} at {} (ended {} open round-robins)",
          ladderId,
          plannedEnd,
          endedRoundRobins);
    }
  }

  // === helpers unchanged ===

  private Instant resolveScheduledStartInstant(LadderSeason scheduled) {
    Instant start = scheduled.getStartedAt();
    if (start != null) {
      return start;
    }
    LocalDate startDate = scheduled.getStartDate();
    return startDate.atStartOfDay(SEASON_ZONE).toInstant();
  }

  private void activateScheduledSeason(
      LadderConfig ladder, LadderSeason scheduled, Instant startInstant) {
    scheduled.setState(LadderSeason.State.ACTIVE);
    scheduled.setStartedAt(startInstant);
    scheduled.setStartedByUserId(null);
    scheduled.setStoryModeEnabled(storyModeFeatureEnabled() && scheduled.isStoryModeEnabled());
    // Ensure end date remains aligned to cadence if it somehow predates the start
    if (scheduled.getEndDate().isBefore(scheduled.getStartDate())) {
      LocalDate adjustedEnd = addCadence(startInstant.atZone(SEASON_ZONE), ladder).toLocalDate();
      scheduled.setEndDate(adjustedEnd);
    }
  }

  private LadderSeason createSeasonAtInstant(LadderConfig ladder, Instant startInstantUtc) {
    ZonedDateTime startZ = startInstantUtc.atZone(SEASON_ZONE);
    ZonedDateTime plannedEndZ = addCadence(startZ, ladder);

    LadderSeason s = new LadderSeason();
    s.setLadderConfig(ladder);
    s.setState(LadderSeason.State.ACTIVE);
    s.setStartedAt(startInstantUtc);
    s.setName(buildRollingSeasonName(ladder, startZ, plannedEndZ));
    s.setStartDate(startZ.toLocalDate());
    s.setEndDate(plannedEndZ.toLocalDate());
    s.setStoryModeEnabled(storyModeFeatureEnabled() && ladder.isStoryModeDefaultEnabled());
    return s;
  }

  private void closeSeasonAtBoundary(LadderSeason active, Instant plannedEndUtc) {
    active.setState(LadderSeason.State.ENDED);
    active.setEndedAt(plannedEndUtc);
    active.setEndDate(plannedEndUtc.atZone(SEASON_ZONE).toLocalDate());
  }

  private Instant computePlannedEndInstant(Instant startedAtUtc, LadderConfig ladder) {
    ZonedDateTime startZ = startedAtUtc.atZone(SEASON_ZONE);
    ZonedDateTime endZ = addCadence(startZ, ladder);
    return endZ.toInstant();
  }

  private ZonedDateTime addCadence(ZonedDateTime start, LadderConfig ladder) {
    int count = Math.max(1, ladder.getRollingEveryCount());
    if (ladder.getRollingEveryUnit() == LadderConfig.CadenceUnit.WEEKS) {
      return start.plusWeeks(count);
    } else {
      return start.plusMonths(count);
    }
  }

  private String buildRollingSeasonName(
      LadderConfig ladder, ZonedDateTime startZ, ZonedDateTime endZ) {
    // Use the SeasonNameGenerator for creative seasonal names instead of date ranges
    return seasonNameGenerator.generate(startZ.toLocalDate());
  }

  private boolean storyModeFeatureEnabled() {
    return storyModeService == null || storyModeService.isFeatureEnabled();
  }
}
