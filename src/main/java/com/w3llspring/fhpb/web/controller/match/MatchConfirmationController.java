package com.w3llspring.fhpb.web.controller.match;

import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.MatchRowModelBuilder;
import com.w3llspring.fhpb.web.service.MatchRowModel;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.RecentDuplicateMatchWarningService;
import com.w3llspring.fhpb.web.service.ConfirmedMatchNullificationService;
import com.w3llspring.fhpb.web.service.matchworkflow.MatchConfirmationCommandService;
import com.w3llspring.fhpb.web.service.matchworkflow.MatchStateTransitionService;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;

@Controller
@RequestMapping("/matches")
public class MatchConfirmationController {

    private static final Logger log = LoggerFactory.getLogger(MatchConfirmationController.class);

    private final MatchConfirmationService confirmationService;
    private final MatchRepository matchRepo;
    private final MatchRowModelBuilder matchRowModelBuilder;
    private final LadderV2Service ladderV2Service;
    private final LadderAccessService ladderAccessService;
    private MatchStateTransitionService matchStateTransitionService;
    private RecentDuplicateMatchWarningService recentDuplicateMatchWarningService;
    private TrophyAwardService trophyAwardService;
    private ConfirmedMatchNullificationService confirmedMatchNullificationService;

    @Autowired
    public MatchConfirmationController(MatchConfirmationService confirmationService, MatchRepository matchRepo,
                                       MatchRowModelBuilder matchRowModelBuilder,
                                       LadderV2Service ladderV2Service,
                                       LadderAccessService ladderAccessService,
                                       MatchStateTransitionService matchStateTransitionService,
                                       RecentDuplicateMatchWarningService recentDuplicateMatchWarningService,
                                       TrophyAwardService trophyAwardService,
                                       ConfirmedMatchNullificationService confirmedMatchNullificationService) {
        this.confirmationService = confirmationService;
        this.matchRepo = matchRepo;
        this.matchRowModelBuilder = matchRowModelBuilder;
        this.ladderV2Service = ladderV2Service;
        this.ladderAccessService = ladderAccessService;
        this.matchStateTransitionService = matchStateTransitionService;
        this.recentDuplicateMatchWarningService = recentDuplicateMatchWarningService;
        this.trophyAwardService = trophyAwardService;
        this.confirmedMatchNullificationService = confirmedMatchNullificationService;
    }

    public MatchConfirmationController(MatchConfirmationService confirmationService, MatchRepository matchRepo,
                                       MatchRowModelBuilder matchRowModelBuilder,
                                       LadderV2Service ladderV2Service,
                                       LadderAccessService ladderAccessService) {
        this(confirmationService, matchRepo, matchRowModelBuilder, ladderV2Service, ladderAccessService, null, null,
                null, null);
    }

    /**
     * Backwards-compatible constructor used by tests that instantiate the controller
     * with only the confirmation service and match repository. Provides a minimal
     * MatchRowModelBuilder and stub LadderAccessService so tests don't need to
     * instantiate the full builder and its dependencies.
     */
    public MatchConfirmationController(MatchConfirmationService confirmationService, MatchRepository matchRepo) {
        this(confirmationService, matchRepo, new MatchRowModelBuilder(null, null, null) {
            @Override
            public com.w3llspring.fhpb.web.service.MatchRowModel buildFor(com.w3llspring.fhpb.web.model.User viewer, java.util.List<com.w3llspring.fhpb.web.model.Match> matches) {
                return new com.w3llspring.fhpb.web.service.MatchRowModel(
                        java.util.Collections.emptySet(),
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap());
            }
        }, null, new LadderAccessService(null, null) {
            @Override
            public boolean isSeasonAdmin(Long seasonId, User user) {
                return false;  // Tests don't use this path
            }
        });
    }

