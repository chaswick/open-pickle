package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompetitionAutoModerationServiceTest {

  @Mock private MatchConfirmationRepository confirmationRepository;

  private CompetitionSeasonService competitionSeasonService;

  private CompetitionAutoModerationService service;
  private LadderSeason competitionSeason;

  @BeforeEach
  void setUp() {
    competitionSeasonService =
        new CompetitionSeasonService(null, null, null) {
          @Override
          public LadderSeason resolveActiveCompetitionSeason() {
            return competitionSeason;
          }
        };
    service =
        new CompetitionAutoModerationService(
            confirmationRepository, competitionSeasonService, true, 1, 2, 3);
    competitionSeason = competitionSeason(55L);
  }

  @Test
  void statusForSeason_countsOnlyExpiredConfirmationStrikes() {
    User player = user(10L);

    MatchConfirmation overdueStrike =
        confirmation(
            player, match(player, competitionSeason, MatchState.NULLIFIED, false, false), null);
    MatchConfirmation disputedNullification =
        confirmation(
            player, match(player, competitionSeason, MatchState.NULLIFIED, false, true), null);
    MatchConfirmation alreadyConfirmed =
        confirmation(
            player,
            match(player, competitionSeason, MatchState.NULLIFIED, false, false),
            Instant.now());
    MatchConfirmation guestOnlyPersonalRecord =
        confirmation(
            player, match(player, competitionSeason, MatchState.NULLIFIED, true, false), null);

    when(confirmationRepository.findByPlayerIdInAndMatchSeasonId(anyList(), eq(55L)))
        .thenReturn(
            List.of(
                overdueStrike, disputedNullification, alreadyConfirmed, guestOnlyPersonalRecord));

    CompetitionAutoModerationService.AutoModerationStatus status =
        service.statusForSeason(player, competitionSeason);

    assertThat(status.getLevel())
        .isEqualTo(CompetitionAutoModerationService.AutoModerationLevel.WARNING_ONE);
    assertThat(status.getIncidentCount()).isEqualTo(1);
    assertThat(status.getBannerTitle()).isEqualTo("Fair Play Warning");
  }

  @Test
  void filterEligibleUserIds_removesBlockedPlayersAndRequireNotBlockedUsesHelpfulMessage() {
    User oneStrike = user(10L);
    User twoStrikes = user(20L);
    User threeStrikes = user(30L);

    when(confirmationRepository.findByPlayerIdInAndMatchSeasonId(anyList(), eq(55L)))
        .thenReturn(
            List.of(
                confirmation(
                    oneStrike,
                    match(oneStrike, competitionSeason, MatchState.NULLIFIED, false, false),
                    null),
                confirmation(
                    twoStrikes,
                    match(twoStrikes, competitionSeason, MatchState.NULLIFIED, false, false),
                    null),
                confirmation(
                    twoStrikes,
                    match(twoStrikes, competitionSeason, MatchState.NULLIFIED, false, false),
                    null),
                confirmation(
                    threeStrikes,
                    match(threeStrikes, competitionSeason, MatchState.NULLIFIED, false, false),
                    null),
                confirmation(
                    threeStrikes,
                    match(threeStrikes, competitionSeason, MatchState.NULLIFIED, false, false),
                    null),
                confirmation(
                    threeStrikes,
                    match(threeStrikes, competitionSeason, MatchState.NULLIFIED, false, false),
                    null)));

    Set<Long> eligible = service.filterEligibleUserIds(competitionSeason, Set.of(10L, 20L, 30L));

    assertThat(eligible).containsExactlyInAnyOrder(10L, 20L);

    SecurityException ex =
        assertThrows(
            SecurityException.class,
            () -> service.requireNotBlocked(threeStrikes, competitionSeason));
    assertThat(ex.getMessage()).contains("rest of this season");
  }

  private LadderSeason competitionSeason(Long id) {
    LadderConfig config = new LadderConfig();
    config.setType(LadderConfig.Type.COMPETITION);
    LadderSeason season = new LadderSeason();
    season.setLadderConfig(config);
    ReflectionTestUtils.setField(season, "id", id);
    return season;
  }

  private User user(Long id) {
    User user = new User();
    user.setId(id);
    user.setNickName("U" + id);
    user.setEmail("u" + id + "@example.com");
    return user;
  }

  private Match match(
      User player,
      LadderSeason season,
      MatchState state,
      boolean guestOnlyOpponents,
      boolean disputed) {
    Match match = new Match();
    match.setSeason(season);
    match.setState(state);
    match.setA1(player);
    match.setA1Guest(false);
    match.setB1Guest(guestOnlyOpponents);
    match.setB2Guest(guestOnlyOpponents);
    if (disputed) {
      match.setDisputedBy(player);
      match.setDisputedAt(Instant.now());
      match.setDisputeNote("did not play");
    }
    return match;
  }

  private MatchConfirmation confirmation(User player, Match match, Instant confirmedAt) {
    MatchConfirmation confirmation = new MatchConfirmation();
    confirmation.setPlayer(player);
    confirmation.setMatch(match);
    confirmation.setConfirmedAt(confirmedAt);
    return confirmation;
  }
}
