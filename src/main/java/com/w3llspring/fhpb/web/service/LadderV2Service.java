package com.w3llspring.fhpb.web.service;

import static java.time.DayOfWeek.FRIDAY;

import com.w3llspring.fhpb.web.db.BandPositionRepository;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMatchLinkRepository;
import com.w3llspring.fhpb.web.db.LadderRatingChangeRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.BadgeView;
import com.w3llspring.fhpb.web.model.BandPosition;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMatchLink;
import com.w3llspring.fhpb.web.model.LadderRatingChange;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithm;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringExplanation;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringRequest;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringResult;
import com.w3llspring.fhpb.web.service.trophy.AutoTrophyService;
import com.w3llspring.fhpb.web.service.trophy.TrophyBadgeSupport;
import com.w3llspring.fhpb.web.service.trophy.TrophyBadgeViewService;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class LadderV2Service {

  private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/New_York");
  private static final LocalDate SEASON_ANCHOR = LocalDate.of(2024, 1, 5); // Friday anchor
  private static final int DISPLAY_RATING_BASE = 1000;
  private static final Logger log = LoggerFactory.getLogger(LadderV2Service.class);
  private final LadderStandingRepository standingRepo;
  private final LadderSeasonRepository seasonRepo;
  private final LadderMatchLinkRepository linkRepo;
  private final BandPositionRepository bandRepo;
  private final MatchRepository matchRepo;
  private final AutoTrophyService autoTrophyService;
  private final LadderConfigRepository configRepo;
  private final com.w3llspring.fhpb.web.service.MatchConfirmationService matchConfirmationService;
  private final SeasonNameGenerator seasonNameGenerator;
  private final LadderScoringAlgorithms scoringAlgorithms;
  private final LadderRatingChangeRepository ratingChangeRepo;
  private final SeasonCarryOverService seasonCarryOverService;
  private final SeasonStandingsRecalcTracker recalcTracker;
  private final ApplicationEventPublisher eventPublisher;
  private CompetitionSuspiciousMatchDetector competitionSuspiciousMatchDetector;
  private TrophyBadgeViewService trophyBadgeViewService;
  private MatchConfirmationRepository matchConfirmationRepository;

  @Value("${fhpb.admin.user-id:2}")
  private Long defaultAdminUserId;

  @Value("${fhpb.features.story-mode.enabled:true}")
  private boolean storyModeFeatureEnabled = true;

  private final SecureRandom rnd = new SecureRandom();

  @org.springframework.beans.factory.annotation.Autowired
  public LadderV2Service(
      LadderStandingRepository standingRepo,
      LadderSeasonRepository seasonRepo,
      LadderMatchLinkRepository linkRepo,
      BandPositionRepository bandRepo,
      MatchRepository matchRepo,
      AutoTrophyService autoTrophyService,
      LadderConfigRepository configRepo,
      com.w3llspring.fhpb.web.service.MatchConfirmationService matchConfirmationService,
      SeasonNameGenerator seasonNameGenerator,
      LadderScoringAlgorithms scoringAlgorithms,
      LadderRatingChangeRepository ratingChangeRepo,
      SeasonCarryOverService seasonCarryOverService,
      SeasonStandingsRecalcTracker recalcTracker,
      ApplicationEventPublisher eventPublisher,
      CompetitionSuspiciousMatchDetector competitionSuspiciousMatchDetector,
      TrophyBadgeViewService trophyBadgeViewService,
      MatchConfirmationRepository matchConfirmationRepository) {
    this.standingRepo = standingRepo;
    this.seasonRepo = seasonRepo;
    this.linkRepo = linkRepo;
    this.bandRepo = bandRepo;
    this.matchRepo = matchRepo;
    this.autoTrophyService = autoTrophyService;
    this.configRepo = configRepo;
    this.matchConfirmationService = matchConfirmationService;
    this.seasonNameGenerator = seasonNameGenerator;
    this.scoringAlgorithms = scoringAlgorithms;
    this.ratingChangeRepo = ratingChangeRepo;
    this.seasonCarryOverService = seasonCarryOverService;
    this.recalcTracker = recalcTracker;
    this.eventPublisher = eventPublisher;
    this.competitionSuspiciousMatchDetector = competitionSuspiciousMatchDetector;
    this.trophyBadgeViewService = trophyBadgeViewService;
    this.matchConfirmationRepository = matchConfirmationRepository;
  }

  public LadderV2Service(
      LadderStandingRepository standingRepo,
      LadderSeasonRepository seasonRepo,
      LadderMatchLinkRepository linkRepo,
      BandPositionRepository bandRepo,
      MatchRepository matchRepo,
      AutoTrophyService autoTrophyService,
      LadderConfigRepository configRepo,
      com.w3llspring.fhpb.web.service.MatchConfirmationService matchConfirmationService,
      SeasonNameGenerator seasonNameGenerator,
      LadderScoringAlgorithms scoringAlgorithms,
      LadderRatingChangeRepository ratingChangeRepo,
      SeasonCarryOverService seasonCarryOverService,
      SeasonStandingsRecalcTracker recalcTracker,
      ApplicationEventPublisher eventPublisher) {
    this(
        standingRepo,
        seasonRepo,
        linkRepo,
        bandRepo,
        matchRepo,
        autoTrophyService,
        configRepo,
        matchConfirmationService,
        seasonNameGenerator,
        scoringAlgorithms,
        ratingChangeRepo,
        seasonCarryOverService,
        recalcTracker,
        eventPublisher,
        null,
        null,
        null);
  }

  private LadderConfig ensureDefaultConfig() {
    // 1) If any config already exists, re-use the oldest (acts as "universal" by
    // default)
    if (configRepo.count() > 0) {
      return configRepo
          .findAll(PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "id")))
          .getContent()
          .get(0);
    }

    // 2) Otherwise, create ONE default config with a unique invite (retry a few
    // times)
    LadderConfig cfg = new LadderConfig();
    cfg.setTitle("Universal Ladder");
    cfg.setOwnerUserId(defaultAdminUserId != null ? defaultAdminUserId : 1L);
    cfg.setStatus(LadderConfig.Status.ACTIVE);

    for (int i = 0; i < 6; i++) {
      String code = generateInviteCandidate(); // e.g. "univ-4827"
      if (configRepo.findByInviteCode(code).isPresent()) {
        continue; // try another candidate
      }
      cfg.setInviteCode(code);
      try {
        return configRepo.save(cfg);
      } catch (DataIntegrityViolationException ex) {
        // race or rare collision - loop and try another code
      }
    }

    // 3) Last-ditch: another thread may have created one while we were looping
    return configRepo
        .findAll(PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "id")))
        .getContent()
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Unable to create or locate default LadderConfig"));
  }

  private String generateInviteCandidate() {
    // Minimal, readable, and unique enough with a quick pre-check:
    // feel free to replace with your LadderInviteGenerator later.
    int n = rnd.nextInt(10_000); // 0000..9999
    return String.format("univ-%04d", n);
  }

  @Transactional
  public void refreshDisplayNameArtifacts(User user) {
    if (user == null || user.getId() == null) {
      return;
    }

    List<LadderStanding> standings = standingRepo.findByUser(user);
    if (!standings.isEmpty()) {
      String name = resolveStandingDisplayName(user);
      standings.forEach(s -> s.setDisplayName(name));
      standingRepo.saveAll(standings);
    }

    List<Match> matches = matchRepo.findByParticipant(user);
    if (!matches.isEmpty()) {
      for (Match match : matches) {
        linkRepo
            .findByMatch(match)
            .ifPresent(
                link -> {
                  link.setHeadline(buildHeadline(match));
                  linkRepo.save(link);
                });
      }
    }
  }

  @Transactional
  public void applyMatch(Match match) {
    LadderSeason season = ensureSeasonAttached(match, true);
    if (season == null) {
      return; // no season window could be determined
    }

    // Always keep the ladder match link up to date so the UI can render provisional
    // and confirmed matches consistently.
    LadderMatchLink link = linkRepo.findByMatch(match).orElseGet(LadderMatchLink::new);
    link.setSeason(season);
    link.setMatch(match);
    link.setHeadline(buildHeadline(match));
    linkRepo.save(link);

    // Standings are deterministic from CONFIRMED match history only.
    // Always queue a recalc for confirmed matches, even when excluded:
    // an edit can flip include/exclude state and must be replayed.
    if (match.getState() != MatchState.CONFIRMED) {
      return;
    }

    if (competitionSuspiciousMatchDetector != null) {
      try {
        competitionSuspiciousMatchDetector.reviewConfirmedCompetitionMatch(match);
      } catch (Exception ex) {
        log.warn("Suspicious-match review failed for confirmed match {}", match.getId(), ex);
      }
    }

    eventPublisher.publishEvent(new SeasonStandingsRecalcRequestedEvent(season.getId()));
  }

  // === NEW: nullify a previously applied/confirmed match ===
  @Transactional
  public void nullifyMatch(Match match) {
    nullifyMatch(match, false);
  }

  @Transactional
  public void nullifyMatch(Match match, boolean preserveConfirmationRows) {
    LadderSeason season = ensureSeasonAttached(match, true);
    if (season == null) {
      return;
    }

    if (!preserveConfirmationRows && matchConfirmationRepository != null) {
      try {
        matchConfirmationRepository.deleteByMatch(match);
      } catch (Exception ex) {
        log.warn("Failed to delete confirmation rows for nullified match {}", match.getId(), ex);
      }
    }

    if (match.getState() != MatchState.NULLIFIED) {
      match.setState(MatchState.NULLIFIED);
      matchRepo.save(match);
    }

    // Update link headline to reflect nullification (and ensure link exists)
    // Use findByMatchAnyState to find existing link even if match is NULLIFIED
    LadderMatchLink link = linkRepo.findByMatchAnyState(match).orElseGet(LadderMatchLink::new);
    link.setSeason(season);
    link.setMatch(match);
    link.setHeadline("[Nullified] " + buildHeadline(match));
    linkRepo.save(link);

    if (competitionSuspiciousMatchDetector != null) {
      try {
        competitionSuspiciousMatchDetector.clearFlags(match);
      } catch (Exception ex) {
        log.warn(
            "Failed to clear suspicious-match flags for nullified match {}", match.getId(), ex);
      }
    }

    // Deterministic rebuild from confirmed match history (queued asynchronously per season).
    eventPublisher.publishEvent(new SeasonStandingsRecalcRequestedEvent(season.getId()));
  }

  public LadderSeason startSeason(LocalDate startDate) {
    LocalDate start = seasonStartFor(startDate);
    return createSeasonIfAbsent(start);
  }

  private void storeMatchDeltas(Match match, Map<User, Integer> adjustments) {
    if (match == null) {
      return;
    }

    Map<Long, Integer> deltasById =
        adjustments.entrySet().stream()
            .filter(e -> e.getKey() != null && e.getKey().getId() != null)
            .collect(
                Collectors.toMap(
                    e -> e.getKey().getId(),
                    Map.Entry::getValue,
                    (existing, replacement) -> existing));

    match.setA1Delta(resolveParticipantDelta(match.getA1(), match.isA1Guest(), deltasById));
    match.setA2Delta(resolveParticipantDelta(match.getA2(), match.isA2Guest(), deltasById));
    match.setB1Delta(resolveParticipantDelta(match.getB1(), match.isB1Guest(), deltasById));
    match.setB2Delta(resolveParticipantDelta(match.getB2(), match.isB2Guest(), deltasById));

    matchRepo.save(match);
  }

  private Integer resolveParticipantDelta(User user, boolean guest, Map<Long, Integer> deltasById) {
    if (guest || user == null || user.getId() == null) {
      return 0;
    }
    return deltasById.getOrDefault(user.getId(), 0);
  }

  private List<LadderRatingChange> buildRatingChanges(
      LadderSeason season,
      Match match,
      Map<User, Integer> adjustments,
      Map<Long, LadderScoringExplanation> explanations,
      Map<Long, LadderStanding> standingsByUser) {
    if (season == null || match == null || adjustments == null || adjustments.isEmpty()) {
      return List.of();
    }

    List<LadderRatingChange> changes = new ArrayList<>();
    Instant occurredAt =
        match.getPlayedAt() != null
            ? match.getPlayedAt()
            : (match.getCreatedAt() != null ? match.getCreatedAt() : Instant.now());
    for (Map.Entry<User, Integer> entry : adjustments.entrySet()) {
      User user = entry.getKey();
      if (user == null || user.getId() == null) {
        continue;
      }

      LadderStanding standing = standingsByUser.get(user.getId());
      int beforePoints = standing != null ? standing.getPoints() : 0;
      int delta = entry.getValue() == null ? 0 : entry.getValue();
      int afterPoints = beforePoints + delta;

      LadderRatingChange change = new LadderRatingChange();
      change.setSeason(season);
      change.setMatch(match);
      change.setUser(user);
      change.setOccurredAt(occurredAt);
      change.setRatingBefore(DISPLAY_RATING_BASE + beforePoints);
      change.setRatingDelta(delta);
      change.setRatingAfter(DISPLAY_RATING_BASE + afterPoints);
      change.setSummary(
          buildRatingChangeSummary(
              match, user, delta, change.getRatingBefore(), change.getRatingAfter()));
      change.setDetails(
          buildRatingChangeDetails(explanations == null ? null : explanations.get(user.getId())));
      changes.add(change);
    }
    return changes;
  }

  private String buildRatingChangeSummary(
      Match match, User user, int delta, int ratingBefore, int ratingAfter) {
    boolean onTeamA = isParticipantOnTeamA(match, user);
    int myScore = onTeamA ? match.getScoreA() : match.getScoreB();
    int theirScore = onTeamA ? match.getScoreB() : match.getScoreA();
    String outcome = myScore > theirScore ? "Won" : "Lost";
    String matchupSummary = buildRatingChangeMatchupSummary(match, user, onTeamA);
    String matchIdSummary =
        match != null && match.getId() != null ? String.format(" (match #%d)", match.getId()) : "";
    return String.format(
        "%s %d-%d%s%s: %+d rating (%d -> %d)",
        outcome,
        myScore,
        theirScore,
        matchupSummary,
        matchIdSummary,
        delta,
        ratingBefore,
        ratingAfter);
  }

  private String buildRatingChangeMatchupSummary(Match match, User user, boolean onTeamA) {
    if (match == null) {
      return "";
    }
    String partnerSummary =
        onTeamA
            ? teamAuditLabel(
                match.getA1(), match.isA1Guest(), match.getA2(), match.isA2Guest(), user)
            : teamAuditLabel(
                match.getB1(), match.isB1Guest(), match.getB2(), match.isB2Guest(), user);
    String opponentSummary =
        onTeamA
            ? teamAuditLabel(
                match.getB1(), match.isB1Guest(), match.getB2(), match.isB2Guest(), null)
            : teamAuditLabel(
                match.getA1(), match.isA1Guest(), match.getA2(), match.isA2Guest(), null);

    StringBuilder summary = new StringBuilder();
    if (!partnerSummary.isBlank()) {
      summary.append(" with ").append(partnerSummary);
    }
    if (!opponentSummary.isBlank()) {
      summary.append(" against ").append(opponentSummary);
    }
    return summary.toString();
  }

  private String teamAuditLabel(
      User p1, boolean guest1, User p2, boolean guest2, User excludedUser) {
    List<String> names = new ArrayList<>();
    addAuditParticipant(names, p1, guest1, excludedUser);
    addAuditParticipant(names, p2, guest2, excludedUser);
    if (names.isEmpty()) {
      return "";
    }
    if (names.size() > 1 && names.stream().allMatch("Guest"::equals)) {
      return "Guest Squad";
    }
    return String.join(" and ", names);
  }

  private void addAuditParticipant(
      List<String> names, User participant, boolean guest, User excludedUser) {
    if (guest) {
      names.add("Guest");
      return;
    }
    if (participant == null || sameUser(participant, excludedUser)) {
      return;
    }
    names.add(resolveStandingDisplayName(participant));
  }

  private String buildRatingChangeDetails(LadderScoringExplanation explanation) {
    if (explanation == null || explanation.getSteps() == null || explanation.getSteps().isEmpty()) {
      return "No explanation available.";
    }
    return String.join("\n", explanation.getSteps());
  }

  private boolean isParticipantOnTeamA(Match match, User user) {
    if (match == null || user == null || user.getId() == null) {
      return false;
    }
    return sameUser(match.getA1(), user) || sameUser(match.getA2(), user);
  }

  private boolean sameUser(User left, User right) {
    if (left == null || right == null || left.getId() == null || right.getId() == null) {
      return false;
    }
    return Objects.equals(left.getId(), right.getId());
  }

  private List<LadderStanding> rerankSeason(LadderSeason season) {
    List<LadderStanding> standings =
        new ArrayList<>(standingRepo.findBySeasonOrderByRankNoAsc(season));
    standings.sort(
        (a, b) -> {
          int pointCompare = Integer.compare(b.getPoints(), a.getPoints());
          if (pointCompare != 0) return pointCompare;
          return normalizeDisplayName(a.getDisplayName())
              .compareToIgnoreCase(normalizeDisplayName(b.getDisplayName()));
        });
    AtomicInteger counter = new AtomicInteger(1);
    standings.forEach(row -> row.setRank(counter.getAndIncrement()));
    standingRepo.saveAll(standings);
    return standings;
  }

  private LadderScoringResult computeDeltaResult(
      LadderScoringAlgorithm algorithm, Match match, LadderSeason season) {
    Map<Long, LadderStanding> standingsByUser;
    Map<Long, BandPosition> bandByUser;
    Map<Integer, Integer> topQuartileLimit;
    int maxBandIndex;

    standingsByUser =
        standingRepo.findBySeasonOrderByRankNoAsc(season).stream()
            .filter(s -> s.getUser() != null && s.getUser().getId() != null)
            .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s, (a, b) -> a));

    List<BandPosition> allPositions = bandRepo.findBySeason(season);
    bandByUser = new HashMap<>();
    Map<Integer, Integer> bandSizes = new HashMap<>();
    maxBandIndex = 1;
    for (BandPosition bp : allPositions) {
      if (bp.getUser() == null || bp.getUser().getId() == null) {
        continue;
      }
      if (!standingsByUser.containsKey(bp.getUser().getId())) {
        continue;
      }
      bandByUser.put(bp.getUser().getId(), bp);
      int bandIndex = resolveBandIndex(bp, 1);
      maxBandIndex = Math.max(maxBandIndex, bandIndex);
      bandSizes.merge(bandIndex, 1, Integer::sum);
    }
    if (maxBandIndex <= 0) {
      maxBandIndex = 1;
    }
    topQuartileLimit = new HashMap<>();
    bandSizes.forEach(
        (band, count) -> topQuartileLimit.put(band, Math.max(1, (int) Math.ceil(count / 4.0))));

    List<User> activeParticipants = collectStandingsParticipants(match);
    Instant matchTime = match.getPlayedAt() != null ? match.getPlayedAt() : Instant.now();
    Instant tentativeStart = matchTime.minus(algorithm.historyWindow());
    final Instant historyStart =
        tentativeStart.isBefore(Instant.EPOCH) ? Instant.EPOCH : tentativeStart;
    final Instant historyEnd = matchTime;

    LadderSeason historySeason = season != null ? season : match != null ? match.getSeason() : null;
    List<Match> history;
    if (activeParticipants.isEmpty() || historySeason == null) {
      history = List.of();
    } else {
      history =
          matchRepo.findRecentPlayedMatchesForPlayersInSeason(
              historySeason, activeParticipants, historyStart, historyEnd);
    }
    List<Match> usefulHistory =
        history.stream()
            .filter(m -> m.getState() != MatchState.NULLIFIED)
            .filter(m -> m.getState() == null || m.getState() == MatchState.CONFIRMED)
            .collect(Collectors.toList());

    LadderScoringResult rawResult =
        algorithm.score(
            new LadderScoringRequest(
                match,
                standingsByUser,
                bandByUser,
                topQuartileLimit,
                maxBandIndex,
                season != null ? season.getId() : null,
                Map.of(),
                usefulHistory));
    if (rawResult != null) {
      return rawResult;
    }
    return LadderScoringResult.empty(
        algorithm.computeBaseStep(match), algorithm.computeGuestScale(match));
  }

  private LadderScoringAlgorithm resolveScoringAlgorithm(LadderSeason season, Match match) {
    if (season != null) {
      return scoringAlgorithms.resolve(season);
    }
    LadderSeason matchSeason = match != null ? match.getSeason() : null;
    if (matchSeason != null) {
      return scoringAlgorithms.resolve(matchSeason);
    }
    return scoringAlgorithms.defaultAlgorithm();
  }

  private void updateBandPositions(
      List<LadderStanding> standings,
      LadderSeason season,
      Map<Long, BandPosition> bandPositionsByUserId) {
    if (standings.isEmpty()) {
      return;
    }
    if (season == null || season.getId() == null) {
      return;
    }
    int total = standings.size();
    int bandCount = BandDivisionSupport.determineBandCount(total);
    int bandSize = (int) Math.ceil((double) total / bandCount);

    for (LadderStanding standing : standings) {
      User user = standing.getUser();
      if (user == null) continue;

      int bandIndex = Math.min(bandCount, ((standing.getRank() - 1) / bandSize) + 1);
      int position = ((standing.getRank() - 1) % bandSize) + 1;

      BandPosition bp = resolveBandPosition(bandPositionsByUserId, season, user);
      if (bp == null) {
        continue;
      }

      bp.setBandIndex(bandIndex);
      bp.setPositionInBand(position);
      bandRepo.save(bp);
    }
  }

  private void replayDailyMomentum(
      List<LadderStanding> standings,
      Map<User, Integer> adjustments,
      LadderSeason season,
      Map<Long, BandPosition> bandPositionsByUserId) {
    if (standings == null || standings.isEmpty() || season == null || season.getId() == null) {
      return;
    }

    Map<Long, Integer> adjustmentsByUserId =
        adjustments == null
            ? Map.of()
            : adjustments.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getId() != null)
                .collect(
                    Collectors.toMap(
                        entry -> entry.getKey().getId(),
                        entry -> entry.getValue() == null ? 0 : entry.getValue(),
                        (left, right) -> right));

    for (LadderStanding standing : standings) {
      User user = standing.getUser();
      if (user == null || user.getId() == null) {
        continue;
      }
      BandPosition bp = resolveBandPosition(bandPositionsByUserId, season, user);
      if (bp == null) {
        continue;
      }
      int delta = adjustmentsByUserId.getOrDefault(user.getId(), 0);
      bp.setDailyMomentum(nextMomentum(bp.getDailyMomentum(), delta));
    }
  }

  private BandPosition resolveBandPosition(
      Map<Long, BandPosition> bandPositionsByUserId, LadderSeason season, User user) {
    if (bandPositionsByUserId == null || user == null || user.getId() == null) {
      return loadOrCreateBandPosition(season, user);
    }
    BandPosition existing = bandPositionsByUserId.get(user.getId());
    if (existing != null) {
      return existing;
    }
    BandPosition created = loadOrCreateBandPosition(season, user);
    if (created != null) {
      bandPositionsByUserId.put(user.getId(), created);
    }
    return created;
  }

  private int nextMomentum(Integer currentMomentum, int delta) {
    int momentum = currentMomentum != null ? currentMomentum : 0;
    if (delta > 0) {
      return Math.min(momentum + 2, 20);
    }
    if (delta < 0) {
      return Math.max(momentum - 2, -20);
    }
    if (momentum > 0) {
      return momentum - 1;
    }
    if (momentum < 0) {
      return momentum + 1;
    }
    return 0;
  }

  /**
   * Racy-safe lookup/create for season-scoped BandPosition rows. Multiple async season recalcs can
   * try to create the same row at once.
   */
  private BandPosition loadOrCreateBandPosition(LadderSeason season, User user) {
    if (season == null || season.getId() == null || user == null || user.getId() == null) {
      return null;
    }

    BandPosition existing = bandRepo.findBySeasonAndUser(season, user).orElse(null);
    if (existing != null) {
      return existing;
    }

    BandPosition created = new BandPosition();
    created.setSeason(season);
    created.setUser(user);
    created.setDailyMomentum(0);
    try {
      return bandRepo.saveAndFlush(created);
    } catch (DataIntegrityViolationException ex) {
      // Another async worker inserted this user first; re-read and continue.
      return bandRepo.findBySeasonAndUser(season, user).orElse(null);
    }
  }

  private List<User> collectStandingsParticipants(Match match) {
    if (match == null) {
      return List.of();
    }
    List<User> participants = new ArrayList<>(4);
    addIfRealPlayer(participants, match.getA1(), match.isA1Guest());
    addIfRealPlayer(participants, match.getA2(), match.isA2Guest());
    addIfRealPlayer(participants, match.getB1(), match.isB1Guest());
    addIfRealPlayer(participants, match.getB2(), match.isB2Guest());

    if (participants.isEmpty()) {
      return List.of();
    }
    Map<Long, User> byId = new LinkedHashMap<>();
    for (User participant : participants) {
      if (participant != null && participant.getId() != null) {
        byId.putIfAbsent(participant.getId(), participant);
      }
    }
    return new ArrayList<>(byId.values());
  }

  private void addIfRealPlayer(List<User> list, User candidate, boolean guest) {
    if (candidate != null && !guest && candidate.getId() != null) {
      list.add(candidate);
    }
  }

  private int resolveBandIndex(BandPosition position, int fallback) {
    if (position == null || position.getBandIndex() == null || position.getBandIndex() <= 0) {
      return Math.max(1, fallback);
    }
    return Math.max(1, position.getBandIndex());
  }

  private int resolvePositionInBand(BandPosition position) {
    if (position == null || position.getPositionInBand() == null) {
      return Integer.MAX_VALUE;
    }
    return Math.max(1, position.getPositionInBand());
  }

  public List<LadderRow> buildDisplayRows(List<LadderStanding> standings) {
    if (standings == null || standings.isEmpty()) {
      return List.of();
    }
    int bandCount = BandDivisionSupport.determineBandCount(standings.size());
    int bandSize = (int) Math.ceil((double) standings.size() / bandCount);
    Map<Integer, String> bandNames = BandDivisionSupport.resolveBandNames(bandCount);

    List<Long> bandUserIds =
        standings.stream()
            .map(LadderStanding::getUser)
            .filter(Objects::nonNull)
            .map(User::getId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

    LadderSeason displaySeason =
        standings.stream()
            .map(LadderStanding::getSeason)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    Map<Long, BandPosition> momentumByUserId =
        (displaySeason == null || bandUserIds.isEmpty())
            ? Map.of()
            : bandRepo.findBySeasonAndUserIdIn(displaySeason, bandUserIds).stream()
                .filter(bp -> bp.getUser() != null && bp.getUser().getId() != null)
                .collect(
                    Collectors.toMap(
                        bp -> bp.getUser().getId(), bp -> bp, (a, b) -> a, LinkedHashMap::new));

    Map<Long, BadgeView> badgeViewsByTrophyId =
        trophyBadgeViewService != null
            ? trophyBadgeViewService.buildBadgeViewMap(collectBadgeTrophyIds(standings))
            : Map.of();

    List<LadderRow> rows = new ArrayList<>();
    int currentBand = -1;
    for (LadderStanding standing : standings) {
      LadderRow row = new LadderRow();
      row.rank = standing.getRank();
      row.displayName = standing.getDisplayName();
      row.points = standing.getPoints();
      row.userPublicCode = standing.getUser() != null ? standing.getUser().getPublicCode() : null;
      row.userId = standing.getUser() != null ? standing.getUser().getId() : null;
      row.badgeViews =
          trophyBadgeViewService != null
              ? trophyBadgeViewService.badgeViews(standing.getUser(), badgeViewsByTrophyId)
              : TrophyBadgeSupport.badgeViewsFromLoadedUser(standing.getUser());

      int bandIndex = Math.min(bandCount, ((standing.getRank() - 1) / bandSize) + 1);
      row.bandIndex = bandIndex;
      row.bandLabel = bandIndex <= 0 ? "Unseeded" : "Division " + bandIndex;
      int normalizedBand = bandIndex <= 0 ? 0 : Math.min(bandIndex, 3);
      row.bandCssClass = "ladder-band-" + normalizedBand;
      row.bandName = bandNames.getOrDefault(bandIndex, row.bandLabel);
      row.bandLabel = row.bandName;
      row.showBandHeader = bandIndex != currentBand;

      if (row.showBandHeader) {
        currentBand = bandIndex;
      }

      if (standing.getUser() != null) {
        Long userId = standing.getUser().getId();
        BandPosition bp = (userId != null) ? momentumByUserId.get(userId) : null;
        row.momentum = bp != null && bp.getDailyMomentum() != null ? bp.getDailyMomentum() : 0;
      } else {
        row.momentum = 0;
      }
      rows.add(row);
    }
    return rows;
  }

  private List<Long> collectBadgeTrophyIds(List<LadderStanding> standings) {
    if (standings == null || standings.isEmpty()) {
      return List.of();
    }
    List<Long> ids = new ArrayList<>();
    for (LadderStanding standing : standings) {
      if (standing == null || standing.getUser() == null) {
        continue;
      }
      addBadgeTrophyId(ids, standing.getUser().getBadgeSlot1TrophyId());
    }
    return ids;
  }

  private void addBadgeTrophyId(List<Long> ids, Long trophyId) {
    if (trophyId != null) {
      ids.add(trophyId);
    }
  }

  public static class LadderRow {
    public int rank;
    public String displayName;
    public String userPublicCode;
    public int points;
    public int bandIndex;
    public String bandLabel;
    public String bandCssClass;
    public String bandName;
    public boolean showBandHeader;
    public int momentum;
    public Long userId;
    public boolean competitionSafeDisplayNameActive;
    public List<BadgeView> badgeViews = List.of();
  }

  private LadderSeason resolveSeason(Match match) {
    LocalDate matchDate = LocalDateTime.ofInstant(match.getPlayedAt(), DEFAULT_ZONE).toLocalDate();
    return seasonRepo
        .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
            matchDate, matchDate)
        .orElseGet(() -> ensureSeasonForDate(matchDate));
  }

  private LadderSeason ensureSeasonAttached(Match match, boolean persist) {
    LadderSeason season = match.getSeason();
    if (season != null) {
      return season;
    }
    LadderSeason resolved = resolveSeason(match);
    if (resolved != null) {
      match.setSeason(resolved);
      if (persist) {
        matchRepo.save(match);
      }
    }
    return resolved;
  }

  private LadderSeason ensureSeasonForDate(LocalDate date) {
    LocalDate start = seasonStartFor(date);
    return createSeasonIfAbsent(start);
  }

  private LadderSeason createSeasonIfAbsent(LocalDate start) {
    return seasonRepo
        .findByStartDate(start)
        .orElseGet(
            () -> {
              LadderConfig ladder = ensureDefaultConfig();
              LadderSeason season = new LadderSeason();
              season.setStartDate(start);
              season.setEndDate(start.plusWeeks(6).minusDays(1));
              season.setName(seasonNameGenerator.generate(start));
              season.setLadderConfig(ladder);
              season.setStoryModeEnabled(
                  storyModeFeatureEnabled && ladder.isStoryModeDefaultEnabled());
              LadderSeason saved = seasonRepo.save(season);
              try {
                autoTrophyService.generateSeasonTrophies(saved);
              } catch (Exception ex) {
                log.warn(
                    "Unable to auto-generate trophies for season {}: {}",
                    saved.getId(),
                    ex.getMessage());
              }
              return saved;
            });
  }

  private LocalDate seasonStartFor(LocalDate date) {
    LocalDate friday = date.with(TemporalAdjusters.previousOrSame(FRIDAY));
    long weeksFromAnchor = ChronoUnit.WEEKS.between(SEASON_ANCHOR, friday);
    long block = Math.floorDiv(weeksFromAnchor, 6) * 6;
    return SEASON_ANCHOR.plusWeeks(block);
  }

  private String resolveStandingDisplayName(User user) {
    if (user == null) {
      return "Unknown Player";
    }
    String nick = normalizeDisplayName(user.getNickName());
    if (!nick.isBlank()) {
      return nick;
    }
    return com.w3llspring.fhpb.web.util.UserPublicName.forUser(user);
  }

  private String normalizeDisplayName(String value) {
    return value == null ? "" : value.trim();
  }

  private boolean isEffectivelyExcludedFromStandings(Match match) {
    if (match == null) {
      return false;
    }
    if (match.isExcludeFromStandings()) {
      return true;
    }
    LadderSeason season = match.getSeason();
    LadderConfig cfg = season != null ? season.getLadderConfig() : null;
    boolean allowGuestOnlyPersonalRecords =
        cfg != null
            && cfg.isAllowGuestOnlyPersonalMatches()
            && cfg.getSecurityLevel() != null
            && com.w3llspring.fhpb.web.model.LadderSecurity.normalize(cfg.getSecurityLevel())
                .isSelfConfirm();
    return allowGuestOnlyPersonalRecords && match.hasGuestOnlyOpposingTeam();
  }

  private String buildHeadline(Match match) {
    boolean teamAWon = match.isTeamAWinner();
    String winners =
        teamAWon
            ? teamLabel(match.getA1(), match.isA1Guest(), match.getA2(), match.isA2Guest())
            : teamLabel(match.getB1(), match.isB1Guest(), match.getB2(), match.isB2Guest());
    String losers =
        teamAWon
            ? teamLabel(match.getB1(), match.isB1Guest(), match.getB2(), match.isB2Guest())
            : teamLabel(match.getA1(), match.isA1Guest(), match.getA2(), match.isA2Guest());
    int winnerScore = teamAWon ? match.getScoreA() : match.getScoreB();
    int loserScore = teamAWon ? match.getScoreB() : match.getScoreA();
    return winners + " def. " + losers + " (" + winnerScore + "-" + loserScore + ")";
  }

  private String teamLabel(User p1, boolean guest1, User p2, boolean guest2) {
    List<String> names = new ArrayList<>();
    if (p1 != null && !guest1) {
      names.add(p1.getNickName());
    }
    if (p2 != null && !guest2) {
      names.add(p2.getNickName());
    }
    if (names.isEmpty()) {
      names.add("Guest Squad");
    }
    return String.join(" & ", names);
  }

  public void onMatchNullified(com.w3llspring.fhpb.web.model.Match m) {
    // Entry point used by controllers: mark nullified + deterministic recalc.
    nullifyMatch(m);
  }

  @Transactional
  public void recalcSeasonStandings(LadderSeason season) {
    if (season == null) return;
    final LadderSeason recalcSeason = hydrateSeasonForRecalc(season);
    Long seasonId = recalcSeason.getId();
    boolean trackerEnabled = false;
    try {
      trackerEnabled = TransactionAspectSupport.currentTransactionStatus().isNewTransaction();
    } catch (Exception ignored) {
      trackerEnabled = false;
    }
    if (trackerEnabled) {
      markRecalcStartedSafely(seasonId);
    }
    final boolean shouldTrack = trackerEnabled;
    final boolean[] finishRegistered = new boolean[] {false};
    if (shouldTrack && TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              markRecalcFinishedSafely(seasonId);
            }
          });
      finishRegistered[0] = true;
    }
    try {

      Map<Long, SeasonCarryOverService.CarryOverSeed> carryOverSeeds =
          seasonCarryOverService.resolveCarryOverSeeds(recalcSeason);

      // 1) Load or create all standings for users we've ever seen in this season
      // (If you prefer, you can lazy-create on demand while replaying.)
      List<LadderStanding> existing = standingRepo.findBySeasonOrderByRankNoAsc(recalcSeason);
      int existingStandingsCount = existing.size();
      Map<Long, LadderStanding> byUserId = new HashMap<>();
      for (LadderStanding s : existing) {
        User standingUser = s.getUser();
        if (standingUser == null || standingUser.getId() == null) {
          continue;
        }
        // reset points to season baseline before replay
        SeasonCarryOverService.CarryOverSeed seed = carryOverSeeds.get(standingUser.getId());
        s.setPoints(seed != null ? seed.points() : 0);
        byUserId.put(standingUser.getId(), s);
      }
      if (!existing.isEmpty()) {
        standingRepo.saveAll(existing);
      }

      List<LadderStanding> baselineRows = new ArrayList<>();
      for (Map.Entry<Long, SeasonCarryOverService.CarryOverSeed> entry :
          carryOverSeeds.entrySet()) {
        Long userId = entry.getKey();
        if (userId == null || byUserId.containsKey(userId)) {
          continue;
        }
        SeasonCarryOverService.CarryOverSeed seed = entry.getValue();
        if (seed == null || seed.user() == null || seed.user().getId() == null) {
          continue;
        }
        LadderStanding fresh = new LadderStanding();
        fresh.setSeason(recalcSeason);
        fresh.setUser(seed.user());
        fresh.setDisplayName(seed.displayName());
        fresh.setPoints(seed.points());
        byUserId.put(userId, fresh);
        baselineRows.add(fresh);
      }
      if (!baselineRows.isEmpty()) {
        standingRepo.saveAll(baselineRows);
      }

      // 2) Reset season-scoped form so it can be rebuilt from the replay below.
      Map<Long, BandPosition> bandPositionsByUserId =
          bandRepo.findBySeason(recalcSeason).stream()
              .filter(bp -> bp.getUser() != null && bp.getUser().getId() != null)
              .collect(
                  Collectors.toMap(
                      bp -> bp.getUser().getId(), bp -> bp, (left, right) -> left, HashMap::new));
      bandPositionsByUserId.values().forEach(bp -> bp.setDailyMomentum(0));
      if (ratingChangeRepo != null) {
        ratingChangeRepo.deleteBySeason(recalcSeason);
      }

      // 3) Replay all confirmed matches in chronological order
      List<Match> matches = matchRepo.findConfirmedForSeasonChrono(recalcSeason);
      LadderScoringAlgorithm seasonAlgorithm = resolveScoringAlgorithm(recalcSeason, null);
      if (log.isDebugEnabled()) {
        log.debug(
            "Recalculating season {} (ladder={}, algorithm={}): {} confirmed matches, {} existing standings",
            recalcSeason.getId(),
            recalcSeason.getLadderConfig() != null ? recalcSeason.getLadderConfig().getId() : null,
            seasonAlgorithm.key(),
            matches.size(),
            existingStandingsCount);
      }
      for (Match m : matches) {
        boolean excluded = isEffectivelyExcludedFromStandings(m);
        if (excluded && !m.isExcludeFromStandings()) {
          m.setExcludeFromStandings(true);
          matchRepo.save(m);
        }
        // recompute deltas for this historical match
        LadderScoringResult result = computeDeltaResult(seasonAlgorithm, m, recalcSeason);
        Map<User, Integer> adjustments = result.getAdjustments();

        // persist deltas on the match (keeps link with standings history)
        storeMatchDeltas(m, adjustments);
        if (excluded) {
          continue;
        }

        // Ensure every real participant gets a standing row even if net delta is 0.
        List<LadderStanding> toSave = new ArrayList<>();
        java.util.Set<Long> touchedUserIds = new java.util.HashSet<>();
        for (User participant : collectStandingsParticipants(m)) {
          if (participant == null || participant.getId() == null) {
            continue;
          }
          LadderStanding st = byUserId.get(participant.getId());
          if (st == null) {
            LadderStanding fresh = new LadderStanding();
            fresh.setSeason(recalcSeason);
            fresh.setUser(participant);
            fresh.setDisplayName(resolveStandingDisplayName(participant));
            SeasonCarryOverService.CarryOverSeed seed = carryOverSeeds.get(participant.getId());
            fresh.setPoints(seed != null ? seed.points() : 0);
            byUserId.put(participant.getId(), fresh);
            toSave.add(fresh);
            touchedUserIds.add(participant.getId());
          }
        }

        if (ratingChangeRepo != null) {
          List<LadderRatingChange> ratingChanges =
              buildRatingChanges(recalcSeason, m, adjustments, result.getExplanations(), byUserId);
          if (!ratingChanges.isEmpty()) {
            ratingChangeRepo.saveAll(ratingChanges);
          }
        }

        // apply to standings (create rows if missing)
        if (!adjustments.isEmpty()) {
          adjustments.forEach(
              (player, change) -> {
                if (player == null || player.getId() == null) return;
                LadderStanding st =
                    byUserId.computeIfAbsent(
                        player.getId(),
                        id -> {
                          LadderStanding fresh = new LadderStanding();
                          fresh.setSeason(recalcSeason);
                          fresh.setUser(player);
                          fresh.setDisplayName(resolveStandingDisplayName(player));
                          SeasonCarryOverService.CarryOverSeed seed =
                              carryOverSeeds.get(player.getId());
                          fresh.setPoints(seed != null ? seed.points() : 0);
                          return fresh;
                        });
                st.setPoints(st.getPoints() + change);
                if (touchedUserIds.add(player.getId())) {
                  toSave.add(st);
                }
              });
        }
        if (!toSave.isEmpty()) {
          standingRepo.saveAll(toSave);
        }

        replayDailyMomentum(
            new ArrayList<>(byUserId.values()), adjustments, recalcSeason, bandPositionsByUserId);
      }

      // 4) Final re-rank & band updates
      List<LadderStanding> ranked = rerankSeason(recalcSeason);
      updateBandPositions(ranked, recalcSeason, bandPositionsByUserId);

      // 5) Remove any standings for players with no confirmed matches
      // (Not just 0 points - a player could have 0 points but still have matches)
      List<LadderStanding> allStandings = standingRepo.findBySeasonOrderByRankNoAsc(recalcSeason);
      List<LadderStanding> toDelete = new ArrayList<>();

      // Build a set of all user IDs who participated in confirmed matches
      java.util.Set<Long> playersWithMatches = new java.util.HashSet<>();
      int includedMatchCount = 0;
      for (Match m : matches) {
        if (isEffectivelyExcludedFromStandings(m)) {
          continue;
        }
        includedMatchCount++;
        for (User participant : collectStandingsParticipants(m)) {
          if (participant != null && participant.getId() != null) {
            playersWithMatches.add(participant.getId());
          }
        }
      }

      // Delete standings for players who have no confirmed matches
      for (LadderStanding standing : allStandings) {
        User player = standing.getUser();
        if (player == null || player.getId() == null) continue;

        if (!playersWithMatches.contains(player.getId())
            && !carryOverSeeds.containsKey(player.getId())) {
          toDelete.add(standing);
        }
      }

      if (!toDelete.isEmpty()) {
        if (existingStandingsCount >= 4
            && toDelete.size() >= Math.max(3, existingStandingsCount / 2)) {
          log.warn(
              "Season {} recalc is removing a large share of standings: existingStandings={}, includedMatches={}, replayedMatches={}, activePlayersAfterReplay={}, removing={}, replaySummary={}",
              recalcSeason.getId(),
              existingStandingsCount,
              includedMatchCount,
              matches.size(),
              playersWithMatches.size(),
              toDelete.size(),
              summarizeReplayMatches(matches));
        }
        log.info(
            "Removing {} standings with no confirmed matches after recalc for season {}",
            toDelete.size(),
            recalcSeason.getId());
        standingRepo.deleteAll(toDelete);
        standingRepo.flush();
      }

      List<Long> activeStandingUserIds =
          standingRepo.findBySeasonOrderByRankNoAsc(recalcSeason).stream()
              .map(LadderStanding::getUser)
              .filter(Objects::nonNull)
              .map(User::getId)
              .filter(Objects::nonNull)
              .distinct()
              .collect(Collectors.toList());
      if (activeStandingUserIds.isEmpty()) {
        bandRepo.deleteBySeason(recalcSeason);
      } else {
        bandRepo.deleteBySeasonAndUserIdNotIn(recalcSeason, activeStandingUserIds);
      }
    } finally {
      if (shouldTrack && !finishRegistered[0]) {
        markRecalcFinishedSafely(seasonId);
      }
    }
  }

  private LadderSeason hydrateSeasonForRecalc(LadderSeason season) {
    if (season == null || season.getId() == null) {
      return season;
    }
    // Async recalc requests can carry a detached season whose ladderConfig proxy no longer
    // has an active session. Reload the season inside this transaction before replay.
    return seasonRepo.findByIdWithLadderConfig(season.getId()).orElse(season);
  }

  private void markRecalcStartedSafely(Long seasonId) {
    try {
      recalcTracker.markStarted(seasonId);
    } catch (Exception ex) {
      log.warn(
          "Unable to mark standings recalculation started for season {}: {}",
          seasonId,
          ex.getMessage());
    }
  }

  private void markRecalcFinishedSafely(Long seasonId) {
    try {
      recalcTracker.markFinished(seasonId);
    } catch (Exception ex) {
      log.warn(
          "Unable to mark standings recalculation finished for season {}: {}",
          seasonId,
          ex.getMessage());
    }
  }

  private String summarizeReplayMatches(List<Match> matches) {
    if (matches == null || matches.isEmpty()) {
      return "no matches";
    }
    return matches.stream()
        .limit(5)
        .map(
            match -> {
              Long matchId = match != null ? match.getId() : null;
              MatchState state = match != null ? match.getState() : null;
              Instant playedAt = match != null ? match.getPlayedAt() : null;
              boolean excluded = match != null && isEffectivelyExcludedFromStandings(match);
              int participants = match != null ? collectStandingsParticipants(match).size() : 0;
              return String.format(
                  "#%s[state=%s,playedAt=%s,excluded=%s,participants=%d]",
                  matchId, state, playedAt, excluded, participants);
            })
        .collect(Collectors.joining(", "));
  }
}
