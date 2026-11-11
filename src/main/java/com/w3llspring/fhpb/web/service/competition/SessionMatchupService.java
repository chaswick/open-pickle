package com.w3llspring.fhpb.web.service.competition;

import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
import com.w3llspring.fhpb.web.model.RoundRobinEntry.Position;
import com.w3llspring.fhpb.web.service.*;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import com.w3llspring.fhpb.web.service.matchworkflow.MatchStateTransitionService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinModificationException;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import com.w3llspring.fhpb.web.util.PlayerNames;
import com.w3llspring.fhpb.web.util.PlayerNames.Name;
import com.w3llspring.fhpb.web.util.UserPublicName;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Session-owned schedules: a stable roster, independent games, and replacements in unplayed slots.
 */
@Service
public class SessionMatchupService {
  private final LadderConfigRepository sessions;
  private final LadderMembershipRepository memberships;
  private final UserRepository users;
  private final RoundRobinRepository schedules;
  private final RoundRobinEntryRepository entries;
  private final RoundRobinService roundRobins;
  private final CompetitionSeasonService seasons;
  private final MatchFactory matchFactory;
  private final MatchValidationService validation;
  private final UserMatchLogGateService logGate;
  private final MatchLoggingQuotaService quota;
  private final CompetitionAutoModerationService moderation;
  private final LadderV2Service standings;
  private final TrophyAwardService trophies;
  private final boolean rateLimitEnabled;
  private final MatchStateTransitionService transitions;

  @Value("${fhpb.session.default-hours:48}")
  private long liveSessionHours = 48;

  public SessionMatchupService(
      LadderConfigRepository sessions,
      LadderMembershipRepository memberships,
      UserRepository users,
      RoundRobinRepository schedules,
      RoundRobinEntryRepository entries,
      RoundRobinService roundRobins,
      CompetitionSeasonService seasons,
      MatchFactory matchFactory,
      MatchValidationService validation,
      UserMatchLogGateService logGate,
      MatchLoggingQuotaService quota,
      CompetitionAutoModerationService moderation,
      LadderV2Service standings,
      TrophyAwardService trophies,
      MatchStateTransitionService transitions,
      @Value("${fhpb.match-log.rate-limit.enabled:true}") boolean rateLimitEnabled) {
    this.sessions = sessions;
    this.memberships = memberships;
    this.users = users;
    this.schedules = schedules;
    this.entries = entries;
    this.roundRobins = roundRobins;
    this.seasons = seasons;
    this.matchFactory = matchFactory;
    this.validation = validation;
    this.logGate = logGate;
    this.quota = quota;
    this.moderation = moderation;
    this.standings = standings;
    this.trophies = trophies;
    this.rateLimitEnabled = rateLimitEnabled;
    this.transitions = transitions;
  }

  @Transactional
  public View view(LadderConfig session, User actor, Set<Long> confirmableIds) {
    return view(session, actor, confirmableIds, false);
  }

