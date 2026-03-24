package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchNullificationRequestRepository;
import com.w3llspring.fhpb.web.model.MatchWorkflowRules;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchNullificationRequest;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the small set of model attributes consumed by the `matchRow` fragments.
 *
 * This keeps controllers thin and makes the model-building logic easy to unit test.
 */
@Component
public class MatchRowModelBuilder {

    private final MatchConfirmationService matchConfirmationService;
    private final MatchConfirmationRepository matchConfirmationRepository;
    private final LadderAccessService ladderAccessService;
    private final MatchNullificationRequestRepository matchNullificationRequestRepository;

    @Autowired
    public MatchRowModelBuilder(MatchConfirmationService matchConfirmationService,
                                MatchConfirmationRepository matchConfirmationRepository,
                                LadderAccessService ladderAccessService,
                                MatchNullificationRequestRepository matchNullificationRequestRepository) {
        this.matchConfirmationService = matchConfirmationService;
        this.matchConfirmationRepository = matchConfirmationRepository;
        this.ladderAccessService = ladderAccessService;
        this.matchNullificationRequestRepository = matchNullificationRequestRepository;
    }

    public MatchRowModelBuilder(MatchConfirmationService matchConfirmationService,
                                MatchConfirmationRepository matchConfirmationRepository,
                                LadderAccessService ladderAccessService) {
        this(matchConfirmationService, matchConfirmationRepository, ladderAccessService, null);
    }

    /**
     * Build a MatchRowModel for the given viewer and matches.
     */
    public MatchRowModel buildFor(User viewer, List<Match> matches) {
        // Delegate to the extended overload with no precomputed pending set
        return buildFor(viewer, matches, null);
    }

