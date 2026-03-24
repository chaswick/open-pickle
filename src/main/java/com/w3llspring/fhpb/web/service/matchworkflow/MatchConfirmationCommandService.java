package com.w3llspring.fhpb.web.service.matchworkflow;

import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.RecentDuplicateMatchWarningService;
import com.w3llspring.fhpb.web.service.ConfirmedMatchNullificationService;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Objects;

public class MatchConfirmationCommandService {

    private static final Logger log = LoggerFactory.getLogger(MatchConfirmationCommandService.class);

    private final MatchConfirmationService confirmationService;
    private final MatchRepository matchRepo;
    private final LadderV2Service ladderV2Service;
    private final LadderAccessService ladderAccessService;
    private final MatchStateTransitionService matchStateTransitionService;
    private final RecentDuplicateMatchWarningService recentDuplicateMatchWarningService;
    private final TrophyAwardService trophyAwardService;
    private final ConfirmedMatchNullificationService confirmedMatchNullificationService;

    public MatchConfirmationCommandService(MatchConfirmationService confirmationService,
                                           MatchRepository matchRepo,
                                           LadderV2Service ladderV2Service,
                                           LadderAccessService ladderAccessService,
                                           MatchStateTransitionService matchStateTransitionService,
                                           RecentDuplicateMatchWarningService recentDuplicateMatchWarningService,
                                           TrophyAwardService trophyAwardService,
                                           ConfirmedMatchNullificationService confirmedMatchNullificationService) {
        this.confirmationService = confirmationService;
        this.matchRepo = matchRepo;
        this.ladderV2Service = ladderV2Service;
        this.ladderAccessService = ladderAccessService;
        this.matchStateTransitionService = matchStateTransitionService;
        this.recentDuplicateMatchWarningService = recentDuplicateMatchWarningService;
        this.trophyAwardService = trophyAwardService;
        this.confirmedMatchNullificationService = confirmedMatchNullificationService;
    }

