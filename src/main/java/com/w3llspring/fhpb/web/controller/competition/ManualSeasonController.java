package com.w3llspring.fhpb.web.controller.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.SeasonCarryOverService;
import com.w3llspring.fhpb.web.service.SeasonTransitionService;
import com.w3llspring.fhpb.web.service.SeasonTransitionWindow;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/groups/{ladderId}/season")
@Secured("ROLE_USER")
public class ManualSeasonController {

  private static final Logger log = LoggerFactory.getLogger(ManualSeasonController.class);

  private final LadderConfigRepository ladderRepo;
  private final LadderMembershipRepository membershipRepo;
  private final LadderSeasonRepository seasonRepo;
  private final SeasonTransitionService transitionSvc;
  private final SeasonCarryOverService seasonCarryOverService;
  private final RoundRobinService roundRobinService;
  private final StoryModeService storyModeService;

  @Value("${fhpb.bootstrap.admin.email:}")
  private String siteWideAdminEmail = "";

  public ManualSeasonController(
      LadderConfigRepository ladderRepo,
      LadderMembershipRepository membershipRepo,
      LadderSeasonRepository seasonRepo,
      SeasonTransitionService transitionSvc,
      SeasonCarryOverService seasonCarryOverService,
      RoundRobinService roundRobinService,
      StoryModeService storyModeService) {
    this.ladderRepo = ladderRepo;
    this.membershipRepo = membershipRepo;
    this.seasonRepo = seasonRepo;
    this.transitionSvc = transitionSvc;
    this.seasonCarryOverService = seasonCarryOverService;
    this.roundRobinService = roundRobinService;
    this.storyModeService = storyModeService;
  }

  /**
   * Start a season (Manual mode only). Race safety: lock ladder row, re-check "no ACTIVE" inside
   * the tx, then create.
   */
  @PostMapping("/start")
  @Transactional
  public String start(@PathVariable Long ladderId, Authentication auth, RedirectAttributes ra) {

    // Pessimistic lock serializes transitions for this ladder
    LadderConfig ladder = requireLadder(ladderId);
    requireAdmin(auth, ladder);
    if (ladder.isCompetitionType()) {
      ra.addFlashAttribute(
          "toastMessage", "Competition seasons are managed from the system competition page.");
      ra.addFlashAttribute("toastLevel", "warning");
      return "redirect:/competition";
    }
    if (ladder.isSessionType()) {
      ra.addFlashAttribute("toastMessage", "Match sessions do not have their own seasons.");
      ra.addFlashAttribute("toastLevel", "warning");
      return redirectShow(ladderId);
    }

    if (ladder.getMode() != LadderConfig.Mode.MANUAL) {
      ra.addFlashAttribute(
          "toastMessage", "This group is in Rolling mode. Switch to Manual to start/stop seasons.");
      ra.addFlashAttribute("toastLevel", "danger");
      return redirectShow(ladderId);
    }

    // Enforce season creation rate limit (1 per 24 hours)
    SeasonTransitionWindow tw = transitionSvc.canCreateSeason(ladder);
    if (!tw.isAllowed()) {
      String countdown = transitionSvc.formatCountdown(tw);
      ra.addFlashAttribute(
          "toastMessage",
          "You can create a new season tomorrow. "
              + (countdown.isEmpty()
                  ? ""
                  : "Next season creation available in " + countdown + "."));
      ra.addFlashAttribute("toastLevel", "warning");
      return redirectShow(ladderId);
    }

    // Guard: only one ACTIVE season
    Optional<LadderSeason> activeOpt = seasonRepo.findActive(ladderId);
    if (activeOpt.isPresent()) {
      ra.addFlashAttribute("toastMessage", "A season is already active.");
      ra.addFlashAttribute("toastLevel", "warning");
      return redirectShow(ladderId);
    }

    // Create ACTIVE season now
    LadderSeason s = new LadderSeason();
    s.setLadderConfig(ladder);
    s.setState(LadderSeason.State.ACTIVE);
    s.setStoryModeEnabled(storyModeFeatureEnabled() && ladder.isStoryModeDefaultEnabled());
    Instant startTime = Instant.now();
    s.setStartedAt(startTime);
    s.setStartedByUserId(currentUserId(auth));

    // Optional niceties: set a human name/dates if you show them in UI
    // (for Manual mode you might use today..today or leave endDate for later)
    java.time.LocalDate today = java.time.LocalDate.now(ZoneOffset.UTC);
    s.setName(defaultSeasonLabel(today));
    s.setStartDate(today);
    s.setEndDate(today.plusYears(90)); // far-future placeholder while season is active

    // Update the ladder's last season creation timestamp
    ladder.setLastSeasonCreatedAt(startTime);

    seasonRepo.saveAndFlush(s);
    storyModeService.ensureTrackers(s);
    seasonCarryOverService.seedSeasonFromCarryOverIfEnabled(s);
    ladderRepo.saveAndFlush(ladder);

    ra.addFlashAttribute("toastMessage", "Season started.");
    ra.addFlashAttribute("toastLevel", "success");
    return redirectShow(ladderId);
  }

