package com.w3llspring.fhpb.web.service.matchlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
class DefaultSpokenMatchInterpreterTest {

  @Mock private UserRepository userRepository;
  @Mock private LadderSeasonRepository seasonRepository;
  @Mock private LadderMembershipRepository membershipRepository;
  @Mock private CourtNameService courtNameService;
  @Mock private ObjectProvider<SpokenMatchLearningSink> learningSinkProvider;
  @Mock private UserCorrectionLearner correctionLearner;
  @Mock private PhoneticMatchingService phoneticService;

  private DefaultSpokenMatchInterpreter interpreter;

  @BeforeEach
  void setUp() {
    interpreter =
        new DefaultSpokenMatchInterpreter(
            userRepository,
            seasonRepository,
            membershipRepository,
            courtNameService,
            learningSinkProvider,
            correctionLearner,
            phoneticService);
  }

  @Test
  void interpretsSimpleDoublesPhrase() {
    Long ladderConfigId = 99L;

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
    request.setTranscript("Me and Dave beat Angelo and Alex 11-8");
    request.setLadderConfigId(ladderConfigId);
    request.setCurrentUserId(1L);

    SpokenMatchInterpretation result = interpreter.interpret(request);

    assertThat(result.getScoreTeamA()).isEqualTo(11);
    assertThat(result.getScoreTeamB()).isEqualTo(8);
    assertThat(result.getWinningTeamIndex()).isEqualTo(0);
    // assertThat(result.isComplete()).isTrue(); // Removed - completeness validation moved to
    // controller layer

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
  void interpretsZeroSynonymsInScores() {
    Long ladderConfigId = 101L;

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

    List<String> zeroWords = List.of("zip", "nothing");
    for (String zeroWord : zeroWords) {
      SpokenMatchInterpretationRequest request = new SpokenMatchInterpretationRequest();
      request.setTranscript("Me and Dave beat Angelo and Alex 11 to " + zeroWord);
      request.setLadderConfigId(ladderConfigId);
      request.setCurrentUserId(1L);

      SpokenMatchInterpretation result = interpreter.interpret(request);

      assertThat(result.getScoreTeamA()).isEqualTo(11);
      assertThat(result.getScoreTeamB()).isEqualTo(0);
      assertThat(result.getWinningTeamIndex()).isEqualTo(0);
    }
  }

  @Test
  void interpretsOrdinalScores() {
    Long ladderConfigId = 42L;

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
    User tim = user(11L, "Tim", "tim@example.com");
    User pam = user(12L, "Pam", "pam@example.com");
    User robin = user(13L, "Robin", "robin@example.com");

    when(userRepository.findAllById(any())).thenReturn(List.of(me, tim, pam, robin));

    when(courtNameService.gatherCourtNamesForUsers(any(), eq(ladderConfigId)))
        .thenReturn(
            Map.of(
                10L, Set.of("Sam"),
                11L, Set.of("Tim"),
                12L, Set.of("Pam"),
                13L, Set.of("Robin")));

    SpokenMatchInterpretationRequest request = new SpokenMatchInterpretationRequest();
    request.setTranscript("Me and Tim beat Pam and Robin 11th to five");
    request.setLadderConfigId(ladderConfigId);
    request.setCurrentUserId(10L);

    SpokenMatchInterpretation result = interpreter.interpret(request);

    assertThat(result.getScoreTeamA()).isEqualTo(11);
    assertThat(result.getScoreTeamB()).isEqualTo(5);
    assertThat(result.getWinningTeamIndex()).isEqualTo(0);

    assertThat(result.getTeams()).hasSize(2);
    var winners = result.getTeams().get(0).getPlayers();
    assertThat(winners)
        .extracting(SpokenMatchInterpretation.PlayerResolution::getMatchedUserId)
        .containsExactly(10L, 11L);

    var opponents = result.getTeams().get(1).getPlayers();
    assertThat(opponents)
        .extracting(SpokenMatchInterpretation.PlayerResolution::getMatchedUserId)
        .containsExactly(12L, 13L);
  }

  @Test
  void doesNotFallbackToGlobalUsersWhenLadderContextMissing() {
    User me = user(1L, "Chris", "chris@example.com");
    when(userRepository.findAllById(any())).thenReturn(List.of(me));
    when(courtNameService.gatherCourtNamesForUsers(any(), eq(null)))
        .thenReturn(Map.of(1L, Set.of("Chris")));

    SpokenMatchInterpretationRequest request = new SpokenMatchInterpretationRequest();
    request.setTranscript("Me and Dave beat Angelo and Alex 11-8");
    request.setCurrentUserId(1L);

    SpokenMatchInterpretation result = interpreter.interpret(request);

    verify(userRepository, never()).findAll();
    assertThat(result.getTeams()).hasSize(2);
    assertThat(result.getTeams().get(0).getPlayers())
        .extracting(SpokenMatchInterpretation.PlayerResolution::getMatchedUserId)
        .containsExactly(1L, null);
    assertThat(result.getTeams().get(1).getPlayers())
        .extracting(SpokenMatchInterpretation.PlayerResolution::getMatchedUserId)
        .containsExactly(null, null);
    assertThat(result.getWarnings())
        .anyMatch(warning -> warning.contains("No close match found for 'Dave'"));
  }

  private User user(Long id, String nickname, String email) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickname);
    user.setEmail(email);
    return user;
  }
}
