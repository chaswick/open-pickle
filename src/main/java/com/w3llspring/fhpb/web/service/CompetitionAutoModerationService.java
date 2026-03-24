package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CompetitionAutoModerationService {

  private final MatchConfirmationRepository confirmationRepository;
  private final CompetitionSeasonService competitionSeasonService;
  private final boolean enabled;
  private final int firstWarningThreshold;
  private final int secondWarningThreshold;
  private final int blockThreshold;

  public CompetitionAutoModerationService(
      MatchConfirmationRepository confirmationRepository,
      CompetitionSeasonService competitionSeasonService,
      @Value("${fhpb.automod.competition.enabled:true}") boolean enabled,
      @Value("${fhpb.automod.competition.warning1-expired-confirmations:3}")
          int firstWarningThreshold,
      @Value("${fhpb.automod.competition.warning2-expired-confirmations:5}")
          int secondWarningThreshold,
      @Value("${fhpb.automod.competition.block-expired-confirmations:7}") int blockThreshold) {
    this.confirmationRepository = confirmationRepository;
    this.competitionSeasonService = competitionSeasonService;
    this.enabled = enabled;

    // Large-pool competition should react to repeated patterns, not isolated incidents.
    int normalizedFirst = Math.max(1, firstWarningThreshold);
    int normalizedSecond = Math.max(normalizedFirst + 1, secondWarningThreshold);
    int normalizedBlock = Math.max(normalizedSecond + 1, blockThreshold);
    this.firstWarningThreshold = normalizedFirst;
    this.secondWarningThreshold = normalizedSecond;
    this.blockThreshold = normalizedBlock;
  }

  public AutoModerationStatus activeCompetitionStatus(User user) {
    if (user == null || user.getId() == null) {
      return AutoModerationStatus.clear();
    }
    LadderSeason activeCompetitionSeason =
        competitionSeasonService.resolveActiveCompetitionSeason();
    return statusForSeason(user, activeCompetitionSeason);
  }

  public AutoModerationStatus statusForSeason(User user, LadderSeason season) {
    if (user == null || user.getId() == null) {
      return AutoModerationStatus.clear();
    }
    return statusForSeason(user.getId(), season);
  }

  public AutoModerationStatus statusForSeason(Long userId, LadderSeason season) {
    if (userId == null || !appliesTo(season)) {
      return AutoModerationStatus.clear();
    }
    return statusesForSeason(List.of(userId), season)
        .getOrDefault(userId, AutoModerationStatus.clear());
  }

  public Set<Long> filterEligibleUserIds(LadderSeason season, Set<Long> userIds) {
    if (!appliesTo(season) || userIds == null || userIds.isEmpty()) {
      return userIds;
    }

    Map<Long, AutoModerationStatus> statuses = statusesForSeason(userIds, season);
    return userIds.stream()
        .filter(Objects::nonNull)
        .filter(userId -> !statuses.getOrDefault(userId, AutoModerationStatus.clear()).isBlocked())
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  public void requireNotBlocked(User user, LadderSeason season) {
    AutoModerationStatus status = statusForSeason(user, season);
    if (status.isBlocked()) {
      throw new SecurityException(status.getActionMessage());
    }
  }

  public boolean appliesTo(LadderSeason season) {
    return enabled
        && season != null
        && season.getId() != null
        && season.getLadderConfig() != null
        && season.getLadderConfig().isCompetitionType();
  }

  public int getFirstWarningThreshold() {
    return firstWarningThreshold;
  }

  public int getSecondWarningThreshold() {
    return secondWarningThreshold;
  }

  public int getBlockThreshold() {
    return blockThreshold;
  }

  Map<Long, AutoModerationStatus> statusesForSeason(Collection<Long> userIds, LadderSeason season) {
    Map<Long, AutoModerationStatus> statuses = new LinkedHashMap<>();
    if (!appliesTo(season) || userIds == null || userIds.isEmpty()) {
      return statuses;
    }

    List<Long> distinctIds =
        userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    if (distinctIds.isEmpty()) {
      return statuses;
    }

    List<MatchConfirmation> confirmations =
        confirmationRepository.findByPlayerIdInAndMatchSeasonId(distinctIds, season.getId());
    Map<Long, Long> incidentCounts =
        confirmations.stream()
            .filter(this::isExpiredConfirmationStrike)
            .collect(
                Collectors.groupingBy(
                    mc -> mc.getPlayer().getId(), LinkedHashMap::new, Collectors.counting()));

    for (Long userId : distinctIds) {
      int incidents = incidentCounts.getOrDefault(userId, 0L).intValue();
      statuses.put(userId, buildStatus(incidents));
    }

    return statuses;
  }

  private boolean isExpiredConfirmationStrike(MatchConfirmation confirmation) {
    if (confirmation == null
        || confirmation.getPlayer() == null
        || confirmation.getPlayer().getId() == null) {
      return false;
    }
    Match match = confirmation.getMatch();
    if (match == null || match.getState() != MatchState.NULLIFIED) {
      return false;
    }
    if (match.isDisputed()) {
      return false;
    }
    if (confirmation.getConfirmedAt() != null) {
      return false;
    }
    return !match.hasBothOpponentsAsGuests(confirmation.getPlayer());
  }

  private AutoModerationStatus buildStatus(int incidentCount) {
    if (incidentCount >= blockThreshold) {
      return AutoModerationStatus.blocked(incidentCount);
    }
    if (incidentCount >= secondWarningThreshold) {
      return AutoModerationStatus.warningTwo(incidentCount);
    }
    if (incidentCount >= firstWarningThreshold) {
      return AutoModerationStatus.warningOne(incidentCount);
    }
    return AutoModerationStatus.clear();
  }

  public enum AutoModerationLevel {
    CLEAR,
    WARNING_ONE,
    WARNING_TWO,
    BLOCKED
  }

  public static final class AutoModerationStatus {
    private final AutoModerationLevel level;
    private final int incidentCount;
    private final String bannerTitle;
    private final String bannerMessage;
    private final String bannerVariant;
    private final String actionMessage;

    private AutoModerationStatus(
        AutoModerationLevel level,
        int incidentCount,
        String bannerTitle,
        String bannerMessage,
        String bannerVariant,
        String actionMessage) {
      this.level = level;
      this.incidentCount = incidentCount;
      this.bannerTitle = bannerTitle;
      this.bannerMessage = bannerMessage;
      this.bannerVariant = bannerVariant;
      this.actionMessage = actionMessage;
    }

    public static AutoModerationStatus clear() {
      return new AutoModerationStatus(AutoModerationLevel.CLEAR, 0, null, null, null, null);
    }

    public static AutoModerationStatus warningOne(int incidentCount) {
      return new AutoModerationStatus(
          AutoModerationLevel.WARNING_ONE,
          incidentCount,
          "Fair Play Warning",
          String.format(
              "You have %d missed required competition confirmation%s this season. If it happens again, you will receive a final warning.",
              incidentCount, incidentCount == 1 ? "" : "s"),
          "warning",
          String.format(
              "You already have %d missed required confirmation%s this season. Another one will trigger a final warning.",
              incidentCount, incidentCount == 1 ? "" : "s"));
    }

    public static AutoModerationStatus warningTwo(int incidentCount) {
      return new AutoModerationStatus(
          AutoModerationLevel.WARNING_TWO,
          incidentCount,
          "Final Fair Play Warning",
          String.format(
              "You have %d missed required competition confirmations this season. One more missed confirmation will block you from competition matches until next season.",
              incidentCount),
          "danger",
          String.format(
              "You are on a final warning with %d missed required confirmations this season. Another missed confirmation will lock you out for the rest of the season.",
              incidentCount));
    }

    public static AutoModerationStatus blocked(int incidentCount) {
      return new AutoModerationStatus(
          AutoModerationLevel.BLOCKED,
          incidentCount,
          "Competition Match Lockout",
          String.format(
              "You missed %d required competition match confirmation%s this season. You cannot be included in competition matches again until the next season starts.",
              incidentCount, incidentCount == 1 ? "" : "s"),
          "danger",
          String.format(
              "You cannot be included in competition matches for the rest of this season because %d required confirmations were missed.",
              incidentCount));
    }

    public AutoModerationLevel getLevel() {
      return level;
    }

    public int getIncidentCount() {
      return incidentCount;
    }

    public String getBannerTitle() {
      return bannerTitle;
    }

    public String getBannerMessage() {
      return bannerMessage;
    }

    public String getBannerVariant() {
      return bannerVariant;
    }

    public String getActionMessage() {
      return actionMessage;
    }

    public boolean isVisibleBanner() {
      return level != AutoModerationLevel.CLEAR;
    }

    public boolean isBlocked() {
      return level == AutoModerationLevel.BLOCKED;
    }
  }
}
