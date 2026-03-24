package com.w3llspring.fhpb.web.service.matchworkflow;

import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.MatchWorkflowRules;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionAutoModerationService;
import com.w3llspring.fhpb.web.service.ConfirmedMatchNullificationService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.MatchFactory;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.ScoreValidationResult;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Service
public class MatchStateTransitionService {

    private final MatchRepository matchRepository;
    private final MatchFactory matchFactory;
    private final MatchConfirmationService matchConfirmationService;
    private final MatchValidationService matchValidationService;
    private final LadderV2Service ladderV2Service;
    private final TrophyAwardService trophyAwardService;

    private RoundRobinService roundRobinService;

    private CompetitionAutoModerationService competitionAutoModerationService;
    private ConfirmedMatchNullificationService confirmedMatchNullificationService;

    @Autowired
    public MatchStateTransitionService(MatchRepository matchRepository,
                                       MatchFactory matchFactory,
                                       MatchConfirmationService matchConfirmationService,
                                       MatchValidationService matchValidationService,
                                       LadderV2Service ladderV2Service,
                                       TrophyAwardService trophyAwardService,
                                       RoundRobinService roundRobinService,
                                       CompetitionAutoModerationService competitionAutoModerationService,
                                       ConfirmedMatchNullificationService confirmedMatchNullificationService) {
        this.matchRepository = matchRepository;
        this.matchFactory = matchFactory;
        this.matchConfirmationService = matchConfirmationService;
        this.matchValidationService = matchValidationService;
        this.ladderV2Service = ladderV2Service;
        this.trophyAwardService = trophyAwardService;
        this.roundRobinService = roundRobinService;
        this.competitionAutoModerationService = competitionAutoModerationService;
        this.confirmedMatchNullificationService = confirmedMatchNullificationService;
    }

    public MatchStateTransitionService(MatchRepository matchRepository,
                                       MatchFactory matchFactory,
                                       MatchConfirmationService matchConfirmationService,
                                       MatchValidationService matchValidationService,
                                       LadderV2Service ladderV2Service,
                                       TrophyAwardService trophyAwardService) {
        this(matchRepository, matchFactory, matchConfirmationService, matchValidationService, ladderV2Service,
                trophyAwardService, null, null, null);
    }


    @Transactional
    public Match editMatch(EditMatchCommand command) {
        if (command == null || command.matchId() == null) {
            throw new IllegalArgumentException("Match not found");
        }
        if (command.actor() == null || command.actor().getId() == null) {
            throw new SecurityException("Authentication required");
        }

        Match match = loadLockedMatch(command.matchId());
        boolean ladderAdmin = command.ladderAdmin();
        requireExpectedVersion(match, command.expectedVersion(),
                "This match was updated by someone else. Reload and review the latest version before editing.");

        if (match.getState() == MatchState.NULLIFIED) {
            throw new IllegalStateException("This match is no longer editable.");
        }
        if (match.getState() == MatchState.CONFIRMED && !ladderAdmin) {
            throw new SecurityException("Only ladder admins can edit confirmed matches.");
        }
        if (match.getState() == MatchState.FLAGGED && !ladderAdmin) {
            throw new SecurityException("Only ladder admins can edit disputed matches.");
        }

        if (!MatchWorkflowRules.canEdit(match, command.actor(), ladderAdmin)) {
            throw new SecurityException("You do not have permission to edit this match.");
        }
        requireCompetitionEligibility(command.actor(), match.getSeason());

        ScoreValidationResult scoreValidation =
                matchValidationService.validateScore(command.scoreA(), command.scoreB());
        if (!scoreValidation.isValid()) {
            throw new IllegalArgumentException(scoreValidation.getErrorMessage());
        }

        if (hasSamePlayerOnBothTeams(
                command.nextA1(), command.nextA1Guest(),
                command.nextA2(), command.nextA2Guest(),
                command.nextB1(), command.nextB1Guest(),
                command.nextB2(), command.nextB2Guest())) {
            throw new IllegalArgumentException("A player cannot be selected on both teams.");
        }

        requireMeaningfulEdit(match, command, ladderAdmin);

        if (isTournamentMode(match.getSeason()) && roundRobinService != null) {
            roundRobinService.assertTournamentMatchParticipants(
                    match.getId(),
                    command.nextA1(),
                    command.nextA2(),
                    command.nextB1(),
                    command.nextB2());
        }

        if (ladderAdmin) {
            match.setA1(command.nextA1());
            match.setA1Guest(command.nextA1Guest());
        }
        match.setA2(command.nextA2());
        match.setB1(command.nextB1());
        match.setB2(command.nextB2());
        match.setA2Guest(command.nextA2Guest());
        match.setB1Guest(command.nextB1Guest());
        match.setB2Guest(command.nextB2Guest());

        if (!isGuestOnlyPersonalRecordAllowed(match.getSeason()) && match.hasGuestOnlyOpposingTeam()) {
            throw new IllegalArgumentException(
                    "This ladder does not allow personal record matches with all-opponent guests.");
        }

        match.setScoreA(command.scoreA());
        match.setScoreB(command.scoreB());
        matchFactory.applyStandingsExclusionPolicy(match);
        match.setUserCorrected(true);
        match.setEditedBy(command.actor());
        match.setEditedAt(Instant.now());

        Match saved = matchRepository.saveAndFlush(match);
        if (saved.getState() == MatchState.CONFIRMED && confirmedMatchNullificationService != null) {
            confirmedMatchNullificationService.clearRequestsForMatch(saved);
        }
        if (saved.getState() == MatchState.PROVISIONAL) {
            matchConfirmationService.rebuildConfirmationRequests(saved);
        }
        ladderV2Service.applyMatch(saved);
        trophyAwardService.evaluateMatch(saved);
        return saved;
    }

