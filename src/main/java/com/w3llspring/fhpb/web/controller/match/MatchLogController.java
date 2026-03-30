package com.w3llspring.fhpb.web.controller.match;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.MatchWorkflowRules;
import com.w3llspring.fhpb.web.model.RoundRobinEntry;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionAutoModerationService;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchFactory;
import com.w3llspring.fhpb.web.service.MatchLoggingQuotaService;
import com.w3llspring.fhpb.web.service.RecentDuplicateMatchWarningService;
import com.w3llspring.fhpb.web.service.UserMatchLogGateService;
import com.w3llspring.fhpb.web.service.matchlog.LearningService;
import com.w3llspring.fhpb.web.service.matchlog.MatchLogContextService;
import com.w3llspring.fhpb.web.service.matchlog.MatchLogContextService.ResolvedMatchLogContext;
import com.w3llspring.fhpb.web.service.matchlog.MatchLogPageShellService;
import com.w3llspring.fhpb.web.service.matchlog.MatchLogPlayerPickerService;
import com.w3llspring.fhpb.web.service.matchlog.MatchLogPlayerPickerService.PlayerSelectionLists;
import com.w3llspring.fhpb.web.service.matchlog.MatchLogRoutingService;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.MatchValidationRequest;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.MatchValidationResult;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.PlayerSlot;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.ScoreValidationResult;
import com.w3llspring.fhpb.web.service.matchworkflow.MatchStateTransitionService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinModificationException;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import com.w3llspring.fhpb.web.session.UserSessionState;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/log-match")
public class MatchLogController {

  private static final ZoneId LADDER_ZONE = ZoneId.of("America/New_York");
  private static final int MAX_VOICE_REVIEW_TRANSCRIPT_CHARS = 300;

  private final UserRepository userRepo;
  private final MatchRepository matchRepo;
  private final LadderV2Service ladderV2;
  // per-user passphrase feature removed â€” no longer storing/injecting user passphrases
  private final TrophyAwardService trophyAwardService;
  private final MatchValidationService matchValidationService;
  private final LadderSeasonRepository seasonRepo;
  private final LadderAccessService ladderAccessService;
  private final MatchFactory matchFactory;
  private final MatchStateTransitionService matchStateTransitionService;
  private final UserMatchLogGateService userMatchLogGateService;
  private final RecentDuplicateMatchWarningService recentDuplicateMatchWarningService;
  private final CourtNameService courtNameService;
  private final LearningService learningService;
  private final com.w3llspring.fhpb.web.service.MatchConfirmationService matchConfirmationService;
  private final boolean matchLogRateLimitEnabled;
  private static final Logger log = LoggerFactory.getLogger(MatchLogController.class);
  private LadderConfigRepository ladderConfigRepository;
  private LadderMembershipRepository ladderMembershipRepository;
  private MatchLoggingQuotaService matchLoggingQuotaService;
  private RoundRobinService roundRobinService;
  private CompetitionAutoModerationService competitionAutoModerationService;

  @Autowired
  public MatchLogController(
      UserRepository userRepo,
      MatchRepository matchRepo,
      LadderV2Service ladderV2,
      LadderSeasonRepository seasonRepo,
      TrophyAwardService trophyAwardService,
      MatchValidationService matchValidationService,
      LadderAccessService ladderAccessService,
      MatchFactory matchFactory,
      MatchStateTransitionService matchStateTransitionService,
      UserMatchLogGateService userMatchLogGateService,
      RecentDuplicateMatchWarningService recentDuplicateMatchWarningService,
      com.w3llspring.fhpb.web.service.MatchConfirmationService matchConfirmationService,
      CourtNameService courtNameService,
      LearningService learningService,
      LadderConfigRepository ladderConfigRepository,
      LadderMembershipRepository ladderMembershipRepository,
      MatchLoggingQuotaService matchLoggingQuotaService,
      RoundRobinService roundRobinService,
      CompetitionAutoModerationService competitionAutoModerationService,
      @Value("${fhpb.match-log.rate-limit.enabled:true}") boolean matchLogRateLimitEnabled) {
    this.userRepo = userRepo;
    this.matchRepo = matchRepo;
    this.ladderV2 = ladderV2;
    this.seasonRepo = seasonRepo;
    this.trophyAwardService = trophyAwardService;
    this.matchValidationService = matchValidationService;
    this.ladderAccessService = ladderAccessService;
    this.matchFactory = matchFactory;
    this.matchStateTransitionService = matchStateTransitionService;
    this.userMatchLogGateService = userMatchLogGateService;
    this.recentDuplicateMatchWarningService = recentDuplicateMatchWarningService;
    this.matchConfirmationService = matchConfirmationService;
    this.courtNameService = courtNameService;
    this.learningService = learningService;
    this.ladderConfigRepository = ladderConfigRepository;
    this.ladderMembershipRepository = ladderMembershipRepository;
    this.matchLoggingQuotaService = matchLoggingQuotaService;
    this.roundRobinService = roundRobinService;
    this.competitionAutoModerationService = competitionAutoModerationService;
    this.matchLogRateLimitEnabled = matchLogRateLimitEnabled;
  }

  public MatchLogController(
      UserRepository userRepo,
      MatchRepository matchRepo,
      LadderV2Service ladderV2,
      LadderSeasonRepository seasonRepo,
      TrophyAwardService trophyAwardService,
      MatchValidationService matchValidationService,
      LadderAccessService ladderAccessService,
      MatchFactory matchFactory,
      MatchStateTransitionService matchStateTransitionService,
      UserMatchLogGateService userMatchLogGateService,
      RecentDuplicateMatchWarningService recentDuplicateMatchWarningService,
      com.w3llspring.fhpb.web.service.MatchConfirmationService matchConfirmationService,
      CourtNameService courtNameService,
      LearningService learningService,
      @Value("${fhpb.match-log.rate-limit.enabled:true}") boolean matchLogRateLimitEnabled) {
    this(
        userRepo,
        matchRepo,
        ladderV2,
        seasonRepo,
        trophyAwardService,
        matchValidationService,
        ladderAccessService,
        matchFactory,
        matchStateTransitionService,
        userMatchLogGateService,
        recentDuplicateMatchWarningService,
        matchConfirmationService,
        courtNameService,
        learningService,
        null,
        null,
        null,
        null,
        null,
        matchLogRateLimitEnabled);
  }

  @GetMapping
  public String form(
      Model model,
      Authentication auth,
      HttpServletRequest request,
      @RequestParam(value = "seasonId", required = false) Long seasonId,
      @RequestParam(value = "ladderId", required = false) Long ladderId,
      @RequestParam(value = "editMatchId", required = false) Long editMatchId,
      @RequestParam(value = "competition", required = false, defaultValue = "false")
          boolean competitionMode,
      @RequestParam(value = "a1", required = false) String prefillA1,
      @RequestParam(value = "a2", required = false) String prefillA2,
      @RequestParam(value = "b1", required = false) String prefillB1,
      @RequestParam(value = "b2", required = false) String prefillB2,
      @RequestParam(value = "returnTo", required = false) String returnTo,
      @RequestParam(value = "returnToRoundRobinId", required = false) Long returnToRoundRobinId,
      @RequestParam(value = "returnToRoundRobinEntryId", required = false)
          Long returnToRoundRobinEntryId,
      @RequestParam(value = "returnToRoundRobinRound", required = false)
          Integer returnToRoundRobinRound) {
    return formInternal(
        model,
        auth,
        request,
        seasonId,
        ladderId,
        editMatchId,
        competitionMode,
        prefillA1,
        prefillA2,
        prefillB1,
        prefillB2,
        returnTo,
        returnToRoundRobinId,
        returnToRoundRobinEntryId,
        returnToRoundRobinRound);
  }

  public String form(
      Model model,
      Authentication auth,
      HttpServletRequest request,
      Long seasonId,
      Long ladderId,
      Long editMatchId,
      boolean competitionMode,
      String prefillA1,
      String prefillA2,
      String prefillB1,
      String prefillB2,
      Long returnToRoundRobinId,
      Long returnToRoundRobinEntryId,
      Integer returnToRoundRobinRound) {
    return formInternal(
        model,
        auth,
        request,
        seasonId,
        ladderId,
        editMatchId,
        competitionMode,
        prefillA1,
        prefillA2,
        prefillB1,
        prefillB2,
        null,
        returnToRoundRobinId,
        returnToRoundRobinEntryId,
        returnToRoundRobinRound);
  }

  public String form(
      Model model,
      Authentication auth,
      HttpServletRequest request,
      Long seasonId,
      Long ladderId,
      Long editMatchId,
      boolean competitionMode,
      String prefillA1,
      String prefillA2,
      String prefillB1,
      String prefillB2,
      Long returnToRoundRobinId,
      Integer returnToRoundRobinRound) {
    return form(
        model,
        auth,
        request,
        seasonId,
        ladderId,
        editMatchId,
        competitionMode,
        prefillA1,
        prefillA2,
        prefillB1,
        prefillB2,
        returnToRoundRobinId,
        null,
        returnToRoundRobinRound);
  }

