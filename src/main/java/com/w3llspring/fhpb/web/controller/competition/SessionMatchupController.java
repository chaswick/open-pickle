package com.w3llspring.fhpb.web.controller.competition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.RoundRobin;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionSeasonService;
import com.w3llspring.fhpb.web.service.competition.GroupCreationService;
import com.w3llspring.fhpb.web.service.competition.SessionMatchupService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinModificationException;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/groups")
public class SessionMatchupController {
  private final SessionMatchupService matchups;
  private final GroupCreationService creation;
  private final CompetitionSeasonService seasons;
  private final LadderConfigRepository sessions;
  private final ObjectMapper json;

  public SessionMatchupController(
      SessionMatchupService matchups,
      GroupCreationService creation,
      CompetitionSeasonService seasons,
      LadderConfigRepository sessions,
      ObjectMapper json) {
    this.matchups = matchups;
    this.creation = creation;
    this.seasons = seasons;
    this.sessions = sessions;
    this.json = json;
  }

  @GetMapping("/new-session")
  public String newSession(Model model) {
    model.addAttribute("sessionPlayMode", LadderConfig.SessionPlayMode.MATCHUPS);
    return "auth/new-session";
  }

  @PostMapping("/create-session")
  @Transactional
  public String create(
      Authentication auth,
      @RequestParam(defaultValue = "MATCHUPS") LadderConfig.SessionPlayMode playMode,
      @RequestParam(defaultValue = "false") boolean organizerScored,
      RedirectAttributes flash) {
    User actor = actor(auth);
    var outcome =
        creation.createOrReuseSession(
            actor.getId(), null, seasons.resolveActiveCompetitionSeason());
    if (!outcome.success()) {
      message(flash, outcome.toastMessage(), "warning");
      return "redirect:/groups/new-session";
    }
    if (!outcome.reusedExistingSession()) {
      LadderConfig session = sessions.lockById(outcome.configId());
      session.setSessionPlayMode(playMode);
      session.setSecurityLevel(
          playMode == LadderConfig.SessionPlayMode.MATCHUPS && organizerScored
              ? LadderSecurity.SELF_CONFIRM
              : LadderSecurity.STANDARD);
      if (playMode == LadderConfig.SessionPlayMode.MATCHUPS) {
        session.setExpiresAt(Instant.now().plusSeconds(7 * 24 * 3600L));
      }
      sessions.save(session);
    } else {
      message(flash, "You already have an active session. We've opened it for you.", "info");
    }
    return back(outcome.configId());
  }

  @PostMapping("/{sessionId}/matchups/start")
  public String start(
      @PathVariable Long sessionId,
      Authentication auth,
      @RequestParam(defaultValue = "ROTATING_PARTNERS") RoundRobin.Format format,
      @RequestParam(defaultValue = "6") int rounds,
      @RequestParam(required = false) String fixedTeamsJson,
      @RequestParam(required = false) Boolean organizerScored,
      RedirectAttributes flash) {
    try {
      List<List<Long>> teams = List.of();
      if (format == RoundRobin.Format.FIXED_TEAMS) {
        if (fixedTeamsJson == null || fixedTeamsJson.length() > 4000) {
          throw new RoundRobinModificationException("Choose a partner for each player.");
        }
        teams = json.readValue(fixedTeamsJson, new TypeReference<List<List<Long>>>() {});
      }
      matchups.start(sessionId, actor(auth), format, rounds, teams, organizerScored);
      message(
          flash, "Your matchups are ready. Play ahead whenever your group is ready.", "success");
    } catch (RoundRobinModificationException | JsonProcessingException ex) {
      message(
          flash,
          ex instanceof JsonProcessingException
              ? "Choose a partner for each player."
              : ex.getMessage(),
          "warning");
    }
    return back(sessionId);
  }

  @PostMapping("/{sessionId}/matchups/{scheduleId}/replace")
  public String replace(
      @PathVariable Long sessionId,
      @PathVariable Long scheduleId,
      @RequestParam Long slotId,
      @RequestParam(required = false) Long replacementId,
      @RequestParam(required = false) String guestName,
      Authentication auth,
      RedirectAttributes flash) {
    try {
      matchups.replace(sessionId, scheduleId, slotId, replacementId, guestName, actor(auth));
      message(
          flash,
          "Replacement saved for the remaining matchups. Recorded games are unchanged.",
          "success");
    } catch (RoundRobinModificationException ex) {
      message(flash, ex.getMessage(), "warning");
    }
    return back(sessionId);
  }

  @PostMapping("/{sessionId}/matchups/{scheduleId}/games/{entryId}/score")
  public String score(
      @PathVariable Long sessionId,
      @PathVariable Long scheduleId,
      @PathVariable Long entryId,
      @RequestParam int scoreA,
      @RequestParam int scoreB,
      Authentication auth,
      RedirectAttributes flash) {
    try {
      var saved = matchups.recordScore(sessionId, scheduleId, entryId, scoreA, scoreB, actor(auth));
      message(
          flash,
          saved.isOrganizerConfirmed()
              ? "Score saved and confirmed."
              : "Score saved. You can keep playing while confirmations catch up.",
          "success");
    } catch (RoundRobinModificationException | SecurityException ex) {
      message(flash, ex.getMessage(), "warning");
    }
    return back(sessionId);
  }

  @PostMapping("/{sessionId}/matchups/{scheduleId}/games/{entryId}/correct")
  public String correct(
      @PathVariable Long sessionId,
      @PathVariable Long scheduleId,
      @PathVariable Long entryId,
      @RequestParam int scoreA,
      @RequestParam int scoreB,
      @RequestParam Long expectedVersion,
      Authentication auth,
      RedirectAttributes flash) {
    try {
      matchups.correctScore(
          sessionId, scheduleId, entryId, scoreA, scoreB, expectedVersion, actor(auth));
      message(flash, "Score corrected and confirmed. Results have been updated.", "success");
    } catch (RoundRobinModificationException
        | IllegalArgumentException
        | IllegalStateException
        | SecurityException
        | OptimisticLockingFailureException ex) {
      message(flash, ex.getMessage(), "warning");
    }
    return back(sessionId);
  }

  @PostMapping("/{sessionId}/matchups/{scheduleId}/end")
  public String end(
      @PathVariable Long sessionId,
      @PathVariable Long scheduleId,
      Authentication auth,
      RedirectAttributes flash) {
    try {
      matchups.end(sessionId, scheduleId, actor(auth));
      message(flash, "Matchups ended. Your recorded games are saved.", "success");
    } catch (RoundRobinModificationException ex) {
      message(flash, ex.getMessage(), "warning");
    }
    return back(sessionId);
  }

  private User actor(Authentication auth) {
    User user = AuthenticatedUserSupport.currentUser(auth);
    if (user == null || user.getId() == null)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return user;
  }

  private void message(RedirectAttributes flash, String text, String level) {
    flash.addFlashAttribute("toastMessage", text);
    flash.addFlashAttribute("toastLevel", level);
  }

  private String back(Long id) {
    return "redirect:/groups/" + id + "#matchups";
  }
}
