package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.w3llspring.fhpb.web.controller.meetups.CheckInApiController;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.PlayLocationAliasRepository;
import com.w3llspring.fhpb.web.db.PlayLocationCheckInRepository;
import com.w3llspring.fhpb.web.db.PlayLocationRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.PlayLocationService;
import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class CheckInApiControllerTest {

  private StubPlayLocationService playLocationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    playLocationService = new StubPlayLocationService();
    CheckInApiController controller = new CheckInApiController(playLocationService);
    ReflectionTestUtils.setField(controller, "checkInEnabled", true);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

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
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
  }

  @Test
  void resolveReturns404WhenFeatureDisabled() throws Exception {
    CheckInApiController controller = new CheckInApiController(playLocationService);
    ReflectionTestUtils.setField(controller, "checkInEnabled", false);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    localMvc
        .perform(
            post("/api/check-in/resolve")
                .contentType("application/json")
                .content("{\"latitude\":27.0,\"longitude\":-82.0}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void resolveRequiresCoordinates() throws Exception {
    mockMvc
        .perform(post("/api/check-in/resolve").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void resolveReturnsSuggestions() throws Exception {
    playLocationService.resolveOutcome =
        PlayLocationService.ResolveOutcome.chooseName(
            44L,
            "Nearby location found.",
            List.of(new PlayLocationService.NameSuggestion("Lakeside", 4)));

    mockMvc
        .perform(
            post("/api/check-in/resolve")
                .contentType("application/json")
                .content("{\"latitude\":27.0,\"longitude\":-82.0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("choose_name"))
        .andExpect(jsonPath("$.locationId").value(44))
        .andExpect(jsonPath("$.suggestions[0].name").value("Lakeside"));
    assertThat(playLocationService.lastResolvedUserId).isEqualTo(123L);
    assertThat(playLocationService.lastResolvedLatitude).isEqualTo(27.0d);
    assertThat(playLocationService.lastResolvedLongitude).isEqualTo(-82.0d);
  }

  @Test
  void completeReturnsSuccessMessage() throws Exception {
    playLocationService.completeOutcome = new PlayLocationService.CheckInOutcome("Checked in.");

    mockMvc
        .perform(
            post("/api/check-in/complete")
                .contentType("application/json")
                .content(
                    "{\"latitude\":27.0,\"longitude\":-82.0,\"locationId\":44,\"selectedName\":\"Lakeside\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Checked in."));
    assertThat(playLocationService.lastCompletedUserId).isEqualTo(123L);
    assertThat(playLocationService.lastCompleteCommand).isNotNull();
    assertThat(playLocationService.lastCompleteCommand.getLocationId()).isEqualTo(44L);
    assertThat(playLocationService.lastCompleteCommand.getSelectedName()).isEqualTo("Lakeside");
  }

  @Test
  void sessionNearbyJoinReturnsSuccessMessage() throws Exception {
    playLocationService.sessionNearbyResolveOutcome =
        PlayLocationService.ResolveOutcome.checkedIn(
            "Location confirmed. Looking for nearby sessions now.");

    mockMvc
        .perform(
            post("/api/check-in/session-nearby-join")
                .contentType("application/json")
                .content("{\"latitude\":27.0,\"longitude\":-82.0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("checked_in"))
        .andExpect(
            jsonPath("$.message")
                .value("Location confirmed. Looking for nearby sessions now."));
    assertThat(playLocationService.lastSessionNearbyResolvedUserId).isEqualTo(123L);
    assertThat(playLocationService.lastSessionNearbyResolvedLatitude).isEqualTo(27.0d);
    assertThat(playLocationService.lastSessionNearbyResolvedLongitude).isEqualTo(-82.0d);
  }

  @Test
  void completeReturnsTooManyRequestsWhenRateLimited() throws Exception {
    playLocationService.completeFailure =
        new PlayLocationService.CheckInRateLimitException("Please wait.");

    mockMvc
        .perform(
            post("/api/check-in/complete")
                .contentType("application/json")
                .content("{\"latitude\":27.0,\"longitude\":-82.0,\"selectedName\":\"Lakeside\"}"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void resolveReturnsUnauthorizedWithoutAuthenticatedUser() throws Exception {
    SecurityContextHolder.clearContext();

    mockMvc
        .perform(
            post("/api/check-in/resolve")
                .contentType("application/json")
                .content("{\"latitude\":27.0,\"longitude\":-82.0}"))
        .andExpect(status().isUnauthorized());
  }

  private static final class StubPlayLocationService extends PlayLocationService {
    private ResolveOutcome resolveOutcome = ResolveOutcome.checkedIn("Checked in.");
    private ResolveOutcome sessionNearbyResolveOutcome = ResolveOutcome.checkedIn("Checked in.");
    private CheckInOutcome completeOutcome = new CheckInOutcome("Checked in.");
    private Long lastResolvedUserId;
    private double lastResolvedLatitude;
    private double lastResolvedLongitude;
    private Long lastSessionNearbyResolvedUserId;
    private double lastSessionNearbyResolvedLatitude;
    private double lastSessionNearbyResolvedLongitude;
    private Long lastCompletedUserId;
    private CompleteCheckInCommand lastCompleteCommand;
    private RuntimeException resolveFailure;
    private RuntimeException sessionNearbyResolveFailure;
    private RuntimeException completeFailure;

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
    public ResolveOutcome resolveCheckIn(long userId, double latitude, double longitude) {
      if (resolveFailure != null) {
        throw resolveFailure;
      }
      this.lastResolvedUserId = userId;
      this.lastResolvedLatitude = latitude;
      this.lastResolvedLongitude = longitude;
      return resolveOutcome;
    }

    @Override
    public ResolveOutcome resolveSessionNearbyJoinCheckIn(long userId, double latitude, double longitude) {
      if (sessionNearbyResolveFailure != null) {
        throw sessionNearbyResolveFailure;
      }
      this.lastSessionNearbyResolvedUserId = userId;
      this.lastSessionNearbyResolvedLatitude = latitude;
      this.lastSessionNearbyResolvedLongitude = longitude;
      return sessionNearbyResolveOutcome;
    }

    @Override
    public CheckInOutcome completeCheckIn(long userId, CompleteCheckInCommand command) {
      if (completeFailure != null) {
        throw completeFailure;
      }
      this.lastCompletedUserId = userId;
      this.lastCompleteCommand = command;
      return completeOutcome;
    }
  }
}
