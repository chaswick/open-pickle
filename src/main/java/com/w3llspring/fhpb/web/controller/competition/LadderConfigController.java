package com.w3llspring.fhpb.web.controller.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserDisplayNameAuditRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMatchLink;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserDisplayNameAudit;
import com.w3llspring.fhpb.web.service.CompetitionDisplayNameModerationService;
import com.w3llspring.fhpb.web.service.CompetitionSeasonService;
import com.w3llspring.fhpb.web.service.InviteChangeCooldownException;
import com.w3llspring.fhpb.web.service.LadderConfigService;
import com.w3llspring.fhpb.web.service.LadderImprovementAdvisor;
import com.w3llspring.fhpb.web.service.MatchDashboardService;
import com.w3llspring.fhpb.web.service.MatchRowModel;
import com.w3llspring.fhpb.web.service.SeasonCarryOverService;
import com.w3llspring.fhpb.web.service.SeasonTransitionService;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationOperations;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationService;
import com.w3llspring.fhpb.web.service.competition.GroupCreationService;
import com.w3llspring.fhpb.web.service.dashboard.MatchDashboardViewService;
import com.w3llspring.fhpb.web.service.matchentry.MatchEntryContextService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.service.standings.SeasonStandingsViewService;
import com.w3llspring.fhpb.web.service.competition.SessionJoinRequestService;
import com.w3llspring.fhpb.web.service.user.UserOnboardingService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import com.w3llspring.fhpb.web.util.ReturnToSanitizer;
import com.w3llspring.fhpb.web.util.SessionInviteCodeSupport;
import com.w3llspring.fhpb.web.util.UserPublicName;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/groups")
public class LadderConfigController {
  private static final Pattern LEGACY_AUTO_SESSION_TITLE =
      Pattern.compile("^(.+ Session) - [A-Z][a-z]{2} \\d{1,2}, \\d{1,2}:\\d{2} [AP]M$");
  private static final String SESSION_TOUR_OWNER = "owner";
  private static final String SESSION_TOUR_JOINER = "joiner";
  private static final int SESSION_RECENT_TICKER_LIMIT = 3;

  private final GroupAdministrationOperations groupAdministration;
  private final GroupCreationService groupCreationService;
  private final LadderConfigRepository configs;
  private final LadderSeasonRepository seasons;
  private final MatchRepository matchRepo;
  private final UserRepository userRepo;
  private final UserDisplayNameAuditRepository userDisplayNameAuditRepository;
  private final LadderMembershipRepository membershipRepo;
  private final StoryModeService storyModeService;
  private final int defaultMaxMembers;

  @Value("${fhpb.bootstrap.admin.email:}")
  private String siteWideAdminEmail = "";

  @Value("${fhpb.sessions.invite-active-seconds:1800}")
  private long sessionInviteActiveSeconds = 1800L;

  @Value("${fhpb.public.base-url:}")
  private String publicBaseUrl = "";

  private CompetitionSeasonService competitionSeasonService;
  private SeasonTransitionService transitionSvc;
  private MatchDashboardService matchDashboardService;
  private MatchDashboardViewService matchDashboardViewService;
  private LadderImprovementAdvisor improvementAdvisor;
  private MatchEntryContextService matchEntryContextService;
  private RoundRobinService roundRobinService;
  private SeasonStandingsViewService seasonStandingsViewService;
  private CompetitionDisplayNameModerationService competitionDisplayNameModerationService;
  private final SessionJoinRequestService sessionJoinRequestService;
  @Autowired private UserOnboardingService userOnboardingService;
  @Autowired private MatchConfirmationRepository matchConfirmationRepository;

  @Autowired
  public LadderConfigController(
      MatchRepository matchRepo,
      UserRepository userRepo,
      UserDisplayNameAuditRepository userDisplayNameAuditRepository,
      GroupAdministrationService groupAdministration,
      GroupCreationService groupCreationService,
      LadderConfigRepository configs,
      LadderSeasonRepository seasons,
      LadderMembershipRepository membershipRepo,
      StoryModeService storyModeService,
      CompetitionSeasonService competitionSeasonService,
      SeasonTransitionService transitionSvc,
      MatchDashboardService matchDashboardService,
      MatchDashboardViewService matchDashboardViewService,
      LadderImprovementAdvisor improvementAdvisor,
      MatchEntryContextService matchEntryContextService,
      RoundRobinService roundRobinService,
      SeasonStandingsViewService seasonStandingsViewService,
      CompetitionDisplayNameModerationService competitionDisplayNameModerationService,
      SessionJoinRequestService sessionJoinRequestService,
      @Value("${fhpb.ladder.max-members:20}") int defaultMaxMembers) {
    this(
        matchRepo,
        userRepo,
        userDisplayNameAuditRepository,
        (GroupAdministrationOperations) groupAdministration,
        groupCreationService,
        configs,
        seasons,
        membershipRepo,
        storyModeService,
        defaultMaxMembers,
        competitionSeasonService,
        transitionSvc,
        matchDashboardService,
        matchDashboardViewService,
        improvementAdvisor,
        matchEntryContextService,
        roundRobinService,
        seasonStandingsViewService,
        competitionDisplayNameModerationService,
        sessionJoinRequestService);
  }

  public LadderConfigController(
      UserRepository userRepo,
      UserDisplayNameAuditRepository userDisplayNameAuditRepository,
      GroupAdministrationService groupAdministration,
      GroupCreationService groupCreationService,
      LadderConfigRepository configs,
      LadderSeasonRepository seasons,
      LadderMembershipRepository membershipRepo,
      StoryModeService storyModeService,
      CompetitionSeasonService competitionSeasonService,
      SeasonTransitionService transitionSvc,
      MatchDashboardService matchDashboardService,
      MatchDashboardViewService matchDashboardViewService,
      LadderImprovementAdvisor improvementAdvisor,
      MatchEntryContextService matchEntryContextService,
      RoundRobinService roundRobinService,
      SeasonStandingsViewService seasonStandingsViewService,
      CompetitionDisplayNameModerationService competitionDisplayNameModerationService,
      SessionJoinRequestService sessionJoinRequestService,
      @Value("${fhpb.ladder.max-members:20}") int defaultMaxMembers) {
    this(
        null,
        userRepo,
        userDisplayNameAuditRepository,
        groupAdministration,
        groupCreationService,
        configs,
        seasons,
        membershipRepo,
        storyModeService,
        defaultMaxMembers,
        competitionSeasonService,
        transitionSvc,
        matchDashboardService,
        matchDashboardViewService,
        improvementAdvisor,
        matchEntryContextService,
        roundRobinService,
        seasonStandingsViewService,
        competitionDisplayNameModerationService,
        sessionJoinRequestService);
  }

