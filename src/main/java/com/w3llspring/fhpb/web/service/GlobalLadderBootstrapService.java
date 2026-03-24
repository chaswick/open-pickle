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
import com.w3llspring.fhpb.web.util.UserPublicName;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GlobalLadderBootstrapService {

  private static final Logger log = LoggerFactory.getLogger(GlobalLadderBootstrapService.class);

  private final boolean enabled;
  private final boolean assignAllUsers;
  private final boolean autoEnrollNewUsers;
  private final String adminEmail;
  private final String adminPassword;
  private final String adminDisplayName;
  private final String ladderTitle;
  private final String seasonName;

  private final UserRepository userRepo;
  private final LadderConfigRepository configRepo;
  private final LadderSeasonRepository seasonRepo;
  private final LadderMembershipRepository membershipRepo;
  private final LadderConfigService configService;
  private final BCryptPasswordEncoder passwordEncoder;

  private volatile Long globalConfigId;

  public GlobalLadderBootstrapService(
      @Value("${fhpb.bootstrap.global-ladder.enabled:false}") boolean enabled,
      @Value("${fhpb.bootstrap.global-ladder.assign-all-users:false}") boolean assignAllUsers,
      @Value("${fhpb.bootstrap.global-ladder.auto-enroll-new:false}") boolean autoEnrollNewUsers,
      @Value("${fhpb.bootstrap.admin.email:}") String adminEmail,
      @Value("${fhpb.bootstrap.admin.password:}") String adminPassword,
      @Value("${fhpb.bootstrap.admin.display-name:SiteAdmin}") String adminDisplayName,
      @Value("${fhpb.bootstrap.global-ladder.title:Community Ladder}") String ladderTitle,
      @Value("${fhpb.bootstrap.global-ladder.season-name:Opening Season}") String seasonName,
      UserRepository userRepo,
      LadderConfigRepository configRepo,
      LadderSeasonRepository seasonRepo,
      LadderMembershipRepository membershipRepo,
      LadderConfigService configService,
      BCryptPasswordEncoder passwordEncoder) {
    this.enabled = enabled;
    this.assignAllUsers = assignAllUsers;
    this.autoEnrollNewUsers = autoEnrollNewUsers;
    this.adminEmail = adminEmail != null ? adminEmail.trim().toLowerCase() : "";
    this.adminPassword = adminPassword;
    this.adminDisplayName = adminDisplayName;
    this.ladderTitle = ladderTitle;
    this.seasonName = seasonName;
    this.userRepo = userRepo;
    this.configRepo = configRepo;
    this.seasonRepo = seasonRepo;
    this.membershipRepo = membershipRepo;
    this.configService = configService;
    this.passwordEncoder = passwordEncoder;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean autoEnrollNewUsers() {
    return enabled && autoEnrollNewUsers;
  }

  @Transactional
  public void initializeIfNeeded() {
    if (!enabled) {
      return;
    }

    User admin = ensureAdminUser();
    LadderConfig config = ensureGlobalLadder(admin);
    ensureActiveSeason(config, admin);
    globalConfigId = config.getId();
    ensureMembership(config, admin, LadderMembership.Role.ADMIN);

    if (assignAllUsers) {
      assignAllUsers(config);
    }
  }

  @Transactional
  public void enrollUserIfConfigured(User user) {
    if (!autoEnrollNewUsers()) {
      return;
    }
    LadderConfig config = resolveConfig();
    if (config == null) {
      log.warn("Global ladder auto-enrollment is enabled but no config is available yet.");
      return;
    }
    ensureMembership(config, user, LadderMembership.Role.MEMBER);
  }

  private LadderConfig resolveConfig() {
    Long id = globalConfigId;
    if (id != null) {
      return configRepo.findById(id).orElse(null);
    }
    if (!enabled) {
      return null;
    }
    User admin = ensureAdminUser();
    LadderConfig config = ensureGlobalLadder(admin);
    globalConfigId = config.getId();
    return config;
  }

  private User ensureAdminUser() {
    if (!StringUtils.hasText(adminEmail)) {
      throw new IllegalStateException(
          "fhpb.bootstrap.admin.email must be configured when global ladder bootstrap is enabled.");
    }

    User existing = userRepo.findByEmail(adminEmail);
    if (existing != null) {
      boolean updated = false;
      if (!existing.isAdmin()) {
        existing.setAdmin(true);
        updated = true;
      }
      String desiredDisplayName = safeAdminDisplayName();
      if (shouldUpdateExistingAdminDisplayName(existing, desiredDisplayName)) {
        existing.setNickName(trimDisplayName(desiredDisplayName));
        updated = true;
      }
      if (existing.getMaxOwnedLadders() == null || existing.getMaxOwnedLadders() < 1) {
        existing.setMaxOwnedLadders(1);
        updated = true;
      }
      if (updated) {
        userRepo.save(existing);
      }
      return existing;
    }

    if (!StringUtils.hasText(adminPassword)) {
      throw new IllegalStateException(
          "fhpb.bootstrap.admin.password must be set to create the bootstrap admin user.");
    }

    User user = new User();
    user.setEmail(adminEmail);
    user.setPassword(passwordEncoder.encode(adminPassword));
    user.setAdmin(true);
    user.setNickName(trimDisplayName(safeAdminDisplayName()));
    user.setMaxOwnedLadders(1);
    return userRepo.save(user);
  }

  private boolean shouldUpdateExistingAdminDisplayName(User existing, String desiredDisplayName) {
    if (!StringUtils.hasText(desiredDisplayName)) {
      return false;
    }
    String currentDisplayName = existing.getNickName();
    if (StringUtils.hasText(adminDisplayName)) {
      return !desiredDisplayName.equals(currentDisplayName);
    }
    return isDerivedFromAdminEmail(currentDisplayName)
        && !desiredDisplayName.equals(currentDisplayName);
  }

  private boolean isDerivedFromAdminEmail(String nickName) {
    if (!StringUtils.hasText(nickName) || !StringUtils.hasText(adminEmail)) {
      return false;
    }
    String current = nickName.trim();
    int at = adminEmail.indexOf('@');
    String localPart = at > 0 ? adminEmail.substring(0, at) : adminEmail;
    return current.equalsIgnoreCase(localPart) || current.equalsIgnoreCase(adminEmail);
  }

  private String safeAdminDisplayName() {
    if (StringUtils.hasText(adminDisplayName)) {
      return adminDisplayName.trim();
    }
    return UserPublicName.FALLBACK;
  }

  private String trimDisplayName(String displayName) {
    return displayName.substring(0, Math.min(displayName.length(), User.MAX_NICKNAME_LENGTH));
  }

  private LadderConfig ensureGlobalLadder(User admin) {
    LadderConfig cfg =
        configRepo
            .findFirstByOwnerUserIdAndTitleIgnoreCase(admin.getId(), ladderTitle)
            .orElseGet(() -> reuseOrCreateGlobal(admin));
    alignConfigSettings(cfg, admin);
    return cfg;
  }

  private LadderConfig reuseOrCreateGlobal(User admin) {
    LadderConfig existingCompetition =
        configRepo
            .findFirstByOwnerUserIdAndTypeOrderByIdAsc(admin.getId(), LadderConfig.Type.COMPETITION)
            .orElseGet(
                () ->
                    configRepo
                        .findFirstByTypeOrderByIdAsc(LadderConfig.Type.COMPETITION)
                        .orElse(null));
    if (existingCompetition != null) {
      log.info(
          "Reusing competition ladder '{}' (id={}) for global bootstrap.",
          existingCompetition.getTitle(),
          existingCompetition.getId());
      return existingCompetition;
    }
    log.info("Creating global ladder '{}' for admin userId={}", ladderTitle, admin.getId());
    return configService.createConfigAndSeason(
        admin.getId(),
        ladderTitle,
        LocalDate.now(ZoneOffset.UTC),
        null,
        seasonName,
        LadderConfig.Mode.ROLLING,
        6,
        LadderConfig.CadenceUnit.WEEKS,
        LadderSecurity.STANDARD,
        false,
        false);
  }

  private void alignConfigSettings(LadderConfig cfg, User admin) {
    boolean dirty = false;
    if (!ladderTitle.equals(cfg.getTitle())) {
      cfg.setTitle(ladderTitle);
      dirty = true;
    }
    if (!cfg.getOwnerUserId().equals(admin.getId())) {
      cfg.setOwnerUserId(admin.getId());
      dirty = true;
    }
    if (cfg.getMode() != LadderConfig.Mode.ROLLING) {
      cfg.setMode(LadderConfig.Mode.ROLLING);
      dirty = true;
    }
    if (cfg.getType() != LadderConfig.Type.COMPETITION) {
      cfg.setType(LadderConfig.Type.COMPETITION);
      dirty = true;
    }
    if (cfg.getRollingEveryCount() != 6) {
      cfg.setRollingEveryCount(6);
      dirty = true;
    }
    if (cfg.getRollingEveryUnit() != LadderConfig.CadenceUnit.WEEKS) {
      cfg.setRollingEveryUnit(LadderConfig.CadenceUnit.WEEKS);
      dirty = true;
    }
    if (dirty) {
      configRepo.save(cfg);
    }
  }

  private void ensureActiveSeason(LadderConfig config, User admin) {
    if (seasonRepo.findActive(config.getId()).isPresent()) {
      return;
    }

    LadderSeason season = new LadderSeason();
    LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
    season.setLadderConfig(config);
    season.setState(LadderSeason.State.ACTIVE);
    season.setName(formatSeasonName(todayUtc));
    season.setStartDate(todayUtc);
    season.setEndDate(todayUtc.plusWeeks(6));
    Instant now = Instant.now();
    season.setStartedAt(now);
    season.setStartedByUserId(admin.getId());
    seasonRepo.save(season);
  }

  private String formatSeasonName(LocalDate start) {
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
    return seasonName + " • " + start.format(fmt);
  }

  private void assignAllUsers(LadderConfig config) {
    List<User> users = userRepo.findAll();
    for (User user : users) {
      LadderMembership.Role role =
          user.getId().equals(config.getOwnerUserId())
              ? LadderMembership.Role.ADMIN
              : LadderMembership.Role.MEMBER;
      ensureMembership(config, user, role);
    }
  }

  private void ensureMembership(LadderConfig config, User user, LadderMembership.Role role) {
    if (user == null || user.getId() == null) {
      return;
    }
    var existingOpt = membershipRepo.findByLadderConfigIdAndUserId(config.getId(), user.getId());
    if (existingOpt.isPresent()) {
      LadderMembership membership = existingOpt.get();
      boolean dirty = false;
      if (membership.getState() != LadderMembership.State.ACTIVE) {
        membership.setState(LadderMembership.State.ACTIVE);
        membership.setJoinedAt(Instant.now());
        membership.setLeftAt(null);
        dirty = true;
      }
      if (membership.getRole() != role) {
        membership.setRole(role);
        dirty = true;
      }
      if (dirty) {
        membershipRepo.save(membership);
      }
      return;
    }

    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(config);
    membership.setUserId(user.getId());
    membership.setRole(role);
    membership.setState(LadderMembership.State.ACTIVE);
    membership.setJoinedAt(Instant.now());
    membershipRepo.save(membership);
  }
}
