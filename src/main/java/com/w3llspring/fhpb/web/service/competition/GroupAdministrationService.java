package com.w3llspring.fhpb.web.service.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.InviteChangeCooldownException;
import com.w3llspring.fhpb.web.service.LadderInviteGenerator;
import com.w3llspring.fhpb.web.util.SessionInviteCodeSupport;
import com.w3llspring.fhpb.web.util.InputValidation;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupAdministrationService implements GroupAdministrationOperations {

  private static final int MAX_INVITE_GENERATION_ATTEMPTS = 32;

  private final LadderConfigRepository configs;
  private final LadderMembershipRepository memberships;
  private final LadderInviteGenerator generator;
  private final UserRepository userRepo;
  private final SessionLifecycleService sessionLifecycleService;
  private final int defaultMaxMembers;
  private final long inviteChangeCooldownSeconds;
  private final String siteWideAdminEmail;

  public GroupAdministrationService(
      LadderConfigRepository configs,
      LadderMembershipRepository memberships,
      LadderInviteGenerator generator,
      UserRepository userRepo,
      SessionLifecycleService sessionLifecycleService,
      @Value("${fhpb.ladder.max-members:20}") int defaultMaxMembers,
      @Value("${fhpb.invites.change-cooldown-seconds:30}") long inviteChangeCooldownSeconds,
      @Value("${fhpb.bootstrap.admin.email:}") String siteWideAdminEmail) {
    this.configs = configs;
    this.memberships = memberships;
    this.generator = generator;
    this.userRepo = userRepo;
    this.sessionLifecycleService = sessionLifecycleService;
    this.defaultMaxMembers = defaultMaxMembers;
    this.inviteChangeCooldownSeconds = inviteChangeCooldownSeconds;
    this.siteWideAdminEmail = siteWideAdminEmail;
  }

  @Override
  @Transactional
  public LadderConfig regenInviteCode(Long ladderConfigId, Long requesterUserId) {
    LadderConfig cfg = lockConfigForInviteChange(ladderConfigId);
    requireAdmin(cfg, requesterUserId);
    if (hasActiveInvite(cfg)) {
      regenerateInviteCode(cfg);
    } else {
      ensureInviteEnabled(cfg);
    }
    return configs.save(cfg);
  }

  @Override
  @Transactional
  public LadderConfig disableInviteCode(Long ladderConfigId, Long requesterUserId) {
    LadderConfig cfg = lockConfigForInviteChange(ladderConfigId);
    requireAdmin(cfg, requesterUserId);
    if (disableInviteCodeInternal(cfg)) {
      configs.save(cfg);
    }
    return cfg;
  }

  @Override
  public LadderConfig syncInviteAvailability(
      LadderConfig cfg, Long requesterUserId, boolean invitesEnabled) {
    requireAdmin(cfg, requesterUserId);
    if (invitesEnabled) {
      ensureInviteEnabled(cfg);
    } else {
      disableInviteCodeInternal(cfg);
    }
    return cfg;
  }

  @Override
  @Transactional
  public LadderConfig joinByInvite(String inviteCode, Long userId) {
    String code = normalizeInviteCode(inviteCode);
    LadderConfig cfg =
        configs
            .findByInviteCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));

    LadderConfig locked = configs.lockById(cfg.getId());

    if (locked.getStatus() != LadderConfig.Status.ACTIVE) {
      throw new IllegalArgumentException("Invalid invite code");
    }
    if (locked.isSessionType()
        && locked.getExpiresAt() != null
        && !locked.getExpiresAt().isAfter(Instant.now())) {
      locked.setStatus(LadderConfig.Status.ARCHIVED);
      locked.setInviteCode(null);
      configs.save(locked);
      throw new IllegalStateException("This match session has expired.");
    }

    var existing = memberships.findByLadderConfigIdAndUserId(locked.getId(), userId);
    boolean enforceMemberCap = locked.getType() != LadderConfig.Type.COMPETITION;
    if (existing.isPresent()) {
      LadderMembership membership = existing.get();
      switch (membership.getState()) {
        case ACTIVE:
          return cfg;
        case BANNED:
          throw new IllegalStateException("You are banned from this ladder");
        case LEFT:
          if (enforceMemberCap) {
            long activeCount =
                memberships
                    .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                        locked.getId(), LadderMembership.State.ACTIVE)
                    .size();
            if (activeCount >= defaultMaxMembers) {
              throw new IllegalStateException("Sorry, that group is full.");
            }
          }
          membership.setState(LadderMembership.State.ACTIVE);
          membership.setLeftAt(null);
          memberships.save(membership);
          return cfg;
        default:
          throw new IllegalStateException("Unexpected state: " + membership.getState());
      }
    }

    if (enforceMemberCap) {
      long activeCount =
          memberships
              .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                  locked.getId(), LadderMembership.State.ACTIVE)
              .size();
      if (activeCount >= defaultMaxMembers) {
        throw new IllegalStateException("Sorry, that group is full.");
      }
    }

    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(locked);
    membership.setUserId(userId);
    membership.setRole(LadderMembership.Role.MEMBER);
    membership.setState(LadderMembership.State.ACTIVE);
    memberships.save(membership);

    return locked;
  }

  @Override
  @Transactional
  public void leaveMember(Long configId, Long requesterUserId, Long membershipId) {
    LadderMembership membership =
        memberships
            .findById(membershipId)
            .orElseThrow(() -> new IllegalArgumentException("Membership not found"));

    if (!membership.getLadderConfig().getId().equals(configId)) {
      throw new SecurityException("Membership does not belong to this ladder");
    }
    if (!membership.getUserId().equals(requesterUserId)) {
      throw new SecurityException("You can only leave your own membership");
    }
    if (membership.getState() == LadderMembership.State.BANNED) {
      throw new IllegalStateException("Banned members cannot change status");
    }
    boolean ownerLeaving =
        Objects.equals(membership.getLadderConfig().getOwnerUserId(), requesterUserId);

    if (ownerLeaving
        && membership.getLadderConfig().isSessionType()
        && membership.getState() != LadderMembership.State.LEFT) {
      sessionLifecycleService.archiveSession(configId, Instant.now());
      return;
    }

    if (membership.getState() != LadderMembership.State.LEFT) {
      membership.setState(LadderMembership.State.LEFT);
      membership.setLeftAt(Instant.now());
      memberships.save(membership);

      if (ownerLeaving && !membership.getLadderConfig().isSessionType()) {
        LadderConfig cfg = membership.getLadderConfig();
        cfg.setPendingDeletion(true);
        cfg.setPendingDeletionAt(Instant.now());
        cfg.setPendingDeletionByUserId(requesterUserId);
        configs.save(cfg);
      }
    }
  }

  @Override
  @Transactional
  public void banMember(Long configId, Long requesterUserId, Long membershipId) {
    requireAdmin(configId, requesterUserId);

    LadderMembership membership =
        memberships
            .findById(membershipId)
            .orElseThrow(() -> new IllegalArgumentException("Membership not found"));

    if (!membership.getLadderConfig().getId().equals(configId)) {
      throw new SecurityException("Membership does not belong to this ladder");
    }
    if (membership.getLadderConfig().isSessionType()) {
      removeSessionMemberInternal(membership, requesterUserId);
      return;
    }
    if (Objects.equals(membership.getUserId(), requesterUserId)) {
      throw new IllegalStateException("Use 'Leave' to remove yourself from the ladder");
    }
    if (membership.getRole() == LadderMembership.Role.ADMIN) {
      throw new IllegalStateException("Cannot ban an admin");
    }
    if (membership.getState() == LadderMembership.State.BANNED) {
      return;
    }

    membership.setState(LadderMembership.State.BANNED);
    membership.setLeftAt(Instant.now());
    memberships.save(membership);
  }

  @Override
  @Transactional
  public void removeSessionMember(Long configId, Long requesterUserId, Long membershipId) {
    requireAdmin(configId, requesterUserId);

    LadderMembership membership =
        memberships
            .findById(membershipId)
            .orElseThrow(() -> new IllegalArgumentException("Membership not found"));

    if (!membership.getLadderConfig().getId().equals(configId)) {
      throw new SecurityException("Membership does not belong to this ladder");
    }
    removeSessionMemberInternal(membership, requesterUserId);
  }

  @Override
  @Transactional
  public void unbanMember(Long configId, Long requesterUserId, Long membershipId) {
    requireAdmin(configId, requesterUserId);

    LadderMembership membership =
        memberships
            .findById(membershipId)
            .orElseThrow(() -> new IllegalArgumentException("Membership not found"));

    if (!membership.getLadderConfig().getId().equals(configId)) {
      throw new SecurityException("Membership does not belong to this ladder");
    }
    if (membership.getState() != LadderMembership.State.BANNED) {
      throw new IllegalStateException("Only banned members can be unbanned");
    }

    membership.setState(LadderMembership.State.LEFT);
    if (membership.getLeftAt() == null) {
      membership.setLeftAt(Instant.now());
    }
    membership.setRole(LadderMembership.Role.MEMBER);
    memberships.save(membership);
  }

  @Override
  @Transactional
  public void requireActiveMember(Long configId, Long userId) {
    LadderMembership membership =
        memberships
            .findByLadderConfigIdAndUserId(configId, userId)
            .orElseThrow(() -> new SecurityException("Not a member"));
    if (membership.getState() != LadderMembership.State.ACTIVE) {
      throw new SecurityException("Not active in ladder");
    }
  }

  @Override
  @Transactional
  public void restorePendingDeletion(Long configId, Long requesterUserId) {
    LadderConfig cfg =
        configs
            .findById(configId)
            .orElseThrow(() -> new IllegalArgumentException("Ladder not found"));

    if (!cfg.isPendingDeletion()) {
      return;
    }
    if (cfg.isCompetitionType()) {
      requireSiteWideCompetitionAdmin(requesterUserId);
    }

    if (!cfg.isCompetitionType()
        && (cfg.getOwnerUserId() == null || !cfg.getOwnerUserId().equals(requesterUserId))) {
      throw new SecurityException("Only the owner can restore this ladder.");
    }

    cfg.setPendingDeletion(false);
    cfg.setPendingDeletionAt(null);
    cfg.setPendingDeletionByUserId(null);

    LadderMembership membership =
        memberships
            .findByLadderConfigIdAndUserId(configId, requesterUserId)
            .orElseGet(
                () -> {
                  LadderMembership created = new LadderMembership();
                  created.setLadderConfig(cfg);
                  created.setUserId(requesterUserId);
                  return created;
                });
    membership.setRole(LadderMembership.Role.ADMIN);
    membership.setState(LadderMembership.State.ACTIVE);
    membership.setLeftAt(null);
    memberships.save(membership);

    configs.save(cfg);
  }

  @Override
  public boolean canRestore(Long configId, Long userId) {
    var cfgOpt = configs.findById(configId);
    if (cfgOpt.isEmpty()) {
      return false;
    }
    LadderConfig cfg = cfgOpt.get();
    if (!cfg.isPendingDeletion()) {
      return false;
    }
    if (cfg.isCompetitionType()) {
      return isConfiguredSiteWideAdmin(userId);
    }
    if (Objects.equals(cfg.getPendingDeletionByUserId(), userId)) {
      return true;
    }

    return memberships
        .findByLadderConfigIdAndUserId(configId, userId)
        .filter(
            membership ->
                membership.getState() == LadderMembership.State.ACTIVE
                    && membership.getRole() == LadderMembership.Role.ADMIN)
        .isPresent();
  }

  @Override
  @Transactional
  public void promoteToAdmin(Long configId, Long requesterUserId, Long membershipId) {
    LadderConfig cfg = lockConfig(configId);
    requireAdmin(cfg, requesterUserId);

    LadderMembership target =
        memberships
            .findById(membershipId)
            .orElseThrow(() -> new IllegalArgumentException("Membership not found"));

    if (!target.getLadderConfig().getId().equals(cfg.getId())) {
      throw new SecurityException("Membership does not belong to this ladder");
    }
    if (target.getState() != LadderMembership.State.ACTIVE) {
      throw new IllegalStateException("Only ACTIVE members can be promoted");
    }
    if (target.getRole() == LadderMembership.Role.ADMIN) {
      return;
    }

    target.setRole(LadderMembership.Role.ADMIN);
    memberships.save(target);
  }

  @Override
  @Transactional
  public void demoteFromAdmin(Long configId, Long requesterUserId, Long membershipId) {
    LadderConfig cfg = lockConfig(configId);
    requireAdmin(cfg, requesterUserId);

    LadderMembership target =
        memberships
            .findById(membershipId)
            .orElseThrow(() -> new IllegalArgumentException("Membership not found"));

    if (!target.getLadderConfig().getId().equals(cfg.getId())) {
      throw new SecurityException("Membership does not belong to this ladder");
    }
    if (target.getRole() != LadderMembership.Role.ADMIN) {
      return;
    }
    if (cfg.getOwnerUserId() != null && cfg.getOwnerUserId().equals(target.getUserId())) {
      throw new IllegalStateException("Cannot demote the ladder owner");
    }

    long activeAdmins =
        memberships.countByLadderConfigIdAndRoleAndState(
            configId, LadderMembership.Role.ADMIN, LadderMembership.State.ACTIVE);
    if (activeAdmins <= 1L) {
      throw new IllegalStateException("At least one active admin is required");
    }

    target.setRole(LadderMembership.Role.MEMBER);
    memberships.save(target);
  }

  @Override
  @Transactional
  public LadderConfig updateTitle(Long configId, Long requesterUserId, String title) {
    LadderConfig cfg = lockConfig(configId);
    requireAdmin(cfg, requesterUserId);
    if (cfg.isCompetitionType()) {
      throw new IllegalStateException(
          "Competition settings are managed from the system competition page.");
    }
    cfg.setTitle(InputValidation.requireGroupTitle(title));
    return configs.saveAndFlush(cfg);
  }

  public void requireAdmin(Long configId, Long requesterUserId) {
    requireAdmin(
        configs
            .findById(configId)
            .orElseThrow(() -> new IllegalArgumentException("Ladder not found")),
        requesterUserId);
  }

  public void requireAdmin(LadderConfig config, Long requesterUserId) {
    if (config == null || config.getId() == null) {
      throw new IllegalArgumentException("Ladder not found");
    }
    if (config.isCompetitionType()) {
      requireSiteWideCompetitionAdmin(requesterUserId);
      return;
    }
    LadderMembership membership =
        memberships
            .findByLadderConfigIdAndUserId(config.getId(), requesterUserId)
            .orElseThrow(() -> new SecurityException("Not a member"));
    if (membership.getState() != LadderMembership.State.ACTIVE) {
      throw new SecurityException("Not active in ladder");
    }
    if (membership.getRole() != LadderMembership.Role.ADMIN) {
      throw new SecurityException("Admin privileges required");
    }
  }

  private void requireSiteWideCompetitionAdmin(Long requesterUserId) {
    if (!isConfiguredSiteWideAdmin(requesterUserId)) {
      throw new SecurityException(
          "Only the site-wide admin can manage the global competition ladder");
    }
  }

  private boolean isConfiguredSiteWideAdmin(Long requesterUserId) {
    if (requesterUserId == null) {
      return false;
    }
    String configuredEmail = normalizedEmail(siteWideAdminEmail);
    if (configuredEmail.isEmpty()) {
      return false;
    }
    return userRepo
        .findById(requesterUserId)
        .map(User::getEmail)
        .map(this::normalizedEmail)
        .filter(configuredEmail::equals)
        .isPresent();
  }

  private String normalizedEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  private LadderConfig lockConfigForInviteChange(Long ladderConfigId) {
    LadderConfig cfg = configs.lockById(ladderConfigId);
    if (cfg == null) {
      throw new IllegalArgumentException("Ladder not found");
    }
    return cfg;
  }

  private void ensureInviteEnabled(LadderConfig cfg) {
    if (hasActiveInvite(cfg)) {
      return;
    }
    Instant now = Instant.now();
    // Re-enabling after a manual disable should be immediate. Cooldown still applies
    // while an invite is active and for explicit regenerate actions.
    cfg.setInviteCode(uniqueInvite(cfg));
    cfg.setLastInviteChangeAt(now);
  }

  private void regenerateInviteCode(LadderConfig cfg) {
    Instant now = Instant.now();
    enforceInviteChangeCooldown(cfg, now);
    cfg.setInviteCode(uniqueInvite(cfg));
    cfg.setLastInviteChangeAt(now);
  }

  private boolean disableInviteCodeInternal(LadderConfig cfg) {
    if (!hasActiveInvite(cfg)) {
      return false;
    }
    Instant now = Instant.now();
    enforceInviteChangeCooldown(cfg, now);
    cfg.setInviteCode(null);
    cfg.setLastInviteChangeAt(now);
    return true;
  }

  private boolean hasActiveInvite(LadderConfig cfg) {
    return cfg != null && cfg.getInviteCode() != null && !cfg.getInviteCode().isBlank();
  }

  private void enforceInviteChangeCooldown(LadderConfig cfg, Instant now) {
    Duration cooldown = inviteChangeCooldown();
    if (cooldown.isZero() || cooldown.isNegative()) {
      return;
    }
    Instant lastInviteChangeAt = cfg.getLastInviteChangeAt();
    if (lastInviteChangeAt == null) {
      return;
    }
    Instant allowedAt = lastInviteChangeAt.plus(cooldown);
    if (!now.isBefore(allowedAt)) {
      return;
    }
    throw new InviteChangeCooldownException(
        "Invite changes are on cooldown. Try again in "
            + formatInviteCooldown(Duration.between(now, allowedAt))
            + ".",
        allowedAt);
  }

  private Duration inviteChangeCooldown() {
    return Duration.ofSeconds(Math.max(0L, inviteChangeCooldownSeconds));
  }

  private String formatInviteCooldown(Duration remaining) {
    long totalSeconds = Math.max(1L, (remaining.toMillis() + 999L) / 1000L);
    long minutes = totalSeconds / 60L;
    long seconds = totalSeconds % 60L;
    if (minutes > 0L && seconds > 0L) {
      return minutes
          + (minutes == 1L ? " minute " : " minutes ")
          + seconds
          + (seconds == 1L ? " second" : " seconds");
    }
    if (minutes > 0L) {
      return minutes + (minutes == 1L ? " minute" : " minutes");
    }
    return totalSeconds + (totalSeconds == 1L ? " second" : " seconds");
  }

  private String uniqueInvite(LadderConfig cfg) {
    boolean sessionCode = cfg != null && cfg.isSessionType();
    for (int i = 0; i < MAX_INVITE_GENERATION_ATTEMPTS; i++) {
      String code = sessionCode ? generator.generateSessionCode() : generator.generate();
      code = code == null ? null : code.toUpperCase(Locale.ROOT);
      if (code != null && configs.findByInviteCode(code).isEmpty()) {
        return code;
      }
    }
    throw new IllegalStateException("Unable to generate unique invite code");
  }

  private String normalizeInviteCode(String inviteCode) {
    String normalized = SessionInviteCodeSupport.normalizeForLookup(inviteCode);
    return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
  }

  private void removeSessionMemberInternal(LadderMembership membership, Long requesterUserId) {
    if (membership == null
        || membership.getLadderConfig() == null
        || !membership.getLadderConfig().isSessionType()) {
      throw new IllegalStateException("Only match sessions support removing players.");
    }
    if (Objects.equals(membership.getUserId(), requesterUserId)) {
      throw new IllegalStateException("Use 'Leave Session' to remove yourself from the session");
    }
    if (Objects.equals(membership.getLadderConfig().getOwnerUserId(), membership.getUserId())) {
      throw new IllegalStateException("Cannot remove the session owner");
    }
    if (membership.getState() != LadderMembership.State.LEFT) {
      membership.setState(LadderMembership.State.LEFT);
      membership.setLeftAt(Instant.now());
      memberships.save(membership);
    }
  }

  private LadderConfig lockConfig(Long configId) {
    LadderConfig cfg = configs.lockById(configId);
    if (cfg == null) {
      throw new IllegalArgumentException("Ladder not found");
    }
    return cfg;
  }
}