    /**
     * Overload that accepts a precomputed set of pending/confirmable match ids.
     * If precomputedPendingMatchIds is null the method will query MatchConfirmationService
     * as before. This avoids duplicate remote/repo calls when the caller already has
     * the pending ids (e.g. HomeController).
     */
    public MatchRowModel buildFor(User viewer, List<Match> matches, Set<Long> precomputedPendingMatchIds) {
        Set<Long> confirmableMatchIds = Collections.emptySet();
        Map<Long, String> confirmerByMatchId = new HashMap<>();
        Map<Long, Boolean> casualAutoConfirmedByMatchId = new HashMap<>();
        Map<Long, Boolean> pendingByMatchId = new HashMap<>();
        Map<Long, Boolean> editableByMatchId = new HashMap<>();
        Map<Long, Boolean> deletableByMatchId = new HashMap<>();
        Map<Long, Boolean> nullifyRequestableByMatchId = new HashMap<>();
        Map<Long, Boolean> nullifyApprovableByMatchId = new HashMap<>();
        Map<Long, Boolean> nullifyWaitingOnOpponentByMatchId = new HashMap<>();
        Map<Long, Boolean> seasonAdminBySeasonId = new HashMap<>();

        if (matches == null || matches.isEmpty()) {
            return new MatchRowModel(confirmableMatchIds, confirmerByMatchId, casualAutoConfirmedByMatchId,
                    pendingByMatchId, Map.of(), editableByMatchId, deletableByMatchId,
                    nullifyRequestableByMatchId, nullifyApprovableByMatchId, nullifyWaitingOnOpponentByMatchId);
        }

        List<Long> matchIds = matches.stream()
                .filter(Objects::nonNull)
                .map(Match::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<Long, List<MatchConfirmation>> confirmationsByMatch = matchIds.isEmpty()
                ? Collections.emptyMap()
                : matchConfirmationRepository.findByMatchIdIn(matchIds).stream()
                        .filter(mc -> mc.getMatch() != null && mc.getMatch().getId() != null)
                        .collect(Collectors.groupingBy(mc -> mc.getMatch().getId()));
        Map<Long, List<MatchNullificationRequest>> nullificationRequestsByMatch = loadActiveNullificationRequests(matchIds);

        // 1) confirmerByMatchId: for confirmed matches, show the confirmed representative(s)
        for (Match m : matches) {
            if (m != null && m.getState() == MatchState.CONFIRMED) {
                List<MatchConfirmation> confs = confirmationsByMatch.getOrDefault(m.getId(), Collections.emptyList());
                if (confs.stream().anyMatch(MatchConfirmation::isCasualModeAutoConfirmed)) {
                    casualAutoConfirmedByMatchId.put(m.getId(), Boolean.TRUE);
                }
                String formatted = formatConfirmers(confs);
                if (formatted != null) {
                    confirmerByMatchId.put(m.getId(), formatted);
                }
            }
        }

        // 2) pendingByMatchId (and confirmableMatchIds) - use precomputed set if provided
        Set<Long> pendingMatchIds = Collections.emptySet();
        if (precomputedPendingMatchIds != null) {
            pendingMatchIds = precomputedPendingMatchIds;
        } else if (viewer != null && viewer.getId() != null) {
            List<MatchConfirmation> pending = matchConfirmationService.pendingForUser(viewer.getId());
            pendingMatchIds = pending.stream().map(pc -> pc.getMatch().getId()).collect(Collectors.toSet());
        }
        for (Long id : pendingMatchIds) pendingByMatchId.put(id, Boolean.TRUE);
        confirmableMatchIds = Collections.unmodifiableSet(pendingMatchIds);

        // 3) editableByMatchId: rely on the shared workflow rule so row affordances
        // match controller/service enforcement.
        for (Match m : matches) {
            if (m == null || viewer == null) {
                continue;
            }
            boolean seasonAdminEditable = canEditAsSeasonAdmin(m, viewer, seasonAdminBySeasonId);
            editableByMatchId.put(m.getId(), MatchWorkflowRules.canEdit(m, viewer, seasonAdminEditable));
        }

        // 4) deletableByMatchId: use Match.isDeletableBy(viewer) to check if user is the original logger
        for (Match m : matches) {
            if (m == null || viewer == null) {
                continue;
            }
            deletableByMatchId.put(m.getId(), m.isDeletableBy(viewer));
        }

        for (Match m : matches) {
            if (m == null || m.getId() == null || viewer == null || viewer.getId() == null) {
                continue;
            }
            if (m.getState() != MatchState.CONFIRMED) {
                continue;
            }

            String viewerTeam = MatchWorkflowRules.teamForUser(m, viewer);
            if (viewerTeam == null) {
                continue;
            }

            List<MatchNullificationRequest> requests =
                    nullificationRequestsByMatch.getOrDefault(m.getId(), Collections.emptyList());
            boolean sameTeamRequested = requests.stream().anyMatch(request -> viewerTeam.equals(request.getTeam()));
            boolean oppositeTeamRequested = requests.stream().anyMatch(request -> !viewerTeam.equals(request.getTeam()));

            if (sameTeamRequested) {
                nullifyWaitingOnOpponentByMatchId.put(m.getId(), Boolean.TRUE);
            } else if (oppositeTeamRequested) {
                nullifyApprovableByMatchId.put(m.getId(), Boolean.TRUE);
            } else {
                nullifyRequestableByMatchId.put(m.getId(), Boolean.TRUE);
            }
        }

        return new MatchRowModel(confirmableMatchIds, confirmerByMatchId, casualAutoConfirmedByMatchId,
                pendingByMatchId, Map.of(), editableByMatchId, deletableByMatchId,
                nullifyRequestableByMatchId, nullifyApprovableByMatchId, nullifyWaitingOnOpponentByMatchId);
    }

    private String safeName(User u) {
        return com.w3llspring.fhpb.web.util.UserPublicName.forUserOrGuest(u);
    }

    private String formatConfirmers(List<MatchConfirmation> confirmations) {
        Set<String> names = confirmations.stream()
                .filter(c -> c.getConfirmedAt() != null)
                .sorted((c1, c2) -> {
                    int teamCompare = String.valueOf(c1.getTeam()).compareTo(String.valueOf(c2.getTeam()));
                    if (teamCompare != 0) {
                        return teamCompare;
                    }
                    if (c1.getConfirmedAt() == null && c2.getConfirmedAt() == null) {
                        return 0;
                    }
                    if (c1.getConfirmedAt() == null) {
                        return 1;
                    }
                    if (c2.getConfirmedAt() == null) {
                        return -1;
                    }
                    return c1.getConfirmedAt().compareTo(c2.getConfirmedAt());
                })
                .map(c -> safeName(c.getPlayer()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (names.isEmpty()) {
            return null;
        }
        return String.join(" and ", names);
    }

    private boolean canEditAsSeasonAdmin(Match match, User viewer, Map<Long, Boolean> seasonAdminBySeasonId) {
        if (match == null || viewer == null || viewer.getId() == null || match.isConfirmationLocked()
                || match.getState() == MatchState.NULLIFIED || ladderAccessService == null
                || match.getSeason() == null || match.getSeason().getId() == null) {
            return false;
        }

        Long seasonId = match.getSeason().getId();
        return seasonAdminBySeasonId.computeIfAbsent(seasonId, ignoredSeasonId -> {
            try {
                return ladderAccessService.isSeasonAdmin(seasonId, viewer);
            } catch (Exception ex) {
                return false;
            }
        });
    }

    private Map<Long, List<MatchNullificationRequest>> loadActiveNullificationRequests(List<Long> matchIds) {
        if (matchNullificationRequestRepository == null || matchIds == null || matchIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return matchNullificationRequestRepository.findActiveByMatchIdIn(matchIds, Instant.now()).stream()
                .filter(request -> request.getMatch() != null && request.getMatch().getId() != null)
                .collect(Collectors.groupingBy(request -> request.getMatch().getId()));
    }
}