    @Transactional
    public Match nullifyMatch(Long matchId, User actor, boolean ladderAdmin) {
        return nullifyMatch(matchId, actor, ladderAdmin, null);
    }

    @Transactional
    public Match nullifyMatch(Long matchId, User actor, boolean ladderAdmin, Long expectedVersion) {
        if (matchId == null) {
            throw new IllegalArgumentException("Match not found");
        }
        if (actor == null || actor.getId() == null) {
            throw new SecurityException("Authentication required");
        }

        Match match = loadLockedMatch(matchId);
        requireExpectedVersion(match, expectedVersion,
                "This match was updated by someone else. Reload and review the latest version before deleting it.");
        if (!ladderAdmin) {
            boolean isLogger = match.getLoggedBy() != null
                    && match.getLoggedBy().getId() != null
                    && match.getLoggedBy().getId().equals(actor.getId());
            if (!isLogger) {
                throw new SecurityException("Only the match creator or ladder admin can delete this match");
            }
            if (!match.isDeletableBy(actor)) {
                throw new IllegalStateException("This match cannot be deleted in its current state");
            }
        }

        if (match.getState() == MatchState.NULLIFIED) {
            return match;
        }

        if (confirmedMatchNullificationService != null) {
            confirmedMatchNullificationService.clearRequestsForMatch(match);
        }
        ladderV2Service.onMatchNullified(match);
        return match;
    }

    @Transactional
    public Match reopenDisputedMatch(Long matchId, User actor, boolean ladderAdmin) {
        if (matchId == null) {
            throw new IllegalArgumentException("Match not found");
        }
        if (actor == null || actor.getId() == null) {
            throw new SecurityException("Authentication required");
        }
        if (!ladderAdmin) {
            throw new SecurityException("Only ladder admins can reopen disputed matches.");
        }

        Match match = loadLockedMatch(matchId);
        if (match.getState() != MatchState.FLAGGED) {
            throw new IllegalStateException("Only disputed matches can be reopened.");
        }

        match.setState(MatchState.PROVISIONAL);
        match.setDisputedBy(null);
        match.setDisputedAt(null);
        match.setDisputeNote(null);
        Match saved = matchRepository.saveAndFlush(match);
        matchConfirmationService.rebuildConfirmationRequests(saved);
        ladderV2Service.applyMatch(saved);
        return saved;
    }

