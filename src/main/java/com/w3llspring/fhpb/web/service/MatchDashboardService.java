package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchNullificationRequestRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.LadderMatchLinkRepository;
import com.w3llspring.fhpb.web.model.LadderMatchLink;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchNullificationRequest;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MatchDashboardService {

    private final LadderMatchLinkRepository linkRepo;
    private final MatchRepository matchRepo;
    private final MatchConfirmationRepository matchConfirmationRepository;
    private final MatchNullificationRequestRepository matchNullificationRequestRepository;
    private final MatchConfirmationService confirmationService;
    private final MatchRowModelBuilder matchRowModelBuilder;

    @Autowired
    public MatchDashboardService(LadderMatchLinkRepository linkRepo,
                                 MatchRepository matchRepo,
                                 MatchConfirmationRepository matchConfirmationRepository,
                                 MatchNullificationRequestRepository matchNullificationRequestRepository,
                                 MatchConfirmationService confirmationService,
                                 MatchRowModelBuilder matchRowModelBuilder) {
        this.linkRepo = linkRepo;
        this.matchRepo = matchRepo;
        this.matchConfirmationRepository = matchConfirmationRepository;
        this.matchNullificationRequestRepository = matchNullificationRequestRepository;
        this.confirmationService = confirmationService;
        this.matchRowModelBuilder = matchRowModelBuilder;
    }

    public MatchDashboardService(LadderMatchLinkRepository linkRepo,
                                 MatchRepository matchRepo,
                                 MatchConfirmationRepository matchConfirmationRepository,
                                 MatchConfirmationService confirmationService,
                                 MatchRowModelBuilder matchRowModelBuilder) {
        this(linkRepo, matchRepo, matchConfirmationRepository, null, confirmationService, matchRowModelBuilder);
    }

    public DashboardModel buildPendingForUser(User viewer) {
        return buildPendingForUserInSeason(viewer, null);
    }

    public int countInboxForUser(User viewer) {
        return countInboxForUserInSeason(viewer, null);
    }

    public int countInboxForUserInSeason(User viewer, LadderSeason season) {
        return inboxCount(buildPendingForUserInSeason(viewer, season).matchRowModel());
    }

    public DashboardModel buildPendingForUserInSeason(User viewer, LadderSeason season) {
        if (viewer == null || viewer.getId() == null) {
            return empty();
        }

        List<MatchConfirmation> pending = confirmationService.pendingForUser(viewer.getId());
        Set<Long> confirmableMatchIds = pending.stream()
                .map(MatchConfirmation::getMatch)
                .filter(Objects::nonNull)
                .filter(match -> season == null || sameSeason(match, season))
                .map(Match::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> waitingOnOpponentMatchIds = findWaitingOnOpponentMatchIds(viewer, season, confirmableMatchIds);
        Set<Long> nullifyRelevantMatchIds = findConfirmedNullifyRelevantMatchIds(viewer, season);
        if (confirmableMatchIds.isEmpty()
                && waitingOnOpponentMatchIds.isEmpty()
                && nullifyRelevantMatchIds.isEmpty()) {
            return empty();
        }

        List<LadderMatchLink> links = combineOrderedLinks(
                loadLinks(confirmableMatchIds, season),
                loadLinks(waitingOnOpponentMatchIds, season),
                loadLinks(nullifyRelevantMatchIds, season));
        List<Match> matches = links.stream()
                .map(LadderMatchLink::getMatch)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        MatchRowModel baseModel = matchRowModelBuilder.buildFor(viewer, matches, confirmableMatchIds);
        Map<Long, Boolean> waitingOnOpponentByMatchId = new HashMap<>();
        for (Long matchId : waitingOnOpponentMatchIds) {
            waitingOnOpponentByMatchId.put(matchId, Boolean.TRUE);
        }

        MatchRowModel matchRowModel = new MatchRowModel(
                baseModel.getConfirmableMatchIds(),
                baseModel.getConfirmerByMatchId(),
                baseModel.getCasualAutoConfirmedByMatchId(),
                baseModel.getPendingByMatchId(),
                waitingOnOpponentByMatchId,
                baseModel.getEditableByMatchId(),
                baseModel.getDeletableByMatchId(),
                baseModel.getNullifyRequestableByMatchId(),
                baseModel.getNullifyApprovableByMatchId(),
                baseModel.getNullifyWaitingOnOpponentByMatchId());
        return new DashboardModel(links, matchRowModel);
    }

    private Set<Long> findWaitingOnOpponentMatchIds(User viewer, LadderSeason season, Set<Long> confirmableMatchIds) {
        if (viewer == null || viewer.getId() == null || matchRepo == null || matchConfirmationRepository == null) {
            return Set.of();
        }

        List<Match> participantMatches = matchRepo.findByParticipantWithUsers(viewer);
        if (participantMatches == null || participantMatches.isEmpty()) {
            return Set.of();
        }

        List<Long> participantMatchIds = participantMatches.stream()
                .map(Match::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<MatchConfirmation>> confirmationsByMatch = participantMatchIds.isEmpty()
                ? Map.of()
                : matchConfirmationRepository.findByMatchIdIn(participantMatchIds).stream()
                        .filter(mc -> mc.getMatch() != null && mc.getMatch().getId() != null)
                        .collect(Collectors.groupingBy(mc -> mc.getMatch().getId()));

        Set<Long> waitingMatchIds = new LinkedHashSet<>();
        for (Match match : participantMatches) {
            if (match == null || match.getId() == null) {
                continue;
            }
            if (confirmableMatchIds.contains(match.getId())
                    || (season != null && !sameSeason(match, season))
                    || match.isConfirmationLocked()
                    || match.getState() == MatchState.FLAGGED
                    || match.getState() == MatchState.CONFIRMED
                    || match.getState() == MatchState.NULLIFIED) {
                continue;
            }

            String viewerTeam = teamForUser(match, viewer.getId());
            if (viewerTeam == null) {
                continue;
            }
            String opponentTeam = "A".equals(viewerTeam) ? "B" : "A";
            if (!teamHasParticipants(match, opponentTeam)) {
                continue;
            }

            List<MatchConfirmation> confirmations =
                    confirmationsByMatch.getOrDefault(match.getId(), List.of());
            if (teamHasConfirmed(match, confirmations, viewerTeam)
                    && !teamHasConfirmed(match, confirmations, opponentTeam)) {
                waitingMatchIds.add(match.getId());
            }
        }
        return waitingMatchIds;
    }

    private boolean sameSeason(Match match, LadderSeason season) {
        return match.getSeason() != null
                && match.getSeason().getId() != null
                && match.getSeason().getId().equals(season.getId());
    }

    private List<LadderMatchLink> loadLinks(Set<Long> matchIds, LadderSeason season) {
        if (matchIds == null || matchIds.isEmpty() || linkRepo == null) {
            return List.of();
        }
        List<LadderMatchLink> links = season != null
                ? linkRepo.findBySeasonAndMatchIds(season, new ArrayList<>(matchIds))
                : linkRepo.findByMatchIds(new ArrayList<>(matchIds));
        return dedupeAndSort(links);
    }

    private List<LadderMatchLink> combineOrderedLinks(List<LadderMatchLink> first, List<LadderMatchLink> second) {
        Set<Long> seenMatchIds = new LinkedHashSet<>();
        List<LadderMatchLink> combined = new ArrayList<>();
        appendUniqueLinks(combined, seenMatchIds, first);
        appendUniqueLinks(combined, seenMatchIds, second);
        return combined;
    }

    private List<LadderMatchLink> combineOrderedLinks(List<LadderMatchLink> first,
                                                      List<LadderMatchLink> second,
                                                      List<LadderMatchLink> third) {
        Set<Long> seenMatchIds = new LinkedHashSet<>();
        List<LadderMatchLink> combined = new ArrayList<>();
        appendUniqueLinks(combined, seenMatchIds, first);
        appendUniqueLinks(combined, seenMatchIds, second);
        appendUniqueLinks(combined, seenMatchIds, third);
        return combined;
    }

    private void appendUniqueLinks(List<LadderMatchLink> target, Set<Long> seenMatchIds, List<LadderMatchLink> links) {
        if (links == null || links.isEmpty()) {
            return;
        }
        for (LadderMatchLink link : links) {
            Long matchId = link != null && link.getMatch() != null ? link.getMatch().getId() : null;
            if (matchId == null || seenMatchIds.add(matchId)) {
                target.add(link);
            }
        }
    }

    private List<LadderMatchLink> dedupeAndSort(List<LadderMatchLink> links) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }

        Set<Long> seenMatchIds = new LinkedHashSet<>();
        List<LadderMatchLink> deduped = new ArrayList<>();
        for (LadderMatchLink link : links) {
            Long matchId = link != null && link.getMatch() != null ? link.getMatch().getId() : null;
            if (matchId == null || seenMatchIds.add(matchId)) {
                deduped.add(link);
            }
        }
        deduped.sort(Comparator.comparing(this::matchTimeline).reversed());
        return deduped;
    }

    private Instant matchTimeline(LadderMatchLink link) {
        if (link == null || link.getMatch() == null) {
            return Instant.EPOCH;
        }
        Match match = link.getMatch();
        if (match.getPlayedAt() != null) {
            return match.getPlayedAt();
        }
        if (match.getCreatedAt() != null) {
            return match.getCreatedAt();
        }
        return Instant.EPOCH;
    }

    private DashboardModel empty() {
        return new DashboardModel(List.of(),
                new MatchRowModel(Set.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                        java.util.Map.of(), java.util.Map.of(), java.util.Map.of()));
    }

    private int inboxCount(MatchRowModel rowModel) {
        if (rowModel == null) {
            return 0;
        }
        Set<Long> inboxMatchIds = new LinkedHashSet<>();
        if (rowModel.getConfirmableMatchIds() != null) {
            inboxMatchIds.addAll(rowModel.getConfirmableMatchIds());
        }
        if (rowModel.getNullifyApprovableByMatchId() != null) {
            rowModel.getNullifyApprovableByMatchId().forEach((matchId, approvable) -> {
                if (Boolean.TRUE.equals(approvable) && matchId != null) {
                    inboxMatchIds.add(matchId);
                }
            });
        }
        return inboxMatchIds.size();
    }

    private Set<Long> findConfirmedNullifyRelevantMatchIds(User viewer, LadderSeason season) {
        if (viewer == null
                || viewer.getId() == null
                || matchRepo == null
                || matchNullificationRequestRepository == null) {
            return Set.of();
        }

        List<Match> participantMatches = matchRepo.findByParticipantWithUsers(viewer);
        if (participantMatches == null || participantMatches.isEmpty()) {
            return Set.of();
        }

        List<Long> matchIds = participantMatches.stream()
                .map(Match::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<MatchNullificationRequest>> requestsByMatch = matchIds.isEmpty()
                ? Map.of()
                : matchNullificationRequestRepository.findActiveByMatchIdIn(matchIds, Instant.now()).stream()
                        .filter(request -> request.getMatch() != null && request.getMatch().getId() != null)
                        .collect(Collectors.groupingBy(request -> request.getMatch().getId()));

        Set<Long> relevant = new LinkedHashSet<>();
        for (Match match : participantMatches) {
            if (match == null
                    || match.getId() == null
                    || match.getState() != MatchState.CONFIRMED
                    || (season != null && !sameSeason(match, season))) {
                continue;
            }

            String viewerTeam = teamForUser(match, viewer.getId());
            if (viewerTeam == null) {
                continue;
            }

            List<MatchNullificationRequest> requests =
                    requestsByMatch.getOrDefault(match.getId(), List.of());
            if (requests.stream().anyMatch(request -> viewerTeam.equals(request.getTeam()))
                    || requests.stream().anyMatch(request -> !viewerTeam.equals(request.getTeam()))) {
                relevant.add(match.getId());
            }
        }
        return relevant;
    }

    private String teamForUser(Match match, Long userId) {
        if (match == null || userId == null) {
            return null;
        }
        if (sameUser(match.getA1(), userId) || sameUser(match.getA2(), userId)) {
            return "A";
        }
        if (sameUser(match.getB1(), userId) || sameUser(match.getB2(), userId)) {
            return "B";
        }
        return null;
    }

    private boolean teamHasParticipants(Match match, String team) {
        return !participantIdsForTeam(match, team).isEmpty();
    }

    private boolean teamHasConfirmed(Match match, List<MatchConfirmation> confirmations, String team) {
        if (confirmations == null || confirmations.isEmpty()) {
            return false;
        }
        Set<Long> participantIds = participantIdsForTeam(match, team);
        return confirmations.stream()
                .filter(mc -> team.equals(mc.getTeam()))
                .filter(mc -> mc.getConfirmedAt() != null)
                .filter(mc -> mc.getPlayer() != null && mc.getPlayer().getId() != null)
                .anyMatch(mc -> participantIds.contains(mc.getPlayer().getId()));
    }

    private Set<Long> participantIdsForTeam(Match match, String team) {
        Set<Long> participantIds = new LinkedHashSet<>();
        if ("A".equals(team)) {
            addParticipantId(participantIds, match != null ? match.getA1() : null);
            addParticipantId(participantIds, match != null ? match.getA2() : null);
        } else if ("B".equals(team)) {
            addParticipantId(participantIds, match != null ? match.getB1() : null);
            addParticipantId(participantIds, match != null ? match.getB2() : null);
        }
        return participantIds;
    }

    private void addParticipantId(Set<Long> participantIds, User user) {
        if (participantIds == null || user == null || user.getId() == null) {
            return;
        }
        participantIds.add(user.getId());
    }

    private boolean sameUser(User candidate, Long userId) {
        return candidate != null && candidate.getId() != null && candidate.getId().equals(userId);
    }

    public record DashboardModel(List<LadderMatchLink> links, MatchRowModel matchRowModel) {
    }
}
