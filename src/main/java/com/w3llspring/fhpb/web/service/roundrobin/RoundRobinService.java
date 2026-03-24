package com.w3llspring.fhpb.web.service.roundrobin;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.RoundRobinEntryRepository;
import com.w3llspring.fhpb.web.db.RoundRobinRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.*;
import com.w3llspring.fhpb.web.service.trophy.TrophyBadgeSupport;
import com.w3llspring.fhpb.web.service.trophy.TrophyBadgeViewService;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoundRobinService {

  private static final Logger log = LoggerFactory.getLogger(RoundRobinService.class);
  private static final ZoneId LADDER_ZONE = ZoneId.of("America/New_York");

  private final LadderMembershipRepository membershipRepo;
  private final UserRepository userRepo;
  private final LadderSeasonRepository seasonRepo;
  private final MatchRepository matchRepo;
  private final RoundRobinRepository rrRepo;
  private final RoundRobinEntryRepository rrEntryRepo;
  private final CourtNameService courtNameService;
  private final RoundRobinScheduler roundRobinScheduler;
  private LadderConfigRepository ladderConfigRepo;
  private TrophyBadgeViewService trophyBadgeViewService;

  @Autowired
  public RoundRobinService(
      LadderMembershipRepository membershipRepo,
      UserRepository userRepo,
      LadderSeasonRepository seasonRepo,
      MatchRepository matchRepo,
      RoundRobinRepository rrRepo,
      RoundRobinEntryRepository rrEntryRepo,
      CourtNameService courtNameService,
      RoundRobinScheduler roundRobinScheduler,
      LadderConfigRepository ladderConfigRepo,
      TrophyBadgeViewService trophyBadgeViewService) {
    this.membershipRepo = membershipRepo;
    this.userRepo = userRepo;
    this.seasonRepo = seasonRepo;
    this.matchRepo = matchRepo;
    this.rrRepo = rrRepo;
    this.rrEntryRepo = rrEntryRepo;
    this.courtNameService = courtNameService;
    this.roundRobinScheduler = roundRobinScheduler;
    this.ladderConfigRepo = ladderConfigRepo;
    this.trophyBadgeViewService = trophyBadgeViewService;
  }

  public RoundRobinService(
      LadderMembershipRepository membershipRepo,
      UserRepository userRepo,
      LadderSeasonRepository seasonRepo,
      MatchRepository matchRepo,
      RoundRobinRepository rrRepo,
      RoundRobinEntryRepository rrEntryRepo,
      CourtNameService courtNameService,
      RoundRobinScheduler roundRobinScheduler) {
    this(
        membershipRepo,
        userRepo,
        seasonRepo,
        matchRepo,
        rrRepo,
        rrEntryRepo,
        courtNameService,
        roundRobinScheduler,
        null,
        null);
  }

  /**
   * Backwards-compatible constructor for tests or callers that do not provide a scheduler. It will
   * default to the built-in DefaultRoundRobinScheduler behavior.
   */
  public RoundRobinService(
      LadderMembershipRepository membershipRepo,
      UserRepository userRepo,
      LadderSeasonRepository seasonRepo,
      MatchRepository matchRepo,
      RoundRobinRepository rrRepo,
      RoundRobinEntryRepository rrEntryRepo,
      CourtNameService courtNameService) {
    this(
        membershipRepo,
        userRepo,
        seasonRepo,
        matchRepo,
        rrRepo,
        rrEntryRepo,
        courtNameService,
        new DefaultRoundRobinScheduler());
  }

  public List<User> listMembersForLadder(Long ladderConfigId) {
    if (ladderConfigId == null) return Collections.emptyList();
    List<LadderMembership> members =
        membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            ladderConfigId, LadderMembership.State.ACTIVE);
    if (members.isEmpty()) return Collections.emptyList();

    java.util.LinkedHashSet<Long> userIds = new java.util.LinkedHashSet<>();
    for (LadderMembership membership : members) {
      if (membership.getUserId() != null) {
        userIds.add(membership.getUserId());
      }
    }
    if (userIds.isEmpty()) return Collections.emptyList();

    java.util.Map<Long, User> usersById = new java.util.HashMap<>();
    for (User user : userRepo.findAllById(userIds)) {
      if (user != null && user.getId() != null) {
        usersById.put(user.getId(), user);
      }
    }

    List<User> users = new ArrayList<>(userIds.size());
    for (Long uid : userIds) {
      User u = usersById.get(uid);
      if (u != null) {
        users.add(u);
      }
    }
    return users;
  }

  /** Find the active or most recent season for a ladder. Returns null if none found. */
  public LadderSeason findSeasonForLadder(Long ladderConfigId) {
    if (ladderConfigId == null) return null;
    LadderConfig ladderConfig = resolveLadderConfig(ladderConfigId);
    if (ladderConfig != null && ladderConfig.isSessionType()) {
      return resolveTargetSeasonForSession(ladderConfig);
    }
    Optional<LadderSeason> seasonOpt = seasonRepo.findActive(ladderConfigId);
    if (seasonOpt.isPresent()) return seasonOpt.get();
    return seasonRepo.findTopByLadderConfigIdOrderByStartDateDesc(ladderConfigId).orElse(null);
  }

  @Transactional
  public RoundRobin createAndStart(
      Long ladderConfigId, String name, List<Long> participantIds, int rounds, Long createdById) {
    return createAndStart(
        ladderConfigId,
        name,
        participantIds,
        rounds,
        createdById,
        RoundRobin.Format.ROTATING_PARTNERS,
        List.of());
  }

  @Transactional
  public RoundRobin createAndStart(
      Long ladderConfigId,
      String name,
      List<Long> participantIds,
      int rounds,
      Long createdById,
      RoundRobin.Format format,
      List<List<Long>> fixedTeams) {
    LadderConfig ownerConfig = resolveLadderConfig(ladderConfigId);
    LadderSeason season;
    if (ownerConfig != null && ownerConfig.isSessionType()) {
      season = resolveTargetSeasonForSession(ownerConfig);
      if (season == null) {
        throw new RoundRobinModificationException(
            "Session does not have an active target season for round-robin play.");
      }
    } else {
      Optional<LadderSeason> seasonOpt = seasonRepo.findActive(ladderConfigId);
      if (seasonOpt.isEmpty()) {
        throw new RoundRobinModificationException(
            "Round-robin can only be started during an active season.");
      }
      season = seasonOpt.get();
    }
    if (!isSeasonOpenToday(season)) {
      throw new RoundRobinModificationException(
          "Round-robin can only be started during an active season.");
    }

    Long availabilityLadderConfigId =
        ownerConfig != null && ownerConfig.getId() != null ? ownerConfig.getId() : ladderConfigId;
    if (availabilityLadderConfigId == null && season != null && season.getLadderConfig() != null) {
      availabilityLadderConfigId = season.getLadderConfig().getId();
    }
    RoundRobin.Format resolvedFormat =
        format != null ? format : RoundRobin.Format.ROTATING_PARTNERS;
    List<Long> normalizedParticipants =
        participantIds == null
            ? List.of()
            : participantIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    List<List<Long>> normalizedFixedTeams =
        resolvedFormat == RoundRobin.Format.FIXED_TEAMS
            ? normalizeFixedTeams(normalizedParticipants, fixedTeams)
            : List.of();
    List<Long> scheduledParticipantIds =
        resolvedFormat == RoundRobin.Format.FIXED_TEAMS
            ? normalizedFixedTeams.stream().flatMap(List::stream).toList()
            : normalizedParticipants;

    assertPlayersAvailableForRoundRobin(availabilityLadderConfigId, scheduledParticipantIds, null);

    RoundRobin rr = new RoundRobin();
    // If caller didn't supply a creator id, try to infer from the SecurityContext (convenience for
    // web callers).
    if (createdById == null) {
      try {
        User currentUser = AuthenticatedUserSupport.currentUser();
        if (currentUser != null) createdById = currentUser.getId();
      } catch (Exception ex) {
        // ignore — security may not be present in non-web contexts
      }
    }

    if (createdById != null) {
      userRepo.findById(createdById).ifPresent(rr::setCreatedBy);
    }
    rr.setSeason(season);
    rr.setSessionConfig(ownerConfig != null && ownerConfig.isSessionType() ? ownerConfig : null);
    rr.setFormat(resolvedFormat);
    // Use start datetime as the round-robin name (e.g. "2025-10-26 15:04")
    java.time.format.DateTimeFormatter fmt =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    String baseName = java.time.LocalDateTime.now().format(fmt);
    // Append creator display name when available (formatted as CourtName-nickName)
    String creatorLabel = null;
    if (rr.getCreatedBy() != null) {
      try {
        creatorLabel = getDisplayNameForUser(rr.getCreatedBy(), availabilityLadderConfigId);
      } catch (Exception ex) {
        // ignore and leave creatorLabel null
      }
    }
    if (creatorLabel != null && !creatorLabel.isBlank()) {
      rr.setName(baseName + ", created by " + creatorLabel);
    } else {
      rr.setName(baseName);
    }
    rr = rrRepo.save(rr);

    // Build priorPairCounts from confirmed matches that were part of any round-robin
    // in the current season. Only confirmed matches linked from RoundRobinEntry should count.
    java.util.Map<Long, java.util.Map<Long, Integer>> priorPairCounts = new java.util.HashMap<>();
    if (season != null) {
      List<RoundRobin> rrs = findRoundRobinsForOwnershipContext(season, ownerConfig);
      java.util.Set<Long> linkedMatchIds = new java.util.HashSet<>();
      for (RoundRobin r : rrs) {
        List<RoundRobinEntry> entries = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(r);
        for (RoundRobinEntry e : entries) {
          if (e.getMatchId() != null) linkedMatchIds.add(e.getMatchId());
        }
      }
      if (!linkedMatchIds.isEmpty()) {
        Iterable<com.w3llspring.fhpb.web.model.Match> matches =
            matchRepo.findAllById(linkedMatchIds);
        for (com.w3llspring.fhpb.web.model.Match m : matches) {
          if (m.getState() == com.w3llspring.fhpb.web.model.MatchState.CONFIRMED) {
            // increment counts between all players across teams
            java.util.List<Long> teamA = new java.util.ArrayList<>();
            if (m.getA1() != null && m.getA1().getId() != null) teamA.add(m.getA1().getId());
            if (m.getA2() != null && m.getA2().getId() != null) teamA.add(m.getA2().getId());
            java.util.List<Long> teamB = new java.util.ArrayList<>();
            if (m.getB1() != null && m.getB1().getId() != null) teamB.add(m.getB1().getId());
            if (m.getB2() != null && m.getB2().getId() != null) teamB.add(m.getB2().getId());
            for (Long a : teamA) {
              for (Long b : teamB) {
                if (a == null || b == null) continue;
                priorPairCounts
                    .computeIfAbsent(a, k -> new java.util.HashMap<>())
                    .merge(b, 1, Integer::sum);
                priorPairCounts
                    .computeIfAbsent(b, k -> new java.util.HashMap<>())
                    .merge(a, 1, Integer::sum);
              }
            }
          }
        }
      }
    }

    List<List<RoundRobinScheduler.MatchSpec>> schedule;
    if (resolvedFormat == RoundRobin.Format.FIXED_TEAMS) {
      schedule = buildFixedTeamSchedule(normalizedFixedTeams);
    } else {
      // Delegate schedule generation to a pluggable scheduler so algorithms can be swapped
      RoundRobinScheduler.GenerationResult generation =
          roundRobinScheduler.generateWithExplanation(
              normalizedParticipants, priorPairCounts, rounds);
      schedule = generation.schedule == null ? List.of() : generation.schedule;
      if (generation.explanations != null && !generation.explanations.isEmpty()) {
        for (String msg : generation.explanations) {
          log.debug("Round-robin scheduler: {}", msg);
        }
      }
    }

    // Persist schedule entries
    for (int round = 0; round < schedule.size(); round++) {
      int roundNumber = round + 1;
      List<RoundRobinScheduler.MatchSpec> specs = schedule.get(round);
      for (RoundRobinScheduler.MatchSpec spec : specs) {
        RoundRobinEntry entry = new RoundRobinEntry();
        entry.setRoundRobin(rr);
        entry.setRoundNumber(roundNumber);
        entry.setBye(spec.bye);
        if (spec.a1 != null) userRepo.findById(spec.a1).ifPresent(entry::setA1);
        if (spec.a2 != null) userRepo.findById(spec.a2).ifPresent(entry::setA2);
        if (spec.b1 != null) userRepo.findById(spec.b1).ifPresent(entry::setB1);
        if (spec.b2 != null) userRepo.findById(spec.b2).ifPresent(entry::setB2);
        rrEntryRepo.save(entry);
      }
    }

    return rr;
  }

  private List<List<Long>> normalizeFixedTeams(
      List<Long> participantIds, List<List<Long>> fixedTeams) {
    java.util.LinkedHashSet<Long> selectedIds =
        new java.util.LinkedHashSet<>(participantIds == null ? List.of() : participantIds);
    if (selectedIds.size() < 4) {
      throw new RoundRobinModificationException(
          "Please select at least 4 players to start a round-robin.");
    }
    if (selectedIds.size() % 2 != 0) {
      throw new RoundRobinModificationException(
          "Fixed teams require an even number of players so everyone can be paired.");
    }
    if (fixedTeams == null || fixedTeams.isEmpty()) {
      throw new RoundRobinModificationException(
          "Please assign the selected players into fixed teams.");
    }

    List<List<Long>> normalized = new ArrayList<>();
    java.util.LinkedHashSet<Long> assignedIds = new java.util.LinkedHashSet<>();
    for (List<Long> rawTeam : fixedTeams) {
      if (rawTeam == null || rawTeam.isEmpty()) {
        continue;
      }
      List<Long> teamIds =
          rawTeam.stream()
              .filter(java.util.Objects::nonNull)
              .filter(id -> id > 0)
              .distinct()
              .toList();
      if (teamIds.isEmpty()) {
        continue;
      }
      if (teamIds.size() != 2) {
        throw new RoundRobinModificationException(
            "Fixed teams require exactly two players per team.");
      }
      normalized.add(teamIds);
      for (Long teamId : teamIds) {
        if (!assignedIds.add(teamId)) {
          throw new RoundRobinModificationException(
              "A player was assigned to more than one fixed team.");
        }
      }
    }

    if (normalized.size() < 2) {
      throw new RoundRobinModificationException("Please create at least two fixed teams.");
    }
    if (!assignedIds.equals(selectedIds)) {
      throw new RoundRobinModificationException(
          "Fixed teams must include each selected player exactly once.");
    }
    return normalized;
  }

  private List<List<RoundRobinScheduler.MatchSpec>> buildFixedTeamSchedule(
      List<List<Long>> fixedTeams) {
    if (fixedTeams == null || fixedTeams.size() < 2) {
      throw new RoundRobinModificationException("Please create at least two fixed teams.");
    }

    List<List<Long>> rotation =
        fixedTeams.stream().map(ArrayList::new).collect(Collectors.toCollection(ArrayList::new));
    if (rotation.size() % 2 == 1) {
      rotation.add(null);
    }

    int teamCount = rotation.size();
    int rounds = Math.max(0, teamCount - 1);
    List<List<RoundRobinScheduler.MatchSpec>> schedule = new ArrayList<>(rounds);

    for (int round = 0; round < rounds; round++) {
      List<RoundRobinScheduler.MatchSpec> roundSpecs = new ArrayList<>();
      for (int i = 0; i < teamCount / 2; i++) {
        List<Long> teamA = rotation.get(i);
        List<Long> teamB = rotation.get(teamCount - 1 - i);
        if (teamA == null && teamB == null) {
          continue;
        }
        if (teamA == null) {
          roundSpecs.add(toMatchSpec(teamB, null, true));
        } else if (teamB == null) {
          roundSpecs.add(toMatchSpec(teamA, null, true));
        } else {
          roundSpecs.add(toMatchSpec(teamA, teamB, false));
        }
      }
      schedule.add(roundSpecs);

      List<List<Long>> nextRotation = new ArrayList<>(teamCount);
      nextRotation.add(rotation.get(0));
      nextRotation.add(rotation.get(teamCount - 1));
      for (int idx = 1; idx < teamCount - 1; idx++) {
        nextRotation.add(rotation.get(idx));
      }
      rotation = nextRotation;
    }

    return schedule;
  }

  private RoundRobinScheduler.MatchSpec toMatchSpec(
      List<Long> teamA, List<Long> teamB, boolean bye) {
    RoundRobinScheduler.MatchSpec spec = new RoundRobinScheduler.MatchSpec();
    if (teamA != null && !teamA.isEmpty()) {
      spec.a1 = teamA.get(0);
      if (teamA.size() > 1) {
        spec.a2 = teamA.get(1);
      }
    }
    if (teamB != null && !teamB.isEmpty()) {
      spec.b1 = teamB.get(0);
      if (teamB.size() > 1) {
        spec.b2 = teamB.get(1);
      }
    }
    spec.bye = bye;
    return spec;
  }

  private boolean isSeasonOpenToday(LadderSeason season) {
    if (season == null) {
      return false;
    }
    LocalDate today = LocalDate.now(LADDER_ZONE);
    LocalDate start = season.getStartDate();
    LocalDate end = season.getEndDate();
    if (start != null && today.isBefore(start)) {
      return false;
    }
    if (end != null && today.isAfter(end)) {
      return false;
    }
    return true;
  }

  @Transactional
  public RoundRobinEntry updateEntryParticipants(
      Long rrId, Long entryId, Long a1Id, Long a2Id, Long b1Id, Long b2Id) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) {
      throw new RoundRobinModificationException("Round-robin not found.");
    }

    RoundRobinEntry entry =
        rrEntryRepo
            .findById(entryId)
            .orElseThrow(() -> new RoundRobinModificationException("Match entry not found."));
    if (entry.getRoundRobin() == null
        || entry.getRoundRobin().getId() == null
        || !entry.getRoundRobin().getId().equals(rr.getId())) {
      throw new RoundRobinModificationException("Match entry does not belong to this round-robin.");
    }
    if (entry.getMatchId() != null) {
      throw new RoundRobinModificationException(
          "Cannot substitute players after a match has been logged.");
    }

    var ladderConfig = resolveLadderConfig(rr);
    validateTeamSize(a1Id, a2Id, "Team A");
    validateTeamSize(b1Id, b2Id, "Team B");
    ensureDistinctPlayers(a1Id, a2Id, b1Id, b2Id);

    User a1 = resolveParticipant(a1Id, ladderConfig);
    User a2 = resolveParticipant(a2Id, ladderConfig);
    User b1 = resolveParticipant(b1Id, ladderConfig);
    User b2 = resolveParticipant(b2Id, ladderConfig);

    Set<Long> playerIds = collectIds(a1, a2, b1, b2);
    validatePlayersAvailable(rr, entry.getId(), entry.getRoundNumber(), playerIds);
    Long ladderConfigId =
        ladderConfig != null && ladderConfig.getId() != null ? ladderConfig.getId() : null;
    assertPlayersAvailableForRoundRobin(ladderConfigId, playerIds, rr.getId());

    entry.setA1(a1);
    entry.setA2(a2);
    entry.setB1(b1);
    entry.setB2(b2);
    entry.setBye(false);
    entry.setMatchId(null);
    return rrEntryRepo.save(entry);
  }

  @Transactional
  public RoundRobinEntry markEntryAsBye(Long rrId, Long entryId) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) {
      throw new RoundRobinModificationException("Round-robin not found.");
    }
    RoundRobinEntry entry =
        rrEntryRepo
            .findById(entryId)
            .orElseThrow(() -> new RoundRobinModificationException("Match entry not found."));
    if (entry.getRoundRobin() == null
        || entry.getRoundRobin().getId() == null
        || !entry.getRoundRobin().getId().equals(rr.getId())) {
      throw new RoundRobinModificationException("Match entry does not belong to this round-robin.");
    }
    if (entry.getMatchId() != null) {
      throw new RoundRobinModificationException(
          "Cannot skip a match that already has a logged result.");
    }
    entry.setBye(true);
    entry.setMatchId(null);
    return rrEntryRepo.save(entry);
  }

  public enum ForfeitWinner {
    TEAM_A,
    TEAM_B
  }

  @Transactional
  public RoundRobinEntry recordForfeit(
      Long rrId, Long entryId, ForfeitWinner winner, int winningScore, Long actorUserId) {
    if (winner == null) {
      throw new RoundRobinModificationException("Winner must be specified for a forfeit.");
    }
    if (winningScore < 1) {
      throw new RoundRobinModificationException("Winning score must be positive.");
    }
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) {
      throw new RoundRobinModificationException("Round-robin not found.");
    }
    RoundRobinEntry entry =
        rrEntryRepo
            .findById(entryId)
            .orElseThrow(() -> new RoundRobinModificationException("Match entry not found."));
    if (entry.getRoundRobin() == null
        || entry.getRoundRobin().getId() == null
        || !entry.getRoundRobin().getId().equals(rr.getId())) {
      throw new RoundRobinModificationException("Match entry does not belong to this round-robin.");
    }
    if (entry.getMatchId() != null) {
      throw new RoundRobinModificationException(
          "Cannot forfeit a match that already has a logged result.");
    }

    if (entry.getA1() == null && entry.getA2() == null) {
      throw new RoundRobinModificationException("Team A does not have any players assigned.");
    }
    if (entry.getB1() == null && entry.getB2() == null) {
      throw new RoundRobinModificationException("Team B does not have any players assigned.");
    }

    Match match = new Match();
    match.setSeason(rr.getSeason());
    match.setSourceSessionConfig(rr.getSessionConfig());
    match.setA1(entry.getA1());
    match.setA2(entry.getA2());
    match.setB1(entry.getB1());
    match.setB2(entry.getB2());
    if (winner == ForfeitWinner.TEAM_A) {
      match.setScoreA(winningScore);
      match.setScoreB(0);
    } else {
      match.setScoreA(0);
      match.setScoreB(winningScore);
    }
    match.setScoreEstimated(true);
    match.setState(MatchState.CONFIRMED);
    match.setConfirmationLocked(true);
    if (actorUserId != null) {
      userRepo.findById(actorUserId).ifPresent(match::setLoggedBy);
    }
    match = matchRepo.save(match);

    entry.setMatchId(match.getId());
    entry.setBye(false);
    return rrEntryRepo.save(entry);
  }

  @Transactional
  public RoundRobinEntry createAdditionalEntry(
      Long rrId, int roundNumber, Long a1Id, Long a2Id, Long b1Id, Long b2Id) {
    if (roundNumber < 1) {
      throw new RoundRobinModificationException("Round number must be at least 1.");
    }
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) {
      throw new RoundRobinModificationException("Round-robin not found.");
    }

    var ladderConfig = resolveLadderConfig(rr);
    validateTeamSize(a1Id, a2Id, "Team A");
    validateTeamSize(b1Id, b2Id, "Team B");
    ensureDistinctPlayers(a1Id, a2Id, b1Id, b2Id);

    RoundRobinEntry entry = new RoundRobinEntry();
    entry.setRoundRobin(rr);
    entry.setRoundNumber(roundNumber);
    entry.setBye(false);
    entry.setMatchId(null);

    User a1 = resolveParticipant(a1Id, ladderConfig);
    User a2 = resolveParticipant(a2Id, ladderConfig);
    User b1 = resolveParticipant(b1Id, ladderConfig);
    User b2 = resolveParticipant(b2Id, ladderConfig);

    Set<Long> playerIds = collectIds(a1, a2, b1, b2);
    validatePlayersAvailable(rr, null, roundNumber, playerIds);
    Long ladderConfigId =
        ladderConfig != null && ladderConfig.getId() != null ? ladderConfig.getId() : null;
    assertPlayersAvailableForRoundRobin(ladderConfigId, playerIds, rr.getId());

    entry.setA1(a1);
    entry.setA2(a2);
    entry.setB1(b1);
    entry.setB2(b2);
    return rrEntryRepo.save(entry);
  }

  public RoundRobin getRoundRobin(Long id) {
    return rrRepo.findById(id).orElse(null);
  }

  /**
   * List round-robins for the provided ladder/season context. If seasonId is provided, list
   * round-robins for that season. Otherwise try to find the active season for the ladderConfigId
   * and list for that.
   */
  public org.springframework.data.domain.Page<RoundRobin> listForLadderSeason(
      Long ladderConfigId, Long seasonId, int page, int size) {
    LadderConfig ladderConfig = resolveLadderConfig(ladderConfigId);
    var pageable =
        org.springframework.data.domain.PageRequest.of(
            Math.max(0, page),
            Math.max(1, size),
            org.springframework.data.domain.Sort.by("createdAt").descending());
    if (ladderConfig != null && ladderConfig.isSessionType()) {
      return rrRepo.findBySessionConfig(ladderConfig, pageable);
    }

    com.w3llspring.fhpb.web.model.LadderSeason season = null;
    if (seasonId != null) {
      season = seasonRepo.findById(seasonId).orElse(null);
    } else if (ladderConfigId != null) {
      season =
          seasonRepo
              .findActive(ladderConfigId)
              .orElseGet(
                  () ->
                      seasonRepo
                          .findTopByLadderConfigIdOrderByStartDateDesc(ladderConfigId)
                          .orElse(null));
    }
    if (season == null) return new org.springframework.data.domain.PageImpl<>(java.util.List.of());
    return rrRepo.findBySeasonAndSessionConfigIsNull(season, pageable);
  }

  public com.w3llspring.fhpb.web.model.MatchState getMatchState(Long matchId) {
    if (matchId == null) return null;
    return matchRepo.findById(matchId).map(Match::getState).orElse(null);
  }

  public Match getMatch(Long matchId) {
    if (matchId == null) return null;
    return matchRepo.findById(matchId).orElse(null);
  }

  public java.util.Map<Long, Match> loadMatchesWithParticipants(
      java.util.Collection<Long> matchIds) {
    java.util.Map<Long, Match> matchMap = new java.util.HashMap<>();
    if (matchIds == null || matchIds.isEmpty()) return matchMap;

    java.util.Set<Long> ids =
        matchIds.stream()
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    if (ids.isEmpty()) return matchMap;

    for (Match match : matchRepo.findAllByIdInWithUsers(ids)) {
      if (match != null && match.getId() != null) {
        matchMap.put(match.getId(), match);
      }
    }
    return matchMap;
  }

  /**
   * Find a logged Match that corresponds to the given RoundRobinEntry. Matching is independent of
   * team A/B designation and player position — it compares team membership as unordered sets.
   * Search is limited to matches played after the provided startInstant.
   */
  public java.util.Optional<Match> findLoggedMatchForEntry(
      RoundRobin rr, RoundRobinEntry entry, java.time.Instant startInstant) {
    // collect participants from the entry
    java.util.Set<User> players = new java.util.HashSet<>();
    if (entry.getA1() != null) players.add(entry.getA1());
    if (entry.getA2() != null) players.add(entry.getA2());
    if (entry.getB1() != null) players.add(entry.getB1());
    if (entry.getB2() != null) players.add(entry.getB2());

    if (players.isEmpty()) return java.util.Optional.empty();

    // search recent matches for these players from startInstant until now
    java.time.Instant now = java.time.Instant.now();
    java.util.List<Match> candidates;
    if (rr != null && rr.getSessionConfig() != null && rr.getSessionConfig().getId() != null) {
      candidates =
          matchRepo.findRecentPlayedMatchesForPlayersInSession(
              rr.getSessionConfig().getId(), players, startInstant, now);
    } else if (rr != null && rr.getSeason() != null) {
      candidates =
          matchRepo.findRecentPlayedMatchesForPlayersInSeason(
              rr.getSeason(), players, startInstant, now);
    } else {
      candidates = matchRepo.findRecentPlayedMatchesForPlayers(players, startInstant, now);
    }
    // Build entry team id sets
    java.util.Set<Long> eTeamA = new java.util.HashSet<>();
    if (entry.getA1() != null && entry.getA1().getId() != null) eTeamA.add(entry.getA1().getId());
    if (entry.getA2() != null && entry.getA2().getId() != null) eTeamA.add(entry.getA2().getId());
    java.util.Set<Long> eTeamB = new java.util.HashSet<>();
    if (entry.getB1() != null && entry.getB1().getId() != null) eTeamB.add(entry.getB1().getId());
    if (entry.getB2() != null && entry.getB2().getId() != null) eTeamB.add(entry.getB2().getId());

    for (Match m : candidates) {
      // build match team id sets
      java.util.Set<Long> mTeamA = new java.util.HashSet<>();
      if (m.getA1() != null && m.getA1().getId() != null) mTeamA.add(m.getA1().getId());
      if (m.getA2() != null && m.getA2().getId() != null) mTeamA.add(m.getA2().getId());
      java.util.Set<Long> mTeamB = new java.util.HashSet<>();
      if (m.getB1() != null && m.getB1().getId() != null) mTeamB.add(m.getB1().getId());
      if (m.getB2() != null && m.getB2().getId() != null) mTeamB.add(m.getB2().getId());

      if (teamsMatchUnordered(mTeamA, mTeamB, eTeamA, eTeamB)) {
        return java.util.Optional.of(m);
      }
    }

    return java.util.Optional.empty();
  }

  public java.util.Optional<Match> findLoggedMatchForEntry(
      RoundRobinEntry entry, java.time.Instant startInstant) {
    return findLoggedMatchForEntry(null, entry, startInstant);
  }

  @Transactional
  public RoundRobinEntry reserveTournamentEntry(
      LadderSeason season, Long entryId, User a1, User a2, User b1, User b2) {
    if (season == null
        || season.getId() == null
        || season.getLadderConfig() == null
        || !season.getLadderConfig().isTournamentMode()) {
      return null;
    }

    java.util.Set<Long> matchTeamA = teamIds(a1, a2);
    java.util.Set<Long> matchTeamB = teamIds(b1, b2);
    if (matchTeamA.isEmpty() || matchTeamB.isEmpty()) {
      throw new RoundRobinModificationException(
          "Tournament matches must be assigned to an active round-robin matchup.");
    }

    java.util.List<RoundRobinEntry> candidates;
    if (entryId != null) {
      RoundRobinEntry lockedEntry =
          rrEntryRepo
              .findByIdWithUsersForUpdate(entryId)
              .orElseThrow(
                  () -> new RoundRobinModificationException("Round-robin matchup not found."));
      if (!isActiveTournamentEntry(lockedEntry, season)) {
        throw new RoundRobinModificationException(
            "Tournament matches must be assigned to an active round-robin matchup.");
      }
      candidates = java.util.List.of(lockedEntry);
    } else {
      candidates = loadActiveTournamentEntriesForUpdate(season);
    }

    java.util.List<RoundRobinEntry> matches =
        candidates.stream()
            .filter(
                entry ->
                    teamsMatchUnordered(
                        teamIds(entry.getA1(), entry.getA2()),
                        teamIds(entry.getB1(), entry.getB2()),
                        matchTeamA,
                        matchTeamB))
            .collect(Collectors.toList());

    if (entryId != null && matches.isEmpty()) {
      throw new RoundRobinModificationException(
          "This round-robin matchup does not match the selected players.");
    }
    if (matches.isEmpty()) {
      throw new RoundRobinModificationException(
          "Tournament matches must be assigned to an active round-robin matchup.");
    }
    if (matches.size() > 1) {
      throw new RoundRobinModificationException(
          "Multiple active round-robin matchups match these players. Use the round-robin quick log button for the exact pairing.");
    }

    RoundRobinEntry reserved = matches.get(0);
    ensureTournamentEntryAvailable(reserved);
    return reserved;
  }

  public void assertTournamentMatchParticipants(Long matchId, User a1, User a2, User b1, User b2) {
    if (matchId == null) {
      return;
    }
    RoundRobinEntry linkedEntry = findLinkedEntryForMatch(matchId);
    if (linkedEntry == null) {
      return;
    }
    if (!teamsMatchUnordered(
        teamIds(linkedEntry.getA1(), linkedEntry.getA2()),
        teamIds(linkedEntry.getB1(), linkedEntry.getB2()),
        teamIds(a1, a2),
        teamIds(b1, b2))) {
      throw new RoundRobinModificationException(
          "Tournament matches linked to a round-robin matchup cannot change participants.");
    }
  }

  public RoundRobinEntry findLinkedEntryForMatch(Long matchId) {
    if (matchId == null) {
      return null;
    }
    return rrEntryRepo.findByMatchId(matchId).stream().findFirst().orElse(null);
  }

  /**
   * Persistently link a RoundRobinEntry to a Match. If the entry already has a matchId, this is a
   * no-op. Returns the saved entry.
   */
  @Transactional
  public RoundRobinEntry linkEntryToMatch(RoundRobinEntry entry, Long matchId) {
    if (entry == null || matchId == null) return entry;
    if (entry.getMatchId() != null) return entry; // already linked
    entry.setMatchId(matchId);
    return rrEntryRepo.save(entry);
  }

  /**
   * Return true if the two matches of teams are equal ignoring order: team labels (A/B) and player
   * positions are ignored. Comparison is by player id sets.
   */
  private boolean teamsMatchUnordered(
      java.util.Set<Long> mA,
      java.util.Set<Long> mB,
      java.util.Set<Long> eA,
      java.util.Set<Long> eB) {
    // direct compare
    if (mA.equals(eA) && mB.equals(eB)) return true;
    // swapped teams
    if (mA.equals(eB) && mB.equals(eA)) return true;
    return false;
  }

  private java.util.List<RoundRobinEntry> loadActiveTournamentEntriesForUpdate(
      LadderSeason season) {
    if (season == null) {
      return java.util.List.of();
    }
    java.util.List<RoundRobinEntry> entries = new java.util.ArrayList<>();
    for (RoundRobin rr : rrRepo.findBySeasonAndSessionConfigIsNull(season)) {
      if (rr == null) {
        continue;
      }
      int activeRound = Math.max(1, rr.getCurrentRound());
      entries.addAll(rrEntryRepo.findByRoundRobinAndRoundNumberWithUsersForUpdate(rr, activeRound));
    }
    return entries;
  }

  private boolean isActiveTournamentEntry(RoundRobinEntry entry, LadderSeason season) {
    if (entry == null || entry.isBye() || entry.getRoundRobin() == null || season == null) {
      return false;
    }
    RoundRobin rr = entry.getRoundRobin();
    if (rr.getSeason() == null
        || rr.getSeason().getId() == null
        || !rr.getSeason().getId().equals(season.getId())) {
      return false;
    }
    return entry.getRoundNumber() == Math.max(1, rr.getCurrentRound());
  }

  private void ensureTournamentEntryAvailable(RoundRobinEntry entry) {
    if (entry == null || entry.getRoundRobin() == null) {
      throw new RoundRobinModificationException(
          "Tournament matches must be assigned to an active round-robin matchup.");
    }

    Match linkedMatch = null;
    if (entry.getMatchId() != null) {
      linkedMatch = matchRepo.findByIdWithUsers(entry.getMatchId()).orElse(null);
      if (linkedMatch == null || linkedMatch.getState() == MatchState.NULLIFIED) {
        entry.setMatchId(null);
        rrEntryRepo.save(entry);
        linkedMatch = null;
      }
    }

    if (linkedMatch == null) {
      linkedMatch =
          findLoggedMatchForEntry(
                  entry.getRoundRobin(), entry, entry.getRoundRobin().getCreatedAt())
              .orElse(null);
      if (linkedMatch != null
          && linkedMatch.getState() != MatchState.NULLIFIED
          && linkedMatch.getId() != null) {
        entry.setMatchId(linkedMatch.getId());
        rrEntryRepo.save(entry);
      }
    }

    if (linkedMatch != null && linkedMatch.getState() != MatchState.NULLIFIED) {
      throw new RoundRobinModificationException(
          "That round-robin matchup already has a logged result.");
    }
  }

  private java.util.Set<Long> teamIds(User... users) {
    java.util.Set<Long> ids = new java.util.HashSet<>();
    if (users == null) {
      return ids;
    }
    for (User user : users) {
      if (user != null && user.getId() != null) {
        ids.add(user.getId());
      }
    }
    return ids;
  }

  /**
   * Format the match score so that it corresponds to the entry's displayed team order (entry team A
   * vs entry team B). If the match's teams are swapped relative to the entry, swap the scores
   * accordingly.
   */
  public String formatMatchResultForEntry(Match m, RoundRobinEntry entry) {
    if (m == null || entry == null) return null;

    java.util.Set<Long> mTeamA = new java.util.HashSet<>();
    if (m.getA1() != null && m.getA1().getId() != null) mTeamA.add(m.getA1().getId());
    if (m.getA2() != null && m.getA2().getId() != null) mTeamA.add(m.getA2().getId());
    java.util.Set<Long> mTeamB = new java.util.HashSet<>();
    if (m.getB1() != null && m.getB1().getId() != null) mTeamB.add(m.getB1().getId());
    if (m.getB2() != null && m.getB2().getId() != null) mTeamB.add(m.getB2().getId());

    java.util.Set<Long> eTeamA = new java.util.HashSet<>();
    if (entry.getA1() != null && entry.getA1().getId() != null) eTeamA.add(entry.getA1().getId());
    if (entry.getA2() != null && entry.getA2().getId() != null) eTeamA.add(entry.getA2().getId());
    java.util.Set<Long> eTeamB = new java.util.HashSet<>();
    if (entry.getB1() != null && entry.getB1().getId() != null) eTeamB.add(entry.getB1().getId());
    if (entry.getB2() != null && entry.getB2().getId() != null) eTeamB.add(entry.getB2().getId());

    int scoreA = m.getScoreA();
    int scoreB = m.getScoreB();

    // If match team A corresponds to entry team A, display as-is
    if (mTeamA.equals(eTeamA) && mTeamB.equals(eTeamB)) {
      return String.format("%d-%d", scoreA, scoreB);
    }
    // If match teams are swapped relative to entry, swap scores
    if (mTeamA.equals(eTeamB) && mTeamB.equals(eTeamA)) {
      return String.format("%d-%d", scoreB, scoreA);
    }
    // Fallback: default to match A-B ordering
    return String.format("%d-%d", scoreA, scoreB);
  }

  public int resolveCurrentRound(Long rrId) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) return 1;
    return resolveCurrentRound(rr);
  }

  public int resolveCurrentRound(RoundRobin rr) {
    if (rr == null) return 1;
    List<RoundRobinEntry> entries = rrEntryRepo.findByRoundRobinOrderByRoundNumberAscWithUsers(rr);
    int effectiveRound = computeEffectiveCurrentRound(rr, entries, true);
    if (rr.getCurrentRound() != effectiveRound) {
      rr.setCurrentRound(effectiveRound);
      rrRepo.save(rr);
    }
    return effectiveRound;
  }

  private int computeEffectiveCurrentRound(
      RoundRobin rr, List<RoundRobinEntry> entries, boolean persistLinks) {
    if (rr == null) return 1;
    if (entries == null || entries.isEmpty()) {
      return Math.max(1, rr.getCurrentRound());
    }

    java.util.Map<Integer, List<RoundRobinEntry>> entriesByRound = new java.util.LinkedHashMap<>();
    List<Long> matchIds = new ArrayList<>();
    int maxRound = 0;
    for (RoundRobinEntry entry : entries) {
      if (entry == null) continue;
      entriesByRound
          .computeIfAbsent(entry.getRoundNumber(), ignored -> new ArrayList<>())
          .add(entry);
      if (entry.getMatchId() != null) {
        matchIds.add(entry.getMatchId());
      }
      if (entry.getRoundNumber() > maxRound) {
        maxRound = entry.getRoundNumber();
      }
    }
    java.util.Map<Long, Match> matchMap = loadMatchesWithParticipants(matchIds);

    for (int round = 1; round <= maxRound; round++) {
      List<RoundRobinEntry> roundEntries = entriesByRound.get(round);
      if (roundEntries == null || roundEntries.isEmpty()) {
        return round;
      }
      for (RoundRobinEntry entry : roundEntries) {
        if (entry == null || entry.isBye()) {
          continue;
        }
        Match match = resolveMatchForEntry(rr, entry, matchMap, persistLinks);
        if (match == null || match.getState() != MatchState.CONFIRMED) {
          return round;
        }
      }
    }

    return maxRound + 1;
  }

  private Match resolveMatchForEntry(
      RoundRobin rr,
      RoundRobinEntry entry,
      java.util.Map<Long, Match> matchMap,
      boolean persistLinks) {
    if (rr == null || entry == null) return null;

    Long matchId = entry.getMatchId();
    if (matchId != null) {
      Match linked = matchMap.get(matchId);
      if (linked != null) {
        return linked;
      }
      var loaded = matchRepo.findById(matchId).orElse(null);
      if (loaded != null) {
        matchMap.put(matchId, loaded);
      }
      return loaded;
    }

    var maybe = findLoggedMatchForEntry(rr, entry, rr.getCreatedAt());
    if (maybe.isEmpty()) {
      return null;
    }

    Match found = maybe.get();
    matchMap.put(found.getId(), found);
    if (persistLinks && found.getId() != null) {
      linkEntryToMatch(entry, found.getId());
    }
    return found;
  }

  public List<RoundRobinEntry> getEntriesForRound(Long rrId, int roundNumber) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) return List.of();
    // OPTIMIZED: Use eager loading query to fetch all players at once
    return rrEntryRepo.findByRoundRobinAndRoundNumberWithUsers(rr, roundNumber);
  }

  public boolean canAdvance(Long rrId) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) return false;
    int round = resolveCurrentRound(rr);
    List<RoundRobinEntry> entries = rrEntryRepo.findByRoundRobinAndRoundNumber(rr, round);
    // If there are no entries for the current round, we cannot advance
    if (entries == null || entries.isEmpty()) return false;

    // Determine maximum round number for this round-robin
    List<RoundRobinEntry> all = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr);
    int maxRound = all.stream().mapToInt(RoundRobinEntry::getRoundNumber).max().orElse(0);
    // If already at or past the final round, do not allow advancing
    if (round >= maxRound) return false;

    for (RoundRobinEntry e : entries) {
      if (e.isBye()) continue;
      Long mid = e.getMatchId();
      if (mid == null) return false;
      var mOpt = matchRepo.findById(mid);
      if (mOpt.isEmpty()) return false;
      if (mOpt.get().getState() != MatchState.CONFIRMED) return false;
    }
    return true;
  }

  @Transactional
  public boolean advanceRoundIfReady(Long rrId) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) return false;
    int round = resolveCurrentRound(rr);
    List<RoundRobinEntry> entries = rrEntryRepo.findByRoundRobinAndRoundNumber(rr, round);
    // ensure all non-bye matches are confirmed
    if (entries == null || entries.isEmpty()) return false;

    // Determine maximum round number to avoid advancing past the final round
    List<RoundRobinEntry> all = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr);
    int maxRound = all.stream().mapToInt(RoundRobinEntry::getRoundNumber).max().orElse(0);
    if (round >= maxRound) return false;

    for (RoundRobinEntry e : entries) {
      if (e.isBye()) continue;
      Long mid = e.getMatchId();
      if (mid == null) return false;
      var mOpt = matchRepo.findById(mid);
      if (mOpt.isEmpty()) return false;
      if (mOpt.get().getState() != MatchState.CONFIRMED) return false;
    }

    // advance
    rr.setCurrentRound(round + 1);
    rr = rrRepo.save(rr);

    // create provisional matches for the new round
    int next = rr.getCurrentRound();
    List<RoundRobinEntry> nextEntries = rrEntryRepo.findByRoundRobinAndRoundNumber(rr, next);
    if (nextEntries != null) {
      for (RoundRobinEntry e : nextEntries) {
        if (e.isBye()) continue;
        if (e.getMatchId() != null) continue; // already exists
        // Do not create provisional Match rows for the upcoming round. Leave
        // the entry.matchId null and wait for users to log the match normally.
        rrEntryRepo.save(e);
      }
    }

    return true;
  }

  public List<RoundRobinStanding> computeStandings(Long rrId) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) return List.of();
    // OPTIMIZED: Eagerly load all entries with users
    List<RoundRobinEntry> entries = rrEntryRepo.findByRoundRobinOrderByRoundNumberAscWithUsers(rr);

    // OPTIMIZED: Batch load all matches at once instead of N+1 queries
    List<Long> matchIds =
        entries.stream()
            .map(RoundRobinEntry::getMatchId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());

    java.util.Map<Long, Match> matchMap = loadMatchesWithParticipants(matchIds);

    java.util.Set<Long> participantIds = new java.util.LinkedHashSet<>();
    for (RoundRobinEntry entry : entries) {
      collectId(entry.getA1(), participantIds);
      collectId(entry.getA2(), participantIds);
      collectId(entry.getB1(), participantIds);
      collectId(entry.getB2(), participantIds);
    }
    java.util.Map<Long, String> displayNames = buildDisplayNameMap(participantIds, rr);

    java.util.Map<Long, RoundRobinStanding> stats = new java.util.HashMap<>();
    for (RoundRobinEntry e : entries) {
      Match m = resolveMatchForEntry(rr, e, matchMap, true);
      if (m == null) continue;
      if (m.getState() != MatchState.CONFIRMED) continue;

      // collect participant ids for both teams
      List<User> teamA = new ArrayList<>();
      if (m.getA1() != null) teamA.add(m.getA1());
      if (m.getA2() != null) teamA.add(m.getA2());
      List<User> teamB = new ArrayList<>();
      if (m.getB1() != null) teamB.add(m.getB1());
      if (m.getB2() != null) teamB.add(m.getB2());

      int scoreA = m.getScoreA();
      int scoreB = m.getScoreB();

      for (User u : teamA) {
        if (u == null || u.getId() == null) continue;
        final Long uid = u.getId();
        stats
            .computeIfAbsent(
                uid,
                id ->
                    createStanding(
                        id,
                        displayNames.getOrDefault(
                            id, getDisplayNameForUser(u, ladderConfigIdForRoundRobin(rr))),
                        u))
            .incGamesPlayed();
        stats.get(uid).addPointsFor(scoreA);
        stats.get(uid).addPointsAgainst(scoreB);
      }
      for (User u : teamB) {
        if (u == null || u.getId() == null) continue;
        final Long uid = u.getId();
        stats
            .computeIfAbsent(
                uid,
                id ->
                    createStanding(
                        id,
                        displayNames.getOrDefault(
                            id, getDisplayNameForUser(u, ladderConfigIdForRoundRobin(rr))),
                        u))
            .incGamesPlayed();
        stats.get(uid).addPointsFor(scoreB);
        stats.get(uid).addPointsAgainst(scoreA);
      }

      if (scoreA > scoreB) {
        for (User u : teamA) if (u != null && u.getId() != null) stats.get(u.getId()).incWins();
        for (User u : teamB) if (u != null && u.getId() != null) stats.get(u.getId()).incLosses();
      } else if (scoreB > scoreA) {
        for (User u : teamB) if (u != null && u.getId() != null) stats.get(u.getId()).incWins();
        for (User u : teamA) if (u != null && u.getId() != null) stats.get(u.getId()).incLosses();
      } else {
        // No draws in pickleball round-robin standings; ignore tied scores here.
      }
    }

    // convert to list and sort
    List<RoundRobinStanding> standings = new ArrayList<>(stats.values());
    // Sort standings primarily by Wins (desc), then Points For (desc), then Losses (asc), then
    // nickname
    standings.sort(
        (a, b) -> {
          int c = Integer.compare(b.getWins(), a.getWins());
          if (c != 0) return c;
          c = Integer.compare(b.getPointsFor(), a.getPointsFor());
          if (c != 0) return c;
          // fewer losses ranks higher
          c = Integer.compare(a.getLosses(), b.getLosses());
          if (c != 0) return c;
          return a.getNickName().compareToIgnoreCase(b.getNickName());
        });
    return standings;
  }

  /**
   * Compute standings aggregated across all round-robins within the given season. If seasonId is
   * null, attempts to resolve an active or most recent season for the ladderId.
   */
  public List<RoundRobinStanding> computeStandingsForSeason(Long ladderConfigId, Long seasonId) {
    LadderConfig ladderConfig = resolveLadderConfig(ladderConfigId);
    if (ladderConfig != null && ladderConfig.isSessionType()) {
      return computeStandingsForSessionRoundRobins(ladderConfig);
    }
    final com.w3llspring.fhpb.web.model.LadderSeason season;
    if (seasonId != null) {
      season = seasonRepo.findById(seasonId).orElse(null);
    } else if (ladderConfigId != null) {
      season = findSeasonForLadder(ladderConfigId);
    } else {
      season = null;
    }
    if (season == null) return List.of();

    List<RoundRobin> rrs = rrRepo.findBySeasonAndSessionConfigIsNull(season);
    java.util.Map<Long, RoundRobinStanding> stats = new java.util.HashMap<>();

    for (RoundRobin rr : rrs) {
      List<RoundRobinEntry> entries = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr);
      java.util.Map<Long, Match> matchMap =
          loadMatchesWithParticipants(
              entries.stream()
                  .map(RoundRobinEntry::getMatchId)
                  .filter(java.util.Objects::nonNull)
                  .collect(java.util.stream.Collectors.toList()));
      for (RoundRobinEntry e : entries) {
        Match m = resolveMatchForEntry(rr, e, matchMap, true);
        if (m == null) continue;
        applyMatchToStandings(
            stats,
            m,
            season != null && season.getLadderConfig() != null
                ? season.getLadderConfig().getId()
                : ladderConfigId);
      }
    }

    return sortStandings(stats);
  }

  public List<RoundRobinStanding> computeStandingsForSessionRoundRobins(
      LadderConfig sessionConfig) {
    if (sessionConfig == null || !sessionConfig.isSessionType() || sessionConfig.getId() == null) {
      return List.of();
    }

    List<RoundRobin> rrs = rrRepo.findBySessionConfig(sessionConfig);
    java.util.Map<Long, RoundRobinStanding> stats = new java.util.HashMap<>();
    for (RoundRobin rr : rrs) {
      List<RoundRobinEntry> entries = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr);
      java.util.Map<Long, Match> matchMap =
          loadMatchesWithParticipants(
              entries.stream()
                  .map(RoundRobinEntry::getMatchId)
                  .filter(java.util.Objects::nonNull)
                  .collect(java.util.stream.Collectors.toList()));
      for (RoundRobinEntry entry : entries) {
        Match match = resolveMatchForEntry(rr, entry, matchMap, true);
        if (match == null) {
          continue;
        }
        applyMatchToStandings(stats, match, sessionConfig.getId());
      }
    }
    return sortStandings(stats);
  }

  public List<RoundRobinStanding> computeStandingsForSession(LadderConfig sessionConfig) {
    if (sessionConfig == null || !sessionConfig.isSessionType() || sessionConfig.getId() == null) {
      return List.of();
    }

    List<Match> matches =
        matchRepo.findConfirmedBySourceSessionConfigIdOrderByPlayedAtDescWithUsers(
            sessionConfig.getId());
    java.util.Map<Long, RoundRobinStanding> stats = new java.util.HashMap<>();
    for (Match match : matches) {
      applyMatchToStandings(stats, match, sessionConfig.getId());
    }
    return sortStandings(stats);
  }

  private RoundRobinStanding createStanding(Long userId, String displayName, User user) {
    RoundRobinStanding standing = new RoundRobinStanding(userId, displayName);
    standing.setBadgeViews(
        trophyBadgeViewService != null
            ? trophyBadgeViewService.badgeViews(user)
            : TrophyBadgeSupport.badgeViewsFromLoadedUser(user));
    return standing;
  }

  private void applyMatchToStandings(
      java.util.Map<Long, RoundRobinStanding> stats, Match match, Long ladderConfigId) {
    if (match == null || match.getState() != MatchState.CONFIRMED) {
      return;
    }

    List<User> teamA = new ArrayList<>();
    if (match.getA1() != null && !match.isA1Guest()) {
      teamA.add(match.getA1());
    }
    if (match.getA2() != null && !match.isA2Guest()) {
      teamA.add(match.getA2());
    }

    List<User> teamB = new ArrayList<>();
    if (match.getB1() != null && !match.isB1Guest()) {
      teamB.add(match.getB1());
    }
    if (match.getB2() != null && !match.isB2Guest()) {
      teamB.add(match.getB2());
    }

    int scoreA = match.getScoreA();
    int scoreB = match.getScoreB();

    for (User user : teamA) {
      if (user == null || user.getId() == null) {
        continue;
      }
      stats
          .computeIfAbsent(
              user.getId(),
              id -> createStanding(id, getDisplayNameForUser(user, ladderConfigId), user))
          .incGamesPlayed();
      stats.get(user.getId()).addPointsFor(scoreA);
      stats.get(user.getId()).addPointsAgainst(scoreB);
    }
    for (User user : teamB) {
      if (user == null || user.getId() == null) {
        continue;
      }
      stats
          .computeIfAbsent(
              user.getId(),
              id -> createStanding(id, getDisplayNameForUser(user, ladderConfigId), user))
          .incGamesPlayed();
      stats.get(user.getId()).addPointsFor(scoreB);
      stats.get(user.getId()).addPointsAgainst(scoreA);
    }

    if (scoreA > scoreB) {
      for (User user : teamA) {
        if (user != null && user.getId() != null) {
          stats.get(user.getId()).incWins();
        }
      }
      for (User user : teamB) {
        if (user != null && user.getId() != null) {
          stats.get(user.getId()).incLosses();
        }
      }
    } else if (scoreB > scoreA) {
      for (User user : teamB) {
        if (user != null && user.getId() != null) {
          stats.get(user.getId()).incWins();
        }
      }
      for (User user : teamA) {
        if (user != null && user.getId() != null) {
          stats.get(user.getId()).incLosses();
        }
      }
    }
  }

  private List<RoundRobinStanding> sortStandings(java.util.Map<Long, RoundRobinStanding> stats) {
    List<RoundRobinStanding> standings = new ArrayList<>(stats.values());
    standings.sort(
        (a, b) -> {
          int c = Integer.compare(b.getWins(), a.getWins());
          if (c != 0) return c;
          c = Integer.compare(b.getPointsFor(), a.getPointsFor());
          if (c != 0) return c;
          c = Integer.compare(a.getLosses(), b.getLosses());
          if (c != 0) return c;
          return a.getNickName().compareToIgnoreCase(b.getNickName());
        });
    return standings;
  }

  /** Return the maximum round number defined for this RoundRobin (0 if none). */
  public int getMaxRound(Long rrId) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) return 0;
    List<RoundRobinEntry> all = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr);
    return all.stream().mapToInt(RoundRobinEntry::getRoundNumber).max().orElse(0);
  }

  /** Count distinct participant user ids referenced by entries of the given round-robin. */
  public int countParticipants(RoundRobin rr) {
    if (rr == null) return 0;
    List<RoundRobinEntry> entries = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr);
    java.util.Set<Long> ids = new java.util.HashSet<>();
    for (RoundRobinEntry e : entries) {
      if (e.getA1() != null && e.getA1().getId() != null) ids.add(e.getA1().getId());
      if (e.getA2() != null && e.getA2().getId() != null) ids.add(e.getA2().getId());
      if (e.getB1() != null && e.getB1().getId() != null) ids.add(e.getB1().getId());
      if (e.getB2() != null && e.getB2().getId() != null) ids.add(e.getB2().getId());
    }
    return ids.size();
  }

  /**
   * End the round-robin early: set the current round to one past the last defined round so the UI
   * will show final results. This does NOT mark any matches as confirmed; it simply advances the
   * currentRound pointer beyond the schedule.
   */
  @Transactional
  public boolean endRoundRobinEarly(Long rrId) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) return false;
    List<RoundRobinEntry> all = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr);
    int maxRound = all.stream().mapToInt(RoundRobinEntry::getRoundNumber).max().orElse(0);
    // move current round to past the last round
    rr.setCurrentRound(maxRound + 1);
    rrRepo.save(rr);
    return true;
  }

  /**
   * End any open round-robins for the provided season. A round-robin is considered open when
   * currentRound <= max configured round.
   *
   * @return number of round-robins that were ended.
   */
  @Transactional
  public int endOpenRoundRobinsForSeason(LadderSeason season) {
    if (season == null) return 0;

    int ended = 0;
    List<RoundRobin> roundRobins = rrRepo.findBySeason(season);
    for (RoundRobin rr : roundRobins) {
      if (rr == null) continue;
      List<RoundRobinEntry> all = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr);
      int maxRound = all.stream().mapToInt(RoundRobinEntry::getRoundNumber).max().orElse(0);
      if (rr.getCurrentRound() > maxRound) continue;
      rr.setCurrentRound(maxRound + 1);
      rrRepo.save(rr);
      ended++;
    }
    return ended;
  }

  /**
   * Prune RoundRobinEntry rows for round-robins that are completed (currentRound > maxRound) and
   * where the entry has no linked match (matchId == null). Returns the number of rows deleted. This
   * is intended to be invoked from a scheduled cleanup job.
   */
  @Transactional
  public int pruneCompletedRoundRobinEntriesWithoutMatch() {
    int deleted = 0;
    List<RoundRobin> allRrs = rrRepo.findAll();
    for (RoundRobin rr : allRrs) {
      List<RoundRobinEntry> allEntries = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr);
      int maxRound = allEntries.stream().mapToInt(RoundRobinEntry::getRoundNumber).max().orElse(0);
      if (rr.getCurrentRound() <= maxRound) continue; // not completed
      for (RoundRobinEntry e : allEntries) {
        if (e.getMatchId() == null) {
          try {
            rrEntryRepo.delete(e);
            deleted++;
          } catch (Exception ex) {
            // log and continue; do not fail the whole job on single-row issue
            // Using System.err here to avoid adding a logger field to the service class
            System.err.println(
                "pruneCompletedRoundRobinEntriesWithoutMatch: delete failed for entryId="
                    + (e.getId() == null ? "?" : e.getId())
                    + ": "
                    + ex.getMessage());
          }
        }
      }
    }
    return deleted;
  }

  private void validatePlayersAvailable(
      RoundRobin rr, Long excludeEntryId, int roundNumber, Set<Long> desiredPlayerIds) {
    if (desiredPlayerIds == null || desiredPlayerIds.isEmpty()) return;
    List<RoundRobinEntry> entries = rrEntryRepo.findByRoundRobinAndRoundNumber(rr, roundNumber);
    if (entries == null || entries.isEmpty()) return;
    for (RoundRobinEntry other : entries) {
      if (other == null) continue;
      if (excludeEntryId != null && other.getId() != null && other.getId().equals(excludeEntryId)) {
        continue;
      }
      Set<Long> otherIds = collectIds(other.getA1(), other.getA2(), other.getB1(), other.getB2());
      if (otherIds.isEmpty()) continue;
      Set<Long> overlap = new HashSet<>(otherIds);
      overlap.retainAll(desiredPlayerIds);
      if (overlap.isEmpty()) continue;

      if (!other.isBye()) {
        throw new RoundRobinModificationException(
            "A selected player is already in another matchup this round.");
      }
    }
  }

  @Transactional
  public List<User> regenerateRoundWithForcedByes(
      Long rrId, int roundNumber, List<Long> forcedByeIds) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) {
      throw new RoundRobinModificationException("Round-robin not found.");
    }
    if (rr.isFixedTeamsFormat()) {
      throw new RoundRobinModificationException(
          "Fixed-team round-robins cannot regenerate round pairings.");
    }
    List<RoundRobinEntry> found = rrEntryRepo.findByRoundRobinAndRoundNumber(rr, roundNumber);
    if (found == null || found.isEmpty()) {
      throw new RoundRobinModificationException("No matchups found for the selected round.");
    }
    List<RoundRobinEntry> entries = new java.util.ArrayList<>(found);
    for (RoundRobinEntry entry : entries) {
      if (!entry.isBye() && entry.getMatchId() != null) {
        throw new RoundRobinModificationException(
            "Cannot regenerate a round that already has logged matches.");
      }
    }

    Set<Long> forcedSet = new java.util.LinkedHashSet<>();
    if (forcedByeIds != null) {
      for (Long id : forcedByeIds) {
        if (id != null && id > 0) forcedSet.add(id);
      }
    }

    Set<Long> participantIds = new java.util.LinkedHashSet<>();
    for (RoundRobinEntry entry : entries) {
      collectId(entry.getA1(), participantIds);
      collectId(entry.getA2(), participantIds);
      collectId(entry.getB1(), participantIds);
      collectId(entry.getB2(), participantIds);
    }
    if (participantIds.isEmpty()) {
      throw new RoundRobinModificationException("Cannot regenerate round without participants.");
    }
    if (!participantIds.containsAll(forcedSet)) {
      throw new RoundRobinModificationException(
          "Forced bye players must already be part of this round.");
    }

    Set<Long> fetchIds = new java.util.LinkedHashSet<>(participantIds);
    fetchIds.addAll(forcedSet);
    java.util.Map<Long, User> userMap = new java.util.HashMap<>();
    userRepo
        .findAllById(fetchIds)
        .forEach(
            user -> {
              if (user != null && user.getId() != null) {
                userMap.put(user.getId(), user);
              }
            });
    if (userMap.size() < fetchIds.size()) {
      throw new RoundRobinModificationException("One or more players could not be loaded.");
    }

    List<User> sortedParticipants =
        participantIds.stream()
            .map(userMap::get)
            .filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparing(User::getId))
            .collect(Collectors.toCollection(java.util.ArrayList::new));

    List<User> remainingPlayers = new java.util.ArrayList<>();
    for (User user : sortedParticipants) {
      if (!forcedSet.contains(user.getId())) remainingPlayers.add(user);
    }

    RoundRobinEntry template = entries.stream().filter(e -> !e.isBye()).findFirst().orElse(null);
    int teamSizeA = template != null ? countTeamA(template) : 2;
    int teamSizeB = template != null ? countTeamB(template) : 2;
    if (teamSizeA <= 0) teamSizeA = 1;
    if (teamSizeB <= 0) teamSizeB = 1;
    int playersPerMatch = teamSizeA + teamSizeB;
    if (playersPerMatch <= 0) {
      throw new RoundRobinModificationException("Invalid team configuration for this round.");
    }

    int remainder = remainingPlayers.size() % playersPerMatch;
    if (remainder != 0) {
      for (int i = 0; i < remainder; i++) {
        User moved = remainingPlayers.remove(remainingPlayers.size() - 1);
        forcedSet.add(moved.getId());
      }
    }
    int requiredMatches = remainingPlayers.size() / playersPerMatch;

    List<RoundRobinEntry> nonByeEntries =
        entries.stream()
            .filter(e -> !e.isBye())
            .collect(Collectors.toCollection(java.util.ArrayList::new));
    List<RoundRobinEntry> byeEntries =
        entries.stream()
            .filter(RoundRobinEntry::isBye)
            .collect(Collectors.toCollection(java.util.ArrayList::new));

    while (nonByeEntries.size() > requiredMatches) {
      RoundRobinEntry entry = nonByeEntries.remove(nonByeEntries.size() - 1);
      clearEntryPlayers(entry);
      entry.setBye(true);
      rrEntryRepo.save(entry);
      byeEntries.add(entry);
    }

    java.util.Iterator<RoundRobinEntry> byeIter = byeEntries.iterator();
    while (nonByeEntries.size() < requiredMatches) {
      RoundRobinEntry entry;
      if (byeIter.hasNext()) {
        entry = byeIter.next();
        byeIter.remove();
      } else {
        entry = new RoundRobinEntry();
        entry.setRoundRobin(rr);
        entry.setRoundNumber(roundNumber);
        entries.add(entry);
      }
      clearEntryPlayers(entry);
      entry.setBye(false);
      rrEntryRepo.save(entry);
      nonByeEntries.add(entry);
    }

    java.util.Iterator<User> playerIterator = remainingPlayers.iterator();
    for (RoundRobinEntry entry : nonByeEntries) {
      clearEntryPlayers(entry);
      entry.setBye(false);
      assignTeamPlayers(entry, teamSizeA, teamSizeB, playerIterator);
      rrEntryRepo.save(entry);
    }

    byeEntries =
        entries.stream()
            .filter(RoundRobinEntry::isBye)
            .collect(Collectors.toCollection(java.util.ArrayList::new));

    List<User> forcedUsers =
        forcedSet.stream()
            .map(userMap::get)
            .filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparing(User::getId))
            .collect(Collectors.toCollection(java.util.ArrayList::new));

    int neededByeEntries = (int) Math.ceil(forcedUsers.size() / 2.0);
    while (byeEntries.size() < neededByeEntries) {
      RoundRobinEntry entry = new RoundRobinEntry();
      entry.setRoundRobin(rr);
      entry.setRoundNumber(roundNumber);
      entry.setBye(true);
      entries.add(entry);
      byeEntries.add(entry);
    }

    int forcedIndex = 0;
    for (RoundRobinEntry entry : byeEntries) {
      clearEntryPlayers(entry);
      entry.setBye(true);
      if (forcedIndex < forcedUsers.size()) {
        entry.setA1(forcedUsers.get(forcedIndex++));
      }
      if (forcedIndex < forcedUsers.size()) {
        entry.setA2(forcedUsers.get(forcedIndex++));
      }
      rrEntryRepo.save(entry);
    }

    return forcedUsers;
  }

  /**
   * Regenerate all future rounds (rounds without matches) to prioritize underserved players. This
   * helps fix imbalances caused by manual substitutions or forced byes.
   */
  @Transactional
  public int rebalanceFutureRounds(Long rrId) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) {
      throw new RoundRobinModificationException("Round-robin not found.");
    }
    if (rr.isFixedTeamsFormat()) {
      throw new RoundRobinModificationException(
          "Fixed-team round-robins cannot rebalance future rounds.");
    }

    List<RoundRobinEntry> allEntries = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr);
    if (allEntries == null || allEntries.isEmpty()) {
      throw new RoundRobinModificationException("No rounds to rebalance.");
    }

    // Find rounds that can be regenerated (no matches logged)
    int maxRound = allEntries.stream().mapToInt(RoundRobinEntry::getRoundNumber).max().orElse(0);
    List<Integer> regenerableRounds = new java.util.ArrayList<>();

    for (int round = 1; round <= maxRound; round++) {
      int currentRound = round;
      boolean hasMatches =
          allEntries.stream()
              .filter(e -> e.getRoundNumber() == currentRound)
              .anyMatch(e -> e.getMatchId() != null);
      if (!hasMatches) {
        regenerableRounds.add(round);
      }
    }

    if (regenerableRounds.isEmpty()) {
      throw new RoundRobinModificationException(
          "All rounds already have matches logged. Cannot rebalance.");
    }

    // Delete all future round entries
    for (int round : regenerableRounds) {
      List<RoundRobinEntry> roundEntries = rrEntryRepo.findByRoundRobinAndRoundNumber(rr, round);
      if (roundEntries != null) {
        rrEntryRepo.deleteAll(roundEntries);
      }
    }

    // Collect participants and their match history
    Set<Long> participantIds = new java.util.LinkedHashSet<>();
    for (RoundRobinEntry entry : allEntries) {
      collectId(entry.getA1(), participantIds);
      collectId(entry.getA2(), participantIds);
      collectId(entry.getB1(), participantIds);
      collectId(entry.getB2(), participantIds);
    }

    List<Long> participants = new java.util.ArrayList<>(participantIds);

    // Build opponent history from completed rounds
    java.util.Map<Long, java.util.Map<Long, Integer>> priorPairCounts = new java.util.HashMap<>();
    for (Long id : participantIds) {
      priorPairCounts.put(id, new java.util.HashMap<>());
    }

    for (RoundRobinEntry entry : allEntries) {
      if (regenerableRounds.contains(entry.getRoundNumber())) continue; // skip future rounds
      if (entry.isBye()) continue;

      List<Long> players = new java.util.ArrayList<>();
      if (entry.getA1() != null && entry.getA1().getId() != null)
        players.add(entry.getA1().getId());
      if (entry.getA2() != null && entry.getA2().getId() != null)
        players.add(entry.getA2().getId());
      if (entry.getB1() != null && entry.getB1().getId() != null)
        players.add(entry.getB1().getId());
      if (entry.getB2() != null && entry.getB2().getId() != null)
        players.add(entry.getB2().getId());

      // Count opponents (everyone in the match except self)
      for (Long player : players) {
        for (Long opponent : players) {
          if (!player.equals(opponent)) {
            priorPairCounts.get(player).merge(opponent, 1, Integer::sum);
          }
        }
      }
    }

    // Use the scheduler to generate new rounds with history context
    List<List<RoundRobinScheduler.MatchSpec>> schedule =
        roundRobinScheduler.generate(participants, priorPairCounts, regenerableRounds.size());

    // Save the regenerated rounds
    for (int i = 0; i < schedule.size() && i < regenerableRounds.size(); i++) {
      int roundNumber = regenerableRounds.get(i);
      List<RoundRobinScheduler.MatchSpec> roundMatches = schedule.get(i);

      for (RoundRobinScheduler.MatchSpec match : roundMatches) {
        RoundRobinEntry entry = new RoundRobinEntry();
        entry.setRoundRobin(rr);
        entry.setRoundNumber(roundNumber);
        entry.setBye(match.bye);

        if (match.a1 != null) entry.setA1(userRepo.findById(match.a1).orElse(null));
        if (match.a2 != null) entry.setA2(userRepo.findById(match.a2).orElse(null));
        if (match.b1 != null) entry.setB1(userRepo.findById(match.b1).orElse(null));
        if (match.b2 != null) entry.setB2(userRepo.findById(match.b2).orElse(null));

        rrEntryRepo.save(entry);
      }
    }

    return regenerableRounds.size();
  }

  public RoundRobinDeviationSummary analyzeDeviations(Long rrId) {
    RoundRobin rr = getRoundRobin(rrId);
    if (rr == null) return RoundRobinDeviationSummary.empty();

    List<RoundRobinEntry> allEntries = rrEntryRepo.findByRoundRobinOrderByRoundNumberAsc(rr);
    if (allEntries == null || allEntries.isEmpty()) {
      return RoundRobinDeviationSummary.empty();
    }

    // Find the current round (first round without matchId or last round if all have matches)
    int currentRound =
        allEntries.stream()
            .filter(e -> e.getMatchId() == null)
            .mapToInt(RoundRobinEntry::getRoundNumber)
            .min()
            .orElse(allEntries.stream().mapToInt(RoundRobinEntry::getRoundNumber).max().orElse(0));

    // Split into completed and future rounds (completed = has matchId or is before current round)
    List<RoundRobinEntry> completedEntries =
        allEntries.stream()
            .filter(e -> e.getRoundNumber() < currentRound || e.getMatchId() != null)
            .collect(Collectors.toList());

    int maxRound = allEntries.stream().mapToInt(RoundRobinEntry::getRoundNumber).max().orElse(0);
    int futureRoundCount = Math.max(0, maxRound - currentRound + 1);

    // Collect all participants
    java.util.LinkedHashSet<Long> participantIds = new java.util.LinkedHashSet<>();
    for (RoundRobinEntry entry : allEntries) {
      collectId(entry.getA1(), participantIds);
      collectId(entry.getA2(), participantIds);
      collectId(entry.getB1(), participantIds);
      collectId(entry.getB2(), participantIds);
    }

    // Build user map for display names
    java.util.Map<Long, User> userMap = new java.util.HashMap<>();
    if (!participantIds.isEmpty()) {
      userRepo
          .findAllById(participantIds)
          .forEach(
              user -> {
                if (user != null && user.getId() != null) {
                  userMap.put(user.getId(), user);
                }
              });
    }

    // Count matches played by each participant (excluding byes)
    java.util.Map<Long, Integer> matchCountById = new java.util.HashMap<>();
    for (Long id : participantIds) {
      matchCountById.put(id, 0);
    }

    for (RoundRobinEntry entry : completedEntries) {
      if (entry.isBye()) continue; // byes don't count as matches

      Set<Long> players = new HashSet<>();
      collectId(entry.getA1(), players);
      collectId(entry.getA2(), players);
      collectId(entry.getB1(), players);
      collectId(entry.getB2(), players);

      for (Long playerId : players) {
        matchCountById.merge(playerId, 1, Integer::sum);
      }
    }

    // Convert to display names
    java.util.Map<String, Integer> matchesByPlayer = new java.util.LinkedHashMap<>();
    for (var entry : matchCountById.entrySet()) {
      String name = displayNameForId(entry.getKey(), userMap, ladderConfigIdForRoundRobin(rr));
      matchesByPlayer.put(name, entry.getValue());
    }

    // Find imbalances
    int minMatches = matchCountById.values().stream().mapToInt(Integer::intValue).min().orElse(0);
    int maxMatches = matchCountById.values().stream().mapToInt(Integer::intValue).max().orElse(0);

    List<String> underservedPlayers =
        matchCountById.entrySet().stream()
            .filter(e -> e.getValue() < maxMatches)
            .map(e -> displayNameForId(e.getKey(), userMap, ladderConfigIdForRoundRobin(rr)))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());

    List<String> overservedPlayers =
        matchCountById.entrySet().stream()
            .filter(e -> e.getValue() > minMatches)
            .map(e -> displayNameForId(e.getKey(), userMap, ladderConfigIdForRoundRobin(rr)))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());

    // Generate simple, actionable fix suggestion
    String suggestedFix = null;
    if (!underservedPlayers.isEmpty() && futureRoundCount > 0) {
      if (underservedPlayers.size() == 1) {
        suggestedFix =
            "Prioritize "
                + underservedPlayers.get(0)
                + " in remaining rounds to balance participation.";
      } else if (underservedPlayers.size() <= 3) {
        suggestedFix =
            "Prioritize "
                + String.join(", ", underservedPlayers)
                + " in remaining rounds to balance participation.";
      } else {
        suggestedFix =
            underservedPlayers.size()
                + " players need more matches. Click 'Rebalance Future Rounds' to automatically adjust.";
      }
    } else if (!underservedPlayers.isEmpty() && futureRoundCount == 0) {
      suggestedFix = "No future rounds to adjust. Consider adding rounds to balance participation.";
    }

    return new RoundRobinDeviationSummary(
        participantIds.size(),
        currentRound > 0 ? currentRound - 1 : 0,
        futureRoundCount,
        matchesByPlayer,
        underservedPlayers,
        overservedPlayers,
        suggestedFix);
  }

  private Set<Long> collectIds(User... users) {
    Set<Long> ids = new HashSet<>();
    if (users == null) return ids;
    for (User u : users) {
      if (u != null && u.getId() != null) ids.add(u.getId());
    }
    return ids;
  }

  private String displayNameForId(
      Long userId, java.util.Map<Long, User> userMap, LadderSeason season) {
    Long ladderConfigId = null;
    if (season != null && season.getLadderConfig() != null) {
      ladderConfigId = season.getLadderConfig().getId();
    }
    return displayNameForId(userId, userMap, ladderConfigId);
  }

  private String displayNameForId(
      Long userId, java.util.Map<Long, User> userMap, Long ladderConfigId) {
    if (userId == null) return "Unknown";
    User user = userMap.get(userId);
    if (user != null) return getDisplayNameForUser(user, ladderConfigId);
    return com.w3llspring.fhpb.web.util.UserPublicName.FALLBACK;
  }

  private String safeEntryLabel(RoundRobinEntry entry) {
    if (entry == null) return "?";
    if (entry.getId() != null) return "#" + entry.getId();
    return "(temp)";
  }

  private void collectId(User user, Set<Long> ids) {
    if (user != null && user.getId() != null) ids.add(user.getId());
  }

  private void assertPlayersAvailableForRoundRobin(
      Long ladderConfigId,
      java.util.Collection<Long> requestedPlayerIds,
      Long excludeRoundRobinId) {
    if (ladderConfigId == null || requestedPlayerIds == null || requestedPlayerIds.isEmpty())
      return;

    java.util.LinkedHashSet<Long> requested =
        requestedPlayerIds.stream()
            .filter(java.util.Objects::nonNull)
            .filter(id -> id > 0)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    if (requested.isEmpty()) return;

    LadderConfig ladderConfig = resolveLadderConfig(ladderConfigId);
    List<RoundRobin> ladderRoundRobins =
        findRoundRobinsForOwnershipContext(findSeasonForLadder(ladderConfigId), ladderConfig);
    if (ladderRoundRobins == null || ladderRoundRobins.isEmpty()) return;

    java.util.LinkedHashMap<Long, String> conflicts = new java.util.LinkedHashMap<>();

    for (RoundRobin existing : ladderRoundRobins) {
      if (existing == null || existing.getId() == null) continue;
      if (excludeRoundRobinId != null && existing.getId().equals(excludeRoundRobinId)) continue;

      RoundRobinActivity activity = analyzeRoundRobinActivity(existing);
      if (!activity.active) continue;

      for (Long requestedId : requested) {
        if (activity.participants.contains(requestedId) && !conflicts.containsKey(requestedId)) {
          String label =
              activity.displayNames.getOrDefault(
                  requestedId, com.w3llspring.fhpb.web.util.UserPublicName.FALLBACK);
          conflicts.put(requestedId, label);
        }
      }
    }

    if (!conflicts.isEmpty()) {
      String joined = String.join(", ", conflicts.values());
      throw new RoundRobinModificationException(
          "Cannot assign players already participating in another active round-robin for this ladder: "
              + joined
              + ".");
    }
  }

  private RoundRobinActivity analyzeRoundRobinActivity(RoundRobin rr) {
    List<RoundRobinEntry> entries = rrEntryRepo.findByRoundRobinOrderByRoundNumberAscWithUsers(rr);
    if (entries == null || entries.isEmpty()) {
      return new RoundRobinActivity(false, java.util.Set.of(), java.util.Map.of());
    }

    java.util.LinkedHashSet<Long> participantIds = new java.util.LinkedHashSet<>();
    java.util.List<Long> matchIds = new java.util.ArrayList<>();
    for (RoundRobinEntry entry : entries) {
      collectId(entry.getA1(), participantIds);
      collectId(entry.getA2(), participantIds);
      collectId(entry.getB1(), participantIds);
      collectId(entry.getB2(), participantIds);
      if (entry.getMatchId() != null) {
        matchIds.add(entry.getMatchId());
      }
    }

    java.util.Map<Long, Match> matchMap = loadMatchesWithParticipants(matchIds);
    boolean active = false;
    for (RoundRobinEntry entry : entries) {
      if (entry.isBye()) continue;
      Long mid = entry.getMatchId();
      if (mid == null) {
        active = true;
        break;
      }
      Match match = matchMap.get(mid);
      if (match == null || match.getState() != MatchState.CONFIRMED) {
        active = true;
        break;
      }
    }

    java.util.Map<Long, String> names =
        participantIds.isEmpty() ? java.util.Map.of() : buildDisplayNameMap(participantIds, rr);
    return new RoundRobinActivity(active, participantIds, names);
  }

  private static final class RoundRobinActivity {
    final boolean active;
    final java.util.Set<Long> participants;
    final java.util.Map<Long, String> displayNames;

    RoundRobinActivity(
        boolean active,
        java.util.Set<Long> participants,
        java.util.Map<Long, String> displayNames) {
      this.active = active;
      this.participants = participants;
      this.displayNames = displayNames;
    }
  }

  public java.util.Map<Long, String> buildDisplayNameMap(
      java.util.Collection<Long> userIds, LadderSeason season) {
    Long ladderConfigId = null;
    if (season != null && season.getLadderConfig() != null) {
      ladderConfigId = season.getLadderConfig().getId();
    }
    return buildDisplayNameMap(userIds, ladderConfigId);
  }

  public java.util.Map<Long, String> buildDisplayNameMap(
      java.util.Collection<Long> userIds, Long ladderConfigId) {
    java.util.Map<Long, String> displayNames = new java.util.HashMap<>();
    if (userIds == null || userIds.isEmpty()) return displayNames;

    java.util.LinkedHashSet<Long> ids =
        userIds.stream()
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    if (ids.isEmpty()) return displayNames;

    java.util.Map<Long, User> usersById = new java.util.HashMap<>();
    for (User user : userRepo.findAllById(ids)) {
      if (user != null && user.getId() != null) {
        usersById.put(user.getId(), user);
      }
    }

    java.util.Map<Long, String> courtNames = buildCourtNameMap(ids, ladderConfigId);

    for (Long id : ids) {
      User user = usersById.get(id);
      if (user == null) continue;
      String court = courtNames.get(id);
      String nick = safeNick(user);
      if (court != null && !court.isBlank()) {
        displayNames.put(id, court + "-" + nick);
      } else {
        displayNames.put(id, nick);
      }
    }

    return displayNames;
  }

  public java.util.Map<Long, String> buildCourtNameMap(
      java.util.Collection<Long> userIds, Long ladderConfigId) {
    java.util.Map<Long, String> courtNamesByUser = new java.util.HashMap<>();
    if (userIds == null || userIds.isEmpty()) return courtNamesByUser;

    java.util.LinkedHashSet<Long> ids =
        userIds.stream()
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    if (ids.isEmpty()) return courtNamesByUser;

    java.util.Map<Long, java.util.Set<String>> preferredCourtNames =
        courtNameService.gatherCourtNamesForUsers(ids, ladderConfigId);
    java.util.Map<Long, java.util.Set<String>> fallbackCourtNames =
        ladderConfigId != null
            ? courtNameService.gatherCourtNamesForUsers(ids, null)
            : java.util.Map.of();

    for (Long id : ids) {
      String courtName = firstAlias(preferredCourtNames.get(id));
      if ((courtName == null || courtName.isBlank()) && ladderConfigId != null) {
        courtName = firstAlias(fallbackCourtNames.get(id));
      }
      if (courtName != null && !courtName.isBlank()) {
        courtNamesByUser.put(id, courtName);
      }
    }

    return courtNamesByUser;
  }

  private void clearEntryPlayers(RoundRobinEntry entry) {
    entry.setA1(null);
    entry.setA2(null);
    entry.setB1(null);
    entry.setB2(null);
    entry.setMatchId(null);
  }

  private int countTeamA(RoundRobinEntry entry) {
    int count = 0;
    if (entry.getA1() != null) count++;
    if (entry.getA2() != null) count++;
    return count;
  }

  private int countTeamB(RoundRobinEntry entry) {
    int count = 0;
    if (entry.getB1() != null) count++;
    if (entry.getB2() != null) count++;
    return count;
  }

  private void assignTeamPlayers(
      RoundRobinEntry entry,
      int teamSizeA,
      int teamSizeB,
      java.util.Iterator<User> playerIterator) {
    if (teamSizeA >= 1 && playerIterator.hasNext()) entry.setA1(playerIterator.next());
    if (teamSizeA >= 2 && playerIterator.hasNext()) entry.setA2(playerIterator.next());
    if (teamSizeB >= 1 && playerIterator.hasNext()) entry.setB1(playerIterator.next());
    if (teamSizeB >= 2 && playerIterator.hasNext()) entry.setB2(playerIterator.next());
    entry.setMatchId(null);
  }

  private void validateTeamSize(Long primaryId, Long secondaryId, String teamLabel) {
    if ((primaryId == null || primaryId <= 0) && (secondaryId == null || secondaryId <= 0)) {
      throw new RoundRobinModificationException(teamLabel + " must include at least one player.");
    }
  }

  private void ensureDistinctPlayers(Long... ids) {
    java.util.Set<Long> seen = new java.util.HashSet<>();
    for (Long id : ids) {
      if (id == null || id <= 0) continue;
      if (!seen.add(id)) {
        throw new RoundRobinModificationException("A player has been selected more than once.");
      }
    }
  }

  private User resolveParticipant(
      Long userId, com.w3llspring.fhpb.web.model.LadderConfig ladderConfig) {
    if (userId == null || userId <= 0) return null;
    User user =
        userRepo
            .findById(userId)
            .orElseThrow(() -> new RoundRobinModificationException("Player not found."));
    if (ladderConfig != null && ladderConfig.getId() != null) {
      var membership = membershipRepo.findByLadderConfigIdAndUserId(ladderConfig.getId(), userId);
      if (membership.isEmpty() || membership.get().getState() != LadderMembership.State.ACTIVE) {
        throw new RoundRobinModificationException(
            "Player " + safeNick(user) + " is not an active member of this ladder.");
      }
    }
    return user;
  }

  private com.w3llspring.fhpb.web.model.LadderConfig resolveLadderConfig(RoundRobin rr) {
    if (rr == null) return null;
    if (rr.getSessionConfig() != null) {
      return rr.getSessionConfig();
    }
    LadderSeason season = rr.getSeason();
    if (season == null) return null;
    return season.getLadderConfig();
  }

  private LadderConfig resolveLadderConfig(Long ladderConfigId) {
    if (ladderConfigId == null || ladderConfigRepo == null) {
      return null;
    }
    Optional<LadderConfig> ladderConfig = ladderConfigRepo.findById(ladderConfigId);
    return ladderConfig == null ? null : ladderConfig.orElse(null);
  }

  private LadderSeason resolveTargetSeasonForSession(LadderConfig sessionConfig) {
    if (sessionConfig == null
        || !sessionConfig.isSessionType()
        || sessionConfig.getTargetSeasonId() == null) {
      return null;
    }
    return seasonRepo
        .findByIdWithLadderConfig(sessionConfig.getTargetSeasonId())
        .or(() -> seasonRepo.findById(sessionConfig.getTargetSeasonId()))
        .orElse(null);
  }

  private List<RoundRobin> findRoundRobinsForOwnershipContext(
      LadderSeason season, LadderConfig ownerConfig) {
    if (ownerConfig != null && ownerConfig.isSessionType()) {
      return rrRepo.findBySessionConfig(ownerConfig);
    }
    if (season == null) {
      return List.of();
    }
    return rrRepo.findBySeasonAndSessionConfigIsNull(season);
  }

  private java.util.Map<Long, String> buildDisplayNameMap(
      java.util.Collection<Long> userIds, RoundRobin rr) {
    return buildDisplayNameMap(userIds, ladderConfigIdForRoundRobin(rr));
  }

  private Long ladderConfigIdForRoundRobin(RoundRobin rr) {
    LadderConfig ladderConfig = resolveLadderConfig(rr);
    return ladderConfig != null ? ladderConfig.getId() : null;
  }

  private String safeNick(User u) {
    if (u == null) return com.w3llspring.fhpb.web.util.UserPublicName.FALLBACK;
    return com.w3llspring.fhpb.web.util.UserPublicName.forUser(u);
  }

  private String firstAlias(java.util.Set<String> aliases) {
    if (aliases == null || aliases.isEmpty()) {
      return null;
    }
    return aliases.stream()
        .filter(alias -> alias != null && !alias.isBlank())
        .findFirst()
        .orElse(null);
  }

  /**
   * Build the display name for a user in the context of a ladder season. Preference order: - first
   * ladder-scoped court name (if any) - first global court name (if any) - fallback to a static
   * public placeholder The result is formatted as "CourtName-nickName" when a court name exists.
   */
  public String getDisplayNameForUser(User u, LadderSeason season) {
    Long ladderConfigId = null;
    if (season != null && season.getLadderConfig() != null)
      ladderConfigId = season.getLadderConfig().getId();
    return getDisplayNameForUser(u, ladderConfigId);
  }

  public String getDisplayNameForUser(User u, Long ladderConfigId) {
    if (u == null) return "User";

    java.util.Set<String> ladderNames =
        courtNameService.gatherCourtNamesForUser(u.getId(), ladderConfigId);
    String court = ladderNames.stream().findFirst().orElse(null);
    if (court == null) {
      java.util.Set<String> global = courtNameService.gatherCourtNamesForUser(u.getId(), null);
      court = global.stream().findFirst().orElse(null);
    }

    String nick = safeNick(u);
    if (court != null && !court.isBlank()) return court + "-" + nick;
    return nick;
  }

  // Placeholder headline helper removed — not needed when matches are not created

}
