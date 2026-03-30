package com.w3llspring.fhpb.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.w3llspring.fhpb.web.controller.competition.SessionJoinRequestController;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.competition.SessionJoinRequestService;
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
class SessionJoinRequestControllerTest {

  @Mock private SessionJoinRequestService sessionJoinRequests;

  private MockMvc mockMvc;
  private Authentication auth;

  @BeforeEach
  void setUp() {
    SessionJoinRequestController controller =
        new SessionJoinRequestController(sessionJoinRequests, 20);
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
  void membersReturnsActiveRosterForSessionPageRefresh() throws Exception {
    when(sessionJoinRequests.listActiveMembers(42L, 123L))
        .thenReturn(
            List.of(
                new SessionJoinRequestService.ActiveSessionMemberView(
                    501L, 123L, "Tester", "North Court"),
                new SessionJoinRequestService.ActiveSessionMemberView(
                    502L, 456L, "Partner", null)));

    mockMvc
        .perform(get("/api/sessions/42/members").principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberSectionTitle").value("Session Members (2/20)"))
        .andExpect(jsonPath("$.memberCount").value(2))
        .andExpect(jsonPath("$.members[0].membershipId").value(501))
        .andExpect(jsonPath("$.members[0].displayName").value("Tester"))
        .andExpect(jsonPath("$.members[0].courtName").value("North Court"))
        .andExpect(jsonPath("$.members[1].membershipId").value(502))
        .andExpect(jsonPath("$.members[1].displayName").value("Partner"));
  }
}
