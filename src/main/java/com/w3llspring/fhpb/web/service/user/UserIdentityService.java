package com.w3llspring.fhpb.web.service.user;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserDisplayNameAuditRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserDisplayNameAudit;
import com.w3llspring.fhpb.web.service.CompetitionDisplayNameModerationService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserIdentityService {

  private static final Logger log = LoggerFactory.getLogger(UserIdentityService.class);
  private static final int PUBLIC_CODE_BACKFILL_BATCH_SIZE = 100;
  private static final int PUBLIC_CODE_GENERATION_ATTEMPTS = 24;

  private final UserRepository userRepository;
  private final LadderMembershipRepository ladderMembershipRepository;
  private final UserDisplayNameAuditRepository userDisplayNameAuditRepository;
  private final LadderV2Service ladderV2Service;
  private CompetitionDisplayNameModerationService competitionDisplayNameModerationService;

  @Autowired
  public UserIdentityService(
      UserRepository userRepository,
      LadderMembershipRepository ladderMembershipRepository,
      UserDisplayNameAuditRepository userDisplayNameAuditRepository,
      LadderV2Service ladderV2Service,
      CompetitionDisplayNameModerationService competitionDisplayNameModerationService) {
    this.userRepository = userRepository;
    this.ladderMembershipRepository = ladderMembershipRepository;
    this.userDisplayNameAuditRepository = userDisplayNameAuditRepository;
    this.ladderV2Service = ladderV2Service;
    this.competitionDisplayNameModerationService = competitionDisplayNameModerationService;
  }

  public UserIdentityService(
      UserRepository userRepository,
      LadderMembershipRepository ladderMembershipRepository,
      UserDisplayNameAuditRepository userDisplayNameAuditRepository,
      LadderV2Service ladderV2Service) {
    this(
        userRepository,
        ladderMembershipRepository,
        userDisplayNameAuditRepository,
        ladderV2Service,
        null);
  }

  @Transactional
  public int backfillMissingPublicCodes() {
    int totalAssigned = 0;
    while (true) {
      List<User> users =
          userRepository.findByPublicCodeIsNull(PageRequest.of(0, PUBLIC_CODE_BACKFILL_BATCH_SIZE));
      if (users.isEmpty()) {
        return totalAssigned;
      }

      Set<String> reservedCodes = new HashSet<>();
      for (User user : users) {
        if (user.getPublicCode() == null || user.getPublicCode().isBlank()) {
          user.setPublicCode(generateUniquePublicCode(reservedCodes));
          totalAssigned++;
        }
      }
      userRepository.saveAll(users);
      userRepository.flush();
    }
  }

  @Transactional
  public DisplayNameChangeResult changeDisplayName(
      Long userId,
      String newDisplayName,
      Long changedByUserId,
      Instant changedAt,
      Duration cooldown) {
    if (userId == null) {
      throw new IllegalArgumentException("User is required.");
    }
    if (newDisplayName == null) {
      throw new IllegalArgumentException("Display name is required.");
    }

    Instant effectiveChangedAt = changedAt != null ? changedAt : Instant.now();
    Duration effectiveCooldown =
        cooldown != null && !cooldown.isNegative() ? cooldown : Duration.ZERO;
    User user =
        userRepository
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

    if (Objects.equals(user.getNickName(), newDisplayName)) {
      return DisplayNameChangeResult.unchanged(user);
    }

    Instant lastChange = user.getLastDisplayNameChangeAt();
    if (lastChange != null && !effectiveCooldown.isZero()) {
      Instant allowedAt = lastChange.plus(effectiveCooldown);
      if (allowedAt.isAfter(effectiveChangedAt)) {
        return DisplayNameChangeResult.cooldown(user, allowedAt);
      }
    }

    User conflictingUser = userRepository.findByNickName(newDisplayName);
    if (conflictingUser != null && !Objects.equals(conflictingUser.getId(), user.getId())) {
      return DisplayNameChangeResult.taken(user);
    }

    String oldDisplayName = user.getNickName();
    user.setNickName(newDisplayName);
    user.setLastDisplayNameChangeAt(effectiveChangedAt);

    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      return DisplayNameChangeResult.taken(user);
    }

    recordDisplayNameChange(
        user.getId(), changedByUserId, oldDisplayName, newDisplayName, effectiveChangedAt);
    ladderV2Service.refreshDisplayNameArtifacts(user);
    if (competitionDisplayNameModerationService != null) {
      competitionDisplayNameModerationService.clearOverrideIfSubstantialRename(
          user, oldDisplayName, newDisplayName);
    }
    return DisplayNameChangeResult.changed(user, effectiveChangedAt);
  }

  public void logBackfillSummary(int totalAssigned) {
    if (totalAssigned > 0) {
      log.info("Assigned public identity codes to {} existing users.", totalAssigned);
    } else {
      log.debug("All users already have public identity codes.");
    }
  }

  private void recordDisplayNameChange(
      Long userId,
      Long changedByUserId,
      String oldDisplayName,
      String newDisplayName,
      Instant changedAt) {
    List<LadderMembership> activeMemberships =
        ladderMembershipRepository.findByUserIdAndState(userId, LadderMembership.State.ACTIVE);

    if (activeMemberships.isEmpty()) {
      userDisplayNameAuditRepository.save(
          newAuditRow(userId, null, changedByUserId, oldDisplayName, newDisplayName, changedAt));
      return;
    }

    List<UserDisplayNameAudit> auditRows =
        activeMemberships.stream()
            .map(
                membership ->
                    newAuditRow(
                        userId,
                        membership.getLadderConfig() != null
                            ? membership.getLadderConfig().getId()
                            : null,
                        changedByUserId,
                        oldDisplayName,
                        newDisplayName,
                        changedAt))
            .toList();
    userDisplayNameAuditRepository.saveAll(auditRows);
  }

  private UserDisplayNameAudit newAuditRow(
      Long userId,
      Long ladderConfigId,
      Long changedByUserId,
      String oldDisplayName,
      String newDisplayName,
      Instant changedAt) {
    UserDisplayNameAudit audit = new UserDisplayNameAudit();
    audit.setUserId(userId);
    audit.setLadderConfigId(ladderConfigId);
    audit.setChangedByUserId(changedByUserId != null ? changedByUserId : userId);
    audit.setOldDisplayName(oldDisplayName);
    audit.setNewDisplayName(newDisplayName);
    audit.setChangedAt(changedAt);
    return audit;
  }

  private String generateUniquePublicCode(Set<String> reservedCodes) {
    for (int attempt = 0; attempt < PUBLIC_CODE_GENERATION_ATTEMPTS; attempt++) {
      String code = UserPublicCodeGenerator.nextCode();
      if (reservedCodes.contains(code) || userRepository.existsByPublicCode(code)) {
        continue;
      }
      reservedCodes.add(code);
      return code;
    }
    throw new IllegalStateException("Unable to generate a unique public code.");
  }

  public record DisplayNameChangeResult(
      DisplayNameChangeStatus status, User user, Instant changedAt, Instant allowedAt) {

    public static DisplayNameChangeResult changed(User user, Instant changedAt) {
      return new DisplayNameChangeResult(DisplayNameChangeStatus.CHANGED, user, changedAt, null);
    }

    public static DisplayNameChangeResult unchanged(User user) {
      return new DisplayNameChangeResult(DisplayNameChangeStatus.UNCHANGED, user, null, null);
    }

    public static DisplayNameChangeResult cooldown(User user, Instant allowedAt) {
      return new DisplayNameChangeResult(DisplayNameChangeStatus.COOLDOWN, user, null, allowedAt);
    }

    public static DisplayNameChangeResult taken(User user) {
      return new DisplayNameChangeResult(DisplayNameChangeStatus.TAKEN, user, null, null);
    }
  }

  public enum DisplayNameChangeStatus {
    CHANGED,
    UNCHANGED,
    COOLDOWN,
    TAKEN
  }
}
