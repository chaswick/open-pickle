package com.w3llspring.fhpb.web.controller.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.UserDisplayNameAuditRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserDisplayNameAudit;
import com.w3llspring.fhpb.web.service.CompetitionDisplayNameModerationService;
import com.w3llspring.fhpb.web.service.CompetitionSeasonService;
import com.w3llspring.fhpb.web.service.InviteChangeCooldownException;
import com.w3llspring.fhpb.web.service.LadderConfigService;
import com.w3llspring.fhpb.web.service.LadderImprovementAdvisor;
import com.w3llspring.fhpb.web.service.MatchDashboardService;
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
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
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

  private final GroupAdministrationOperations groupAdministration;
  private final GroupCreationService groupCreationService;
  private final LadderConfigRepository configs;
  private final LadderSeasonRepository seasons;
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

  @Autowired
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
      LadderConfigService service,
      GroupAdministrationService groupAdministration,
      GroupCreationService groupCreationService,
      LadderConfigRepository configs,
      LadderSeasonRepository seasons,
      LadderMembershipRepository membershipRepo,
      StoryModeService storyModeService,
      @Value("${fhpb.ladder.max-members:20}") int defaultMaxMembers) {
    this(
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

    if (!(cfg.isCompetitionType() && currentUserIsSiteAdmin)) {
      try {
        groupAdministration.requireActiveMember(configId, currentUser.getId());
      } catch (SecurityException ex) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      }
    }

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
        competitionSeasonService != null ? competitionSeasonService.resolveTargetSeason(cfg) : null;
    model.addAttribute("targetSeason", targetSeason);
    model.addAttribute(
        "sessionTargetSeasonDateRange", targetSeason != null ? dateRange(targetSeason) : "");
    model.addAttribute(
        "sessionStandingsRecalculationPending",
        targetSeason != null && targetSeason.isStandingsRecalcInProgress());
    MatchDashboardService.DashboardModel sessionDashboard =
        matchDashboardViewService != null ? matchDashboardViewService.emptyDashboard() : null;
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
      applySessionStandingPreview(model, currentUser, targetSeason);
      List<com.w3llspring.fhpb.web.model.RoundRobinStanding> sessionReportStandings =
          roundRobinService != null ? roundRobinService.computeStandingsForSession(cfg) : List.of();
      model.addAttribute(
          "sessionReportStandings",
          sessionReportStandings != null ? sessionReportStandings : List.of());
      model.addAttribute(
          "improvementAdvice",
          improvementAdvisor != null
              ? improvementAdvisor.buildAdvice(currentUser, cfg, targetSeason)
              : null);
    }

    boolean currentUserIsAdmin =
        currentUserIsSiteAdmin
            || membershipRepo
                .findByLadderConfigIdAndUserId(configId, currentUser.getId())
                .filter(
                    m ->
                        m.getState() == LadderMembership.State.ACTIVE
                            && m.getRole() == LadderMembership.Role.ADMIN)
                .isPresent();
    model.addAttribute("currentUserIsAdmin", currentUserIsAdmin);
    model.addAttribute(
        "pendingSessionJoinRequests",
        cfg.isSessionType() && currentUserIsAdmin && sessionJoinRequestService != null
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
    var allActive =
        membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            configId, LadderMembership.State.ACTIVE);
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

  private void applySessionStandingPreview(
      Model model, User currentUser, LadderSeason targetSeason) {
    if (model == null
        || currentUser == null
        || currentUser.getId() == null
        || targetSeason == null
        || seasonStandingsViewService == null) {
      model.addAttribute("sessionStandingRow", null);
      return;
    }
    SeasonStandingsViewService.SeasonStandingsView standingsView =
        seasonStandingsViewService.load(targetSeason);
    if (standingsView.standings().isEmpty()) {
      model.addAttribute("sessionStandingRow", null);
      return;
    }
    var currentRow = seasonStandingsViewService.findRowForUser(standingsView, currentUser.getId());
    model.addAttribute("sessionStandingRow", currentRow);
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
      return "redirect:/home";
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
