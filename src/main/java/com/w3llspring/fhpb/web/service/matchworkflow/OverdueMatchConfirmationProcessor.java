package com.w3llspring.fhpb.web.service.matchworkflow;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.matchlog.LearningService;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OverdueMatchConfirmationProcessor {

  private static final Logger log =
      LoggerFactory.getLogger(OverdueMatchConfirmationProcessor.class);

  private final MatchConfirmationRepository confirmRepo;
  private final MatchRepository matchRepo;
  private final MatchValidationService matchValidationService;
  private final LearningService learningService;

  public OverdueMatchConfirmationProcessor(
      MatchConfirmationRepository confirmRepo,
      MatchRepository matchRepo,
      MatchValidationService matchValidationService,
      LearningService learningService) {
    this.confirmRepo = confirmRepo;
    this.matchRepo = matchRepo;
    this.matchValidationService = matchValidationService;
    this.learningService = learningService;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processOverdueMatch(long matchId, Instant cutoff) {
    Match match = loadLockedMatch(matchId);
    if (match == null || match.getCreatedAt() == null) {
      return;
    }
    if (match.getCreatedAt().isAfter(cutoff)) {
      return;
    }
    if (match.isConfirmationLocked()) {
      return;
    }
    if (match.getState() == MatchState.CONFIRMED
        || match.getState() == MatchState.FLAGGED
        || match.getState() == MatchState.NULLIFIED) {
      return;
    }

    List<MatchConfirmation> confirmations = loadConfirmations(match);
    if (isReadyToConfirm(match, confirmations)) {
      markConfirmed(match);
      return;
    }

    MatchValidationService.ScoreValidationResult scoreValidation;
    try {
      scoreValidation = matchValidationService.validateScore(match.getScoreA(), match.getScoreB());
    } catch (Exception ex) {
      log.warn("Score validation failed for match {}: {}", match.getId(), ex.getMessage());
      scoreValidation =
          MatchValidationService.ScoreValidationResult.invalid("Match score is not valid.");
    }
    if (!scoreValidation.isValid()) {
      nullifyMatch(match, "invalid score", false);
      return;
    }

    if (!LadderSecurity.normalize(resolveSecurityLevel(match)).requiresOpponentConfirmation()) {
      return;
    }

    nullifyMatch(match, "overdue confirmation window", true);
  }

  private Match loadLockedMatch(long matchId) {
    return matchRepo
        .findByIdWithUsersForUpdate(matchId)
        .or(() -> matchRepo.findByIdWithUsers(matchId))
        .orElse(null);
  }

  private List<MatchConfirmation> loadConfirmations(Match match) {
    List<MatchConfirmation> confirmations = confirmRepo.findByMatch(match);
    return confirmations != null ? new ArrayList<>(confirmations) : List.of();
  }

  private boolean isReadyToConfirm(Match match, List<MatchConfirmation> confirmations) {
    return teamSatisfied(match, confirmations, "A") && teamSatisfied(match, confirmations, "B");
  }

  private boolean teamSatisfied(Match match, List<MatchConfirmation> confirmations, String team) {
    return participantsForTeam(match, team).isEmpty() || teamHasConfirmed(confirmations, team);
  }

  private boolean teamHasConfirmed(List<MatchConfirmation> confirmations, String team) {
    return confirmations.stream()
        .anyMatch(mc -> team.equals(mc.getTeam()) && mc.getConfirmedAt() != null);
  }

  private List<User> participantsForTeam(Match match, String team) {
    Map<Long, User> participants = new LinkedHashMap<>();
    addParticipant(participants, "A".equals(team) ? match.getA1() : match.getB1());
    addParticipant(participants, "A".equals(team) ? match.getA2() : match.getB2());
    return new ArrayList<>(participants.values());
  }

  private void addParticipant(Map<Long, User> participants, User candidate) {
    if (candidate == null || candidate.getId() == null) {
      return;
    }
    participants.putIfAbsent(candidate.getId(), candidate);
  }

  private LadderSecurity resolveSecurityLevel(Match match) {
    if (match == null || match.getSeason() == null || match.getSeason().getLadderConfig() == null) {
      return LadderSecurity.STANDARD;
    }
    LadderSecurity securityLevel = match.getSeason().getLadderConfig().getSecurityLevel();
    return securityLevel != null ? securityLevel : LadderSecurity.STANDARD;
  }

  private void markConfirmed(Match match) {
    if (match == null || match.getState() == MatchState.CONFIRMED) {
      return;
    }

    match.setState(MatchState.CONFIRMED);
    matchRepo.save(match);
    log.info("Match {} transitioned to CONFIRMED during overdue maintenance", match.getId());

    if (learningService != null && match.isUserCorrected() && match.getId() != null) {
      try {
        learningService.applyCorrectionsForMatch(match.getId());
      } catch (Exception ex) {
        log.warn(
            "LearningService failed to apply corrections for match {}: {}",
            match.getId(),
            ex.getMessage());
      }
    }
  }

  private void nullifyMatch(Match match, String reason, boolean preserveConfirmationRows) {
    if (!preserveConfirmationRows) {
      try {
        confirmRepo.deleteByMatch(match);
      } catch (Exception ex) {
        log.warn(
            "Failed to delete confirmation rows for match {}: {}", match.getId(), ex.getMessage());
      }
    }

    match.setState(MatchState.NULLIFIED);
    matchRepo.save(match);
    log.info("Marked match {} as NULLIFIED due to {}", match.getId(), reason);
  }
}