    public MatchCommandResult confirmMatch(Long matchId,
                                           Long expectedVersion,
                                           Long duplicateWarningAcceptedMatchId,
                                           User user) {
        if (user == null || user.getId() == null) {
            return MatchCommandResult.error(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Match match = matchRepo.findByIdWithUsers(matchId).orElse(null);
        if (match == null) {
            return MatchCommandResult.error(HttpStatus.NOT_FOUND, "Match not found");
        }
        if (!matchesExpectedVersion(match, expectedVersion)) {
            return MatchCommandResult.error(HttpStatus.CONFLICT,
                    "This match was updated by someone else. Reload and review the latest version before confirming.");
        }

        boolean wasConfirmed = match.getState() == MatchState.CONFIRMED;
        if (!wasConfirmed && recentDuplicateMatchWarningService != null) {
            var duplicateWarning = recentDuplicateMatchWarningService.findConfirmedWarningForMatch(match, Instant.now());
            if (duplicateWarning.isPresent()
                    && !Objects.equals(duplicateWarningAcceptedMatchId, duplicateWarning.get().matchId())) {
                return MatchCommandResult.warning(
                        HttpStatus.CONFLICT,
                        "duplicateConfirmedMatch",
                        duplicateWarning.get().matchId(),
                        duplicateWarning.get().message());
            }
        }

        try {
            boolean firstManual = confirmationService.confirmMatch(matchId, user.getId(), expectedVersion);
            finishPostConfirmUpdates(matchId, wasConfirmed);
            return MatchCommandResult.ok(firstManual);
        } catch (IllegalStateException ex) {
            log.info("Match confirmation conflict for match {} by user {}: {}", matchId, user.getId(), ex.getMessage());
            return MatchCommandResult.error(HttpStatus.CONFLICT, ex.getMessage());
        } catch (SecurityException ex) {
            log.info("Match confirmation forbidden for match {} by user {}: {}", matchId, user.getId(), ex.getMessage());
            return MatchCommandResult.error(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("Match confirmation rejected for match {} by user {}: {}", matchId, user.getId(), ex.getMessage());
            return MatchCommandResult.error(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            log.info("Match confirmation optimistic locking conflict for match {} by user {}", matchId, user.getId());
            return MatchCommandResult.error(HttpStatus.CONFLICT,
                    "This match was updated by someone else. Reload and try again.");
        } catch (Exception ex) {
            log.error("Unexpected error during match confirmation for match {} by user {}", matchId, user.getId(), ex);
            return MatchCommandResult.error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    public MatchCommandResult disputeMatch(Long matchId,
                                           String note,
                                           Long expectedVersion,
                                           User user) {
        if (user == null || user.getId() == null) {
            return MatchCommandResult.error(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Match match = matchRepo.findByIdWithUsers(matchId).orElse(null);
        if (match == null) {
            return MatchCommandResult.error(HttpStatus.NOT_FOUND, "Match not found");
        }
        if (!matchesExpectedVersion(match, expectedVersion)) {
            return MatchCommandResult.error(HttpStatus.CONFLICT,
                    "This match was updated by someone else. Reload and review the latest version before disputing.");
        }

        try {
            confirmationService.disputeMatch(matchId, user.getId(), note, expectedVersion);
            return MatchCommandResult.ok();
        } catch (IllegalStateException ex) {
            log.info("Match dispute conflict for match {} by user {}: {}", matchId, user.getId(), ex.getMessage());
            return MatchCommandResult.error(HttpStatus.CONFLICT, ex.getMessage());
        } catch (SecurityException ex) {
            log.info("Match dispute forbidden for match {} by user {}: {}", matchId, user.getId(), ex.getMessage());
            return MatchCommandResult.error(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("Match dispute rejected for match {} by user {}: {}", matchId, user.getId(), ex.getMessage());
            return MatchCommandResult.error(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            log.info("Match dispute optimistic locking conflict for match {} by user {}", matchId, user.getId());
            return MatchCommandResult.error(HttpStatus.CONFLICT,
                    "This match was updated by someone else. Reload and try again.");
        } catch (Exception ex) {
            log.error("Unexpected error during match dispute for match {} by user {}", matchId, user.getId(), ex);
            return MatchCommandResult.error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    public MatchCommandResult nullifyMatch(Long matchId,
                                           Long expectedVersion,
                                           User user) {
        if (user == null || user.getId() == null) {
            return MatchCommandResult.error(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Match match = matchRepo.findByIdWithUsers(matchId).orElse(null);
        if (match == null) {
            return MatchCommandResult.error(HttpStatus.NOT_FOUND, "Match not found");
        }
        if (!matchesExpectedVersion(match, expectedVersion)) {
            return MatchCommandResult.error(HttpStatus.CONFLICT,
                    "This match was updated by someone else. Reload and review the latest version before deleting it.");
        }

        try {
            boolean ladderAdmin = isLadderAdmin(match, user);
            if (matchStateTransitionService != null) {
                matchStateTransitionService.nullifyMatch(matchId, user, ladderAdmin, expectedVersion);
            } else if (ladderV2Service != null) {
                if (!ladderAdmin && !match.isDeletableBy(user)) {
                    return MatchCommandResult.error(HttpStatus.FORBIDDEN,
                            "Only the match creator or ladder admin can delete this match");
                }
                ladderV2Service.onMatchNullified(match);
            } else {
                if (!ladderAdmin && !match.isDeletableBy(user)) {
                    return MatchCommandResult.error(HttpStatus.FORBIDDEN,
                            "Only the match creator or ladder admin can delete this match");
                }
                match.setState(MatchState.NULLIFIED);
                matchRepo.save(match);
            }
            return MatchCommandResult.ok();
        } catch (SecurityException ex) {
            return MatchCommandResult.error(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalStateException ex) {
            return MatchCommandResult.error(HttpStatus.CONFLICT, ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            return MatchCommandResult.error(HttpStatus.CONFLICT,
                    "This match was updated by someone else. Reload and try again.");
        } catch (Exception ex) {
            log.error("Failed to nullify match {}", matchId, ex);
            return MatchCommandResult.error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    public MatchCommandResult requestConfirmedNullification(Long matchId,
                                                            Long expectedVersion,
                                                            User user) {
        if (user == null || user.getId() == null) {
            return MatchCommandResult.error(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (confirmedMatchNullificationService == null) {
            return MatchCommandResult.error(HttpStatus.NOT_IMPLEMENTED,
                    "Confirmed-match removal requests are not available.");
        }

        Match match = matchRepo.findByIdWithUsers(matchId).orElse(null);
        if (match == null) {
            return MatchCommandResult.error(HttpStatus.NOT_FOUND, "Match not found");
        }
        if (!matchesExpectedVersion(match, expectedVersion)) {
            return MatchCommandResult.error(HttpStatus.CONFLICT,
                    "This match was updated by someone else. Reload and review the latest version before continuing.");
        }

        try {
            ConfirmedMatchNullificationService.NullificationRequestResult result =
                    confirmedMatchNullificationService.requestNullification(matchId, user.getId(), expectedVersion);
            return MatchCommandResult.ok(result.message());
        } catch (IllegalStateException ex) {
            log.info("Confirmed-match removal conflict for match {} by user {}: {}",
                    matchId, user.getId(), ex.getMessage());
            return MatchCommandResult.error(HttpStatus.CONFLICT, ex.getMessage());
        } catch (SecurityException ex) {
            log.info("Confirmed-match removal forbidden for match {} by user {}: {}",
                    matchId, user.getId(), ex.getMessage());
            return MatchCommandResult.error(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("Confirmed-match removal rejected for match {} by user {}: {}",
                    matchId, user.getId(), ex.getMessage());
            return MatchCommandResult.error(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            return MatchCommandResult.error(HttpStatus.CONFLICT,
                    "This match was updated by someone else. Reload and try again.");
        } catch (Exception ex) {
            log.error("Failed confirmed-match removal request for match {}", matchId, ex);
            return MatchCommandResult.error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    private void finishPostConfirmUpdates(Long matchId, boolean wasConfirmed) {
        try {
            Match updated = matchRepo.findByIdWithUsers(matchId).orElse(null);
            if (!wasConfirmed
                    && updated != null
                    && updated.getState() == MatchState.CONFIRMED) {
                if (ladderV2Service != null) {
                    ladderV2Service.applyMatch(updated);
                }
                if (trophyAwardService != null) {
                    trophyAwardService.evaluateMatch(updated);
                }
            }
        } catch (Exception ex) {
            log.warn("Confirmed match {} but failed to finish post-confirm updates", matchId, ex);
        }
    }

    private boolean isLadderAdmin(Match match, User user) {
        if (ladderAccessService == null
                || match == null
                || match.getSeason() == null
                || match.getSeason().getId() == null) {
            return false;
        }

        try {
            return ladderAccessService.isSeasonAdmin(match.getSeason().getId(), user);
        } catch (Exception ex) {
            log.warn("Failed to check season admin status for match {} and user {}",
                    match.getId(), user != null ? user.getId() : null, ex);
            return false;
        }
    }

    private boolean matchesExpectedVersion(Match match, Long expectedVersion) {
        return match != null && expectedVersion != null && match.getVersion() == expectedVersion.longValue();
    }

    public record MatchCommandResult(HttpStatus status,
                                     boolean success,
                                     String message,
                                     Boolean firstManual,
                                     boolean warning,
                                     String warningCode,
                                     Long duplicateWarningMatchId) {

        public static MatchCommandResult ok() {
            return new MatchCommandResult(HttpStatus.OK, true, "OK", null, false, null, null);
        }

        public static MatchCommandResult ok(String message) {
            return new MatchCommandResult(HttpStatus.OK, true, message, null, false, null, null);
        }

        public static MatchCommandResult ok(boolean firstManual) {
            return new MatchCommandResult(HttpStatus.OK, true, "OK", firstManual, false, null, null);
        }

        public static MatchCommandResult error(HttpStatus status, String message) {
            return new MatchCommandResult(status, false, message, null, false, null, null);
        }

        public static MatchCommandResult warning(HttpStatus status,
                                                 String warningCode,
                                                 Long duplicateWarningMatchId,
                                                 String message) {
            return new MatchCommandResult(status, false, message, null, true, warningCode, duplicateWarningMatchId);
        }
    }
}
