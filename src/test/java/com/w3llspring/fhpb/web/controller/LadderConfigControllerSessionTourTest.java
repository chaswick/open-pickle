package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.competition.LadderConfigController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.UserDisplayNameAuditRepository;
import com.w3llspring.fhpb.web.db.UserOnboardingMarkerRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserOnboardingMarker;
import com.w3llspring.fhpb.web.service.SeasonTransitionService;
import com.w3llspring.fhpb.web.service.SeasonTransitionWindow;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationOperations;
import com.w3llspring.fhpb.web.service.user.UserOnboardingService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
class LadderConfigControllerSessionTourTest {

  @Mock private UserRepository userRepo;
  @Mock private UserDisplayNameAuditRepository userDisplayNameAuditRepository;
  @Mock private LadderConfigRepository configs;
  @Mock private LadderSeasonRepository seasons;
  @Mock private LadderMembershipRepository membershipRepo;
  @Mock private GroupAdministrationOperations groupAdministration;
  @Mock private UserOnboardingMarkerRepository userOnboardingMarkerRepository;

  private LadderConfigController controller;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    SeasonTransitionService transitionSvc =
        new SeasonTransitionService(null, null) {
          @Override
          public SeasonTransitionWindow canCreateSeason(LadderConfig ladder) {
            return SeasonTransitionWindow.ok();
          }

          @Override
          public String formatCountdown(SeasonTransitionWindow window) {
            return "";
          }
        };
    StoryModeService storyModeService =
        new StoryModeService(null, null, null, null, null, null) {
          @Override
          public boolean isFeatureEnabled() {
            return true;
          }
        };
    controller =
        new LadderConfigController(
            userRepo,
            userDisplayNameAuditRepository,
            null,
            groupAdministration,
            configs,
            seasons,
            membershipRepo,
            transitionSvc,
            null,
            null,
            storyModeService,
            20);
    ReflectionTestUtils.setField(
        controller,
        "userOnboardingService",
        new UserOnboardingService(userOnboardingMarkerRepository));
  }

  @Test
  void showOpensOwnerTourWhenRequestedAndMarkerNotCompleted() {
    User owner = user(7L, "Owner");
    LadderConfig session = session(42L, 7L);
    LadderMembership ownerMembership =
        membership(session, 7L, LadderMembership.Role.ADMIN, LadderMembership.State.ACTIVE);

    when(configs.findById(42L)).thenReturn(Optional.of(session));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(ownerMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(anyIterable())).thenReturn(List.of(owner));
    when(userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
            7L, UserOnboardingService.SESSION_OWNER_TOUR_V1))
        .thenReturn(false);

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(owner), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");
    request.setParameter("tour", "owner");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat(model.get("sessionTourVariant")).isEqualTo("owner");
  }

  @Test
  void showOpensJoinerTourWhenRequestedAndMarkerNotCompleted() {
    User joiner = user(8L, "Joiner");
    User owner = user(7L, "Owner");
    LadderConfig session = session(42L, 7L);
    LadderMembership joinerMembership =
        membership(session, 8L, LadderMembership.Role.MEMBER, LadderMembership.State.ACTIVE);

    when(configs.findById(42L)).thenReturn(Optional.of(session));
    when(seasons.findByLadderConfigIdOrderByStartDateDesc(42L)).thenReturn(List.of());
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(joinerMembership));
    when(membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.BANNED))
        .thenReturn(List.of());
    when(userRepo.findAllById(anyIterable())).thenReturn(List.of(joiner, owner));
    when(userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
            8L, UserOnboardingService.SESSION_JOINER_TOUR_V1))
        .thenReturn(false);

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(joiner), null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/groups/42");
    request.setParameter("tour", "joiner");

    String view = controller.show(42L, "joined", model, auth, request);

    assertThat(view).isEqualTo("auth/show");
    assertThat(model.get("sessionTourVariant")).isEqualTo("joiner");
  }

  @Test
  void completeSessionTourMarksPersistentOnboardingMarker() {
    User joiner = user(8L, "Joiner");
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(joiner), null, List.of());

    when(userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
            8L, UserOnboardingService.SESSION_JOINER_TOUR_V1))
        .thenReturn(false);
    when(userOnboardingMarkerRepository.saveAndFlush(any(UserOnboardingMarker.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = controller.completeSessionTour("joiner", auth);

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    verify(userOnboardingMarkerRepository)
        .saveAndFlush(
            argThat(
                marker ->
                    marker != null
                        && marker.getUserId().equals(8L)
                        && UserOnboardingService.SESSION_JOINER_TOUR_V1.equals(marker.getMarkerKey())
                        && marker.getCompletedAt() != null));
  }

  private User user(Long id, String nickName) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickName);
    return user;
  }

  private LadderConfig session(Long id, Long ownerUserId) {
    LadderConfig session = new LadderConfig();
    session.setId(id);
    session.setTitle("Saturday Open Session");
    session.setOwnerUserId(ownerUserId);
    session.setType(LadderConfig.Type.SESSION);
    session.setCreatedAt(Instant.now().minusSeconds(60));
    return session;
  }

  private LadderMembership membership(
      LadderConfig session,
      Long userId,
      LadderMembership.Role role,
      LadderMembership.State state) {
    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(session);
    membership.setUserId(userId);
    membership.setRole(role);
    membership.setState(state);
    membership.setJoinedAt(Instant.now().minusSeconds(30));
    return membership;
  }
}
