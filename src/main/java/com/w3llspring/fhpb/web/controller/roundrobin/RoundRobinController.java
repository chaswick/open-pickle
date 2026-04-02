package com.w3llspring.fhpb.web.controller.roundrobin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.MatchWorkflowRules;
import com.w3llspring.fhpb.web.model.RoundRobin;
import com.w3llspring.fhpb.web.model.RoundRobinEntry;
import com.w3llspring.fhpb.web.model.RoundRobinStanding;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.push.PushNotificationService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinModificationException;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.session.UserSessionState;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoundRobinController {

  private final RoundRobinService roundRobinService;
  private final LadderAccessService ladderAccessService;
  private final LadderMembershipRepository ladderMembershipRepository;
  private final MatchConfirmationService matchConfirmationService;
  private final PushNotificationService pushNotificationService;
  private LadderConfigRepository ladderConfigRepository;

  @Autowired
  public RoundRobinController(
      RoundRobinService roundRobinService,
      LadderAccessService ladderAccessService,
      LadderMembershipRepository ladderMembershipRepository,
      MatchConfirmationService matchConfirmationService,
      LadderConfigRepository ladderConfigRepository,
      PushNotificationService pushNotificationService) {
    this.roundRobinService = roundRobinService;
    this.ladderAccessService = ladderAccessService;
    this.ladderMembershipRepository = ladderMembershipRepository;
    this.matchConfirmationService = matchConfirmationService;
    this.ladderConfigRepository = ladderConfigRepository;
    this.pushNotificationService = pushNotificationService;
  }

  public RoundRobinController(
      RoundRobinService roundRobinService,
      LadderAccessService ladderAccessService,
      LadderMembershipRepository ladderMembershipRepository,
      MatchConfirmationService matchConfirmationService,
      LadderConfigRepository ladderConfigRepository) {
    this(
        roundRobinService,
        ladderAccessService,
        ladderMembershipRepository,
        matchConfirmationService,
        ladderConfigRepository,
        null);
  }

  public RoundRobinController(
      RoundRobinService roundRobinService,
      LadderAccessService ladderAccessService,
      LadderMembershipRepository ladderMembershipRepository,
      MatchConfirmationService matchConfirmationService) {
    this(
        roundRobinService,
        ladderAccessService,
        ladderMembershipRepository,
        matchConfirmationService,
        null,
        null);
  }

  @GetMapping("/round-robin")
  public String showStart(
      @RequestParam(value = "ladderId", required = false) Long ladderId,
      HttpServletRequest request,
      Authentication auth,
      Model model) {
    Long requestedLadderId = ladderId;
    ladderId = UserSessionState.resolveSelectedGroupId(request, ladderId);
    User currentUser = resolveUser(auth);
    List<LadderMembership> myMemberships = activeMemberships(currentUser);
    if (requestedLadderId == null && isCompetitionLadderId(ladderId)) {
      ladderId = null;
    }
    if (ladderId == null) {
      ladderId = defaultLadderId(myMemberships);
    }
    if (isCompetitionLadderId(ladderId)) {
      return "redirect:/competition";
    }
    List<User> members = new ArrayList<>();
    LadderSeason season = null;
    LadderConfig selectedLadder = findLadderConfig(ladderId);
    if (ladderId != null) {
      if (!isSessionLadderId(ladderId) && !isCompetitionLadderId(ladderId)) {
        UserSessionState.storeSelectedGroupId(request, ladderId);
      }
      requireActiveLadderMember(ladderId, currentUser);
      if (!canStartRoundRobin(selectedLadder, currentUser)) {
        return "redirect:/round-robin/list?ladderId=" + ladderId;
      }
      members = roundRobinService.listMembersForLadder(ladderId);
      model.addAttribute("ladderId", ladderId);
      season = roundRobinService.findSeasonForLadder(ladderId);
    }
    populateStartViewModel(
        model,
        currentUser,
        ladderId,
        members,
        season,
        null,
        List.of(),
        null,
        RoundRobin.Format.ROTATING_PARTNERS.name(),
        "");
    return "roundrobin/start";
  }

  @PostMapping("/round-robin/start")
  public String start(
      @RequestParam(value = "ladderId", required = false) Long ladderId,
      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "participantIds", required = false) List<Long> participantIds,
      @RequestParam(value = "rounds", required = false) Integer rounds,
      @RequestParam(value = "format", required = false) String formatValue,
      @RequestParam(value = "fixedTeamsJson", required = false) String fixedTeamsJson,
      HttpServletRequest request,
      Model model,
      RedirectAttributes redirectAttributes,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          com.w3llspring.fhpb.web.model.CustomUserDetails principal) {
    Long requestedLadderId = ladderId;
    ladderId = UserSessionState.resolveSelectedGroupId(request, ladderId);
    if (requestedLadderId == null && isCompetitionLadderId(ladderId)) {
      ladderId = null;
    }
    if (participantIds == null) participantIds = new ArrayList<>();
    String roundRobinFormat = RoundRobin.Format.fromParam(formatValue).name();
    String submittedFixedTeamsJson = fixedTeamsJson != null ? fixedTeamsJson.trim() : "";

    // Enforce ladder context + authorization. This endpoint is mutating.
    User actor = resolveUser(principal);
    LadderConfig selectedLadder = findLadderConfig(ladderId);
    if (isCompetitionLadderId(ladderId)) {
      redirectAttributes.addFlashAttribute(
          "toastMessage", "Competition does not support round-robin management.");
      redirectAttributes.addFlashAttribute("toastLevel", "warning");
      return "redirect:/competition";
    }
    if (ladderId == null) {
      showStartError(model, "A ladderId is required to start a round-robin.");
      populateStartViewModel(
          model,
          actor,
          null,
          new ArrayList<>(),
          null,
          name,
          participantIds,
          rounds,
          roundRobinFormat,
          submittedFixedTeamsJson);
      return "roundrobin/start";
    }

    List<User> members = roundRobinService.listMembersForLadder(ladderId);
    LadderSeason season = roundRobinService.findSeasonForLadder(ladderId);
    java.util.Set<Long> memberIds = new java.util.HashSet<>();
    for (User u : members) {
      if (u != null && u.getId() != null) memberIds.add(u.getId());
    }
    boolean actorAllowed =
        actor != null
            && actor.getId() != null
            && (actor.isAdmin() || memberIds.contains(actor.getId()));
    if (!actorAllowed) {
      showStartError(model, "You must be a member of this ladder to start a round-robin.");
      populateStartViewModel(
          model,
          actor,
          ladderId,
          members,
          season,
          name,
          participantIds,
          rounds,
          roundRobinFormat,
          submittedFixedTeamsJson);
      return "roundrobin/start";
    }
    if (!canStartRoundRobin(selectedLadder, actor)) {
      redirectAttributes.addFlashAttribute(
          "toastMessage", roundRobinStartRestrictionMessage(selectedLadder));
      redirectAttributes.addFlashAttribute("toastLevel", "warning");
      return buildRoundRobinListRedirect(
          ladderId,
          season != null ? season.getId() : null,
          selectedLadder != null && selectedLadder.isSessionType());
    }

    // Validate participant IDs are ACTIVE ladder members (prevent forged requests).
    java.util.List<Long> invalidParticipants =
        participantIds.stream()
            .filter(java.util.Objects::nonNull)
            .filter(id -> !memberIds.contains(id))
            .distinct()
            .collect(java.util.stream.Collectors.toList());
    if (!invalidParticipants.isEmpty()) {
      showStartError(model, "Invalid participant selection. Please choose only ladder members.");
      populateStartViewModel(
          model,
          actor,
          ladderId,
          members,
          season,
          name,
          participantIds,
          rounds,
          roundRobinFormat,
          submittedFixedTeamsJson);
      return "roundrobin/start";
    }

    // De-dupe participant ids while preserving order
    participantIds =
        participantIds.stream()
            .filter(java.util.Objects::nonNull)
            .filter(id -> id > 0)
            .distinct()
            .collect(java.util.stream.Collectors.toList());

    // Enforce minimum participant count at controller level
    if (participantIds.size() < 4) {
      // When rendering the start page directly, use errorMessage so the alerts fragment
      // will render an inline error alert. Do not redirect here.
      showStartError(model, "Please select at least 4 players to start a round-robin.");
      populateStartViewModel(
          model,
          actor,
          ladderId,
          members,
          season,
          name,
          participantIds,
          rounds,
          roundRobinFormat,
          submittedFixedTeamsJson);
      return "roundrobin/start";
    }

    int r = rounds == null ? 0 : rounds.intValue();
    Long createdById = actor != null ? actor.getId() : null;
    RoundRobin.Format format = RoundRobin.Format.fromParam(roundRobinFormat);
    List<List<Long>> fixedTeams = List.of();
    if (format == RoundRobin.Format.FIXED_TEAMS) {
      try {
        fixedTeams = parseFixedTeamsJson(submittedFixedTeamsJson);
      } catch (RoundRobinModificationException ex) {
        showStartError(model, ex.getMessage());
        populateStartViewModel(
            model,
            actor,
            ladderId,
            members,
            season,
            name,
            participantIds,
            rounds,
            roundRobinFormat,
            submittedFixedTeamsJson);
        return "roundrobin/start";
      }
    }
    try {
      RoundRobin rr =
          roundRobinService.createAndStart(
              ladderId, name, participantIds, r, createdById, format, fixedTeams);
      // Use flash attributes so the redirected page can render a toast message via the centralized
      // fragment
      redirectAttributes.addFlashAttribute("toastMessage", "Round-robin started successfully.");
      redirectAttributes.addFlashAttribute("toastLevel", "success");
      notifySessionRoundRobinParticipants(rr);
      return "redirect:/round-robin/view/" + rr.getId();
    } catch (RoundRobinModificationException ex) {
      showStartError(model, ex.getMessage());
      populateStartViewModel(
          model,
          actor,
          ladderId,
          members,
          season,
          name,
          participantIds,
          rounds,
          roundRobinFormat,
          submittedFixedTeamsJson);
      return "roundrobin/start";
    }
  }

  @GetMapping("/round-robin/view/{id}")
  public String view(
      @PathVariable("id") Long id,
      @RequestParam(value = "round", required = false) Integer roundParam,
      Model model,
      RedirectAttributes redirectAttributes,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          com.w3llspring.fhpb.web.model.CustomUserDetails principal) {
    RoundRobin rr = roundRobinService.getRoundRobin(id);
    if (rr == null) {
      // Use flash attribute so home can show a toast
      redirectAttributes.addFlashAttribute("toastMessage", "Round-robin not found.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/home";
    }
    User currentUser = resolveUser(principal);
    boolean sessionOwned = rr.getSessionConfig() != null && rr.getSessionConfig().getId() != null;
    if (sessionOwned) {
      requireActiveLadderMember(rr.getSessionConfig().getId(), currentUser);
    } else {
      if (rr.getSeason() == null || rr.getSeason().getId() == null) {
        throw new SecurityException("Round-robin unavailable.");
      }
      ladderAccessService.requireMember(rr.getSeason().getId(), currentUser);
    }

    boolean canManage = canManageRoundRobin(rr, currentUser);

    int maxRound = roundRobinService.getMaxRound(id);
    int derivedCurrent = roundRobinService.resolveCurrentRound(rr);
    int current;
    if (roundParam != null) {
      current = clampRound(roundParam, maxRound);
    } else if (maxRound > 0) {
      current = Math.min(Math.max(derivedCurrent, 1), maxRound);
    } else {
      current = Math.max(derivedCurrent, 1);
    }
    var entries = roundRobinService.getEntriesForRound(id, current);

    List<RoundRobinStanding> standings = roundRobinService.computeStandings(id);

    boolean canAdvance = roundRobinService.canAdvance(id);
    model.addAttribute("maxRound", maxRound);
    boolean roundRobinComplete = maxRound > 0 && derivedCurrent > maxRound;

    // Collect user ids tied to this round
    java.util.Set<Long> entryUserIds = new java.util.HashSet<>();
    java.util.Set<Long> allParticipantIds = new java.util.HashSet<>();
    java.util.Set<Long> activePlayerIds = new java.util.HashSet<>();
    java.util.Set<Long> byePlayerIds = new java.util.HashSet<>();
    java.util.Set<Long> linkedMatchIds = new java.util.HashSet<>();
    if (entries != null) {
      for (var e : entries) {
        Long a1Id = e.getA1() != null ? e.getA1().getId() : null;
        Long a2Id = e.getA2() != null ? e.getA2().getId() : null;
        Long b1Id = e.getB1() != null ? e.getB1().getId() : null;
        Long b2Id = e.getB2() != null ? e.getB2().getId() : null;

        if (a1Id != null) {
          entryUserIds.add(a1Id);
          allParticipantIds.add(a1Id);
          if (e.isBye()) byePlayerIds.add(a1Id);
          else activePlayerIds.add(a1Id);
        }
        if (a2Id != null) {
          entryUserIds.add(a2Id);
          allParticipantIds.add(a2Id);
          if (e.isBye()) byePlayerIds.add(a2Id);
          else activePlayerIds.add(a2Id);
        }
        if (b1Id != null) {
          entryUserIds.add(b1Id);
          allParticipantIds.add(b1Id);
          if (e.isBye()) byePlayerIds.add(b1Id);
          else activePlayerIds.add(b1Id);
        }
        if (b2Id != null) {
          entryUserIds.add(b2Id);
          allParticipantIds.add(b2Id);
          if (e.isBye()) byePlayerIds.add(b2Id);
          else activePlayerIds.add(b2Id);
        }
        if (e.getMatchId() != null) {
          linkedMatchIds.add(e.getMatchId());
        }
      }
    }

    Long ladderId =
        sessionOwned
            ? rr.getSessionConfig().getId()
            : (rr.getSeason() != null && rr.getSeason().getLadderConfig() != null
                ? rr.getSeason().getLadderConfig().getId()
                : null);
    Long seasonId = rr.getSeason() != null ? rr.getSeason().getId() : null;
    List<User> members =
        ladderId != null ? roundRobinService.listMembersForLadder(ladderId) : new ArrayList<>();

    java.util.Set<Long> displayUserIds = new java.util.HashSet<>(entryUserIds);
    for (User member : members) {
      if (member != null && member.getId() != null) {
        displayUserIds.add(member.getId());
      }
    }
    java.util.Map<Long, String> displayNames =
        roundRobinService.buildDisplayNameMap(displayUserIds, ladderId);
    java.util.Map<Long, com.w3llspring.fhpb.web.model.Match> matchMap =
        roundRobinService.loadMatchesWithParticipants(linkedMatchIds);
    Set<Long> confirmableMatchIds = pendingMatchIdsFor(currentUser);

    // Build entry view models
    List<java.util.Map<String, Object>> entryViews = new ArrayList<>();
    int matchupNumber = 0;
    if (entries != null) {
      for (var e : entries) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("bye", e.isBye());
        m.put("entryId", e.getId());
        m.put("roundNumber", e.getRoundNumber());
        m.put("quickLogEnabled", false);
        m.put("editEnabled", false);
        m.put("confirmEnabled", false);

        Long a1Id = e.getA1() != null ? e.getA1().getId() : null;
        Long a2Id = e.getA2() != null ? e.getA2().getId() : null;
        Long b1Id = e.getB1() != null ? e.getB1().getId() : null;
        Long b2Id = e.getB2() != null ? e.getB2().getId() : null;

        m.put("a1Id", a1Id);
        m.put("a2Id", a2Id);
        m.put("b1Id", b1Id);
        m.put("b2Id", b2Id);
        m.put("a1Name", a1Id != null ? displayNames.get(a1Id) : null);
        m.put("a2Name", a2Id != null ? displayNames.get(a2Id) : null);
        m.put("b1Name", b1Id != null ? displayNames.get(b1Id) : null);
        m.put("b2Name", b2Id != null ? displayNames.get(b2Id) : null);

        Long matchId = e.getMatchId();
        m.put("matchId", matchId);

        com.w3llspring.fhpb.web.model.Match linked = matchId != null ? matchMap.get(matchId) : null;
        if (linked == null && matchId != null) {
          linked = roundRobinService.getMatch(matchId);
          if (linked != null) {
            matchMap.put(matchId, linked);
          }
        }

        String status;
        if (e.isBye()) {
          status = "Bye";
        } else if (linked == null) {
          var maybe = roundRobinService.findLoggedMatchForEntry(rr, e, rr.getCreatedAt());
          if (maybe.isPresent()) {
            var found = maybe.get();
            linked = found;
            roundRobinService.linkEntryToMatch(e, found.getId());
            matchMap.put(found.getId(), found);
            m.put("matchId", found.getId());
            if (found.getState() == com.w3llspring.fhpb.web.model.MatchState.CONFIRMED) {
              status = roundRobinService.formatMatchResultForEntry(found, e);
              m.put("result", status);
            } else {
              status = provisionalStatus(found, e);
            }
          } else {
            status = "Waiting";
          }
        } else {
          if (linked.getState() == com.w3llspring.fhpb.web.model.MatchState.CONFIRMED) {
            status = roundRobinService.formatMatchResultForEntry(linked, e);
            m.put("result", status);
          } else {
            status = provisionalStatus(linked, e);
          }
        }

        m.put("status", status);
        m.put("linkedMatch", linked);
        if (!e.isBye() && m.get("matchId") == null) {
          java.util.Map<String, String> quickLogSelections =
              buildQuickLogSelections(e, currentUser, canManage);
          if (quickLogSelections != null) {
            m.put("quickLogEnabled", true);
            m.put("quickLogA1", quickLogSelections.get("a1"));
            m.put("quickLogA2", quickLogSelections.get("a2"));
            m.put("quickLogB1", quickLogSelections.get("b1"));
            m.put("quickLogB2", quickLogSelections.get("b2"));
            m.put("returnRound", current);
          }
        }
        if (linked != null && canEditRoundRobinMatch(linked, currentUser)) {
          m.put("editEnabled", true);
          m.put("editMatchId", linked.getId());
          m.put("returnRound", current);
        }
        if (linked != null && canConfirmRoundRobinMatch(linked, currentUser, confirmableMatchIds)) {
          m.put("confirmEnabled", true);
        }
        if (!e.isBye()) {
          matchupNumber++;
          m.put("displayIndex", matchupNumber);
        }
        entryViews.add(m);
      }
    }

    // Build participant list
    List<java.util.Map<String, Object>> allParticipants = new ArrayList<>();
    if (!allParticipantIds.isEmpty()) {
      java.util.Map<Long, User> userMap = new java.util.HashMap<>();
      for (User u : members) {
        if (u != null && u.getId() != null) {
          userMap.put(u.getId(), u);
        }
      }

      for (Long playerId : allParticipantIds) {
        User user = userMap.get(playerId);
        if (user == null) continue;

        java.util.Map<String, Object> participant = new java.util.HashMap<>();
        participant.put("id", playerId);
        String name = displayNames.get(playerId);
        if (name == null) {
          name = roundRobinService.getDisplayNameForUser(user, ladderId);
        }
        participant.put("name", name);

        boolean inActiveMatch = activePlayerIds.contains(playerId);
        boolean inBye = byePlayerIds.contains(playerId);
        if (inActiveMatch && !inBye) {
          participant.put("status", "playing");
          participant.put("statusLabel", "Playing");
        } else if (!inActiveMatch && inBye) {
          participant.put("status", "bye");
          participant.put("statusLabel", "Bye");
        } else if (inActiveMatch && inBye) {
          participant.put("status", "playing");
          participant.put("statusLabel", "Playing");
        } else {
          participant.put("status", "unknown");
          participant.put("statusLabel", "Not Assigned");
        }
        allParticipants.add(participant);
      }

      allParticipants.sort(
          (a, b) -> {
            String nameA = (String) a.get("name");
            String nameB = (String) b.get("name");
            if (nameA == null || nameB == null) return 0;
            return nameA.compareToIgnoreCase(nameB);
          });
    }

    model.addAttribute("roundRobin", rr);
    model.addAttribute("roundRobinId", id);
    model.addAttribute("currentRound", current);
    model.addAttribute("roundComplete", isRoundComplete(entryViews));
    model.addAttribute("roundRobinComplete", roundRobinComplete);
    model.addAttribute(
        "standingsTitle", roundRobinComplete ? "Standings (Completed)" : "Standings");
    model.addAttribute("entries", entryViews);
    model.addAttribute("allParticipants", allParticipants);
    model.addAttribute("standings", standings);
    model.addAttribute("canAdvance", canAdvance);
    model.addAttribute("canEnd", canManage);
    model.addAttribute("canManage", canManage);

    // Deviation analysis UI was removed, so skip this computation
    // var deviationSummary = roundRobinService.analyzeDeviations(id);
    // model.addAttribute("deviationSummary", deviationSummary);

    if (canManage) {
      java.util.Map<Long, String> ladderDisplayNames = new java.util.HashMap<>();
      for (User u : members) {
        if (u == null || u.getId() == null) continue;
        String name = displayNames.get(u.getId());
        if (name == null) {
          name = roundRobinService.getDisplayNameForUser(u, ladderId);
        }
        ladderDisplayNames.put(u.getId(), name);
      }
      model.addAttribute("ladderMembers", members);
      model.addAttribute("ladderDisplayNames", ladderDisplayNames);
    }

    model.addAttribute("roundRobinLadderId", ladderId);
    model.addAttribute("roundRobinSeasonId", seasonId);
    model.addAttribute(
        "roundRobinListPath", buildRoundRobinListPath(ladderId, seasonId, sessionOwned));
    model.addAttribute("isSessionRoundRobin", sessionOwned);

    return "roundrobin/view";
  }

  @GetMapping("/round-robin/list")
  public String list(
      @RequestParam(value = "ladderId", required = false) Long ladderId,
      @RequestParam(value = "seasonId", required = false) Long seasonId,
      @RequestParam(value = "page", required = false, defaultValue = "0") int page,
      @RequestParam(value = "size", required = false, defaultValue = "10") int size,
      HttpServletRequest request,
      Authentication auth,
      Model model) {
    Long requestedLadderId = ladderId;
    ladderId = UserSessionState.resolveSelectedGroupId(request, ladderId);
    User currentUser = resolveUser(auth);
    List<LadderMembership> myMemberships = activeMemberships(currentUser);
    if (requestedLadderId == null && isCompetitionLadderId(ladderId)) {
      ladderId = null;
    }
    if (ladderId == null) {
      ladderId = defaultLadderId(myMemberships);
    }
    if (isCompetitionLadderId(ladderId)) {
      return "redirect:/competition";
    }

    LadderConfig selectedLadder = findLadderConfig(ladderId);
    boolean sessionLadder = selectedLadder != null && selectedLadder.isSessionType();
    LadderSeason selectedSeason = null;
    if (sessionLadder) {
      requireActiveLadderMember(ladderId, currentUser);
      selectedSeason = roundRobinService.findSeasonForLadder(ladderId);
      if (selectedSeason != null) {
        seasonId = selectedSeason.getId();
      }
    } else if (seasonId != null) {
      selectedSeason = ladderAccessService.requireSeason(seasonId);
      if (selectedSeason != null
          && selectedSeason.getLadderConfig() != null
          && selectedSeason.getLadderConfig().isCompetitionType()) {
        return "redirect:/competition";
      }
      ladderAccessService.requireMember(seasonId, currentUser);
      if (selectedSeason != null
          && selectedSeason.getLadderConfig() != null
          && selectedSeason.getLadderConfig().getId() != null) {
        ladderId = selectedSeason.getLadderConfig().getId();
      }
    } else if (ladderId != null) {
      requireActiveLadderMember(ladderId, currentUser);
      selectedSeason = roundRobinService.findSeasonForLadder(ladderId);
      if (selectedSeason != null) {
        seasonId = selectedSeason.getId();
      }
    }
    if (ladderId != null && !sessionLadder && !isCompetitionLadderId(ladderId)) {
      UserSessionState.storeSelectedGroupId(request, ladderId);
    }

    selectedLadder = findLadderConfig(ladderId);
    sessionLadder = selectedLadder != null && selectedLadder.isSessionType();
    boolean canStartRoundRobin = canStartRoundRobin(selectedLadder, currentUser);
    var rrPage = roundRobinService.listForLadderSeason(ladderId, seasonId, page, size);
    var rrs = rrPage.getContent();
    model.addAttribute("roundRobins", rrs);
    model.addAttribute("ladderId", ladderId);
    model.addAttribute("seasonId", seasonId);
    model.addAttribute("isSessionLadder", sessionLadder);
    model.addAttribute("canStartRoundRobin", canStartRoundRobin);
    model.addAttribute(
        "roundRobinAdminOnly", selectedLadder != null && selectedLadder.isTournamentMode());
    model.addAttribute(
        "roundRobinStartRestrictionMessage", roundRobinStartRestrictionMessage(selectedLadder));
    java.util.Map<Long, Integer> maxRounds = new java.util.HashMap<>();
    java.util.Map<Long, Integer> playerCounts = new java.util.HashMap<>();
    java.util.Map<Long, Integer> displayRounds = new java.util.HashMap<>();
    for (var rr : rrs) {
      if (rr != null && rr.getId() != null) {
        int maxRound = roundRobinService.getMaxRound(rr.getId());
        maxRounds.put(rr.getId(), maxRound);
        playerCounts.put(rr.getId(), roundRobinService.countParticipants(rr));
        int effectiveRound = roundRobinService.resolveCurrentRound(rr);
        displayRounds.put(
            rr.getId(), maxRound > 0 ? Math.min(effectiveRound, maxRound) : effectiveRound);
      }
    }
    model.addAttribute("maxRounds", maxRounds);
    model.addAttribute("playerCounts", playerCounts);
    model.addAttribute("displayRounds", displayRounds);
    model.addAttribute("pageNumber", rrPage.getNumber());
    model.addAttribute("totalPages", rrPage.getTotalPages());
    model.addAttribute("pageSize", rrPage.getSize());
    model.addAttribute("totalElements", rrPage.getTotalElements());
    var seasonStandings =
        sessionLadder
            ? roundRobinService.computeStandingsForSessionRoundRobins(selectedLadder)
            : roundRobinService.computeStandingsForSeason(ladderId, seasonId);
    model.addAttribute("seasonStandings", seasonStandings);
    model.addAttribute(
        "roundRobinBackPath", buildRoundRobinBackPath(ladderId, seasonId, sessionLadder));
    model.addAttribute(
        "roundRobinListPath", buildRoundRobinListPath(ladderId, seasonId, sessionLadder));
    populateLadderSelectorContext(model, myMemberships, ladderId, selectedSeason);
    return "roundrobin/list";
  }

  @PostMapping("/round-robin/advance/{id}")
  public String advance(
      @PathVariable("id") Long id,
      Model model,
      RedirectAttributes redirectAttributes,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          com.w3llspring.fhpb.web.model.CustomUserDetails principal) {
    RoundRobin rr = roundRobinService.getRoundRobin(id);
    User actor = resolveUser(principal);
    if (!canManageRoundRobin(rr, actor)) {
      redirectAttributes.addFlashAttribute(
          "toastMessage", "You are not authorized to advance this round-robin.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    boolean advanced = roundRobinService.advanceRoundIfReady(id);
    if (!advanced) {
      // Use flash attributes so the warning survives the redirect and is shown as a toast
      redirectAttributes.addFlashAttribute(
          "toastMessage",
          "Cannot advance round: not all matches confirmed or invalid round-robin.");
      redirectAttributes.addFlashAttribute("toastLevel", "warning");
    } else {
      notifySessionRoundRobinParticipants(roundRobinService.getRoundRobin(id));
    }
    return "redirect:/round-robin/view/" + id;
  }

  @PostMapping("/round-robin/end/{id}")
  public String endEarly(
      @PathVariable("id") Long id,
      Model model,
      RedirectAttributes redirectAttributes,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          com.w3llspring.fhpb.web.model.CustomUserDetails principal) {
    RoundRobin rr = roundRobinService.getRoundRobin(id);
    User actor = resolveUser(principal);
    if (!canManageRoundRobin(rr, actor)) {
      redirectAttributes.addFlashAttribute(
          "toastMessage", "You are not authorized to end this round-robin.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }

    boolean ended = roundRobinService.endRoundRobinEarly(id);
    if (!ended) {
      redirectAttributes.addFlashAttribute(
          "toastMessage", "Could not end round-robin: invalid id.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    redirectAttributes.addFlashAttribute("toastMessage", "Round-robin ended.");
    redirectAttributes.addFlashAttribute("toastLevel", "success");
    return "redirect:/round-robin/view/" + id;
  }

  @PostMapping("/round-robin/{id}/entry/{entryId}/substitute")
  public String substituteParticipants(
      @PathVariable("id") Long id,
      @PathVariable("entryId") Long entryId,
      @RequestParam(value = "a1Id", required = false) Long a1Id,
      @RequestParam(value = "a2Id", required = false) Long a2Id,
      @RequestParam(value = "b1Id", required = false) Long b1Id,
      @RequestParam(value = "b2Id", required = false) Long b2Id,
      RedirectAttributes redirectAttributes,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          com.w3llspring.fhpb.web.model.CustomUserDetails principal) {
    RoundRobin rr = roundRobinService.getRoundRobin(id);
    if (rr == null) {
      redirectAttributes.addFlashAttribute("toastMessage", "Round-robin not found.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    User actor = resolveUser(principal);
    if (!canManageRoundRobin(rr, actor)) {
      redirectAttributes.addFlashAttribute(
          "toastMessage", "You are not authorized to modify this round-robin.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    try {
      roundRobinService.updateEntryParticipants(id, entryId, a1Id, a2Id, b1Id, b2Id);
      redirectAttributes.addFlashAttribute("toastMessage", "Match participants updated.");
      redirectAttributes.addFlashAttribute("toastLevel", "success");
    } catch (RoundRobinModificationException ex) {
      redirectAttributes.addFlashAttribute("toastMessage", ex.getMessage());
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
    }
    return "redirect:/round-robin/view/" + id;
  }

  @PostMapping("/round-robin/{id}/entry/{entryId}/skip")
  public String skipMatch(
      @PathVariable("id") Long id,
      @PathVariable("entryId") Long entryId,
      @RequestParam("action") String action,
      @RequestParam(value = "winningScore", required = false, defaultValue = "11") int winningScore,
      RedirectAttributes redirectAttributes,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          com.w3llspring.fhpb.web.model.CustomUserDetails principal) {
    RoundRobin rr = roundRobinService.getRoundRobin(id);
    if (rr == null) {
      redirectAttributes.addFlashAttribute("toastMessage", "Round-robin not found.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    User actor = resolveUser(principal);
    if (!canManageRoundRobin(rr, actor)) {
      redirectAttributes.addFlashAttribute(
          "toastMessage", "You are not authorized to modify this round-robin.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    try {
      switch (action) {
        case "bye":
          roundRobinService.markEntryAsBye(id, entryId);
          redirectAttributes.addFlashAttribute("toastMessage", "Match marked as a bye.");
          redirectAttributes.addFlashAttribute("toastLevel", "info");
          break;
        case "forfeitA":
          roundRobinService.recordForfeit(
              id,
              entryId,
              RoundRobinService.ForfeitWinner.TEAM_A,
              winningScore,
              actor != null ? actor.getId() : null);
          redirectAttributes.addFlashAttribute(
              "toastMessage", "Team A awarded the win by forfeit.");
          redirectAttributes.addFlashAttribute("toastLevel", "success");
          break;
        case "forfeitB":
          roundRobinService.recordForfeit(
              id,
              entryId,
              RoundRobinService.ForfeitWinner.TEAM_B,
              winningScore,
              actor != null ? actor.getId() : null);
          redirectAttributes.addFlashAttribute(
              "toastMessage", "Team B awarded the win by forfeit.");
          redirectAttributes.addFlashAttribute("toastLevel", "success");
          break;
        default:
          redirectAttributes.addFlashAttribute("toastMessage", "Invalid skip action.");
          redirectAttributes.addFlashAttribute("toastLevel", "danger");
          break;
      }
    } catch (RoundRobinModificationException ex) {
      redirectAttributes.addFlashAttribute("toastMessage", ex.getMessage());
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
    }
    return "redirect:/round-robin/view/" + id;
  }

  @PostMapping("/round-robin/{id}/round/add-entry")
  public String addMatch(
      @PathVariable("id") Long id,
      @RequestParam(value = "a1Id", required = false) Long a1Id,
      @RequestParam(value = "a2Id", required = false) Long a2Id,
      @RequestParam(value = "b1Id", required = false) Long b1Id,
      @RequestParam(value = "b2Id", required = false) Long b2Id,
      RedirectAttributes redirectAttributes,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          com.w3llspring.fhpb.web.model.CustomUserDetails principal) {
    RoundRobin rr = roundRobinService.getRoundRobin(id);
    if (rr == null) {
      redirectAttributes.addFlashAttribute("toastMessage", "Round-robin not found.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    User actor = resolveUser(principal);
    if (!canManageRoundRobin(rr, actor)) {
      redirectAttributes.addFlashAttribute(
          "toastMessage", "You are not authorized to modify this round-robin.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    try {
      int roundNumber = rr.getCurrentRound();
      roundRobinService.createAdditionalEntry(id, roundNumber, a1Id, a2Id, b1Id, b2Id);
      redirectAttributes.addFlashAttribute(
          "toastMessage", "Additional matchup added to round " + roundNumber + ".");
      redirectAttributes.addFlashAttribute("toastLevel", "success");
    } catch (RoundRobinModificationException ex) {
      redirectAttributes.addFlashAttribute("toastMessage", ex.getMessage());
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
    }
    return "redirect:/round-robin/view/" + id;
  }

  @PostMapping("/round-robin/{id}/round/regenerate")
  public String regenerateRound(
      @PathVariable("id") Long id,
      @RequestParam(value = "forcedByeIds", required = false) List<Long> forcedByeIds,
      @RequestParam(value = "roundNumber", required = false) Integer roundNumber,
      RedirectAttributes redirectAttributes,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          com.w3llspring.fhpb.web.model.CustomUserDetails principal) {
    RoundRobin rr = roundRobinService.getRoundRobin(id);
    if (rr == null) {
      redirectAttributes.addFlashAttribute("toastMessage", "Round-robin not found.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    User actor = resolveUser(principal);
    if (!canManageRoundRobin(rr, actor)) {
      redirectAttributes.addFlashAttribute(
          "toastMessage", "You are not authorized to modify this round-robin.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    try {
      int targetRound = roundNumber == null ? rr.getCurrentRound() : roundNumber.intValue();
      List<User> byePlayers =
          roundRobinService.regenerateRoundWithForcedByes(id, targetRound, forcedByeIds);
      String byeSummary =
          byePlayers.isEmpty()
              ? "None"
              : byePlayers.stream()
                  .map(
                      u ->
                          roundRobinService.getDisplayNameForUser(
                              u,
                              rr.getSessionConfig() != null
                                  ? rr.getSessionConfig().getId()
                                  : (rr.getSeason() != null
                                          && rr.getSeason().getLadderConfig() != null
                                      ? rr.getSeason().getLadderConfig().getId()
                                      : null)))
                  .collect(Collectors.joining(", "));
      redirectAttributes.addFlashAttribute(
          "toastMessage", "Round " + targetRound + " regenerated. Byes: " + byeSummary + ".");
      redirectAttributes.addFlashAttribute("toastLevel", "success");
    } catch (RoundRobinModificationException ex) {
      redirectAttributes.addFlashAttribute("toastMessage", ex.getMessage());
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
    }
    return "redirect:/round-robin/view/" + id;
  }

  @PostMapping("/round-robin/{id}/rebalance")
  public String rebalanceFutureRounds(
      @PathVariable("id") Long id,
      RedirectAttributes redirectAttributes,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          com.w3llspring.fhpb.web.model.CustomUserDetails principal) {
    RoundRobin rr = roundRobinService.getRoundRobin(id);
    if (rr == null) {
      redirectAttributes.addFlashAttribute("toastMessage", "Round-robin not found.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    User actor = resolveUser(principal);
    if (!canManageRoundRobin(rr, actor)) {
      redirectAttributes.addFlashAttribute(
          "toastMessage", "You are not authorized to modify this round-robin.");
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
      return "redirect:/round-robin/view/" + id;
    }
    try {
      int roundsRegenerated = roundRobinService.rebalanceFutureRounds(id);
      redirectAttributes.addFlashAttribute(
          "toastMessage",
          "Regenerated " + roundsRegenerated + " future round(s) to balance participation.");
      redirectAttributes.addFlashAttribute("toastLevel", "success");
    } catch (RoundRobinModificationException ex) {
      redirectAttributes.addFlashAttribute("toastMessage", ex.getMessage());
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
    } catch (Exception ex) {
      redirectAttributes.addFlashAttribute(
          "toastMessage", "Error rebalancing rounds: " + ex.getMessage());
      redirectAttributes.addFlashAttribute("toastLevel", "danger");
    }
    return "redirect:/round-robin/view/" + id;
  }

  private void requireActiveLadderMember(Long ladderId, User user) {
    if (ladderId == null) {
      return;
    }
    if (user == null || user.getId() == null) {
      throw new SecurityException("Round-robin unavailable.");
    }
    LadderMembership membership =
        ladderMembershipRepository
            .findByLadderConfigIdAndUserId(ladderId, user.getId())
            .orElse(null);
    if (membership == null || membership.getState() != LadderMembership.State.ACTIVE) {
      throw new SecurityException("Round-robin unavailable.");
    }
  }

  private User resolveUser(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails principal)) {
      return null;
    }
    return resolveUser(principal);
  }

  private User resolveUser(CustomUserDetails principal) {
    return principal == null ? null : AuthenticatedUserSupport.refresh(principal.getUserObject());
  }

  private boolean canManageRoundRobin(RoundRobin rr, User user) {
    if (rr == null || user == null) return false;
    if (user.isAdmin()) return true;
    LadderConfig ladderConfig = ladderConfigForRoundRobin(rr);
    if (ladderConfig != null && ladderConfig.isTournamentMode()) {
      return isActiveAdmin(ladderConfig, user);
    }
    return rr.getCreatedBy() != null
        && rr.getCreatedBy().getId() != null
        && user.getId() != null
        && rr.getCreatedBy().getId().equals(user.getId());
  }

  private boolean canStartRoundRobin(LadderConfig ladderConfig, User user) {
    if (ladderConfig != null && ladderConfig.isSessionType()) {
      return canStartSessionRoundRobin(ladderConfig, user);
    }
    if (ladderConfig == null || !ladderConfig.isTournamentMode()) {
      return true;
    }
    return isActiveAdmin(ladderConfig, user);
  }

  private boolean canStartSessionRoundRobin(LadderConfig ladderConfig, User user) {
    if (ladderConfig == null || !ladderConfig.isSessionType()) {
      return true;
    }
    if (user == null || user.getId() == null) {
      return false;
    }
    return user.isAdmin() || Objects.equals(ladderConfig.getOwnerUserId(), user.getId());
  }

  private String roundRobinStartRestrictionMessage(LadderConfig ladderConfig) {
    if (ladderConfig == null) {
      return null;
    }
    if (ladderConfig.isSessionType()) {
      return "Only the session starter can start round-robins from this session.";
    }
    if (ladderConfig.isTournamentMode()) {
      return "Tournament mode requires league admins to start and manage round-robins. Matches logged for this season must match an active round-robin pairing.";
    }
    return null;
  }

  private boolean isActiveAdmin(LadderConfig ladderConfig, User user) {
    if (ladderConfig == null
        || ladderConfig.getId() == null
        || user == null
        || user.getId() == null) {
      return false;
    }
    if (user.isAdmin()) {
      return true;
    }
    LadderMembership membership =
        ladderMembershipRepository
            .findByLadderConfigIdAndUserId(ladderConfig.getId(), user.getId())
            .orElse(null);
    return membership != null
        && membership.getState() == LadderMembership.State.ACTIVE
        && membership.getRole() == LadderMembership.Role.ADMIN;
  }

  private LadderConfig ladderConfigForRoundRobin(RoundRobin rr) {
    if (rr == null) {
      return null;
    }
    if (rr.getSessionConfig() != null) {
      return rr.getSessionConfig();
    }
    if (rr.getSeason() != null) {
      return rr.getSeason().getLadderConfig();
    }
    return null;
  }

  private String buildRoundRobinListRedirect(Long ladderId, Long seasonId, boolean sessionLadder) {
    if (ladderId == null) {
      return "redirect:/round-robin/list";
    }
    if (sessionLadder || seasonId == null) {
      return "redirect:/round-robin/list?ladderId=" + ladderId;
    }
    return "redirect:/round-robin/list?ladderId=" + ladderId + "&seasonId=" + seasonId;
  }

  private java.util.Map<String, String> buildQuickLogSelections(
      RoundRobinEntry entry, User currentUser, boolean canManage) {
    if (entry == null || entry.isBye()) {
      return null;
    }

    if (currentUser != null && currentUser.getId() != null) {
      Long currentUserId = currentUser.getId();
      if (sameUser(entry.getA1(), currentUserId)) {
        return quickLogSelections(entry.getA1(), entry.getA2(), entry.getB1(), entry.getB2());
      }
      if (sameUser(entry.getA2(), currentUserId)) {
        return quickLogSelections(entry.getA2(), entry.getA1(), entry.getB1(), entry.getB2());
      }
      if (sameUser(entry.getB1(), currentUserId)) {
        return quickLogSelections(entry.getB1(), entry.getB2(), entry.getA1(), entry.getA2());
      }
      if (sameUser(entry.getB2(), currentUserId)) {
        return quickLogSelections(entry.getB2(), entry.getB1(), entry.getA1(), entry.getA2());
      }
    }

    if (canManage) {
      return quickLogSelections(entry.getA1(), entry.getA2(), entry.getB1(), entry.getB2());
    }

    return null;
  }

  private java.util.Map<String, String> quickLogSelections(User a1, User a2, User b1, User b2) {
    java.util.Map<String, String> values = new java.util.HashMap<>();
    values.put("a1", playerValue(a1));
    values.put("a2", playerValue(a2));
    values.put("b1", playerValue(b1));
    values.put("b2", playerValue(b2));
    return values;
  }

  private boolean sameUser(User candidate, Long userId) {
    return candidate != null && candidate.getId() != null && candidate.getId().equals(userId);
  }

  private String playerValue(User user) {
    return (user != null && user.getId() != null) ? String.valueOf(user.getId()) : "guest";
  }

  private boolean canEditRoundRobinMatch(com.w3llspring.fhpb.web.model.Match match, User user) {
    if (match == null || user == null) {
      return false;
    }
    if (user.isAdmin()) {
      return true;
    }
    boolean isSeasonAdmin = false;
    if (match.getSeason() != null && match.getSeason().getId() != null) {
      try {
        isSeasonAdmin = ladderAccessService.isSeasonAdmin(match.getSeason().getId(), user);
      } catch (Exception ex) {
        isSeasonAdmin = false;
      }
    }
    if (isSeasonAdmin) {
      return true;
    }
    return MatchWorkflowRules.canEdit(match, user, false);
  }

  private Set<Long> pendingMatchIdsFor(User user) {
    if (user == null || user.getId() == null || matchConfirmationService == null) {
      return Set.of();
    }
    List<MatchConfirmation> pending = matchConfirmationService.pendingForUser(user.getId());
    if (pending == null || pending.isEmpty()) {
      return Set.of();
    }
    Set<Long> ids = new HashSet<>();
    for (MatchConfirmation confirmation : pending) {
      if (confirmation == null
          || confirmation.getMatch() == null
          || confirmation.getMatch().getId() == null) {
        continue;
      }
      ids.add(confirmation.getMatch().getId());
    }
    return ids;
  }

  private boolean canConfirmRoundRobinMatch(Match match, User user, Set<Long> confirmableMatchIds) {
    if (match == null || user == null || user.getId() == null) {
      return false;
    }
    if (match.getId() != null
        && confirmableMatchIds != null
        && confirmableMatchIds.contains(match.getId())) {
      return true;
    }
    if (match.isConfirmationLocked() || match.getState() != MatchState.PROVISIONAL) {
      return false;
    }
    if (match.getSeason() == null || match.getSeason().getLadderConfig() == null) {
      return false;
    }
    if (!LadderSecurity.normalize(match.getSeason().getLadderConfig().getSecurityLevel())
        .isSelfConfirm()) {
      return false;
    }
    if (match.getLoggedBy() == null
        || match.getLoggedBy().getId() == null
        || !Objects.equals(match.getLoggedBy().getId(), user.getId())) {
      return false;
    }
    return user.isAdmin() || isSeasonAdmin(match, user);
  }

  private boolean isSeasonAdmin(Match match, User user) {
    if (match == null
        || user == null
        || ladderAccessService == null
        || match.getSeason() == null
        || match.getSeason().getId() == null) {
      return false;
    }
    try {
      return ladderAccessService.isSeasonAdmin(match.getSeason().getId(), user);
    } catch (Exception ex) {
      return false;
    }
  }

  private int clampRound(int round, int maxRound) {
    if (maxRound <= 0) {
      return Math.max(1, round);
    }
    return Math.max(1, Math.min(round, maxRound));
  }

  private void showStartError(Model model, String message) {
    model.addAttribute("toastMessage", message);
    model.addAttribute("toastLevel", "danger");
    model.addAttribute("toastAuto", Boolean.TRUE);
  }

  private List<List<Long>> parseFixedTeamsJson(String fixedTeamsJson) {
    if (!StringUtils.hasText(fixedTeamsJson)) {
      throw new RoundRobinModificationException(
          "Please assign the selected players into fixed teams.");
    }
    try {
      return new ObjectMapper().readValue(fixedTeamsJson, new TypeReference<List<List<Long>>>() {});
    } catch (Exception ex) {
      throw new RoundRobinModificationException(
          "Please assign the selected players into fixed teams.");
    }
  }

  private void populateStartViewModel(
      Model model,
      User actor,
      Long ladderId,
      List<User> members,
      LadderSeason season,
      String name,
      List<Long> participantIds,
      Integer rounds,
      String roundRobinFormat,
      String fixedTeamsJson) {
    LadderConfig ladderConfig = findLadderConfig(ladderId);
    List<User> safeMembers = members != null ? members : List.of();
    java.util.Map<Long, String> displayNames = new java.util.HashMap<>();
    java.util.List<Long> memberIds = new java.util.ArrayList<>();
    for (User user : safeMembers) {
      if (user == null || user.getId() == null) {
        continue;
      }
      memberIds.add(user.getId());
      displayNames.put(user.getId(), roundRobinService.getDisplayNameForUser(user, ladderId));
    }
    java.util.Map<Long, String> courtNames =
        roundRobinService.buildCourtNameMap(memberIds, ladderId);

    populateLadderSelectorContext(model, activeMemberships(actor), ladderId, season);
    model.addAttribute(
        "roundRobinListPath",
        buildRoundRobinListPath(
            ladderId, season != null ? season.getId() : null, isSessionLadderId(ladderId)));
    model.addAttribute("canStartRoundRobin", canStartRoundRobin(ladderConfig, actor));
    model.addAttribute(
        "roundRobinAdminOnly", ladderConfig != null && ladderConfig.isTournamentMode());
    model.addAttribute(
        "roundRobinStartRestrictionMessage", roundRobinStartRestrictionMessage(ladderConfig));
    model.addAttribute("displayNames", displayNames);
    model.addAttribute("courtNames", courtNames);
    model.addAttribute("members", safeMembers);
    model.addAttribute(
        "selectedParticipantIds", participantIds == null ? List.of() : List.copyOf(participantIds));
    model.addAttribute(
        "roundRobinFormat",
        StringUtils.hasText(roundRobinFormat)
            ? roundRobinFormat
            : RoundRobin.Format.ROTATING_PARTNERS.name());
    model.addAttribute("fixedTeamsJson", fixedTeamsJson != null ? fixedTeamsJson : "");
    model.addAttribute("rounds", rounds);
    model.addAttribute("name", name);
  }

  private List<LadderMembership> activeMemberships(User user) {
    if (user == null || user.getId() == null) {
      return List.of();
    }
    return ladderMembershipRepository.findByUserIdAndState(
        user.getId(), LadderMembership.State.ACTIVE);
  }

  private Long defaultLadderId(List<LadderMembership> memberships) {
    Long preferred =
        selectorMemberships(memberships, null).stream()
            .map(LadderMembership::getLadderConfig)
            .filter(Objects::nonNull)
            .map(LadderConfig::getId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    if (preferred != null) {
      return preferred;
    }
    if (memberships == null || memberships.isEmpty()) {
      return null;
    }
    return memberships.stream()
        .map(LadderMembership::getLadderConfig)
        .filter(Objects::nonNull)
        .filter(config -> !config.isCompetitionType() && !config.isSessionType())
        .map(LadderConfig::getId)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private void populateLadderSelectorContext(
      Model model, List<LadderMembership> memberships, Long ladderId, LadderSeason season) {
    model.addAttribute("myMemberships", memberships == null ? List.of() : memberships);
    model.addAttribute("selectorMemberships", selectorMemberships(memberships, ladderId));
    model.addAttribute("ladderId", ladderId);
    model.addAttribute("seasonId", season != null ? season.getId() : null);
    model.addAttribute(
        "seasonName", season != null && season.getName() != null ? season.getName() : "");
    model.addAttribute("seasonDateRange", seasonDateRange(season));
  }

  private List<LadderMembership> selectorMemberships(
      List<LadderMembership> memberships, Long selectedLadderId) {
    if (memberships == null || memberships.isEmpty()) {
      return List.of();
    }
    return memberships.stream()
        .filter(Objects::nonNull)
        .filter(
            membership -> {
              LadderConfig config = membership.getLadderConfig();
              if (config == null) {
                return false;
              }
              if (config.isCompetitionType()) {
                return false;
              }
              if (!config.isSessionType()) {
                return true;
              }
              return Objects.equals(config.getId(), selectedLadderId);
            })
        .collect(Collectors.toList());
  }

  private boolean isCompetitionLadderId(Long ladderId) {
    if (ladderId == null || ladderConfigRepository == null) {
      return false;
    }
    Optional<LadderConfig> ladderConfig = ladderConfigRepository.findById(ladderId);
    return ladderConfig != null && ladderConfig.map(LadderConfig::isCompetitionType).orElse(false);
  }

  private boolean isSessionLadderId(Long ladderId) {
    if (ladderId == null || ladderConfigRepository == null) {
      return false;
    }
    Optional<LadderConfig> ladderConfig = ladderConfigRepository.findById(ladderId);
    return ladderConfig != null && ladderConfig.map(LadderConfig::isSessionType).orElse(false);
  }

  private LadderConfig findLadderConfig(Long ladderId) {
    if (ladderId == null || ladderConfigRepository == null) {
      return null;
    }
    Optional<LadderConfig> ladderConfig = ladderConfigRepository.findById(ladderId);
    return ladderConfig == null ? null : ladderConfig.orElse(null);
  }

  private String buildRoundRobinListPath(Long ladderId, Long seasonId, boolean sessionLadder) {
    if (ladderId == null) {
      return "/home";
    }
    if (sessionLadder) {
      return "/round-robin/list?ladderId=" + ladderId;
    }
    if (seasonId != null) {
      return "/round-robin/list?ladderId=" + ladderId + "&seasonId=" + seasonId;
    }
    return "/round-robin/list?ladderId=" + ladderId;
  }

  private String buildRoundRobinBackPath(Long ladderId, Long seasonId, boolean sessionLadder) {
    if (ladderId == null) {
      return "/private-groups";
    }
    if (sessionLadder) {
      return "/groups/" + ladderId;
    }
    if (seasonId != null) {
      return "/private-groups/" + ladderId + "?seasonId=" + seasonId;
    }
    return "/private-groups/" + ladderId;
  }

  private void notifySessionRoundRobinParticipants(RoundRobin rr) {
    if (pushNotificationService == null
        || rr == null
        || rr.getId() == null
        || rr.getSessionConfig() == null
        || rr.getSessionConfig().getId() == null) {
      return;
    }
    int currentRound = roundRobinService.resolveCurrentRound(rr);
    int maxRound = roundRobinService.getMaxRound(rr.getId());
    if (maxRound <= 0 || currentRound > maxRound) {
      return;
    }

    List<RoundRobinEntry> entries = roundRobinService.getEntriesForRound(rr.getId(), currentRound);
    if (entries == null || entries.isEmpty()) {
      return;
    }

    java.util.LinkedHashSet<Long> participantIds = new java.util.LinkedHashSet<>();
    for (RoundRobinEntry entry : entries) {
      collectNotificationUserId(entry != null ? entry.getA1() : null, participantIds);
      collectNotificationUserId(entry != null ? entry.getA2() : null, participantIds);
      collectNotificationUserId(entry != null ? entry.getB1() : null, participantIds);
      collectNotificationUserId(entry != null ? entry.getB2() : null, participantIds);
    }

    for (Long participantId : participantIds) {
      pushNotificationService.sendRoundRobinReady(
          participantId, rr.getSessionConfig().getId(), rr.getSessionConfig().getTitle(), currentRound);
    }
  }

  private void collectNotificationUserId(User user, java.util.Set<Long> participantIds) {
    if (participantIds == null || user == null || user.getId() == null) {
      return;
    }
    participantIds.add(user.getId());
  }

  private String seasonDateRange(LadderSeason season) {
    if (season == null || season.getStartDate() == null) {
      return "";
    }
    java.time.LocalDate start = season.getStartDate();
    java.time.LocalDate end = season.getEndDate();
    java.time.format.DateTimeFormatter formatter =
        java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy");
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

  private boolean isRoundComplete(List<java.util.Map<String, Object>> entryViews) {
    if (entryViews == null || entryViews.isEmpty()) {
      return false;
    }
    boolean sawPlayableMatch = false;
    for (java.util.Map<String, Object> entry : entryViews) {
      if (entry == null || Boolean.TRUE.equals(entry.get("bye"))) {
        continue;
      }
      sawPlayableMatch = true;
      Match linked = (Match) entry.get("linkedMatch");
      if (linked == null || linked.getState() != MatchState.CONFIRMED) {
        return false;
      }
    }
    return sawPlayableMatch;
  }

  private String provisionalStatus(Match match, RoundRobinEntry entry) {
    if (match == null || entry == null) {
      return "Needs confirmation";
    }
    String score = roundRobinService.formatMatchResultForEntry(match, entry);
    if (score == null || score.isBlank()) {
      return "Needs confirmation";
    }
    return score + " (Needs confirmation)";
  }
}
