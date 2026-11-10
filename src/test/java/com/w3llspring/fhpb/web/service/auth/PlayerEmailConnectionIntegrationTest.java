package com.w3llspring.fhpb.web.service.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
import com.w3llspring.fhpb.web.service.email.EmailService;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "fhpb.security.remember-me.key=email-connection-test-key",
      "server.servlet.session.cookie.secure=false",
      "fhpb.security.guest-access.enabled=true",
      "fhpb.public.base-url="
    })
@AutoConfigureMockMvc
@Transactional
class PlayerEmailConnectionIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired UserOnboardingMarkerRepository markers;
  @Autowired LadderConfigRepository sessions;
  @Autowired LadderMembershipRepository memberships;
  @Autowired PlayerEmailConnectionService emails;
  @Autowired PlayerAccessService players;
  @Autowired BCryptPasswordEncoder encoder;
  @Autowired UserPasswordResetService resets;
  @Autowired EntityManager em;
  @MockitoBean EmailService mail;
  User player;
  String address;

  @BeforeEach
  void setup() {
    address = UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    player = new User();
    player.setNameOnlyAccount(true);
    player.setEmail(UUID.randomUUID().toString().replace("-", "") + "@g.invalid");
    player.setNickName("EmailPlayer" + UUID.randomUUID().toString().substring(0, 6));
    player.setPassword(PlayerAccessService.newInternalCredential());
    player.setRegisteredAt(Instant.now());
    player.setAcknowledgedTermsAt(Instant.now());
    users.saveAndFlush(player);
  }

  private String requestLink() throws Exception {
    mvc.perform(
            post("/account/connect-email")
                .with(user(new CustomUserDetails(player)))
                .with(csrf())
                .with(
                    request -> {
                      request.setRemoteAddr("email-test-" + player.getId());
                      return request;
                    })
                .param("email", address))
        .andExpect(status().isOk())
        .andExpect(model().attribute("emailSent", true));
    ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
    verify(mail, atLeastOnce()).sendHtml(eq(address), anyString(), html.capture());
    var matcher = Pattern.compile("\\?token=([A-Za-z0-9_-]{43})").matcher(html.getValue());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private MockHttpSession open(String token) throws Exception {
    return (MockHttpSession)
        mvc.perform(get("/connect-email").param("token", token))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/connect-email"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andReturn()
            .getRequest()
            .getSession();
  }

  @Test
  void reminderWaitsAWeekAndDoesNotDependOnSessionEnding() throws Exception {
    assertThat(emails.shouldRemind(player)).isFalse();
    mvc.perform(get("/account-menu").with(user(new CustomUserDetails(player))))
        .andExpect(status().isOk())
        .andExpect(model().attribute("showConnectEmailReminder", false));
    player.setRegisteredAt(Instant.now().minus(Duration.ofDays(7)).minusSeconds(1));
    users.saveAndFlush(player);
    mvc.perform(get("/account-menu").with(user(new CustomUserDetails(player))))
        .andExpect(status().isOk())
        .andExpect(model().attribute("showConnectEmailReminder", true))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("data-connect-email-reminder")));
    player.setNameOnlyAccount(false);
    users.saveAndFlush(player);
    assertThat(emails.shouldRemind(player)).isFalse();
  }

  @Test
  void notNowHidesTheReminderForAnotherWeekAcrossVisits() throws Exception {
    player.setRegisteredAt(Instant.now().minus(Duration.ofDays(10)));
    users.saveAndFlush(player);
    mvc.perform(
            post("/account/connect-email/dismiss")
                .with(user(new CustomUserDetails(player)))
                .with(csrf()))
        .andExpect(redirectedUrl("/home"));
    assertThat(emails.shouldRemind(player)).isFalse();
    var marker =
        markers.findByUserIdAndMarkerKey(player.getId(), "connect_email_reminder").orElseThrow();
    marker.setCompletedAt(Instant.now().minus(Duration.ofDays(7)).minusSeconds(1));
    markers.saveAndFlush(marker);
    assertThat(emails.shouldRemind(player)).isTrue();
  }

  @Test
  void accountSettingsOffersEmailBeforeTheReminderAndOtherPlayersCannotAccessIt() throws Exception {
    mvc.perform(get("/account/connect-email").with(user(new CustomUserDetails(player))))
        .andExpect(status().isOk())
        .andExpect(view().name("auth/connect-email"));
    mvc.perform(get("/account/connect-email")).andExpect(status().is3xxRedirection());
    mvc.perform(
            post("/account/connect-email")
                .with(user(new CustomUserDetails(player)))
                .param("email", address))
        .andExpect(status().isForbidden());
    verifyNoInteractions(mail);
  }

  @Test
  void emailIsOnlyPendingUntilVerifiedAndPrivateTokenIsRemovedBeforeRendering() throws Exception {
    String original = player.getEmail();
    String token = requestLink();
    assertThat(player.getEmail()).isEqualTo(original);
    assertThat(player.isNameOnlyAccount()).isTrue();
    assertThat(player.getConnectEmailTokenHash()).isEqualTo(PlayerAccessService.hash(token));
    assertThat(player.getConnectEmailPending()).isEqualTo(address);
    var browser = open(token);
    mvc.perform(get("/connect-email").session(browser))
        .andExpect(status().isOk())
        .andExpect(model().attribute("connectionAvailable", true))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(token))));
    assertThat(player.isNameOnlyAccount()).isTrue();
  }

  @Test
  void connectingOnAnotherBrowserPreservesPlayerAndMembershipAndEnablesNormalLoginAndReset()
      throws Exception {
    var group = new LadderConfig();
    group.setTitle("Email test session");
    group.setType(LadderConfig.Type.SESSION);
    group.setOwnerUserId(player.getId());
    sessions.saveAndFlush(group);
    var membership = new LadderMembership();
    membership.setUserId(player.getId());
    membership.setLadderConfig(group);
    membership.setState(LadderMembership.State.ACTIVE);
    membership.setRole(LadderMembership.Role.MEMBER);
    memberships.saveAndFlush(membership);
    String publicId = player.getPublicCode();
    String recovery = players.createRecoveryCode(player.getId());
    String token = requestLink();
    var browser = open(token);
    mvc.perform(
            post("/connect-email")
                .session(browser)
                .with(csrf())
                .param("password", "Playing123")
                .param("confirm_password", "Playing123")
                .param("userId", "1")
                .param("isAdmin", "true"))
        .andExpect(redirectedUrl("/home"))
        .andExpect(cookie().httpOnly("remember-me", true));
    em.flush();
    em.clear();
    User saved = users.findByEmail(address);
    assertThat(saved.getId()).isEqualTo(player.getId());
    assertThat(saved.getPublicCode()).isEqualTo(publicId);
    assertThat(saved.isNameOnlyAccount()).isFalse();
    assertThat(saved.isAdmin()).isFalse();
    assertThat(saved.getConnectEmailTokenHash()).isNull();
    assertThat(saved.getRecoveryCodeHash()).isNull();
    assertThat(encoder.matches("Playing123", saved.getPassword())).isTrue();
    assertThat(memberships.findByLadderConfigIdAndUserId(group.getId(), saved.getId())).isPresent();
    assertThatThrownBy(() -> players.recover(recovery))
        .isInstanceOf(IllegalArgumentException.class);
    mvc.perform(post("/login").with(csrf()).param("user", address).param("password", "Playing123"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/home"));
    assertThat(
            resets.issueResetPasswordToken(
                address, "email-connected-reset", Instant.now().plusSeconds(600)))
        .isNotNull();
    assertThatThrownBy(() -> emails.preview(token)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anotherSignedInAccountCannotBeChangedOrSilentlySwitched() throws Exception {
    String token = requestLink();
    var other = new User();
    other.setEmail("other" + address);
    other.setNickName("OtherEmailPlayer");
    other.setPassword("unused");
    other.setAcknowledgedTermsAt(Instant.now());
    users.saveAndFlush(other);
    mvc.perform(
            post("/connect-email")
                .session(open(token))
                .with(user(new CustomUserDetails(other)))
                .with(csrf())
                .param("password", "Playing123")
                .param("confirm_password", "Playing123"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("connectionAvailable", false));
    assertThat(player.isNameOnlyAccount()).isTrue();
    assertThat(other.getEmail()).isNotEqualTo(address);
  }

  @Test
  void expiredTokensFailAndRequestingAnotherEmailInvalidatesThePreviousLink() throws Exception {
    String old = requestLink();
    String fresh = requestLink();
    assertThatThrownBy(() -> emails.preview(old)).isInstanceOf(IllegalArgumentException.class);
    player.setConnectEmailExpiresAt(Instant.now().minusSeconds(1));
    users.saveAndFlush(player);
    mvc.perform(get("/connect-email").session(open(fresh)))
        .andExpect(status().isOk())
        .andExpect(model().attribute("connectionAvailable", false));
    assertThat(player.isNameOnlyAccount()).isTrue();
  }

  @Test
  void existingEmailIsNeverClaimedOrMergedEvenIfItRegistersWhileTheLinkIsPending()
      throws Exception {
    String token = requestLink();
    var other = new User();
    other.setEmail(address);
    other.setNickName("RegisteredEmailPlayer");
    other.setPassword("original");
    users.saveAndFlush(other);
    assertThatThrownBy(() -> emails.complete(token, player.getId(), "Playing123", "Playing123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("existing account");
    assertThat(other.getPassword()).isEqualTo("original");
    assertThat(player.isNameOnlyAccount()).isTrue();
    reset(mail);
    assertThatThrownBy(
            () -> emails.request(player.getId(), address, "http://localhost/connect-email"))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(mail);
  }

  @Test
  void weakOrMismatchingPasswordsDoNotUseTheLinkAndCsrfIsRequired() throws Exception {
    String token = requestLink();
    var browser = open(token);
    mvc.perform(
            post("/connect-email")
                .session(browser)
                .param("password", "Playing123")
                .param("confirm_password", "Playing123"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/connect-email")
                .session(browser)
                .with(csrf())
                .param("password", "weak")
                .param("confirm_password", "weak"))
        .andExpect(model().attribute("connectionAvailable", true));
    mvc.perform(
            post("/connect-email")
                .session(browser)
                .with(csrf())
                .param("password", "Playing123")
                .param("confirm_password", "Playing456"))
        .andExpect(model().attribute("connectionAvailable", true));
    assertThat(emails.preview(token).getId()).isEqualTo(player.getId());
  }

  @Test
  void anUntrustedHostCannotBeUsedForTheEmailLink() throws Exception {
    mvc.perform(
            post("/account/connect-email")
                .with(user(new CustomUserDetails(player)))
                .with(csrf())
                .with(
                    request -> {
                      request.setServerName("attacker.example");
                      request.setRemoteAddr("host-test-" + player.getId());
                      return request;
                    })
                .param("email", address))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("connectError"));
    verifyNoInteractions(mail);
    assertThat(player.getConnectEmailPending()).isNull();
  }
}
