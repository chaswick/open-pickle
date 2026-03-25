package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.competition.LadderConfigController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationOperations;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
class LadderConfigControllerLeaveTest {

  @Mock private LadderConfigRepository configs;
  @Mock private GroupAdministrationOperations groupAdministration;

  private LadderConfigController controller;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(AuthenticatedUserSupport.class, "authenticatedUserService", null);
    controller =
        new LadderConfigController(
            null, null, null, groupAdministration, configs, null, null, null, null, null, null, 20);
  }

  @Test
  void leave_sessionRedirectsToHome() {
    User user = new User();
    user.setId(5L);
    user.setNickName("Tester");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    LadderConfig session = new LadderConfig();
    session.setId(99L);
    session.setType(LadderConfig.Type.SESSION);

    when(configs.findById(99L)).thenReturn(Optional.of(session));

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    String view = controller.leave(99L, 77L, auth, redirectAttributes);

    assertThat(view).isEqualTo("redirect:/home");
    assertThat(redirectAttributes.getFlashAttributes().get("toastMessage"))
        .isEqualTo("You left the session.");
  }
}