  /**
   * End the active season (Manual mode). Allowed anytime; shows a confirm on the UI side. Applies
   * transition cap.
   */
  @PostMapping("/end")
  @Transactional
  public String end(@PathVariable Long ladderId, Authentication auth, RedirectAttributes ra) {

    // Pessimistic lock serializes transitions for this ladder
    LadderConfig ladder = requireLadder(ladderId);
    requireAdmin(auth, ladder);
    if (ladder.isCompetitionType()) {
      ra.addFlashAttribute(
          "toastMessage", "Competition seasons are managed from the system competition page.");
      ra.addFlashAttribute("toastLevel", "warning");
      return "redirect:/competition";
    }
    if (ladder.isSessionType()) {
      ra.addFlashAttribute("toastMessage", "Match sessions do not have their own seasons.");
      ra.addFlashAttribute("toastLevel", "warning");
      return redirectShow(ladderId);
    }

    Optional<LadderSeason> activeOpt = seasonRepo.findActive(ladderId);
    if (!activeOpt.isPresent()) {
      ra.addFlashAttribute("toastMessage", "No active season to end.");
      ra.addFlashAttribute("toastLevel", "warning");
      return redirectShow(ladderId);
    }

    // Prevent ending if user would be locked out (can't create new season today)
    SeasonTransitionWindow tw = transitionSvc.canCreateSeason(ladder);
    if (!tw.isAllowed()) {
      String countdown = transitionSvc.formatCountdown(tw);
      ra.addFlashAttribute(
          "toastMessage",
          "Cannot end season - you've already created a season today. "
              + "Ending now would prevent you from starting a new season. "
              + (countdown.isEmpty()
                  ? "Try again tomorrow."
                  : "You can end this season in " + countdown + "."));
      ra.addFlashAttribute("toastLevel", "warning");
      return redirectShow(ladderId);
    }

    LadderSeason active = activeOpt.get();
    active.setState(LadderSeason.State.ENDED);
    active.setEndedAt(Instant.now());
    active.setEndedByUserId(currentUserId(auth));

    // Optional: set the season's endDate to today for nice UI range
    active.setEndDate(java.time.LocalDate.now(ZoneOffset.UTC));

    seasonRepo.saveAndFlush(active);
    roundRobinService.endOpenRoundRobinsForSeason(active);

    ra.addFlashAttribute("toastMessage", "Season ended.");
    ra.addFlashAttribute("toastLevel", "success");
    return redirectShow(ladderId);
  }

  // ===== Helpers =====

  private String redirectShow(Long ladderId) {
    return "redirect:/groups/" + ladderId;
  }

  private LadderConfig requireLadder(Long ladderId) {
    LadderConfig ladder = ladderRepo.lockById(ladderId);
    if (ladder == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
    }
    return ladder;
  }

  /**
   * Confirm the user is the ladder owner or an ADMIN member. Wire this to your existing
   * ladder-admin logic. Throw AccessDeniedException if not admin.
   */
  private void requireAdmin(Authentication auth, LadderConfig ladder) {
    if (auth == null) {
      throw new AccessDeniedException("Login required");
    }
    if (ladder != null && ladder.isCompetitionType() && !isSiteWideAdmin(auth)) {
      throw new AccessDeniedException("Only the site-wide admin can manage competition");
    }
    Long userId = currentUserId(auth);
    if (userId == null) {
      throw new AccessDeniedException("Login required");
    }
    // Owner is always admin
    if (ladder.getOwnerUserId() != null && ladder.getOwnerUserId().equals(userId)) {
      return;
    }

    LadderMembership lm =
        membershipRepo.findByLadderConfigIdAndUserId(ladder.getId(), userId).orElse(null);
    boolean isAdmin =
        lm != null
            && lm.getState() == LadderMembership.State.ACTIVE
            && lm.getRole() == LadderMembership.Role.ADMIN;
    if (!isAdmin) {
      throw new AccessDeniedException("Admin required");
    }
  }

  private boolean isSiteWideAdmin(Authentication auth) {
    User currentUser = AuthenticatedUserSupport.currentUser(auth);
    return currentUser != null
        && normalizedEmail(currentUser.getEmail()).equals(normalizedEmail(siteWideAdminEmail));
  }

  private String normalizedEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private Long currentUserId(Authentication auth) {
    User currentUser = AuthenticatedUserSupport.currentUser(auth);
    return currentUser != null ? currentUser.getId() : null;
  }

  /** Optional: derive a simple season name; customize as you see fit. */
  private String defaultSeasonLabel(java.time.LocalDate startDate) {
    if (startDate == null) {
      return "Season";
    }
    java.time.format.DateTimeFormatter fmt =
        java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy");
    return "Season • " + startDate.format(fmt);
  }

  private boolean storyModeFeatureEnabled() {
    return storyModeService == null || storyModeService.isFeatureEnabled();
  }
}
