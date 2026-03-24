package com.w3llspring.fhpb.web.controller.match;

import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.service.matchentry.MatchEntryContextService;

import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.util.PaginationWindowSupport;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/users/{memberCode}/matches")
public class UserMatchLogController {

    private static final ZoneId LADDER_ZONE = ZoneId.of("America/New_York");
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 25;

    private final MatchRepository matchRepo;
    private final UserRepository userRepo;
    private final com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder;
    private final MatchEntryContextService matchEntryContextService;

    public UserMatchLogController(MatchRepository matchRepo,
                                  UserRepository userRepo,
                                  com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder,
                                  MatchEntryContextService matchEntryContextService) {
        this.matchRepo = matchRepo;
        this.userRepo = userRepo;
        this.matchRowModelBuilder = matchRowModelBuilder;
        this.matchEntryContextService = matchEntryContextService;
    }

    @GetMapping
    public String recentMatches(@PathVariable String memberCode,
                                @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                                @RequestParam(value = "size", required = false, defaultValue = "10") int size,
                                Model model, org.springframework.security.core.Authentication auth) {
        User user = userRepo.findByPublicCode(memberCode).orElse(null);
        if (user == null) {
            model.addAttribute("recentMatches", List.of());
            model.addAttribute("memberCode", memberCode);
            return "fragments/userRecentMatches";
        }
        User current = getCurrentUser(auth);
        requireProfileOwner(current, user);

        RecentMatchesPage recentMatchesPage = loadRecentMatchesPage(user, page, size);
        List<Match> recent = recentMatchesPage.matches();
        model.addAttribute("canEditDisplayName", true);
        model.addAttribute("pageNumber", recentMatchesPage.pageNumber());
        model.addAttribute("totalPages", recentMatchesPage.totalPages());
        model.addAttribute("pageSize", recentMatchesPage.pageSize());
        model.addAttribute("totalElements", recentMatchesPage.totalElements());
        model.addAttribute("recentMatchesPage", recentMatchesPage.pageNumber());
        model.addAttribute("recentMatchesTotalPages", recentMatchesPage.totalPages());
        model.addAttribute("recentMatchesPageSize", recentMatchesPage.pageSize());
        model.addAttribute("recentMatchesTotalElements", recentMatchesPage.totalElements());
        model.addAttribute("recentMatchesStandalonePage", true);
        applyRecentMatchesPaginationModel(model, recentMatchesPage.pageNumber(), recentMatchesPage.totalPages());
        model.addAttribute("recentMatches", recent);
        model.addAttribute("memberCode", memberCode);

        // Compute confirmer/pending/editable attributes via MatchRowModelBuilder.
        var mrModel = matchRowModelBuilder.buildFor(current, recent);
        model.addAttribute("confirmerByMatchId", mrModel.getConfirmerByMatchId());
        model.addAttribute("casualAutoConfirmedByMatchId", mrModel.getCasualAutoConfirmedByMatchId());
        model.addAttribute("editableByMatchId", mrModel.getEditableByMatchId());
        model.addAttribute("deletableByMatchId", mrModel.getDeletableByMatchId());
        model.addAttribute("nullifyRequestableByMatchId", mrModel.getNullifyRequestableByMatchId());
        model.addAttribute("nullifyApprovableByMatchId", mrModel.getNullifyApprovableByMatchId());
        model.addAttribute("nullifyWaitingOnOpponentByMatchId", mrModel.getNullifyWaitingOnOpponentByMatchId());
        // Preserve previous behavior: only expose pending map to profile owner
        if (current != null && current.getId().equals(user.getId())) {
            model.addAttribute("pendingByMatchId", mrModel.getPendingByMatchId());
            model.addAttribute("confirmableMatchIds", mrModel.getConfirmableMatchIds());
        }

        // Build courtNameByUser map (no ladder context here; use global aliases only)
        java.util.Map<Long, String> courtNameByUser = matchEntryContextService.buildCourtNameByMatches(recent, null);
        model.addAttribute("courtNameByUser", courtNameByUser);

        return "fragments/userRecentMatches";
    }

    private RecentMatchesPage loadRecentMatchesPage(User user, int requestedPage, int requestedSize) {
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedSize <= 0 ? DEFAULT_PAGE_SIZE : requestedSize));
        int safeRequestedPage = Math.max(0, requestedPage);
        long totalElements = matchRepo.countParticipantMatchesIncludingNullified(user);
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / pageSize);
        int pageNumber = Math.max(0, Math.min(safeRequestedPage, totalPages - 1));

        List<Match> matches = List.of();
        if (totalElements > 0) {
            List<Long> matchIds = matchRepo.findParticipantMatchIdsOrderByPlayedAtDescIncludingNullified(
                    user,
                    PageRequest.of(pageNumber, pageSize));
            matches = matchIds.isEmpty()
                    ? List.of()
                    : matchRepo.findAllByIdInWithUsers(matchIds).stream()
                            .sorted(java.util.Comparator.comparing(this::matchTimeline).reversed())
                            .toList();
        }
        return new RecentMatchesPage(matches, pageNumber, pageSize, totalPages, totalElements);
    }

    private void applyRecentMatchesPaginationModel(Model model, int currentPage, int totalPages) {
        PaginationWindowSupport.PaginationWindow pagination = PaginationWindowSupport.buildWindow(currentPage, totalPages);
        model.addAttribute("recentMatchesPageNumbers", pagination.pageNumbers());
        model.addAttribute("recentMatchesShowFirstPage", pagination.showFirstPage());
        model.addAttribute("recentMatchesShowLastPage", pagination.showLastPage());
        model.addAttribute("recentMatchesShowLeadingEllipsis", pagination.showLeadingEllipsis());
        model.addAttribute("recentMatchesShowTrailingEllipsis", pagination.showTrailingEllipsis());
        model.addAttribute("recentMatchesJumpBackPage", pagination.jumpBackPage());
        model.addAttribute("recentMatchesJumpForwardPage", pagination.jumpForwardPage());
    }

    private void requireProfileOwner(User currentUser, User targetUser) {
        if (currentUser == null || currentUser.getId() == null || targetUser == null || targetUser.getId() == null) {
            throw new SecurityException("User match log unavailable.");
        }
        if (!currentUser.getId().equals(targetUser.getId())) {
            throw new SecurityException("User match log unavailable.");
        }
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportCsv(@PathVariable String memberCode, org.springframework.security.core.Authentication auth) {
        User user = userRepo.findByPublicCode(memberCode).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        // Ensure only the profile owner can export their matches
        User current = getCurrentUser(auth);
        if (current == null || current.getId() == null || !current.getId().equals(user.getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        List<Match> matches = matchRepo.findByParticipantOrderByPlayedAtDescIncludingNullified(user);

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
                    .append(escapeCsv(match.getState() == null ? "" : match.getState().name())).append(',')
                    .append(escapeCsv(match.getCosignedBy() == null ? "—" : safeName(match.getCosignedBy())))
                    .append(',')
                    .append(escapeCsv(publicCodeOrBlank(match.getCosignedBy(), false)))
                    .append('\n');
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=user-" + memberCode + "-match-log.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csv.toString());
    }

    private User getCurrentUser(org.springframework.security.core.Authentication auth) {
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

    private record RecentMatchesPage(
            List<Match> matches,
            int pageNumber,
            int pageSize,
            int totalPages,
            long totalElements) {
    }
}
