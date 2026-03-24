package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.controller.trophy.TrophyController;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.jobs.trophy.TrophyAwardSweepJob;
import com.w3llspring.fhpb.web.service.trophy.TrophyCatalog;
import com.w3llspring.fhpb.web.service.trophy.TrophyCatalogService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
class TrophyControllerTest {

  private TrophyCatalogService catalogService;
  private TrophyRepository trophyRepository;
  private TrophyAwardSweepJob trophyAwardSweepJob;

  private List<TrophyCatalog> seasonCatalogs = List.of();

  private TrophyController controller;

  @BeforeEach
  void setUp() {
    catalogService =
        new TrophyCatalogService(null, null, null, null, null, null, null) {
          @Override
          public List<TrophyCatalog> fetchSeasonCatalogs(User user) {
            return seasonCatalogs;
          }
        };
    trophyRepository = null;
    trophyAwardSweepJob = new TrophyAwardSweepJob(null, null, null, 0);
    controller = new TrophyController(catalogService, trophyRepository, trophyAwardSweepJob);
  }

  @Test
  void viewTrophiesPaginatesSeasonCatalogsFiveAtATime() {
    User viewer = new User();
    viewer.setId(7L);
    viewer.setNickName("viewer");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of());

    List<TrophyCatalog> catalogs =
        List.of(
            catalog(1L, "Season 1"),
            catalog(2L, "Season 2"),
            catalog(3L, "Season 3"),
            catalog(4L, "Season 4"),
            catalog(5L, "Season 5"),
            catalog(6L, "Season 6"),
            catalog(7L, "Season 7"));
    seasonCatalogs = catalogs;

    ExtendedModelMap model = new ExtendedModelMap();
    String view = controller.viewTrophies(auth, 1, model);

    assertThat(view).isEqualTo("auth/trophies");
    assertThat(model.get("seasonPage")).isEqualTo(1);
    assertThat(model.get("seasonPageSize")).isEqualTo(5);
    assertThat(model.get("seasonCatalogTotalPages")).isEqualTo(2);
    assertThat(model.get("seasonCatalogPageNumbers")).isEqualTo(List.of(0, 1));
    @SuppressWarnings("unchecked")
    List<TrophyCatalog> pageCatalogs = (List<TrophyCatalog>) model.get("seasonCatalogs");
    assertThat(pageCatalogs)
        .extracting(TrophyCatalog::getSeasonName)
        .containsExactly("Season 6", "Season 7");
  }

  private TrophyCatalog catalog(Long seasonId, String seasonName) {
    return new TrophyCatalog(
        seasonId,
        seasonName,
        "Competition",
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 2, 1),
        List.of(),
        false,
        null,
        null,
        null);
  }
}
