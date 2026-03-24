package com.w3llspring.fhpb.web.service.competition;

import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CompetitionSessionService {

  private final LadderSeasonRepository seasonRepo;

  public CompetitionSessionService(LadderSeasonRepository seasonRepo) {
    this.seasonRepo = seasonRepo;
  }

  public CompetitionSessionSummary summarizeSessions(
      List<LadderMembership> memberships, Long currentUserId, Long requestedLadderId) {
    List<LadderMembership> sessionMemberships =
        competitionSessionMemberships(memberships, currentUserId);
    LadderMembership selectedSessionMembership =
        resolveSelectedCompetitionSession(sessionMemberships, requestedLadderId);
    boolean showSessionChooser =
        requiresCompetitionSessionChooser(sessionMemberships, currentUserId);
    boolean hasOwnedCompetitionSession =
        hasOwnedCompetitionSession(sessionMemberships, currentUserId);
    return new CompetitionSessionSummary(
        sessionMemberships,
        selectedSessionMembership,
        showSessionChooser,
        hasOwnedCompetitionSession);
  }

  public LadderSeason resolveCompetitionTargetSeason(
      LadderConfig sessionConfig, LadderSeason activeCompetitionSeason) {
    if (sessionConfig != null && sessionConfig.getTargetSeasonId() != null) {
      LadderSeason targetSeason =
          seasonRepo.findById(sessionConfig.getTargetSeasonId()).orElse(null);
      if (targetSeason != null
          && targetSeason.getLadderConfig() != null
          && targetSeason.getLadderConfig().isCompetitionType()) {
        return targetSeason;
      }
    }
    return activeCompetitionSeason;
  }

  private List<LadderMembership> competitionSessionMemberships(
      List<LadderMembership> memberships, Long currentUserId) {
    if (memberships == null || memberships.isEmpty()) {
      return List.of();
    }
    return memberships.stream()
        .filter(Objects::nonNull)
        .filter(membership -> isActiveSessionConfig(membership.getLadderConfig()))
        .sorted(
            Comparator.comparing(
                    (LadderMembership membership) -> {
                      LadderConfig config = membership.getLadderConfig();
                      return config == null
                          || !Objects.equals(config.getOwnerUserId(), currentUserId);
                    })
                .thenComparing(
                    LadderMembership::getJoinedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                    membership -> {
                      LadderConfig config = membership.getLadderConfig();
                      return config != null ? config.getCreatedAt() : null;
                    },
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                    membership -> {
                      LadderConfig config = membership.getLadderConfig();
                      return config != null ? config.getId() : null;
                    },
                    Comparator.nullsLast(Comparator.reverseOrder())))
        .collect(Collectors.toList());
  }

  private LadderMembership resolveSelectedCompetitionSession(
      List<LadderMembership> sessionMemberships, Long requestedLadderId) {
    if (sessionMemberships == null || sessionMemberships.isEmpty()) {
      return null;
    }
    if (requestedLadderId != null) {
      return sessionMemberships.stream()
          .filter(membership -> membership.getLadderConfig() != null)
          .filter(
              membership -> Objects.equals(membership.getLadderConfig().getId(), requestedLadderId))
          .findFirst()
          .orElse(sessionMemberships.get(0));
    }
    return sessionMemberships.get(0);
  }

  private boolean requiresCompetitionSessionChooser(
      List<LadderMembership> sessionMemberships, Long currentUserId) {
    if (sessionMemberships == null || sessionMemberships.isEmpty()) {
      return false;
    }
    if (sessionMemberships.size() > 1) {
      return true;
    }
    LadderConfig config = sessionMemberships.get(0).getLadderConfig();
    return config == null || !Objects.equals(config.getOwnerUserId(), currentUserId);
  }

  private boolean hasOwnedCompetitionSession(
      List<LadderMembership> sessionMemberships, Long currentUserId) {
    if (sessionMemberships == null || sessionMemberships.isEmpty() || currentUserId == null) {
      return false;
    }
    return sessionMemberships.stream()
        .map(LadderMembership::getLadderConfig)
        .filter(Objects::nonNull)
        .anyMatch(config -> Objects.equals(config.getOwnerUserId(), currentUserId));
  }

  private boolean isActiveSessionConfig(LadderConfig config) {
    if (config == null || !config.isSessionType()) {
      return false;
    }
    return config.getExpiresAt() == null || config.getExpiresAt().isAfter(Instant.now());
  }

  public record CompetitionSessionSummary(
      List<LadderMembership> sessionMemberships,
      LadderMembership selectedSessionMembership,
      boolean showSessionChooser,
      boolean hasOwnedCompetitionSession) {}
}
