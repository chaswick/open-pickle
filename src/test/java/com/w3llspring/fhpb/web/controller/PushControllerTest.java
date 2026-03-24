package com.w3llspring.fhpb.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.w3llspring.fhpb.web.controller.account.PushController;
import com.w3llspring.fhpb.web.db.UserPushSubscriptionRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserPushSubscription;
import com.w3llspring.fhpb.web.service.push.PushEndpointValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PushControllerTest {

  private UserPushSubscriptionRepository subscriptions;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    subscriptions = mock(UserPushSubscriptionRepository.class);
    PushEndpointValidator validator = new PushEndpointValidator("push.example.test");
    PushController controller = new PushController(subscriptions, null, validator);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    User user = new User();
    user.setId(17L);
    user.setNickName("tester");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void subscribeRejectsInvalidEndpoint() throws Exception {
    mockMvc
        .perform(
            post("/api/push/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"endpoint\":\"javascript:alert(1)\",\"p256dh\":\"abc\",\"auth\":\"def\"}"))
        .andExpect(status().isBadRequest());

    verify(subscriptions, never()).save(any());
  }

  @Test
  void subscribeRejectsDisallowedHost() throws Exception {
    mockMvc
        .perform(
            post("/api/push/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"endpoint\":\"https://127.0.0.1/internal\",\"p256dh\":\"abc\",\"auth\":\"def\"}"))
        .andExpect(status().isBadRequest());

    verify(subscriptions, never()).save(any());
  }

  @Test
  void subscribeStoresNormalizedSubscription() throws Exception {
    when(subscriptions.findByEndpoint("https://push.example.test/sub/123"))
        .thenReturn(Optional.empty());

    mockMvc
        .perform(
            post("/api/push/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "endpoint": " https://push.example.test/sub/123 ",
                                  "p256dh": " abcDEF123+/=_- ",
                                  "auth": " xyz987+/=_- ",
                                  "userAgent": " Browser/1.0 "
                                }
                                """))
        .andExpect(status().isOk());

    verify(subscriptions).findByEndpoint("https://push.example.test/sub/123");
    verify(subscriptions).save(any(UserPushSubscription.class));
  }

  @Test
  void unsubscribeRejectsInvalidEndpoint() throws Exception {
    mockMvc
        .perform(
            post("/api/push/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"ftp://push.example.test/sub/123\"}"))
        .andExpect(status().isBadRequest());

    verify(subscriptions, never()).deleteByUserIdAndEndpoint(any(), any());
  }

  @Test
  void unsubscribeDeletesByNormalizedEndpoint() throws Exception {
    mockMvc
        .perform(
            post("/api/push/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\" https://push.example.test/sub/123 \"}"))
        .andExpect(status().isOk());

    verify(subscriptions).deleteByUserIdAndEndpoint(17L, "https://push.example.test/sub/123");
  }

  @Test
  void subscribeRequiresAuthentication() throws Exception {
    SecurityContextHolder.clearContext();

    mockMvc
        .perform(
            post("/api/push/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "endpoint": "https://push.example.test/sub/123",
                                  "p256dh": "abcDEF123+/=_-",
                                  "auth": "xyz987+/=_-"
                                }
                                """))
        .andExpect(status().isUnauthorized());
  }
}