  @Transactional
  public View view(
      LadderConfig session, User actor, Set<Long> confirmableIds, boolean prepareMore) {
    if (!session.isMatchupSession()) return null;
    List<User> members = roundRobins.listMembersForLadder(session.getId());
    boolean host = isHost(session, actor);
    RoundRobin rr = latest(session);
    if (rr == null) {
      return preparation(session, members, host, true);
    }
    List<RoundRobinEntry> all = loadEntries(rr);
    if (prepareMore
        && host
        && (rr.getEndedAt() != null
            || all.stream().noneMatch(e -> !e.isBye() && e.getMatchId() == null))) {
      return preparation(session, members, host, false);
    }
    Map<Long, Match> matchMap =
        roundRobins.loadMatchesWithParticipants(
            all.stream()
                .map(RoundRobinEntry::getMatchId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    Map<Long, Name> names = playerNames(session, members, all);
    Map<Long, Slot> roster = roster(all, names);
    Set<Long> playingIds = new HashSet<>();
    roster
        .values()
        .forEach(
            s -> {
              if (s.userId() != null) playingIds.add(s.userId());
            });
    List<Member> available =
        members.stream()
            .filter(u -> !playingIds.contains(u.getId()))
            .map(u -> member(u, names))
            .toList();
    Set<Long> activeMemberIds = new HashSet<>();
    members.forEach(u -> activeMemberIds.add(u.getId()));
    List<Slot> slots =
        roster.values().stream()
            .map(
                s ->
                    new Slot(
                        s.id(),
                        s.userId(),
                        s.name(),
                        s.guest(),
                        s.userId() != null && !activeMemberIds.contains(s.userId()),
                        all.stream()
                            .anyMatch(e -> e.getMatchId() == null && containsSlot(e, s.id())),
                        s.publicCode()))
            .toList();
    List<Round> rounds = new ArrayList<>();
    int maxRound = all.stream().mapToInt(RoundRobinEntry::getRoundNumber).max().orElse(0);
    Game next = null;
    int remaining = 0, pending = 0;
    boolean player = playingIds.contains(actor.getId());
    int initialRound = 0;
    for (int r = 1; r <= maxRound; r++) {
      List<Game> games = new ArrayList<>();
      List<Name> byes = new ArrayList<>();
      int gameNumber = 0;
      for (RoundRobinEntry entry : all) {
        if (entry.getRoundNumber() != r) continue;
        if (entry.isBye()) {
          for (Position p : Position.values())
            if (entry.slotId(p) != null) byes.add(playerName(entry, p, names));
          continue;
        }
        Match match = entry.getMatchId() == null ? null : matchMap.get(entry.getMatchId());
        boolean mine = containsUser(entry, actor.getId());
        boolean unplayed = entry.getMatchId() == null;
        boolean canLog =
            rr.getEndedAt() == null && unplayed && (host || (mine && !session.isOrganizerScored()));
        String status =
            unplayed
                ? "Ready to play"
                : match == null
                    ? "Score recorded"
                    : switch (match.getState()) {
                      case CONFIRMED ->
                          match.isOrganizerConfirmed() ? "Confirmed by organizer" : "Confirmed";
                      case NULLIFIED -> "Result removed";
                      case FLAGGED -> "Score disputed";
                      default -> "Awaiting confirmation";
                    };
        Game game =
            new Game(
                entry.getId(),
                r,
                ++gameNumber,
                playerName(entry, Position.A1, names),
                playerName(entry, Position.A2, names),
                playerName(entry, Position.B1, names),
                playerName(entry, Position.B2, names),
                mine,
                canLog,
                status,
                match,
                match != null && confirmableIds.contains(match.getId()),
                match != null && MatchWorkflowRules.canEdit(match, actor, actor.isAdmin()));
        games.add(game);
        if (unplayed) {
          remaining++;
          if (initialRound == 0) initialRound = r;
          if (mine && next == null && rr.getEndedAt() == null) next = game;
        }
        if (match != null
            && match.getState() != MatchState.CONFIRMED
            && match.getState() != MatchState.NULLIFIED) pending++;
      }
      rounds.add(new Round(r, games, byes));
    }
    String revision =
        rr.getId()
            + ":"
            + session.isOrganizerScored()
            + ":"
            + rr.getEndedAt()
            + ":"
            + rr.isTrophiesProcessed()
            + ":"
            + rr.getTrophiesProcessedAt()
            + ":"
            + slots
            + ":"
            + available
            + ":"
            + all.stream().map(e -> e.getId() + ":" + e.getMatchId()).toList()
            + ":"
            + matchMap.values().stream()
                .sorted(Comparator.comparing(Match::getId))
                .map(m -> m.getId() + ":" + m.getState() + ":" + m.getVersion())
                .toList();
    return new View(
        rr.getId(),
        host,
        session.isOrganizerScored(),
        false,
        rr.getEndedAt() != null,
        remaining == 0,
        player,
        !player && !host && rr.getEndedAt() == null,
        maxRound,
        remaining,
        pending,
        initialRound == 0 ? maxRound : initialRound,
        rr.isFixedTeamsFormat() ? "Fixed partners" : "Rotating partners",
        members.stream().map(u -> member(u, names)).toList(),
        slots,
        available,
        rounds,
        next,
        revision);
  }

  @Transactional
  public RoundRobin start(
      Long sessionId,
      User actor,
      RoundRobin.Format format,
      int rounds,
      List<List<Long>> fixedTeams) {
    return start(sessionId, actor, format, rounds, fixedTeams, null);
  }

  @Transactional
  public RoundRobin start(
      Long sessionId,
      User actor,
      RoundRobin.Format format,
      int rounds,
      List<List<Long>> fixedTeams,
      Boolean organizerScored) {
    LadderConfig session = requireSession(sessionId, actor, true);
    if (!session.isMatchupSession())
      throw problem("This session is set up to log games as you go.");
    if (rounds < 1 || rounds > 30) throw problem("Choose between 1 and 30 rounds.");
    RoundRobin previous = latest(session);
    if (organizerScored != null && organizerScored != session.isOrganizerScored()) {
      if (previous != null)
        throw problem(
            "Score confirmation is fixed once matchups start. Create a new session to change it.");
      session.setSecurityLevel(
          organizerScored ? LadderSecurity.SELF_CONFIRM : LadderSecurity.STANDARD);
    }
    if (previous != null && previous.getEndedAt() == null) {
      if (loadEntries(previous).stream().anyMatch(e -> !e.isBye() && e.getMatchId() == null)) {
        throw problem("End the current matchups before starting a new schedule.");
      }
      roundRobins.endRoundRobinEarly(previous.getId());
    }
    List<Long> playerIds =
        roundRobins.listMembersForLadder(sessionId).stream().map(User::getId).toList();
    if (playerIds.size() < 4)
      throw problem("Invite at least four players before starting matchups.");
    if (playerIds.size() > LadderConfig.MAX_SESSION_MEMBERS)
      throw problem(
          "A session can include up to " + LadderConfig.MAX_SESSION_MEMBERS + " players.");
    LadderSeason season = seasons.resolveActiveCompetitionSeason();
    if (season == null) throw problem("There is no active season for recording scores right now.");
    session.setTargetSeasonId(season.getId());
    RoundRobin rr =
        roundRobins.createAndStart(
            sessionId,
            null,
            playerIds,
            rounds,
            actor.getId(),
            format,
            fixedTeams == null ? List.of() : fixedTeams);
    rr.setSessionFlow(true);
    schedules.save(rr);
    // Preparation can happen days ahead. The live session gets its full playing window at start.
    Instant startedAt = Instant.now();
    if (session.getSessionStartedAt() == null) session.setSessionStartedAt(startedAt);
    session.setExpiresAt(startedAt.plusSeconds(Math.max(1, liveSessionHours) * 3600L));
    sessions.save(session);
    return rr;
  }

  @Transactional
  public int replace(
      Long sessionId,
      Long scheduleId,
      Long slotId,
      Long replacementId,
      String guestName,
      User actor) {
    LadderConfig session = requireSession(sessionId, actor, true);
    RoundRobin rr = lockedSchedule(session, scheduleId);
    if (rr.getEndedAt() != null) throw problem("These matchups have ended.");
    List<RoundRobinEntry> all = loadEntries(rr);
    Map<Long, Slot> roster = roster(all, Map.of());
    if (!roster.containsKey(slotId)) throw problem("Choose a player from this schedule.");
    User replacement = null;
    String guest = null;
    if (replacementId != null) {
      if (guestName != null && !guestName.isBlank())
        throw problem("Choose a joined player or enter a guest name.");
      if (!isActiveMember(sessionId, replacementId))
        throw problem("That player has not joined this session.");
      if (roster.values().stream().anyMatch(s -> Objects.equals(s.userId(), replacementId))) {
        throw problem("That player already has a place in the matchups.");
      }
      replacement = users.findById(replacementId).orElseThrow(() -> problem("Player not found."));
    } else {
      guest = guestName == null ? "" : guestName.trim().replaceAll("\\s+", " ");
      if (guest.isBlank()
          || guest.codePointCount(0, guest.length()) > 80
          || guest.chars().anyMatch(Character::isISOControl)) {
        throw problem("Enter a guest name of up to 80 characters.");
      }
    }
    int changed = 0;
    for (RoundRobinEntry entry : all) {
      if (entry.getMatchId() != null) continue; // Pending confirmations are played games too.
      for (Position p : Position.values()) {
        if (Objects.equals(entry.slotId(p), slotId)) {
          entry.replace(p, slotId, replacement, guest);
          changed++;
        }
      }
    }
    if (changed == 0) throw problem("This player has no unplayed matchups left to replace.");
    entries.saveAll(all);
    return changed;
  }

  @Transactional
  public Match recordScore(
      Long sessionId, Long scheduleId, Long entryId, int scoreA, int scoreB, User actor) {
    LadderConfig session = requireSession(sessionId, actor, false);
    RoundRobin rr = lockedSchedule(session, scheduleId);
    if (rr.getEndedAt() != null) throw problem("These matchups have ended.");
    RoundRobinEntry entry =
        entries
            .findByIdWithUsersForUpdate(entryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!Objects.equals(entry.getRoundRobin().getId(), rr.getId()) || entry.isBye()) {
      throw problem("Choose a game from this schedule.");
    }
    boolean host = isHost(session, actor);
    if (!host && (session.isOrganizerScored() || !containsUser(entry, actor.getId())))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (entry.getMatchId() != null)
      throw problem("A score has already been recorded for this game.");
    LadderSeason season = rr.getSeason();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (season == null
        || season.getState() != LadderSeason.State.ACTIVE
        || (season.getStartDate() != null && today.isBefore(season.getStartDate()))
        || (season.getEndDate() != null && today.isAfter(season.getEndDate()))) {
      throw problem("This season has ended. Start a new session to record new games.");
    }
    var scoreCheck = validation.validateScore(scoreA, scoreB);
    if (!scoreCheck.isValid()) throw problem(scoreCheck.getErrorMessage());
    moderation.requireNotBlocked(actor, season);
    for (Position p : Position.values()) {
      if (entry.slotId(p) == null)
        throw problem("This game needs a replacement before its score can be recorded.");
      User player = entry.player(p);
      if (player != null) {
        if (!isActiveMember(sessionId, player.getId()))
          throw problem("Replace the player who left before recording this game.");
        moderation.requireNotBlocked(player, season);
      }
    }
    // The organizer can enter results for several courts together. A locked, single-use
    // assignment bounds organizer scoring by the generated schedule. Organizer-only
    // sessions also bypass the ordinary weekly quota: a 32-player fixed cycle has
    // 120 games, and its organizer must be able to finish the whole schedule.
    var gate = logGate.reserveMatchLogging(actor.getId(), Instant.now(), rateLimitEnabled && !host);
    if (!gate.allowed()) throw problem(gate.message());
    if (!session.isOrganizerScored()) {
      var quotaStatus = quota.evaluate(gate.user());
      if (!quotaStatus.allowed())
        throw problem(
            "You have reached the weekly score-entry limit. Another player can record the score.");
    }
    Match match = new Match();
    match.setSeason(season);
    match.setSourceSessionConfig(session);
    if (session.isOrganizerScored()) match.markOrganizerConfirmed();
    match.setLoggedBy(gate.user());
    match.setA1(entry.getA1());
    match.setA2(entry.getA2());
    match.setB1(entry.getB1());
    match.setB2(entry.getB2());
    match.setA1Guest(entry.getA1() == null);
    match.setA2Guest(entry.getA2() == null);
    match.setB1Guest(entry.getB1() == null);
    match.setB2Guest(entry.getB2() == null);
    match.setA1GuestName(entry.guestName(Position.A1));
    match.setA2GuestName(entry.guestName(Position.A2));
    match.setB1GuestName(entry.guestName(Position.B1));
    match.setB2GuestName(entry.guestName(Position.B2));
    match.setScoreA(scoreA);
    match.setScoreB(scoreB);
    Match saved = matchFactory.createMatch(match);
    if (session.isOrganizerScored() && saved.getState() != MatchState.CONFIRMED)
      throw problem("The score could not be confirmed. Please try again.");
    entry.setMatchId(saved.getId());
    entries.save(entry);
    standings.applyMatch(saved);
    trophies.evaluateMatch(saved);
    return saved;
  }

  @Transactional
  public Match correctScore(
      Long sessionId,
      Long scheduleId,
      Long entryId,
      int scoreA,
      int scoreB,
      Long expectedVersion,
      User actor) {
    LadderConfig session = requireSession(sessionId, actor, true);
    RoundRobin rr = lockedSchedule(session, scheduleId);
    RoundRobinEntry entry =
        entries.findByIdWithUsersForUpdate(entryId).orElseThrow(() -> problem("Game not found."));
    if (entry.getRoundRobin() == null
        || !rr.getId().equals(entry.getRoundRobin().getId())
        || entry.getMatchId() == null)
      throw problem("A recorded game in this schedule is required.");
    Match match =
        roundRobins
            .loadMatchesWithParticipants(List.of(entry.getMatchId()))
            .get(entry.getMatchId());
    if (match == null
        || !match.isOrganizerConfirmed()
        || expectedVersion == null
        || match.getSourceSessionConfig() == null
        || !sessionId.equals(match.getSourceSessionConfig().getId())) {
      throw problem("Reload the session before correcting this score.");
    }
    return transitions.editMatch(
        new MatchStateTransitionService.EditMatchCommand(
            match.getId(),
            actor,
            actor.isAdmin(),
            expectedVersion,
            match.getA1(),
            match.isA1Guest(),
            match.getA2(),
            match.isA2Guest(),
            match.getB1(),
            match.isB1Guest(),
            match.getB2(),
            match.isB2Guest(),
            scoreA,
            scoreB));
  }

  @Transactional
  public void end(Long sessionId, Long scheduleId, User actor) {
    LadderConfig session = requireSession(sessionId, actor, true);
    RoundRobin rr = lockedSchedule(session, scheduleId);
    roundRobins.endRoundRobinEarly(rr.getId());
  }

  private LadderConfig requireSession(Long id, User actor, boolean hostOnly) {
    LadderConfig session = sessions.lockById(id);
    if (session == null
        || !session.isSessionType()
        || actor == null
        || !isActiveMember(id, actor.getId()))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    if (hostOnly && !isHost(session, actor))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (session.getStatus() != LadderConfig.Status.ACTIVE
        || (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(Instant.now()))) {
      throw problem("This session has ended.");
    }
    return session;
  }

  private RoundRobin lockedSchedule(LadderConfig session, Long id) {
    RoundRobin rr =
        schedules
            .findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (rr.getSessionConfig() == null
        || !Objects.equals(rr.getSessionConfig().getId(), session.getId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return rr;
  }

  private boolean isActiveMember(Long sessionId, Long userId) {
    return memberships
        .findByLadderConfigIdAndUserId(sessionId, userId)
        .filter(m -> m.getState() == LadderMembership.State.ACTIVE)
        .isPresent();
  }

  private RoundRobin latest(LadderConfig session) {
    return schedules.findBySessionConfig(session).stream()
        .max(Comparator.comparing(RoundRobin::getCreatedAt).thenComparing(RoundRobin::getId))
        .orElse(null);
  }

  private List<RoundRobinEntry> loadEntries(RoundRobin rr) {
    List<RoundRobinEntry> all =
        new ArrayList<>(entries.findByRoundRobinOrderByRoundNumberAscWithUsers(rr));
    all.sort(
        Comparator.comparingInt(RoundRobinEntry::getRoundNumber)
            .thenComparing(RoundRobinEntry::getId));
    if (!rr.isSessionFlow()) {
      // Convert existing schedules once. Earlier UI paths linked ordinary score entries lazily.
      for (RoundRobinEntry e : all) {
        if (!e.isBye() && e.getMatchId() == null)
          roundRobins
              .findLoggedMatchForEntry(rr, e, rr.getCreatedAt())
              .ifPresent(m -> roundRobins.linkEntryToMatch(e, m.getId()));
      }
      rr.setSessionFlow(true);
      schedules.save(rr);
    }
    return all;
  }

  private Map<Long, Slot> roster(List<RoundRobinEntry> all, Map<Long, Name> names) {
    Map<Long, Slot> result = new LinkedHashMap<>();
    List<RoundRobinEntry> snapshots = new ArrayList<>(all);
    snapshots.sort(Comparator.comparing(e -> e.getMatchId() == null));
    for (RoundRobinEntry entry : snapshots)
      for (Position p : Position.values()) {
        Long id = entry.slotId(p);
        if (id == null) continue;
        User user = entry.player(p);
        result.put(
            id,
            new Slot(
                id,
                user == null ? null : user.getId(),
                playerName(entry, p, names).name(),
                user == null,
                false,
                false,
                playerName(entry, p, names).publicCode()));
      }
    return result;
  }

  private static boolean containsSlot(RoundRobinEntry e, Long id) {
    return Arrays.stream(Position.values()).anyMatch(p -> Objects.equals(e.slotId(p), id));
  }

  private static boolean containsUser(RoundRobinEntry e, Long id) {
    return Arrays.stream(Position.values())
        .anyMatch(p -> e.player(p) != null && Objects.equals(e.player(p).getId(), id));
  }

  private Map<Long, Name> playerNames(
      LadderConfig session, List<User> members, List<RoundRobinEntry> all) {
    Map<Long, User> known = new LinkedHashMap<>();
    members.forEach(u -> known.put(u.getId(), u));
    Map<Long, String> guests = new LinkedHashMap<>();
    for (RoundRobinEntry entry : all) {
      for (Position p : Position.values()) {
        User user = entry.player(p);
        if (user != null) known.putIfAbsent(user.getId(), user);
        else if (entry.slotId(p) != null && entry.guestName(p) != null)
          guests.put(entry.slotId(p), entry.guestName(p));
      }
    }
    return PlayerNames.resolve(
        known.values(),
        roundRobins.buildCourtNameMap(known.keySet(), session.getId()),
        guests.values());
  }

  private Name playerName(RoundRobinEntry e, Position p, Map<Long, Name> names) {
    if (e.player(p) != null)
      return names.getOrDefault(
          e.player(p).getId(), new Name(UserPublicName.forUser(e.player(p)), null));
    String guest = e.guestName(p);
    return new Name(guest == null ? (e.slotId(p) == null ? "" : "Guest") : guest, null);
  }

  private View preparation(
      LadderConfig session, List<User> members, boolean host, boolean configurable) {
    Map<Long, Name> names = playerNames(session, members, List.of());
    return new View(
        null,
        host,
        session.isOrganizerScored(),
        configurable,
        false,
        false,
        false,
        false,
        0,
        0,
        0,
        0,
        "Rotating partners",
        members.stream().map(u -> member(u, names)).toList(),
        List.of(),
        List.of(),
        List.of(),
        null,
        "ready:"
            + session.isOrganizerScored()
            + ":"
            + members.stream().map(u -> u.getId() + ":" + member(u, names)).toList());
  }

  private Member member(User user, Map<Long, Name> names) {
    Name label = names.getOrDefault(user.getId(), new Name(UserPublicName.forUser(user), null));
    return new Member(user.getId(), label.name(), label.publicCode());
  }

  private static boolean isHost(LadderConfig session, User actor) {
    return actor != null
        && (Objects.equals(session.getOwnerUserId(), actor.getId()) || actor.isAdmin());
  }

  private RoundRobinModificationException problem(String message) {
    return new RoundRobinModificationException(message);
  }

  public record Member(Long id, String name, String publicCode) {}

  public record Slot(
      Long id,
      Long userId,
      String name,
      boolean guest,
      boolean left,
      boolean replaceable,
      String publicCode) {}

  public record Game(
      Long id,
      int round,
      int number,
      Name a1,
      Name a2,
      Name b1,
      Name b2,
      boolean mine,
      boolean canLog,
      String status,
      Match match,
      boolean canConfirm,
      boolean canEdit) {}

  public record Round(int number, List<Game> games, List<Name> byes) {}

  public record View(
      Long id,
      boolean host,
      boolean organizerScored,
      boolean scoringConfigurable,
      boolean ended,
      boolean complete,
      boolean player,
      boolean waiting,
      int roundCount,
      int remaining,
      int pending,
      int initialRound,
      String format,
      List<Member> members,
      List<Slot> roster,
      List<Member> available,
      List<Round> rounds,
      Game next,
      String revision) {}
}
