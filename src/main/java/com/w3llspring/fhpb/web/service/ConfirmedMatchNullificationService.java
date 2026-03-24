package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchNullificationRequestRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchNullificationRequest;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ConfirmedMatchNullificationService {

    private static final Logger log = LoggerFactory.getLogger(ConfirmedMatchNullificationService.class);
    private static final Duration REQUEST_WINDOW = Duration.ofHours(48);

    private final MatchNullificationRequestRepository requestRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final LadderV2Service ladderV2Service;

    public ConfirmedMatchNullificationService(MatchNullificationRequestRepository requestRepository,
                                              MatchRepository matchRepository,
                                              UserRepository userRepository,
                                              LadderV2Service ladderV2Service) {
        this.requestRepository = requestRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.ladderV2Service = ladderV2Service;
    }

    @Transactional
    public NullificationRequestResult requestNullification(long matchId, long userId, Long expectedVersion) {
        Optional<Match> lockedMatch = matchRepository.findByIdWithUsersForUpdate(matchId);
        if (lockedMatch == null || lockedMatch.isEmpty()) {
            lockedMatch = matchRepository.findByIdWithUsers(matchId);
        }

        Match match = lockedMatch != null ? lockedMatch.orElse(null) : null;
        if (match == null) {
            throw new IllegalArgumentException("Match not found");
        }
        requireExpectedVersion(match, expectedVersion);

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        if (match.getState() == MatchState.NULLIFIED) {
            throw new IllegalStateException("This match is already nullified.");
        }
        if (match.getState() != MatchState.CONFIRMED) {
            throw new IllegalStateException("Only confirmed matches can be removed by agreement.");
        }

        String team = teamForUser(match, userId);
        if (team == null) {
            throw new IllegalArgumentException("User not a participant in match");
        }

        Instant now = Instant.now();
        List<MatchNullificationRequest> activeRequests = new ArrayList<>(requestRepository.findActiveByMatch(match, now));
        pruneExpiredRowsForMatch(match, activeRequests, now);

        String otherTeam = "A".equals(team) ? "B" : "A";
        MatchNullificationRequest sameTeamRequest = activeRequests.stream()
                .filter(request -> team.equals(request.getTeam()))
                .findFirst()
                .orElse(null);
        if (sameTeamRequest != null) {
            throw new IllegalStateException("Your team already requested removal for this match.");
        }

        boolean otherTeamHasParticipants = teamHasParticipants(match, otherTeam);
        MatchNullificationRequest oppositeTeamRequest = activeRequests.stream()
                .filter(request -> otherTeam.equals(request.getTeam()))
                .findFirst()
                .orElse(null);

        if (!otherTeamHasParticipants || oppositeTeamRequest != null) {
            clearRequestsForMatch(match);
            ladderV2Service.onMatchNullified(match);
            log.info("Match {} nullified by bilateral participant agreement", match.getId());
            return new NullificationRequestResult(true, "Match nullified.");
        }

        MatchNullificationRequest request = new MatchNullificationRequest();
        request.setMatch(match);
        request.setPlayer(user);
        request.setTeam(team);
        request.setRequestedAt(now);
        request.setExpiresAt(now.plus(REQUEST_WINDOW));
        requestRepository.save(request);

        log.info("Match {} removal requested by user {} on team {}", match.getId(), userId, team);
        return new NullificationRequestResult(false, "Removal requested.");
    }

    @Transactional
    public void clearRequestsForMatch(Match match) {
        if (match == null) {
            return;
        }
        try {
            requestRepository.deleteByMatch(match);
        } catch (Exception ex) {
            log.warn("Failed to clear confirmed-match removal requests for match {}: {}",
                    match.getId(), ex.getMessage());
        }
    }

    @Transactional
    public void pruneExpiredRequests() {
        Instant cutoff = Instant.now();
        try {
            int deleted = requestRepository.deleteByExpiresAtBefore(cutoff);
            if (deleted > 0) {
                log.info("Pruned {} expired confirmed-match removal requests older than {}", deleted, cutoff);
            }
        } catch (Exception ex) {
            log.warn("Failed to prune expired confirmed-match removal requests: {}", ex.getMessage());
        }
    }

    private void pruneExpiredRowsForMatch(Match match,
                                          List<MatchNullificationRequest> activeRequests,
                                          Instant now) {
        if (match == null) {
            return;
        }

        List<MatchNullificationRequest> allRows = requestRepository.findByMatchWithPlayer(match);
        for (MatchNullificationRequest request : allRows) {
            if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(now)) {
                requestRepository.delete(request);
                activeRequests.removeIf(active -> active.getId() != null && active.getId().equals(request.getId()));
            }
        }
    }

    private void requireExpectedVersion(Match match, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (match == null || match.getVersion() != expectedVersion.longValue()) {
            throw new OptimisticLockingFailureException(
                    "This match was updated by someone else. Reload and review the latest version before continuing.");
        }
    }

    private String teamForUser(Match match, long userId) {
        if (match == null) {
            return null;
        }
        if (isSameUser(match.getA1(), userId) || isSameUser(match.getA2(), userId)) {
            return "A";
        }
        if (isSameUser(match.getB1(), userId) || isSameUser(match.getB2(), userId)) {
            return "B";
        }
        return null;
    }

    private boolean teamHasParticipants(Match match, String team) {
        return !participantsForTeam(match, team).isEmpty();
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

    private boolean isSameUser(User candidate, long userId) {
        return candidate != null && candidate.getId() != null && candidate.getId().equals(userId);
    }

    public record NullificationRequestResult(boolean nullified, String message) {
    }
}
