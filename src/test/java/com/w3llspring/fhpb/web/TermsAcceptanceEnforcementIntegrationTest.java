package com.w3llspring.fhpb.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TermsAcceptanceEnforcementIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Test
  void unacknowledgedUserIsRedirectedFromProtectedPagesToAcceptTerms() throws Exception {
    User user = saveUser("pending@example.com", "PendingPlayer", null);

    mockMvc
        .perform(get("/home").with(user(new CustomUserDetails(user))))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/accept-terms?returnTo=/home"));
  }

  @Test
  void acceptTermsPageKeepsLegalLinksInsideAcceptanceFlow() throws Exception {
    User user = saveUser("pending@example.com", "PendingPlayer", null);

    String html =
        mockMvc
            .perform(
                get("/accept-terms")
                    .param("returnTo", "/groups/7")
                    .with(user(new CustomUserDetails(user))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).contains("href=\"/terms?returnTo=/accept-terms?returnTo%3D/groups/7\"");
    assertThat(html).contains("href=\"/privacy?returnTo=/accept-terms?returnTo%3D/groups/7\"");
  }

  @Test
  void legalPagesRenderBackLinkToAcceptTermsWhenReturnToIsProvided() throws Exception {
    User user = saveUser("pending@example.com", "PendingPlayer", null);

    String html =
        mockMvc
            .perform(
                get("/terms")
                    .param("returnTo", "/accept-terms?returnTo=/home")
                    .with(user(new CustomUserDetails(user))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).contains("href=\"/accept-terms?returnTo=/home\"");
    assertThat(html).contains("href=\"/privacy?returnTo=/accept-terms?returnTo%3D/home\"");
    assertThat(html).contains(">Back<");
  }

  private User saveUser(String email, String nickName, Instant acknowledgedTermsAt) {
    User user = new User();
    user.setEmail(email);
    user.setNickName(nickName);
    user.setPassword("pw");
    user.setRegisteredAt(Instant.now());
    user.setAcknowledgedTermsAt(acknowledgedTermsAt);
    user.setMaxOwnedLadders(10);
    return userRepository.saveAndFlush(user);
  }
}
