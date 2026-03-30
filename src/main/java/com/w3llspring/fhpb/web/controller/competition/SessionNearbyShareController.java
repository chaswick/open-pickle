package com.w3llspring.fhpb.web.controller.competition;

import com.w3llspring.fhpb.web.service.competition.SessionJoinRequestService;
import com.w3llspring.fhpb.web.service.competition.SessionNearbyShareService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sessions")
public class SessionNearbyShareController {

  public record NearbyShareStatusResponse(
      Long sessionId, Long locationId, String locationName, boolean sharingActive, String message) {}

  public record NearbySessionsResponse(String locationName, List<NearbySessionResponse> sessions) {}

  public record NearbySessionResponse(
      Long sessionId,
      String sessionTitle,
      String ownerDisplayName,
      String locationName,
      long activeMemberCount) {}

  public record NearbyJoinResponse(
      String state, Long sessionId, Long requestId, String redirectUrl, String message) {}

  private final SessionNearbyShareService nearbySharing;
  private final SessionJoinRequestService sessionJoinRequests;

  public SessionNearbyShareController(
      SessionNearbyShareService nearbySharing, SessionJoinRequestService sessionJoinRequests) {
    this.nearbySharing = nearbySharing;
    this.sessionJoinRequests = sessionJoinRequests;
  }

  @PostMapping("/{sessionId}/nearby-sharing")
  public NearbyShareStatusResponse enableForHost(
      @PathVariable("sessionId") Long sessionId, Authentication auth) {
    Long userId = requireCurrentUserId(auth);
    try {
      SessionNearbyShareService.NearbyShareStatusView status =
          nearbySharing.enableUsingActiveCheckIn(sessionId, userId);
      return new NearbyShareStatusResponse(
          status.sessionId(),
          status.locationId(),
          status.locationName(),
          status.sharingActive(),
          status.message());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @GetMapping("/nearby-sharing/candidates")
  public NearbySessionsResponse nearbyCandidates(Authentication auth) {
    Long userId = requireCurrentUserId(auth);
    try {
      SessionNearbyShareService.NearbySessionsView view =
          nearbySharing.listForRequesterFromActiveCheckIn(userId);
      return new NearbySessionsResponse(
          view.locationName(),
          view.sessions().stream()
              .map(
                  session ->
                      new NearbySessionResponse(
                          session.sessionId(),
                          session.sessionTitle(),
                          session.ownerDisplayName(),
                          session.locationName(),
                          session.activeMemberCount()))
              .toList());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @PostMapping("/{sessionId}/nearby-sharing/join")
  public NearbyJoinResponse requestJoin(
      @PathVariable("sessionId") Long sessionId, Authentication auth) {
    Long userId = requireCurrentUserId(auth);
    try {
      nearbySharing.requireSessionVisibleToRequester(sessionId, userId);
      SessionJoinRequestService.SubmissionOutcome outcome =
          sessionJoinRequests.submitBySessionId(sessionId, userId);
      if (outcome.state() == SessionJoinRequestService.SubmissionState.ALREADY_MEMBER) {
        return new NearbyJoinResponse(
            outcome.state().name(),
            outcome.sessionId(),
            null,
            "/groups/" + outcome.sessionId() + "?joined=1",
            "Already in the session. Opening it now.");
      }
      return new NearbyJoinResponse(
          outcome.state().name(),
          outcome.sessionId(),
          outcome.requestId(),
          "/groups/join-requests/" + outcome.requestId(),
          "Waiting for the session host to approve your request.");
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  private Long requireCurrentUserId(Authentication auth) {
    Long userId =
        AuthenticatedUserSupport.currentUser(auth) != null
            ? AuthenticatedUserSupport.currentUser(auth).getId()
            : null;
    if (userId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return userId;
  }
}
