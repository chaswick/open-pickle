package com.w3llspring.fhpb.web.service.auth;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserCourtName;
import com.w3llspring.fhpb.web.service.GlobalLadderBootstrapService;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationService;
import com.w3llspring.fhpb.web.service.competition.SessionJoinRequestService;
import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import com.w3llspring.fhpb.web.service.user.UserCreationService;
import com.w3llspring.fhpb.web.util.InputValidation;
import com.w3llspring.fhpb.web.util.SessionInviteCodeSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerAccessService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final UserRepository users;
  private final UserCreationService userCreation;
  private final LadderConfigRepository sessions;
  private final LadderMembershipRepository memberships;
  private final GroupAdministrationService groups;
  private final SessionJoinRequestService requests;
  private final GlobalLadderBootstrapService enrollment;
  private final DisplayNameModerationService moderation;
  private final long inviteSeconds;
  private final int maxOwned;

  public PlayerAccessService(
      UserRepository users,
      UserCreationService userCreation,
      LadderConfigRepository sessions,
      LadderMembershipRepository memberships,
      GroupAdministrationService groups,
      SessionJoinRequestService requests,
      GlobalLadderBootstrapService enrollment,
      DisplayNameModerationService moderation,
      @Value("${fhpb.sessions.invite-active-seconds:1800}") long inviteSeconds,
      @Value("${fhpb.ladder.max-per-user:3}") int maxOwned) {
    this.users = users;
    this.userCreation = userCreation;
    this.sessions = sessions;
    this.memberships = memberships;
    this.groups = groups;
    this.requests = requests;
    this.enrollment = enrollment;
    this.moderation = moderation;
    this.inviteSeconds = inviteSeconds;
    this.maxOwned = maxOwned;
  }

  @Transactional(readOnly = true)
  public LadderConfig preview(String invite) {
    return validate(sessions.findByInviteCode(normalize(invite)).orElse(null));
  }

  public Joined createAndJoin(String invite, String name, boolean acceptTerms, boolean direct) {
    if (!acceptTerms) throw new IllegalArgumentException("Please agree to the terms to join.");
    String cleanName = InputValidation.requireDisplayName(name, moderation);
    return userCreation.create(code -> createAndJoinOnce(invite, cleanName, direct, code));
  }

  private Joined createAndJoinOnce(String invite, String cleanName, boolean direct, String code) {
    LadderConfig found = preview(invite);
    LadderConfig session = validate(sessions.lockById(found.getId()));
    if (memberships
            .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                session.getId(), LadderMembership.State.ACTIVE)
            .size()
        >= LadderConfig.MAX_SESSION_MEMBERS)
      throw new IllegalArgumentException("This session is full. Ask the organizer for help.");
    User user = new User();
    user.setPublicCode(code);
    user.setNameOnlyAccount(true);
    user.setEmail(UUID.randomUUID().toString().replace("-", "") + "@g.invalid");
    user.setPassword(newInternalCredential());
    user.setAdmin(false);
    user.setNickName(cleanName);
    user.setAcknowledgedTermsAt(Instant.now());
    user.setRegisteredAt(Instant.now());
    user.setMaxOwnedLadders(maxOwned);
    var alias = new UserCourtName();
    alias.setUser(user);
    alias.setAlias(cleanName);
    user.getCourtNames().add(alias);
    users.saveAndFlush(user);
    enrollment.enrollUserIfConfigured(user);
    return new Joined(user, joinExisting(invite, user, direct));
  }

  @Transactional
  public String joinExisting(String invite, User user, boolean direct) {
    preview(invite);
    if (direct) return "/groups/" + groups.joinByInvite(normalize(invite), user.getId()).getId();
    var outcome = requests.submitByInvite(normalize(invite), user.getId());
    return outcome.state() == SessionJoinRequestService.SubmissionState.ALREADY_MEMBER
        ? "/groups/" + outcome.sessionId()
        : "/groups/join-requests/" + outcome.requestId();
  }

  @Transactional
  public String createRecoveryCode(Long userId) {
    User user = users.findByIdForUpdate(userId).orElseThrow();
    if (!user.isNameOnlyAccount())
      throw new IllegalArgumentException("This account already has a normal sign-in.");
    String code = randomSecret();
    user.setRecoveryCodeHash(hash(code));
    users.save(user);
    return code;
  }

  @Transactional(readOnly = true)
  public User recover(String code) {
    String clean = code == null ? "" : code.trim();
    if (!clean.matches("[A-Za-z0-9_-]{43}")) throw invalidRecovery();
    return users
        .findByRecoveryCodeHash(hash(clean))
        .filter(User::isNameOnlyAccount)
        .filter(user -> !user.isAdmin())
        .orElseThrow(PlayerAccessService::invalidRecovery);
  }

  private LadderConfig validate(LadderConfig session) {
    if (session == null
        || !session.isSessionType()
        || session.getStatus() != LadderConfig.Status.ACTIVE
        || (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(Instant.now()))
        || !SessionInviteCodeSupport.isCurrentlyActive(session, inviteSeconds, Instant.now())) {
      throw new IllegalArgumentException(
          "This invite is no longer available. Ask the organizer for a new link.");
    }
    return session;
  }

  private static String normalize(String code) {
    String value = SessionInviteCodeSupport.normalizeForLookup(code);
    if (value == null || value.length() > 64)
      throw new IllegalArgumentException("Please use a valid session invite.");
    return value;
  }

  static String newInternalCredential() {
    // Not a usable password encoding. This secret only binds Spring's signed cookie to the account.
    return randomSecret();
  }

  static String randomSecret() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hash(String code) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(code.getBytes(StandardCharsets.US_ASCII)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static IllegalArgumentException invalidRecovery() {
    return new IllegalArgumentException(
        "That recovery code is not valid. Use the private code you saved, not your member ID.");
  }

  public record Joined(User user, String destination) {}
}
