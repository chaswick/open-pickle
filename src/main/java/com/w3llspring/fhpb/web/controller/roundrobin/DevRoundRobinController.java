package com.w3llspring.fhpb.web.controller.roundrobin;

import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.RoundRobin;
import com.w3llspring.fhpb.web.model.RoundRobinEntry;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.MatchFactory;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Development-only utilities for round-robin testing. Enabled only when the 'dev' Spring profile is
 * active.
 */
@Controller
@Profile("dev")
public class DevRoundRobinController {

  private final RoundRobinService roundRobinService;
  private final MatchConfirmationService confirmationService;
  private final MatchFactory matchFactory;

  public DevRoundRobinController(
      RoundRobinService roundRobinService,
      MatchConfirmationService confirmationService,
      MatchFactory matchFactory) {
    this.roundRobinService = roundRobinService;
    this.confirmationService = confirmationService;
    this.matchFactory = matchFactory;
  }

  /**
   * Attempt to auto-confirm matches for the current round of the given round-robin. This tries to
   * link any discovered logged matches to entries and then calls the confirmation service with
   * available participant accounts until one manual confirmation succeeds per match. Useful for
   * rapid local testing.
   */
  @PostMapping("/round-robin/dev/auto-confirm/{id}")
  public String autoConfirmCurrentRound(
      @PathVariable("id") Long id,
      RedirectAttributes redirect,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          com.w3llspring.fhpb.web.model.CustomUserDetails principal) {
    User actor =
        principal != null ? AuthenticatedUserSupport.refresh(principal.getUserObject()) : null;
    if (actor == null || !actor.isAdmin()) {
      redirect.addFlashAttribute("toastMessage", "Admin privileges required.");
      redirect.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    RoundRobin rr = roundRobinService.getRoundRobin(id);
    if (rr == null) {
      redirect.addFlashAttribute("toastMessage", "Round-robin not found");
      redirect.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/list";
    }

    int current = rr.getCurrentRound();
    List<RoundRobinEntry> entries = roundRobinService.getEntriesForRound(id, current);
    int confirmed = 0;
    if (entries != null) {
      for (RoundRobinEntry e : entries) {
        if (e.isBye()) continue;
        Long mid = e.getMatchId();
        // If no match linked, try to discover a logged match and link it; otherwise create one
        if (mid == null) {
          var maybe = roundRobinService.findLoggedMatchForEntry(e, rr.getCreatedAt());
          if (maybe.isPresent()) {
            Match found = maybe.get();
            roundRobinService.linkEntryToMatch(e, found.getId());
            mid = found.getId();
          } else {
            // create a synthetic match for this entry (dev-only)
            Match newMatch = new Match();
            newMatch.setSeason(rr.getSeason());
            newMatch.setPlayedAt(
                rr.getCreatedAt() != null ? rr.getCreatedAt() : java.time.Instant.now());
            // Do not set loggedBy so confirmation rules will require the losing side to confirm
            newMatch.setLoggedBy(null);
            newMatch.setA1(e.getA1());
            newMatch.setA2(e.getA2());
            newMatch.setB1(e.getB1());
            newMatch.setB2(e.getB2());
            // assign a plausible score: 11-8 (A wins)
            newMatch.setScoreA(11);
            newMatch.setScoreB(8);
            // Persist via MatchFactory to ensure confirmation rows are created
            Match saved = matchFactory.createMatch(newMatch);
            roundRobinService.linkEntryToMatch(e, saved.getId());
            mid = saved.getId();
          }
        }

        if (mid == null) continue;

        Match m = roundRobinService.getMatch(mid);
        if (m == null) continue;

        // Choose a member of the losing side to confirm (B team for our synthetic matches)
        List<User> confirmCandidates = new ArrayList<>();
        if (m.getB1() != null) confirmCandidates.add(m.getB1());
        if (m.getB2() != null) confirmCandidates.add(m.getB2());
        if (confirmCandidates.isEmpty()) {
          // fallback: try any participant
          if (m.getA1() != null) confirmCandidates.add(m.getA1());
          if (m.getA2() != null) confirmCandidates.add(m.getA2());
        }

        for (User u : confirmCandidates) {
          if (u == null || u.getId() == null) continue;
          try {
            confirmationService.confirmMatch(mid, u.getId());
            confirmed++;
            break;
          } catch (IllegalArgumentException ex) {
            continue;
          } catch (Exception ex) {
            continue;
          }
        }
      }
    }

    redirect.addFlashAttribute(
        "toastMessage", "Auto-confirmed " + confirmed + " matches for round " + current);
    redirect.addFlashAttribute("toastLevel", "success");
    return "redirect:/round-robin/view/" + id;
  }
}
