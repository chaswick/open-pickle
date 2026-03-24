package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.util.InputValidation;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LadderConfigService {

  private static final int MAX_INVITE_GENERATION_ATTEMPTS = 32;

  private final LadderConfigRepository configs;
  private final LadderSeasonRepository seasons;
  private final LadderMembershipRepository memberships;
  private final LadderInviteGenerator generator;
  private final UserRepository userRepo;
  private final int maxLaddersPerUser;
  private final SeasonNameGenerator seasonNameGenerator;

  @Value("${fhpb.features.story-mode.enabled:true}")
  private boolean storyModeFeatureEnabled = true;

  @Value("${fhpb.session.max-per-user:3}")
  private int maxSessionsPerUser = 3;

  @Value("${fhpb.session.default-hours:48}")
  private int defaultSessionLifetimeHours = 48;

  public LadderConfigService(
      LadderConfigRepository configs,
      LadderSeasonRepository seasons,
      LadderMembershipRepository memberships,
      LadderInviteGenerator generator,
      @Value("${fhpb.ladder.max-per-user:10}") int maxLaddersPerUser,
      UserRepository userRepo,
      SeasonNameGenerator seasonNameGenerator) {
    this.configs = configs;
    this.seasons = seasons;
    this.memberships = memberships;
    this.generator = generator;
    this.userRepo = userRepo;
    this.maxLaddersPerUser = maxLaddersPerUser;
    this.seasonNameGenerator = seasonNameGenerator;
  }

  @Transactional
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
    if (!canCreateMore(ownerUserId)) {
      int allowed = allowedLaddersForUser(ownerUserId);
      throw new IllegalStateException(
          String.format(
              "You can create at most %d ladders. Archive or delete one before creating another.",
              allowed));
    }

    LadderConfig cfg = new LadderConfig();
    cfg.setOwnerUserId(ownerUserId);
    cfg.setTitle(InputValidation.requireGroupTitle(title));
    cfg.setInviteCode(uniqueInvite());
    cfg.setMode(mode);
    int cadenceCount =
        Math.max(1, rollingEveryCount != null ? rollingEveryCount : cfg.getRollingEveryCount());
    LadderConfig.CadenceUnit cadenceUnit =
        rollingEveryUnit != null ? rollingEveryUnit : cfg.getRollingEveryUnit();
    cfg.setRollingEveryCount(cadenceCount);
    cfg.setRollingEveryUnit(cadenceUnit);
    LadderSecurity normalizedSecurity =
        securityLevel != null ? securityLevel : LadderSecurity.STANDARD;
    cfg.setSecurityLevel(normalizedSecurity);
    cfg.setAllowGuestOnlyPersonalMatches(
        LadderSecurity.normalize(normalizedSecurity).isSelfConfirm()
            && allowGuestOnlyPersonalMatches);
    cfg.setStoryModeDefaultEnabled(storyModeFeatureEnabled && storyModeDefaultEnabled);
    LadderConfig savedCfg = configs.save(cfg);

    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(savedCfg);
    membership.setUserId(ownerUserId);
    membership.setRole(LadderMembership.Role.ADMIN);
    membership.setState(LadderMembership.State.ACTIVE);
    memberships.save(membership);

    LadderSeason season =
        buildInitialSeason(
            savedCfg,
            InputValidation.normalizeOptionalSeasonName(seasonName),
            seasonStart,
            seasonEnd,
            mode,
            cadenceCount,
            cadenceUnit);
    seasons.save(season);

    return savedCfg;
  }

  private boolean canCreateMore(Long ownerUserId) {
    if (ownerUserId == null) {
      return true;
    }
    int allowed = allowedLaddersForUser(ownerUserId);
    long currentCount =
        configs.countByOwnerUserIdAndTypeNot(ownerUserId, LadderConfig.Type.SESSION);
    return currentCount < allowed;
  }

  public boolean hasReachedLimit(Long ownerUserId) {
    return !canCreateMore(ownerUserId);
  }

  public int allowedLaddersForUser(Long ownerUserId) {
    if (ownerUserId == null) {
      return maxLaddersPerUser;
    }
    return userRepo
        .findById(ownerUserId)
        .map(user -> user.resolveMaxOwnedLadders(maxLaddersPerUser))
        .orElse(maxLaddersPerUser);
  }

  public boolean hasReachedSessionLimit(Long ownerUserId) {
    if (ownerUserId == null) {
      return false;
    }
    return configs.countByOwnerUserIdAndTypeAndStatusAndExpiresAtAfter(
            ownerUserId, LadderConfig.Type.SESSION, LadderConfig.Status.ACTIVE, Instant.now())
        >= maxSessionsPerUser;
  }

  public int allowedSessionsPerUser() {
    return maxSessionsPerUser;
  }

  public LadderConfig findReusableSessionConfig(Long userId) {
    SessionLaunchState launchState = resolveSessionLaunchState(userId);
    return launchState.hasOwnedSession() ? launchState.preferredSession() : null;
  }

  public SessionLaunchState resolveSessionLaunchState(Long userId) {
    if (userId == null) {
      return SessionLaunchState.empty();
    }

    List<LadderConfig> activeSessions =
        memberships.findByUserIdAndState(userId, LadderMembership.State.ACTIVE).stream()
            .filter(Objects::nonNull)
            .filter(membership -> isReusableSessionConfig(membership.getLadderConfig()))
            .sorted(preferredSessionMembershipComparator(userId))
            .map(LadderMembership::getLadderConfig)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    if (activeSessions.isEmpty()) {
      return SessionLaunchState.empty();
    }

    LadderConfig preferredSession = activeSessions.get(0);
    boolean hasOwnedSession =
        activeSessions.stream().anyMatch(config -> Objects.equals(config.getOwnerUserId(), userId));
    boolean chooserRequired =
        activeSessions.size() > 1
            || (preferredSession != null
                && !Objects.equals(preferredSession.getOwnerUserId(), userId));
    return new SessionLaunchState(
        preferredSession, activeSessions.size(), hasOwnedSession, chooserRequired);
  }

  private String resolveSeasonName(String requested, LocalDate startDate) {
    if (requested != null && !requested.isBlank()) {
      return requested;
    }
    return defaultSeasonLabel(startDate);
  }

  private String defaultSeasonLabel(LocalDate startDate) {
    if (startDate == null) {
      return "Season";
    }
    return seasonNameGenerator.generate(startDate);
  }

  private LadderSeason buildInitialSeason(
      LadderConfig cfg,
      String requestedSeasonName,
      LocalDate requestedStart,
      LocalDate requestedEnd,
      LadderConfig.Mode mode,
      int rollingEveryCount,
      LadderConfig.CadenceUnit rollingEveryUnit) {
    Instant now = Instant.now();
    LocalDate todayUtc = LocalDate.ofInstant(now, ZoneOffset.UTC);

    LadderSeason season = new LadderSeason();
    season.setLadderConfig(cfg);

    if (mode == LadderConfig.Mode.ROLLING) {
      ZonedDateTime plannedEnd = addCadence(now, rollingEveryCount, rollingEveryUnit);
      season.setName(resolveSeasonName(requestedSeasonName, todayUtc));
      season.setStartDate(todayUtc);
      season.setEndDate(plannedEnd.toLocalDate());
      season.setState(LadderSeason.State.ACTIVE);
      season.setStartedAt(now);
      season.setStartedByUserId(cfg.getOwnerUserId());
    } else {
      LocalDate effectiveStart = todayUtc;
      season.setName(resolveSeasonName(requestedSeasonName, effectiveStart));
      season.setStartDate(effectiveStart);
      season.setEndDate(
          requestedEnd != null && !requestedEnd.isBefore(effectiveStart)
              ? requestedEnd
              : effectiveStart.plusYears(90));
      season.setState(LadderSeason.State.ACTIVE);
      season.setStartedAt(now);
      season.setStartedByUserId(cfg.getOwnerUserId());
    }
    season.setStoryModeEnabled(storyModeFeatureEnabled && cfg.isStoryModeDefaultEnabled());
    return season;
  }

  private ZonedDateTime addCadence(
      Instant startInstant, int rollingEveryCount, LadderConfig.CadenceUnit rollingEveryUnit) {
    ZonedDateTime start = startInstant.atZone(ZoneOffset.UTC);
    return rollingEveryUnit == LadderConfig.CadenceUnit.WEEKS
        ? start.plusWeeks(rollingEveryCount)
        : start.plusMonths(rollingEveryCount);
  }

  @Transactional(dontRollbackOn = IllegalStateException.class)
  public LadderConfig createSessionConfig(
      Long ownerUserId, String title, LadderSeason targetSeason) {
    if (ownerUserId == null) {
      throw new IllegalArgumentException("Owner is required");
    }
    if (targetSeason == null || targetSeason.getId() == null) {
      throw new IllegalStateException("Competition season is unavailable.");
    }
    if (hasReachedSessionLimit(ownerUserId)) {
      throw new IllegalStateException(
          String.format(
              "You can have at most %d active match sessions at a time.",
              allowedSessionsPerUser()));
    }

    LadderConfig cfg = new LadderConfig();
    cfg.setOwnerUserId(ownerUserId);
    cfg.setTitle(resolveSessionTitle(ownerUserId, title));
    cfg.setInviteCode(uniqueInvite());
    cfg.setType(LadderConfig.Type.SESSION);
    cfg.setMode(LadderConfig.Mode.MANUAL);
    cfg.setSecurityLevel(LadderSecurity.STANDARD);
    cfg.setAllowGuestOnlyPersonalMatches(false);
    cfg.setStoryModeDefaultEnabled(false);
    cfg.setTargetSeasonId(targetSeason.getId());
    cfg.setExpiresAt(Instant.now().plusSeconds(Math.max(1, defaultSessionLifetimeHours) * 3600L));
    LadderConfig savedCfg = configs.save(cfg);

    LadderMembership ownerMembership = new LadderMembership();
    ownerMembership.setLadderConfig(savedCfg);
    ownerMembership.setUserId(ownerUserId);
    ownerMembership.setRole(LadderMembership.Role.ADMIN);
    ownerMembership.setState(LadderMembership.State.ACTIVE);
    memberships.save(ownerMembership);
    return savedCfg;
  }

  private String resolveSessionTitle(Long ownerUserId, String requestedTitle) {
    String normalizedRequestedTitle = InputValidation.normalizeOptionalGroupTitle(requestedTitle);
    if (normalizedRequestedTitle != null) {
      return normalizedRequestedTitle;
    }
    String ownerName =
        userRepo
            .findById(ownerUserId)
            .map(this::sessionOwnerDisplayName)
            .filter(name -> name != null && !name.isBlank())
            .orElse("Open Play");
    if ("Open Play".equals(ownerName)) {
      return "Open Play Session";
    }
    return ownerName + "'s Session";
  }

  private String sessionOwnerDisplayName(User user) {
    if (user == null) {
      return "Open Play";
    }
    return com.w3llspring.fhpb.web.util.UserPublicName.forUser(user);
  }

  private boolean isReusableSessionConfig(LadderConfig config) {
    if (config == null || !config.isSessionType()) {
      return false;
    }
    if (config.getStatus() != LadderConfig.Status.ACTIVE) {
      return false;
    }
    return config.getExpiresAt() == null || config.getExpiresAt().isAfter(Instant.now());
  }

  private Comparator<LadderMembership> preferredSessionMembershipComparator(Long userId) {
    return Comparator.comparing(
            (LadderMembership membership) -> {
              LadderConfig config = membership.getLadderConfig();
              return config == null || !Objects.equals(config.getOwnerUserId(), userId);
            })
        .thenComparing(
            LadderMembership::getJoinedAt, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(
            membership -> {
              LadderConfig config = membership.getLadderConfig();
              return config != null ? config.getCreatedAt() : null;
            },
            Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(
            membership -> {
              LadderConfig config = membership.getLadderConfig();
              return config != null ? config.getId() : null;
            },
            Comparator.nullsLast(Comparator.reverseOrder()));
  }

  public record SessionLaunchState(
      LadderConfig preferredSession,
      int activeSessionCount,
      boolean hasOwnedSession,
      boolean chooserRequired) {
    public static SessionLaunchState empty() {
      return new SessionLaunchState(null, 0, false, false);
    }
  }

  private String uniqueInvite() {
    for (int i = 0; i < MAX_INVITE_GENERATION_ATTEMPTS; i++) {
      String code = generator.generate();
      code = code == null ? null : code.toUpperCase(java.util.Locale.ROOT);
      if (code != null && configs.findByInviteCode(code).isEmpty()) {
        return code;
      }
    }
    throw new IllegalStateException("Unable to generate unique invite code");
  }

  public String generateUniqueInviteCode() {
    return uniqueInvite();
  }
}
