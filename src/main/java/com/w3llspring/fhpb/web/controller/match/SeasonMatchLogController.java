// SeasonMatchLogController.java
package com.w3llspring.fhpb.web.controller.match;

import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.matchentry.MatchEntryContextService;
import com.w3llspring.fhpb.web.service.matchworkflow.MatchStateTransitionService;
import com.w3llspring.fhpb.web.util.ReturnToSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;

@Controller
@RequestMapping("/seasons/{seasonId}/matches")
public class SeasonMatchLogController {

    private static final ZoneId LADDER_ZONE = ZoneId.of("America/New_York");
    private static final int RECENT_COMPETITION_MATCH_LIMIT = 50;

    private final MatchRepository matchRepo;
    private final UserRepository userRepo;
    private final LadderAccessService access;
    private final LadderV2Service ladderV2;
    private final com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder;
    private final MatchEntryContextService matchEntryContextService;
    private MatchStateTransitionService matchStateTransitionService;

    @Autowired
    public SeasonMatchLogController(MatchRepository matchRepo,
            UserRepository userRepo,
            LadderAccessService access,
            LadderV2Service ladderV2,
            com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder,
            MatchEntryContextService matchEntryContextService,
            MatchStateTransitionService matchStateTransitionService) {
        this.matchRepo = matchRepo;
        this.userRepo = userRepo;
        this.access = access;
        this.ladderV2 = ladderV2;
        this.matchRowModelBuilder = matchRowModelBuilder;
        this.matchEntryContextService = matchEntryContextService;
        this.matchStateTransitionService = matchStateTransitionService;
    }

    public SeasonMatchLogController(MatchRepository matchRepo,
            UserRepository userRepo,
            LadderAccessService access,
            LadderV2Service ladderV2,
            com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder,
            MatchEntryContextService matchEntryContextService) {
        this(matchRepo, userRepo, access, ladderV2, matchRowModelBuilder, matchEntryContextService, null);
    }


    @GetMapping
    public String fullLog(@PathVariable Long seasonId,
        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
        @RequestParam(value = "size", required = false, defaultValue = "20") int size,
        Model model,
        Authentication auth) {
        return renderLog(seasonId, page, size, model, auth, false, null);
    }

    @GetMapping("/recent")
    public String recentLog(@PathVariable Long seasonId,
            @RequestParam(value = "backTo", required = false) String backTo,
            Model model,
            Authentication auth) {
        return renderLog(seasonId, 0, RECENT_COMPETITION_MATCH_LIMIT, model, auth, true, backTo);
    }

