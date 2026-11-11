package com.w3llspring.fhpb.web.service.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.w3llspring.fhpb.web.db.*;
import com.w3llspring.fhpb.web.model.*;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "fhpb.security.remember-me.key=player-access-integration-test-key",
      "server.servlet.session.cookie.secure=false",
      "fhpb.security.guest-access.enabled=true",
      "spring.datasource.url=jdbc:h2:mem:playeraccess;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlayerAccessIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired LadderConfigRepository sessions;
  @Autowired LadderMembershipRepository memberships;
  @Autowired SessionJoinRequestRepository joinRequests;
  @Autowired PlayerAccessService players;
  @Autowired RegistrationFormTokenService forms;
  @Autowired UserPasswordResetService passwordReset;
  @Autowired com.w3llspring.fhpb.web.service.user.UserIdentityService identities;
  private User owner;
  private LadderConfig session;

  @BeforeEach
  void setup() {
    owner = new User();
    owner.setEmail(UUID.randomUUID().toString().substring(0, 8) + "@test.local");
    owner.setNickName("AccessHost");
    owner.setPassword("unused");
    owner.setAcknowledgedTermsAt(Instant.now());
    users.saveAndFlush(owner);
    session = new LadderConfig();
    session.setOwnerUserId(owner.getId());
    session.setTitle("Friends on Sunday");
    session.setType(LadderConfig.Type.SESSION);
    session.setSessionPlayMode(LadderConfig.SessionPlayMode.MATCHUPS);
    session.setInviteCode(
        "FRESH-COURT-"
            + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT));
    session.setExpiresAt(Instant.now().plusSeconds(172800));
    session = sessions.saveAndFlush(session);
    var member = new LadderMembership();
    member.setLadderConfig(session);
    member.setUserId(owner.getId());
    member.setRole(LadderMembership.Role.ADMIN);
    member.setState(LadderMembership.State.ACTIVE);
    memberships.saveAndFlush(member);
  }

  private MvcResult join(String name) throws Exception {
    return mvc.perform(
            post("/join/session")
                .with(csrf())
                .param("inviteCode", session.getInviteCode())
                .param("name", name)
                .param("acceptTerms", "true")
                .param("formToken", forms.issueToken()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/groups/" + session.getId()))
        .andReturn();
  }

  private User joined(MvcResult result) {
    var context =
        (org.springframework.security.core.context.SecurityContext)
            result
                .getRequest()
                .getSession()
                .getAttribute(
                    org.springframework.security.web.context.HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY);
    return ((CustomUserDetails) context.getAuthentication().getPrincipal()).getUserObject();
  }

  @Test
  void sharedLinksShowTheNameCardWithoutRequiringLogin() throws Exception {
    mvc.perform(
            get("/groups/join")
                .param("inviteCode", session.getInviteCode())
                .param("autoJoin", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            redirectedUrl("/join/session?inviteCode=" + session.getInviteCode() + "&direct=true"));
    mvc.perform(get("/join/session").param("inviteCode", session.getInviteCode()))
        .andExpect(status().isOk())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("What should we call you?")))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void nameOnlyJoinCreatesARegularParticipantAndAOneYearHttpOnlyCookie() throws Exception {
    var result = join("Yuki");
    User user = joined(result);
    assertThat(user.isNameOnlyAccount()).isTrue();
    assertThat(user.isAdmin()).isFalse();
    assertThat(user.getNickName()).isEqualTo("Yuki");
    assertThat(user.getPublicCode()).matches("[a-z]{3,6}-[a-z]{3,6}-[a-z]{3,6}");
    assertThat(user.getAcknowledgedTermsAt()).isNotNull();
    assertThat(new BCryptPasswordEncoder().matches(user.getPassword(), user.getPassword()))
        .isFalse();
    assertThat(
            memberships
                .findByLadderConfigIdAndUserId(session.getId(), user.getId())
                .orElseThrow()
                .getRole())
        .isEqualTo(LadderMembership.Role.MEMBER);
    Cookie cookie = result.getResponse().getCookie("remember-me");
    assertThat(cookie).isNotNull();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getMaxAge()).isEqualTo(365 * 86400);
    assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
  }

  @Test
  void namesNeverClaimAnExistingIdentityOrPrivileges() throws Exception {
    var result =
        mvc.perform(
                post("/join/session")
                    .with(csrf())
                    .param("inviteCode", session.getInviteCode())
                    .param("name", owner.getNickName())
                    .param("acceptTerms", "true")
                    .param("formToken", forms.issueToken())
                    .param("id", owner.getId().toString())
                    .param("isAdmin", "true")
                    .param("nameOnlyAccount", "false"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    User created = joined(result);
    assertThat(created.getId()).isNotEqualTo(owner.getId());
    assertThat(created.getNickName()).isEqualTo("AccessHost");
    assertThat(created.getPublicCode()).isNotEqualTo(owner.getPublicCode());
    assertThat(created.isAdmin()).isFalse();
    assertThat(created.isNameOnlyAccount()).isTrue();
  }

  @Test
  void repeatedNamesKeepSeparateMemberIdsAndAccounts() throws Exception {
    User first = joined(join("Alex"));
    User second = joined(join("Alex"));
    assertThat(first.getNickName()).isEqualTo("Alex");
    assertThat(second.getNickName()).isEqualTo("Alex");
    assertThat(first.getId()).isNotEqualTo(second.getId());
    assertThat(first.getPublicCode()).isNotEqualTo(second.getPublicCode());
    assertThat(first.getEmail()).isNotEqualTo(second.getEmail());
    assertThat(users.findByPublicCode(first.getPublicCode()).orElseThrow().getId())
        .isEqualTo(first.getId());
    assertThat(users.findByPublicCode(second.getPublicCode()).orElseThrow().getId())
        .isEqualTo(second.getId());
    var renamed =
        identities.changeDisplayName(
            first.getId(),
            owner.getNickName(),
            first.getId(),
            Instant.now(),
            java.time.Duration.ZERO);
    assertThat(renamed.status())
        .isEqualTo(
            com.w3llspring.fhpb.web.service.user.UserIdentityService.DisplayNameChangeStatus
                .CHANGED);
    assertThat(users.findById(first.getId()).orElseThrow().getNickName())
        .isEqualTo(owner.getNickName());
    assertThat(users.findById(second.getId()).orElseThrow().getNickName()).isEqualTo("Alex");
  }

  @Test
  void quickJoinRetriesAnOccupiedMemberCodeWithoutDuplicatingTheAccountOrMembership()
      throws Exception {
    long accountsBefore = users.count();
    long membersBefore = memberships.count();
    try (var codes =
        org.mockito.Mockito.mockStatic(
            com.w3llspring.fhpb.web.service.user.UserPublicCodeGenerator.class,
            org.mockito.Mockito.CALLS_REAL_METHODS)) {
      codes
          .when(com.w3llspring.fhpb.web.service.user.UserPublicCodeGenerator::nextCode)
          .thenReturn(owner.getPublicCode(), "calm-ruby-paddle");
      User created = joined(join("Alex"));
      assertThat(created.getPublicCode()).isEqualTo("calm-ruby-paddle");
      assertThat(created.getId()).isNotEqualTo(owner.getId());
      assertThat(users.count()).isEqualTo(accountsBefore + 1);
      assertThat(memberships.count()).isEqualTo(membersBefore + 1);
      assertThat(users.findById(owner.getId()).orElseThrow().getPublicCode())
          .isEqualTo(owner.getPublicCode());
    }
  }

  @Test
  void aReturningBrowserRestoresTheSameAccountWithoutAnHttpSession() throws Exception {
    var result = join("ReturnPlayer");
    User user = joined(result);
    mvc.perform(get("/account-menu").cookie(result.getResponse().getCookie("remember-me")))
        .andExpect(status().isOk())
        .andExpect(authenticated().withUsername(user.getEmail()).withRoles("USER"))
        .andExpect(cookie().maxAge("remember-me", 365 * 86400));
    assertThat(users.findByEmail(user.getEmail()).getId()).isEqualTo(user.getId());
  }

  @Test
  void tamperedRememberedCredentialsDoNotAuthenticate() throws Exception {
    Cookie valid = join("TamperTest").getResponse().getCookie("remember-me");
    mvc.perform(
            get("/account-menu").cookie(new Cookie("remember-me", valid.getValue() + "tampered")))
        .andExpect(status().is3xxRedirection())
        .andExpect(unauthenticated());
  }

  @Test
  void logoutRevokesTheOldCookieEvenAfterTheHttpSessionHasExpired() throws Exception {
    Cookie valid = join("LogoutTest").getResponse().getCookie("remember-me");
    mvc.perform(post("/logout").with(csrf()).cookie(valid)).andExpect(status().is3xxRedirection());
    mvc.perform(get("/account-menu").cookie(valid))
        .andExpect(status().is3xxRedirection())
        .andExpect(unauthenticated());
  }

  @Test
  void publicMemberIdCannotRecoverAnAccountButThePrivateCodeCan() throws Exception {
    User user = joined(join("RecoverTest"));
    String code = players.createRecoveryCode(user.getId());
    assertThat(user.getRecoveryCodeHash()).isNotEqualTo(code);
    mvc.perform(post("/recover-player").with(csrf()).param("recoveryCode", user.getPublicCode()))
        .andExpect(status().isOk())
        .andExpect(unauthenticated());
    mvc.perform(post("/recover-player").with(csrf()).param("recoveryCode", code))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account-menu"))
        .andExpect(authenticated().withUsername(user.getEmail()));
  }

  @Test
  void replacingARecoveryCodeInvalidatesTheOldOne() throws Exception {
    User user = joined(join("RekeyTest"));
    String first = players.createRecoveryCode(user.getId());
    String second = players.createRecoveryCode(user.getId());
    assertThatThrownBy(() -> players.recover(first)).isInstanceOf(IllegalArgumentException.class);
    assertThat(players.recover(second).getId()).isEqualTo(user.getId());
  }

  @Test
  void recoveringFromAnInviteReturnsToThatInviteWithoutCreatingAnotherPlayer() throws Exception {
    User user = joined(join("InviteRecovery"));
    String code = players.createRecoveryCode(user.getId());
    long before = users.count();
    var invite =
        mvc.perform(get("/join/session").param("inviteCode", session.getInviteCode()))
            .andExpect(status().isOk())
            .andReturn();
    var recovered =
        mvc.perform(
                post("/recover-player")
                    .with(csrf())
                    .session((MockHttpSession) invite.getRequest().getSession())
                    .param("recoveryCode", code))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    assertThat(recovered.getResponse().getRedirectedUrl()).startsWith("/join/session?");
    assertThat(users.count()).isEqualTo(before);
  }

  @Test
  void recoveryDownloadIsPrivateAndBelongsOnlyToTheSignedInPlayer() throws Exception {
    var result = join("DownloadTest");
    mvc.perform(
            post("/account/recovery-code")
                .with(csrf())
                .session((MockHttpSession) result.getRequest().getSession())
                .param("userId", owner.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=pickleball-recovery-code.txt"))
        .andExpect(content().contentTypeCompatibleWith("text/plain"));
    assertThat(users.findById(owner.getId()).orElseThrow().getRecoveryCodeHash()).isNull();
  }

  @Test
  void noEmailResetIsSentToTheInternalPlaceholderAddress() throws Exception {
    User user = joined(join("NoEmail"));
    assertThat(
            passwordReset.issueResetPasswordToken(
                user.getEmail(), "secret-reset", Instant.now().plusSeconds(3600)))
        .isNull();
  }

  @Test
  void invalidExpiredOrDisabledInvitesNeverCreateAccounts() throws Exception {
    long before = users.count();
    session.setExpiresAt(Instant.now().minusSeconds(1));
    session = sessions.saveAndFlush(session);
    mvc.perform(
            post("/join/session")
                .with(csrf())
                .param("inviteCode", session.getInviteCode())
                .param("name", "Late")
                .param("acceptTerms", "true")
                .param("formToken", forms.issueToken()))
        .andExpect(status().isOk());
    assertThat(users.count()).isEqualTo(before);
    session.setExpiresAt(Instant.now().plusSeconds(3600));
    String oldInvite = session.getInviteCode();
    session.setInviteCode(null);
    session = sessions.saveAndFlush(session);
    assertThatThrownBy(() -> players.createAndJoin(oldInvite, "Late", true, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(users.count()).isEqualTo(before);
  }

  @Test
  void csrfTermsAndSignedFormChecksAreRequired() throws Exception {
    long before = users.count();
    mvc.perform(
            post("/join/session").param("inviteCode", session.getInviteCode()).param("name", "Bad"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/join/session")
                .with(csrf())
                .param("inviteCode", session.getInviteCode())
                .param("name", "Bad")
                .param("formToken", forms.issueToken()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/join/session")
                .with(csrf())
                .param("inviteCode", session.getInviteCode())
                .param("name", "Bad")
                .param("acceptTerms", "true")
                .param("formToken", "forged"))
        .andExpect(status().isOk());
    assertThat(users.count()).isEqualTo(before);
  }

  @Test
  void codeBasedRequestsKeepOrganizerApproval() throws Exception {
    var result =
        mvc.perform(
                post("/join/session")
                    .with(csrf())
                    .param("inviteCode", session.getInviteCode())
                    .param("direct", "false")
                    .param("name", "ApprovalPlayer")
                    .param("acceptTerms", "true")
                    .param("formToken", forms.issueToken()))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    User user = joined(result);
    assertThat(memberships.findByLadderConfigIdAndUserId(session.getId(), user.getId())).isEmpty();
    assertThat(result.getResponse().getRedirectedUrl()).startsWith("/groups/join-requests/");
  }

  @Test
  void aFullSessionCreatesNoOrphanAccount() {
    for (int i = 0; i < 31; i++) {
      User user = new User();
      user.setEmail("full-" + i + "@test.local");
      user.setNickName("Full" + i);
      user.setPassword("unused");
      users.saveAndFlush(user);
      var member = new LadderMembership();
      member.setLadderConfig(session);
      member.setUserId(user.getId());
      member.setRole(LadderMembership.Role.MEMBER);
      member.setState(LadderMembership.State.ACTIVE);
      memberships.saveAndFlush(member);
    }
    long before = users.count();
    assertThatThrownBy(() -> players.createAndJoin(session.getInviteCode(), "Overflow", true, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("full");
    assertThat(users.count()).isEqualTo(before);
  }
}