  public LadderConfigController(
      UserRepository userRepo,
      UserDisplayNameAuditRepository userDisplayNameAuditRepository,
      LadderConfigService service,
      GroupAdministrationService groupAdministration,
      GroupCreationService groupCreationService,
      LadderConfigRepository configs,
      LadderSeasonRepository seasons,
      LadderMembershipRepository membershipRepo,
      StoryModeService storyModeService,
      @Value("${fhpb.ladder.max-members:20}") int defaultMaxMembers) {
    this(
        null,
        userRepo,
        userDisplayNameAuditRepository,
        (GroupAdministrationOperations) groupAdministration,
        groupCreationService,
        configs,
        seasons,
        membershipRepo,
        storyModeService,
        defaultMaxMembers,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public LadderConfigController(
      UserRepository userRepo,
      UserDisplayNameAuditRepository userDisplayNameAuditRepository,
      LadderConfigService service,
      GroupAdministrationOperations groupAdministration,
      LadderConfigRepository configs,
      LadderSeasonRepository seasons,
      LadderMembershipRepository membershipRepo,
      StoryModeService storyModeService,
      @Value("${fhpb.ladder.max-members:20}") int defaultMaxMembers) {
    this(
        null,
        userRepo,
        userDisplayNameAuditRepository,
        groupAdministration,
        new GroupCreationService(service, configs, seasons, storyModeService),
        configs,
        seasons,
        membershipRepo,
        storyModeService,
        defaultMaxMembers,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public LadderConfigController(
      UserRepository userRepo,
      UserDisplayNameAuditRepository userDisplayNameAuditRepository,
      LadderConfigService service,
      GroupAdministrationOperations groupAdministration,
      LadderConfigRepository configs,
      LadderSeasonRepository seasons,
      LadderMembershipRepository membershipRepo,
      SeasonTransitionService transitionSvc,
      SeasonCarryOverService seasonCarryOverService,
      RoundRobinService roundRobinService,
      StoryModeService storyModeService,
      @Value("${fhpb.ladder.max-members:20}") int defaultMaxMembers) {
    this(
        null,
        userRepo,
        userDisplayNameAuditRepository,
        groupAdministration,
        new GroupCreationService(service, configs, seasons, storyModeService),
        configs,
        seasons,
        membershipRepo,
        storyModeService,
        defaultMaxMembers,
        null,
        transitionSvc,
        null,
        null,
        null,
        null,
        roundRobinService,
        null,
        null,
        null);
  }

  private LadderConfigController(
      MatchRepository matchRepo,
      UserRepository userRepo,
      UserDisplayNameAuditRepository userDisplayNameAuditRepository,
      GroupAdministrationOperations groupAdministration,
      GroupCreationService groupCreationService,
      LadderConfigRepository configs,
      LadderSeasonRepository seasons,
      LadderMembershipRepository membershipRepo,
      StoryModeService storyModeService,
      int defaultMaxMembers,
      CompetitionSeasonService competitionSeasonService,
      SeasonTransitionService transitionSvc,
      MatchDashboardService matchDashboardService,
      MatchDashboardViewService matchDashboardViewService,
      LadderImprovementAdvisor improvementAdvisor,
      MatchEntryContextService matchEntryContextService,
      RoundRobinService roundRobinService,
      SeasonStandingsViewService seasonStandingsViewService,
      CompetitionDisplayNameModerationService competitionDisplayNameModerationService,
      SessionJoinRequestService sessionJoinRequestService) {
    this.matchRepo = matchRepo;
    this.groupAdministration = groupAdministration;
    this.groupCreationService = groupCreationService;
    this.configs = configs;
    this.seasons = seasons;
    this.userRepo = userRepo;
    this.userDisplayNameAuditRepository = userDisplayNameAuditRepository;
    this.membershipRepo = membershipRepo;
    this.storyModeService = storyModeService;
    this.defaultMaxMembers = defaultMaxMembers;
    this.competitionSeasonService = competitionSeasonService;
    this.transitionSvc = transitionSvc;
    this.matchDashboardService = matchDashboardService;
    this.matchDashboardViewService = matchDashboardViewService;
    this.improvementAdvisor = improvementAdvisor;
    this.matchEntryContextService = matchEntryContextService;
    this.roundRobinService = roundRobinService;
    this.seasonStandingsViewService = seasonStandingsViewService;
    this.competitionDisplayNameModerationService = competitionDisplayNameModerationService;
    this.sessionJoinRequestService = sessionJoinRequestService;
  }

  @GetMapping("/new")
  public String newForm(
      Model model,
      Authentication auth,
      @RequestParam(name = "returnTo", required = false) String returnTo,
      @RequestParam(name = "type", required = false) LadderConfig.Type type,
      @RequestParam(name = "tournamentMode", required = false, defaultValue = "false")
          boolean tournamentMode) {
    User currentUser = getCurrentUser(auth);
    String navName = currentUser != null ? currentUser.getNickName() : null;
    LadderConfig.Type requestedType =
        type == LadderConfig.Type.SESSION ? LadderConfig.Type.SESSION : LadderConfig.Type.STANDARD;
    boolean tournamentModePreset = requestedType != LadderConfig.Type.SESSION && tournamentMode;
    model.addAttribute("userName", navName);
    model.addAttribute("returnToPath", normalizeGroupReturnTo(returnTo));
    model.addAttribute("defaultSeasonStart", LocalDate.now(ZoneOffset.UTC));
    model.addAttribute("recurrenceOptions", new String[] {"ONE_OFF", "RECURRING_6W"});
    model.addAttribute("maxRollingEveryCount", LadderConfig.MAX_ROLLING_EVERY_COUNT);
    model.addAttribute("groupTitleMaxLength", LadderConfig.MAX_TITLE_LENGTH);
    model.addAttribute("storyModeFeatureEnabled", storyModeFeatureEnabled());
    model.addAttribute("selectedLadderType", requestedType);
    model.addAttribute("tournamentModePreset", tournamentModePreset);
    model.addAttribute(
        "competitionSeasonAvailable",
        competitionSeasonService != null
            && competitionSeasonService.resolveActiveCompetitionSeason() != null);

    return "auth/createLadderConfig";
  }

  public String newForm(Model model, Authentication auth) {
    return newForm(model, auth, null, null, false);
  }

  @PostMapping("/start-session")
  public String startSession(
      Authentication auth,
      @RequestParam(name = "returnTo", required = false) String returnTo,
      RedirectAttributes ra) {
    User currentUser = getCurrentUser(auth);
    if (currentUser == null) {
      return "redirect:/login";
    }
    LadderSeason competitionSeason =
        competitionSeasonService != null
            ? competitionSeasonService.resolveActiveCompetitionSeason()
            : null;
    GroupCreationService.GroupCreationOutcome outcome =
        groupCreationService.createOrReuseSession(currentUser.getId(), null, competitionSeason);
    if (!outcome.success()) {
      ra.addFlashAttribute("toastMessage", outcome.toastMessage());
      ra.addFlashAttribute("toastLevel", outcome.toastLevel());
      return "redirect:" + normalizeSessionStartReturnTo(returnTo);
    }
    if (outcome.toastMessage() != null) {
      ra.addFlashAttribute("toastMessage", outcome.toastMessage());
      ra.addFlashAttribute("toastLevel", outcome.toastLevel());
    }
    return redirectToSession(
        outcome.configId(), false, outcome.reusedExistingSession() ? null : SESSION_TOUR_OWNER);
  }

  @PostMapping
  @Transactional
  public String create(
      Authentication auth,
      @RequestParam(name = "type", required = false, defaultValue = "STANDARD")
          LadderConfig.Type type,
      @RequestParam(name = "returnTo", required = false) String returnTo,
      @RequestParam(name = "tournamentMode", required = false, defaultValue = "false")
          boolean tournamentMode,
      @RequestParam String title,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate seasonStart,
      // Old form sent seasonEnd; new form may omit it
      @RequestParam(name = "seasonEnd", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate seasonEnd,
      // Old: recurrenceType (kept for compatibility)
      @RequestParam(name = "recurrenceType", required = false) String recurrenceType,
      // New: season mode and cadence
      @RequestParam(name = "mode", required = false, defaultValue = "ROLLING")
          LadderConfig.Mode mode,
      @RequestParam(name = "rollingEveryCount", required = false) Integer rollingEveryCount,
      @RequestParam(name = "rollingEveryUnit", required = false)
          LadderConfig.CadenceUnit rollingEveryUnit,
      @RequestParam(name = "seasonName", required = false) String seasonName,
      @RequestParam(name = "securityLevel", required = false, defaultValue = "STANDARD")
          LadderSecurity securityLevel,
      @RequestParam(
              name = "allowGuestOnlyPersonalMatches",
              required = false,
              defaultValue = "false")
          boolean allowGuestOnlyPersonalMatches,
      @RequestParam(name = "storyModeDefaultEnabled", required = false, defaultValue = "false")
          boolean storyModeDefaultEnabled,
      RedirectAttributes ra) {

    User currentUser = getCurrentUser(auth);
    if (currentUser == null) {
      return "redirect:/login";
    }
    String normalizedReturnTo = normalizeGroupReturnTo(returnTo);

    if (type == LadderConfig.Type.SESSION) {
      LadderSeason competitionSeason =
          competitionSeasonService != null
              ? competitionSeasonService.resolveActiveCompetitionSeason()
              : null;
      GroupCreationService.GroupCreationOutcome sessionOutcome =
          groupCreationService.createOrReuseSession(currentUser.getId(), title, competitionSeason);
      if (!sessionOutcome.success()) {
        ra.addFlashAttribute("toastMessage", sessionOutcome.toastMessage());
        ra.addFlashAttribute("toastLevel", sessionOutcome.toastLevel());
        return "redirect:/groups/new?type=SESSION";
      }
      if (sessionOutcome.toastMessage() != null) {
        ra.addFlashAttribute("toastMessage", sessionOutcome.toastMessage());
        ra.addFlashAttribute("toastLevel", sessionOutcome.toastLevel());
      }
      return redirectToSession(
          sessionOutcome.configId(),
          false,
          sessionOutcome.reusedExistingSession() ? null : SESSION_TOUR_OWNER);
    }
    GroupCreationService.GroupCreationOutcome outcome =
        groupCreationService.createGroup(
            currentUser.getId(),
            new GroupCreationService.GroupCreateRequest(
                title,
                seasonStart,
                seasonEnd,
                mode,
                rollingEveryCount,
                rollingEveryUnit,
                seasonName,
                securityLevel,
                allowGuestOnlyPersonalMatches,
                storyModeDefaultEnabled,
                tournamentMode));
    if (!outcome.success()) {
      ra.addFlashAttribute("toastMessage", outcome.toastMessage());
      ra.addFlashAttribute("toastLevel", outcome.toastLevel());
      return redirectCreateForm(normalizedReturnTo, tournamentMode);
    }
    ra.addFlashAttribute("toastMessage", outcome.toastMessage());
    ra.addFlashAttribute("toastLevel", outcome.toastLevel());
    return "redirect:/groups/" + outcome.configId();
  }

  public String create(
      Authentication auth,
      String title,
      LocalDate seasonStart,
      LocalDate seasonEnd,
      String recurrenceType,
      LadderConfig.Mode mode,
      Integer rollingEveryCount,
      LadderConfig.CadenceUnit rollingEveryUnit,
      String seasonName,
      LadderSecurity securityLevel,
      boolean allowGuestOnlyPersonalMatches,
      boolean storyModeDefaultEnabled,
      RedirectAttributes ra) {
    return create(
        auth,
        LadderConfig.Type.STANDARD,
        null,
        false,
        title,
        seasonStart,
        seasonEnd,
        recurrenceType,
        mode,
        rollingEveryCount,
        rollingEveryUnit,
        seasonName,
        securityLevel,
        allowGuestOnlyPersonalMatches,
        storyModeDefaultEnabled,
        ra);
  }

  private String normalizeSessionStartReturnTo(String returnTo) {
    if ("/home".equals(returnTo)) {
      return "/home";
    }
    if ("/competition/sessions".equals(returnTo)) {
      return "/competition/sessions";
    }
    return "/competition";
  }

  private String resolveSessionDisplayTitle(LadderConfig cfg) {
    if (cfg == null || !cfg.isSessionType()) {
      return cfg != null ? cfg.getTitle() : null;
    }
    String title = cfg.getTitle();
    if (!StringUtils.hasText(title)) {
      return "Session";
    }
    Matcher matcher = LEGACY_AUTO_SESSION_TITLE.matcher(title.trim());
    return matcher.matches() ? matcher.group(1) : title;
  }

  @GetMapping("/{configId}")
  public String show(
      @PathVariable Long configId,
      @RequestParam(name = "sort", required = false, defaultValue = "joined") String sort,
      Model model,
      Authentication auth,
      jakarta.servlet.http.HttpServletRequest request) {

    var cfg =
        configs
            .findById(configId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    User currentUser = getCurrentUser(auth);
    if (currentUser == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND); // hide existence
    }
    boolean currentUserIsSiteAdmin = isSiteWideAdmin(auth);
    if (cfg.isCompetitionType() && !currentUserIsSiteAdmin) {
      return "redirect:/competition";
    }

    List<LadderMembership> allActive =
        membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            configId, LadderMembership.State.ACTIVE);
    LadderMembership currentMembership =
        allActive.stream()
            .filter(member -> Objects.equals(member.getUserId(), currentUser.getId()))
            .findFirst()
            .orElse(null);
    if (!(cfg.isCompetitionType() && currentUserIsSiteAdmin) && currentMembership == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    boolean currentUserIsLadderAdmin =
        currentMembership != null && currentMembership.getRole() == LadderMembership.Role.ADMIN;
    boolean currentUserIsAdmin =
        (cfg.isCompetitionType() && currentUserIsSiteAdmin) || currentUserIsLadderAdmin;
    model.addAttribute("currentUserIsAdmin", currentUserIsAdmin);
    // === New: season flags for Thymeleaf ===
    var activeSeasonOpt =
        cfg.isSessionType() ? Optional.<LadderSeason>empty() : seasons.findActive(configId);
    activeSeasonOpt.ifPresent(
        s -> {
          model.addAttribute("season", s);
          model.addAttribute("seasonId", s.getId());
        });
    boolean hasActiveSeason = activeSeasonOpt.isPresent();
    model.addAttribute("hasActiveSeason", hasActiveSeason);
    model.addAttribute("isSessionLadder", cfg.isSessionType());
    model.addAttribute("isCompetitionLadder", cfg.isCompetitionType());
    model.addAttribute("sessionDisplayTitle", resolveSessionDisplayTitle(cfg));
    LadderSeason targetSeason =
        cfg.isSessionType()
            ? resolveSessionTargetSeason(cfg)
            : (competitionSeasonService != null
                ? competitionSeasonService.resolveTargetSeason(cfg)
                : null);
    model.addAttribute("targetSeason", targetSeason);
    model.addAttribute(
        "sessionTargetSeasonDateRange", targetSeason != null ? dateRange(targetSeason) : "");
    model.addAttribute(
        "sessionStandingsRecalculationPending",
        targetSeason != null && targetSeason.isStandingsRecalcInProgress());
    MatchDashboardService.DashboardModel sessionDashboard =
        matchDashboardViewService != null ? matchDashboardViewService.emptyDashboard() : null;
    SessionConfirmationState sessionConfirmationState = SessionConfirmationState.empty();
    List<com.w3llspring.fhpb.web.model.RoundRobinStanding> sessionBaseStandings = List.of();
    if (cfg.isSessionType() && matchDashboardService != null && matchDashboardViewService != null) {
      sessionDashboard = matchDashboardService.buildPendingForUserInSeason(currentUser, targetSeason);
      matchDashboardViewService.applyToModel(model, sessionDashboard);
    }
    if (cfg.isSessionType()) {
      applySessionRoundRobinTask(
          model,
          cfg,
          currentUser,
          sessionDashboard != null
              ? sessionDashboard.matchRowModel().getConfirmableMatchIds()
              : Set.of());
      model.addAttribute("canStartSessionRoundRobin", canStartSessionRoundRobin(cfg, currentUser));
      sessionBaseStandings =
          roundRobinService != null
              ? Objects.requireNonNullElse(roundRobinService.computeStandingsForSession(cfg), List.of())
              : List.of();
      model.addAttribute(
          "improvementAdvice",
          improvementAdvisor != null
              ? improvementAdvisor.buildAdvice(currentUser, cfg, targetSeason)
              : null);
      sessionConfirmationState = buildSessionConfirmationState(sessionDashboard);
      applySessionConfirmationModel(model, sessionConfirmationState);
    }

    model.addAttribute(
        "pendingSessionJoinRequests",
        cfg.isSessionType() && currentUserIsLadderAdmin && sessionJoinRequestService != null
            ? sessionJoinRequestService.listPendingForAdmin(configId, currentUser.getId())
            : List.of());
    String sessionTourVariant =
        resolveSessionTourVariant(cfg, currentUser, request.getParameter("tour"));
    if (sessionTourVariant != null) {
      model.addAttribute("sessionTourVariant", sessionTourVariant);
    }

    List<UserDisplayNameAudit> recentDisplayNameChanges =
        currentUserIsAdmin && !cfg.isSessionType()
            ? userDisplayNameAuditRepository.findByLadderConfigIdOrderByChangedAtDesc(
                configId, PageRequest.of(0, 12))
            : List.of();
    model.addAttribute("recentDisplayNameChanges", recentDisplayNameChanges);

    // Transition guard (for manual Start/End buttons + countdown)
    // Requires SeasonTransitionService to be injected into this controller.
    var tw = transitionSvc.canCreateSeason(cfg);
    model.addAttribute("transitionAllowed", tw.isAllowed());
    model.addAttribute("countdownText", tw.isAllowed() ? "" : transitionSvc.formatCountdown(tw));

    // Keep your existing attributes
    model.addAttribute("ladder", cfg);
    model.addAttribute("ladderId", cfg.getId()); // handy for th:hrefs
    model.addAttribute("seasons", seasons.findByLadderConfigIdOrderByStartDateDesc(configId));

    // Base list (already joinedAt ASC from repo)
    var members = allActive.stream().collect(Collectors.toList());

    var bannedMembers =
        membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            configId, LadderMembership.State.BANNED);

    // Load users for display-name resolution
    var userIds =
        Stream.concat(members.stream(), bannedMembers.stream())
            .map(LadderMembership::getUserId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    recentDisplayNameChanges.stream()
        .map(UserDisplayNameAudit::getUserId)
        .filter(id -> id != null)
        .forEach(userIds::add);
    var users = userRepo.findAllById(userIds);
    Map<Long, User> userById = users.stream().collect(Collectors.toMap(User::getId, u -> u));

    // Sorting
    if ("alpha".equalsIgnoreCase(sort)) {
      Comparator<LadderMembership> byName =
          Comparator.comparing(
              m -> {
                User u = userById.get(m.getUserId());
                if (u == null) return "zzz_unknown"; // push unknown to bottom
                String name = com.w3llspring.fhpb.web.util.UserPublicName.forUser(u);
                return name == null ? "zzz_unknown" : name.toLowerCase();
              });
      members.sort(byName);
    } else if ("joined_desc".equalsIgnoreCase(sort)) {
      members.sort(Comparator.comparing(LadderMembership::getJoinedAt).reversed());
    } // else default from repo: joined asc

    model.addAttribute("members", members);
    model.addAttribute("bannedMembers", bannedMembers);
    model.addAttribute("userById", userById);
    model.addAttribute("sort", sort);
    model.addAttribute(
        "courtNameByUser",
        cfg.isSessionType() && matchEntryContextService != null
            ? matchEntryContextService.buildCourtNameByUserIds(
                members.stream().map(LadderMembership::getUserId).filter(Objects::nonNull).toList(),
                cfg.getId())
            : Map.of());
    if (cfg.isSessionType()) {
      List<User> activeUsers =
          members.stream()
              .map(member -> userById.get(member.getUserId()))
              .filter(Objects::nonNull)
              .toList();
      model.addAttribute("sessionHeroTitle", resolveSessionDisplayTitle(cfg));
      applySessionRecentTickerModel(model, cfg);
      applySessionStandingsModel(
          model, members, userById, targetSeason, sessionBaseStandings);
      model.addAttribute(
          "voiceLanguage",
          matchEntryContextService != null
              ? matchEntryContextService.determineVoiceLanguage(currentUser)
              : "en-US");
      model.addAttribute(
          "voicePhraseHints",
          matchEntryContextService != null
              ? matchEntryContextService.buildVoicePhraseHintsFromUsers(currentUser, activeUsers)
              : List.of());
      model.addAttribute("voiceMaxAlternatives", Integer.valueOf(3));
    }
    int activeMemberCount = members.size();
    model.addAttribute(
        "memberSectionTitle",
        cfg.isSessionType()
            ? String.format("Session Members (%d/%d)", activeMemberCount, defaultMaxMembers)
            : String.format("Members (%d/%d)", activeMemberCount, defaultMaxMembers));
    model.addAttribute("maxRollingEveryCount", LadderConfig.MAX_ROLLING_EVERY_COUNT);
    model.addAttribute("storyModeFeatureEnabled", storyModeFeatureEnabled());

    model.addAttribute("currentUserId", currentUser.getId());
    model.addAttribute("leaveConfirmMessage", buildLeaveConfirmMessage(cfg, currentUser.getId()));
    model.addAttribute("leaveActionLabel", buildLeaveActionLabel(cfg, currentUser.getId()));
    model.addAttribute(
        "returnToPath", ReturnToSanitizer.sanitize(ReturnToSanitizer.toAppRelativePath(request)));

    model.addAttribute("pendingDeletion", cfg.isPendingDeletion());

    model.addAttribute("ownerUserId", cfg.getOwnerUserId());

    boolean inviteActive = hasActiveInvite(cfg);
    Instant sessionInviteActiveUntil =
        cfg.isSessionType()
            ? SessionInviteCodeSupport.activeUntil(cfg.getLastInviteChangeAt(), sessionInviteActiveSeconds)
            : null;
    model.addAttribute("inviteActive", Boolean.valueOf(inviteActive));
    model.addAttribute("sessionInviteActiveUntil", sessionInviteActiveUntil);

    String inviteShareLink = null;
    if (inviteActive) {
      String normalizedConfiguredBaseUrl = normalizeConfiguredPublicBaseUrl(publicBaseUrl);
      org.springframework.web.util.UriComponentsBuilder inviteBuilder;
      if (StringUtils.hasText(normalizedConfiguredBaseUrl)) {
        inviteBuilder =
            org.springframework.web.util.UriComponentsBuilder.fromUriString(
                    normalizedConfiguredBaseUrl)
                .path("/groups/join");
      } else {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String normalizedPath =
            contextPath.endsWith("/")
                ? contextPath.substring(0, contextPath.length() - 1)
                : contextPath;
        String basePath = normalizedPath + "/groups/join";
        inviteBuilder =
            org.springframework.web.util.UriComponentsBuilder.fromUriString(
                    org.springframework.web.servlet.support.ServletUriComponentsBuilder
                        .fromRequestUri(request)
                        .replacePath(basePath)
                        .replaceQuery(null)
                        .build()
                        .toUriString());
      }
      inviteBuilder.replaceQueryParam("inviteCode", cfg.getInviteCode());
      if (cfg.isSessionType()) {
        inviteBuilder.replaceQueryParam("autoJoin", "true");
      }
      inviteShareLink = inviteBuilder.build().toUriString();
    }
    model.addAttribute("ladderInviteLink", inviteShareLink);

    return "auth/show";
  }

  private void applySessionStandingsModel(
      Model model,
      List<LadderMembership> members,
      Map<Long, User> userById,
      LadderSeason targetSeason,
      List<com.w3llspring.fhpb.web.model.RoundRobinStanding> baseStandings) {
    LinkedHashSet<Long> activeMemberIds = new LinkedHashSet<>();
    if (members != null) {
      for (LadderMembership member : members) {
        if (member != null && member.getUserId() != null) {
          activeMemberIds.add(member.getUserId());
        }
      }
    }

    List<com.w3llspring.fhpb.web.model.RoundRobinStanding> sessionStandings = new ArrayList<>();
    if (baseStandings != null) {
      sessionStandings.addAll(
          baseStandings.stream()
              .filter(Objects::nonNull)
              .filter(
                  standing ->
                      standing.getUserId() == null
                          || activeMemberIds.isEmpty()
                          || activeMemberIds.contains(standing.getUserId()))
              .toList());
    }

    LinkedHashSet<Long> seenUserIds = new LinkedHashSet<>();
    for (com.w3llspring.fhpb.web.model.RoundRobinStanding standing : sessionStandings) {
      if (standing.getUserId() != null) {
        seenUserIds.add(standing.getUserId());
      }
    }

    if (members != null) {
      for (LadderMembership member : members) {
        if (member == null || member.getUserId() == null || !seenUserIds.add(member.getUserId())) {
          continue;
        }
        User user = userById != null ? userById.get(member.getUserId()) : null;
        sessionStandings.add(
            new com.w3llspring.fhpb.web.model.RoundRobinStanding(
                member.getUserId(), resolveSessionStandingName(user)));
      }
    }

    Map<Long, Integer> globalRankByUserId = new LinkedHashMap<>();
    Map<Long, Integer> ratingByUserId = new LinkedHashMap<>();
    Map<Long, Integer> momentumByUserId = new LinkedHashMap<>();
    if (members != null) {
      for (LadderMembership member : members) {
        if (member != null && member.getUserId() != null) {
          ratingByUserId.put(member.getUserId(), Integer.valueOf(1000));
          momentumByUserId.put(member.getUserId(), Integer.valueOf(0));
        }
      }
    }
    if (targetSeason != null && seasonStandingsViewService != null) {
      SeasonStandingsViewService.SeasonStandingsView standingsView =
          seasonStandingsViewService.load(targetSeason);
      for (var row : standingsView.rows()) {
        if (row == null || row.userId == null) {
          continue;
        }
        globalRankByUserId.put(row.userId, Integer.valueOf(row.rank));
        ratingByUserId.put(row.userId, Integer.valueOf(1000 + row.points));
        momentumByUserId.put(row.userId, Integer.valueOf(row.momentum));
      }
    }

    model.addAttribute("sessionStandings", sessionStandings);
    model.addAttribute("sessionGlobalRankByUserId", globalRankByUserId);
    model.addAttribute("sessionRatingByUserId", ratingByUserId);
    model.addAttribute("sessionMomentumByUserId", momentumByUserId);
    model.addAttribute(
        "sessionStandingsAwaitingConfirmationUserIds",
        findSessionStandingsAwaitingConfirmationUserIds(targetSeason));
  }

  private LadderSeason resolveSessionTargetSeason(LadderConfig sessionConfig) {
    if (sessionConfig == null || !sessionConfig.isSessionType()) {
      return null;
    }

    LadderSeason linkedTargetSeason =
        competitionSeasonService != null ? competitionSeasonService.resolveTargetSeason(sessionConfig) : null;
    if (linkedTargetSeason != null) {
      return linkedTargetSeason;
    }
    return competitionSeasonService != null
        ? competitionSeasonService.resolveActiveCompetitionSeason()
        : null;
  }

  private Set<Long> findSessionStandingsAwaitingConfirmationUserIds(LadderSeason targetSeason) {
    if (targetSeason == null || matchRepo == null) {
      return Set.of();
    }

    List<Match> seasonMatches = matchRepo.findBySeasonOrderByPlayedAtDescWithUsers(targetSeason);
    if (seasonMatches == null || seasonMatches.isEmpty()) {
      return Set.of();
    }

    LinkedHashSet<Long> userIds = new LinkedHashSet<>();
    for (Match match : seasonMatches) {
      if (match == null
          || match.isConfirmationLocked()
          || match.getState() == MatchState.CONFIRMED
          || match.getState() == MatchState.FLAGGED
          || match.getState() == MatchState.NULLIFIED) {
        continue;
      }

      addSessionStandingsAwaitingConfirmationUserId(userIds, match.getA1());
      addSessionStandingsAwaitingConfirmationUserId(userIds, match.getA2());
      addSessionStandingsAwaitingConfirmationUserId(userIds, match.getB1());
      addSessionStandingsAwaitingConfirmationUserId(userIds, match.getB2());
    }
    return userIds;
  }

  private void addSessionStandingsAwaitingConfirmationUserId(Set<Long> userIds, User user) {
    if (userIds == null || user == null || user.getId() == null) {
      return;
    }
    userIds.add(user.getId());
  }

  private void applySessionRecentTickerModel(Model model, LadderConfig sessionConfig) {
    model.addAttribute("sessionRecentTickerItems", buildSessionRecentTickerItems(sessionConfig));
  }

  private List<SessionRecentTickerItem> buildSessionRecentTickerItems(LadderConfig sessionConfig) {
    if (sessionConfig == null
        || sessionConfig.getId() == null
        || !sessionConfig.isSessionType()
        || matchRepo == null) {
      return List.of();
    }

    List<SessionRecentTickerMatchTimeline> timelines =
        loadSessionRecentTickerTimelines(sessionConfig.getId());
    if (timelines.isEmpty()) {
      return List.of();
    }

    Map<Long, Match> matchById =
        matchRepo.findAllByIdInWithUsers(
                timelines.stream().map(SessionRecentTickerMatchTimeline::matchId).toList())
            .stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Match::getId, match -> match, (left, right) -> left));

    return timelines.stream()
        .map(
            timeline -> {
              Match match = matchById.get(timeline.matchId());
              if (match == null) {
                return null;
              }
              Instant confirmedAt = timeline.confirmedAt();
              return new SessionRecentTickerItem(
                  match.getId(),
                  buildSessionRecentTickerAgeLabel(confirmedAt),
                  buildSessionRecentTickerSummary(match),
                  confirmedAt);
            })
        .filter(Objects::nonNull)
        .toList();
  }

  private List<SessionRecentTickerMatchTimeline> loadSessionRecentTickerTimelines(Long sessionConfigId) {
    if (sessionConfigId == null || matchConfirmationRepository == null) {
      return List.of();
    }

    List<MatchConfirmationRepository.SessionConfirmedMatchTimeline> projections =
        matchConfirmationRepository.findRecentConfirmedSessionTimelines(
            sessionConfigId,
            MatchState.CONFIRMED,
            PageRequest.of(0, SESSION_RECENT_TICKER_LIMIT));
    if (projections == null || projections.isEmpty()) {
      return List.of();
    }

    return projections.stream()
        .filter(
            projection ->
                projection != null
                    && projection.getMatchId() != null
                    && projection.getConfirmedAt() != null)
        .map(
            projection ->
                new SessionRecentTickerMatchTimeline(
                    projection.getMatchId(), projection.getConfirmedAt()))
        .toList();
  }

  private String buildSessionRecentTickerSummary(Match match) {
    if (match == null) {
      return "";
    }

    boolean teamAWon = match.isTeamAWinner();
    String winners =
        teamAWon
            ? sessionTickerTeamLabel(match.getA1(), match.isA1Guest(), match.getA2(), match.isA2Guest())
            : sessionTickerTeamLabel(match.getB1(), match.isB1Guest(), match.getB2(), match.isB2Guest());
    String losers =
        teamAWon
            ? sessionTickerTeamLabel(match.getB1(), match.isB1Guest(), match.getB2(), match.isB2Guest())
            : sessionTickerTeamLabel(match.getA1(), match.isA1Guest(), match.getA2(), match.isA2Guest());
    int winnerScore = teamAWon ? match.getScoreA() : match.getScoreB();
    int loserScore = teamAWon ? match.getScoreB() : match.getScoreA();
    return winners + " def " + losers + " " + winnerScore + "-" + loserScore;
  }

  private String sessionTickerTeamLabel(
      User firstPlayer, boolean firstGuest, User secondPlayer, boolean secondGuest) {
    List<String> names = new ArrayList<>(2);
    if (firstGuest) {
      names.add(UserPublicName.GUEST);
    } else if (firstPlayer != null) {
      names.add(UserPublicName.forUser(firstPlayer));
    }
    if (secondGuest) {
      names.add(UserPublicName.GUEST);
    } else if (secondPlayer != null) {
      names.add(UserPublicName.forUser(secondPlayer));
    }
    if (names.isEmpty()) {
      return "Guest Squad";
    }
    return String.join(" & ", names);
  }

  private String buildSessionRecentTickerAgeLabel(Instant timeline) {
    if (timeline == null || Instant.EPOCH.equals(timeline)) {
      return "Recent";
    }

    Duration age = Duration.between(timeline, Instant.now());
    if (age.isNegative()) {
      age = Duration.ZERO;
    }

    long seconds = age.getSeconds();
    if (seconds < 90L) {
      return "Just now";
    }

    long minutes = age.toMinutes();
    if (minutes < 60L) {
      return minutes + " minute" + (minutes == 1L ? "" : "s") + " ago";
    }

    long hours = age.toHours();
    if (hours < 24L) {
      return hours + " hour" + (hours == 1L ? "" : "s") + " ago";
    }

    long days = age.toDays();
    return days + " day" + (days == 1L ? "" : "s") + " ago";
  }

  private void applySessionConfirmationModel(Model model, SessionConfirmationState state) {
    SessionConfirmationState effectiveState =
        state != null ? state : SessionConfirmationState.empty();
    model.addAttribute(
        "sessionConfirmationInboxCount", Integer.valueOf(effectiveState.inboxCount()));
    model.addAttribute(
        "sessionConfirmationOutboxCount", Integer.valueOf(effectiveState.outboxCount()));
    model.addAttribute("sessionConfirmationInboxLinks", effectiveState.inboxLinks());
    model.addAttribute("sessionConfirmationOutboxLinks", effectiveState.outboxLinks());
    model.addAttribute(
        "sessionConfirmationInboxConfirmableMatchIds", effectiveState.inboxConfirmableMatchIds());
    model.addAttribute(
        "sessionConfirmationInboxNullifyApprovableByMatchId",
        effectiveState.inboxNullifyApprovableByMatchId());
    model.addAttribute(
        "sessionConfirmationOutboxWaitingOnOpponentByMatchId",
        effectiveState.outboxWaitingOnOpponentByMatchId());
    model.addAttribute(
        "sessionConfirmationOutboxNullifyWaitingOnOpponentByMatchId",
        effectiveState.outboxNullifyWaitingOnOpponentByMatchId());
    model.addAttribute("sessionConfirmationsAutoOpen", effectiveState.hasAny());
  }

  private SessionConfirmationState buildSessionConfirmationState(
      MatchDashboardService.DashboardModel dashboard) {
    Set<Long> inboxMatchIds = sessionConfirmationInboxMatchIds(dashboard);
    Set<Long> outboxMatchIds = sessionConfirmationOutboxMatchIds(dashboard);
    MatchRowModel rowModel = dashboard != null ? dashboard.matchRowModel() : null;
    return new SessionConfirmationState(
        filterLinksByMatchIds(dashboard, inboxMatchIds),
        filterLinksByMatchIds(dashboard, outboxMatchIds),
        filterMatchIds(rowModel != null ? rowModel.getConfirmableMatchIds() : Set.of(), inboxMatchIds),
        filterTrueFlagsByMatchIds(
            rowModel != null ? rowModel.getNullifyApprovableByMatchId() : Map.of(), inboxMatchIds),
        filterTrueFlagsByMatchIds(
            rowModel != null ? rowModel.getWaitingOnOpponentByMatchId() : Map.of(), outboxMatchIds),
        filterTrueFlagsByMatchIds(
            rowModel != null ? rowModel.getNullifyWaitingOnOpponentByMatchId() : Map.of(),
            outboxMatchIds));
  }

  private Set<Long> sessionConfirmationInboxMatchIds(
      MatchDashboardService.DashboardModel dashboard) {
    Set<Long> inboxMatchIds = new LinkedHashSet<>();
    if (dashboard != null
        && dashboard.matchRowModel() != null
        && dashboard.matchRowModel().getConfirmableMatchIds() != null) {
      inboxMatchIds.addAll(dashboard.matchRowModel().getConfirmableMatchIds());
    }
    collectTrueMatchIds(
        inboxMatchIds,
        dashboard != null && dashboard.matchRowModel() != null
            ? dashboard.matchRowModel().getNullifyApprovableByMatchId()
            : Map.of());
    return inboxMatchIds;
  }

  private Set<Long> sessionConfirmationOutboxMatchIds(
      MatchDashboardService.DashboardModel dashboard) {
    Set<Long> outboxMatchIds = new LinkedHashSet<>();
    collectTrueMatchIds(
        outboxMatchIds,
        dashboard != null && dashboard.matchRowModel() != null
            ? dashboard.matchRowModel().getWaitingOnOpponentByMatchId()
            : Map.of());
    collectTrueMatchIds(
        outboxMatchIds,
        dashboard != null && dashboard.matchRowModel() != null
            ? dashboard.matchRowModel().getNullifyWaitingOnOpponentByMatchId()
            : Map.of());
    return outboxMatchIds;
  }

  private List<LadderMatchLink> filterLinksByMatchIds(
      MatchDashboardService.DashboardModel dashboard, Set<Long> matchIds) {
    if (dashboard == null || dashboard.links() == null || dashboard.links().isEmpty()) {
      return List.of();
    }
    if (matchIds == null || matchIds.isEmpty()) {
      return List.of();
    }
    return dashboard.links().stream()
        .filter(Objects::nonNull)
        .filter(link -> link.getMatch() != null && matchIds.contains(link.getMatch().getId()))
        .toList();
  }

  private Set<Long> filterMatchIds(Set<Long> matchIds, Set<Long> allowedMatchIds) {
    if (matchIds == null || matchIds.isEmpty() || allowedMatchIds == null || allowedMatchIds.isEmpty()) {
      return Set.of();
    }
    return matchIds.stream()
        .filter(Objects::nonNull)
        .filter(allowedMatchIds::contains)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Map<Long, Boolean> filterTrueFlagsByMatchIds(
      Map<Long, Boolean> flagsByMatchId, Set<Long> allowedMatchIds) {
    if (flagsByMatchId == null
        || flagsByMatchId.isEmpty()
        || allowedMatchIds == null
        || allowedMatchIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, Boolean> filtered = new LinkedHashMap<>();
    for (Map.Entry<Long, Boolean> entry : flagsByMatchId.entrySet()) {
      if (entry.getKey() != null
          && allowedMatchIds.contains(entry.getKey())
          && Boolean.TRUE.equals(entry.getValue())) {
        filtered.put(entry.getKey(), Boolean.TRUE);
      }
    }
    return filtered;
  }

  private void collectTrueMatchIds(Set<Long> target, Map<Long, Boolean> flagsByMatchId) {
    if (target == null || flagsByMatchId == null || flagsByMatchId.isEmpty()) {
      return;
    }
    for (Map.Entry<Long, Boolean> entry : flagsByMatchId.entrySet()) {
      if (entry.getKey() != null && Boolean.TRUE.equals(entry.getValue())) {
        target.add(entry.getKey());
      }
    }
  }

  private boolean canStartSessionRoundRobin(LadderConfig ladder, User currentUser) {
    if (ladder == null || !ladder.isSessionType()) {
      return false;
    }
    if (currentUser == null || currentUser.getId() == null) {
      return false;
    }
    return currentUser.isAdmin() || Objects.equals(ladder.getOwnerUserId(), currentUser.getId());
  }

  private void applySessionRoundRobinTask(
      Model model, LadderConfig sessionConfig, User currentUser, java.util.Set<Long> confirmableMatchIds) {
    model.addAttribute("sessionRoundRobinTask", null);
    model.addAttribute("sessionRoundRobinRounds", List.of());
    if (model == null
        || sessionConfig == null
        || !sessionConfig.isSessionType()
        || currentUser == null
        || currentUser.getId() == null
        || roundRobinService == null) {
      return;
    }

    var assignment =
        roundRobinService.findActiveSessionAssignment(sessionConfig, currentUser.getId()).orElse(null);
    if (assignment == null || assignment.entry() == null || assignment.roundRobin() == null) {
      return;
    }

    java.util.LinkedHashSet<Long> userIds = new java.util.LinkedHashSet<>();
    collectSessionRoundRobinUserId(assignment.entry().getA1(), userIds);
    collectSessionRoundRobinUserId(assignment.entry().getA2(), userIds);
    collectSessionRoundRobinUserId(assignment.entry().getB1(), userIds);
    collectSessionRoundRobinUserId(assignment.entry().getB2(), userIds);
    Map<Long, String> displayNames = roundRobinService.buildDisplayNameMap(userIds, sessionConfig.getId());

    java.util.LinkedHashMap<String, Object> task = new java.util.LinkedHashMap<>();
    task.put("roundRobinId", assignment.roundRobin().getId());
    task.put("currentRound", assignment.currentRound());
    task.put("maxRound", assignment.maxRound());
    task.put("entryId", assignment.entry().getId());
    task.put("bye", assignment.entry().isBye());
    task.put("match", assignment.match());
    task.put("matchId", assignment.match() != null ? assignment.match().getId() : null);
    task.put("confirmed", assignment.match() != null && assignment.match().getState() == com.w3llspring.fhpb.web.model.MatchState.CONFIRMED);
    task.put("a1Name", sessionRoundRobinName(assignment.entry().getA1(), displayNames));
    task.put("a2Name", sessionRoundRobinName(assignment.entry().getA2(), displayNames));
    task.put("b1Name", sessionRoundRobinName(assignment.entry().getB1(), displayNames));
    task.put("b2Name", sessionRoundRobinName(assignment.entry().getB2(), displayNames));

    boolean canConfirm =
        assignment.match() != null
            && assignment.match().getId() != null
            && confirmableMatchIds != null
            && confirmableMatchIds.contains(assignment.match().getId());
    task.put("canConfirm", canConfirm);
    task.put(
        "waitingOnOpponentConfirmation",
        assignment.match() != null
            && assignment.match().getState() != com.w3llspring.fhpb.web.model.MatchState.CONFIRMED
            && !canConfirm);
    task.put(
        "readyToLog", !assignment.entry().isBye() && assignment.match() == null);

    Long currentUserId = currentUser.getId();
    task.put("quickLogA1", sessionRoundRobinQuickLogValue(currentUserId, assignment.entry(), "a1"));
    task.put("quickLogA2", sessionRoundRobinQuickLogValue(currentUserId, assignment.entry(), "a2"));
    task.put("quickLogB1", sessionRoundRobinQuickLogValue(currentUserId, assignment.entry(), "b1"));
    task.put("quickLogB2", sessionRoundRobinQuickLogValue(currentUserId, assignment.entry(), "b2"));
    model.addAttribute("sessionRoundRobinTask", task);
    model.addAttribute(
        "sessionRoundRobinRounds",
        buildSessionRoundRobinRounds(
            currentUser,
            assignment,
            displayNames,
            confirmableMatchIds,
            task));
  }

  private List<Map<String, Object>> buildSessionRoundRobinRounds(
      User currentUser,
      RoundRobinService.ActiveSessionAssignment assignment,
      Map<Long, String> displayNames,
      Set<Long> confirmableMatchIds,
      Map<String, Object> currentRoundTask) {
    if (currentUser == null
        || currentUser.getId() == null
        || assignment == null
        || assignment.roundRobin() == null
        || assignment.roundRobin().getId() == null
        || roundRobinService == null) {
      return List.of();
    }

    try {
      int maxRound =
          assignment.maxRound() > 0
              ? assignment.maxRound()
              : roundRobinService.getMaxRound(assignment.roundRobin().getId());
      if (maxRound <= 0) {
        return List.of(buildCurrentSessionRoundRobinRound(assignment, displayNames, currentRoundTask));
      }

      List<Map<String, Object>> rounds = new ArrayList<>();
      for (int roundNumber = 1; roundNumber <= maxRound; roundNumber++) {
        var entry =
            findSessionRoundRobinEntryForUser(
                roundRobinService.getEntriesForRound(assignment.roundRobin().getId(), roundNumber),
                currentUser.getId());
        if (entry == null) {
          continue;
        }

        LinkedHashMap<String, Object> round = new LinkedHashMap<>();
        round.put("roundNumber", Integer.valueOf(roundNumber));
        round.put("current", Boolean.valueOf(roundNumber == assignment.currentRound()));
        round.put("bye", entry.isBye());
        round.put("a1Name", sessionRoundRobinName(entry.getA1(), displayNames));
        round.put("a2Name", sessionRoundRobinName(entry.getA2(), displayNames));
        round.put("b1Name", sessionRoundRobinName(entry.getB1(), displayNames));
        round.put("b2Name", sessionRoundRobinName(entry.getB2(), displayNames));

        Match linkedMatch = resolveSessionRoundRobinMatch(assignment.roundRobin(), entry);
        round.put("match", linkedMatch);

        boolean currentRound = roundNumber == assignment.currentRound();
        boolean canConfirm =
            currentRound
                && linkedMatch != null
                && linkedMatch.getId() != null
                && confirmableMatchIds != null
                && confirmableMatchIds.contains(linkedMatch.getId());
        boolean readyToLog = currentRound && !entry.isBye() && linkedMatch == null;

        round.put("canConfirm", Boolean.valueOf(canConfirm));
        round.put("readyToLog", Boolean.valueOf(readyToLog));
        round.put("quickLogA1", currentRound ? currentRoundTask.get("quickLogA1") : null);
        round.put("quickLogA2", currentRound ? currentRoundTask.get("quickLogA2") : null);
        round.put("quickLogB1", currentRound ? currentRoundTask.get("quickLogB1") : null);
        round.put("quickLogB2", currentRound ? currentRoundTask.get("quickLogB2") : null);
        round.put(
            "status",
            sessionRoundRobinRoundStatus(roundNumber, assignment.currentRound(), entry, linkedMatch));
        rounds.add(round);
      }
      return rounds.isEmpty()
          ? List.of(buildCurrentSessionRoundRobinRound(assignment, displayNames, currentRoundTask))
          : rounds;
    } catch (RuntimeException ex) {
      return List.of(buildCurrentSessionRoundRobinRound(assignment, displayNames, currentRoundTask));
    }
  }

  private Map<String, Object> buildCurrentSessionRoundRobinRound(
      RoundRobinService.ActiveSessionAssignment assignment,
      Map<Long, String> displayNames,
      Map<String, Object> currentRoundTask) {
    LinkedHashMap<String, Object> round = new LinkedHashMap<>();
    round.put("roundNumber", Integer.valueOf(assignment.currentRound()));
    round.put("current", Boolean.TRUE);
    round.put("bye", assignment.entry().isBye());
    round.put("a1Name", sessionRoundRobinName(assignment.entry().getA1(), displayNames));
    round.put("a2Name", sessionRoundRobinName(assignment.entry().getA2(), displayNames));
    round.put("b1Name", sessionRoundRobinName(assignment.entry().getB1(), displayNames));
    round.put("b2Name", sessionRoundRobinName(assignment.entry().getB2(), displayNames));
    round.put("match", assignment.match());
    round.put("canConfirm", currentRoundTask.get("canConfirm"));
    round.put("readyToLog", currentRoundTask.get("readyToLog"));
    round.put("quickLogA1", currentRoundTask.get("quickLogA1"));
    round.put("quickLogA2", currentRoundTask.get("quickLogA2"));
    round.put("quickLogB1", currentRoundTask.get("quickLogB1"));
    round.put("quickLogB2", currentRoundTask.get("quickLogB2"));
    round.put(
        "status",
        sessionRoundRobinRoundStatus(
            assignment.currentRound(), assignment.currentRound(), assignment.entry(), assignment.match()));
    return round;
  }

  private com.w3llspring.fhpb.web.model.RoundRobinEntry findSessionRoundRobinEntryForUser(
      List<com.w3llspring.fhpb.web.model.RoundRobinEntry> entries, Long userId) {
    if (entries == null || userId == null) {
      return null;
    }
    for (com.w3llspring.fhpb.web.model.RoundRobinEntry entry : entries) {
      if (entry == null) {
        continue;
      }
      if (sameSessionRoundRobinUser(entry.getA1(), userId)
          || sameSessionRoundRobinUser(entry.getA2(), userId)
          || sameSessionRoundRobinUser(entry.getB1(), userId)
          || sameSessionRoundRobinUser(entry.getB2(), userId)) {
        return entry;
      }
    }
    return null;
  }

  private Match resolveSessionRoundRobinMatch(
      com.w3llspring.fhpb.web.model.RoundRobin roundRobin,
      com.w3llspring.fhpb.web.model.RoundRobinEntry entry) {
    if (entry == null || roundRobinService == null) {
      return null;
    }
    if (entry.getMatchId() != null) {
      Match linkedMatch = roundRobinService.getMatch(entry.getMatchId());
      if (linkedMatch != null) {
        return linkedMatch;
      }
    }
    return roundRobin != null
        ? roundRobinService.findLoggedMatchForEntry(roundRobin, entry, roundRobin.getCreatedAt()).orElse(null)
        : null;
  }

  private String sessionRoundRobinRoundStatus(
      int roundNumber,
      int currentRound,
      com.w3llspring.fhpb.web.model.RoundRobinEntry entry,
      Match linkedMatch) {
    if (entry == null) {
      return null;
    }
    if (entry.isBye()) {
      return "Bye";
    }
    if (linkedMatch != null && linkedMatch.getState() == com.w3llspring.fhpb.web.model.MatchState.CONFIRMED) {
      return roundRobinService.formatMatchResultForEntry(linkedMatch, entry);
    }
    if (linkedMatch != null) {
      return roundNumber == currentRound ? "Result logged" : "Logged";
    }
    return roundNumber < currentRound ? "No result logged" : (roundNumber == currentRound ? "Ready" : "Coming up");
  }

  private void collectSessionRoundRobinUserId(User user, java.util.Set<Long> userIds) {
    if (userIds == null || user == null || user.getId() == null) {
      return;
    }
    userIds.add(user.getId());
  }

  private String sessionRoundRobinName(User user, Map<Long, String> displayNames) {
    if (user == null || user.getId() == null) {
      return null;
    }
    return displayNames.get(user.getId());
  }

  private Long sessionRoundRobinQuickLogValue(Long currentUserId, com.w3llspring.fhpb.web.model.RoundRobinEntry entry, String slot) {
    if (entry == null) {
      return null;
    }
    User orderedA1 = entry.getA1();
    User orderedA2 = entry.getA2();
    User orderedB1 = entry.getB1();
    User orderedB2 = entry.getB2();
    if (currentUserId != null) {
      if (sameSessionRoundRobinUser(entry.getA1(), currentUserId)) {
        orderedA1 = entry.getA1();
        orderedA2 = entry.getA2();
        orderedB1 = entry.getB1();
        orderedB2 = entry.getB2();
      } else if (sameSessionRoundRobinUser(entry.getA2(), currentUserId)) {
        orderedA1 = entry.getA2();
        orderedA2 = entry.getA1();
        orderedB1 = entry.getB1();
        orderedB2 = entry.getB2();
      } else if (sameSessionRoundRobinUser(entry.getB1(), currentUserId)) {
        orderedA1 = entry.getB1();
        orderedA2 = entry.getB2();
        orderedB1 = entry.getA1();
        orderedB2 = entry.getA2();
      } else if (sameSessionRoundRobinUser(entry.getB2(), currentUserId)) {
        orderedA1 = entry.getB2();
        orderedA2 = entry.getB1();
        orderedB1 = entry.getA1();
        orderedB2 = entry.getA2();
      }
    }
    return switch (slot) {
      case "a1" -> orderedA1 != null ? orderedA1.getId() : null;
      case "a2" -> orderedA2 != null ? orderedA2.getId() : null;
      case "b1" -> orderedB1 != null ? orderedB1.getId() : null;
      case "b2" -> orderedB2 != null ? orderedB2.getId() : null;
      default -> null;
    };
  }

  private boolean sameSessionRoundRobinUser(User user, Long userId) {
    return user != null && user.getId() != null && user.getId().equals(userId);
  }

  private String resolveSessionStandingName(User user) {
    if (user == null) {
      return "Player";
    }
    String displayName = UserPublicName.forUser(user);
    return StringUtils.hasText(displayName) ? displayName : "Player";
  }

  @PostMapping("/{configId}/ban/{memberId}")
  public String ban(
      @PathVariable Long configId,
      @PathVariable Long memberId,
      Authentication auth,
      RedirectAttributes redirect) {
    User currentUser = getCurrentUser(auth);
    LadderConfig ladder =
        configs
            .findById(configId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    try {
      groupAdministration.banMember(configId, currentUser.getId(), memberId);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
    if (ladder.isSessionType()) {
      redirect.addFlashAttribute("toastMessage", "Player removed from the session.");
      redirect.addFlashAttribute("toastLevel", "light");
      return "redirect:/groups/" + configId;
    }
    return "redirect:/groups/" + configId + "?banned=1";
  }

  @PostMapping("/{configId}/remove/{memberId}")
  public String removeSessionMember(
      @PathVariable Long configId,
      @PathVariable Long memberId,
      Authentication auth,
      RedirectAttributes redirect) {
    User currentUser = getCurrentUser(auth);
    LadderConfig ladder =
        configs
            .findById(configId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!ladder.isSessionType()) {
      redirect.addFlashAttribute("toastMessage", "Only match sessions support removing players.");
      redirect.addFlashAttribute("toastLevel", "warning");
      return "redirect:/groups/" + configId;
    }
    try {
      groupAdministration.removeSessionMember(configId, currentUser.getId(), memberId);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
    redirect.addFlashAttribute("toastMessage", "Player removed from the session.");
    redirect.addFlashAttribute("toastLevel", "light");
    return "redirect:/groups/" + configId;
  }

  @PostMapping("/{configId}/unban/{memberId}")
  public String unban(
      @PathVariable Long configId, @PathVariable Long memberId, Authentication auth) {
    User currentUser = getCurrentUser(auth);
    try {
      groupAdministration.unbanMember(configId, currentUser.getId(), memberId);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
    return "redirect:/groups/" + configId + "?unbanned=1";
  }

  @PostMapping("/{configId}/leave/{memberId}")
  public String leave(
      @PathVariable Long configId,
      @PathVariable Long memberId,
      Authentication auth,
      RedirectAttributes redirect) {
    User currentUser = getCurrentUser(auth);
    if (currentUser == null) {
      return "redirect:/login";
    }
    LadderConfig ladder =
        configs
            .findById(configId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    try {
      groupAdministration.leaveMember(configId, currentUser.getId(), memberId);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
    if (ladder.isSessionType()) {
      boolean ownerLeaving = Objects.equals(ladder.getOwnerUserId(), currentUser.getId());
      redirect.addFlashAttribute("toastMessage", ownerLeaving ? "Session ended." : "You left the session.");
      redirect.addFlashAttribute("toastLevel", "light");
      return "redirect:/competition/sessions";
    }

    // Option A: flash attributes (preferred if your home page reads them)
    redirect.addFlashAttribute("toastMessage", "You left the group.");
    redirect.addFlashAttribute("toastLevel", "light"); // or "secondary"
    return "redirect:/";
  }

  @PostMapping("/{configId}/regen-invite")
  public String regenInvite(
      @PathVariable Long configId, Authentication auth, RedirectAttributes redirect) {
    User currentUser = getCurrentUser(auth);
    if (currentUser == null) {
      return "redirect:/login";
    }
    try {
      groupAdministration.regenInviteCode(configId, currentUser.getId());
      redirect.addFlashAttribute("toastMessage", "Invite regenerated.");
      redirect.addFlashAttribute("toastLevel", "light");
      return "redirect:/groups/" + configId + "?inviteRegenerated=1";
    } catch (InviteChangeCooldownException ex) {
      redirect.addFlashAttribute("toastMessage", ex.getMessage());
      redirect.addFlashAttribute("toastLevel", "warning");
      return "redirect:/groups/" + configId;
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    } catch (SecurityException ex) {
      redirect.addFlashAttribute(
          "toastMessage", "You’re not allowed to change invites for this ladder.");
      redirect.addFlashAttribute("toastLevel", "danger");
      return "redirect:/";
    }
  }

  @PostMapping("/{configId}/disable-invite")
  public String disableInvite(
      @PathVariable Long configId, Authentication auth, RedirectAttributes redirect) {
    User currentUser = getCurrentUser(auth);
    if (currentUser == null) {
      return "redirect:/login";
    }
    try {
      groupAdministration.disableInviteCode(configId, currentUser.getId());
      redirect.addFlashAttribute("toastMessage", "Invite disabled.");
      redirect.addFlashAttribute("toastLevel", "light");
      return "redirect:/groups/" + configId + "?inviteDisabled=1";
    } catch (InviteChangeCooldownException ex) {
      redirect.addFlashAttribute("toastMessage", ex.getMessage());
      redirect.addFlashAttribute("toastLevel", "warning");
      return "redirect:/groups/" + configId;
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    } catch (SecurityException ex) {
      redirect.addFlashAttribute(
          "toastMessage", "You’re not allowed to change invites for this ladder.");
      redirect.addFlashAttribute("toastLevel", "danger");
      return "redirect:/";
    }
  }

  @GetMapping("/join")
  public String joinForm(
      @RequestParam(name = "inviteCode", required = false) String inviteCode,
      @RequestParam(name = "returnTo", required = false) String returnTo,
      @RequestParam(name = "autoJoin", required = false, defaultValue = "false") boolean autoJoin,
      Authentication auth,
      Model model) {
    String normalizedReturnTo = normalizeGroupReturnTo(returnTo);
    model.addAttribute("returnToPath", normalizedReturnTo);
    model.addAttribute("sessionJoinContext", "/competition/sessions".equals(normalizedReturnTo));
    model.addAttribute("sessionApprovalMode", Boolean.FALSE);
    model.addAttribute("inviteTargetTitle", null);
    String flashedInviteCode = asString(model.asMap().get("prefillInviteCode"));
    populateSessionInvitePickerModel(
        model,
        inviteCode != null
            ? normalizeInviteCodeForLookup(inviteCode)
            : normalizeInviteCodeForLookup(flashedInviteCode));
    String effectiveInviteCode =
        inviteCode != null && !inviteCode.isBlank()
            ? inviteCode
            : flashedInviteCode;
    if (effectiveInviteCode == null || effectiveInviteCode.isBlank()) {
      return "auth/join";
    }

    String sanitizedInvite = normalizeInviteCodeForLookup(effectiveInviteCode);
    model.addAttribute("prefillInviteCode", effectiveInviteCode.trim());
    populateSessionInvitePickerModel(model, sanitizedInvite);
    if (sanitizedInvite == null || sanitizedInvite.isBlank()) {
      applyJoinFeedback(model, invalidInviteFeedback());
      return "auth/join";
    }

    // Check membership cap before attempting to join
    var cfgOpt = configs.findByInviteCode(sanitizedInvite);
    if (cfgOpt.isEmpty()) {
      applyJoinFeedback(model, invalidInviteFeedback());
      return "auth/join";
    }

    var cfg = cfgOpt.get();
    model.addAttribute("sessionApprovalMode", cfg.isSessionType() && !autoJoin);
    model.addAttribute(
        "inviteTargetTitle", cfg.isSessionType() ? resolveSessionDisplayTitle(cfg) : cfg.getTitle());
    if (autoJoin && cfg.isSessionType()) {
      User currentUser = getCurrentUser(auth);
      if (currentUser != null && currentUser.getId() != null) {
        try {
          LadderConfig joinedConfig =
              groupAdministration.joinByInvite(sanitizedInvite, currentUser.getId());
          return joinRedirectFor(joinedConfig);
        } catch (IllegalArgumentException ex) {
          applyJoinFeedback(model, invalidInviteFeedback());
          return "auth/join";
        } catch (IllegalStateException ex) {
          applyJoinFeedback(model, joinFailureFeedback(ex.getMessage()));
          return "auth/join";
        }
      }
    }
    long activeCount =
        membershipRepo
            .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                cfg.getId(), LadderMembership.State.ACTIVE)
            .size();
    if ((cfg.getType() == LadderConfig.Type.STANDARD || (cfg.isSessionType() && autoJoin))
        && activeCount >= defaultMaxMembers) {
      applyJoinFeedback(model, fullGroupFeedback());
      return "auth/join";
    }
    return "auth/join";
  }

  @PostMapping("/join")
  public String join(
      @RequestParam(name = "inviteCode", required = false) String inviteCode,
      @RequestParam(name = "inviteCodeWordOne", required = false) String inviteCodeWordOne,
      @RequestParam(name = "inviteCodeWordTwo", required = false) String inviteCodeWordTwo,
      @RequestParam(name = "inviteCodeNumber", required = false) String inviteCodeNumber,
      @RequestParam(name = "returnTo", required = false) String returnTo,
      Authentication auth,
      RedirectAttributes redirect) {
    User currentUser = getCurrentUser(auth);
    if (currentUser == null) {
      return "redirect:/login";
    }
    String normalizedReturnTo = normalizeGroupReturnTo(returnTo);
    try {
      String code =
          normalizeInviteCodeForLookup(
              resolveSubmittedInviteCode(
                  inviteCode, inviteCodeWordOne, inviteCodeWordTwo, inviteCodeNumber));
      if (code == null || code.isBlank()) {
        applyJoinFeedback(redirect, invalidInviteFeedback(), inviteCode);
        return redirectJoinForm(normalizedReturnTo);
      }
      var cfgOpt = configs.findByInviteCode(code);
      if (cfgOpt.isPresent()) {
        var cfg = cfgOpt.get();
        if (cfg.isSessionType() && sessionJoinRequestService != null) {
          SessionJoinRequestService.SubmissionOutcome outcome =
              sessionJoinRequestService.submitByInvite(code, currentUser.getId());
          if (outcome.state() == SessionJoinRequestService.SubmissionState.ALREADY_MEMBER) {
            return "redirect:/groups/" + outcome.sessionId() + "?joined=1";
          }
          return "redirect:/groups/join-requests/" + outcome.requestId();
        }
        long activeCount =
            membershipRepo
                .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                    cfg.getId(), LadderMembership.State.ACTIVE)
                .size();
        if (cfg.getType() == LadderConfig.Type.STANDARD && activeCount >= defaultMaxMembers) {
          applyJoinFeedback(redirect, fullGroupFeedback(), inviteCode);
          return redirectJoinForm(normalizedReturnTo);
        }
      }
      var cfg = groupAdministration.joinByInvite(code, currentUser.getId());
      return joinRedirectFor(cfg);
    } catch (IllegalArgumentException e) {
      // invalid invite code
      applyJoinFeedback(
          redirect,
          invalidInviteFeedback(),
          normalizeInviteCodeForLookup(
              resolveSubmittedInviteCode(
                  inviteCode, inviteCodeWordOne, inviteCodeWordTwo, inviteCodeNumber)));
      return redirectJoinForm(normalizedReturnTo);
    } catch (IllegalStateException e) {
      applyJoinFeedback(
          redirect,
          joinFailureFeedback(e.getMessage()),
          normalizeInviteCodeForLookup(
              resolveSubmittedInviteCode(
                  inviteCode, inviteCodeWordOne, inviteCodeWordTwo, inviteCodeNumber)));
      return redirectJoinForm(normalizedReturnTo);
    }
  }

  public String join(
      String inviteCode,
      String returnTo,
      Authentication auth,
      RedirectAttributes redirect) {
    return join(inviteCode, null, null, null, returnTo, auth, redirect);
  }

  @GetMapping("/join-requests/{requestId}")
  public String joinRequestStatusPage(
      @PathVariable("requestId") Long requestId, Authentication auth, Model model) {
    User currentUser = getCurrentUser(auth);
    if (currentUser == null) {
      return "redirect:/login";
    }
    if (sessionJoinRequestService == null) {
      return "redirect:/competition/sessions";
    }
    try {
      SessionJoinRequestService.RequestStatusView status =
          sessionJoinRequestService.getStatusForRequester(requestId, currentUser.getId());
      if (status.redirectUrl() != null) {
        return "redirect:" + status.redirectUrl();
      }
      model.addAttribute("joinRequestStatus", status);
      return "auth/session-join-request";
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    } catch (SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  private String joinRedirectFor(LadderConfig cfg) {
    if (cfg != null && cfg.isSessionType()) {
      return redirectToSession(cfg.getId(), true, SESSION_TOUR_JOINER);
    }
    if (cfg != null && cfg.getId() != null) {
      return "redirect:/private-groups/" + cfg.getId() + "?joined=1";
    }
    return "redirect:/private-groups";
  }

  @PostMapping("/session-tour-complete")
  @ResponseBody
  public ResponseEntity<Void> completeSessionTour(
      @RequestParam("tour") String tour, Authentication auth) {
    User currentUser = getCurrentUser(auth);
    if (currentUser == null || currentUser.getId() == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String markerKey = markerKeyForSessionTour(tour);
    if (markerKey == null || userOnboardingService == null) {
      return ResponseEntity.badRequest().build();
    }
    userOnboardingService.markCompleted(currentUser.getId(), markerKey);
    return ResponseEntity.noContent().build();
  }

  private String redirectToSession(Long configId, boolean joined, String tour) {
    if (configId == null) {
      return "redirect:/competition/sessions";
    }
    UriComponentsBuilder redirect = UriComponentsBuilder.fromPath("/groups/{configId}");
    if (joined) {
      redirect.queryParam("joined", 1);
    }
    if (StringUtils.hasText(tour)) {
      redirect.queryParam("tour", tour);
    }
    return "redirect:" + redirect.buildAndExpand(configId).encode().toUriString();
  }

  private String resolveSessionTourVariant(
      LadderConfig cfg, User currentUser, String requestedTour) {
    if (cfg == null
        || currentUser == null
        || currentUser.getId() == null
        || !cfg.isSessionType()
        || !StringUtils.hasText(requestedTour)
        || userOnboardingService == null) {
      return null;
    }

    if (SESSION_TOUR_OWNER.equalsIgnoreCase(requestedTour)
        && Objects.equals(cfg.getOwnerUserId(), currentUser.getId())
        && userOnboardingService.shouldShow(
            currentUser.getId(), UserOnboardingService.SESSION_OWNER_TOUR_V1)) {
      return SESSION_TOUR_OWNER;
    }

    if (SESSION_TOUR_JOINER.equalsIgnoreCase(requestedTour)
        && !Objects.equals(cfg.getOwnerUserId(), currentUser.getId())
        && userOnboardingService.shouldShow(
            currentUser.getId(), UserOnboardingService.SESSION_JOINER_TOUR_V1)) {
      return SESSION_TOUR_JOINER;
    }

    return null;
  }

  private String markerKeyForSessionTour(String tour) {
    if (SESSION_TOUR_OWNER.equalsIgnoreCase(tour)) {
      return UserOnboardingService.SESSION_OWNER_TOUR_V1;
    }
    if (SESSION_TOUR_JOINER.equalsIgnoreCase(tour)) {
      return UserOnboardingService.SESSION_JOINER_TOUR_V1;
    }
    return null;
  }

  private JoinFeedback invalidInviteFeedback() {
    return new JoinFeedback("Invalid invite code.", "danger");
  }

  private JoinFeedback fullGroupFeedback() {
    return new JoinFeedback("Sorry, that group is full.", "danger");
  }

  private JoinFeedback joinFailureFeedback(String message) {
    if ("You are banned from this ladder".equals(message)) {
      return new JoinFeedback(
          "You are banned from this ladder. Please contact the ladder admin.", "danger");
    }
    if ("That invite is no longer active.".equals(message)) {
      return new JoinFeedback("That session code is no longer active. Ask the host to re-enable it.", "warning");
    }
    if ("This match session has expired.".equals(message)) {
      return new JoinFeedback("This match session has expired.", "warning");
    }
    if ("Sorry, that group is full.".equals(message)) {
      return fullGroupFeedback();
    }
    return new JoinFeedback("Sorry, something went wrong. Please try again.", "danger");
  }

  private void applyJoinFeedback(Model model, JoinFeedback feedback) {
    if (model == null || feedback == null) {
      return;
    }
    model.addAttribute("toastMessage", feedback.message());
    model.addAttribute("toastLevel", feedback.level());
  }

  private void applyJoinFeedback(
      RedirectAttributes redirect, JoinFeedback feedback, String inviteCode) {
    if (redirect == null || feedback == null) {
      return;
    }
    redirect.addFlashAttribute("toastMessage", feedback.message());
    redirect.addFlashAttribute("toastLevel", feedback.level());
    redirect.addFlashAttribute("prefillInviteCode", inviteCode);
  }

  private User getCurrentUser(Authentication auth) {
    return AuthenticatedUserSupport.currentUser(auth);
  }

  private String normalizeGroupReturnTo(String returnTo) {
    if ("/private-groups".equals(returnTo)) {
      return "/private-groups";
    }
    if ("/competition/sessions".equals(returnTo)) {
      return "/competition/sessions";
    }
    return null;
  }

  private String redirectCreateForm(String returnTo, boolean tournamentMode) {
    StringBuilder redirect = new StringBuilder("redirect:/groups/new");
    boolean first = true;
    if (returnTo != null) {
      redirect.append(first ? '?' : '&').append("returnTo=").append(returnTo);
      first = false;
    }
    if (tournamentMode) {
      redirect.append(first ? '?' : '&').append("tournamentMode=true");
    }
    return redirect.toString();
  }

  private String redirectJoinForm(String returnTo) {
    if (returnTo != null) {
      return "redirect:/groups/join?returnTo=" + returnTo;
    }
    return "redirect:/groups/join";
  }

  private void populateSessionInvitePickerModel(Model model, String inviteCode) {
    if (model == null) {
      return;
    }
    model.addAttribute("inviteCodeWordOneOptions", SessionInviteCodeSupport.WORD_ONE_OPTIONS);
    model.addAttribute("inviteCodeWordTwoOptions", SessionInviteCodeSupport.WORD_TWO_OPTIONS);
    model.addAttribute("inviteCodeNumberOptions", SessionInviteCodeSupport.NUMBER_OPTIONS);
    SessionInviteCodeSupport.Parts parts = SessionInviteCodeSupport.split(inviteCode);
    model.addAttribute("prefillInviteCodeWordOne", parts != null ? parts.wordOne() : null);
    model.addAttribute("prefillInviteCodeWordTwo", parts != null ? parts.wordTwo() : null);
    model.addAttribute("prefillInviteCodeNumber", parts != null ? parts.number() : null);
  }

  private String resolveSubmittedInviteCode(
      String inviteCode, String inviteCodeWordOne, String inviteCodeWordTwo, String inviteCodeNumber) {
    String combined = SessionInviteCodeSupport.compose(inviteCodeWordOne, inviteCodeWordTwo, inviteCodeNumber);
    if (combined != null && !combined.isBlank()) {
      return combined;
    }
    return inviteCode;
  }

  private String normalizeInviteCodeForLookup(String inviteCode) {
    return SessionInviteCodeSupport.normalizeForLookup(inviteCode);
  }

  private String normalizeConfiguredPublicBaseUrl(String configured) {
    if (!StringUtils.hasText(configured)) {
      return null;
    }
    try {
      URI uri = URI.create(configured.trim());
      String scheme = uri.getScheme();
      if (!StringUtils.hasText(scheme)
          || !StringUtils.hasText(uri.getHost())
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
        return null;
      }
      String normalized = configured.trim();
      return normalized.endsWith("/")
          ? normalized.substring(0, normalized.length() - 1)
          : normalized;
    } catch (Exception ex) {
      return null;
    }
  }

  private boolean hasActiveInvite(LadderConfig cfg) {
    if (cfg == null || cfg.getInviteCode() == null || cfg.getInviteCode().isBlank()) {
      return false;
    }
    if (!cfg.isSessionType()) {
      return true;
    }
    return SessionInviteCodeSupport.isCurrentlyActive(
        cfg.getInviteCode(), cfg.getLastInviteChangeAt(), sessionInviteActiveSeconds, Instant.now());
  }

  private String asString(Object value) {
    return value instanceof String str ? str : null;
  }

  @PostMapping("/{configId}/restore")
  public String restore(
      @PathVariable Long configId, Authentication auth, RedirectAttributes redirect) {
    User currentUser = getCurrentUser(auth);
    if (currentUser == null) return "redirect:/login";

    try {
      groupAdministration.restorePendingDeletion(configId, currentUser.getId());
      redirect.addFlashAttribute("toastMessage", "Ladder restored.");
      redirect.addFlashAttribute("toastLevel", "light");

      // If user is still an active member, send to ladder; otherwise home.
      try {
        groupAdministration.requireActiveMember(configId, currentUser.getId());
        return "redirect:/groups/" + configId;
      } catch (SecurityException ignored) {
        return "redirect:/";
      }
    } catch (SecurityException ex) {
      redirect.addFlashAttribute("toastMessage", "You’re not allowed to restore this ladder.");
      redirect.addFlashAttribute("toastLevel", "danger");
      return "redirect:/";
    }
  }

  @PostMapping("/{configId}/promote/{memberId}")
  public String promote(
      @PathVariable Long configId,
      @PathVariable Long memberId,
      Authentication auth,
      RedirectAttributes redirect) {
    User currentUser = getCurrentUser(auth);
    try {
      groupAdministration.promoteToAdmin(configId, currentUser.getId(), memberId);
      return "redirect:/groups/" + configId + "?promoted=1";
    } catch (Exception e) {
      redirect.addFlashAttribute(
          "toastMessage", e.getMessage() != null ? e.getMessage() : "Unable to promote.");
      redirect.addFlashAttribute("toastLevel", "danger");
      return "redirect:/groups/" + configId;
    }
  }

  @PostMapping("/{configId}/demote/{memberId}")
  public String demote(
      @PathVariable Long configId,
      @PathVariable Long memberId,
      Authentication auth,
      RedirectAttributes redirect) {
    User currentUser = getCurrentUser(auth);
    try {
      groupAdministration.demoteFromAdmin(configId, currentUser.getId(), memberId);
      return "redirect:/groups/" + configId + "?demoted=1";
    } catch (Exception e) {
      redirect.addFlashAttribute(
          "toastMessage", e.getMessage() != null ? e.getMessage() : "Unable to demote.");
      redirect.addFlashAttribute("toastLevel", "danger");
      return "redirect:/groups/" + configId;
    }
  }

  private String defaultSeasonLabel(LocalDate startDate) {
    if (startDate == null) {
      return "Season";
    }
    return "Season • "
        + startDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"));
  }

  private String dateRange(LadderSeason season) {
    if (season == null || season.getStartDate() == null) {
      return "";
    }
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
    return startText + " - " + endText;
  }

  private boolean storyModeFeatureEnabled() {
    return storyModeService == null || storyModeService.isFeatureEnabled();
  }

  private String buildLeaveConfirmMessage(LadderConfig ladder, Long currentUserId) {
    boolean ownerViewing = ladder != null && Objects.equals(ladder.getOwnerUserId(), currentUserId);
    if (ladder != null && ladder.isSessionType()) {
      if (ownerViewing) {
        return "End this session? Everyone will be removed immediately.";
      }
      return "Leave this session? You can rejoin later with the current invite if it is still active.";
    }
    if (ownerViewing) {
      return "You are the owner. Leaving will schedule this group for deletion. Continue?";
    }
    return "Leave this group? You will need the current invite code to rejoin.";
  }

  private String buildLeaveActionLabel(LadderConfig ladder, Long currentUserId) {
    boolean ownerViewing = ladder != null && Objects.equals(ladder.getOwnerUserId(), currentUserId);
    if (ladder != null && ladder.isSessionType()) {
      return ownerViewing ? "End Session" : "Leave Session";
    }
    return "Leave Group";
  }

  private record JoinFeedback(String message, String level) {}

  private record SessionConfirmationState(
      List<LadderMatchLink> inboxLinks,
      List<LadderMatchLink> outboxLinks,
      Set<Long> inboxConfirmableMatchIds,
      Map<Long, Boolean> inboxNullifyApprovableByMatchId,
      Map<Long, Boolean> outboxWaitingOnOpponentByMatchId,
      Map<Long, Boolean> outboxNullifyWaitingOnOpponentByMatchId) {

    private static SessionConfirmationState empty() {
      return new SessionConfirmationState(List.of(), List.of(), Set.of(), Map.of(), Map.of(), Map.of());
    }

    private int inboxCount() {
      return inboxLinks.size();
    }

    private int outboxCount() {
      return outboxLinks.size();
    }

    private int totalCount() {
      return inboxCount() + outboxCount();
    }

    private boolean hasAny() {
      return totalCount() > 0;
    }
  }

  public record SessionRecentTickerItem(
      Long matchId, String ageLabel, String summary, Instant confirmedAt) {}

  private record SessionRecentTickerMatchTimeline(Long matchId, Instant confirmedAt) {}

  @PostMapping("/{ladderId}/title")
  @Transactional
  public String updateTitle(
      @PathVariable Long ladderId,
      @RequestParam("title") String title,
      Authentication auth,
      RedirectAttributes ra) {

    User currentUser = getCurrentUser(auth);
    Long requesterUserId = currentUser != null ? currentUser.getId() : null;
    try {
      groupAdministration.updateTitle(ladderId, requesterUserId, title);
    } catch (SecurityException ex) {
      ra.addFlashAttribute("toastMessage", "Admin required to rename group.");
      ra.addFlashAttribute("toastLevel", "danger");
      return "redirect:/groups/" + ladderId;
    } catch (IllegalStateException ex) {
      ra.addFlashAttribute("toastMessage", ex.getMessage());
      ra.addFlashAttribute("toastLevel", "warning");
      return "redirect:/competition";
    } catch (IllegalArgumentException ex) {
      ra.addFlashAttribute("toastMessage", ex.getMessage());
      ra.addFlashAttribute("toastLevel", "danger");
      return "redirect:/groups/" + ladderId;
    }

    ra.addFlashAttribute("toastMessage", "Group name updated.");
    ra.addFlashAttribute("toastLevel", "light");
    return "redirect:/groups/" + ladderId;
  }

  private static class LadderListRow {
    final Long id;
    final String title;
    final String role;

    LadderListRow(Long id, String title, String role) {
      this.id = id;
      this.title = title;
      this.role = role;
    }

    public Long getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public String getRole() {
      return role;
    }
  }

  private boolean isSiteWideAdmin(Authentication auth) {
    User user = getCurrentUser(auth);
    if (user == null) {
      return false;
    }
    return normalizedEmail(user.getEmail()).equals(normalizedEmail(siteWideAdminEmail));
  }

  private String normalizedEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
  }
}
