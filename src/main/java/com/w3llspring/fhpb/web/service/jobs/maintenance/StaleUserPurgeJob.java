package com.w3llspring.fhpb.web.service.jobs.maintenance;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.model.User;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class StaleUserPurgeJob {

  private static final Logger log = LoggerFactory.getLogger(StaleUserPurgeJob.class);

  private final UserRepository userRepository;
  private final boolean enabled;
  private final boolean dryRun;
  private final int inactiveDays;
  private final int batchSize;
  private final int maxDeletesPerRun;
  private final Set<Long> protectedUserIds;

  public StaleUserPurgeJob(
      UserRepository userRepository,
      @Value("${fhpb.users.stale-purge.enabled:false}") boolean enabled,
      @Value("${fhpb.users.stale-purge.dry-run:false}") boolean dryRun,
      @Value("${fhpb.users.stale-purge.inactive-days:45}") int inactiveDays,
      @Value("${fhpb.users.stale-purge.batch-size:200}") int batchSize,
      @Value("${fhpb.users.stale-purge.max-deletes-per-run:1000}") int maxDeletesPerRun,
      @Value("${fhpb.users.stale-purge.protected-user-ids:1,2}") String protectedUserIds) {
    this.userRepository = userRepository;
    this.enabled = enabled;
    this.dryRun = dryRun;
    this.inactiveDays = Math.max(1, inactiveDays);
    this.batchSize = Math.max(1, batchSize);
    this.maxDeletesPerRun = Math.max(1, maxDeletesPerRun);
    this.protectedUserIds = parseProtectedUserIds(protectedUserIds);
  }

  // Monthly off-hours job. Default: 4:20 AM America/New_York on day 1 of each month.
  @Scheduled(
      cron = "${fhpb.users.stale-purge.cron:0 20 4 1 * *}",
      zone = "${fhpb.users.stale-purge.zone:America/New_York}")
  public void purgeStaleUsers() {
    try (BackgroundJobLogContext ignored = BackgroundJobLogContext.open("stale-user-purge")) {
      if (!enabled) {
        return;
      }

      Instant cutoff = Instant.now().minus(Duration.ofDays(inactiveDays));
      long afterId = 0L;
      int scanned = 0;
      int deleted = 0;
      int protectedSkipped = 0;
      int failedDeletes = 0;

      while (deleted < maxDeletesPerRun) {
        int pageSize = Math.min(batchSize, maxDeletesPerRun - deleted);
        List<User> candidates =
            userRepository.findStaleZeroFootprintUsersAfterId(
                afterId, cutoff, PageRequest.of(0, pageSize));
        if (candidates.isEmpty()) {
          break;
        }

        afterId = candidates.get(candidates.size() - 1).getId();
        for (User candidate : candidates) {
          if (candidate == null || candidate.getId() == null) {
            continue;
          }
          scanned++;
          if (protectedUserIds.contains(candidate.getId())) {
            protectedSkipped++;
            continue;
          }

          if (dryRun) {
            deleted++;
            continue;
          }

          try {
            userRepository.delete(candidate);
            userRepository.flush();
            deleted++;
          } catch (Exception ex) {
            failedDeletes++;
            log.warn(
                "Stale user purge failed for userId={}: {}", candidate.getId(), ex.getMessage());
          }

          if (deleted >= maxDeletesPerRun) {
            break;
          }
        }
      }

      if (dryRun) {
        log.info(
            "Stale user purge dry-run complete: candidatesScanned={}, wouldDelete={}, protectedSkipped={}, cutoff={}",
            scanned,
            deleted,
            protectedSkipped,
            cutoff);
        return;
      }

      log.info(
          "Stale user purge complete: candidatesScanned={}, deleted={}, protectedSkipped={}, failedDeletes={}, cutoff={}",
          scanned,
          deleted,
          protectedSkipped,
          failedDeletes,
          cutoff);
    }
  }

  private Set<Long> parseProtectedUserIds(String raw) {
    Set<Long> ids = new LinkedHashSet<>();
    if (raw == null || raw.isBlank()) {
      return ids;
    }
    String normalized = raw.replace(';', ',');
    for (String token : normalized.split(",")) {
      if (token == null || token.isBlank()) {
        continue;
      }
      try {
        ids.add(Long.parseLong(token.trim()));
      } catch (NumberFormatException ignored) {
        // Ignore malformed values so one bad token does not disable purge.
      }
    }
    return ids;
  }
}
