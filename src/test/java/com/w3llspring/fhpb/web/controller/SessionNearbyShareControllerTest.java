package com.w3llspring.fhpb.web.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.w3llspring.fhpb.web.controller.competition.SessionNearbyShareController;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.competition.SessionJoinRequestService;
import com.w3llspring.fhpb.web.service.competition.SessionNearbyShareService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SessionNearbyShareControllerTest {

  @Mock private SessionNearbyShareService nearbySharing;
  @Mock private SessionJoinRequestService sessionJoinRequests;

  private MockMvc mockMvc;
  private Authentication auth;

  @BeforeEach
  void setUp() {
    SessionNearbyShareController controller =
        new SessionNearbyShareController(nearbySharing, sessionJoinRequests);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    User user = new User();
    user.setId(123L);
    user.setNickName("Tester");
    auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void nearbyCandidatesReturnsSessionsFromActiveCheckIn() throws Exception {
    when(nearbySharing.listForRequesterFromActiveCheckIn(123L))
        .thenReturn(
            new SessionNearbyShareService.NearbySessionsView(
                "Lakeside Courts",
                List.of(
                    new SessionNearbyShareService.NearbySessionView(
                        42L, "Saturday Open", "Host", "Lakeside Courts", 6L))));

    mockMvc
        .perform(get("/api/sessions/nearby-sharing/candidates").principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locationName").value("Lakeside Courts"))
        .andExpect(jsonPath("$.sessions[0].sessionId").value(42))
        .andExpect(jsonPath("$.sessions[0].sessionTitle").value("Saturday Open"))
        .andExpect(jsonPath("$.sessions[0].ownerDisplayName").value("Host"));
  }

  @Test
  void enableForHostReturnsConflictWhenCheckInMissing() throws Exception {
    when(nearbySharing.enableUsingActiveCheckIn(42L, 123L))
        .thenThrow(new IllegalStateException("Check in first to use nearby session sharing."));

    mockMvc
        .perform(post("/api/sessions/42/nearby-sharing").principal(auth))
        .andExpect(status().isConflict());
  }

  @Test
  void requestJoinRedirectsToWaitingPageWhenPendingApproval() throws Exception {
    when(sessionJoinRequests.submitBySessionId(42L, 123L))
        .thenReturn(
            new SessionJoinRequestService.SubmissionOutcome(
                SessionJoinRequestService.SubmissionState.PENDING_APPROVAL, 42L, 88L));

    mockMvc
        .perform(post("/api/sessions/42/nearby-sharing/join").principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.requestId").value(88))
        .andExpect(jsonPath("$.redirectUrl").value("/groups/join-requests/88"));
  }

  @Test
  void requestJoinReturnsConflictWhenSessionIsNotNearby() throws Exception {
    doThrow(new IllegalStateException("This session is not discoverable from your current check-in."))
        .when(nearbySharing)
        .requireSessionVisibleToRequester(42L, 123L);

    mockMvc
        .perform(post("/api/sessions/42/nearby-sharing/join").principal(auth))
        .andExpect(status().isConflict());
  }
}
