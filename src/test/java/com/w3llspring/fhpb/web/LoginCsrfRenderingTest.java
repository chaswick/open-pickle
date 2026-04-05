package com.w3llspring.fhpb.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
class LoginCsrfRenderingTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Test
  void loginPageRendersSingleCsrfHiddenInput() throws Exception {
    String html =
        mockMvc
            .perform(get("/login"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // One meta tag is expected for JS/AJAX helpers
    assertThat(countOccurrences(html, Pattern.compile("<meta\\s+name=\\\"_csrf\\\""))).isEqualTo(1);

    // Exactly one hidden input per page for unauthenticated /login (the logout form isn't rendered)
    assertThat(countOccurrences(html, Pattern.compile("<input[^>]+name=\\\"_csrf\\\"")))
        .isEqualTo(1);
  }

  @Test
  void authenticatedHomePageRendersCsrfProtectedLogoutForm() throws Exception {
    User user = new User();
    user.setEmail("tester@example.com");
    user.setNickName("Tester");
    user.setPassword("ignored");
    user.setAcknowledgedTermsAt(java.time.Instant.now());
    user = userRepository.save(user);

    String html =
        mockMvc
            .perform(get("/home").with(user(new CustomUserDetails(user))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).contains("action=\"/logout\"");
    assertThat(html).contains("name=\"_csrf\"");
    assertThat(html).contains("<meta name=\"_csrf\"");
  }

  @Test
  void logoutUsesSpringSecurityHandlerAndRedirectsHome() throws Exception {
    User user = new User();
    user.setId(123L);
    user.setEmail("tester@example.com");
    user.setNickName("Tester");
    user.setPassword("ignored");
    user.setAcknowledgedTermsAt(java.time.Instant.now());

    mockMvc
        .perform(post("/logout").with(user(new CustomUserDetails(user))).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void logoutWithoutCsrfLogsDeniedRequestWithOriginalUri(CapturedOutput output)
      throws Exception {
    User user = new User();
    user.setId(123L);
    user.setEmail("tester@example.com");
    user.setNickName("Tester");
    user.setPassword("ignored");
    user.setAcknowledgedTermsAt(java.time.Instant.now());

    mockMvc
        .perform(post("/logout").with(user(new CustomUserDetails(user))))
        .andExpect(status().isForbidden());

    assertThat(output.getAll()).contains("Denied request: status=403");
    assertThat(output.getAll()).contains("cause=csrf");
    assertThat(output.getAll()).contains("method=POST");
    assertThat(output.getAll()).contains("uri=/logout");
  }

  @Test
  void registerPageRendersCsrfProtectionWithoutIssuingJsessionId() throws Exception {
    var response =
        mockMvc.perform(get("/register")).andExpect(status().isOk()).andReturn().getResponse();

    assertThat(response.getContentAsString()).contains("name=\"formToken\"");
    assertThat(response.getContentAsString()).contains("name=\"_csrf\"");
    assertThat(response.getContentAsString()).contains("<meta name=\"_csrf\"");
    assertThat(response.getCookies()).extracting(Cookie::getName).doesNotContain("JSESSIONID");
  }

  @Test
  void landingPageDoesNotRenderGoogleTagScriptsByDefault() throws Exception {
    String html =
        mockMvc
            .perform(get("/"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).doesNotContain("https://www.googletagmanager.com/gtag/js?id=");
    assertThat(html).doesNotContain("window.dataLayer = window.dataLayer || [];");
    assertThat(html).doesNotContain("gtag('config'");
  }

  private static int countOccurrences(String haystack, Pattern needle) {
    int count = 0;
    Matcher matcher = needle.matcher(haystack);
    while (matcher.find()) {
      count++;
    }
    return count;
  }
}
