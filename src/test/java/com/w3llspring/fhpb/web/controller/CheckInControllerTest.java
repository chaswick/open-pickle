package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.controller.meetups.CheckInController;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.PlayLocationAliasRepository;
import com.w3llspring.fhpb.web.db.PlayLocationCheckInRepository;
import com.w3llspring.fhpb.web.db.PlayLocationRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.PlayLocationService;
import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

class CheckInControllerTest {

  private StubPlayLocationService playLocationService;
  private CheckInController controller;

  @BeforeEach
  void setUp() {
    playLocationService = new StubPlayLocationService();
    controller = new CheckInController(playLocationService);
    ReflectionTestUtils.setField(controller, "checkInEnabled", true);

    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void checkInPageAddsPageModelToView() {
    User currentUser =
        ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
            .getUserObject();
    PlayLocationService.CheckInPageView pageView =
        new PlayLocationService.CheckInPageView(null, List.of());
    playLocationService.pageView = pageView;

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.checkInPage("Checked in.", true, model);

    assertThat(view).isEqualTo("auth/check-in");
    assertThat(model.get("checkInPage")).isEqualTo(pageView);
    assertThat(model.get("autoStartCheckIn")).isEqualTo(true);
    assertThat(model.get("toastMessage")).isEqualTo("Checked in.");
    assertThat(playLocationService.lastViewedUser).isSameAs(currentUser);
  }

  @Test
  void checkInPageSuppressesAutostartWhenUserAlreadyCheckedIn() {
    PlayLocationService.ActiveCheckInView activeCheckIn =
        new PlayLocationService.ActiveCheckInView(
            44L,
            "Lakeside Courts",
            java.time.Instant.parse("2026-03-15T23:00:00Z"),
            "Mar 15, 7:00 PM",
            2,
            1);
    playLocationService.pageView =
        new PlayLocationService.CheckInPageView(activeCheckIn, List.of());

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.checkInPage(null, true, model);

    assertThat(view).isEqualTo("auth/check-in");
    assertThat(model.get("autoStartCheckIn")).isEqualTo(false);
  }

  @Test
  void checkInPageRedirectsToLoginWithoutAuthenticatedUser() {
    SecurityContextHolder.clearContext();

    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.checkInPage(null, true, model);

    assertThat(view).isEqualTo("redirect:/login");
  }

  private static final class StubPlayLocationService extends PlayLocationService {
    private CheckInPageView pageView = new CheckInPageView(null, List.of());
    private User lastViewedUser;

    private StubPlayLocationService() {
      super(
          org.mockito.Mockito.mock(PlayLocationRepository.class),
          org.mockito.Mockito.mock(PlayLocationAliasRepository.class),
          org.mockito.Mockito.mock(PlayLocationCheckInRepository.class),
          org.mockito.Mockito.mock(LadderMembershipRepository.class),
          org.mockito.Mockito.mock(UserRepository.class),
          org.mockito.Mockito.mock(DisplayNameModerationService.class),
          180,
          120d,
          2);
    }

    @Override
    public CheckInPageView buildPage(User currentUser) {
      this.lastViewedUser = currentUser;
      return pageView;
    }

    @Override
    public String getExpiryLabel() {
      return "3 hours";
    }

    @Override
    public int getLocationNameMaxLength() {
      return 80;
    }
  }
}
