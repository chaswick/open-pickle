package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.controller.match.VoiceMatchLogController;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.LadderSecurityService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.MatchFactory;
import com.w3llspring.fhpb.web.service.SeasonNameGenerator;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpreter;
import com.w3llspring.fhpb.web.service.scoring.BalancedV1LadderScoringAlgorithm;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms;
import com.w3llspring.fhpb.web.service.scoring.MarginCurveV1LadderScoringAlgorithm;
import com.w3llspring.fhpb.web.service.trophy.TrophyAwardService;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VoiceMatchLogControllerSessionSourceTest {

  @Mock private SpokenMatchInterpreter defaultInterpreter;
  @Mock private SpokenMatchInterpreter spanishInterpreter;
  @Mock private UserRepository userRepository;
  @Mock private LadderSeasonRepository seasonRepository;
  @Mock private LadderConfigRepository ladderConfigRepository;
  @Mock private LadderMembershipRepository ladderMembershipRepository;

  private VoiceMatchLogController controller;
  private Match capturedMatch;
  private User currentUser;
  private LadderSeason competitionSeason;

  @BeforeEach
  void setUp() {
    capturedMatch = null;

    MatchConfirmationService confirmationService =
        new MatchConfirmationService() {
          @Override
          public void createRequests(Match match) {}

          @Override
          public boolean confirmMatch(long matchId, long userId) {
            return false;
          }

          @Override
          public List<com.w3llspring.fhpb.web.model.MatchConfirmation> pendingForUser(long userId) {
            return List.of();
          }

          @Override
          public void autoConfirmOverdue() {}

          @Override
          public void rebuildConfirmationRequests(Match match) {}
        };

    MatchFactory matchFactory =
        new MatchFactory(null, confirmationService) {
          @Override
          public Match createMatch(Match match) {
            ReflectionTestUtils.setField(match, "id", 101L);
            capturedMatch = match;
            return match;
          }
        };

    LadderV2Service ladderV2Service =
        new LadderV2Service(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new SeasonNameGenerator(),
            new LadderScoringAlgorithms(
                List.of(
                    new MarginCurveV1LadderScoringAlgorithm(),
                    new BalancedV1LadderScoringAlgorithm())),
            null,
            null,
            null,
            null) {
          @Override
          public void applyMatch(Match match) {}
        };

    TrophyAwardService trophyAwardService =
        new TrophyAwardService(null, null, null, null, null, null, null, null) {
          @Override
          public void evaluateMatch(Match match) {}
        };

    MatchValidationService matchValidationService =
        new MatchValidationService(seasonRepository, ladderMembershipRepository);
    LadderSecurityService ladderSecurityService =
        new LadderSecurityService() {
          @Override
          public boolean validateMatchLogging(LadderConfig ladder, User user, String passphrase) {
            return true;
          }

          @Override
          public String regeneratePassphrase(User user) {
            return null;
          }
        };
    LadderAccessService ladderAccessService =
        new LadderAccessService(seasonRepository, ladderMembershipRepository) {
          @Override
          public boolean isSeasonAdmin(Long seasonId, User user) {
            return false;
          }
        };

    controller =
        new VoiceMatchLogController(
            defaultInterpreter,
            spanishInterpreter,
            userRepository,
            seasonRepository,
            ladderV2Service,
            trophyAwardService,
            matchValidationService,
            ladderSecurityService,
            ladderAccessService,
            matchFactory);
    ReflectionTestUtils.setField(controller, "ladderConfigRepository", ladderConfigRepository);
    ReflectionTestUtils.setField(
        controller, "ladderMembershipRepository", ladderMembershipRepository);

    currentUser = new User();
    ReflectionTestUtils.setField(currentUser, "id", 1L);
    currentUser.setEmail("user1@test.com");
    currentUser.setNickName("User1");

    LadderConfig competitionConfig = new LadderConfig();
    competitionConfig.setId(1L);
    competitionConfig.setType(LadderConfig.Type.COMPETITION);
    competitionConfig.setSecurityLevel(LadderSecurity.NONE);

    competitionSeason = new LadderSeason();
    ReflectionTestUtils.setField(competitionSeason, "id", 1L);
    competitionSeason.setLadderConfig(competitionConfig);
    competitionSeason.setState(LadderSeason.State.ACTIVE);
  }

  @Test
  void confirm_stampsSessionSourceWhenSessionLadderIsProvided() {
    User user2 = user(2L, "User2");
    User user3 = user(3L, "User3");
    User user4 = user(4L, "User4");

    LadderConfig sessionConfig = new LadderConfig();
    sessionConfig.setId(77L);
    sessionConfig.setType(LadderConfig.Type.SESSION);
    sessionConfig.setTargetSeasonId(1L);

    when(seasonRepository.findById(1L)).thenReturn(Optional.of(competitionSeason));
    when(ladderConfigRepository.findById(77L)).thenReturn(Optional.of(sessionConfig));
    when(ladderMembershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            77L, LadderMembership.State.ACTIVE))
        .thenReturn(
            List.of(
                membership(77L, 1L),
                membership(77L, 2L),
                membership(77L, 3L),
                membership(77L, 4L)));
    when(userRepository.findAllById(Arrays.asList(1L, 2L, 3L, 4L)))
        .thenReturn(List.of(currentUser, user2, user3, user4));

    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            new CustomUserDetails(currentUser), null, List.of());

    VoiceMatchLogController.ConfirmRequest request = new VoiceMatchLogController.ConfirmRequest();
    request.setSeasonId(1L);
    request.setLadderConfigId(77L);
    request.setTeamAUserIds(Arrays.asList(1L, 2L));
    request.setTeamBUserIds(Arrays.asList(3L, 4L));
    request.setScoreTeamA(11);
    request.setScoreTeamB(9);

    VoiceMatchLogController.ConfirmResponse response = controller.confirm(request, auth);

    assertThat(response.getMatchId()).isEqualTo(101L);
    assertThat(capturedMatch).isNotNull();
    assertThat(capturedMatch.getSourceSessionConfig()).isEqualTo(sessionConfig);
    assertThat(capturedMatch.getSeason()).isEqualTo(competitionSeason);
  }

  private User user(Long id, String nickname) {
    User user = new User();
    ReflectionTestUtils.setField(user, "id", id);
    user.setEmail("user" + id + "@test.com");
    user.setNickName(nickname);
    return user;
  }

  private LadderMembership membership(Long ladderConfigId, Long userId) {
    LadderMembership membership = new LadderMembership();
    membership.setUserId(userId);
    membership.setState(LadderMembership.State.ACTIVE);
    LadderConfig config = new LadderConfig();
    config.setId(ladderConfigId);
    membership.setLadderConfig(config);
    return membership;
  }
}
