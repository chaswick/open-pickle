package com.w3llspring.fhpb.web.service.matchlog;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionAutoModerationService;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MatchLogContextService {

  private final LadderSeasonRepository seasonRepository;
  private final LadderConfigRepository ladderConfigRepository;
  private final LadderMembershipRepository ladderMembershipRepository;
  private final MatchValidationService matchValidationService;
  private final LadderAccessService ladderAccessService;
  private final CompetitionAutoModerationService competitionAutoModerationService;
  private final ZoneId ladderZone;

  public MatchLogContextService(
      LadderSeasonRepository seasonRepository,
      LadderConfigRepository ladderConfigRepository,
      LadderMembershipRepository ladderMembershipRepository,
      MatchValidationService matchValidationService,
      LadderAccessService ladderAccessService,
      CompetitionAutoModerationService competitionAutoModerationService,
      ZoneId ladderZone) {
    this.seasonRepository = seasonRepository;
    this.ladderConfigRepository = ladderConfigRepository;
    this.ladderMembershipRepository = ladderMembershipRepository;
    this.matchValidationService = matchValidationService;
    this.ladderAccessService = ladderAccessService;
    this.competitionAutoModerationService = competitionAutoModerationService;
    this.ladderZone = ladderZone;
  }

  public ResolvedMatchLogContext resolveMatchLogContext(
      Long seasonId, Long ladderId, Instant playedAt, boolean competitionMode) {
    LadderConfig selectedLadder = findLadderConfig(ladderId);
    if (selectedLadder != null && selectedLadder.isSessionType()) {
      LadderSeason sessionTarget = resolveSessionTargetSeason(selectedLadder, playedAt);
      return new ResolvedMatchLogContext(
          ladderId,
          sessionTarget,
          selectedLadder,
          filterCompetitionEligibleMembers(
              sessionTarget, resolveEligibleMemberIdsForLadder(ladderId)),
          true);
    }

    if (competitionMode) {
      return new ResolvedMatchLogContext(ladderId, null, null, Collections.emptySet(), true);
    }

    LadderSeason targetSeason = resolveSeasonForSubmission(seasonId, ladderId, playedAt);
    Long selectionLadderId = ladderId;
    if (targetSeason != null
        && targetSeason.getLadderConfig() != null
        && targetSeason.getLadderConfig().getId() != null) {
      selectionLadderId = targetSeason.getLadderConfig().getId();
    }
    Set<Long> eligibleMemberIds =
        targetSeason != null
            ? matchValidationService.resolveEligibleMemberUserIdsForSeason(targetSeason.getId())
            : (seasonId == null ? null : Collections.emptySet());
    return new ResolvedMatchLogContext(
        selectionLadderId, targetSeason, null, eligibleMemberIds, false);
  }

  public ResolvedMatchLogContext resolveEditMatchContext(Match match) {
    if (match == null) {
      return new ResolvedMatchLogContext(null, null, null, Collections.emptySet(), false);
    }
    LadderSeason season = match.getSeason();
    LadderConfig sourceSession = match.getSourceSessionConfig();
    if (sourceSession != null && sourceSession.isSessionType()) {
      Set<Long> eligibleMemberIds =
          filterCompetitionEligibleMembers(
              season, resolveEligibleMemberIdsForLadder(sourceSession.getId()));
      return new ResolvedMatchLogContext(
          sourceSession.getId(), season, sourceSession, eligibleMemberIds, true);
    }

    Long selectionLadderId =
        season != null && season.getLadderConfig() != null
            ? season.getLadderConfig().getId()
            : null;
    Set<Long> eligibleMemberIds =
        season != null && season.getId() != null
            ? matchValidationService.resolveEligibleMemberUserIdsForSeason(season.getId())
            : Collections.emptySet();
    return new ResolvedMatchLogContext(selectionLadderId, season, null, eligibleMemberIds, false);
  }

  public boolean isSessionLadderId(Long ladderId) {
    LadderConfig ladderConfig = findLadderConfig(ladderId);
    return ladderConfig != null && ladderConfig.isSessionType();
  }

  public boolean isCompetitionLadderId(Long ladderId) {
    LadderConfig ladderConfig = findLadderConfig(ladderId);
    return ladderConfig != null && ladderConfig.isCompetitionType();
  }

  public boolean isDirectCompetitionSelection(Long ladderId, Long seasonId) {
    LadderConfig selectedLadder = findLadderConfig(ladderId);
    if (selectedLadder != null) {
      if (selectedLadder.isSessionType()) {
        return false;
      }
      if (selectedLadder.isCompetitionType()) {
        return true;
      }
    }
    return isCompetitionSeasonId(seasonId);
  }

  public boolean canAccessStandardLadderContext(User currentUser, ResolvedMatchLogContext context) {
    if (currentUser == null
        || currentUser.getId() == null
        || context == null
        || context.isSession()) {
      return false;
    }

    Long ladderId = context.getSelectionLadderId();
    if (ladderId == null || ladderMembershipRepository == null) {
      return false;
    }

    LadderMembership membership =
        ladderMembershipRepository
            .findByLadderConfigIdAndUserId(ladderId, currentUser.getId())
            .orElse(null);
    return membership != null && membership.getState() == LadderMembership.State.ACTIVE;
  }

  public boolean hasActiveSessionMembership(Long ladderId, Long userId) {
    if (ladderId == null || userId == null || ladderMembershipRepository == null) {
      return false;
    }
    LadderMembership membership =
        ladderMembershipRepository.findByLadderConfigIdAndUserId(ladderId, userId).orElse(null);
    return membership != null && membership.getState() == LadderMembership.State.ACTIVE;
  }

  public boolean resolveSeasonAdminOverride(Long seasonId, User currentUser) {
    if (currentUser == null || currentUser.getId() == null || seasonId == null) {
      return false;
    }
    try {
      return ladderAccessService.isSeasonAdmin(seasonId, currentUser);
    } catch (Exception ex) {
      return false;
    }
  }

  public boolean canLogForOthersInContext(User currentUser, ResolvedMatchLogContext context) {
    if (currentUser == null || currentUser.getId() == null || context == null) {
      return false;
    }
    if (context.isSession()) {
      LadderConfig sessionConfig = context.getSessionConfig();
      if (sessionConfig != null
          && Objects.equals(sessionConfig.getOwnerUserId(), currentUser.getId())) {
        return true;
      }
      return hasActiveAdminMembership(context.getSelectionLadderId(), currentUser.getId());
    }
    LadderSeason targetSeason = context.getSeason();
    return targetSeason != null && resolveSeasonAdminOverride(targetSeason.getId(), currentUser);
  }

  public void requireCompetitionEligibility(User user, LadderSeason season) {
    if (competitionAutoModerationService == null) {
      return;
    }
    competitionAutoModerationService.requireNotBlocked(user, season);
  }

  public boolean shouldUsePlainHomeNav(Long ladderId, boolean competitionMode) {
    return competitionMode || isSessionLadderId(ladderId);
  }

  public boolean isSeasonClosed(Long seasonId) {
    if (seasonId == null) {
      return false;
    }
    LadderSeason season = seasonRepository.findById(seasonId).orElse(null);
    if (season == null || season.getState() != LadderSeason.State.ACTIVE) {
      return true;
    }
    LocalDate today = LocalDate.now(ladderZone);
    return season.getEndDate() != null && season.getEndDate().isBefore(today);
  }

  private LadderSeason resolveSeasonForSubmission(Long seasonId, Long ladderId, Instant playedAt) {
    LocalDate playedDate = playedAt.atZone(ladderZone).toLocalDate();

    if (seasonId != null) {
      LadderSeason season = seasonRepository.findById(seasonId).orElse(null);
      if (season == null || season.getState() != LadderSeason.State.ACTIVE) {
        return null;
      }
      return seasonCoversInstant(season, playedAt) ? season : null;
    }

    if (ladderId != null) {
      LadderSeason ladderScoped =
          seasonRepository
              .findFirstByLadderConfigIdAndStateAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                  ladderId, LadderSeason.State.ACTIVE, playedDate, playedDate)
              .orElse(null);
      if (ladderScoped != null && seasonCoversInstant(ladderScoped, playedAt)) {
        return ladderScoped;
      }
      return null;
    }

    return seasonRepository
        .findFirstByStateAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
            LadderSeason.State.ACTIVE, playedDate, playedDate)
        .filter(season -> seasonCoversInstant(season, playedAt))
        .orElse(null);
  }

  private boolean seasonCoversInstant(LadderSeason season, Instant instant) {
    if (season == null || instant == null) {
      return false;
    }
    LocalDate day = instant.atZone(ladderZone).toLocalDate();
    if (season.getStartDate() != null && day.isBefore(season.getStartDate())) {
      return false;
    }
    return season.getEndDate() == null || !day.isAfter(season.getEndDate());
  }

  private LadderConfig findLadderConfig(Long ladderId) {
    if (ladderId == null || ladderConfigRepository == null) {
      return null;
    }
    return ladderConfigRepository.findById(ladderId).orElse(null);
  }

  private boolean isCompetitionSeasonId(Long seasonId) {
    if (seasonId == null) {
      return false;
    }
    LadderSeason season =
        seasonRepository
            .findByIdWithLadderConfig(seasonId)
            .orElseGet(() -> seasonRepository.findById(seasonId).orElse(null));
    return season != null
        && season.getLadderConfig() != null
        && season.getLadderConfig().isCompetitionType();
  }

  private LadderSeason resolveSessionTargetSeason(LadderConfig sessionConfig, Instant playedAt) {
    if (sessionConfig == null || !sessionConfig.isSessionType()) {
      return null;
    }
    if (sessionConfig.getExpiresAt() != null
        && !sessionConfig.getExpiresAt().isAfter(Instant.now())) {
      return null;
    }
    if (sessionConfig.getTargetSeasonId() == null) {
      return null;
    }
    LadderSeason season = seasonRepository.findById(sessionConfig.getTargetSeasonId()).orElse(null);
    if (season == null || season.getState() != LadderSeason.State.ACTIVE) {
      return null;
    }
    return seasonCoversInstant(season, playedAt) ? season : null;
  }

  private Set<Long> resolveEligibleMemberIdsForLadder(Long ladderId) {
    if (ladderId == null || ladderMembershipRepository == null) {
      return Collections.emptySet();
    }
    return ladderMembershipRepository
        .findByLadderConfigIdAndStateOrderByJoinedAtAsc(ladderId, LadderMembership.State.ACTIVE)
        .stream()
        .map(LadderMembership::getUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(HashSet::new));
  }

  private Set<Long> filterCompetitionEligibleMembers(LadderSeason season, Set<Long> memberIds) {
    if (competitionAutoModerationService == null) {
      return memberIds;
    }
    return competitionAutoModerationService.filterEligibleUserIds(season, memberIds);
  }

  private boolean hasActiveAdminMembership(Long ladderId, Long userId) {
    if (ladderId == null || userId == null || ladderMembershipRepository == null) {
      return false;
    }
    LadderMembership membership =
        ladderMembershipRepository.findByLadderConfigIdAndUserId(ladderId, userId).orElse(null);
    return membership != null
        && membership.getState() == LadderMembership.State.ACTIVE
        && membership.getRole() == LadderMembership.Role.ADMIN;
  }

  public static final class ResolvedMatchLogContext {
    private final Long selectionLadderId;
    private final LadderSeason season;
    private final LadderConfig sessionConfig;
    private final Set<Long> eligibleMemberIds;
    private final boolean session;

    public ResolvedMatchLogContext(
        Long selectionLadderId,
        LadderSeason season,
        LadderConfig sessionConfig,
        Set<Long> eligibleMemberIds,
        boolean session) {
      this.selectionLadderId = selectionLadderId;
      this.season = season;
      this.sessionConfig = sessionConfig;
      this.eligibleMemberIds = eligibleMemberIds;
      this.session = session;
    }

    public Long getSelectionLadderId() {
      return selectionLadderId;
    }

    public LadderSeason getSeason() {
      return season;
    }

    public LadderConfig getSessionConfig() {
      return sessionConfig;
    }

    public Set<Long> getEligibleMemberIds() {
      return eligibleMemberIds;
    }

    public boolean isSession() {
      return session;
    }
  }
}