    private String renderLog(Long seasonId,
            int page,
            int size,
            Model model,
            Authentication auth,
            boolean recentOnly,
            String backTo) {
        User current = getCurrentUser(auth);
        LadderSeason season = access.requireSeason(seasonId);
        if (!allowsCompetitionRecentPreview(season, recentOnly)) {
            access.requireMember(seasonId, current);
        } else if (current == null) {
            return "redirect:/login";
        }

        ZoneId displayZone = resolveUserZone(current);

        // Pull matches for the requested page via pageable (newest-first by playedAt)
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size));
        var matchPage = matchRepo.findBySeasonOrderByPlayedAtDescIncludingNullified(season, pageable);
        List<Match> matches = matchPage.getContent();

        // Use MatchRowModelBuilder to compute confirmer/pending/editable attributes
        com.w3llspring.fhpb.web.service.MatchRowModel mrModel = matchRowModelBuilder != null
                ? matchRowModelBuilder.buildFor(current, matches)
                : new com.w3llspring.fhpb.web.service.MatchRowModel(
                        java.util.Set.of(),
                        java.util.Map.of(),
                        java.util.Map.of(),
                        java.util.Map.of(),
                        java.util.Map.of(),
                        java.util.Map.of(),
                        java.util.Map.of());

        // Map to rows with user-local playedAt (defaults to Eastern)
        List<MatchRow> rows = matches.stream().map(m -> {
            java.time.ZonedDateTime playedEastern = java.time.ZonedDateTime.ofInstant(matchTimeline(m), displayZone);
            String confirmer = mrModel.getConfirmerByMatchId().get(m.getId());
            return new MatchRow(
                m.getId(),
                nameOrGuest(m.getA1(), m.isA1Guest()),
                nameOrGuest(m.getA2(), m.isA2Guest()),
                nameOrGuest(m.getB1(), m.isB1Guest()),
                nameOrGuest(m.getB2(), m.isB2Guest()),
                m.getScoreA(),
                m.getScoreB(),
                confirmer,
                playedEastern,
                m.getState(),
                m); // Pass Match object for editability checks
        }).collect(java.util.stream.Collectors.toList());

        // --- Group by LocalDate (user zone), newest day first
        java.util.Map<java.time.LocalDate, java.util.List<MatchRow>> byDay = rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> r.playedEastern.toLocalDate(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));

        // ensure newest day first (LinkedHashMap insertion order) by re-inserting with
        // a sorted key list
        java.util.List<java.time.LocalDate> daysDesc = byDay.keySet().stream()
                .sorted(java.util.Comparator.reverseOrder())
                .collect(java.util.stream.Collectors.toList());

        java.util.List<DayGroup> groups = new java.util.ArrayList<>();
        for (java.time.LocalDate d : daysDesc) {
            java.util.List<MatchRow> dayRows = byDay.get(d);
            // already sorted desc by played time, but keep it explicit in case of
            // refactors
            dayRows.sort(java.util.Comparator.comparing((MatchRow r) -> r.playedEastern).reversed());
            groups.add(new DayGroup(d, dayRows));
        }

        boolean isAdmin = access.isSeasonAdmin(seasonId, current);
        String backHref = recentOnly
                ? buildRecentBackHref(backTo)
                : buildStandingsBackHref(season);

        // add the ladder to match your header rhythm
        model.addAttribute("ladder", season.getLadderConfig());
        model.addAttribute("backHref", backHref);
        model.addAttribute("backLabel", recentOnly ? recentBackLabel(backHref) : "Back");
        model.addAttribute("matchLogSubtitle", recentOnly
                ? "Most recent " + RECENT_COMPETITION_MATCH_LIMIT + " matches from this season."
                : "Full season match log.");
        model.addAttribute("recentOnly", recentOnly);
        model.addAttribute("showExport", !recentOnly);
        model.addAttribute("recentMatchLimit", RECENT_COMPETITION_MATCH_LIMIT);
        model.addAttribute("season", season);
        model.addAttribute("seasonName", season.getName());
        model.addAttribute("seasonDateRange", seasonDateRange(season));
        model.addAttribute("groups", groups);
        model.addAttribute("pageNumber", matchPage.getNumber());
        model.addAttribute("totalPages", matchPage.getTotalPages());
        model.addAttribute("pageSize", matchPage.getSize());
        model.addAttribute("totalElements", matchPage.getTotalElements());
        model.addAttribute("isAdmin", isAdmin);
        // Note: currentUser available globally as 'loggedInUser' via GlobalModelAttributes
        model.addAttribute("ladderId", season.getLadderConfig().getId());
        model.addAttribute("seasonId", seasonId);

        // Add pending/confirmable/editable maps computed by the builder
        if (mrModel != null) {
            model.addAttribute("casualAutoConfirmedByMatchId", mrModel.getCasualAutoConfirmedByMatchId());
            model.addAttribute("pendingByMatchId", mrModel.getPendingByMatchId());
            model.addAttribute("confirmableMatchIds", mrModel.getConfirmableMatchIds());
            model.addAttribute("editableByMatchId", mrModel.getEditableByMatchId());
            model.addAttribute("deletableByMatchId", mrModel.getDeletableByMatchId());
            model.addAttribute("nullifyRequestableByMatchId", mrModel.getNullifyRequestableByMatchId());
            model.addAttribute("nullifyApprovableByMatchId", mrModel.getNullifyApprovableByMatchId());
            model.addAttribute("nullifyWaitingOnOpponentByMatchId", mrModel.getNullifyWaitingOnOpponentByMatchId());
        }

        // Build courtNameByUser map using ladder-scoped aliases where possible
        java.util.Map<Long, String> courtNameByUser = matchEntryContextService != null
                ? matchEntryContextService.buildCourtNameByMatches(matches, season.getLadderConfig().getId())
                : java.util.Map.of();
        model.addAttribute("courtNameByUser", courtNameByUser);

        return "auth/seasonMatchLog";
    }

    private String buildStandingsBackHref(LadderSeason season) {
        if (season == null || season.getId() == null || season.getLadderConfig() == null
                || season.getLadderConfig().getId() == null) {
            return "/standings";
        }
        return "/standings?ladderId=" + season.getLadderConfig().getId() + "&seasonId=" + season.getId();
    }

    private String buildRecentBackHref(String backTo) {
        String sanitizedBackTo = ReturnToSanitizer.sanitize(backTo);
        return sanitizedBackTo != null ? sanitizedBackTo : "/competition";
    }

    private String recentBackLabel(String backHref) {
        return backHref != null && backHref.startsWith("/groups/")
                ? "Back to Session"
                : "Back to Competition";
    }

    private boolean allowsCompetitionRecentPreview(LadderSeason season, boolean recentOnly) {
        return recentOnly
                && season != null
                && season.getLadderConfig() != null
                && season.getLadderConfig().isCompetitionType();
    }

    private ZoneId resolveUserZone(User user) {
        if (user == null) return LADDER_ZONE;
        String tz = user.getTimeZone();
        if (tz == null || tz.isBlank()) return LADDER_ZONE;
        try {
            return ZoneId.of(tz.trim());
        } catch (Exception ignored) {
            return LADDER_ZONE;
        }
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportCsv(@PathVariable Long seasonId,
            Authentication auth) {

        User current = getCurrentUser(auth);
        access.requireMember(seasonId, current);

        LadderSeason season = access.requireSeason(seasonId);

        List<Match> matches = matchRepo.findBySeasonOrderByPlayedAtDescIncludingNullified(season);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

        StringBuilder csv = new StringBuilder();
        csv.append("match_id,played_at_eastern,a1,a1_code,a2,a2_code,b1,b1_code,b2,b2_code,score_a,score_b,state,cosigner,cosigner_code\n");

        for (Match match : matches) {
            ZonedDateTime playedEastern = ZonedDateTime.ofInstant(matchTimeline(match), LADDER_ZONE);

            csv.append(match.getId()).append(',')
                    .append(escapeCsv(playedEastern.format(formatter))).append(',')
                    .append(escapeCsv(nameOrGuest(match.getA1(), match.isA1Guest()))).append(',')
                    .append(escapeCsv(publicCodeOrBlank(match.getA1(), match.isA1Guest()))).append(',')
                    .append(escapeCsv(nameOrGuest(match.getA2(), match.isA2Guest()))).append(',')
                    .append(escapeCsv(publicCodeOrBlank(match.getA2(), match.isA2Guest()))).append(',')
                    .append(escapeCsv(nameOrGuest(match.getB1(), match.isB1Guest()))).append(',')
                    .append(escapeCsv(publicCodeOrBlank(match.getB1(), match.isB1Guest()))).append(',')
                    .append(escapeCsv(nameOrGuest(match.getB2(), match.isB2Guest()))).append(',')
                    .append(escapeCsv(publicCodeOrBlank(match.getB2(), match.isB2Guest()))).append(',')
                    .append(match.getScoreA()).append(',')
                    .append(match.getScoreB()).append(',')
                    .append(escapeCsv(match.getState().name())).append(',')
                    .append(escapeCsv(match.getCosignedBy() == null ? "—" : safeName(match.getCosignedBy())))
                    .append(',')
                    .append(escapeCsv(publicCodeOrBlank(match.getCosignedBy(), false)))
                    .append('\n');
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=season-" + seasonId + "-match-log.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csv.toString());
    }

    // Add a tiny DayGroup holder under the MatchRow inner class
    public static class DayGroup {
        public final java.time.LocalDate day;
        public final java.util.List<MatchRow> rows;

        public DayGroup(java.time.LocalDate day, java.util.List<MatchRow> rows) {
            this.day = day;
            this.rows = rows;
        }
    }

    @PostMapping("/{matchId}/nullify")
    public String nullify(@PathVariable Long seasonId,
            @PathVariable Long matchId,
            Authentication auth) {
        User current = getCurrentUser(auth);
        access.requireAdmin(seasonId, current);

        Match m = matchRepo.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        // only allow nullify if match is within this season’s date range (safety)
        LadderSeason season = access.requireSeason(seasonId);
        if (!season.equals(m.getSeason())) {
            throw new IllegalArgumentException("Match is not in this season");
        }

        try {
            if (matchStateTransitionService != null) {
                matchStateTransitionService.nullifyMatch(matchId, current, true);
            } else {
                m.setState(MatchState.NULLIFIED);
                matchRepo.save(m);
                ladderV2.onMatchNullified(m);
            }
        } catch (OptimisticLockingFailureException ex) {
            return "redirect:/seasons/" + seasonId + "/matches?toast=matchConflict";
        } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
        }

        return "redirect:/seasons/" + seasonId + "/matches";
    }

    @PostMapping("/{matchId}/reopen")
    public String reopen(@PathVariable Long seasonId,
            @PathVariable Long matchId,
            Authentication auth) {
        User current = getCurrentUser(auth);
        access.requireAdmin(seasonId, current);

        Match m = matchRepo.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        LadderSeason season = access.requireSeason(seasonId);
        if (!season.equals(m.getSeason())) {
            throw new IllegalArgumentException("Match is not in this season");
        }

        try {
            if (matchStateTransitionService != null) {
                matchStateTransitionService.reopenDisputedMatch(matchId, current, true);
            } else {
                m.setState(MatchState.PROVISIONAL);
                m.setDisputedBy(null);
                m.setDisputedAt(null);
                m.setDisputeNote(null);
                matchRepo.save(m);
            }
        } catch (OptimisticLockingFailureException ex) {
            return "redirect:/seasons/" + seasonId + "/matches?toast=matchConflict";
        } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
        }

        return "redirect:/seasons/" + seasonId + "/matches";
    }

    // --- helpers ---

    private User getCurrentUser(Authentication auth) {
        return AuthenticatedUserSupport.currentUser(auth);
    }

    private String safeName(User u) {
        return com.w3llspring.fhpb.web.util.UserPublicName.forUserOrGuest(u);
    }

    private String nameOrGuest(User u, boolean guest) {
        return guest ? "Guest" : safeName(u);
    }

    private String publicCodeOrBlank(User user, boolean guest) {
        if (guest || user == null || user.getPublicCode() == null || user.getPublicCode().isBlank()) {
            return "";
        }
        return user.getPublicCode();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String safe = neutralizeSpreadsheetFormula(value);
        boolean needsQuotes = safe.contains(",") || safe.contains("\"") || safe.contains("\n");
        String escaped = safe.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    private String neutralizeSpreadsheetFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + value;
        }
        return value;
    }

    private java.time.Instant matchTimeline(Match match) {
        if (match == null) {
            return java.time.Instant.now();
        }
        if (match.getPlayedAt() != null) {
            return match.getPlayedAt();
        }
        if (match.getCreatedAt() != null) {
            return match.getCreatedAt();
        }
        return java.time.Instant.now();
    }

    private String seasonDateRange(LadderSeason season) {
        if (season == null || season.getStartDate() == null) {
            return "";
        }
        LocalDate start = season.getStartDate();
        LocalDate end = season.getEndDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy");
        boolean placeholder = end != null && end.isAfter(start.plusYears(80));
        String startText = start.format(formatter);
        String endText;
        if (placeholder) {
            endText = "Present";
        } else if (end != null) {
            endText = end.format(formatter);
        } else {
            endText = "Present";
        }
        return startText + " - " + endText;
    }

    public static class MatchRow {
        public final Long id;
        public final String a1;
        public final String a2;
        public final String b1;
        public final String b2;
        public final int scoreA;
        public final int scoreB;
        public final String confirmer;
        public final ZonedDateTime playedEastern;
        public final MatchState state;
        public final Match match; // For editability checks (Phase C: User Correction Feature)

        public MatchRow(Long id, String a1, String a2, String b1, String b2,
                int scoreA, int scoreB, String confirmer,
                ZonedDateTime playedEastern, MatchState state, Match match) {
            this.id = id;
            this.a1 = a1;
            this.a2 = a2;
            this.b1 = b1;
            this.b2 = b2;
            this.scoreA = scoreA;
            this.scoreB = scoreB;
            this.confirmer = confirmer;
            this.playedEastern = playedEastern;
            this.state = state;
            this.match = match;
        }

        public boolean isNullifiable() {
            return state != MatchState.NULLIFIED;
        }
    }

    
}
