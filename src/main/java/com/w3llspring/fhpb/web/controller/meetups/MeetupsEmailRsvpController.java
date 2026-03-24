package com.w3llspring.fhpb.web.controller.meetups;

import com.w3llspring.fhpb.web.db.LadderMeetupSlotRepository;
import com.w3llspring.fhpb.web.model.LadderMeetupSlot;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.LadderMeetupService;
import com.w3llspring.fhpb.web.service.meetups.MeetupsEmailLinkSigner;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

@Controller
public class MeetupsEmailRsvpController {

  private final MeetupsEmailLinkSigner signer;
  private final LadderMeetupService meetups;
  private final LadderMeetupSlotRepository slotRepo;

  public MeetupsEmailRsvpController(
      MeetupsEmailLinkSigner signer,
      LadderMeetupService meetups,
      LadderMeetupSlotRepository slotRepo) {
    this.signer = signer;
    this.meetups = meetups;
    this.slotRepo = slotRepo;
  }

  @GetMapping("/meetups/email-rsvp")
  public String rsvpFromEmail(
      @RequestParam("t") String token, RedirectAttributes redirectAttributes) {
    if (token == null || token.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    MeetupsEmailLinkSigner.ParsedToken parsed;
    try {
      parsed = signer.verify(token);
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    // Require login for v1. The token is bound to a specific recipient userId and must
    // match the currently logged-in user to prevent forwarded-link acceptance.
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    User currentUser = AuthenticatedUserSupport.currentUser(auth);
    Long currentUserId = currentUser != null ? currentUser.getId() : null;
    if (currentUserId == null) {
      String returnTo = "/meetups/email-rsvp?t=" + token;
      return "redirect:/login?returnTo="
          + UriUtils.encodeQueryParam(returnTo, java.nio.charset.StandardCharsets.UTF_8);
    }

    if (!currentUserId.equals(parsed.userId())) {
      return "redirect:/meetups/email-rsvp-mismatch";
    }

    LadderMeetupSlot slot = slotRepo.findById(parsed.slotId()).orElse(null);
    if (slot == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found: " + parsed.slotId());
    }
    if (slot.getCanceledAt() != null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot canceled");
    }

    meetups.setRsvp(currentUserId, parsed.slotId(), parsed.status());

    // Important: success toasts are normally suppressed from auto-show; set toastAuto to
    // force-display.
    String statusLabel;
    switch (parsed.status()) {
      case IN -> statusLabel = "You're in";
      case MAYBE -> statusLabel = "Marked as maybe";
      case CANT -> statusLabel = "Marked as can't make it";
      default -> statusLabel = "RSVP updated";
    }
    redirectAttributes.addFlashAttribute("toastLevel", "success");
    redirectAttributes.addFlashAttribute("toastAuto", Boolean.TRUE);
    redirectAttributes.addFlashAttribute("toastMessage", statusLabel + ".");

    // Send them straight to Play Plans. Try to preserve ladder context when possible.
    try {
      if (slot.getLadderConfig() != null && slot.getLadderConfig().getId() != null) {
        redirectAttributes.addAttribute("ladderId", slot.getLadderConfig().getId());
      }
    } catch (Exception ignored) {
      // Best-effort only; landing on Play Plans without ladderId is still OK.
    }

    return "redirect:/play-plans";
  }

  @GetMapping("/meetups/email-rsvp-mismatch")
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public String rsvpMismatch() {
    return "auth/meetups_email_rsvp_mismatch";
  }
}
