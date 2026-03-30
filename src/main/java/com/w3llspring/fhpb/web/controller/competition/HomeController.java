package com.w3llspring.fhpb.web.controller.competition;

import com.w3llspring.fhpb.web.db.BandPositionRepository;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMatchLinkRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMatchLink;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionDisplayNameModerationService;
import com.w3llspring.fhpb.web.service.CompetitionSeasonService;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.LadderImprovementAdvisor;
import com.w3llspring.fhpb.web.service.LadderImprovementAdvisor.Advice;
import com.w3llspring.fhpb.web.service.LadderSecurityService;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.MatchDashboardService;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.competition.CompetitionSessionService;
import com.w3llspring.fhpb.web.service.competition.HomeSelectionService;
import com.w3llspring.fhpb.web.service.dashboard.MatchDashboardViewService;
import com.w3llspring.fhpb.web.service.matchentry.MatchEntryContextService;
import com.w3llspring.fhpb.web.service.standings.SeasonStandingsViewService;
import com.w3llspring.fhpb.web.service.standings.StandingsPageService;
import com.w3llspring.fhpb.web.service.user.UserOnboardingService;
import com.w3llspring.fhpb.web.session.LadderPageState;
import com.w3llspring.fhpb.web.session.UserSessionState;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import com.w3llspring.fhpb.web.util.ReturnToSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@Secured("ROLE_USER")
@SessionAttributes("homeState")
public class HomeController {

  private static final int DEFAULT_STANDINGS_PAGE_SIZE = 25;
  private static final int MAX_STANDINGS_PAGE_SIZE = 100;

  @Value("${fhpb.features.meetups.enabled:false}")
  private boolean meetupsEnabled;

  private final BandPositionRepository posRepo;
  private final LadderMatchLinkRepository linkRepo;
  private final com.w3llspring.fhpb.web.db.MatchRepository matchRepo;
  private final com.w3llspring.fhpb.web.db.RoundRobinEntryRepository rrEntryRepo;
  private final LadderSeasonRepository seasonRepo;
  private final LadderSecurityService ladderSecurityService;
  private final LadderImprovementAdvisor improvementAdvisor;
  // per-user passphrases removed
  private final LadderMembershipRepository membershipRepo;
  private final LadderConfigRepository ladderConfigRepo;
  private final LadderAccessService access;
  private final MatchConfirmationService matchConfirmationService;
  private final com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder;
  private final StoryModeService storyModeService;
  private final HomeSelectionService homeSelectionService;
  private final CompetitionSessionService competitionSessionService;
  private final StandingsPageService standingsPageService;
  private final SeasonStandingsViewService seasonStandingsViewService;
  private final MatchDashboardViewService matchDashboardViewService;
  private final MatchEntryContextService matchEntryContextService;
  private final UserOnboardingService userOnboardingService;
  private CompetitionSeasonService competitionSeasonService;
  private MatchDashboardService matchDashboardService;
  private CompetitionDisplayNameModerationService competitionDisplayNameModerationService;

  @Value("${fhpb.standings.page-size-default:25}")
  private int standingsPageSizeDefault = DEFAULT_STANDINGS_PAGE_SIZE;

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(HomeController.class);

  @Autowired
  public HomeController(
      BandPositionRepository posRepo,
      LadderMatchLinkRepository linkRepo,
      com.w3llspring.fhpb.web.db.MatchRepository matchRepo,
      com.w3llspring.fhpb.web.db.RoundRobinEntryRepository rrEntryRepo,
      LadderSeasonRepository seasonRepo,
      LadderSecurityService ladderSecurityService,
      LadderImprovementAdvisor improvementAdvisor,
      LadderMembershipRepository membershipRepo,
      LadderConfigRepository ladderConfigRepo,
      LadderAccessService access,
      MatchConfirmationService matchConfirmationService,
      com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder,
      StoryModeService storyModeService,
      HomeSelectionService homeSelectionService,
      CompetitionSessionService competitionSessionService,
      StandingsPageService standingsPageService,
      SeasonStandingsViewService seasonStandingsViewService,
      MatchDashboardViewService matchDashboardViewService,
      MatchEntryContextService matchEntryContextService,
      UserOnboardingService userOnboardingService,
      CompetitionSeasonService competitionSeasonService,
      MatchDashboardService matchDashboardService,
      CompetitionDisplayNameModerationService competitionDisplayNameModerationService) {
    this.posRepo = posRepo;
    this.linkRepo = linkRepo;
    this.matchRepo = matchRepo;
    this.rrEntryRepo = rrEntryRepo;
    this.seasonRepo = seasonRepo;
    this.improvementAdvisor = improvementAdvisor;
    this.membershipRepo = membershipRepo;
    this.ladderConfigRepo = ladderConfigRepo;
    this.ladderSecurityService = ladderSecurityService;
    this.access = access;
    this.matchConfirmationService = matchConfirmationService;
    this.matchRowModelBuilder = matchRowModelBuilder;
    this.storyModeService = storyModeService;
    this.homeSelectionService = homeSelectionService;
    this.competitionSessionService = competitionSessionService;
    this.standingsPageService = standingsPageService;
    this.seasonStandingsViewService = seasonStandingsViewService;
    this.matchDashboardViewService = matchDashboardViewService;
    this.matchEntryContextService = matchEntryContextService;
    this.userOnboardingService = userOnboardingService;
    this.competitionSeasonService = competitionSeasonService;
    this.matchDashboardService = matchDashboardService;
    this.competitionDisplayNameModerationService = competitionDisplayNameModerationService;
  }

