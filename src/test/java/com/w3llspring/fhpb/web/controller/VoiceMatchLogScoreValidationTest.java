package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.w3llspring.fhpb.web.controller.match.VoiceMatchLogController;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class VoiceMatchLogScoreValidationTest {

  @Test
  void confirmRejectsWinnerBelowConfiguredMinimum() {
    VoiceMatchLogController controller = makeController();
    Authentication authentication = makeAuthentication(1L);

    VoiceMatchLogController.ConfirmRequest request = baseRequest();
    request.setScoreTeamA(10);
    request.setScoreTeamB(8);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> controller.confirm(request, authentication));

    assertThat(exception.getReason()).contains("at least 11 points");
  }

  @Test
  void confirmRejectsScoresAboveConfiguredCap() {
    VoiceMatchLogController controller = makeController();
    Authentication authentication = makeAuthentication(1L);

    VoiceMatchLogController.ConfirmRequest request = baseRequest();
    request.setScoreTeamA(36);
    request.setScoreTeamB(34);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> controller.confirm(request, authentication));

    assertThat(exception.getReason()).contains("cannot exceed 35 points");
  }

  @Test
  void confirmRejectsTiedScores() {
    VoiceMatchLogController controller = makeController();
    Authentication authentication = makeAuthentication(1L);

    VoiceMatchLogController.ConfirmRequest request = baseRequest();
    request.setScoreTeamA(7);
    request.setScoreTeamB(7);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> controller.confirm(request, authentication));

    assertThat(exception.getReason()).contains("cannot be tied");
  }

  private VoiceMatchLogController makeController() {
    MatchValidationService matchValidationService = new MatchValidationService(null, null);
    return new VoiceMatchLogController(
        null, null, null, null, null, null, matchValidationService, null, null, null);
  }

  private Authentication makeAuthentication(Long userId) {
    User user = new User();
    user.setId(userId);
    user.setNickName("tester");
    return new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of());
  }

  private VoiceMatchLogController.ConfirmRequest baseRequest() {
    VoiceMatchLogController.ConfirmRequest request = new VoiceMatchLogController.ConfirmRequest();
    request.setSeasonId(1L);
    request.setTeamAUserIds(List.of(1L, 2L));
    request.setTeamBUserIds(List.of(3L, 4L));
    return request;
  }
}
