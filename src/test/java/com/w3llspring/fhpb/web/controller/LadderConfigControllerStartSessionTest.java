package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.w3llspring.fhpb.web.controller.competition.LadderConfigController;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.CompetitionSeasonService;
import com.w3llspring.fhpb.web.service.LadderConfigService;
import com.w3llspring.fhpb.web.service.competition.GroupAdministrationOperations;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class LadderConfigControllerStartSessionTest {

  private LadderConfigController controller;

  @BeforeEach
  void setUp() {
    LadderConfigService service =
        new LadderConfigService(null, null, null, null, 10, null, null) {
          @Override
          public LadderConfig findReusableSessionConfig(Long userId) {
            return null;
          }

          @Override
          public LadderConfig createSessionConfig(
              Long ownerUserId, String title, LadderSeason targetSeason) {
            LadderConfig created = new LadderConfig();
            created.setId(99L);
            return created;
          }
        };

    controller =
        new LadderConfigController(
            null,
            null,
            service,
            mock(GroupAdministrationOperations.class),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            20);

    CompetitionSeasonService competitionSeasonService =
        new CompetitionSeasonService(null, null, null) {
          @Override
          public LadderSeason resolveActiveCompetitionSeason() {
            LadderSeason season = new LadderSeason();
            org.springframework.test.util.ReflectionTestUtils.setField(season, "id", 77L);
            return season;
          }
        };
    org.springframework.test.util.ReflectionTestUtils.setField(
        controller, "competitionSeasonService", competitionSeasonService);
  }

  @Test
  void startSession_redirectsToCreatedSession() {
    User user = new User();
    user.setId(5L);
    user.setNickName("Tester");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    String view = controller.startSession(auth, "/home", redirectAttributes);

    assertThat(view).isEqualTo("redirect:/groups/99");
    assertThat(redirectAttributes.getFlashAttributes().get("toastMessage"))
        .isEqualTo("Match session created.");
  }

  @Test
  void startSession_redirectsToExistingReusableSession() {
    LadderConfig existingSession = new LadderConfig();
    existingSession.setId(88L);
    existingSession.setType(LadderConfig.Type.SESSION);

    LadderConfigService serviceWithExistingSession =
        new LadderConfigService(null, null, null, null, 10, null, null) {
          @Override
          public LadderConfig findReusableSessionConfig(Long userId) {
            return existingSession;
          }

          @Override
          public LadderConfig createSessionConfig(
              Long ownerUserId, String title, LadderSeason targetSeason) {
            throw new AssertionError(
                "createSessionConfig should not be called when a reusable session exists");
          }
        };

    controller =
        new LadderConfigController(
            null,
            null,
            serviceWithExistingSession,
            mock(GroupAdministrationOperations.class),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            20);

    User user = new User();
    user.setId(5L);
    user.setNickName("Tester");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    String view = controller.startSession(auth, "/home", redirectAttributes);

    assertThat(view).isEqualTo("redirect:/groups/88");
    assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("toastMessage");
  }

  @Test
  void startSession_redirectsBackToCompetitionWhenCreationFails() {
    LadderConfigService failingService =
        new LadderConfigService(null, null, null, null, 10, null, null) {
          @Override
          public LadderConfig findReusableSessionConfig(Long userId) {
            return null;
          }

          @Override
          public LadderConfig createSessionConfig(
              Long ownerUserId, String title, LadderSeason targetSeason) {
            throw new IllegalStateException(
                "You can have at most 3 active match sessions at a time.");
          }
        };

    controller =
        new LadderConfigController(
            null,
            null,
            failingService,
            mock(GroupAdministrationOperations.class),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            20);

    CompetitionSeasonService competitionSeasonService =
        new CompetitionSeasonService(null, null, null) {
          @Override
          public LadderSeason resolveActiveCompetitionSeason() {
            LadderSeason season = new LadderSeason();
            org.springframework.test.util.ReflectionTestUtils.setField(season, "id", 77L);
            return season;
          }
        };
    org.springframework.test.util.ReflectionTestUtils.setField(
        controller, "competitionSeasonService", competitionSeasonService);

    User user = new User();
    user.setId(5L);
    user.setNickName("Tester");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    String view = controller.startSession(auth, "/competition", redirectAttributes);

    assertThat(view).isEqualTo("redirect:/competition");
    assertThat(redirectAttributes.getFlashAttributes().get("toastMessage"))
        .isEqualTo("You can have at most 3 active match sessions at a time.");
  }

  @Test
  void startSession_preservesCompetitionSessionPickerAsFailureReturnTarget() {
    LadderConfigService failingService =
        new LadderConfigService(null, null, null, null, 10, null, null) {
          @Override
          public LadderConfig findReusableSessionConfig(Long userId) {
            return null;
          }

          @Override
          public LadderConfig createSessionConfig(
              Long ownerUserId, String title, LadderSeason targetSeason) {
            throw new IllegalStateException("Competition season is unavailable.");
          }
        };

    controller =
        new LadderConfigController(
            null,
            null,
            failingService,
            mock(GroupAdministrationOperations.class),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            20);

    CompetitionSeasonService competitionSeasonService =
        new CompetitionSeasonService(null, null, null) {
          @Override
          public LadderSeason resolveActiveCompetitionSeason() {
            return null;
          }
        };
    org.springframework.test.util.ReflectionTestUtils.setField(
        controller, "competitionSeasonService", competitionSeasonService);

    User user = new User();
    user.setId(5L);
    user.setNickName("Tester");
    var auth =
        new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    String view = controller.startSession(auth, "/competition/sessions", redirectAttributes);

    assertThat(view).isEqualTo("redirect:/competition/sessions");
    assertThat(redirectAttributes.getFlashAttributes().get("toastMessage"))
        .isEqualTo("Competition season is unavailable. Try again after it has been created.");
  }
}
