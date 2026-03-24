package com.w3llspring.fhpb.web.controller.meetups;

import com.w3llspring.fhpb.web.service.PlayLocationService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/check-in")
@Secured("ROLE_USER")
public class CheckInApiController {

  @Value("${fhpb.features.check-in.enabled:true}")
  private boolean checkInEnabled;

  private final PlayLocationService playLocationService;

  public CheckInApiController(PlayLocationService playLocationService) {
    this.playLocationService = playLocationService;
  }

  public record ResolveRequest(Double latitude, Double longitude) {}

  public record CompleteRequest(
      Double latitude, Double longitude, Long locationId, String selectedName, String customName) {}

  public record SuggestionResponse(String name, long usageCount) {}

  public record ResolveResponse(
      String status, Long locationId, String message, List<SuggestionResponse> suggestions) {}

  public record CompleteResponse(String message) {}

  @PostMapping("/resolve")
  public ResolveResponse resolve(@RequestBody ResolveRequest request) {
    ensureFeatureEnabled();
    if (request == null || request.latitude() == null || request.longitude() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Location coordinates are required.");
    }

    try {
      PlayLocationService.ResolveOutcome outcome =
          playLocationService.resolveCheckIn(
              resolveCurrentUserId(), request.latitude(), request.longitude());
      List<SuggestionResponse> suggestions =
          outcome.getSuggestions().stream()
              .map(
                  suggestion ->
                      new SuggestionResponse(suggestion.getName(), suggestion.getUsageCount()))
              .toList();
      return new ResolveResponse(
          outcome.getStatus(), outcome.getLocationId(), outcome.getMessage(), suggestions);
    } catch (PlayLocationService.CheckInRateLimitException ex) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @PostMapping("/complete")
  public CompleteResponse complete(@RequestBody CompleteRequest request) {
    ensureFeatureEnabled();
    if (request == null || request.latitude() == null || request.longitude() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Location coordinates are required.");
    }

    try {
      PlayLocationService.CheckInOutcome outcome =
          playLocationService.completeCheckIn(
              resolveCurrentUserId(),
              new PlayLocationService.CompleteCheckInCommand(
                  request.latitude(),
                  request.longitude(),
                  request.locationId(),
                  request.selectedName(),
                  request.customName()));
      return new CompleteResponse(outcome.getMessage());
    } catch (PlayLocationService.CheckInRateLimitException ex) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  private void ensureFeatureEnabled() {
    if (!checkInEnabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  private Long resolveCurrentUserId() {
    return AuthenticatedUserSupport.requireCurrentUserId();
  }
}
