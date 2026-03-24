package com.w3llspring.fhpb.web.controller.meetups;

import com.w3llspring.fhpb.web.model.LadderMeetupRsvp;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.LadderMeetupService;
import com.w3llspring.fhpb.web.service.meetups.DuplicateMeetupException;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/meetups")
@Secured("ROLE_USER")
public class MeetupsController {

  private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/New_York");

  @Value("${fhpb.features.meetups.enabled:false}")
  private boolean meetupsEnabled;

  private final LadderMeetupService meetups;

  public MeetupsController(LadderMeetupService meetups) {
    this.meetups = meetups;
  }

  public record CreateMeetupRequest(Long ladderId, String date, String time) {}

  public record SetRsvpRequest(String status) {}

  public record UpdateLocationRequest(String locationCode) {}

  @GetMapping("/upcoming")
  public List<LadderMeetupService.MeetupRow> upcoming() {
    if (!meetupsEnabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    User user = requireCurrentUser();
    Long userId = user.getId();
    return meetups.upcomingForUser(userId, 5);
  }

  @PostMapping("/create")
  public void create(@RequestBody CreateMeetupRequest req) {
    if (!meetupsEnabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    if (req == null || req.ladderId() == null || req.date() == null || req.time() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    User user = requireCurrentUser();
    Long userId = user.getId();

    ZoneId zone = DEFAULT_ZONE;
    try {
      if (user.getTimeZone() != null && !user.getTimeZone().isBlank()) {
        zone = ZoneId.of(user.getTimeZone().trim());
      }
    } catch (Exception ignored) {
      zone = DEFAULT_ZONE;
    }

    Instant startsAt;
    try {
      LocalDate date = LocalDate.parse(req.date());
      LocalTime time = LocalTime.parse(req.time());
      if ((time.getMinute() % 15) != 0) {
        throw new IllegalArgumentException("Time must be in 15-minute increments");
      }
      LocalDateTime ldt = LocalDateTime.of(date, time);
      startsAt = ldt.atZone(zone).toInstant();
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    try {
      meetups.createSlot(userId, req.ladderId(), startsAt);
    } catch (DuplicateMeetupException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @PostMapping("/{slotId}/rsvp")
  public void rsvp(@PathVariable("slotId") Long slotId, @RequestBody SetRsvpRequest req) {
    if (!meetupsEnabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    if (slotId == null || req == null || req.status() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    LadderMeetupRsvp.Status status;
    try {
      status = LadderMeetupRsvp.Status.valueOf(req.status().toUpperCase());
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    Long userId = requireCurrentUser().getId();
    meetups.setRsvp(userId, slotId, status);
  }

  @PostMapping("/{slotId}/delete")
  public void delete(@PathVariable("slotId") Long slotId) {
    if (!meetupsEnabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    if (slotId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    Long userId = requireCurrentUser().getId();
    try {
      meetups.deleteSlot(userId, slotId);
    } catch (IllegalArgumentException ex) {
      // Distinguish common cases
      if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("not allowed")) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
      }
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/{slotId}/location")
  public void updateLocation(
      @PathVariable("slotId") Long slotId, @RequestBody UpdateLocationRequest req) {
    if (!meetupsEnabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    if (slotId == null || req == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    Long userId = requireCurrentUser().getId();
    try {
      meetups.updateLocation(userId, slotId, req.locationCode());
    } catch (IllegalArgumentException ex) {
      if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("not allowed")) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
      }
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  private User requireCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    User currentUser = AuthenticatedUserSupport.currentUser(authentication);
    if (currentUser == null || currentUser.getId() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return currentUser;
  }
}
