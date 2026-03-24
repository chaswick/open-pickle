package com.w3llspring.fhpb.web.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.w3llspring.fhpb.web.controller.meetups.MeetupsController;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderMeetupRsvp;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.LadderMeetupService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class MeetupsControllerTest {

  private LadderMeetupService meetups;

  @BeforeEach
  void setup() {
    meetups = mock(LadderMeetupService.class);
    MeetupsController controller = new MeetupsController(meetups);

    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  void upcoming_returns404WhenFeatureDisabled() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", false);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    localMvc.perform(get("/api/meetups/upcoming")).andExpect(status().isNotFound());
  }

  @Test
  void upcoming_returns200WhenEnabled() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    when(meetups.upcomingForUser(123L, 5)).thenReturn(List.of());

    localMvc.perform(get("/api/meetups/upcoming")).andExpect(status().isOk());

    verify(meetups).upcomingForUser(123L, 5);
  }

  @Test
  void create_requiresBodyFields() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    localMvc
        .perform(post("/api/meetups/create").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_happyPath() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    // 2026-01-22 13:00 ET == 18:00Z (winter)
    Instant startsAt =
        ZonedDateTime.of(2026, 1, 22, 13, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();
    localMvc
        .perform(
            post("/api/meetups/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ladderId\":7,\"date\":\"2026-01-22\",\"time\":\"13:00\"}"))
        .andExpect(status().isOk());

    verify(meetups).createSlot(123L, 7L, startsAt);
  }

  @Test
  void create_returns400WhenPlanLimitReached() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    Instant startsAt =
        ZonedDateTime.of(2026, 1, 22, 13, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();
    doThrow(new IllegalArgumentException("You can only have 3 active plans per ladder"))
        .when(meetups)
        .createSlot(123L, 7L, startsAt);

    localMvc
        .perform(
            post("/api/meetups/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ladderId\":7,\"date\":\"2026-01-22\",\"time\":\"13:00\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rsvp_rejectsInvalidStatus() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    localMvc
        .perform(
            post("/api/meetups/55/rsvp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"wat\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rsvp_happyPath_isCaseInsensitive() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    localMvc
        .perform(
            post("/api/meetups/55/rsvp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"maybe\"}"))
        .andExpect(status().isOk());

    verify(meetups).setRsvp(123L, 55L, LadderMeetupRsvp.Status.MAYBE);
  }

  @Test
  void delete_returns404WhenFeatureDisabled() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", false);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    localMvc.perform(post("/api/meetups/55/delete")).andExpect(status().isNotFound());
  }

  @Test
  void delete_happyPath() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    localMvc.perform(post("/api/meetups/55/delete")).andExpect(status().isOk());

    verify(meetups).deleteSlot(123L, 55L);
  }

  @Test
  void delete_returns403WhenNotAllowed() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    doThrow(new IllegalArgumentException("Not allowed to delete this plan"))
        .when(meetups)
        .deleteSlot(123L, 55L);

    localMvc.perform(post("/api/meetups/55/delete")).andExpect(status().isForbidden());
  }

  @Test
  void location_returns404WhenFeatureDisabled() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", false);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    localMvc
        .perform(
            post("/api/meetups/55/location")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locationCode\":\"AB\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void location_requiresBody() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    localMvc.perform(post("/api/meetups/55/location")).andExpect(status().isBadRequest());
  }

  @Test
  void location_happyPath() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    localMvc
        .perform(
            post("/api/meetups/55/location")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locationCode\":\"ab\"}"))
        .andExpect(status().isOk());

    verify(meetups).updateLocation(123L, 55L, "ab");
  }

  @Test
  void location_returns403WhenNotAllowed() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    doThrow(new IllegalArgumentException("Not allowed to update location"))
        .when(meetups)
        .updateLocation(123L, 55L, "AB");

    localMvc
        .perform(
            post("/api/meetups/55/location")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locationCode\":\"AB\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void location_returns400WhenInvalid() throws Exception {
    MeetupsController controller = new MeetupsController(meetups);
    ReflectionTestUtils.setField(controller, "meetupsEnabled", true);
    MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

    doThrow(new IllegalArgumentException("Location must be exactly 2 characters"))
        .when(meetups)
        .updateLocation(123L, 55L, "A");

    localMvc
        .perform(
            post("/api/meetups/55/location")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locationCode\":\"A\"}"))
        .andExpect(status().isBadRequest());
  }
}
