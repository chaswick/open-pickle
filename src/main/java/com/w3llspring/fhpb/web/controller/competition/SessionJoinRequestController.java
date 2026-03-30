package com.w3llspring.fhpb.web.controller.competition;

import com.w3llspring.fhpb.web.model.SessionJoinRequest;
import com.w3llspring.fhpb.web.service.competition.SessionJoinRequestService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class SessionJoinRequestController {

  private final SessionJoinRequestService sessionJoinRequests;

  public SessionJoinRequestController(SessionJoinRequestService sessionJoinRequests) {
    this.sessionJoinRequests = sessionJoinRequests;
  }

  public record PendingJoinRequestResponse(
      Long id,
      Long sessionId,
      Long requesterUserId,
      String requesterDisplayName,
      String requesterPublicCode,
      java.time.Instant requestedAt,
      java.time.Instant expiresAt) {}

  public record JoinRequestStatusResponse(
      Long id,
      Long sessionId,
      String sessionTitle,
      String status,
      java.time.Instant requestedAt,
      java.time.Instant expiresAt,
      String message,
      String redirectUrl) {}

  public record JoinRequestReviewResponse(String status, String message) {}

  @GetMapping("/api/sessions/{sessionId}/join-requests")
  public List<PendingJoinRequestResponse> pending(
      @PathVariable("sessionId") Long sessionId, Authentication auth) {
    Long userId = requireCurrentUserId(auth);
    try {
      return sessionJoinRequests.listPendingForAdmin(sessionId, userId).stream()
          .map(
              request ->
                  new PendingJoinRequestResponse(
                      request.id(),
                      request.sessionId(),
                      request.requesterUserId(),
                      request.requesterDisplayName(),
                      request.requesterPublicCode(),
                      request.requestedAt(),
                      request.expiresAt()))
          .toList();
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  @PostMapping("/api/sessions/{sessionId}/join-requests/{requestId}/approve")
  public ResponseEntity<JoinRequestReviewResponse> approve(
      @PathVariable("sessionId") Long sessionId,
      @PathVariable("requestId") Long requestId,
      Authentication auth) {
    Long userId = requireCurrentUserId(auth);
    try {
      SessionJoinRequestService.ReviewOutcome outcome =
          sessionJoinRequests.approve(sessionId, requestId, userId);
      HttpStatus status =
          outcome.pending() ? HttpStatus.CONFLICT : HttpStatus.OK;
      return ResponseEntity.status(status)
          .body(new JoinRequestReviewResponse(outcome.status().name(), outcome.message()));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  @PostMapping("/api/sessions/{sessionId}/join-requests/{requestId}/deny")
  public JoinRequestReviewResponse deny(
      @PathVariable("sessionId") Long sessionId,
      @PathVariable("requestId") Long requestId,
      Authentication auth) {
    Long userId = requireCurrentUserId(auth);
    try {
      SessionJoinRequestService.ReviewOutcome outcome =
          sessionJoinRequests.deny(sessionId, requestId, userId);
      return new JoinRequestReviewResponse(outcome.status().name(), outcome.message());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  @GetMapping("/api/session-join-requests/{requestId}")
  public JoinRequestStatusResponse status(
      @PathVariable("requestId") Long requestId, Authentication auth) {
    Long userId = requireCurrentUserId(auth);
    try {
      SessionJoinRequestService.RequestStatusView status =
          sessionJoinRequests.getStatusForRequester(requestId, userId);
      return new JoinRequestStatusResponse(
          status.id(),
          status.sessionId(),
          status.sessionTitle(),
          status.status().name(),
          status.requestedAt(),
          status.expiresAt(),
          status.message(),
          status.redirectUrl());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  private Long requireCurrentUserId(Authentication auth) {
    Long userId = AuthenticatedUserSupport.currentUser(auth) != null
        ? AuthenticatedUserSupport.currentUser(auth).getId()
        : null;
    if (userId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return userId;
  }
}