    @org.springframework.web.bind.annotation.GetMapping("/{id}/fragment")
    public String matchFragment(@PathVariable("id") Long id,
                                org.springframework.ui.Model model,
                                @org.springframework.security.core.annotation.AuthenticationPrincipal CustomUserDetails principal) {
        com.w3llspring.fhpb.web.model.User user = resolveUser(principal);
        // OPTIMIZED: Use findByIdWithUsers to eagerly fetch all user entities in one query
        com.w3llspring.fhpb.web.model.Match m = matchRepo.findByIdWithUsers(id).orElse(null);
        if (m == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        if (!canViewMatchFragment(user, m)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // Build the same small model used by the fragments (confirmable/editable/confirmer)
        com.w3llspring.fhpb.web.service.MatchRowModel mrModel = matchRowModelBuilder.buildFor(user, java.util.List.of(m));

        boolean confirmable = mrModel.getConfirmableMatchIds() != null && mrModel.getConfirmableMatchIds().contains(id);
        boolean editable = mrModel.getEditableByMatchId() != null && mrModel.getEditableByMatchId().getOrDefault(id, Boolean.FALSE);
        String confirmer = mrModel.getConfirmerByMatchId() == null ? null : mrModel.getConfirmerByMatchId().get(id);
        boolean casualAutoConfirmed = mrModel.getCasualAutoConfirmedByMatchId() != null
                && Boolean.TRUE.equals(mrModel.getCasualAutoConfirmedByMatchId().get(id));

        model.addAttribute("match", m);
        model.addAttribute("confirmable", confirmable);
        model.addAttribute("editable", editable);
        model.addAttribute("showTime", true);
        model.addAttribute("title", "");
        model.addAttribute("showScore", true);
        model.addAttribute("showState", true);
        model.addAttribute("showAdminControls", false);
        model.addAttribute("a1Name", null);
        model.addAttribute("a2Name", null);
        model.addAttribute("b1Name", null);
        model.addAttribute("b2Name", null);
        model.addAttribute("confirmer", confirmer);
        model.addAttribute("casualAutoConfirmedByMatchId", java.util.Map.of(id, casualAutoConfirmed));
        model.addAttribute("nullifyRequestableByMatchId", mrModel.getNullifyRequestableByMatchId());
        model.addAttribute("nullifyApprovableByMatchId", mrModel.getNullifyApprovableByMatchId());
        model.addAttribute("nullifyWaitingOnOpponentByMatchId", mrModel.getNullifyWaitingOnOpponentByMatchId());
        model.addAttribute("removeOnConfirm", false);
        // Preserve season/ladder ids if available so edit links remain correct
        model.addAttribute("seasonId", m.getSeason() != null ? m.getSeason().getId() : null);
        model.addAttribute("ladderId", (m.getSeason() != null && m.getSeason().getLadderConfig() != null) ? m.getSeason().getLadderConfig().getId() : null);

        // Render the single match fragment
        return "fragments/matchRow :: matchRow";
    }

    public ResponseEntity<String> confirmMatch(Long id, CustomUserDetails principal) {
        return confirmMatch(id, null, null, principal);
    }

    public ResponseEntity<String> confirmMatch(@PathVariable("id") Long id,
                                               @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        return confirmMatch(id, expectedVersion, null, principal);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<String> confirmMatch(@PathVariable("id") Long id,
                                               @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
                                               @RequestParam(value = "duplicateWarningAcceptedMatchId", required = false) Long duplicateWarningAcceptedMatchId,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        return toResponse(commandService().confirmMatch(id, expectedVersion, duplicateWarningAcceptedMatchId, resolveUser(principal)));
    }

    public ResponseEntity<String> disputeMatch(Long id, String note, CustomUserDetails principal) {
        return disputeMatch(id, note, null, principal);
    }

    @PostMapping("/{id}/dispute")
    public ResponseEntity<String> disputeMatch(@PathVariable("id") Long id,
                                               @RequestParam(value = "note", required = false) String note,
                                               @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        return toResponse(commandService().disputeMatch(id, note, expectedVersion, resolveUser(principal)));
    }

    public ResponseEntity<String> nullifyMatchByOwner(Long id, CustomUserDetails principal) {
        return nullifyMatchByOwner(id, null, principal);
    }

    @PostMapping("/{id}/nullify")
    public ResponseEntity<String> nullifyMatchByOwner(@PathVariable("id") Long id,
                                                      @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
                                                      @AuthenticationPrincipal CustomUserDetails principal) {
        return toResponse(commandService().nullifyMatch(id, expectedVersion, resolveUser(principal)));
    }

    @PostMapping("/{id}/request-nullify")
    public ResponseEntity<String> requestConfirmedNullification(@PathVariable("id") Long id,
                                                                @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
                                                                @AuthenticationPrincipal CustomUserDetails principal) {
        return toResponse(commandService().requestConfirmedNullification(id, expectedVersion, resolveUser(principal)));
    }

    private MatchConfirmationCommandService commandService() {
        return new MatchConfirmationCommandService(
                confirmationService,
                matchRepo,
                ladderV2Service,
                ladderAccessService,
                matchStateTransitionService,
                recentDuplicateMatchWarningService,
                trophyAwardService,
                confirmedMatchNullificationService);
    }

    private boolean canViewMatchFragment(User viewer, Match match) {
        if (viewer == null || viewer.getId() == null || match == null) {
            return false;
        }

        Long viewerId = viewer.getId();
        if (isSameUser(match.getA1(), viewerId)
                || isSameUser(match.getA2(), viewerId)
                || isSameUser(match.getB1(), viewerId)
                || isSameUser(match.getB2(), viewerId)
                || isSameUser(match.getLoggedBy(), viewerId)
                || isSameUser(match.getCosignedBy(), viewerId)) {
            return true;
        }

        if (ladderAccessService == null || match.getSeason() == null || match.getSeason().getId() == null) {
            return false;
        }

        try {
            return ladderAccessService.isSeasonAdmin(match.getSeason().getId(), viewer);
        } catch (Exception ex) {
            log.warn("Failed to check access for match fragment {}", match.getId(), ex);
            return false;
        }
    }

    private boolean isSameUser(User candidate, Long viewerId) {
        return candidate != null && candidate.getId() != null && candidate.getId().equals(viewerId);
    }

    private User resolveUser(CustomUserDetails principal) {
        return principal == null ? null : AuthenticatedUserSupport.refresh(principal.getUserObject());
    }

    private boolean matchesExpectedVersion(Match match, Long expectedVersion) {
        return match != null && expectedVersion != null && match.getVersion() == expectedVersion.longValue();
    }

    private ResponseEntity<String> toResponse(MatchConfirmationCommandService.MatchCommandResult result) {
        if (result == null) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected match workflow result");
        }
        if (result.warning()) {
            return warning(result.status(), result.warningCode(), result.duplicateWarningMatchId(), result.message());
        }
        if (result.success()) {
            if (result.firstManual() != null) {
                return ResponseEntity.status(result.status()).body(success(result.firstManual(), result.message()));
            }
            return ResponseEntity.status(result.status()).body(success(result.message()));
        }
        return error(result.status(), result.message());
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(failure(message));
    }

    private ResponseEntity<String> warning(HttpStatus status,
                                           String warningCode,
                                           Long duplicateWarningMatchId,
                                           String message) {
        return ResponseEntity.status(status).body(warningPayload(warningCode, duplicateWarningMatchId, message));
    }

    private String success(String message) {
        return "{\"success\":true,\"message\":\"" + escapeJsonValue(message) + "\"}";
    }

    private String success(boolean firstManual, String message) {
        return "{\"success\":true,\"firstManual\":" + firstManual + ",\"message\":\""
                + escapeJsonValue(message) + "\"}";
    }

    private String failure(String message) {
        return "{\"success\":false,\"message\":\"" + escapeJsonValue(message) + "\"}";
    }

    private String warningPayload(String warningCode, Long duplicateWarningMatchId, String message) {
        String duplicateWarningMatchIdValue = duplicateWarningMatchId == null ? "null" : String.valueOf(duplicateWarningMatchId);
        return "{\"success\":false,\"warning\":true,\"warningCode\":\""
                + escapeJsonValue(warningCode)
                + "\",\"duplicateWarningMatchId\":"
                + duplicateWarningMatchIdValue
                + ",\"message\":\""
                + escapeJsonValue(message)
                + "\"}";
    }

    private String escapeJsonValue(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ");
    }
}
