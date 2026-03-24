package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchFactoryTest {

  @Mock private MatchRepository matchRepository;

  @Mock private MatchConfirmationService matchConfirmationService;

  private MatchFactory matchFactory;

  @BeforeEach
  void setUp() {
    matchFactory = new MatchFactory(matchRepository, matchConfirmationService);
    when(matchRepository.save(any(Match.class)))
        .thenAnswer(
            invocation -> {
              Match saved = invocation.getArgument(0);
              if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 99L);
              }
              return saved;
            });
  }

  @Test
  void
      createMatch_setsExcludeFromStandings_whenSelfConfirmAndFlagEnabledAndOpposingTeamAllGuests() {
    Match match = buildGuestOnlyOpposingTeamMatch(LadderSecurity.SELF_CONFIRM, true);

    Match saved = matchFactory.createMatch(match);

    assertThat(saved.isExcludeFromStandings()).isTrue();
    verify(matchConfirmationService).createRequests(saved);
  }

  @Test
  void createMatch_doesNotSetExcludeFromStandings_whenStandardEvenIfFlagEnabled() {
    Match match = buildGuestOnlyOpposingTeamMatch(LadderSecurity.STANDARD, true);

    Match saved = matchFactory.createMatch(match);

    assertThat(saved.isExcludeFromStandings()).isFalse();
    verify(matchConfirmationService).createRequests(saved);
  }

  @Test
  void createMatch_doesNotSetExcludeFromStandings_whenSelfConfirmButFlagDisabled() {
    Match match = buildGuestOnlyOpposingTeamMatch(LadderSecurity.SELF_CONFIRM, false);

    Match saved = matchFactory.createMatch(match);

    assertThat(saved.isExcludeFromStandings()).isFalse();
    verify(matchConfirmationService).createRequests(saved);
  }

  @Test
  void createMatch_doesNotSetExcludeFromStandings_whenBothTeamsHaveRealPlayers() {
    LadderConfig config = new LadderConfig();
    config.setSecurityLevel(LadderSecurity.SELF_CONFIRM);
    config.setAllowGuestOnlyPersonalMatches(true);

    LadderSeason season = new LadderSeason();
    season.setLadderConfig(config);

    User a1 = new User();
    ReflectionTestUtils.setField(a1, "id", 1L);
    User b1 = new User();
    ReflectionTestUtils.setField(b1, "id", 2L);

    Match match = new Match();
    match.setSeason(season);
    match.setA1(a1);
    match.setA1Guest(false);
    match.setA2Guest(true);
    match.setB1(b1);
    match.setB1Guest(false);
    match.setB2Guest(true);

    Match saved = matchFactory.createMatch(match);

    assertThat(saved.isExcludeFromStandings()).isFalse();
    verify(matchConfirmationService).createRequests(saved);
  }

  @Test
  void createMatch_copiesPlayedAtIntoCreatedAtWhenCreatedAtMissing() {
    Instant playedAt = Instant.parse("2026-03-10T18:00:00Z");
    Match match = new Match();
    match.setCreatedAt(null);
    match.setPlayedAt(playedAt);

    Match saved = matchFactory.createMatch(match);

    assertThat(saved.getPlayedAt()).isEqualTo(playedAt);
    assertThat(saved.getCreatedAt()).isEqualTo(playedAt);
    verify(matchConfirmationService).createRequests(saved);
  }

  @Test
  void createMatch_copiesCreatedAtIntoPlayedAtWhenPlayedAtMissing() {
    Instant createdAt = Instant.parse("2026-03-10T18:00:00Z");
    Match match = new Match();
    match.setPlayedAt(null);
    match.setCreatedAt(createdAt);

    Match saved = matchFactory.createMatch(match);

    assertThat(saved.getPlayedAt()).isEqualTo(createdAt);
    assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
    verify(matchConfirmationService).createRequests(saved);
  }

  private Match buildGuestOnlyOpposingTeamMatch(
      LadderSecurity security, boolean allowGuestOnlyPersonalMatches) {
    LadderConfig config = new LadderConfig();
    config.setSecurityLevel(security);
    config.setAllowGuestOnlyPersonalMatches(allowGuestOnlyPersonalMatches);

    LadderSeason season = new LadderSeason();
    season.setLadderConfig(config);

    User a1 = new User();
    ReflectionTestUtils.setField(a1, "id", 1L);

    Match match = new Match();
    match.setSeason(season);
    match.setA1(a1);
    match.setA1Guest(false);
    match.setA2Guest(true);
    match.setB1Guest(true);
    match.setB2Guest(true);
    return match;
  }
}