  public HomeController(
      BandPositionRepository posRepo,
      LadderMatchLinkRepository linkRepo,
      com.w3llspring.fhpb.web.db.MatchRepository matchRepo,
      com.w3llspring.fhpb.web.db.RoundRobinEntryRepository rrEntryRepo,
      LadderSeasonRepository seasonRepo,
      LadderSecurityService ladderSecurityService,
      LadderImprovementAdvisor improvementAdvisor,
      LadderMembershipRepository membershipRepo,
      LadderConfigRepository ladderConfigRepo,
      LadderAccessService access,
      MatchConfirmationService matchConfirmationService,
      com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder,
      StoryModeService storyModeService,
      HomeSelectionService homeSelectionService,
      CompetitionSessionService competitionSessionService,
      StandingsPageService standingsPageService,
      SeasonStandingsViewService seasonStandingsViewService,
      MatchDashboardViewService matchDashboardViewService,
      MatchEntryContextService matchEntryContextService,
      UserOnboardingService userOnboardingService) {
    this(
        posRepo,
        linkRepo,
        matchRepo,
        rrEntryRepo,
        seasonRepo,
        ladderSecurityService,
        improvementAdvisor,
        membershipRepo,
        ladderConfigRepo,
        access,
        matchConfirmationService,
        matchRowModelBuilder,
        storyModeService,
        homeSelectionService,
        competitionSessionService,
        standingsPageService,
        seasonStandingsViewService,
        matchDashboardViewService,
        matchEntryContextService,
        userOnboardingService,
        null,
        null,
        null);
  }

  /** Ensure a state object exists in session */
  @ModelAttribute("homeState")
  public LadderPageState initHomeState() {
    return new LadderPageState();
  }

