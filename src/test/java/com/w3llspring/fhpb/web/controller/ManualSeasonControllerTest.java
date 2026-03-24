package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.competition.ManualSeasonController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.SeasonCarryOverService;
import com.w3llspring.fhpb.web.service.SeasonTransitionService;
import com.w3llspring.fhpb.web.service.SeasonTransitionWindow;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class ManualSeasonControllerTest {

  @Mock private LadderConfigRepository ladderRepo;

  @Mock private LadderMembershipRepository membershipRepo;

  @Mock private LadderSeasonRepository seasonRepo;

  @Captor private ArgumentCaptor<LadderSeason> seasonCaptor;

  private SeasonTransitionService transitionSvc;
  private SeasonCarryOverService seasonCarryOverService;
  private RoundRobinService roundRobinService;
  private RecordingStoryModeService storyModeService;
  private ManualSeasonController controller;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    transitionSvc =
        new SeasonTransitionService(null, null) {
          @Override
          public SeasonTransitionWindow canCreateSeason(LadderConfig ladder) {
            return SeasonTransitionWindow.ok();
          }
        };
    seasonCarryOverService =
        new SeasonCarryOverService(null, null, null) {
          @Override
          public void seedSeasonFromCarryOverIfEnabled(LadderSeason season) {}
        };
    roundRobinService = new RoundRobinService(null, null, null, null, null, null, null);
    storyModeService = new RecordingStoryModeService();
    controller =
        new ManualSeasonController(
            ladderRepo,
            membershipRepo,
            seasonRepo,
            transitionSvc,
            seasonCarryOverService,
            roundRobinService,
            storyModeService);
    ReflectionTestUtils.setField(controller, "siteWideAdminEmail", "admin@test.com");
  }

  @Test
  void start_inheritsStoryModeDefaultAndCreatesTrackers() {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(9L);
    ladder.setMode(LadderConfig.Mode.MANUAL);
    ladder.setOwnerUserId(77L);
    ladder.setStoryModeDefaultEnabled(true);

    User user = new User();
    user.setId(77L);
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    when(ladderRepo.lockById(9L)).thenReturn(ladder);
    when(seasonRepo.findActive(9L)).thenReturn(Optional.empty());

    String view = controller.start(9L, auth, new RedirectAttributesModelMap());

    verify(seasonRepo).saveAndFlush(seasonCaptor.capture());
    LadderSeason savedSeason = seasonCaptor.getValue();
    assertThat(view).isEqualTo("redirect:/groups/9");
    assertThat(savedSeason.isStoryModeEnabled()).isTrue();
    assertThat(savedSeason.getLadderConfig()).isEqualTo(ladder);
    assertThat(storyModeService.ensured).containsExactly(savedSeason);
  }

  @Test
  void start_deniesCompetitionManagementToNonSiteWideAdmin() {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(9L);
    ladder.setType(LadderConfig.Type.COMPETITION);
    ladder.setOwnerUserId(77L);

    User user = new User();
    user.setId(77L);
    user.setEmail("other-admin@test.com");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    when(ladderRepo.lockById(9L)).thenReturn(ladder);

    assertThatThrownBy(() -> controller.start(9L, auth, new RedirectAttributesModelMap()))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("site-wide admin");
  }

  @Test
  void start_doesNotTreatAuthenticationNameAsSiteWideAdminIdentity() {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(9L);
    ladder.setType(LadderConfig.Type.COMPETITION);
    ladder.setOwnerUserId(77L);

    var auth = new UsernamePasswordAuthenticationToken("admin@test.com", null, List.of());

    when(ladderRepo.lockById(9L)).thenReturn(ladder);

    assertThatThrownBy(() -> controller.start(9L, auth, new RedirectAttributesModelMap()))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("site-wide admin");
  }

  private static final class RecordingStoryModeService extends StoryModeService {
    private final java.util.List<LadderSeason> ensured = new java.util.ArrayList<>();

    private RecordingStoryModeService() {
      super(null, null, null, null, null, null);
    }

    @Override
    public void ensureTrackers(LadderSeason season) {
      ensured.add(season);
    }
  }
}