    private Match loadLockedMatch(Long matchId) {
        return matchRepository.findByIdWithUsersForUpdate(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    }

    private void requireExpectedVersion(Match match, Long expectedVersion, String message) {
        if (expectedVersion == null) {
            return;
        }
        if (match == null || match.getVersion() != expectedVersion.longValue()) {
            throw new OptimisticLockingFailureException(message);
        }
    }

    private boolean isGuestOnlyPersonalRecordAllowed(LadderSeason season) {
        if (season == null || season.getLadderConfig() == null) {
            return false;
        }
        return season.getLadderConfig().isAllowGuestOnlyPersonalMatches()
                && season.getLadderConfig().getSecurityLevel() != null
                && com.w3llspring.fhpb.web.model.LadderSecurity
                        .normalize(season.getLadderConfig().getSecurityLevel())
                        .isSelfConfirm();
    }

    private void requireCompetitionEligibility(User user, LadderSeason season) {
        if (competitionAutoModerationService == null) {
            return;
        }
        competitionAutoModerationService.requireNotBlocked(user, season);
    }

    private boolean isTournamentMode(LadderSeason season) {
        return season != null
                && season.getLadderConfig() != null
                && season.getLadderConfig().isTournamentMode();
    }

    private boolean hasSamePlayerOnBothTeams(User a1,
                                             boolean a1Guest,
                                             User a2,
                                             boolean a2Guest,
                                             User b1,
                                             boolean b1Guest,
                                             User b2,
                                             boolean b2Guest) {
        Set<Long> teamAIds = new HashSet<>();
        Set<Long> teamBIds = new HashSet<>();
        addRealPlayerId(teamAIds, a1, a1Guest);
        addRealPlayerId(teamAIds, a2, a2Guest);
        addRealPlayerId(teamBIds, b1, b1Guest);
        addRealPlayerId(teamBIds, b2, b2Guest);
        return teamAIds.stream().anyMatch(teamBIds::contains);
    }

    private void addRealPlayerId(Set<Long> ids, User player, boolean guest) {
        if (ids == null || guest || player == null || player.getId() == null) {
            return;
        }
        ids.add(player.getId());
    }

    private void requireMeaningfulEdit(Match match, EditMatchCommand command, boolean ladderAdmin) {
        User effectiveA1 = ladderAdmin ? command.nextA1() : match.getA1();
        boolean effectiveA1Guest = ladderAdmin ? command.nextA1Guest() : match.isA1Guest();

        boolean unchanged = sameSlot(match.getA1(), match.isA1Guest(), effectiveA1, effectiveA1Guest)
                && sameSlot(match.getA2(), match.isA2Guest(), command.nextA2(), command.nextA2Guest())
                && sameSlot(match.getB1(), match.isB1Guest(), command.nextB1(), command.nextB1Guest())
                && sameSlot(match.getB2(), match.isB2Guest(), command.nextB2(), command.nextB2Guest())
                && match.getScoreA() == command.scoreA()
                && match.getScoreB() == command.scoreB();
        if (unchanged) {
            throw new IllegalArgumentException("No changes were made to this match.");
        }
    }

    private boolean sameSlot(User currentUser, boolean currentGuest, User nextUser, boolean nextGuest) {
        if (currentGuest != nextGuest) {
            return false;
        }
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        Long nextUserId = nextUser != null ? nextUser.getId() : null;
        return Objects.equals(currentUserId, nextUserId);
    }

    public record EditMatchCommand(Long matchId,
                                   User actor,
                                   boolean ladderAdmin,
                                   Long expectedVersion,
                                   User nextA1,
                                   boolean nextA1Guest,
                                   User nextA2,
                                   boolean nextA2Guest,
                                   User nextB1,
                                   boolean nextB1Guest,
                                   User nextB2,
                                   boolean nextB2Guest,
                                   int scoreA,
                                   int scoreB) {

        public EditMatchCommand {
            if (actor == null || actor.getId() == null) {
                throw new IllegalArgumentException("Actor is required.");
            }
            if (nextA1 != null && nextA1.getId() == null) {
                nextA1 = null;
            }
            if (nextA2 != null && nextA2.getId() == null) {
                nextA2 = null;
            }
            if (nextB1 != null && nextB1.getId() == null) {
                nextB1 = null;
            }
            if (nextB2 != null && nextB2.getId() == null) {
                nextB2 = null;
            }
        }
    }
}
