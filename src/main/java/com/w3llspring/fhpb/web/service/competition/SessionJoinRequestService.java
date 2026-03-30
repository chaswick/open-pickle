package com.w3llspring.fhpb.web.service.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.SessionJoinRequestRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.SessionJoinRequest;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.util.SessionInviteCodeSupport;
import com.w3llspring.fhpb.web.util.UserPublicName;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionJoinRequestService {

  public enum SubmissionState {
    ALREADY_MEMBER,
    PENDING_APPROVAL
  }

  public record SubmissionOutcome(SubmissionState state, Long sessionId, Long requestId) {}

  public record PendingRequestView(
      Long id,
      Long sessionId,
      Long requesterUserId,
      String requesterDisplayName,
      String requesterPublicCode,
      Instant requestedAt,
      Instant expiresAt) {}

  public record RequestStatusView(
      Long id,
      Long sessionId,
      String sessionTitle,
      SessionJoinRequest.Status status,
      Instant requestedAt,
      Instant expiresAt,
      String message,
      String redirectUrl) {

    public boolean pending() {
      return status == SessionJoinRequest.Status.PENDING;
    }
  }

  public record ReviewOutcome(SessionJoinRequest.Status status, String message) {
    public boolean pending() {
      return status == SessionJoinRequest.Status.PENDING;
    }
  }

  private final LadderConfigRepository configs;
  private final LadderMembershipRepository memberships;
  private final SessionJoinRequestRepository requests;
  private final UserRepository userRepo;
  private final GroupAdministrationService groupAdministration;
  private final long joinRequestTtlSeconds;

  @Value("${fhpb.sessions.invite-active-seconds:1800}")
  private long sessionInviteActiveSeconds = 1800L;

  public SessionJoinRequestService(
      LadderConfigRepository configs,
      LadderMembershipRepository memberships,
      SessionJoinRequestRepository requests,
      UserRepository userRepo,
      GroupAdministrationService groupAdministration,
      @Value("${fhpb.sessions.join-request-ttl-seconds:300}") long joinRequestTtlSeconds) {
    this.configs = configs;
    this.memberships = memberships;
    this.requests = requests;
    this.userRepo = userRepo;
    this.groupAdministration = groupAdministration;
    this.joinRequestTtlSeconds = joinRequestTtlSeconds;
  }

  @Transactional
  public SubmissionOutcome submitByInvite(String inviteCode, Long requesterUserId) {
    String normalizedCode = normalizeInviteCode(inviteCode);
    LadderConfig cfg =
        configs
            .findByInviteCode(normalizedCode)
            .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));
    LadderConfig locked = lockSession(cfg.getId());
    return submitBySession(locked, normalizedCode, requesterUserId);
  }

  @Transactional
  public SubmissionOutcome submitBySessionId(Long sessionId, Long requesterUserId) {
    LadderConfig locked = lockSession(sessionId);
    return submitBySession(locked, normalizeInviteCode(locked.getInviteCode()), requesterUserId);
  }

  private SubmissionOutcome submitBySession(
      LadderConfig locked, String normalizedCode, Long requesterUserId) {
    validateSessionInvite(locked, normalizedCode);

    LadderMembership membership =
        memberships.findByLadderConfigIdAndUserId(locked.getId(), requesterUserId).orElse(null);
    if (membership != null) {
      if (membership.getState() == LadderMembership.State.ACTIVE) {
        return new SubmissionOutcome(SubmissionState.ALREADY_MEMBER, locked.getId(), null);
      }
      if (membership.getState() == LadderMembership.State.BANNED) {
        throw new IllegalStateException("You are banned from this ladder");
      }
    }

    Instant now = Instant.now();
    SessionJoinRequest request =
        requests
            .findByLadderConfigIdAndRequesterUserId(locked.getId(), requesterUserId)
            .orElseGet(
                () -> {
                  SessionJoinRequest created = new SessionJoinRequest();
                  created.setLadderConfig(locked);
                  created.setRequesterUserId(requesterUserId);
                  return created;
                });
    request = expireIfNeeded(request, now);
    if (request.getStatus() == SessionJoinRequest.Status.PENDING
        && Objects.equals(request.getInviteCodeSnapshot(), normalizedCode)) {
      return new SubmissionOutcome(
          SubmissionState.PENDING_APPROVAL, locked.getId(), request.getId());
    }

    request.setInviteCodeSnapshot(normalizedCode);
    request.setStatus(SessionJoinRequest.Status.PENDING);
    request.setRequestedAt(now);
    request.setExpiresAt(now.plus(requestTtl()));
    request.setResolvedAt(null);
    request.setResolvedByUserId(null);
    SessionJoinRequest saved = requests.save(request);
    return new SubmissionOutcome(SubmissionState.PENDING_APPROVAL, locked.getId(), saved.getId());
  }

  @Transactional
  public List<PendingRequestView> listPendingForAdmin(Long sessionId, Long adminUserId) {
    LadderConfig session = findSession(sessionId);
    groupAdministration.requireAdmin(session, adminUserId);
    List<SessionJoinRequest> pending =
        requests.findByLadderConfigIdAndStatusOrderByRequestedAtAsc(
            sessionId, SessionJoinRequest.Status.PENDING);
    Instant now = Instant.now();
    List<SessionJoinRequest> activePending =
        pending.stream()
            .map(request -> expireIfNeeded(request, now))
            .filter(request -> request.getStatus() == SessionJoinRequest.Status.PENDING)
            .toList();
    Map<Long, User> users =
        userRepo.findAllById(
                activePending.stream()
                    .map(SessionJoinRequest::getRequesterUserId)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(User::getId, user -> user));
    return activePending.stream()
        .map(
            request -> {
              User requester = users.get(request.getRequesterUserId());
              return new PendingRequestView(
                  request.getId(),
                  sessionId,
                  request.getRequesterUserId(),
                  UserPublicName.forUser(requester),
                  requester != null ? requester.getPublicCode() : null,
                  request.getRequestedAt(),
                  request.getExpiresAt());
            })
        .toList();
  }

  @Transactional
  public ReviewOutcome approve(Long sessionId, Long requestId, Long adminUserId) {
    LadderConfig session = lockSession(sessionId);
    groupAdministration.requireAdmin(session, adminUserId);
    SessionJoinRequest request = lockRequest(requestId, sessionId);
    Instant now = Instant.now();
    request = expireIfNeeded(request, now);
    if (request.getStatus() != SessionJoinRequest.Status.PENDING) {
      return new ReviewOutcome(request.getStatus(), reviewMessageFor(request.getStatus()));
    }

    LadderMembership membership =
        memberships.findByLadderConfigIdAndUserId(sessionId, request.getRequesterUserId()).orElse(null);
    if (membership != null) {
      if (membership.getState() == LadderMembership.State.ACTIVE) {
        markApproved(request, adminUserId, now);
        requests.save(request);
        return new ReviewOutcome(SessionJoinRequest.Status.APPROVED, "Player joined the session.");
      }
      if (membership.getState() == LadderMembership.State.BANNED) {
        markDenied(request, adminUserId, now);
        requests.save(request);
        return new ReviewOutcome(
            SessionJoinRequest.Status.DENIED, "This player is banned from the session.");
      }
    }

    try {
      validateSessionInvite(session, request.getInviteCodeSnapshot());
      groupAdministration.joinByInvite(request.getInviteCodeSnapshot(), request.getRequesterUserId());
      markApproved(request, adminUserId, now);
      requests.save(request);
      return new ReviewOutcome(SessionJoinRequest.Status.APPROVED, "Player joined the session.");
    } catch (IllegalArgumentException ex) {
      markExpired(request, now);
      requests.save(request);
      return new ReviewOutcome(
          SessionJoinRequest.Status.EXPIRED,
          "That invite is no longer active. Ask the host for the latest session code.");
    } catch (IllegalStateException ex) {
      if (ex.getMessage() != null && ex.getMessage().contains("no longer active")) {
        markExpired(request, now);
        requests.save(request);
        return new ReviewOutcome(
            SessionJoinRequest.Status.EXPIRED,
            "That invite is no longer active. Ask the host for the latest session code.");
      }
      if ("Sorry, that group is full.".equals(ex.getMessage())) {
        return new ReviewOutcome(
            SessionJoinRequest.Status.PENDING,
            "Session is full right now. Remove a player or wait for someone to leave.");
      }
      if ("You are banned from this ladder".equals(ex.getMessage())) {
        markDenied(request, adminUserId, now);
        requests.save(request);
        return new ReviewOutcome(
            SessionJoinRequest.Status.DENIED, "This player is banned from the session.");
      }
      if ("This match session has expired.".equals(ex.getMessage())) {
        markExpired(request, now);
        requests.save(request);
        return new ReviewOutcome(
            SessionJoinRequest.Status.EXPIRED,
            "This session expired before the request could be approved.");
      }
      return new ReviewOutcome(
          SessionJoinRequest.Status.PENDING, "Unable to approve right now. Try again.");
    }
  }

  @Transactional
  public ReviewOutcome deny(Long sessionId, Long requestId, Long adminUserId) {
    LadderConfig session = lockSession(sessionId);
    groupAdministration.requireAdmin(session, adminUserId);
    SessionJoinRequest request = lockRequest(requestId, sessionId);
    Instant now = Instant.now();
    request = expireIfNeeded(request, now);
    if (request.getStatus() != SessionJoinRequest.Status.PENDING) {
      return new ReviewOutcome(request.getStatus(), reviewMessageFor(request.getStatus()));
    }
    markDenied(request, adminUserId, now);
    requests.save(request);
    return new ReviewOutcome(
        SessionJoinRequest.Status.DENIED, "Join request declined for this session.");
  }

  @Transactional
  public RequestStatusView getStatusForRequester(Long requestId, Long requesterUserId) {
    SessionJoinRequest request =
        requests.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Join request not found"));
    if (!Objects.equals(request.getRequesterUserId(), requesterUserId)) {
      throw new SecurityException("Join request not found");
    }
    request = expireIfNeeded(request, Instant.now());
    return toStatusView(request);
  }

  private RequestStatusView toStatusView(SessionJoinRequest request) {
    LadderConfig session = request.getLadderConfig();
    Long sessionId = session != null ? session.getId() : null;
    return new RequestStatusView(
        request.getId(),
        sessionId,
        session != null ? session.getTitle() : "Session",
        request.getStatus(),
        request.getRequestedAt(),
        request.getExpiresAt(),
        statusMessageFor(request.getStatus()),
        request.getStatus() == SessionJoinRequest.Status.APPROVED && sessionId != null
            ? "/groups/" + sessionId + "?joined=1"
            : null);
  }

  private String statusMessageFor(SessionJoinRequest.Status status) {
    if (status == SessionJoinRequest.Status.APPROVED) {
      return "Approved. Opening the session.";
    }
    if (status == SessionJoinRequest.Status.DENIED) {
      return "The session host declined your request.";
    }
    if (status == SessionJoinRequest.Status.EXPIRED) {
      return "This request expired. Ask the host for the latest session code and try again.";
    }
    return "Waiting for the session host to approve your request.";
  }

  private String reviewMessageFor(SessionJoinRequest.Status status) {
    if (status == SessionJoinRequest.Status.APPROVED) {
      return "Player already joined the session.";
    }
    if (status == SessionJoinRequest.Status.DENIED) {
      return "This join request was already denied.";
    }
    if (status == SessionJoinRequest.Status.EXPIRED) {
      return "This join request already expired.";
    }
    return "Join request is still pending.";
  }

  private SessionJoinRequest expireIfNeeded(SessionJoinRequest request, Instant now) {
    if (request == null
        || request.getStatus() != SessionJoinRequest.Status.PENDING
        || request.getExpiresAt() == null
        || request.getExpiresAt().isAfter(now)) {
      return request;
    }
    request.setStatus(SessionJoinRequest.Status.EXPIRED);
    request.setResolvedAt(now);
    request.setResolvedByUserId(null);
    return requests.save(request);
  }

  private void validateSessionInvite(LadderConfig session, String inviteCode) {
    if (session == null || session.getId() == null || !session.isSessionType()) {
      throw new IllegalArgumentException("Invalid invite code");
    }
    if (session.getStatus() != LadderConfig.Status.ACTIVE) {
      throw new IllegalArgumentException("Invalid invite code");
    }
    if (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(Instant.now())) {
      session.setStatus(LadderConfig.Status.ARCHIVED);
      session.setInviteCode(null);
      configs.save(session);
      throw new IllegalStateException("This match session has expired.");
    }
    if (!SessionInviteCodeSupport.isCurrentlyActive(
        session.getInviteCode(), session.getLastInviteChangeAt(), sessionInviteActiveSeconds, Instant.now())) {
      session.setInviteCode(null);
      configs.save(session);
      throw new IllegalStateException("That invite is no longer active.");
    }
    String currentInvite = normalizeInviteCode(session.getInviteCode());
    if (!Objects.equals(currentInvite, normalizeInviteCode(inviteCode))) {
      throw new IllegalArgumentException("Invalid invite code");
    }
  }

  private String normalizeInviteCode(String inviteCode) {
    String normalized = SessionInviteCodeSupport.normalizeForLookup(inviteCode);
    return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
  }

  private void markApproved(SessionJoinRequest request, Long adminUserId, Instant now) {
    request.setStatus(SessionJoinRequest.Status.APPROVED);
    request.setResolvedAt(now);
    request.setResolvedByUserId(adminUserId);
  }

  private void markDenied(SessionJoinRequest request, Long adminUserId, Instant now) {
    request.setStatus(SessionJoinRequest.Status.DENIED);
    request.setResolvedAt(now);
    request.setResolvedByUserId(adminUserId);
  }

  private void markExpired(SessionJoinRequest request, Instant now) {
    request.setStatus(SessionJoinRequest.Status.EXPIRED);
    request.setResolvedAt(now);
    request.setResolvedByUserId(null);
  }

  private LadderConfig lockSession(Long sessionId) {
    LadderConfig session = configs.lockById(sessionId);
    if (session == null || !session.isSessionType()) {
      throw new IllegalArgumentException("Session not found");
    }
    return session;
  }

  private LadderConfig findSession(Long sessionId) {
    LadderConfig session = configs.findById(sessionId).orElse(null);
    if (session == null || !session.isSessionType()) {
      throw new IllegalArgumentException("Session not found");
    }
    return session;
  }

  private SessionJoinRequest lockRequest(Long requestId, Long sessionId) {
    SessionJoinRequest request =
        requests.lockById(requestId).orElseThrow(() -> new IllegalArgumentException("Join request not found"));
    if (request.getLadderConfig() == null
        || !Objects.equals(request.getLadderConfig().getId(), sessionId)) {
      throw new SecurityException("Join request not found");
    }
    return request;
  }

  private Duration requestTtl() {
    return Duration.ofSeconds(Math.max(1L, joinRequestTtlSeconds));
  }
}
