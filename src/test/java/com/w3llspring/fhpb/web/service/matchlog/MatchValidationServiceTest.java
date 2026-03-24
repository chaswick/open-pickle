package com.w3llspring.fhpb.web.service.matchlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.service.CompetitionAutoModerationService;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.MatchValidationRequest;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.MatchValidationResult;
import com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.PlayerSlot;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchValidationServiceTest {

  @Mock private LadderSeasonRepository seasonRepository;

  @Mock private LadderMembershipRepository membershipRepository;

  private MatchValidationService service;

  @BeforeEach
  void setUp() {
    service = new MatchValidationService(seasonRepository, membershipRepository);
  }

  @Nested
  class ValidateMatch {

    private MatchValidationRequest baseRequest(Set<Long> eligibleMembers) {
      LadderSeason season = new LadderSeason();
      LadderConfig config = new LadderConfig();
      config.setId(101L);
      season.setLadderConfig(config);

      MatchValidationRequest request = new MatchValidationRequest();
      request.setSeason(season);
      request.setEligibleMemberIds(eligibleMembers);
      request.setReporterSlot(
          PlayerSlot.builder("A1")
              .userId(1L)
              .guest(false)
              .guestAllowed(false)
              .requireMemberCheck(true)
              .build());
      request.setPartnerSlot(
          PlayerSlot.builder("A2")
              .userId(2L)
              .guest(false)
              .guestAllowed(true)
              .requireMemberCheck(true)
              .guestSuggestion("leave A2 as Guest")
              .build());
      request.setOpponentOneSlot(
          PlayerSlot.builder("B1")
              .userId(3L)
              .guest(false)
              .guestAllowed(true)
              .requireMemberCheck(true)
              .guestSuggestion("set B1 as Guest")
              .build());
      request.setOpponentTwoSlot(
          PlayerSlot.builder("B2")
              .userId(4L)
              .guest(false)
              .guestAllowed(true)
              .requireMemberCheck(true)
              .guestSuggestion("set B2 as Guest")
              .build());
      return request;
    }

    @Test
    void allowsValidCombination() {
      MatchValidationRequest request = baseRequest(Set.of(1L, 2L, 3L, 4L));

      MatchValidationResult result = service.validate(request);

      assertThat(result.isValid()).isTrue();
      assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void rejectsOpponentTeamWithoutMembers() {
      MatchValidationRequest request = baseRequest(Set.of(1L, 2L, 4L));
      request.setOpponentOneSlot(
          PlayerSlot.builder("B1")
              .guest(true)
              .guestAllowed(true)
              .requireMemberCheck(true)
              .guestSuggestion("set B1 as Guest")
              .build());
      request.setOpponentTwoSlot(
          PlayerSlot.builder("B2")
              .guest(true)
              .guestAllowed(true)
              .requireMemberCheck(true)
              .guestSuggestion("set B2 as Guest")
              .build());

      MatchValidationResult result = service.validate(request);

      assertThat(result.isValid()).isFalse();
      assertThat(result.getErrors())
          .contains("Opponent team: at least one player must be a ladder member");
    }

    @Test
    void rejectsNonMemberOpponent() {
      MatchValidationRequest request = baseRequest(Set.of(1L, 2L, 4L));

      MatchValidationResult result = service.validate(request);

      assertThat(result.isValid()).isFalse();
      assertThat(result.getErrors())
          .contains(
              "B1: must be a member of this ladder for the selected season (or set B1 as Guest)");
    }

    @Test
    void detectsDuplicatePlayersAcrossTeams() {
      MatchValidationRequest request = baseRequest(Set.of(1L, 2L, 3L, 4L));
      request.setOpponentOneSlot(
          PlayerSlot.builder("B1")
              .userId(1L)
              .guest(false)
              .guestAllowed(true)
              .requireMemberCheck(true)
              .guestSuggestion("set B1 as Guest")
              .build());

      MatchValidationResult result = service.validate(request);

      assertThat(result.isValid()).isFalse();
      assertThat(result.getErrors()).contains("Duplicate players detected: 1");
    }
  }

  @Nested
  class ResolveEligibleMembers {

    @Test
    @DisplayName("Returns null when no season id provided")
    void returnsNullWhenSeasonMissing() {
      assertThat(service.resolveEligibleMemberUserIdsForSeason(null)).isNull();
    }

    @Test
    void returnsOrderedMemberIds() {
      LadderSeason season = new LadderSeason();
      LadderConfig config = new LadderConfig();
      config.setId(55L);
      season.setLadderConfig(config);
      ReflectionTestUtils.setField(season, "id", 9L);

      LadderMembership first = new LadderMembership();
      first.setUserId(10L);
      LadderMembership second = new LadderMembership();
      second.setUserId(20L);

      when(seasonRepository.findById(9L)).thenReturn(Optional.of(season));
      when(membershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              55L, LadderMembership.State.ACTIVE))
          .thenReturn(List.of(first, second));

      Set<Long> result = service.resolveEligibleMemberUserIdsForSeason(9L);

      assertThat(result).containsExactly(10L, 20L);
    }

    @Test
    void filtersBlockedCompetitionMembers() {
      LadderSeason season = new LadderSeason();
      LadderConfig config = new LadderConfig();
      config.setId(55L);
      config.setType(LadderConfig.Type.COMPETITION);
      season.setLadderConfig(config);
      ReflectionTestUtils.setField(season, "id", 9L);

      LadderMembership first = new LadderMembership();
      first.setUserId(10L);
      LadderMembership second = new LadderMembership();
      second.setUserId(20L);

      CompetitionAutoModerationService competitionAutoModerationService =
          new CompetitionAutoModerationService(
              null,
              new com.w3llspring.fhpb.web.service.CompetitionSeasonService(null, null, null),
              true,
              1,
              2,
              3) {
            @Override
            public Set<Long> filterEligibleUserIds(LadderSeason ignoredSeason, Set<Long> userIds) {
              return Set.of(10L);
            }
          };
      ReflectionTestUtils.setField(
          service, "competitionAutoModerationService", competitionAutoModerationService);
      when(seasonRepository.findById(9L)).thenReturn(Optional.of(season));
      when(membershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              55L, LadderMembership.State.ACTIVE))
          .thenReturn(List.of(first, second));

      Set<Long> result = service.resolveEligibleMemberUserIdsForSeason(9L);

      assertThat(result).containsExactly(10L);
    }
  }

  @Test
  void scoreValidationRespectsConfiguredMinimumMargin() {
    MatchValidationService strictMarginService =
        new MatchValidationService(seasonRepository, membershipRepository, 11, 35, 2);

    assertThat(strictMarginService.validateScore(11, 10).isValid()).isFalse();
    assertThat(strictMarginService.validateScore(11, 9).isValid()).isTrue();
  }
}
