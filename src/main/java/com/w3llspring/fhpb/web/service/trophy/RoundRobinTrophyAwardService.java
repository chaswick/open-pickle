package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoundRobinTrophyAwardService {
  private final RoundRobinRepository runs;
  private final RoundRobinEntryRepository entries;
  private final MatchRepository matches;
  private final TrophyRepository trophies;
  private final RoundRobinTrophyAwardRepository awards;
  private final UserTrophyRepository collections;
  private final UserRepository users;
  private final LadderSeasonRepository seasons;
  private final AutoTrophyService catalog;
  private final RoundRobinTrophyStats stats;
  private final TrophyAwardService expressions;
  private final TrophyArtRealizer art;

  public RoundRobinTrophyAwardService(
      RoundRobinRepository runs,
      RoundRobinEntryRepository entries,
      MatchRepository matches,
      TrophyRepository trophies,
      RoundRobinTrophyAwardRepository awards,
      UserTrophyRepository collections,
      UserRepository users,
      LadderSeasonRepository seasons,
      AutoTrophyService catalog,
      RoundRobinTrophyStats stats,
      TrophyAwardService expressions,
      TrophyArtRealizer art) {
    this.runs = runs;
    this.entries = entries;
    this.matches = matches;
    this.trophies = trophies;
    this.awards = awards;
    this.collections = collections;
    this.users = users;
    this.seasons = seasons;
    this.catalog = catalog;
    this.stats = stats;
    this.expressions = expressions;
    this.art = art;
  }

  /** Returns false for an already processed run, a running schedule or unsettled results. */
  @Transactional
  public boolean process(Long runId) {
    RoundRobin run = runs.findByIdForUpdate(runId).orElse(null);
    if (run == null || run.isTrophiesProcessed()) return false;
    List<RoundRobinEntry> games =
        entries.findByRoundRobinOrderByRoundNumberAscWithUsers(run).stream()
            .filter(e -> !e.isBye())
            .toList();
    LadderConfig session = run.getSessionConfig();
    Instant now = Instant.now();
    boolean ended =
        run.getEndedAt() != null
            || (session != null
                && (session.getStatus() == LadderConfig.Status.ARCHIVED
                    || session.getExpiresAt() != null && !session.getExpiresAt().isAfter(now)));
    boolean complete = !games.isEmpty() && games.stream().allMatch(e -> e.getMatchId() != null);
    if (!ended && !complete) return false;
    List<Long> ids =
        games.stream()
            .map(RoundRobinEntry::getMatchId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    List<Match> recorded = ids.isEmpty() ? List.of() : matches.findAllByIdInWithUsers(ids);
    if (recorded.stream()
        .anyMatch(
            m -> m.getState() != MatchState.CONFIRMED && m.getState() != MatchState.NULLIFIED))
      return false;
    List<Match> confirmed =
        recorded.stream()
            .filter(
                m ->
                    m.getState() == MatchState.CONFIRMED
                        && m.getScoreA() != m.getScoreB()
                        && m.getScoreA() >= 0
                        && m.getScoreB() >= 0)
            .toList();
    User host =
        session != null
            ? users.findById(session.getOwnerUserId()).orElse(null)
            : run.getCreatedBy();
    var facts = stats.calculate(run, games, confirmed, host);
    catalog.generateSeasonTrophies(run.getSeason());
    List<Trophy> definitions =
        trophies.findBySeasonOrderByDisplayOrderAscIdAsc(run.getSeason()).stream()
            .filter(Trophy::isRoundRobinTrophy)
            .sorted(Comparator.comparing(Trophy::getId))
            .toList();
    Set<Long> affected = new TreeSet<>();
    for (var prior : awards.findForRun(runId)) affected.add(prior.getTrophy().getId());
    definitions.forEach(t -> affected.add(t.getId()));
    for (Long id : affected) trophies.lockById(id);
    awards.deleteAll(awards.findForRun(runId));
    awards.flush();
    Instant awardedAt =
        run.getEndedAt() != null
            ? run.getEndedAt()
            : confirmed.stream()
                .map(Match::getPlayedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(now);
    String reason = "Round robin #" + runId + (session != null ? " · " + session.getTitle() : "");
    for (Trophy trophy : definitions) {
      if (trophy.getStatus() == TrophyStatus.ARCHIVED
          || trophy.getEvaluationScope() == TrophyEvaluationScope.GROUP) continue;
      List<Long> qualified =
          facts.metrics().entrySet().stream()
              .filter(e -> facts.users().containsKey(e.getKey()))
              .filter(e -> expressions.evaluateMetrics(trophy.getUnlockExpression(), e.getValue()))
              .map(Map.Entry::getKey)
              .sorted()
              .toList();
      Set<String> claimedUnits = new LinkedHashSet<>();
      for (Long userId : qualified) {
        String unit = facts.awardUnits().get(userId);
        if (trophy.isLimited()
            && trophy.getMaxClaims() != null
            && !claimedUnits.contains(unit)
            && claimedUnits.size() >= Math.max(0, trophy.getMaxClaims())) continue;
        claimedUnits.add(unit);
        RoundRobinTrophyAward grant = new RoundRobinTrophyAward();
        grant.setRoundRobin(run);
        grant.setTrophy(trophy);
        grant.setUser(facts.users().get(userId));
        grant.setAwardedAt(awardedAt);
        grant.setReason(reason);
        awards.save(grant);
      }
    }
    awards.flush();
    for (Long id : affected) trophies.findById(id).ifPresent(this::syncCollection);
    run.setTrophiesProcessed(true);
    run.setTrophiesProcessedAt(now);
    runs.save(run);
    return true;
  }

  /** Withdraw a changed run's contributions while it waits for settled results. */
  @Transactional
  public void withdrawRunAwards(Long id) {
    List<RoundRobinTrophyAward> previous = awards.findForRun(id);
    List<Long> affected =
        previous.stream().map(a -> a.getTrophy().getId()).distinct().sorted().toList();
    affected.forEach(trophies::lockById);
    awards.deleteAll(previous);
    awards.flush();
    affected.forEach(t -> trophies.findById(t).ifPresent(this::syncCollection));
  }

  public record RunAwardPlayer(String name, String publicCode) {}

  public record RunAward(
      Long trophyId, String title, int place, String imageUrl, List<RunAwardPlayer> players) {}

  private static int placement(Trophy trophy) {
    if ("round-robin-v1:rr-champion".equals(trophy.getGenerationSeed())) return 1;
    if ("round-robin-v1:rr-runner-up".equals(trophy.getGenerationSeed())) return 2;
    return 0;
  }

  /** Winner and runner-up awards for the session's result card. */
  @Transactional(readOnly = true)
  public List<RunAward> awardsForRun(Long id) {
    Map<Long, List<RoundRobinTrophyAward>> grouped = new LinkedHashMap<>();
    awards.findForRun(id).stream()
        .filter(a -> placement(a.getTrophy()) > 0)
        .forEach(
            a -> grouped.computeIfAbsent(a.getTrophy().getId(), k -> new ArrayList<>()).add(a));
    return grouped.values().stream()
        .map(
            list -> {
              Trophy trophy = list.getFirst().getTrophy();
              return new RunAward(
                  trophy.getId(),
                  trophy.getTitle(),
                  placement(trophy),
                  trophy.hasArt()
                      ? "/trophies/image/" + trophy.getId()
                      : "/images/trophy/fallback.png",
                  list.stream()
                      .map(
                          a ->
                              new RunAwardPlayer(
                                  com.w3llspring.fhpb.web.util.UserPublicName.forUser(a.getUser()),
                                  a.getUser().getPublicCode()))
                      .sorted(
                          Comparator.comparing(RunAwardPlayer::name, String.CASE_INSENSITIVE_ORDER))
                      .toList());
            })
        .sorted(Comparator.comparingInt(RunAward::place))
        .toList();
  }

  /** A deleted run's ledger rows cascade away; reconcile counts without rereading old games. */
  @Transactional
  public void reconcileSeason(Long seasonId) {
    LadderSeason season = seasons.findById(seasonId).orElse(null);
    if (season == null) return;
    trophies.findBySeasonOrderByDisplayOrderAscIdAsc(season).stream()
        .filter(Trophy::isRoundRobinTrophy)
        .sorted(Comparator.comparing(Trophy::getId))
        .forEach(
            t -> {
              trophies.lockById(t.getId());
              syncCollection(t);
            });
  }

  private void syncCollection(Trophy trophy) {
    Map<Long, UserTrophy> existing = new HashMap<>();
    collections.findByTrophy(trophy).forEach(a -> existing.put(a.getUser().getId(), a));
    for (var owner : awards.summarizeOwners(trophy)) {
      UserTrophy grant = existing.remove(owner.getUserId());
      if (grant == null) {
        grant = new UserTrophy();
        grant.setTrophy(trophy);
        grant.setUser(users.getReferenceById(owner.getUserId()));
      }
      grant.setAwardCount(trophy.isRepeatable() ? Math.toIntExact(owner.getTotal()) : 1);
      grant.setFirstAwardedAt(owner.getFirstAwardedAt());
      grant.setLastAwardedAt(owner.getLastAwardedAt());
      grant.setAwardedAt(owner.getFirstAwardedAt());
      grant.setAwardedReason(
          "Earned in "
              + owner.getTotal()
              + (owner.getTotal() == 1 ? " round robin." : " round robins."));
      collections.save(grant);
      art.ensureImage(trophy);
    }
    collections.deleteAll(existing.values());
  }
}