  private String formInternal(
      Model model,
      Authentication auth,
      HttpServletRequest request,
      Long seasonId,
      Long ladderId,
      Long editMatchId,
      boolean competitionMode,
      String prefillA1,
      String prefillA2,
      String prefillB1,
      String prefillB2,
      String returnTo,
      Long returnToRoundRobinId,
      Long returnToRoundRobinEntryId,
      Integer returnToRoundRobinRound) {
    boolean voiceReviewMode = isVoiceReviewRequest(request);
    Long requestedLadderId = ladderId;
    if (!competitionMode) {
      ladderId = UserSessionState.resolveSelectedGroupId(request, ladderId);
    }

    User currentUser = getCurrentUser(auth);
    List<LadderMembership> myMemberships = activeMemberships(currentUser);
    if (requestedLadderId == null && contextService().isCompetitionLadderId(ladderId)) {
      ladderId = null;
    }
    if (competitionMode) {
      if (!contextService().isSessionLadderId(ladderId)) {
        ladderId = pageShellService().defaultCompetitionSessionLadderId(myMemberships);
      }
    } else if (ladderId == null) {
      ladderId = pageShellService().defaultLadderId(myMemberships);
    }
    String meEmail = auth != null ? auth.getName() : null;
    model.addAttribute("returnToPath", routingService().sanitizeReturnTo(returnTo));
    model.addAttribute("voiceReviewMode", voiceReviewMode);

    // ===== EDIT MODE HANDLING =====
    if (editMatchId != null) {
      return handleEditMode(
          model,
          auth,
          editMatchId,
          seasonId,
          ladderId,
          currentUser,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
    }
    // ===== END EDIT MODE =====

    if (!competitionMode && contextService().isDirectCompetitionSelection(ladderId, seasonId)) {
      return routingService().competitionRedirect(null);
    }
    if (!competitionMode && ladderId != null && !contextService().isSessionLadderId(ladderId)) {
      UserSessionState.storeSelectedGroupId(request, ladderId);
    }

    if (contextService().isSeasonClosed(seasonId)) {
      if (competitionMode) {
        return routingService().competitionLogRedirect("seasonClosed", ladderId);
      }
      String target =
          (ladderId != null && seasonId != null)
              ? "/private-groups/" + ladderId + "?seasonId=" + seasonId + "&toast=seasonClosed"
              : (ladderId != null
                  ? "/private-groups/" + ladderId + "?toast=seasonClosed"
                  : "/home?toast=seasonClosed");
      return "redirect:" + target;
    }

    // ðŸ”¹ Restrict to members/admins of the seasonâ€™s ladder config (if seasonId
    // present)
    ResolvedMatchLogContext context =
        contextService().resolveMatchLogContext(seasonId, ladderId, Instant.now(), competitionMode);
    LadderSeason season = context.getSeason();
    ladderId = context.getSelectionLadderId();
    if (season != null) {
      seasonId = season.getId();
    }
    if (context.isSession()
        && currentUser != null
        && currentUser.getId() != null
        && !contextService()
            .hasActiveSessionMembership(context.getSelectionLadderId(), currentUser.getId())) {
      return competitionMode
          ? routingService().competitionLogRedirect("forbidden", ladderId)
          : "redirect:/home?toast=forbidden";
    }
    if (!competitionMode
        && !context.isSession()
        && !contextService().canAccessStandardLadderContext(currentUser, context)) {
      return "redirect:/home?toast=forbidden";
    }
    if (!competitionMode && ladderId != null && !context.isSession()) {
      UserSessionState.storeSelectedGroupId(request, ladderId);
    }

    RoundRobinService.ActiveSessionAssignment activeSessionRoundRobin =
        resolveActiveSessionRoundRobinAssignment(context, currentUser);
    if (activeSessionRoundRobin != null) {
      returnToRoundRobinId = activeSessionRoundRobin.roundRobin().getId();
      returnToRoundRobinEntryId = activeSessionRoundRobin.entry().getId();
      returnToRoundRobinRound = activeSessionRoundRobin.currentRound();
      if (activeSessionRoundRobin.entry().isBye() || activeSessionRoundRobin.match() != null) {
        return redirectToSessionRoundRobinTask(
            context, sessionRoundRobinLockedMessage(activeSessionRoundRobin));
      }
      RoundRobinSelection lockedSelection =
          sessionRoundRobinSelection(activeSessionRoundRobin.entry(), currentUser.getId());
      prefillA1 = lockedSelection.a1();
      prefillA2 = lockedSelection.a2();
      prefillB1 = lockedSelection.b1();
      prefillB2 = lockedSelection.b2();
      model.addAttribute("toastMessage", "Use this assigned round-robin pairing next.");
      model.addAttribute("toastLevel", "info");
    }

    Set<Long> eligibleMemberIds = context.getEligibleMemberIds();
    final List<User> all;
    if (eligibleMemberIds == null) {
      all = Collections.emptyList();
    } else if (eligibleMemberIds.isEmpty()) {
      all = Collections.emptyList();
    } else {
      all = userRepo.findAllById(eligibleMemberIds);
    }
    PlayerSelectionLists selectionLists =
        playerPickerService()
            .buildCreatePlayerSelectionLists(
                all, currentUser, meEmail, eligibleMemberIds, seasonId, voiceReviewMode);
    populatePlayerSelectionModel(
        model, selectionLists.users(), selectionLists.otherPlayers(), ladderId, seasonId);
    // Per-user passphrase support removed: no passphrase attributes added to model

    String navName = currentUser != null ? currentUser.getNickName() : null;
    model.addAttribute("userName", navName);

    model.addAttribute("ladderId", ladderId);
    model.addAttribute("seasonId", seasonId);
    model.addAttribute("competitionLogMode", competitionMode);
    model.addAttribute("returnToRoundRobinId", returnToRoundRobinId);
    model.addAttribute("returnToRoundRobinEntryId", returnToRoundRobinEntryId);
    model.addAttribute("returnToRoundRobinRound", returnToRoundRobinRound);

    boolean hasSeasonAdminOverride =
        contextService().resolveSeasonAdminOverride(seasonId, currentUser);
    boolean canLogForOthers = contextService().canLogForOthersInContext(currentUser, context);
    model.addAttribute("hasSeasonAdminOverride", hasSeasonAdminOverride);
    model.addAttribute("quickLogAdminMode", !voiceReviewMode && canLogForOthers);
    pageShellService()
        .populateLadderSelectorContext(
            model,
            myMemberships,
            ladderId,
            season,
            competitionMode,
            contextService().shouldUsePlainHomeNav(ladderId, competitionMode));

    if (voiceReviewMode && currentUser != null) {
      model.addAttribute("selectedA1Guest", Boolean.FALSE);
      model.addAttribute("selectedA1", currentUser.getId());
    }
    if (prefillA1 != null || prefillA2 != null || prefillB1 != null || prefillB2 != null) {
      Long selectedA1 = parseUserId(prefillA1);
      model.addAttribute("selectedA1Guest", isGuest(prefillA1));
      model.addAttribute("selectedA1", selectedA1);
      model.addAttribute("selectedA2Guest", isGuest(prefillA2));
      model.addAttribute("selectedA2Id", parseUserId(prefillA2));
      model.addAttribute("selectedB1Guest", isGuest(prefillB1));
      model.addAttribute("selectedB1Id", parseUserId(prefillB1));
      model.addAttribute("selectedB2Guest", isGuest(prefillB2));
      model.addAttribute("selectedB2Id", parseUserId(prefillB2));
    } else if (!voiceReviewMode
        && canLogForOthers
        && currentUser != null
        && currentUser.getId() != null) {
      model.addAttribute("selectedA1Guest", Boolean.FALSE);
      model.addAttribute("selectedA1", currentUser.getId());
    }

    return "auth/logMatch";
  }

  @PostMapping
  @Transactional
  public String submit(
      Model model,
      Authentication auth,
      HttpServletRequest request,
      @RequestParam(value = "a1", required = false) String teamA1,
      @RequestParam(value = "a2", required = false) String teamA2,
      @RequestParam("b1") String teamB1,
      @RequestParam(value = "b2", required = false) String teamB2,
      @RequestParam("scoreA") int scoreA,
      @RequestParam("scoreB") int scoreB,
      @RequestParam(value = "playedAt", required = false) Optional<Long> playedAtMillis,
      /* per-user cosigner passphrase removed */
      @RequestParam(value = "seasonId", required = false) Long seasonId,
      @RequestParam(value = "ladderId", required = false) Long ladderId,
      @RequestParam(value = "competition", required = false, defaultValue = "false")
          boolean competitionMode,
      @RequestParam(value = "returnTo", required = false) String returnTo,
      @RequestParam(value = "returnToRoundRobinId", required = false) Long returnToRoundRobinId,
      @RequestParam(value = "returnToRoundRobinEntryId", required = false)
          Long returnToRoundRobinEntryId,
      @RequestParam(value = "returnToRoundRobinRound", required = false)
          Integer returnToRoundRobinRound,
      @RequestParam(value = "editMatchId", required = false) Long editMatchId) {
    return submitInternal(
        model,
        auth,
        request,
        teamA1,
        teamA2,
        teamB1,
        teamB2,
        scoreA,
        scoreB,
        playedAtMillis,
        seasonId,
        ladderId,
        competitionMode,
        returnTo,
        returnToRoundRobinId,
        returnToRoundRobinEntryId,
        returnToRoundRobinRound,
        editMatchId);
  }

  public String submit(
      Model model,
      Authentication auth,
      HttpServletRequest request,
      String teamA1,
      String teamA2,
      String teamB1,
      String teamB2,
      int scoreA,
      int scoreB,
      Optional<Long> playedAtMillis,
      Long seasonId,
      Long ladderId,
      boolean competitionMode,
      Long returnToRoundRobinId,
      Long returnToRoundRobinEntryId,
      Integer returnToRoundRobinRound,
      Long editMatchId) {
    return submitInternal(
        model,
        auth,
        request,
        teamA1,
        teamA2,
        teamB1,
        teamB2,
        scoreA,
        scoreB,
        playedAtMillis,
        seasonId,
        ladderId,
        competitionMode,
        null,
        returnToRoundRobinId,
        returnToRoundRobinEntryId,
        returnToRoundRobinRound,
        editMatchId);
  }

  public String submit(
      Model model,
      Authentication auth,
      HttpServletRequest request,
      String teamA1,
      String teamA2,
      String teamB1,
      String teamB2,
      int scoreA,
      int scoreB,
      Optional<Long> playedAtMillis,
      Long seasonId,
      Long ladderId,
      boolean competitionMode,
      Long returnToRoundRobinId,
      Integer returnToRoundRobinRound,
      Long editMatchId) {
    return submit(
        model,
        auth,
        request,
        teamA1,
        teamA2,
        teamB1,
        teamB2,
        scoreA,
        scoreB,
        playedAtMillis,
        seasonId,
        ladderId,
        competitionMode,
        returnToRoundRobinId,
        null,
        returnToRoundRobinRound,
        editMatchId);
  }

  private String submitInternal(
      Model model,
      Authentication auth,
      HttpServletRequest request,
      String teamA1,
      String teamA2,
      String teamB1,
      String teamB2,
      int scoreA,
      int scoreB,
      Optional<Long> playedAtMillis,
      Long seasonId,
      Long ladderId,
      boolean competitionMode,
      String returnTo,
      Long returnToRoundRobinId,
      Long returnToRoundRobinEntryId,
      Integer returnToRoundRobinRound,
      Long editMatchId) {
    Long expectedVersion = parseUserId(request.getParameter("expectedVersion"));
    if (!competitionMode) {
      ladderId = UserSessionState.resolveSelectedGroupId(request, ladderId);
    }

    User currentUser = getCurrentUser(auth);
    boolean voiceReviewMode = isVoiceReviewRequest(request);
    if (currentUser == null) {
      return "redirect:/login";
    }
    // ===== EDIT MODE HANDLING =====
    if (editMatchId != null) {
      return handleEditSubmission(
          model,
          auth,
          editMatchId,
          teamA1,
          teamA2,
          teamB1,
          teamB2,
          scoreA,
          scoreB,
          expectedVersion,
          currentUser,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
    }
    // ===== END EDIT MODE =====

    if (competitionMode) {
      List<LadderMembership> myMemberships = activeMemberships(currentUser);
      if (!contextService().isSessionLadderId(ladderId)) {
        ladderId = pageShellService().defaultCompetitionSessionLadderId(myMemberships);
      }
    }
    if (!competitionMode && contextService().isDirectCompetitionSelection(ladderId, seasonId)) {
      return routingService().competitionRedirect(null);
    }

    if (matchLoggingQuotaService != null) {
      MatchLoggingQuotaService.QuotaStatus quota = matchLoggingQuotaService.evaluate(currentUser);
      if (!quota.allowed()) {
        formInternal(
            model,
            auth,
            request,
            seasonId,
            ladderId,
            null,
            competitionMode,
            teamA1,
            teamA2,
            teamB1,
            teamB2,
            returnTo,
            returnToRoundRobinId,
            returnToRoundRobinEntryId,
            returnToRoundRobinRound);
        reapplySelections(model, teamA1, teamA2, teamB1, teamB2, scoreA, scoreB);
        model.addAttribute(
            "toastMessage",
            "Weekly logging limit reached ("
                + quota.limit()
                + " matches in 7 days). Please try again later.");
        model.addAttribute("toastLevel", "warning");
        return "auth/logMatch";
      }
    }

    // Normal match creation flow continues below...
    Match m = new Match();

    Instant playedAtInstant = playedAtMillis.map(Instant::ofEpochMilli).orElse(Instant.now());

    ResolvedMatchLogContext context =
        contextService()
            .resolveMatchLogContext(seasonId, ladderId, playedAtInstant, competitionMode);
    LadderSeason targetSeason = context.getSeason();
    if (context.isSession()
        && currentUser.getId() != null
        && !contextService()
            .hasActiveSessionMembership(context.getSelectionLadderId(), currentUser.getId())) {
      return competitionMode
          ? routingService().competitionLogRedirect("forbidden", ladderId)
          : "redirect:/home?toast=forbidden";
    }
    if (!competitionMode
        && !context.isSession()
        && !contextService().canAccessStandardLadderContext(currentUser, context)) {
      return "redirect:/home?toast=forbidden";
    }
    if (targetSeason == null) {
      formInternal(
          model,
          auth,
          request,
          seasonId,
          ladderId,
          null,
          competitionMode,
          teamA1,
          teamA2,
          teamB1,
          teamB2,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
      reapplySelections(model, teamA1, teamA2, teamB1, teamB2, scoreA, scoreB);
      model.addAttribute(
          "toastMessage", "Unable to find an active ladder season for the selected match time.");
      model.addAttribute("toastLevel", "warning");
      return "auth/logMatch";
    }
    try {
      contextService().requireCompetitionEligibility(currentUser, targetSeason);
    } catch (SecurityException ex) {
      formInternal(
          model,
          auth,
          request,
          targetSeason.getId(),
          ladderId,
          null,
          competitionMode,
          teamA1,
          teamA2,
          teamB1,
          teamB2,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
      reapplySelections(model, teamA1, teamA2, teamB1, teamB2, scoreA, scoreB);
      model.addAttribute("toastMessage", ex.getMessage());
      model.addAttribute("toastLevel", "danger");
      return "auth/logMatch";
    }

    Long resolvedSeasonId = targetSeason.getId();
    // keep seasonId in model for re-render on validation errors
    model.addAttribute("seasonId", resolvedSeasonId);

    model.addAttribute("returnToRoundRobinId", returnToRoundRobinId);
    model.addAttribute("returnToRoundRobinEntryId", returnToRoundRobinEntryId);
    model.addAttribute("returnToRoundRobinRound", returnToRoundRobinRound);

    boolean hasSeasonAdminOverride =
        contextService().resolveSeasonAdminOverride(resolvedSeasonId, currentUser);
    boolean canLogForOthers = contextService().canLogForOthersInContext(currentUser, context);

    if (!voiceReviewMode
        && !canLogForOthers
        && teamA1 != null
        && !teamA1.isBlank()
        && !Objects.equals(parseUserId(teamA1), currentUser.getId())) {
      return competitionMode
          ? routingService().competitionLogRedirect("forbidden", ladderId)
          : "redirect:/home?toast=forbidden";
    }

    User effectiveA1 = currentUser;
    boolean effectiveA1Guest = false;
    if (voiceReviewMode) {
      effectiveA1 = resolveUser(teamA1);
      effectiveA1Guest = isGuest(teamA1);
    } else if (canLogForOthers && teamA1 != null && !teamA1.isBlank()) {
      User adminSelectedA1 = resolveUser(teamA1);
      if (adminSelectedA1 != null) {
        effectiveA1 = adminSelectedA1;
      }
    }

    User resolvedA2 = resolveUser(teamA2);
    User resolvedB1 = resolveUser(teamB1);
    User resolvedB2 = resolveUser(teamB2);
    boolean resolvedA2Guest = isGuest(teamA2);
    boolean resolvedB1Guest = isGuest(teamB1);
    boolean resolvedB2Guest = isGuest(teamB2);

    RoundRobinService.ActiveSessionAssignment activeSessionRoundRobin =
        resolveActiveSessionRoundRobinAssignment(context, currentUser);
    if (activeSessionRoundRobin != null) {
      returnToRoundRobinId = activeSessionRoundRobin.roundRobin().getId();
      returnToRoundRobinEntryId = activeSessionRoundRobin.entry().getId();
      returnToRoundRobinRound = activeSessionRoundRobin.currentRound();
      if (activeSessionRoundRobin.entry().isBye() || activeSessionRoundRobin.match() != null) {
        return redirectToSessionRoundRobinTask(
            context, sessionRoundRobinLockedMessage(activeSessionRoundRobin));
      }
      if (!matchesRoundRobinAssignment(
          activeSessionRoundRobin.entry(), effectiveA1, resolvedA2, resolvedB1, resolvedB2)) {
        RoundRobinSelection lockedSelection =
            sessionRoundRobinSelection(activeSessionRoundRobin.entry(), currentUser.getId());
        formInternal(
            model,
            auth,
            request,
            resolvedSeasonId,
            ladderId,
            null,
            competitionMode,
            lockedSelection.a1(),
            lockedSelection.a2(),
            lockedSelection.b1(),
            lockedSelection.b2(),
            returnTo,
            returnToRoundRobinId,
            returnToRoundRobinEntryId,
            returnToRoundRobinRound);
        reapplySelections(
            model,
            lockedSelection.a1(),
            lockedSelection.a2(),
            lockedSelection.b1(),
            lockedSelection.b2(),
            scoreA,
            scoreB);
        model.addAttribute(
            "toastMessage", "You’re in an active round robin. Use the assigned pairing shown here.");
        model.addAttribute("toastLevel", "warning");
        return "auth/logMatch";
      }
    }

    if (hasSamePlayerOnBothTeams(
        effectiveA1, effectiveA1Guest,
        resolvedA2, resolvedA2Guest,
        resolvedB1, resolvedB1Guest,
        resolvedB2, resolvedB2Guest)) {
      formInternal(
          model,
          auth,
          request,
          resolvedSeasonId,
          ladderId,
          null,
          competitionMode,
          teamA1,
          teamA2,
          teamB1,
          teamB2,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
      reapplySelections(model, teamA1, teamA2, teamB1, teamB2, scoreA, scoreB);
      model.addAttribute("toastMessage", "A player cannot be selected on both teams.");
      model.addAttribute("toastLevel", "warning");
      return "auth/logMatch";
    }

    // ðŸ”’ Submit-time membership guard (reject crafted POSTs)
    MatchValidationRequest validationRequest = new MatchValidationRequest();
    validationRequest.setSeason(targetSeason);
    validationRequest.setEligibleMemberIds(context.getEligibleMemberIds());
    ValidationSlotAssignments slotAssignments =
        resolveValidationSlotAssignments(
            currentUser,
            effectiveA1,
            effectiveA1Guest,
            resolvedA2,
            resolvedA2Guest,
            resolvedB1,
            resolvedB1Guest,
            resolvedB2,
            resolvedB2Guest,
            voiceReviewMode);
    if (voiceReviewMode && !canLogForOthers && !slotAssignments.currentUserParticipates()) {
      formInternal(
          model,
          auth,
          request,
          resolvedSeasonId,
          ladderId,
          null,
          competitionMode,
          teamA1,
          teamA2,
          teamB1,
          teamB2,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
      reapplySelections(model, teamA1, teamA2, teamB1, teamB2, scoreA, scoreB);
      model.addAttribute("toastMessage", "You must be on one of the teams to log a voice result.");
      model.addAttribute("toastLevel", "warning");
      return "auth/logMatch";
    }
    validationRequest.setReporterSlot(
        toReporterSlot(
            slotAssignments.getReporter(),
            voiceReviewMode && slotAssignments.getReporter().isGuest()));
    validationRequest.setPartnerSlot(toPlayerSlot(slotAssignments.getPartner()));
    validationRequest.setOpponentOneSlot(toPlayerSlot(slotAssignments.getOpponentOne()));
    validationRequest.setOpponentTwoSlot(toPlayerSlot(slotAssignments.getOpponentTwo()));
    validationRequest.setRequireOpponentMember(!isGuestOnlyPersonalRecordAllowed(targetSeason));

    MatchValidationResult validationResult = matchValidationService.validate(validationRequest);
    if (!validationResult.isValid()) {
      form(
          model,
          auth,
          request,
          resolvedSeasonId,
          ladderId,
          null,
          competitionMode,
          teamA1,
          teamA2,
          teamB1,
          teamB2,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
      reapplySelections(model, teamA1, teamA2, teamB1, teamB2, scoreA, scoreB);
      model.addAttribute(
          "toastMessage",
          "Only members of this ladder can be selected for this season. "
              + String.join(" â€¢ ", validationResult.getErrors()));
      model.addAttribute("toastLevel", "warning");
      return "auth/logMatch";
    }

    Instant now = Instant.now();
    Long acceptedDuplicateWarningMatchId =
        parseUserId(request.getParameter("duplicateWarningAcceptedMatchId"));

    m.setSeason(targetSeason);
    m.setSourceSessionConfig(context.getSessionConfig());
    m.setPlayedAt(playedAtInstant);

    m.setA1(effectiveA1);
    m.setA2(resolvedA2);
    m.setB1(resolvedB1);
    m.setB2(resolvedB2);

    m.setA1Guest(effectiveA1Guest);
    m.setA2Guest(resolvedA2Guest);
    m.setB1Guest(resolvedB1Guest);
    m.setB2Guest(resolvedB2Guest);
    m.setScoreA(scoreA);
    m.setScoreB(scoreB);
    m.setTranscript(sanitizeVoiceTranscript(request.getParameter("transcript")));

    ScoreValidationResult scoreValidation = matchValidationService.validateScore(scoreA, scoreB);
    if (!scoreValidation.isValid()) {
      formInternal(
          model,
          auth,
          request,
          resolvedSeasonId,
          ladderId,
          null,
          competitionMode,
          teamA1,
          teamA2,
          teamB1,
          teamB2,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
      reapplySelections(model, teamA1, teamA2, teamB1, teamB2, scoreA, scoreB);
      model.addAttribute("toastMessage", scoreValidation.getErrorMessage());
      model.addAttribute("toastLevel", "warning");
      return "auth/logMatch";
    }

    if (recentDuplicateMatchWarningService != null) {
      java.util.Optional<RecentDuplicateMatchWarningService.RecentDuplicateMatchWarning>
          duplicateWarning =
              recentDuplicateMatchWarningService.findWarning(
                  new RecentDuplicateMatchWarningService.DetectionRequest(
                      targetSeason != null ? targetSeason.getId() : null,
                      context.isSession() ? context.getSelectionLadderId() : null,
                      duplicateWarningTeamKeys(
                          effectiveA1, effectiveA1Guest, resolvedA2, resolvedA2Guest),
                      duplicateWarningTeamKeys(
                          resolvedB1, resolvedB1Guest, resolvedB2, resolvedB2Guest),
                      scoreA,
                      scoreB,
                      now));
      if (duplicateWarning.isPresent()
          && !Objects.equals(acceptedDuplicateWarningMatchId, duplicateWarning.get().matchId())) {
        formInternal(
            model,
            auth,
            request,
            resolvedSeasonId,
            ladderId,
            null,
            competitionMode,
            teamA1,
            teamA2,
            teamB1,
            teamB2,
            returnTo,
            returnToRoundRobinId,
            returnToRoundRobinEntryId,
            returnToRoundRobinRound);
        reapplySelections(model, teamA1, teamA2, teamB1, teamB2, scoreA, scoreB);
        model.addAttribute("duplicateWarningMatchId", duplicateWarning.get().matchId());
        model.addAttribute("duplicateWarningMessage", duplicateWarning.get().message());
        return "auth/logMatch";
      }
    }

    // per-user passphrase parameters are ignored (feature removed)

    // Per-user passphrase validation removed.
    // If ladder requires additional security, that behavior should be implemented
    // by the higher-level confirmation service; for now proceed with match logging.

    RoundRobinEntry reservedTournamentEntry = null;
    if (isTournamentMode(targetSeason) && roundRobinService != null) {
      try {
        reservedTournamentEntry =
            roundRobinService.reserveTournamentEntry(
                targetSeason,
                returnToRoundRobinEntryId,
                effectiveA1,
                resolvedA2,
                resolvedB1,
                resolvedB2);
      } catch (RoundRobinModificationException ex) {
        formInternal(
            model,
            auth,
            request,
            resolvedSeasonId,
            ladderId,
            null,
            competitionMode,
            teamA1,
            teamA2,
            teamB1,
            teamB2,
            returnTo,
            returnToRoundRobinId,
            returnToRoundRobinEntryId,
            returnToRoundRobinRound);
        reapplySelections(model, teamA1, teamA2, teamB1, teamB2, scoreA, scoreB);
        model.addAttribute("toastMessage", ex.getMessage());
        model.addAttribute("toastLevel", "warning");
        return "auth/logMatch";
      }
    }

    UserMatchLogGateService.MatchLogGateResult gateResult =
        userMatchLogGateService.reserveMatchLogging(
            currentUser.getId(), now, matchLogRateLimitEnabled);
    syncSessionUserMatchLoggingState(currentUser, gateResult.user());
    if (!gateResult.allowed()) {
      formInternal(
          model,
          auth,
          request,
          resolvedSeasonId,
          ladderId,
          null,
          competitionMode,
          teamA1,
          teamA2,
          teamB1,
          teamB2,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
      reapplySelections(model, teamA1, teamA2, teamB1, teamB2, scoreA, scoreB);
      model.addAttribute("toastMessage", gateResult.message());
      model.addAttribute("toastLevel", "warning");
      return "auth/logMatch";
    }
    User loggingUser = gateResult.user();
    if (effectiveA1 != null && Objects.equals(effectiveA1.getId(), currentUser.getId())) {
      effectiveA1 = loggingUser;
    }
    m.setState(MatchState.PROVISIONAL);
    m.setLoggedBy(loggingUser);

    Match saved = matchFactory.createMatch(m);
    if (reservedTournamentEntry != null && saved.getId() != null && roundRobinService != null) {
      roundRobinService.linkEntryToMatch(reservedTournamentEntry, saved.getId());
    }
    // If this match was voice-logged earlier, attempt to link the interpretation event
    try {
      if (learningService != null) {
        learningService.linkInterpretationEventToMatch(
            saved.getId(),
            targetSeason != null && targetSeason.getLadderConfig() != null
                ? targetSeason.getLadderConfig().getId()
                : null,
            m.getTranscript(),
            currentUser != null ? currentUser.getId() : null);
      }
    } catch (Exception ex) {
      log.warn(
          "Failed to link interpretation event for match {}: {}", saved.getId(), ex.getMessage());
    }
    ladderV2.applyMatch(
        saved); // (no API change; still applies to correct season via service rules)
    trophyAwardService.evaluateMatch(saved);

    if (returnToRoundRobinId != null) {
      StringBuilder redirect =
          new StringBuilder("redirect:/round-robin/view/").append(returnToRoundRobinId);
      boolean first = true;
      if (returnToRoundRobinRound != null) {
        redirect.append(first ? '?' : '&').append("round=").append(returnToRoundRobinRound);
        first = false;
      }
      redirect.append(first ? '?' : '&').append("toast=matchLogged");
      redirect.append("&matchId=").append(saved.getId());
      return redirect.toString();
    }

    String returnToRedirect =
        routingService().redirectToReturnTarget(returnTo, "matchLogged", saved.getId());
    if (returnToRedirect != null) {
      return returnToRedirect;
    }

    Long targetLadderId =
        targetSeason != null && targetSeason.getLadderConfig() != null
            ? targetSeason.getLadderConfig().getId()
            : ladderId;
    if (targetSeason != null
        && targetSeason.getLadderConfig() != null
        && targetSeason.getLadderConfig().isCompetitionType()) {
      Long sessionLadderId = context.isSession() ? context.getSelectionLadderId() : ladderId;
      return routingService()
          .competitionContextRedirect(sessionLadderId, "matchLogged", saved.getId());
    }
    String standingsRedirect = "/standings";
    if (targetLadderId != null && resolvedSeasonId != null) {
      standingsRedirect +=
          "?ladderId=" + targetLadderId + "&seasonId=" + resolvedSeasonId + "&toast=matchLogged";
    } else if (targetLadderId != null) {
      standingsRedirect += "?ladderId=" + targetLadderId + "&toast=matchLogged";
    } else if (resolvedSeasonId != null) {
      standingsRedirect += "?seasonId=" + resolvedSeasonId + "&toast=matchLogged";
    } else {
      standingsRedirect += "?toast=matchLogged";
    }
    standingsRedirect += "&matchId=" + saved.getId();
    return "redirect:" + standingsRedirect;
  }

  private void populatePlayerSelectionModel(
      Model model, List<User> users, List<User> otherPlayers, Long ladderId, Long seasonId) {
    playerPickerService()
        .populatePlayerSelectionModel(model, users, otherPlayers, ladderId, seasonId);
  }

  private void syncSessionUserMatchLoggingState(User sessionUser, User persistedUser) {
    if (sessionUser == null || persistedUser == null) {
      return;
    }
    sessionUser.setFailedPassphraseAttempts(persistedUser.getFailedPassphraseAttempts());
    sessionUser.setPassphraseTimeoutUntil(persistedUser.getPassphraseTimeoutUntil());
    sessionUser.setLastMatchLoggedAt(persistedUser.getLastMatchLoggedAt());
    sessionUser.setConsecutiveMatchLogs(persistedUser.getConsecutiveMatchLogs());
  }

  private MatchLogPlayerPickerService playerPickerService() {
    return new MatchLogPlayerPickerService(
        matchRepo, seasonRepo, courtNameService, ladderConfigRepository);
  }

  private MatchLogPageShellService pageShellService() {
    return new MatchLogPageShellService();
  }

  private MatchLogContextService contextService() {
    return new MatchLogContextService(
        seasonRepo,
        ladderConfigRepository,
        ladderMembershipRepository,
        matchValidationService,
        ladderAccessService,
        competitionAutoModerationService,
        LADDER_ZONE);
  }

  private MatchLogRoutingService routingService() {
    return new MatchLogRoutingService();
  }

  private RoundRobinService.ActiveSessionAssignment resolveActiveSessionRoundRobinAssignment(
      ResolvedMatchLogContext context, User currentUser) {
    if (roundRobinService == null
        || context == null
        || !context.isSession()
        || context.getSessionConfig() == null
        || currentUser == null
        || currentUser.getId() == null) {
      return null;
    }
    return roundRobinService
        .findActiveSessionAssignment(context.getSessionConfig(), currentUser.getId())
        .orElse(null);
  }

  private String redirectToSessionRoundRobinTask(
      ResolvedMatchLogContext context, String toastMessage) {
    Long ladderId = context != null ? context.getSelectionLadderId() : null;
    if (ladderId == null) {
      return "redirect:/home";
    }
    String path =
        org.springframework.web.util.UriComponentsBuilder.fromPath("/groups/{ladderId}")
            .queryParam("toastMessage", toastMessage)
            .buildAndExpand(ladderId)
            .encode()
            .toUriString();
    return "redirect:" + path;
  }

  private String sessionRoundRobinLockedMessage(
      RoundRobinService.ActiveSessionAssignment activeSessionRoundRobin) {
    if (activeSessionRoundRobin == null || activeSessionRoundRobin.entry() == null) {
      return "Finish your active round-robin task from the session page first.";
    }
    if (activeSessionRoundRobin.entry().isBye()) {
      return "You have a bye in the active round robin. Wait for the next round to open.";
    }
    if (activeSessionRoundRobin.match() != null
        && activeSessionRoundRobin.match().getState() == MatchState.CONFIRMED) {
      return "Your round-robin match is already complete. Wait for the next round to open.";
    }
    return "Your round-robin match is already logged. Confirm it from the session card before logging anything else.";
  }

  private RoundRobinSelection sessionRoundRobinSelection(RoundRobinEntry entry, Long currentUserId) {
    if (entry == null) {
      return new RoundRobinSelection(null, null, null, null);
    }
    User orderedA1 = entry.getA1();
    User orderedA2 = entry.getA2();
    User orderedB1 = entry.getB1();
    User orderedB2 = entry.getB2();
    if (currentUserId != null) {
      if (sameUser(entry.getA1(), currentUserId)) {
        orderedA1 = entry.getA1();
        orderedA2 = entry.getA2();
        orderedB1 = entry.getB1();
        orderedB2 = entry.getB2();
      } else if (sameUser(entry.getA2(), currentUserId)) {
        orderedA1 = entry.getA2();
        orderedA2 = entry.getA1();
        orderedB1 = entry.getB1();
        orderedB2 = entry.getB2();
      } else if (sameUser(entry.getB1(), currentUserId)) {
        orderedA1 = entry.getB1();
        orderedA2 = entry.getB2();
        orderedB1 = entry.getA1();
        orderedB2 = entry.getA2();
      } else if (sameUser(entry.getB2(), currentUserId)) {
        orderedA1 = entry.getB2();
        orderedA2 = entry.getB1();
        orderedB1 = entry.getA1();
        orderedB2 = entry.getA2();
      }
    }
    return new RoundRobinSelection(
        userParamValue(orderedA1), userParamValue(orderedA2), userParamValue(orderedB1), userParamValue(orderedB2));
  }

  private String userParamValue(User user) {
    return user != null && user.getId() != null ? String.valueOf(user.getId()) : null;
  }

  private boolean sameUser(User user, Long userId) {
    return user != null && user.getId() != null && user.getId().equals(userId);
  }

  private boolean matchesRoundRobinAssignment(
      RoundRobinEntry entry, User a1, User a2, User b1, User b2) {
    if (entry == null) {
      return false;
    }
    return teamsMatchUnordered(teamIds(entry.getA1(), entry.getA2()), teamIds(entry.getB1(), entry.getB2()), teamIds(a1, a2), teamIds(b1, b2));
  }

  private boolean teamsMatchUnordered(
      Set<Long> entryTeamA, Set<Long> entryTeamB, Set<Long> submittedTeamA, Set<Long> submittedTeamB) {
    return (entryTeamA.equals(submittedTeamA) && entryTeamB.equals(submittedTeamB))
        || (entryTeamA.equals(submittedTeamB) && entryTeamB.equals(submittedTeamA));
  }

  private Set<Long> teamIds(User first, User second) {
    Set<Long> ids = new HashSet<>();
    if (first != null && first.getId() != null) {
      ids.add(first.getId());
    }
    if (second != null && second.getId() != null) {
      ids.add(second.getId());
    }
    return ids;
  }

  private record RoundRobinSelection(String a1, String a2, String b1, String b2) {}

  private void reapplySelections(
      Model model,
      String teamA1,
      String teamA2,
      String teamB1,
      String teamB2,
      int scoreA,
      int scoreB) {
    model.addAttribute("selectedA1", parseUserId(teamA1));
    model.addAttribute("selectedA1Guest", isGuest(teamA1));
    model.addAttribute("selectedA2Guest", isGuest(teamA2));
    model.addAttribute("selectedA2Id", parseUserId(teamA2));
    model.addAttribute("selectedB1Guest", isGuest(teamB1));
    model.addAttribute("selectedB1Id", parseUserId(teamB1));
    model.addAttribute("selectedB2Guest", isGuest(teamB2));
    model.addAttribute("selectedB2Id", parseUserId(teamB2));
    model.addAttribute("selectedScoreA", scoreA);
    model.addAttribute("selectedScoreB", scoreB);
  }

  // ...existing code...

  private List<LadderMembership> activeMemberships(User user) {
    if (ladderMembershipRepository == null || user == null || user.getId() == null) {
      return List.of();
    }
    return ladderMembershipRepository.findByUserIdAndState(
        user.getId(), LadderMembership.State.ACTIVE);
  }

  private User resolveUser(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      Long id = Long.valueOf(value);
      return userRepo.findById(id).orElse(null);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private boolean isGuest(String value) {
    if (value == null || value.isBlank()) {
      return true;
    }
    try {
      Long.parseLong(value);
      return false;
    } catch (NumberFormatException ex) {
      return true;
    }
  }

  private Long parseUserId(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private List<String> duplicateWarningTeamKeys(
      User firstPlayer, boolean firstGuest, User secondPlayer, boolean secondGuest) {
    List<String> teamKeys = new ArrayList<>(2);
    teamKeys.add(duplicateWarningParticipantKey(firstPlayer, firstGuest));
    teamKeys.add(duplicateWarningParticipantKey(secondPlayer, secondGuest));
    teamKeys.sort(String::compareTo);
    return teamKeys;
  }

  private String duplicateWarningParticipantKey(User player, boolean guest) {
    if (guest || player == null || player.getId() == null) {
      return "guest";
    }
    return "user:" + player.getId();
  }

  private boolean isGuestOnlyPersonalRecordAllowed(LadderSeason season) {
    if (season == null || season.getLadderConfig() == null) {
      return false;
    }
    var cfg = season.getLadderConfig();
    return cfg.isAllowGuestOnlyPersonalMatches()
        && cfg.getSecurityLevel() != null
        && com.w3llspring.fhpb.web.model.LadderSecurity.normalize(cfg.getSecurityLevel())
            .isSelfConfirm();
  }

  private boolean isTournamentMode(LadderSeason season) {
    return season != null
        && season.getLadderConfig() != null
        && season.getLadderConfig().isTournamentMode();
  }

  private User getCurrentUser(Authentication auth) {
    return AuthenticatedUserSupport.currentUser(auth);
  }

  /**
   * Handle edit mode for existing matches. Implements Phase B of user correction feature as per
   * USER_CORRECTION_FEATURE.md
   */
  private String handleEditMode(
      Model model,
      Authentication auth,
      Long editMatchId,
      Long seasonId,
      Long ladderId,
      User currentUser,
      String returnTo,
      Long returnToRoundRobinId,
      Long returnToRoundRobinEntryId,
      Integer returnToRoundRobinRound) {
    // Load the match
    Match match =
        matchRepo
            .findByIdWithUsers(editMatchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + editMatchId));
    model.addAttribute("returnToPath", routingService().sanitizeReturnTo(returnTo));
    boolean hasSeasonAdminOverride =
        contextService()
            .resolveSeasonAdminOverride(
                match.getSeason() != null ? match.getSeason().getId() : null, currentUser);
    // Never allow editing nullified matches.
    if (match.getState() == MatchState.NULLIFIED) {
      log.warn(
          "User {} attempted to edit nullified match {}",
          currentUser != null ? currentUser.getId() : null,
          editMatchId);
      return "redirect:/home?toast=forbidden";
    }
    // Confirmed matches require ladder admin privileges.
    if (match.getState() == MatchState.CONFIRMED && !hasSeasonAdminOverride) {
      log.warn(
          "User {} attempted to edit confirmed match {}",
          currentUser != null ? currentUser.getId() : null,
          editMatchId);
      return "redirect:/home?toast=forbidden";
    }

    // Security check: use the shared workflow rule so standard-mode edit rights
    // follow the current confirmation priority instead of ad hoc logger checks.
    boolean canEdit = MatchWorkflowRules.canEdit(match, currentUser, hasSeasonAdminOverride);

    if (!canEdit) {
      log.warn(
          "User {} attempted to edit match {} without permission",
          currentUser != null ? currentUser.getId() : null,
          editMatchId);
      return "redirect:/home?toast=forbidden";
    }

    // Determine ladder admin for UI purposes.

    // Set edit mode flag
    model.addAttribute("editMode", true);
    model.addAttribute("match", match);
    model.addAttribute("editMatchId", editMatchId);
    model.addAttribute("expectedVersion", match.getVersion());
    model.addAttribute("returnToRoundRobinId", returnToRoundRobinId);
    model.addAttribute("returnToRoundRobinEntryId", returnToRoundRobinEntryId);
    model.addAttribute("returnToRoundRobinRound", returnToRoundRobinRound);

    // Pre-populate form with existing match data
    // Handle potential null values for optional players (A2, B2)
    model.addAttribute("selectedA1Guest", match.isA1Guest());
    model.addAttribute("selectedA1", match.getA1() != null ? match.getA1().getId() : null);
    model.addAttribute("selectedA2Id", match.getA2() != null ? match.getA2().getId() : null);
    model.addAttribute("selectedA2Guest", match.getA2() == null);
    model.addAttribute("selectedB1Id", match.getB1() != null ? match.getB1().getId() : null);
    model.addAttribute("selectedB1Guest", match.getB1() == null);
    model.addAttribute("selectedB2Id", match.getB2() != null ? match.getB2().getId() : null);
    model.addAttribute("selectedB2Guest", match.getB2() == null);
    model.addAttribute("selectedScoreA", match.getScoreA());
    model.addAttribute("selectedScoreB", match.getScoreB());

    // Use match's season and ladder
    LadderSeason matchSeason = match.getSeason();
    Long matchSeasonId = matchSeason.getId();

    // Populate user lists directly (avoid form() redirecting if season closed)
    ResolvedMatchLogContext editContext = contextService().resolveEditMatchContext(match);
    Set<Long> eligibleMemberIds = editContext.getEligibleMemberIds();
    List<User> all;
    if (eligibleMemberIds == null) {
      all = Collections.emptyList();
    } else if (eligibleMemberIds.isEmpty()) {
      all = Collections.emptyList();
    } else {
      all = userRepo.findAllById(eligibleMemberIds);
    }
    PlayerSelectionLists selectionLists =
        playerPickerService().buildEditPlayerSelectionLists(all, match, currentUser, matchSeasonId);

    // Call the normal form method to populate other attributes (passphrases, etc.), but skip user
    // lists
    // form(model, auth, matchSeasonId, matchLadderId, null);

    // Override with edit mode attributes again (in case form() overwrote them)
    model.addAttribute("editMode", true);
    model.addAttribute("match", match);
    model.addAttribute("editMatchId", editMatchId);
    model.addAttribute("expectedVersion", match.getVersion());
    model.addAttribute("returnToRoundRobinId", returnToRoundRobinId);
    model.addAttribute("returnToRoundRobinEntryId", returnToRoundRobinEntryId);
    model.addAttribute("returnToRoundRobinRound", returnToRoundRobinRound);
    model.addAttribute("selectedA1Guest", match.isA1Guest());
    model.addAttribute("selectedA1", match.getA1() != null ? match.getA1().getId() : null);
    model.addAttribute("selectedA2Id", match.getA2() != null ? match.getA2().getId() : null);
    model.addAttribute("selectedA2Guest", match.getA2() == null);
    model.addAttribute("selectedB1Id", match.getB1() != null ? match.getB1().getId() : null);
    model.addAttribute("selectedB1Guest", match.getB1() == null);
    model.addAttribute("selectedB2Id", match.getB2() != null ? match.getB2().getId() : null);
    model.addAttribute("selectedB2Guest", match.getB2() == null);
    model.addAttribute("selectedScoreA", match.getScoreA());
    model.addAttribute("selectedScoreB", match.getScoreB());

    List<User> users = selectionLists.users();
    List<User> otherPlayers = selectionLists.otherPlayers();
    Long editLadderId =
        editContext.getSelectionLadderId() != null
            ? editContext.getSelectionLadderId()
            : (match.getSeason() != null && match.getSeason().getLadderConfig() != null
                ? match.getSeason().getLadderConfig().getId()
                : null);
    populatePlayerSelectionModel(model, users, otherPlayers, editLadderId, matchSeasonId);

    log.info(
        "Edit mode for match {}: users list has {} users: {}",
        editMatchId,
        users.size(),
        users.stream()
            .map(u -> u.getNickName() + "(" + u.getId() + ")")
            .collect(Collectors.joining(", ")));
    log.info(
        "Selected IDs: A1={}, A2={}, B1={}, B2={}",
        match.getA1() != null ? match.getA1().getId() : null,
        match.getA2() != null ? match.getA2().getId() : null,
        match.getB1() != null ? match.getB1().getId() : null,
        match.getB2() != null ? match.getB2().getId() : null);

    model.addAttribute("ladderId", editLadderId);
    model.addAttribute("seasonId", matchSeasonId);
    model.addAttribute("userName", currentUser != null ? currentUser.getNickName() : null);
    pageShellService()
        .populateLadderSelectorContext(
            model,
            activeMemberships(currentUser),
            editLadderId,
            matchSeason,
            false,
            contextService().shouldUsePlainHomeNav(editLadderId, false));
    model.addAttribute("hasSeasonAdminOverride", hasSeasonAdminOverride);

    return "auth/logMatch";
  }

  /**
   * Handle edit submission for existing matches. Implements Phase B of user correction feature as
   * per USER_CORRECTION_FEATURE.md
   */
  private String handleEditSubmission(
      Model model,
      Authentication auth,
      Long editMatchId,
      String teamA1,
      String teamA2,
      String teamB1,
      String teamB2,
      int scoreA,
      int scoreB,
      Long expectedVersion,
      User currentUser,
      String returnTo,
      Long returnToRoundRobinId,
      Long returnToRoundRobinEntryId,
      Integer returnToRoundRobinRound) {
    // Load the match
    Match match =
        matchRepo
            .findByIdWithUsers(editMatchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + editMatchId));
    if (expectedVersion == null || match.getVersion() != expectedVersion.longValue()) {
      model.addAttribute(
          "toastMessage",
          "This match was updated by someone else. Reload and review the latest version before editing.");
      model.addAttribute("toastLevel", "warning");
      return handleEditMode(
          model,
          auth,
          editMatchId,
          null,
          null,
          currentUser,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
    }
    boolean hasSeasonAdminOverride =
        contextService()
            .resolveSeasonAdminOverride(
                match.getSeason() != null ? match.getSeason().getId() : null, currentUser);
    // Never allow editing nullified matches.
    if (match.getState() == MatchState.NULLIFIED) {
      log.warn(
          "User {} attempted to edit nullified match {} (submit)",
          currentUser != null ? currentUser.getId() : null,
          editMatchId);
      return "redirect:/home?toast=forbidden";
    }
    // Confirmed matches require ladder admin privileges.
    if (match.getState() == MatchState.CONFIRMED && !hasSeasonAdminOverride) {
      log.warn(
          "User {} attempted to edit confirmed match {} (submit)",
          currentUser != null ? currentUser.getId() : null,
          editMatchId);
      return "redirect:/home?toast=forbidden";
    }

    // Security check: use the shared workflow rule so standard-mode edit rights
    // follow the current confirmation priority instead of ad hoc logger checks.
    boolean canEdit = MatchWorkflowRules.canEdit(match, currentUser, hasSeasonAdminOverride);

    if (!canEdit) {
      log.warn(
          "User {} attempted to edit match {} without permission (submit)",
          currentUser != null ? currentUser.getId() : null,
          editMatchId);
      return "redirect:/home?toast=forbidden";
    }

    try {
      contextService().requireCompetitionEligibility(currentUser, match.getSeason());
    } catch (SecurityException ex) {
      model.addAttribute("toastMessage", ex.getMessage());
      model.addAttribute("toastLevel", "danger");
      return handleEditMode(
          model,
          auth,
          editMatchId,
          null,
          null,
          currentUser,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
    }

    ScoreValidationResult scoreValidation = matchValidationService.validateScore(scoreA, scoreB);
    if (!scoreValidation.isValid()) {
      model.addAttribute("toastMessage", scoreValidation.getErrorMessage());
      model.addAttribute("toastLevel", "warning");
      return handleEditMode(
          model,
          auth,
          editMatchId,
          null,
          null,
          currentUser,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
    }

    User nextA1 = match.getA1();
    boolean nextA1Guest = match.isA1Guest();
    if (hasSeasonAdminOverride && teamA1 != null) {
      nextA1 = resolveUser(teamA1);
      nextA1Guest = isGuest(teamA1);
    }
    User nextA2 = resolveUser(teamA2);
    User nextB1 = resolveUser(teamB1);
    User nextB2 = resolveUser(teamB2);
    boolean nextA2Guest = isGuest(teamA2);
    boolean nextB1Guest = isGuest(teamB1);
    boolean nextB2Guest = isGuest(teamB2);

    if (hasSamePlayerOnBothTeams(
        nextA1, nextA1Guest,
        nextA2, nextA2Guest,
        nextB1, nextB1Guest,
        nextB2, nextB2Guest)) {
      model.addAttribute("toastMessage", "A player cannot be selected on both teams.");
      model.addAttribute("toastLevel", "warning");
      return handleEditMode(
          model,
          auth,
          editMatchId,
          null,
          null,
          currentUser,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
    }

    MatchValidationResult validationResult =
        validateEditParticipants(
            match.getSeason(),
            contextService().resolveEditMatchContext(match).getEligibleMemberIds(),
            hasSeasonAdminOverride,
            nextA1,
            nextA1Guest,
            nextA2,
            nextA2Guest,
            nextB1,
            nextB1Guest,
            nextB2,
            nextB2Guest);
    if (!validationResult.isValid()) {
      model.addAttribute(
          "toastMessage",
          "Only members of this ladder can be selected for this season. "
              + String.join(" â€¢ ", validationResult.getErrors()));
      model.addAttribute("toastLevel", "warning");
      return handleEditMode(
          model,
          auth,
          editMatchId,
          null,
          null,
          currentUser,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
    }

    if (isTournamentMode(match.getSeason()) && roundRobinService != null) {
      try {
        roundRobinService.assertTournamentMatchParticipants(
            editMatchId, nextA1, nextA2, nextB1, nextB2);
      } catch (RoundRobinModificationException ex) {
        model.addAttribute("toastMessage", ex.getMessage());
        model.addAttribute("toastLevel", "warning");
        return handleEditMode(
            model,
            auth,
            editMatchId,
            null,
            null,
            currentUser,
            returnTo,
            returnToRoundRobinId,
            returnToRoundRobinEntryId,
            returnToRoundRobinRound);
      }
    }

    try {
      matchStateTransitionService.editMatch(
          new MatchStateTransitionService.EditMatchCommand(
              editMatchId,
              currentUser,
              hasSeasonAdminOverride,
              expectedVersion,
              hasSeasonAdminOverride && teamA1 != null ? nextA1 : match.getA1(),
              hasSeasonAdminOverride && teamA1 != null ? nextA1Guest : match.isA1Guest(),
              nextA2,
              nextA2Guest,
              nextB1,
              nextB1Guest,
              nextB2,
              nextB2Guest,
              scoreA,
              scoreB));
    } catch (SecurityException ex) {
      log.warn(
          "User {} lost permission while editing match {}: {}",
          currentUser != null ? currentUser.getId() : null,
          editMatchId,
          ex.getMessage());
      return "redirect:/home?toast=forbidden";
    } catch (OptimisticLockingFailureException ex) {
      model.addAttribute(
          "toastMessage", "This match was updated by someone else. Reload and try again.");
      model.addAttribute("toastLevel", "warning");
      return handleEditMode(
          model,
          auth,
          editMatchId,
          null,
          null,
          currentUser,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      model.addAttribute("toastMessage", ex.getMessage());
      model.addAttribute("toastLevel", "warning");
      return handleEditMode(
          model,
          auth,
          editMatchId,
          null,
          null,
          currentUser,
          returnTo,
          returnToRoundRobinId,
          returnToRoundRobinEntryId,
          returnToRoundRobinRound);
    }

    // Do NOT apply learning corrections at edit time. Corrections should only be
    // recorded when/if the match is later confirmed (and only if it was edited).
    // The confirmation service will trigger learning on confirm.
    log.info(
        "Match {} edited by user {} (isAdmin: {})",
        editMatchId,
        currentUser != null ? currentUser.getId() : null,
        hasSeasonAdminOverride);

    if (returnToRoundRobinId != null) {
      if (returnToRoundRobinRound != null) {
        return "redirect:/round-robin/view/"
            + returnToRoundRobinId
            + "?round="
            + returnToRoundRobinRound;
      }
      return "redirect:/round-robin/view/" + returnToRoundRobinId;
    }

    String returnToRedirect =
        routingService().redirectToReturnTarget(returnTo, "matchUpdated", null);
    if (returnToRedirect != null) {
      return returnToRedirect;
    }

    // Redirect back to the originating ladder context when possible.
    Long seasonId = match.getSeason().getId();
    Long ladderId = match.getSeason().getLadderConfig().getId();
    if (match.getSeason() != null
        && match.getSeason().getLadderConfig() != null
        && match.getSeason().getLadderConfig().isCompetitionType()) {
      Long sessionLadderId =
          match.getSourceSessionConfig() != null ? match.getSourceSessionConfig().getId() : null;
      return routingService().competitionContextRedirect(sessionLadderId, "matchUpdated", null);
    }
    String standingsRedirect = "redirect:/standings";
    if (ladderId != null && seasonId != null) {
      standingsRedirect +=
          "?ladderId=" + ladderId + "&seasonId=" + seasonId + "&toast=matchUpdated";
    } else if (ladderId != null) {
      standingsRedirect += "?ladderId=" + ladderId + "&toast=matchUpdated";
    } else if (seasonId != null) {
      standingsRedirect += "?seasonId=" + seasonId + "&toast=matchUpdated";
    } else {
      standingsRedirect += "?toast=matchUpdated";
    }
    return standingsRedirect;
  }

  private boolean isVoiceReviewRequest(HttpServletRequest request) {
    if (request == null) {
      return false;
    }
    String raw = request.getParameter("voiceReview");
    if (!StringUtils.hasText(raw)) {
      return false;
    }
    String normalized = raw.trim();
    return !"0".equals(normalized) && !"false".equalsIgnoreCase(normalized);
  }

  private String sanitizeVoiceTranscript(String transcript) {
    if (!StringUtils.hasText(transcript)) {
      return null;
    }
    String normalized = transcript.trim();
    if (normalized.length() > MAX_VOICE_REVIEW_TRANSCRIPT_CHARS) {
      return normalized.substring(0, MAX_VOICE_REVIEW_TRANSCRIPT_CHARS);
    }
    return normalized;
  }

  private ValidationSlotAssignments resolveValidationSlotAssignments(
      User currentUser,
      User a1,
      boolean a1Guest,
      User a2,
      boolean a2Guest,
      User b1,
      boolean b1Guest,
      User b2,
      boolean b2Guest,
      boolean voiceReviewMode) {
    SlotDescriptor slotA1 = new SlotDescriptor("A1", a1, a1Guest, "set A1 as Guest");
    SlotDescriptor slotA2 = new SlotDescriptor("A2", a2, a2Guest, "leave A2 as Guest");
    SlotDescriptor slotB1 = new SlotDescriptor("B1", b1, b1Guest, "set B1 as Guest");
    SlotDescriptor slotB2 = new SlotDescriptor("B2", b2, b2Guest, "set B2 as Guest");

    if (!voiceReviewMode || currentUser == null || currentUser.getId() == null) {
      return new ValidationSlotAssignments(slotA1, slotA2, slotB1, slotB2, false);
    }

    Long currentUserId = currentUser.getId();
    if (slotMatchesUser(slotA1, currentUserId)) {
      return new ValidationSlotAssignments(slotA1, slotA2, slotB1, slotB2, true);
    }
    if (slotMatchesUser(slotA2, currentUserId)) {
      return new ValidationSlotAssignments(slotA2, slotA1, slotB1, slotB2, true);
    }
    if (slotMatchesUser(slotB1, currentUserId)) {
      return new ValidationSlotAssignments(slotB1, slotB2, slotA1, slotA2, true);
    }
    if (slotMatchesUser(slotB2, currentUserId)) {
      return new ValidationSlotAssignments(slotB2, slotB1, slotA1, slotA2, true);
    }

    return new ValidationSlotAssignments(slotA1, slotA2, slotB1, slotB2, false);
  }

  private boolean slotMatchesUser(SlotDescriptor slot, Long userId) {
    return slot != null
        && !slot.isGuest()
        && slot.getUser() != null
        && Objects.equals(slot.getUser().getId(), userId);
  }

  private PlayerSlot toReporterSlot(SlotDescriptor slot, boolean guestAllowed) {
    return PlayerSlot.builder(slot.getLabel())
        .userId(slot.getUser() != null ? slot.getUser().getId() : null)
        .guest(slot.isGuest())
        .guestAllowed(guestAllowed)
        .requireMemberCheck(true)
        .build();
  }

  private PlayerSlot toPlayerSlot(SlotDescriptor slot) {
    return PlayerSlot.builder(slot.getLabel())
        .userId(slot.getUser() != null ? slot.getUser().getId() : null)
        .guest(slot.isGuest())
        .guestAllowed(true)
        .requireMemberCheck(true)
        .guestSuggestion(slot.getGuestSuggestion())
        .build();
  }

  private MatchValidationResult validateEditParticipants(
      LadderSeason season,
      Set<Long> eligibleMemberIds,
      boolean ladderAdmin,
      User a1,
      boolean a1Guest,
      User a2,
      boolean a2Guest,
      User b1,
      boolean b1Guest,
      User b2,
      boolean b2Guest) {
    MatchValidationRequest validationRequest = new MatchValidationRequest();
    validationRequest.setSeason(season);
    validationRequest.setEligibleMemberIds(eligibleMemberIds);

    ValidationSlotAssignments slotAssignments =
        resolveValidationSlotAssignments(
            null, a1, a1Guest, a2, a2Guest, b1, b1Guest, b2, b2Guest, false);
    validationRequest.setReporterSlot(
        toReporterSlot(
            slotAssignments.getReporter(), ladderAdmin && slotAssignments.getReporter().isGuest()));
    validationRequest.setPartnerSlot(toPlayerSlot(slotAssignments.getPartner()));
    validationRequest.setOpponentOneSlot(toPlayerSlot(slotAssignments.getOpponentOne()));
    validationRequest.setOpponentTwoSlot(toPlayerSlot(slotAssignments.getOpponentTwo()));
    validationRequest.setRequireOpponentMember(!isGuestOnlyPersonalRecordAllowed(season));
    return matchValidationService.validate(validationRequest);
  }

  private boolean hasSamePlayerOnBothTeams(
      User a1,
      boolean a1Guest,
      User a2,
      boolean a2Guest,
      User b1,
      boolean b1Guest,
      User b2,
      boolean b2Guest) {
    Set<Long> teamAIds = new HashSet<>();
    Set<Long> teamBIds = new HashSet<>();

    addRealPlayerId(teamAIds, a1, a1Guest);
    addRealPlayerId(teamAIds, a2, a2Guest);
    addRealPlayerId(teamBIds, b1, b1Guest);
    addRealPlayerId(teamBIds, b2, b2Guest);

    return teamAIds.stream().anyMatch(teamBIds::contains);
  }

  private void addRealPlayerId(Set<Long> ids, User player, boolean guest) {
    if (ids == null || guest || player == null || player.getId() == null) {
      return;
    }
    ids.add(player.getId());
  }

  private static final class SlotDescriptor {
    private final String label;
    private final User user;
    private final boolean guest;
    private final String guestSuggestion;

    private SlotDescriptor(String label, User user, boolean guest, String guestSuggestion) {
      this.label = label;
      this.user = user;
      this.guest = guest;
      this.guestSuggestion = guestSuggestion;
    }

    private String getLabel() {
      return label;
    }

    private User getUser() {
      return user;
    }

    private boolean isGuest() {
      return guest;
    }

    private String getGuestSuggestion() {
      return guestSuggestion;
    }
  }

  private static final class ValidationSlotAssignments {
    private final SlotDescriptor reporter;
    private final SlotDescriptor partner;
    private final SlotDescriptor opponentOne;
    private final SlotDescriptor opponentTwo;
    private final boolean currentUserParticipates;

    private ValidationSlotAssignments(
        SlotDescriptor reporter,
        SlotDescriptor partner,
        SlotDescriptor opponentOne,
        SlotDescriptor opponentTwo,
        boolean currentUserParticipates) {
      this.reporter = reporter;
      this.partner = partner;
      this.opponentOne = opponentOne;
      this.opponentTwo = opponentTwo;
      this.currentUserParticipates = currentUserParticipates;
    }

    private SlotDescriptor getReporter() {
      return reporter;
    }

    private SlotDescriptor getPartner() {
      return partner;
    }

    private SlotDescriptor getOpponentOne() {
      return opponentOne;
    }

    private SlotDescriptor getOpponentTwo() {
      return opponentTwo;
    }

    private boolean currentUserParticipates() {
      return currentUserParticipates;
    }
  }
}
