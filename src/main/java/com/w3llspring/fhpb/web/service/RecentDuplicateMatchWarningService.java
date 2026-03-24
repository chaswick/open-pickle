package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.util.UserPublicName;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RecentDuplicateMatchWarningService {

  private final MatchRepository matchRepository;
  private final MatchConfirmationRepository matchConfirmationRepository;
  private final int warningWindowSeconds;

  public RecentDuplicateMatchWarningService(
      MatchRepository matchRepository,
      MatchConfirmationRepository matchConfirmationRepository,
      @Value("${fhpb.match-log.duplicate-warning-window-seconds:240}") int warningWindowSeconds) {
    this.matchRepository = matchRepository;
    this.matchConfirmationRepository = matchConfirmationRepository;
    this.warningWindowSeconds = Math.max(15, warningWindowSeconds);
  }

  public java.util.Optional<RecentDuplicateMatchWarning> findWarning(DetectionRequest request) {
    return findWarning(request, null, false, "log it");
  }

  public java.util.Optional<RecentDuplicateMatchWarning> findConfirmedWarningForMatch(
      Match match, Instant referenceTime) {
    if (match == null || referenceTime == null) {
      return java.util.Optional.empty();
    }

    Long seasonId = match.getSeason() != null ? match.getSeason().getId() : null;
    Long sessionConfigId =
        match.getSourceSessionConfig() != null ? match.getSourceSessionConfig().getId() : null;
    DetectionRequest request =
        new DetectionRequest(
            seasonId,
            sessionConfigId,
            canonicalizeTeam(
                teamTokens(match.getA1(), match.isA1Guest(), match.getA2(), match.isA2Guest())),
            canonicalizeTeam(
                teamTokens(match.getB1(), match.isB1Guest(), match.getB2(), match.isB2Guest())),
            match.getScoreA(),
            match.getScoreB(),
            referenceTime);
    return findWarning(request, match.getId(), true, "confirm this one");
  }

  private java.util.Optional<RecentDuplicateMatchWarning> findWarning(
      DetectionRequest request,
      Long excludedMatchId,
      boolean requireConfirmedCandidate,
      String actionPrompt) {
    if (request == null || request.referenceTime() == null) {
      return java.util.Optional.empty();
    }
    if (request.sessionConfigId() == null && request.seasonId() == null) {
      return java.util.Optional.empty();
    }

    Instant endExclusive = request.referenceTime().plusSeconds(1);
    Instant startInclusive = request.referenceTime().minusSeconds(warningWindowSeconds);
    List<Match> recentMatches =
        matchRepository.findByCreatedAtInRange(startInclusive, endExclusive);
    if (recentMatches.isEmpty()) {
      return java.util.Optional.empty();
    }

    List<Match> candidates =
        recentMatches.stream()
            .filter(match -> matchesScope(match, request))
            .filter(
                match -> excludedMatchId == null || !Objects.equals(match.getId(), excludedMatchId))
            .filter(match -> !requireConfirmedCandidate || match.getState() == MatchState.CONFIRMED)
            .filter(match -> matchesTeams(match, request))
            .collect(Collectors.toList());
    if (candidates.isEmpty()) {
      return java.util.Optional.empty();
    }

    List<Long> candidateIds =
        candidates.stream().map(Match::getId).filter(Objects::nonNull).collect(Collectors.toList());
    Map<Long, List<MatchConfirmation>> confirmationsByMatch =
        candidateIds.isEmpty()
            ? Collections.emptyMap()
            : matchConfirmationRepository.findByMatchIdIn(candidateIds).stream()
                .filter(
                    confirmation ->
                        confirmation.getMatch() != null && confirmation.getMatch().getId() != null)
                .collect(Collectors.groupingBy(confirmation -> confirmation.getMatch().getId()));

    return candidates.stream()
        .map(
            match ->
                toCandidate(
                    match, confirmationsByMatch.getOrDefault(match.getId(), List.of()), request))
        .sorted(candidateComparator())
        .findFirst()
        .map(
            candidate ->
                new RecentDuplicateMatchWarning(
                    candidate.match().getId(),
                    buildMessage(candidate.match(), candidate.confirmedNames(), actionPrompt)));
  }

  private boolean matchesScope(Match match, DetectionRequest request) {
    if (match == null) {
      return false;
    }
    if (request.sessionConfigId() != null) {
      return match.getSourceSessionConfig() != null
          && Objects.equals(match.getSourceSessionConfig().getId(), request.sessionConfigId());
    }
    return match.getSeason() != null
        && Objects.equals(match.getSeason().getId(), request.seasonId());
  }

  private CandidateWarning toCandidate(
      Match match, List<MatchConfirmation> confirmations, DetectionRequest request) {
    Long loggedById =
        match != null && match.getLoggedBy() != null ? match.getLoggedBy().getId() : null;
    List<String> confirmedNames =
        confirmations.stream()
            .filter(confirmation -> confirmation.getConfirmedAt() != null)
            .filter(confirmation -> !sameUser(confirmation.getPlayer(), loggedById))
            .sorted(
                Comparator.comparing(
                    MatchConfirmation::getConfirmedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .map(MatchConfirmation::getPlayer)
            .map(UserPublicName::forUserOrGuest)
            .collect(
                Collectors.collectingAndThen(
                    Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
    return new CandidateWarning(
        match, confirmedNames, match != null && matchesTeamsAndScore(match, request));
  }

  private Comparator<CandidateWarning> candidateComparator() {
    return Comparator.comparing(
            (CandidateWarning candidate) -> !candidate.confirmedNames().isEmpty(),
            Comparator.reverseOrder())
        .thenComparing(
            candidate -> candidate.match().getState() == MatchState.CONFIRMED,
            Comparator.reverseOrder())
        .thenComparing(CandidateWarning::exactScoreMatch, Comparator.reverseOrder())
        .thenComparing(
            candidate -> candidate.match().getCreatedAt(),
            Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(
            candidate -> candidate.match().getId(),
            Comparator.nullsLast(Comparator.reverseOrder()));
  }

  private String buildMessage(Match match, List<String> confirmedNames, String actionPrompt) {
    String loggerName = UserPublicName.forUser(match != null ? match.getLoggedBy() : null);
    StringBuilder message =
        new StringBuilder("It looks like ").append(loggerName).append(" already logged this match");
    if (!confirmedNames.isEmpty()) {
      message.append(" and ").append(joinNames(confirmedNames)).append(" confirmed it.");
    } else if (match != null && match.getState() == MatchState.CONFIRMED) {
      message.append(" and it is already confirmed.");
    } else {
      message.append(" recently.");
    }
    message
        .append(" Are you sure you want to ")
        .append(actionPrompt == null || actionPrompt.isBlank() ? "continue" : actionPrompt)
        .append("?");
    return message.toString();
  }

  private String joinNames(List<String> names) {
    if (names == null || names.isEmpty()) {
      return "";
    }
    if (names.size() == 1) {
      return names.get(0);
    }
    if (names.size() == 2) {
      return names.get(0) + " and " + names.get(1);
    }
    return String.join(", ", names.subList(0, names.size() - 1))
        + ", and "
        + names.get(names.size() - 1);
  }

  private List<String> teamTokens(
      com.w3llspring.fhpb.web.model.User firstPlayer,
      boolean firstGuest,
      com.w3llspring.fhpb.web.model.User secondPlayer,
      boolean secondGuest) {
    List<String> tokens = new ArrayList<>(2);
    tokens.add(participantToken(firstPlayer, firstGuest));
    tokens.add(participantToken(secondPlayer, secondGuest));
    return canonicalizeTeam(tokens);
  }

  private List<String> canonicalizeTeam(Collection<String> tokens) {
    List<String> canonical = new ArrayList<>(tokens == null ? List.of() : tokens);
    canonical.sort(String::compareTo);
    return canonical;
  }

  private String participantToken(com.w3llspring.fhpb.web.model.User player, boolean guest) {
    if (guest || player == null || player.getId() == null) {
      return "guest";
    }
    return "user:" + player.getId();
  }

  private boolean sameUser(User candidate, Long userId) {
    return candidate != null
        && candidate.getId() != null
        && Objects.equals(candidate.getId(), userId);
  }

  private boolean matchesTeams(Match match, DetectionRequest request) {
    if (match == null || match.getState() == MatchState.NULLIFIED) {
      return false;
    }
    List<String> requestedTeamA = canonicalizeTeam(request.teamAKeys());
    List<String> requestedTeamB = canonicalizeTeam(request.teamBKeys());
    List<String> matchTeamA =
        canonicalizeTeam(
            teamTokens(match.getA1(), match.isA1Guest(), match.getA2(), match.isA2Guest()));
    List<String> matchTeamB =
        canonicalizeTeam(
            teamTokens(match.getB1(), match.isB1Guest(), match.getB2(), match.isB2Guest()));

    boolean directTeams = matchTeamA.equals(requestedTeamA) && matchTeamB.equals(requestedTeamB);
    if (directTeams) {
      return true;
    }

    return matchTeamA.equals(requestedTeamB) && matchTeamB.equals(requestedTeamA);
  }

  private boolean matchesTeamsAndScore(Match match, DetectionRequest request) {
    if (!matchesTeams(match, request)) {
      return false;
    }
    List<String> requestedTeamA = canonicalizeTeam(request.teamAKeys());
    List<String> requestedTeamB = canonicalizeTeam(request.teamBKeys());
    List<String> matchTeamA =
        canonicalizeTeam(
            teamTokens(match.getA1(), match.isA1Guest(), match.getA2(), match.isA2Guest()));
    List<String> matchTeamB =
        canonicalizeTeam(
            teamTokens(match.getB1(), match.isB1Guest(), match.getB2(), match.isB2Guest()));

    boolean directTeams = matchTeamA.equals(requestedTeamA) && matchTeamB.equals(requestedTeamB);
    if (directTeams) {
      return match.getScoreA() == request.scoreA() && match.getScoreB() == request.scoreB();
    }

    return match.getScoreA() == request.scoreB() && match.getScoreB() == request.scoreA();
  }

  private record CandidateWarning(
      Match match, List<String> confirmedNames, boolean exactScoreMatch) {}

  public record DetectionRequest(
      Long seasonId,
      Long sessionConfigId,
      List<String> teamAKeys,
      List<String> teamBKeys,
      int scoreA,
      int scoreB,
      Instant referenceTime) {}

  public record RecentDuplicateMatchWarning(Long matchId, String message) {}
}
