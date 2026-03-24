package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.w3llspring.fhpb.web.controller.competition.LadderConfigController;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationOperations;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;

class LadderConfigControllerNewFormTest {

  private LadderConfigController controller;

  @BeforeEach
  void setUp() {
    controller =
        new LadderConfigController(
            null,
            null,
            null,
            mock(GroupAdministrationOperations.class),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            20);
  }

  @Test
  void newForm_usesUtcDateForDefaultSeasonStart() {
    User user = new User();
    user.setId(1L);
    user.setNickName("Tester");
    CustomUserDetails cud = new CustomUserDetails(user);
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(cud, null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();
    LocalDate before = LocalDate.now(ZoneOffset.UTC);

    String view = controller.newForm(model, auth);

    LocalDate after = LocalDate.now(ZoneOffset.UTC);
    assertThat(view).isEqualTo("auth/createLadderConfig");
    assertThat(model.get("defaultSeasonStart")).isIn(before, after);
  }

  @Test
  void newForm_setsTournamentPresetFlag() {
    User user = new User();
    user.setId(1L);
    user.setNickName("Tester");
    CustomUserDetails cud = new CustomUserDetails(user);
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(cud, null, List.of());
    ExtendedModelMap model = new ExtendedModelMap();

    String view =
        controller.newForm(model, auth, "/private-groups", LadderConfig.Type.STANDARD, true);

    assertThat(view).isEqualTo("auth/createLadderConfig");
    assertThat(model.get("tournamentModePreset")).isEqualTo(Boolean.TRUE);
    assertThat(model.get("returnToPath")).isEqualTo("/private-groups");
  }
}
