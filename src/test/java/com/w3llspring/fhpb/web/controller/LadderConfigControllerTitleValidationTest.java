package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.competition.LadderConfigController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserDisplayNameAuditRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class LadderConfigControllerTitleValidationTest {

  private LadderConfigController controller;
  private LadderConfigRepository configs;
  private LadderMembershipRepository memberships;
  private GroupAdministrationOperations groupAdministration;

  @BeforeEach
  void setUp() {
    configs = mock(LadderConfigRepository.class);
    memberships = mock(LadderMembershipRepository.class);
    groupAdministration = mock(GroupAdministrationOperations.class);
    controller =
        new LadderConfigController(
            mock(UserRepository.class),
            mock(UserDisplayNameAuditRepository.class),
            null,
            groupAdministration,
            configs,
            null,
            memberships,
            null,
            null,
            null,
            null,
            20);

    User user = new User();
    user.setId(3L);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user), null, java.util.List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void updateTitleRejectsUnsupportedCharacters() {
    when(groupAdministration.updateTitle(10L, 3L, "<b>Group</b>"))
        .thenThrow(new IllegalArgumentException("Group name contains unsupported characters."));
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
    String view =
        controller.updateTitle(
            10L,
            "<b>Group</b>",
            SecurityContextHolder.getContext().getAuthentication(),
            redirectAttributes);

    assertThat(view).isEqualTo("redirect:/groups/10");
    assertThat(redirectAttributes.getFlashAttributes().get("toastLevel")).isEqualTo("danger");
    assertThat(redirectAttributes.getFlashAttributes().get("toastMessage"))
        .isEqualTo("Group name contains unsupported characters.");
    verify(configs, never()).saveAndFlush(any());
  }
}
