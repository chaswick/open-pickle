package com.w3llspring.fhpb.web.controller.trophy;

import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyArt;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.jobs.trophy.TrophyAwardSweepJob;
import com.w3llspring.fhpb.web.service.trophy.TrophyCatalog;
import com.w3llspring.fhpb.web.service.trophy.TrophyCatalogService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import com.w3llspring.fhpb.web.util.PaginationWindowSupport;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Secured("ROLE_USER")
public class TrophyController {

  private static final int TROPHY_SEASONS_PAGE_SIZE = 5;

  private final TrophyCatalogService catalogService;
  private final TrophyRepository trophyRepository;
  private final TrophyAwardSweepJob trophyAwardSweepJob;

  public TrophyController(
      TrophyCatalogService catalogService,
      TrophyRepository trophyRepository,
      TrophyAwardSweepJob trophyAwardSweepJob) {
    this.catalogService = catalogService;
    this.trophyRepository = trophyRepository;
    this.trophyAwardSweepJob = trophyAwardSweepJob;
  }

  @GetMapping("/trophies")
  public String viewTrophies(
      Authentication authentication,
      @org.springframework.web.bind.annotation.RequestParam(value = "seasonPage", required = false)
          Integer seasonPage,
      Model model) {
    User user = resolveUser(authentication);
    if (user != null) {
      model.addAttribute("userName", user.getNickName());
    }
    model.addAttribute("viewerUser", user);
    model.addAttribute("trophySweepLastCheckedAt", trophyAwardSweepJob.getLastCompletedAt());
    PaginationWindowSupport.PageSlice<TrophyCatalog> seasonCatalogPage =
        PaginationWindowSupport.slice(
            catalogService.fetchSeasonCatalogs(user),
            seasonPage != null ? Math.max(0, seasonPage) : 0,
            TROPHY_SEASONS_PAGE_SIZE);
    model.addAttribute("seasonCatalogs", seasonCatalogPage.items());
    model.addAttribute("seasonPage", seasonCatalogPage.pageNumber());
    model.addAttribute("seasonPageSize", seasonCatalogPage.pageSize());
    model.addAttribute("seasonCatalogTotalPages", seasonCatalogPage.totalPages());
    model.addAttribute("seasonCatalogTotalElements", seasonCatalogPage.totalCount());
    applySeasonCatalogPaginationModel(
        model, seasonCatalogPage.pageNumber(), seasonCatalogPage.totalPages());
    return "auth/trophies";
  }

  @GetMapping("/trophies/season/{seasonId}")
  public String viewSeason(
      @PathVariable Long seasonId, Authentication authentication, Model model) {
    User user = resolveUser(authentication);
    if (user != null) {
      model.addAttribute("userName", user.getNickName());
    }

    TrophyCatalog seasonCatalog =
        catalogService
            .fetchSeasonCatalog(user, seasonId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    model.addAttribute("seasonCatalog", seasonCatalog);
    return "auth/trophySeason";
  }

  @GetMapping("/trophies/image/{trophyId}")
  public ResponseEntity<?> fetchTrophyImage(@PathVariable Long trophyId) {
    Trophy trophy = loadTrophyWithArt(trophyId);
    return respondWithArt(trophy.getArt());
  }

  @GetMapping("/trophies/badge/{trophyId}")
  public ResponseEntity<?> fetchTrophyBadgeImage(@PathVariable Long trophyId) {
    Trophy trophy = loadTrophyWithArt(trophyId);
    TrophyArt art = trophy.getBadgeArt() != null ? trophy.getBadgeArt() : trophy.getArt();
    return respondWithArt(art);
  }

  private User resolveUser(Authentication authentication) {
    return AuthenticatedUserSupport.currentUser(authentication);
  }

  private void applySeasonCatalogPaginationModel(Model model, int currentPage, int totalPages) {
    PaginationWindowSupport.PaginationWindow pagination =
        PaginationWindowSupport.buildWindow(currentPage, totalPages);
    model.addAttribute("seasonCatalogPageNumbers", pagination.pageNumbers());
    model.addAttribute("seasonCatalogShowFirstPage", pagination.showFirstPage());
    model.addAttribute("seasonCatalogShowLastPage", pagination.showLastPage());
    model.addAttribute("seasonCatalogShowLeadingEllipsis", pagination.showLeadingEllipsis());
    model.addAttribute("seasonCatalogShowTrailingEllipsis", pagination.showTrailingEllipsis());
    model.addAttribute("seasonCatalogJumpBackPage", pagination.jumpBackPage());
    model.addAttribute("seasonCatalogJumpForwardPage", pagination.jumpForwardPage());
  }

  private Trophy loadTrophyWithArt(Long trophyId) {
    if (trophyId == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    Trophy trophy = trophyRepository.findByIdWithArtAndBadgeArt(trophyId).orElse(null);
    if (trophy == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return trophy;
  }

  private ResponseEntity<?> respondWithArt(TrophyArt art) {
    if (art == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    byte[] imageBytes = art.getImageBytes();
    if (imageBytes != null && imageBytes.length > 0) {
      return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(imageBytes);
    }
    String imageUrl = art.getImageUrl();
    if (imageUrl == null || imageUrl.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(imageUrl)).build();
  }
}