  @GetMapping("/home")
  public String home(
      @RequestParam(value = "ladderId", required = false) Long ladderId,
      @RequestParam(value = "seasonId", required = false) Long seasonId,
      @RequestParam(value = "toast", required = false) String toastKey,
      @RequestParam(value = "toastMessage", required = false) String toastMessage,
      @RequestParam(value = "matchId", required = false) Long matchId,
      @ModelAttribute("homeState") LadderPageState homeState, // <— session state
      HttpServletRequest request,
      Model model) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }
    if (homeSelectionService.isCompetitionSelection(ladderId, seasonId)) {
      return "redirect:/competition";
    }

    if (ladderId != null || seasonId != null) {
      org.springframework.ui.ExtendedModelMap selectionModel =
          new org.springframework.ui.ExtendedModelMap();
      String result =
          populateSelectedGroupSummaryModel(
              currentUser, ladderId, seasonId, homeState, request, selectionModel);
      if (result != null && result.startsWith("redirect:")) {
        return result;
      }
      Long effectiveLadderId = asLong(selectionModel.get("ladderId"));
      Long effectiveSeasonId = asLong(selectionModel.get("seasonId"));
      return buildPrivateGroupRedirect(
          effectiveLadderId, effectiveSeasonId, toastKey, toastMessage, matchId);
    }

    model.addAttribute("userName", currentUser.getNickName());
    model.addAttribute("meetupsEnabled", meetupsEnabled);
    if (!model.containsAttribute("showLadderSelection")) {
      model.addAttribute("showLadderSelection", Boolean.TRUE);
    }

    List<LadderMembership> myMemberships =
        membershipRepo.findByUserIdAndState(currentUser.getId(), LadderMembership.State.ACTIVE);
    CompetitionSessionService.CompetitionSessionSummary competitionSessions =
        competitionSessionService.summarizeSessions(myMemberships, currentUser.getId(), null);
    LadderSeason activeCompetitionSeason =
        competitionSeasonService != null
            ? competitionSeasonService.resolveActiveCompetitionSeason()
            : null;
    List<LadderMembership> privateGroupMemberships =
        homeSelectionService.selectorMemberships(myMemberships, null);
    Long selectedPrivateGroupId = UserSessionState.resolveSelectedGroupId(request, null);
    boolean showHomeIntro =
        currentUser.getId() != null
            && userOnboardingService.shouldShow(currentUser.getId(), UserOnboardingService.HOME_TOUR_V1);

    model.addAttribute("myMemberships", myMemberships);
    model.addAttribute("competitionSessionMemberships", competitionSessions.sessionMemberships());
    model.addAttribute("privateGroupMemberships", privateGroupMemberships);
    model.addAttribute("selectedPrivateGroupId", selectedPrivateGroupId);
    boolean showStartHereCallout =
        (myMemberships == null || myMemberships.isEmpty())
            && !membershipRepo.existsByUserId(currentUser.getId());
    model.addAttribute("showStartHereCallout", showStartHereCallout);
    model.addAttribute("showHomeIntro", showHomeIntro);
    model.addAttribute(
        "activeCompetitionSessionId",
        competitionSessions.selectedSessionMembership() != null
                && competitionSessions.selectedSessionMembership().getLadderConfig() != null
            ? competitionSessions.selectedSessionMembership().getLadderConfig().getId()
            : null);
    model.addAttribute(
        "activeCompetitionSessionTitle",
        competitionSessions.selectedSessionMembership() != null
                && competitionSessions.selectedSessionMembership().getLadderConfig() != null
            ? competitionSessions.selectedSessionMembership().getLadderConfig().getTitle()
            : null);
    model.addAttribute(
        "activeCompetitionSessionCount", competitionSessions.sessionMemberships().size());
    model.addAttribute("showCompetitionSessionChooser", competitionSessions.showSessionChooser());
    model.addAttribute(
        "canCreateCompetitionSession", !competitionSessions.hasOwnedCompetitionSession());
    model.addAttribute("competitionUnavailable", activeCompetitionSeason == null);
    clearSeasonSnapshot(homeState);

    String resolvedToast = null;
    if (toastMessage != null && !toastMessage.isBlank()) {
      resolvedToast = toastMessage;
    } else if (toastKey != null && !toastKey.isBlank()) {
      resolvedToast = resolveToastMessage(toastKey, matchId);
    }
    if (resolvedToast != null) {
      model.addAttribute("toastMessage", resolvedToast);
    }

    // App-style home is now the only supported home UI.
    return "auth/home";
  }

  @GetMapping("/competition")
  public String competition(
      @RequestParam(value = "toast", required = false) String toastKey,
      @RequestParam(value = "toastMessage", required = false) String toastMessage,
      @RequestParam(value = "matchId", required = false) Long matchId,
      @RequestParam(value = "standingsPage", required = false) Integer standingsPage,
      @RequestParam(value = "standingsSize", required = false) Integer standingsSize,
      @RequestParam(value = "view", required = false, defaultValue = "all") String view,
      @RequestParam(value = "findMe", required = false, defaultValue = "false") boolean findMe,
      Model model) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    model.addAttribute("userName", currentUser.getNickName());
    model.addAttribute("showLadderSelection", Boolean.FALSE);
    model.addAttribute("currentUserId", currentUser.getId());

    List<LadderMembership> myMemberships =
        membershipRepo.findByUserIdAndState(currentUser.getId(), LadderMembership.State.ACTIVE);
    CompetitionSessionService.CompetitionSessionSummary competitionSessions =
        competitionSessionService.summarizeSessions(myMemberships, currentUser.getId(), null);
    LadderMembership selectedSessionMembership = competitionSessions.selectedSessionMembership();
    LadderConfig selectedSession =
        selectedSessionMembership != null ? selectedSessionMembership.getLadderConfig() : null;

    LadderSeason season =
        competitionSeasonService != null
            ? competitionSeasonService.resolveActiveCompetitionSeason()
            : null;
    LadderConfig ladder = season != null ? season.getLadderConfig() : null;

    model.addAttribute("competitionUnavailable", season == null || ladder == null);
    model.addAttribute("ladder", ladder);
    model.addAttribute("season", season);
    model.addAttribute("competitionLadderId", ladder != null ? ladder.getId() : null);
    model.addAttribute(
        "ladderName", ladder != null ? homeSelectionService.ladderName(ladder) : "Competition");
    model.addAttribute("competitionSeasonId", season != null ? season.getId() : null);
    model.addAttribute("ladderId", null);
    model.addAttribute("seasonId", null);
    model.addAttribute("seasonName", season != null ? nullSafe(season.getName(), "") : "");
    model.addAttribute(
        "seasonDateRange", season != null ? homeSelectionService.dateRange(season) : "");
    model.addAttribute(
        "activeCompetitionSessionId", selectedSession != null ? selectedSession.getId() : null);
    model.addAttribute(
        "activeCompetitionSessionTitle",
        selectedSession != null ? selectedSession.getTitle() : null);
    Set<Long> playedAgainstUserIds = resolvePlayedAgainstUserIds(currentUser, season);
    model.addAttribute("playedAgainstUserIds", playedAgainstUserIds);
    model.addAttribute("hasPlayedAgainstOpponents", !playedAgainstUserIds.isEmpty());
    SeasonStandingsViewService.SeasonStandingsView standingsView =
        seasonStandingsViewService.load(season);
    var standings = standingsView.standings();
    model.addAttribute("standings", standings);
    String normalizedCompetitionView = normalizeCompetitionStandingsView(view);
    var filteredCompetitionRows = standingsView.rows();
    if ("played".equalsIgnoreCase(normalizedCompetitionView)) {
      Set<Long> visibleUserIds = new LinkedHashSet<>(playedAgainstUserIds);
      visibleUserIds.add(currentUser.getId());
      filteredCompetitionRows =
          standingsPageService.filterToVisibleUsers(standingsView.rows(), visibleUserIds);
    }
    int resolvedStandingsPageSize =
        standingsPageService.parsePageSize(
            standingsSize, standingsPageSizeDefault, MAX_STANDINGS_PAGE_SIZE);
    int resolvedStandingsPage = standingsPageService.parsePage(standingsPage);
    if (findMe) {
      resolvedStandingsPage =
          standingsPageService.resolvePageForCurrentUser(
              filteredCompetitionRows,
              currentUser.getId(),
              resolvedStandingsPageSize,
              resolvedStandingsPage);
    }
    StandingsPageService.StandingsPage competitionSlice =
        standingsPageService.paginateRows(
            filteredCompetitionRows,
            resolvedStandingsPage,
            resolvedStandingsPageSize,
            standingsPageSizeDefault,
            MAX_STANDINGS_PAGE_SIZE);
    standingsPageService.applyToModel(model, competitionSlice);
    model.addAttribute("competitionStandingsView", normalizedCompetitionView);
    model.addAttribute("competitionFindMeActive", findMe);
    model.addAttribute(
        "reportedCompetitionUserIds",
        competitionDisplayNameModerationService != null
            ? competitionDisplayNameModerationService.findReportedTargetUserIds(currentUser.getId())
            : Set.of());
    model.addAttribute("storyMode", storyModeService.buildPage(season, currentUser));

    String resolvedToast = null;
    if (toastMessage != null && !toastMessage.isBlank()) {
      resolvedToast = toastMessage;
    } else if (toastKey != null && !toastKey.isBlank()) {
      resolvedToast = resolveToastMessage(toastKey, matchId);
    }
    if (resolvedToast != null) {
      model.addAttribute("toastMessage", resolvedToast);
    }

    return "auth/competition";
  }

  @PostMapping("/competition/report-name")
  public String reportCompetitionDisplayName(
      @RequestParam("targetUserId") Long targetUserId, RedirectAttributes redirectAttributes) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }
    if (competitionDisplayNameModerationService == null) {
      redirectAttributes.addFlashAttribute("toastLevel", "warning");
      redirectAttributes.addFlashAttribute(
          "toastMessage", "Name reporting is unavailable right now.");
      return "redirect:/competition";
    }
    if (targetUserId == null || Objects.equals(targetUserId, currentUser.getId())) {
      redirectAttributes.addFlashAttribute("toastLevel", "warning");
      redirectAttributes.addFlashAttribute("toastMessage", "You can't report that name.");
      return "redirect:/competition";
    }

    LadderSeason season =
        competitionSeasonService != null
            ? competitionSeasonService.resolveActiveCompetitionSeason()
            : null;
    if (season == null) {
      redirectAttributes.addFlashAttribute("toastLevel", "warning");
      redirectAttributes.addFlashAttribute(
          "toastMessage", "Competition season is unavailable right now.");
      return "redirect:/competition";
    }

    User target =
        seasonStandingsViewService.findStandingUser(
            seasonStandingsViewService.load(season), targetUserId);
    if (target == null) {
      redirectAttributes.addFlashAttribute("toastLevel", "warning");
      redirectAttributes.addFlashAttribute(
          "toastMessage", "That player is not on the current competition table.");
      return "redirect:/competition";
    }

    CompetitionDisplayNameModerationService.ReportOutcome outcome;
    try {
      outcome = competitionDisplayNameModerationService.reportDisplayName(currentUser, target);
    } catch (IllegalArgumentException ex) {
      redirectAttributes.addFlashAttribute("toastLevel", "warning");
      redirectAttributes.addFlashAttribute("toastMessage", ex.getMessage());
      return "redirect:/competition";
    }

    switch (outcome) {
      case ALREADY_REPORTED -> {
        redirectAttributes.addFlashAttribute("toastLevel", "info");
        redirectAttributes.addFlashAttribute("toastMessage", "You already reported that name.");
      }
      case ALREADY_HIDDEN -> {
        redirectAttributes.addFlashAttribute("toastLevel", "info");
        redirectAttributes.addFlashAttribute(
            "toastMessage", "That name is already hidden on the competition table.");
      }
      case AUTO_HIDDEN -> {
        redirectAttributes.addFlashAttribute("toastLevel", "success");
        redirectAttributes.addFlashAttribute(
            "toastMessage", "Thanks. That name is now hidden on the competition table.");
      }
      default -> {
        redirectAttributes.addFlashAttribute("toastLevel", "success");
        redirectAttributes.addFlashAttribute("toastMessage", "Thanks. Your report was recorded.");
      }
    }
    return "redirect:/competition";
  }

  @GetMapping("/private-groups")
  public String privateGroups(
      @ModelAttribute("homeState") LadderPageState homeState,
      HttpServletRequest request,
      Model model) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    model.addAttribute("userName", currentUser.getNickName());
    model.addAttribute("meetupsEnabled", meetupsEnabled);

    List<LadderMembership> myMemberships =
        membershipRepo.findByUserIdAndState(currentUser.getId(), LadderMembership.State.ACTIVE);
    List<LadderMembership> privateGroupMemberships =
        homeSelectionService.selectorMemberships(myMemberships, null);
    Long selectedPrivateGroupId = UserSessionState.resolveSelectedGroupId(request, null);
    model.addAttribute("myMemberships", myMemberships);
    model.addAttribute("privateGroupMemberships", privateGroupMemberships);
    model.addAttribute("selectedPrivateGroupId", selectedPrivateGroupId);

    return "auth/private-group-picker";
  }

  @GetMapping("/private-groups/{ladderId}")
  public String privateGroupHome(
      @PathVariable("ladderId") Long ladderId,
      @RequestParam(value = "seasonId", required = false) Long seasonId,
      @ModelAttribute("homeState") LadderPageState homeState,
      HttpServletRequest request,
      Model model) {
    return renderSelectedPrivateGroupPage(
        "auth/private-groups", ladderId, seasonId, homeState, request, model);
  }

  @GetMapping("/competition/sessions")
  public String competitionSessions(Model model) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    model.addAttribute("userName", currentUser.getNickName());
    model.addAttribute("showLadderSelection", Boolean.TRUE);

    List<LadderMembership> myMemberships =
        membershipRepo.findByUserIdAndState(currentUser.getId(), LadderMembership.State.ACTIVE);
    CompetitionSessionService.CompetitionSessionSummary competitionSessions =
        competitionSessionService.summarizeSessions(myMemberships, currentUser.getId(), null);
    LadderMembership preferredSessionMembership = competitionSessions.selectedSessionMembership();
    LadderConfig preferredSession =
        preferredSessionMembership != null ? preferredSessionMembership.getLadderConfig() : null;
    LadderSeason activeCompetitionSeason =
        competitionSeasonService != null
            ? competitionSeasonService.resolveActiveCompetitionSeason()
            : null;

    model.addAttribute("myMemberships", myMemberships);
    model.addAttribute("sessionMemberships", competitionSessions.sessionMemberships());
    model.addAttribute(
        "activeCompetitionSessionId", preferredSession != null ? preferredSession.getId() : null);
    model.addAttribute(
        "activeCompetitionSessionTitle",
        preferredSession != null ? preferredSession.getTitle() : null);
    model.addAttribute(
        "activeCompetitionSessionCount", competitionSessions.sessionMemberships().size());
    model.addAttribute("showCompetitionSessionChooser", competitionSessions.showSessionChooser());
    model.addAttribute(
        "canCreateCompetitionSession", !competitionSessions.hasOwnedCompetitionSession());
    model.addAttribute("competitionUnavailable", activeCompetitionSeason == null);

    return "auth/competition-session-picker";
  }

  @GetMapping("/competition/log-match")
  public String competitionLogMatch(
      @RequestParam(value = "ladderId", required = false) Long ladderId,
      @RequestParam(value = "toast", required = false) String toastKey,
      @RequestParam(value = "toastMessage", required = false) String toastMessage,
      @RequestParam(value = "matchId", required = false) Long matchId,
      Model model) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    model.addAttribute("userName", currentUser.getNickName());
    model.addAttribute("showLadderSelection", Boolean.TRUE);
    model.addAttribute("competitionLogMode", Boolean.TRUE);
    model.addAttribute("selectorTitle", "Session");
    model.addAttribute("selectorSingleMessage", "You're only in one active session.");
    model.addAttribute(
        "selectorEmptyMessage",
        "You don't have any active match sessions right now. Start one to log a global league match.");

    List<LadderMembership> myMemberships =
        membershipRepo.findByUserIdAndState(currentUser.getId(), LadderMembership.State.ACTIVE);
    CompetitionSessionService.CompetitionSessionSummary competitionSessions =
        competitionSessionService.summarizeSessions(myMemberships, currentUser.getId(), ladderId);
    LadderMembership selectedSessionMembership = competitionSessions.selectedSessionMembership();
    LadderConfig selectedSession =
        selectedSessionMembership != null ? selectedSessionMembership.getLadderConfig() : null;
    LadderSeason activeCompetitionSeason =
        competitionSeasonService != null
            ? competitionSeasonService.resolveActiveCompetitionSeason()
            : null;
    LadderSeason targetSeason =
        competitionSessionService.resolveCompetitionTargetSeason(
            selectedSession, activeCompetitionSeason);

    model.addAttribute("myMemberships", myMemberships);
    model.addAttribute("selectorMemberships", competitionSessions.sessionMemberships());
    model.addAttribute(
        "selectedSessionTitle", selectedSession != null ? selectedSession.getTitle() : null);
    model.addAttribute("ladderId", selectedSession != null ? selectedSession.getId() : null);
    model.addAttribute("seasonId", targetSeason != null ? targetSeason.getId() : null);
    model.addAttribute(
        "returnToPath",
        ReturnToSanitizer.sanitize(
            selectedSession != null
                ? "/competition/log-match?ladderId=" + selectedSession.getId()
                : "/competition/log-match"));
    model.addAttribute("navHomePath", "/home");
    model.addAttribute(
        "seasonName", targetSeason != null ? nullSafe(targetSeason.getName(), "") : "");
    model.addAttribute(
        "seasonDateRange",
        targetSeason != null ? homeSelectionService.dateRange(targetSeason) : "");
    model.addAttribute("competitionUnavailable", activeCompetitionSeason == null);
    model.addAttribute("activeCompetitionSeason", activeCompetitionSeason);

    String resolvedToast = null;
    if (toastMessage != null && !toastMessage.isBlank()) {
      resolvedToast = toastMessage;
    } else if (toastKey != null && !toastKey.isBlank()) {
      resolvedToast = resolveToastMessage(toastKey, matchId);
    }
    if (resolvedToast != null) {
      model.addAttribute("toastMessage", resolvedToast);
    }

    return "auth/competition-log-match";
  }

  @GetMapping("/confirm-matches")
  public String confirmMatches(
      @RequestParam(value = "toast", required = false) String toastKey,
      @RequestParam(value = "toastMessage", required = false) String toastMessage,
      @RequestParam(value = "matchId", required = false) Long matchId,
      Model model) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    model.addAttribute("userName", currentUser.getNickName());
    model.addAttribute("returnToPath", "/confirm-matches");

    matchDashboardViewService.applyToModel(model, buildDashboardModel(currentUser, null));

    String resolvedToast = null;
    if (toastMessage != null && !toastMessage.isBlank()) {
      resolvedToast = toastMessage;
    } else if (toastKey != null && !toastKey.isBlank()) {
      resolvedToast = resolveToastMessage(toastKey, matchId);
    }
    if (resolvedToast != null) {
      model.addAttribute("toastMessage", resolvedToast);
    }
    return "auth/confirm-matches";
  }

  @GetMapping("/play-plans")
  public String playPlans(
      @RequestParam(value = "ladderId", required = false) Long ladderId,
      @RequestParam(value = "seasonId", required = false) Long seasonId,
      @ModelAttribute("homeState") LadderPageState homeState,
      HttpServletRequest request,
      Model model) {
    return renderSelectedGroupSummaryPage(
        "auth/play-plans", "/play-plans", ladderId, seasonId, homeState, request, model);
  }

  @GetMapping("/match-log")
  public String logMatchHub(
      @RequestParam(value = "ladderId", required = false) Long ladderId,
      @RequestParam(value = "seasonId", required = false) Long seasonId,
      @ModelAttribute("homeState") LadderPageState homeState,
      HttpServletRequest request,
      Model model) {
    return renderSelectedGroupLadderV2Page(
        "auth/log-match-app",
        "/match-log",
        ladderId,
        seasonId,
        homeState,
        request,
        model,
        true,
        false,
        false);
  }

  @GetMapping("/standings")
  public String standings(
      @RequestParam(value = "ladderId", required = false) Long ladderId,
      @RequestParam(value = "seasonId", required = false) Long seasonId,
      @ModelAttribute("homeState") LadderPageState homeState,
      HttpServletRequest request,
      Model model) {
    return renderSelectedGroupLadderV2Page(
        "auth/standings",
        "/standings",
        ladderId,
        seasonId,
        homeState,
        request,
        model,
        false,
        true,
        true);
  }

  @GetMapping("/groups")
  public String groups(
      @RequestParam(value = "ladderId", required = false) Long ladderId,
      @RequestParam(value = "seasonId", required = false) Long seasonId,
      @RequestParam(value = "returnTo", required = false) String returnTo,
      @ModelAttribute("homeState") LadderPageState homeState,
      HttpServletRequest request,
      Model model) {
    String normalizedReturnTo = normalizeGroupHubReturnTo(returnTo);
    model.addAttribute("groupHubReturnTo", normalizedReturnTo);
    if (normalizedReturnTo != null) {
      User currentUser = resolveCurrentUser();
      if (currentUser == null) {
        return "redirect:/login";
      }
      model.addAttribute("showLadderSelection", Boolean.FALSE);
      String result =
          populateSelectedGroupManageModel(
              currentUser, ladderId, seasonId, homeState, request, model);
      if (result != null && result.startsWith("redirect:")) {
        return result;
      }
      return "auth/manage-groups";
    }
    return renderSelectedGroupManagePage(
        "auth/manage-groups", "/groups", ladderId, seasonId, homeState, request, model);
  }

  @GetMapping("/help")
  public String help(Model model) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    model.addAttribute("userName", currentUser.getNickName());
    return "auth/help-app";
  }

  @PostMapping("/home/intro-complete")
  @ResponseBody
  public ResponseEntity<Void> completeHomeIntro() {
    User currentUser = resolveCurrentUser();
    if (currentUser == null || currentUser.getId() == null) {
      return ResponseEntity.status(401).build();
    }

    userOnboardingService.markCompleted(currentUser.getId(), UserOnboardingService.HOME_TOUR_V1);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/account-menu")
  public String accountMenu(Model model) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    model.addAttribute("userName", currentUser.getNickName());
    model.addAttribute("showLadderSelection", Boolean.FALSE);
    return "auth/account-menu";
  }

  private String renderSelectedGroupSummaryPage(
      String viewName,
      String navPath,
      Long ladderId,
      Long seasonId,
      LadderPageState homeState,
      HttpServletRequest request,
      Model model) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    model.addAttribute("ladderNavPath", navPath);
    model.addAttribute("showLadderSelection", Boolean.FALSE);

    String result =
        populateSelectedGroupSummaryModel(
            currentUser, ladderId, seasonId, homeState, request, model);
    if (result != null && result.startsWith("redirect:")) {
      return result;
    }
    String canonicalRedirect = buildCanonicalAppRedirect(navPath, ladderId, seasonId, model);
    if (canonicalRedirect != null) {
      return canonicalRedirect;
    }
    return viewName;
  }

  private String renderSelectedGroupManagePage(
      String viewName,
      String navPath,
      Long ladderId,
      Long seasonId,
      LadderPageState homeState,
      HttpServletRequest request,
      Model model) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    model.addAttribute("ladderNavPath", navPath);
    model.addAttribute("showLadderSelection", Boolean.FALSE);

    String result =
        populateSelectedGroupManageModel(
            currentUser, ladderId, seasonId, homeState, request, model);
    if (result != null && result.startsWith("redirect:")) {
      return result;
    }
    String canonicalRedirect = buildCanonicalAppRedirect(navPath, ladderId, seasonId, model);
    if (canonicalRedirect != null) {
      return canonicalRedirect;
    }
    return viewName;
  }

  private String renderSelectedGroupLadderV2Page(
      String viewName,
      String navPath,
      Long ladderId,
      Long seasonId,
      LadderPageState homeState,
      HttpServletRequest request,
      Model model,
      boolean includeDashboard,
      boolean includeStandings,
      boolean includeImprovementAdvice) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    model.addAttribute("ladderNavPath", navPath);
    model.addAttribute("showLadderSelection", Boolean.FALSE);

    String result =
        populateSelectedGroupLadderV2Model(
            currentUser,
            ladderId,
            seasonId,
            homeState,
            request,
            model,
            includeDashboard,
            includeStandings,
            includeImprovementAdvice);
    if (result != null && result.startsWith("redirect:")) {
      return result;
    }
    String canonicalRedirect = buildCanonicalAppRedirect(navPath, ladderId, seasonId, model);
    if (canonicalRedirect != null) {
      return canonicalRedirect;
    }
    return viewName;
  }

  private String renderSelectedPrivateGroupPage(
      String viewName,
      Long requestedLadderId,
      Long requestedSeasonId,
      LadderPageState homeState,
      HttpServletRequest request,
      Model model) {
    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    model.addAttribute("showLadderSelection", Boolean.FALSE);

    String result =
        populateSelectedGroupSummaryModel(
            currentUser, requestedLadderId, requestedSeasonId, homeState, request, model);
    if (result != null && result.startsWith("redirect:")) {
      return result;
    }

    Long effectiveLadderId = asLong(model.asMap().get("ladderId"));
    Long effectiveSeasonId = asLong(model.asMap().get("seasonId"));
    if (effectiveLadderId == null || !Objects.equals(effectiveLadderId, requestedLadderId)) {
      return "redirect:/private-groups";
    }

    if (requestedSeasonId != null && effectiveSeasonId == null) {
      return "redirect:/private-groups/" + effectiveLadderId;
    }
    if (!Objects.equals(requestedSeasonId, effectiveSeasonId) && effectiveSeasonId != null) {
      return "redirect:/private-groups/" + effectiveLadderId + "?seasonId=" + effectiveSeasonId;
    }

    return viewName;
  }

  private String buildCanonicalAppRedirect(
      String navPath, Long requestedLadderId, Long requestedSeasonId, Model model) {
    if (navPath == null || model == null) {
      return null;
    }

    Object ladderValue = model.asMap().get("ladderId");
    Object seasonValue = model.asMap().get("seasonId");
    Long effectiveLadderId = ladderValue instanceof Long ? (Long) ladderValue : null;
    Long effectiveSeasonId = seasonValue instanceof Long ? (Long) seasonValue : null;

    if (Objects.equals(requestedLadderId, effectiveLadderId)
        && Objects.equals(requestedSeasonId, effectiveSeasonId)) {
      return null;
    }
    if (effectiveLadderId == null && effectiveSeasonId == null) {
      return null;
    }

    StringBuilder redirect = new StringBuilder("redirect:").append(navPath);
    boolean first = true;
    if (effectiveLadderId != null) {
      redirect.append(first ? '?' : '&').append("ladderId=").append(effectiveLadderId);
      first = false;
    }
    if (effectiveSeasonId != null) {
      redirect.append(first ? '?' : '&').append("seasonId=").append(effectiveSeasonId);
    }
    return redirect.toString();
  }

  private Long asLong(Object value) {
    return value instanceof Long ? (Long) value : null;
  }

  private String populateSelectedGroupSummaryModel(
      User currentUser,
      Long requestedLadderId,
      Long requestedSeasonId,
      LadderPageState homeState,
      HttpServletRequest request,
      Model model) {
    if (homeSelectionService.isCompetitionSelection(requestedLadderId, requestedSeasonId)) {
      return "redirect:/competition";
    }

    SelectedGroupContext context =
        resolveSelectedGroupContext(
            currentUser, requestedLadderId, requestedSeasonId, homeState, request);
    applySelectedGroupSummaryModel(model, currentUser, context);
    return null;
  }

  private String populateSelectedGroupManageModel(
      User currentUser,
      Long requestedLadderId,
      Long requestedSeasonId,
      LadderPageState homeState,
      HttpServletRequest request,
      Model model) {
    if (homeSelectionService.isCompetitionSelection(requestedLadderId, requestedSeasonId)) {
      return "redirect:/competition";
    }

    SelectedGroupContext context =
        resolveSelectedGroupContext(
            currentUser, requestedLadderId, requestedSeasonId, homeState, request);
    applySelectedGroupSummaryModel(model, currentUser, context);
    model.addAttribute("myMemberships", context.myMemberships());
    model.addAttribute(
        "selectorMemberships",
        homeSelectionService.selectorMemberships(
            context.myMemberships(), context.effectiveState().ladderId));
    model.addAttribute(
        "restorableLadders",
        ladderConfigRepo.findByOwnerUserIdAndPendingDeletionIsTrue(currentUser.getId()));
    return null;
  }

  private String populateSelectedGroupLadderV2Model(
      User currentUser,
      Long requestedLadderId,
      Long requestedSeasonId,
      LadderPageState homeState,
      HttpServletRequest request,
      Model model,
      boolean includeDashboard,
      boolean includeStandings,
      boolean includeImprovementAdvice) {
    if (homeSelectionService.isCompetitionSelection(requestedLadderId, requestedSeasonId)) {
      return "redirect:/competition";
    }

    SelectedGroupContext context =
        resolveSelectedGroupContext(
            currentUser, requestedLadderId, requestedSeasonId, homeState, request);
    applySelectedGroupSummaryModel(model, currentUser, context);
    applySelectedGroupLadderNavigationModel(model, context);
    if (includeDashboard) {
      applySelectedGroupDashboardModel(model, currentUser, context);
    }

    LadderSeason season = context.season();
    boolean seasonActive = (season != null) && season.getState() == LadderSeason.State.ACTIVE;
    model.addAttribute("seasonActive", seasonActive);

    if (includeStandings) {
      applySelectedGroupStandingsModel(
          model, currentUser, context, request, includeImprovementAdvice);
    }
    return null;
  }

  private SelectedGroupContext resolveSelectedGroupContext(
      User currentUser,
      Long requestedLadderId,
      Long requestedSeasonId,
      LadderPageState homeState,
      HttpServletRequest request) {
    final boolean haveExplicitParams = requestedLadderId != null || requestedSeasonId != null;
    final Long sessionLadderId =
        haveExplicitParams ? null : UserSessionState.resolveSelectedGroupId(request, null);

    List<LadderMembership> myMemberships =
        membershipRepo.findByUserIdAndState(currentUser.getId(), LadderMembership.State.ACTIVE);
    HomeSelectionService.HomeSelection selection =
        haveExplicitParams
            ? homeSelectionService.resolveSelection(
                currentUser, requestedLadderId, requestedSeasonId, myMemberships)
            : homeSelectionService.resolveSelection(
                currentUser, sessionLadderId, null, myMemberships);

    LadderPageState effective = selection.state();
    copyInto(homeState, effective);
    UserSessionState.storeSelectedGroupId(request, effective.ladderId);
    return new SelectedGroupContext(
        myMemberships, effective, selection.ladder(), selection.season());
  }

  private void applySelectedGroupSummaryModel(
      Model model, User currentUser, SelectedGroupContext context) {
    model.addAttribute("userName", currentUser.getNickName());
    model.addAttribute("meetupsEnabled", meetupsEnabled);
    if (!model.containsAttribute("showLadderSelection")) {
      model.addAttribute("showLadderSelection", Boolean.TRUE);
    }

    LadderPageState effective = context.effectiveState();
    LadderConfig ladder = context.ladder();
    LadderSeason season = context.season();
    model.addAttribute("ladder", ladder);
    model.addAttribute("season", season);
    model.addAttribute("ladderId", effective.ladderId);
    model.addAttribute(
        "ladderName",
        effective.ladderName != null
            ? effective.ladderName
            : homeSelectionService.ladderName(ladder));
    model.addAttribute("seasonId", effective.seasonId);
    model.addAttribute("seasonName", effective.seasonName != null ? effective.seasonName : "");
    model.addAttribute(
        "seasonDateRange", effective.seasonDateRange != null ? effective.seasonDateRange : "");
  }

  private void applySelectedGroupLadderNavigationModel(Model model, SelectedGroupContext context) {
    LadderPageState effective = context.effectiveState();
    model.addAttribute("prevSeasonId", effective.prevSeasonId);
    model.addAttribute("nextSeasonId", effective.nextSeasonId);
    model.addAttribute("prevLadderId", effective.prevLadderId);
    model.addAttribute("nextLadderId", effective.nextLadderId);
  }

  private void applySelectedGroupDashboardModel(
      Model model, User currentUser, SelectedGroupContext context) {
    LadderPageState effective = context.effectiveState();
    matchDashboardViewService.applyToModel(
        model, buildDashboardModel(currentUser, context.season()));

    @SuppressWarnings("unchecked")
    java.util.List<LadderMatchLink> dashboardLinks =
        (java.util.List<LadderMatchLink>) model.asMap().getOrDefault("links", java.util.List.of());
    List<Long> matchEntryUserIds =
        effective.ladderId != null
            ? membershipRepo
                .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                    effective.ladderId, LadderMembership.State.ACTIVE)
                .stream()
                .map(LadderMembership::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList()
            : List.of();
    if (matchEntryUserIds.isEmpty()) {
      matchEntryUserIds =
          matchEntryContextService.extractParticipantUserIdsFromLinks(dashboardLinks);
    }
    Map<Long, String> courtNames =
        matchEntryContextService.buildCourtNameByUserIds(matchEntryUserIds, effective.ladderId);
    String voiceLanguage = matchEntryContextService.determineVoiceLanguage(currentUser);
    List<String> voiceHints =
        matchEntryContextService.buildVoicePhraseHints(currentUser, courtNames.values());
    model.addAttribute("voiceLanguage", voiceLanguage);
    model.addAttribute("voicePhraseHints", voiceHints);
    model.addAttribute("voiceMaxAlternatives", Integer.valueOf(3));
  }

  private void applySelectedGroupStandingsModel(
      Model model,
      User currentUser,
      SelectedGroupContext context,
      HttpServletRequest request,
      boolean includeImprovementAdvice) {
    LadderSeason season = context.season();
    SeasonStandingsViewService.SeasonStandingsView standingsView =
        seasonStandingsViewService.load(season);
    var standings = standingsView.standings();
    model.addAttribute("standings", standings);
    StandingsPageService.StandingsPage standingsPage =
        standingsPageService.paginateRows(
            standingsView.rows(),
            standingsPageService.parsePage(request.getParameter("standingsPage")),
            standingsPageService.parsePageSize(
                request.getParameter("standingsSize"),
                standingsPageSizeDefault,
                MAX_STANDINGS_PAGE_SIZE),
            standingsPageSizeDefault,
            MAX_STANDINGS_PAGE_SIZE);
    standingsPageService.applyToModel(model, standingsPage);
    model.addAttribute(
        "standingsRecalculationPending", season != null && season.isStandingsRecalcInProgress());
    model.addAttribute("storyMode", storyModeService.buildPage(season, currentUser));
    if (includeImprovementAdvice) {
      Advice improvementAdvice =
          improvementAdvisor.buildAdvice(currentUser, context.ladder(), season);
      model.addAttribute("improvementAdvice", improvementAdvice);
    }
  }

  private String buildPrivateGroupRedirect(
      Long ladderId, Long seasonId, String toastKey, String toastMessage, Long matchId) {
    if (ladderId == null) {
      return "redirect:/private-groups";
    }
    UriComponentsBuilder redirect = UriComponentsBuilder.fromPath("/private-groups/{ladderId}");
    if (seasonId != null) {
      redirect.queryParam("seasonId", seasonId);
    }
    if (toastKey != null && !toastKey.isBlank()) {
      redirect.queryParam("toast", toastKey);
    }
    if (toastMessage != null && !toastMessage.isBlank()) {
      redirect.queryParam("toastMessage", toastMessage);
    }
    if (matchId != null) {
      redirect.queryParam("matchId", matchId);
    }
    return "redirect:" + redirect.buildAndExpand(ladderId).encode().toUriString();
  }

  private String normalizeGroupHubReturnTo(String returnTo) {
    if ("/private-groups".equals(returnTo)) {
      return "/private-groups";
    }
    return null;
  }

  private String normalizeCompetitionStandingsView(String view) {
    if ("played".equalsIgnoreCase(view)) {
      return "played";
    }
    return "all";
  }

  private MatchDashboardService.DashboardModel buildDashboardModel(
      User currentUser, LadderSeason season) {
    if (currentUser == null || matchDashboardService == null) {
      return matchDashboardViewService.emptyDashboard();
    }
    return matchDashboardService.buildPendingForUserInSeason(currentUser, season);
  }

  private record SelectedGroupContext(
      List<LadderMembership> myMemberships,
      LadderPageState effectiveState,
      LadderConfig ladder,
      LadderSeason season) {}

  /**
   * Copy fields into the session object without swapping the reference (Spring expects same
   * instance).
   */
  private void copyInto(LadderPageState target, LadderPageState src) {
    target.ladderId = src.ladderId;
    target.ladderName = src.ladderName;
    target.seasonId = null;
    target.seasonName = null;
    target.seasonDateRange = null;
    target.prevLadderId = src.prevLadderId;
    target.nextLadderId = src.nextLadderId;
    target.prevSeasonId = null;
    target.nextSeasonId = null;
  }

  private void clearSeasonSnapshot(LadderPageState target) {
    target.seasonId = null;
    target.seasonName = null;
    target.seasonDateRange = null;
    target.prevSeasonId = null;
    target.nextSeasonId = null;
  }

  private String resolveToastMessage(String key, Long matchId) {
    switch (key) {
      case "matchLogged":
        if (matchId != null) {
          return "Match #" + matchId + " logged!";
        }
        return "Match logged!";
      case "matchLoggedDetailed":
        return "Match logged and standings updated.";
      case "matchUpdated":
        return "Match updated successfully!";
      case "forbidden":
        return "You don't have permission to do that.";
      default:
        return null;
    }
  }

  private Set<Long> resolvePlayedAgainstUserIds(User currentUser, LadderSeason season) {
    if (currentUser == null || currentUser.getId() == null || season == null) {
      return Set.of();
    }
    List<Match> matches = matchRepo.findBySeasonOrderByPlayedAtDescWithUsers(season);
    if (matches == null || matches.isEmpty()) {
      return Set.of();
    }
    Set<Long> opponentIds = new LinkedHashSet<>();
    for (Match match : matches) {
      if (match == null) {
        continue;
      }
      boolean onTeamA =
          sameUser(match.getA1(), currentUser) || sameUser(match.getA2(), currentUser);
      boolean onTeamB =
          sameUser(match.getB1(), currentUser) || sameUser(match.getB2(), currentUser);
      if (onTeamA) {
        addOpponentUserId(opponentIds, match.getB1());
        addOpponentUserId(opponentIds, match.getB2());
      }
      if (onTeamB) {
        addOpponentUserId(opponentIds, match.getA1());
        addOpponentUserId(opponentIds, match.getA2());
      }
    }
    return opponentIds;
  }

  private void addOpponentUserId(Set<Long> opponentIds, User opponent) {
    if (opponentIds == null || opponent == null || opponent.getId() == null) {
      return;
    }
    opponentIds.add(opponent.getId());
  }

  private boolean sameUser(User left, User right) {
    if (left == null || right == null || left.getId() == null || right.getId() == null) {
      return false;
    }
    return Objects.equals(left.getId(), right.getId());
  }

  private String dateRange(LadderSeason season) {
    if (season == null || season.getStartDate() == null) return "";
    LocalDate start = season.getStartDate();
    LocalDate end = season.getEndDate();
    var formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy");
    boolean placeholder = end != null && end.isAfter(start.plusYears(80));
    String startText = start.format(formatter);
    String endText;
    if (placeholder) {
      endText = "Present";
    } else if (end != null) {
      endText = end.format(formatter);
    } else {
      endText = "Present";
    }
    return startText + " – " + endText;
  }

  private String nullSafe(String s, String def) {
    return (s == null ? def : s);
  }

  private Instant matchTimeline(LadderMatchLink link) {
    if (link == null) {
      return Instant.EPOCH;
    }
    Match match = link.getMatch();
    if (match == null) {
      return Instant.EPOCH;
    }
    if (match.getPlayedAt() != null) {
      return match.getPlayedAt();
    }
    if (match.getCreatedAt() != null) {
      return match.getCreatedAt();
    }
    return Instant.EPOCH;
  }

  private User resolveCurrentUser() {
    return AuthenticatedUserSupport.currentUser();
  }
}
