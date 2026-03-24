package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeasonCarryOverService {

  private final LadderSeasonRepository seasonRepository;
  private final LadderStandingRepository standingRepository;
  private final LadderMembershipRepository membershipRepository;

  public SeasonCarryOverService(
      LadderSeasonRepository seasonRepository,
      LadderStandingRepository standingRepository,
      LadderMembershipRepository membershipRepository) {
    this.seasonRepository = seasonRepository;
    this.standingRepository = standingRepository;
    this.membershipRepository = membershipRepository;
  }

  public Map<Long, CarryOverSeed> resolveCarryOverSeeds(LadderSeason season) {
    if (season == null || season.getId() == null) {
      return Map.of();
    }

    LadderSeason sourceSeason =
        seasonRepository.findByIdWithLadderConfig(season.getId()).orElse(season);
    LadderConfig ladder = sourceSeason.getLadderConfig();
    if (ladder == null || ladder.getId() == null || !ladder.isCarryOverPreviousRating()) {
      return Map.of();
    }

    LadderSeason previousEndedSeason = resolvePreviousEndedSeason(sourceSeason);
    if (previousEndedSeason == null) {
      return Map.of();
    }

    Set<Long> activeMemberIds =
        membershipRepository
            .findByLadderConfigIdAndStateOrderByJoinedAtAsc(
                ladder.getId(), LadderMembership.State.ACTIVE)
            .stream()
            .map(LadderMembership::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (activeMemberIds.isEmpty()) {
      return Map.of();
    }

    List<LadderStanding> priorStandings =
        standingRepository.findBySeasonOrderByRankNoAscWithUser(previousEndedSeason);
    if (priorStandings.isEmpty()) {
      return Map.of();
    }

    Map<Long, CarryOverSeed> seeds = new LinkedHashMap<>();
    for (LadderStanding standing : priorStandings) {
      if (standing == null || standing.getUser() == null || standing.getUser().getId() == null) {
        continue;
      }
      Long userId = standing.getUser().getId();
      if (!activeMemberIds.contains(userId)) {
        continue;
      }
      seeds.putIfAbsent(
          userId,
          new CarryOverSeed(
              standing.getUser(), resolveDisplayName(standing), standing.getPoints()));
    }
    if (seeds.isEmpty()) {
      return Map.of();
    }
    return Collections.unmodifiableMap(seeds);
  }

  @Transactional
  public void seedSeasonFromCarryOverIfEnabled(LadderSeason season) {
    if (season == null || season.getId() == null) {
      return;
    }

    List<LadderStanding> existing = standingRepository.findBySeasonOrderByRankNoAsc(season);
    if (!existing.isEmpty()) {
      return;
    }

    Map<Long, CarryOverSeed> seedsByUser = resolveCarryOverSeeds(season);
    if (seedsByUser.isEmpty()) {
      return;
    }

    List<LadderStanding> carryRows = new ArrayList<>(seedsByUser.size());
    for (CarryOverSeed seed : seedsByUser.values()) {
      LadderStanding standing = new LadderStanding();
      standing.setSeason(season);
      standing.setUser(seed.user());
      standing.setDisplayName(seed.displayName());
      standing.setPoints(seed.points());
      carryRows.add(standing);
    }

    carryRows.sort(
        Comparator.comparingInt(LadderStanding::getPoints)
            .reversed()
            .thenComparing(
                standing -> sortKey(standing.getDisplayName()), String.CASE_INSENSITIVE_ORDER));

    for (int i = 0; i < carryRows.size(); i++) {
      carryRows.get(i).setRank(i + 1);
    }
    standingRepository.saveAll(carryRows);
  }

  private LadderSeason resolvePreviousEndedSeason(LadderSeason season) {
    LadderConfig ladder = season.getLadderConfig();
    if (ladder == null || ladder.getId() == null) {
      return null;
    }
    return seasonRepository.findByLadderConfigIdOrderByStartDateDesc(ladder.getId()).stream()
        .filter(candidate -> candidate != null && candidate.getId() != null)
        .filter(candidate -> !Objects.equals(candidate.getId(), season.getId()))
        .filter(candidate -> candidate.getState() == LadderSeason.State.ENDED)
        .filter(
            candidate ->
                season.getStartDate() == null
                    || candidate.getStartDate() == null
                    || !candidate.getStartDate().isAfter(season.getStartDate()))
        .findFirst()
        .orElse(null);
  }

  private String resolveDisplayName(LadderStanding standing) {
    String standingName = normalizeDisplayName(standing.getDisplayName());
    if (!standingName.isBlank()) {
      return standingName;
    }
    User user = standing.getUser();
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

  private String sortKey(String value) {
    return normalizeDisplayName(value).toLowerCase(Locale.ENGLISH);
  }

  public record CarryOverSeed(User user, String displayName, int points) {}
}
