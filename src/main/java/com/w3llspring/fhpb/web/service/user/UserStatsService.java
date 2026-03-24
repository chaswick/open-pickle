package com.w3llspring.fhpb.web.service.user;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserStatsService {

  private final MatchRepository matchRepository;
  private final LadderMembershipRepository membershipRepository;
  private final LadderStandingRepository standingRepository;

  public UserStatsService(
      MatchRepository matchRepository,
      LadderMembershipRepository membershipRepository,
      LadderStandingRepository standingRepository) {
    this.matchRepository = matchRepository;
    this.membershipRepository = membershipRepository;
    this.standingRepository = standingRepository;
  }

  public Map<String, Object> calculateUserStats(User user) {
    Map<String, Object> stats = new HashMap<>();

    // Get all matches for this user
    List<Match> allMatches = matchRepository.findByParticipant(user);
    List<Match> confirmedMatches =
        allMatches.stream().filter(this::isConfirmedMatch).collect(Collectors.toList());
    List<Match> activeSeasonConfirmedMatches =
        confirmedMatches.stream().filter(this::isInActiveSeason).collect(Collectors.toList());

    // Get memberships
    List<LadderMembership> activeMemberships =
        membershipRepository.findByUserIdAndState(user.getId(), LadderMembership.State.ACTIVE);

    // Get standings
    List<LadderStanding> standings = standingRepository.findByUser(user);

    // Calculate pickleball-specific stats
    stats.putAll(buildPickleballStats(user, confirmedMatches));
    stats.put("activeSeasonStats", buildPickleballStats(user, activeSeasonConfirmedMatches));

    // Calculate site-specific stats
    calculateSiteStats(stats, user, allMatches, confirmedMatches, activeMemberships, standings);

    return stats;
  }

  private Map<String, Object> buildPickleballStats(User user, List<Match> confirmedMatches) {
    Map<String, Object> stats = new HashMap<>();
    int totalMatches = confirmedMatches.size();
    stats.put("totalMatches", totalMatches);

    if (totalMatches == 0) {
      // Set defaults for empty state
      stats.put("wins", 0);
      stats.put("losses", 0);
      stats.put("winRate", "0%");
      stats.put("pointsFor", 0);
      stats.put("pointsAgainst", 0);
      stats.put("pointDifferential", 0);
      stats.put("avgPointsFor", "0.0");
      stats.put("avgPointsAgainst", "0.0");
      stats.put("uniquePartners", 0);
      stats.put("uniqueOpponents", 0);
      stats.put("favoritePartner", "None");
      stats.put("favoritePartnerDetail", "");
      stats.put("mostBeatenOpponent", "None");
      stats.put("mostBeatenOpponentDetail", "");
      stats.put("biggestWin", "—");
      stats.put("closestMatch", "—");
      stats.put("longestWinStreak", 0);
      stats.put("currentStreak", "None");
      return stats;
    }

    int wins = 0;
    int pointsFor = 0;
    int pointsAgainst = 0;
    Set<Long> partners = new HashSet<>();
    Set<Long> opponents = new HashSet<>();
    Map<Long, Integer> partnerCounts = new HashMap<>();
    Map<Long, String> partnerNames = new HashMap<>();
    Map<Long, Integer> beatenOpponentCounts = new HashMap<>();
    Map<Long, String> opponentNames = new HashMap<>();
    int maxWinMargin = 0;
    String biggestWinDesc = "—";
    int minPointDiff = Integer.MAX_VALUE;
    String closestMatchDesc = "—";

    // Win streak tracking
    int currentStreakCount = 0;
    int longestWinStreak = 0;
    int tempWinStreak = 0;
    boolean lastMatchWon = false;

    // Process matches chronologically for streak calculation
    List<Match> chronoMatches =
        confirmedMatches.stream()
            .sorted((a, b) -> matchChronology(a).compareTo(matchChronology(b)))
            .collect(Collectors.toList());

    for (Match match : chronoMatches) {
      boolean userOnTeamA =
          (match.getA1() != null && match.getA1().getId().equals(user.getId()))
              || (match.getA2() != null && match.getA2().getId().equals(user.getId()));
      boolean teamAWon = match.getScoreA() > match.getScoreB();
      boolean userWon = (userOnTeamA && teamAWon) || (!userOnTeamA && !teamAWon);

      if (userWon) {
        wins++;
        tempWinStreak++;
        lastMatchWon = true;
      } else {
        longestWinStreak = Math.max(longestWinStreak, tempWinStreak);
        tempWinStreak = 0;
        lastMatchWon = false;
      }

      // Track points
      int userPoints = userOnTeamA ? match.getScoreA() : match.getScoreB();
      int oppPoints = userOnTeamA ? match.getScoreB() : match.getScoreA();
      pointsFor += userPoints;
      pointsAgainst += oppPoints;

      // Track biggest win
      if (userWon) {
        int margin = userPoints - oppPoints;
        if (margin > maxWinMargin) {
          maxWinMargin = margin;
          biggestWinDesc = userPoints + "-" + oppPoints;
        }
      }

      // Track closest match
      int pointDiff = Math.abs(userPoints - oppPoints);
      if (pointDiff < minPointDiff) {
        minPointDiff = pointDiff;
        closestMatchDesc = Math.max(userPoints, oppPoints) + "-" + Math.min(userPoints, oppPoints);
      }

      // Track partners and opponents
      if (userOnTeamA) {
        recordPartner(
            resolveTeammate(match.getA1(), match.getA2(), user.getId()),
            partners,
            partnerCounts,
            partnerNames);
        recordOpponent(match.getB1(), opponents, opponentNames, userWon, beatenOpponentCounts);
        recordOpponent(match.getB2(), opponents, opponentNames, userWon, beatenOpponentCounts);
      } else {
        recordPartner(
            resolveTeammate(match.getB1(), match.getB2(), user.getId()),
            partners,
            partnerCounts,
            partnerNames);
        recordOpponent(match.getA1(), opponents, opponentNames, userWon, beatenOpponentCounts);
        recordOpponent(match.getA2(), opponents, opponentNames, userWon, beatenOpponentCounts);
      }
    }

    longestWinStreak = Math.max(longestWinStreak, tempWinStreak);
    currentStreakCount = lastMatchWon ? tempWinStreak : 0;

    int losses = totalMatches - wins;
    double winRate = totalMatches > 0 ? (wins * 100.0 / totalMatches) : 0.0;
    int pointDiff = pointsFor - pointsAgainst;
    double avgPointsFor = totalMatches > 0 ? (pointsFor * 1.0 / totalMatches) : 0.0;
    double avgPointsAgainst = totalMatches > 0 ? (pointsAgainst * 1.0 / totalMatches) : 0.0;

    String favoritePartner = "None";
    String favoritePartnerDetail = "";
    Long favPartnerId = selectTopParticipant(partnerCounts, partnerNames);
    if (favPartnerId != null) {
      favoritePartner = partnerNames.getOrDefault(favPartnerId, "None");
      favoritePartnerDetail = formatCountLabel(partnerCounts.get(favPartnerId), "match", "matches");
    }

    String mostBeatenOpponent = "None";
    String mostBeatenOpponentDetail = "";
    Long mostBeatenOpponentId = selectTopParticipant(beatenOpponentCounts, opponentNames);
    if (mostBeatenOpponentId != null) {
      mostBeatenOpponent = opponentNames.getOrDefault(mostBeatenOpponentId, "None");
      mostBeatenOpponentDetail =
          formatCountLabel(beatenOpponentCounts.get(mostBeatenOpponentId), "win", "wins");
    }

    String currentStreakDesc =
        currentStreakCount > 0
            ? currentStreakCount + " win" + (currentStreakCount == 1 ? "" : "s")
            : "None";

    stats.put("wins", wins);
    stats.put("losses", losses);
    stats.put("winRate", String.format("%.1f%%", winRate));
    stats.put("pointsFor", pointsFor);
    stats.put("pointsAgainst", pointsAgainst);
    stats.put("pointDifferential", pointDiff > 0 ? "+" + pointDiff : String.valueOf(pointDiff));
    stats.put("avgPointsFor", String.format("%.1f", avgPointsFor));
    stats.put("avgPointsAgainst", String.format("%.1f", avgPointsAgainst));
    stats.put("uniquePartners", partners.size());
    stats.put("uniqueOpponents", opponents.size());
    stats.put("favoritePartner", favoritePartner);
    stats.put("favoritePartnerDetail", favoritePartnerDetail);
    stats.put("mostBeatenOpponent", mostBeatenOpponent);
    stats.put("mostBeatenOpponentDetail", mostBeatenOpponentDetail);
    stats.put("biggestWin", biggestWinDesc);
    stats.put("closestMatch", closestMatchDesc);
    stats.put("longestWinStreak", longestWinStreak);
    stats.put("currentStreak", currentStreakDesc);
    return stats;
  }

  private void calculateSiteStats(
      Map<String, Object> stats,
      User user,
      List<Match> allMatches,
      List<Match> confirmedMatches,
      List<LadderMembership> activeMemberships,
      List<LadderStanding> standings) {

    // Account age
    if (user.getRegisteredAt() != null) {
      long daysSinceJoined =
          ChronoUnit.DAYS.between(
              user.getRegisteredAt().atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now());
      stats.put("accountAgeDays", daysSinceJoined);
    } else {
      stats.put("accountAgeDays", 0);
    }

    // Ladder memberships
    stats.put("activeLadders", activeMemberships.size());

    long adminCount =
        activeMemberships.stream().filter(m -> m.getRole() == LadderMembership.Role.ADMIN).count();
    stats.put("laddersAsAdmin", (int) adminCount);

    // Match logging activity
    int matchesLogged =
        (int)
            allMatches.stream()
                .filter(
                    m -> m.getLoggedBy() != null && m.getLoggedBy().getId().equals(user.getId()))
                .count();
    stats.put("matchesLogged", matchesLogged);

    int matchesCorrected = (int) allMatches.stream().filter(Match::isUserCorrected).count();
    stats.put("matchesCorrected", matchesCorrected);

    // Current rankings
    int currentRankings = 0;
    Integer bestRank = null;
    for (LadderStanding standing : standings) {
      if (standing.getSeason() != null
          && standing.getSeason().getState()
              == com.w3llspring.fhpb.web.model.LadderSeason.State.ACTIVE) {
        currentRankings++;
        if (bestRank == null || standing.getRank() < bestRank) {
          bestRank = standing.getRank();
        }
      }
    }
    stats.put("currentRankings", currentRankings);
    stats.put("bestCurrentRank", bestRank != null ? "#" + bestRank : "—");

    // Activity metrics
    if (!confirmedMatches.isEmpty()) {
      Match firstMatch =
          confirmedMatches.stream()
              .min((a, b) -> matchChronology(a).compareTo(matchChronology(b)))
              .orElse(null);
      Match lastMatch =
          confirmedMatches.stream()
              .max((a, b) -> matchChronology(a).compareTo(matchChronology(b)))
              .orElse(null);

      if (firstMatch != null) {
        long daysSinceFirstMatch =
            ChronoUnit.DAYS.between(
                matchChronology(firstMatch).atZone(ZoneId.systemDefault()).toLocalDate(),
                LocalDate.now());
        stats.put("daysSinceFirstMatch", daysSinceFirstMatch);
      }

      if (lastMatch != null) {
        long daysSinceLastMatch =
            ChronoUnit.DAYS.between(
                matchChronology(lastMatch).atZone(ZoneId.systemDefault()).toLocalDate(),
                LocalDate.now());
        stats.put("daysSinceLastMatch", daysSinceLastMatch);
      }
    } else {
      stats.put("daysSinceFirstMatch", 0);
      stats.put("daysSinceLastMatch", 0);
    }
  }

  private boolean isConfirmedMatch(Match match) {
    return match != null
        && (match.getState() == null
            || match.getState() == com.w3llspring.fhpb.web.model.MatchState.CONFIRMED);
  }

  private boolean isInActiveSeason(Match match) {
    return match != null
        && match.getSeason() != null
        && match.getSeason().getState() == com.w3llspring.fhpb.web.model.LadderSeason.State.ACTIVE;
  }

  private void recordPartner(
      User partner,
      Set<Long> partners,
      Map<Long, Integer> partnerCounts,
      Map<Long, String> partnerNames) {
    if (partner == null || partner.getId() == null) {
      return;
    }
    partners.add(partner.getId());
    partnerCounts.put(partner.getId(), partnerCounts.getOrDefault(partner.getId(), 0) + 1);
    partnerNames.putIfAbsent(partner.getId(), displayName(partner));
  }

  private void recordOpponent(
      User opponent,
      Set<Long> opponents,
      Map<Long, String> opponentNames,
      boolean userWon,
      Map<Long, Integer> beatenOpponentCounts) {
    if (opponent == null || opponent.getId() == null) {
      return;
    }
    opponents.add(opponent.getId());
    opponentNames.putIfAbsent(opponent.getId(), displayName(opponent));
    if (userWon) {
      beatenOpponentCounts.put(
          opponent.getId(), beatenOpponentCounts.getOrDefault(opponent.getId(), 0) + 1);
    }
  }

  private User resolveTeammate(User slotOne, User slotTwo, Long userId) {
    if (isSameUser(slotOne, userId)) {
      return slotTwo;
    }
    if (isSameUser(slotTwo, userId)) {
      return slotOne;
    }
    return null;
  }

  private boolean isSameUser(User participant, Long userId) {
    return participant != null && participant.getId() != null && participant.getId().equals(userId);
  }

  private Long selectTopParticipant(Map<Long, Integer> counts, Map<Long, String> names) {
    return counts.entrySet().stream()
        .sorted(
            Comparator.comparingInt((Map.Entry<Long, Integer> entry) -> entry.getValue())
                .reversed()
                .thenComparing(entry -> names.getOrDefault(entry.getKey(), ""))
                .thenComparing(Map.Entry::getKey))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  private String formatCountLabel(Integer count, String singular, String plural) {
    if (count == null || count <= 0) {
      return "";
    }
    return count + " " + (count == 1 ? singular : plural);
  }

  private String displayName(User participant) {
    if (participant == null) {
      return "Unknown";
    }
    return com.w3llspring.fhpb.web.util.UserPublicName.forUser(participant);
  }

  private Instant matchChronology(Match match) {
    if (match == null) {
      return Instant.EPOCH;
    }
    if (match.getPlayedAt() != null) {
      return match.getPlayedAt();
    }
    if (match.getCreatedAt() != null) {
      return match.getCreatedAt();
    }
    return Instant.EPOCH;
  }
}
