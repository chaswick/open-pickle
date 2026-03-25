package com.w3llspring.fhpb.web.service.matchlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.PhoneticMatchingService;
import com.w3llspring.fhpb.web.service.UserCorrectionLearner;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class SpanishSpokenMatchInterpreterTest {

  @Mock private UserRepository userRepository;
  @Mock private LadderSeasonRepository seasonRepository;
  @Mock private LadderMembershipRepository membershipRepository;
  @Mock private CourtNameService courtNameService;
  @Mock private ObjectProvider<SpokenMatchLearningSink> learningSinkProvider;
  @Mock private UserCorrectionLearner correctionLearner;
  @Mock private PhoneticMatchingService phoneticService;

  private SpanishSpokenMatchInterpreter interpreter;

  @BeforeEach
  void setUp() {
    interpreter =
        new SpanishSpokenMatchInterpreter(
            userRepository,
            seasonRepository,
            membershipRepository,
            courtNameService,
            learningSinkProvider,
            correctionLearner,
            phoneticService);
  }

  @Test
  void interpretsSpanishDoublesPhrase() {
    Long ladderConfigId = 77L;

    LadderMembership m1 = new LadderMembership();
    m1.setUserId(1L);
    LadderMembership m2 = new LadderMembership();
    m2.setUserId(2L);
    LadderMembership m3 = new LadderMembership();
    m3.setUserId(3L);
    LadderMembership m4 = new LadderMembership();
    m4.setUserId(4L);

    when(membershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            eq(ladderConfigId), eq(LadderMembership.State.ACTIVE)))
        .thenReturn(List.of(m1, m2, m3, m4));

    User me = user(1L, "Chris", "chris@example.com");
    User dave = user(2L, "Dave", "dave@example.com");
    User angelo = user(3L, "Angelo", "angelo@example.com");
    User alex = user(4L, "Alex", "alex@example.com");

    when(userRepository.findAllById(any())).thenReturn(List.of(me, dave, angelo, alex));

    when(courtNameService.gatherCourtNamesForUsers(any(), eq(ladderConfigId)))
        .thenReturn(
            Map.of(
                1L, Set.of("Chris"),
                2L, Set.of("Dave"),
                3L, Set.of("Angelo"),
                4L, Set.of("Alex")));

    SpokenMatchInterpretationRequest request = new SpokenMatchInterpretationRequest();
    request.setTranscript("Yo y Dave ganamos contra Angelo y Alex once a tres");
    request.setLadderConfigId(ladderConfigId);
    request.setCurrentUserId(1L);

    SpokenMatchInterpretation result = interpreter.interpret(request);

    assertThat(result.getScoreTeamA()).isEqualTo(11);
    assertThat(result.getScoreTeamB()).isEqualTo(3);
    assertThat(result.getWinningTeamIndex()).isEqualTo(0);

    assertThat(result.getTeams()).hasSize(2);
    var winners = result.getTeams().get(0).getPlayers();
    assertThat(winners)
        .extracting(SpokenMatchInterpretation.PlayerResolution::getMatchedUserId)
        .containsExactly(1L, 2L);
    var opponents = result.getTeams().get(1).getPlayers();
    assertThat(opponents)
        .extracting(SpokenMatchInterpretation.PlayerResolution::getMatchedUserId)
        .containsExactly(3L, 4L);
  }

  @Test
  void interpretsSpanishZeroWord() {
    Long ladderConfigId = 78L;

    LadderMembership m1 = new LadderMembership();
    m1.setUserId(10L);
    LadderMembership m2 = new LadderMembership();
    m2.setUserId(11L);
    LadderMembership m3 = new LadderMembership();
    m3.setUserId(12L);
    LadderMembership m4 = new LadderMembership();
    m4.setUserId(13L);

    when(membershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            eq(ladderConfigId), eq(LadderMembership.State.ACTIVE)))
        .thenReturn(List.of(m1, m2, m3, m4));

    User me = user(10L, "Sam", "sam@example.com");
    User ana = user(11L, "Ana", "ana@example.com");
    User pam = user(12L, "Pam", "pam@example.com");
    User robin = user(13L, "Robin", "robin@example.com");

    when(userRepository.findAllById(any())).thenReturn(List.of(me, ana, pam, robin));

    when(courtNameService.gatherCourtNamesForUsers(any(), eq(ladderConfigId)))
        .thenReturn(
            Map.of(
                10L, Set.of("Sam"),
                11L, Set.of("Ana"),
                12L, Set.of("Pam"),
                13L, Set.of("Robin")));

    SpokenMatchInterpretationRequest request = new SpokenMatchInterpretationRequest();
    request.setTranscript("Sam y Ana ganamos a Pam y Robin once a cero");
    request.setLadderConfigId(ladderConfigId);
    request.setCurrentUserId(10L);

    SpokenMatchInterpretation result = interpreter.interpret(request);

    assertThat(result.getScoreTeamA()).isEqualTo(11);
    assertThat(result.getScoreTeamB()).isEqualTo(0);
    assertThat(result.getWinningTeamIndex()).isEqualTo(0);
  }

  @Test
  void interpretsSpanishLossPhraseWithoutExtraOpponents() {
    Long ladderConfigId = 79L;

    LadderMembership m1 = new LadderMembership();
    m1.setUserId(20L);
    LadderMembership m2 = new LadderMembership();
    m2.setUserId(21L);
    LadderMembership m3 = new LadderMembership();
    m3.setUserId(22L);
    LadderMembership m4 = new LadderMembership();
    m4.setUserId(23L);

    when(membershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            eq(ladderConfigId), eq(LadderMembership.State.ACTIVE)))
        .thenReturn(List.of(m1, m2, m3, m4));

    User young = user(20L, "Young", "young@example.com");
    User reporter = user(21L, "Reporter", "reporter@example.com");
    User john = user(22L, "John", "john@example.com");
    User dave = user(23L, "Dave", "dave@example.com");

    when(userRepository.findAllById(any())).thenReturn(List.of(young, reporter, john, dave));

    when(courtNameService.gatherCourtNamesForUsers(any(), eq(ladderConfigId)))
        .thenReturn(
            Map.of(
                20L, Set.of("Young"),
                21L, Set.of("Reporter"),
                22L, Set.of("John"),
                23L, Set.of("Dave")));

    SpokenMatchInterpretationRequest request = new SpokenMatchInterpretationRequest();
    request.setTranscript("Young y yo perdimos ante John y Dave 11 a 9");
    request.setLadderConfigId(ladderConfigId);
    request.setCurrentUserId(21L);

    SpokenMatchInterpretation result = interpreter.interpret(request);

    // Team A is the losing side in this phrase, so score mapping should be 9-11.
    assertThat(result.getScoreTeamA()).isEqualTo(9);
    assertThat(result.getScoreTeamB()).isEqualTo(11);
    assertThat(result.getWinningTeamIndex()).isEqualTo(1);

    var team0 = result.getTeams().get(0).getPlayers();
    var team1 = result.getTeams().get(1).getPlayers();

    assertThat(team0)
        .extracting(SpokenMatchInterpretation.PlayerResolution::getMatchedUserId)
        .containsExactly(20L, 21L);
    assertThat(team1)
        .extracting(SpokenMatchInterpretation.PlayerResolution::getMatchedUserId)
        .containsExactly(22L, 23L);
  }

  private User user(Long id, String nickname, String email) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickname);
    user.setEmail(email);
    return user;
  }
}
