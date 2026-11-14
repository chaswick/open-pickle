package com.w3llspring.fhpb.web.controller.competition;

import com.w3llspring.fhpb.web.service.competition.SessionCapacityService;
import com.w3llspring.fhpb.web.service.competition.SessionJoinRequestService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SessionCapacityController {
  private final SessionCapacityService capacity;
  private final SessionJoinRequestService requests;

  public SessionCapacityController(
      SessionCapacityService capacity, SessionJoinRequestService requests) {
    this.capacity = capacity;
    this.requests = requests;
  }

  @PostMapping("/groups/{sessionId}/capacity")
  public String update(
      @PathVariable Long sessionId,
      @RequestParam int memberLimit,
      Authentication auth,
      RedirectAttributes flash) {
    try {
      capacity.updateLimit(sessionId, actor(auth), memberLimit);
      flash.addFlashAttribute("toastMessage", "Player limit updated.");
      flash.addFlashAttribute("toastLevel", "success");
    } catch (IllegalArgumentException | IllegalStateException ex) {
      flash.addFlashAttribute("toastMessage", ex.getMessage());
      flash.addFlashAttribute("toastLevel", "warning");
    } catch (SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    return "redirect:/groups/" + sessionId + "#session-capacity";
  }

  @PostMapping("/groups/join-requests/{requestId}/cancel")
  public String cancel(@PathVariable Long requestId, Authentication auth) {
    try {
      requests.cancel(requestId, actor(auth));
    } catch (IllegalArgumentException | SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return "redirect:/home";
  }

  private Long actor(Authentication auth) {
    var user = AuthenticatedUserSupport.currentUser(auth);
    if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return user.getId();
  }
}
