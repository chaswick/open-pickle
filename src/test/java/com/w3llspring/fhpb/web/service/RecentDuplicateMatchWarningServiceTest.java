package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecentDuplicateMatchWarningServiceTest {

  @Mock private MatchRepository matchRepository;
  @Mock private MatchConfirmationRepository matchConfirmationRepository;

  private RecentDuplicateMatchWarningService service;

  @BeforeEach
  void setUp() {
    service =
        new RecentDuplicateMatchWarningService(matchRepository, matchConfirmationRepository, 120);
  }

  @Test
  void findWarning_detectsOppositeTeamDuplicateWithReversedScore() {
    Instant now = Instant.parse("2026-03-20T22:00:00Z");
    User reporter = user(10L, "reporter");
    User partner = user(20L, "partner");
    User opponentOne = user(30L, "opponent1");
    User opponentTwo = user(40L, "opponent2");

    LadderSeason season = new LadderSeason();
    setId(season, 811L);
    LadderConfig ladder = new LadderConfig();
    ladder.setId(711L);
    season.setLadderConfig(ladder);

    Match existing = new Match();
    setId(existing, 321L);
    existing.setSeason(season);
    existing.setCreatedAt(now.minusSeconds(35));
    existing.setState(MatchState.PROVISIONAL);
    existing.setLoggedBy(opponentOne);
    existing.setA1(opponentOne);
    existing.setA2(opponentTwo);
    existing.setB1(reporter);
    existing.setB2(partner);
    existing.setA1Guest(false);
    existing.setA2Guest(false);
    existing.setB1Guest(false);
    existing.setB2Guest(false);
    existing.setScoreA(9);
    existing.setScoreB(11);

    MatchConfirmation confirmation = new MatchConfirmation();
    confirmation.setMatch(existing);
    confirmation.setPlayer(partner);
    confirmation.setTeam("B");
    confirmation.setConfirmedAt(now.minusSeconds(15));

    when(matchRepository.findByCreatedAtInRange(now.minusSeconds(120), now.plusSeconds(1)))
        .thenReturn(List.of(existing));
    when(matchConfirmationRepository.findByMatchIdIn(List.of(321L)))
        .thenReturn(List.of(confirmation));

    Optional<RecentDuplicateMatchWarningService.RecentDuplicateMatchWarning> warning =
        service.findWarning(
            new RecentDuplicateMatchWarningService.DetectionRequest(
                811L,
                null,
                List.of("user:10", "user:20"),
                List.of("user:30", "user:40"),
                11,
                9,
                now));

    assertThat(warning).isPresent();
    assertThat(warning.get().matchId()).isEqualTo(321L);
    assertThat(warning.get().message()).contains("opponent1");
    assertThat(warning.get().message()).contains("partner confirmed it");
    assertThat(warning.get().message()).contains("Are you sure you want to log it?");
  }

  @Test
  void findWarning_detectsDuplicateWhenTeamsMatchButScoreDiffers() {
    Instant now = Instant.parse("2026-03-20T22:05:00Z");

    LadderSeason season = new LadderSeason();
    setId(season, 815L);

    Match existing = new Match();
    setId(existing, 401L);
    existing.setSeason(season);
    existing.setCreatedAt(now.minusSeconds(25));
    existing.setState(MatchState.PROVISIONAL);
    existing.setScoreA(11);
    existing.setScoreB(8);
    existing.setA1(user(10L, "reporter"));
    existing.setA2(user(20L, "partner"));
    existing.setB1(user(30L, "opponent1"));
    existing.setB2(user(40L, "opponent2"));
    existing.setA1Guest(false);
    existing.setA2Guest(false);
    existing.setB1Guest(false);
    existing.setB2Guest(false);

    when(matchRepository.findByCreatedAtInRange(now.minusSeconds(120), now.plusSeconds(1)))
        .thenReturn(List.of(existing));
    when(matchConfirmationRepository.findByMatchIdIn(List.of(401L))).thenReturn(List.of());

    Optional<RecentDuplicateMatchWarningService.RecentDuplicateMatchWarning> warning =
        service.findWarning(
            new RecentDuplicateMatchWarningService.DetectionRequest(
                815L,
                null,
                List.of("user:10", "user:20"),
                List.of("user:30", "user:40"),
                11,
                9,
                now));

    assertThat(warning).isPresent();
    assertThat(warning.get().matchId()).isEqualTo(401L);
  }

  @Test
  void findWarning_prefersExactScoreMatchWhenMultipleDuplicatesExist() {
    Instant now = Instant.parse("2026-03-20T22:07:00Z");

    LadderSeason season = new LadderSeason();
    setId(season, 816L);

    Match exactScoreMatch = new Match();
    setId(exactScoreMatch, 501L);
    exactScoreMatch.setSeason(season);
    exactScoreMatch.setCreatedAt(now.minusSeconds(35));
    exactScoreMatch.setState(MatchState.PROVISIONAL);
    exactScoreMatch.setScoreA(11);
    exactScoreMatch.setScoreB(9);
    exactScoreMatch.setA1(user(10L, "reporter"));
    exactScoreMatch.setA2(user(20L, "partner"));
    exactScoreMatch.setB1(user(30L, "opponent1"));
    exactScoreMatch.setB2(user(40L, "opponent2"));
    exactScoreMatch.setA1Guest(false);
    exactScoreMatch.setA2Guest(false);
    exactScoreMatch.setB1Guest(false);
    exactScoreMatch.setB2Guest(false);

    Match scoreMismatch = new Match();
    setId(scoreMismatch, 502L);
    scoreMismatch.setSeason(season);
    scoreMismatch.setCreatedAt(now.minusSeconds(10));
    scoreMismatch.setState(MatchState.PROVISIONAL);
    scoreMismatch.setScoreA(11);
    scoreMismatch.setScoreB(8);
    scoreMismatch.setA1(user(10L, "reporter"));
    scoreMismatch.setA2(user(20L, "partner"));
    scoreMismatch.setB1(user(30L, "opponent1"));
    scoreMismatch.setB2(user(40L, "opponent2"));
    scoreMismatch.setA1Guest(false);
    scoreMismatch.setA2Guest(false);
    scoreMismatch.setB1Guest(false);
    scoreMismatch.setB2Guest(false);

    when(matchRepository.findByCreatedAtInRange(now.minusSeconds(120), now.plusSeconds(1)))
        .thenReturn(List.of(scoreMismatch, exactScoreMatch));
    when(matchConfirmationRepository.findByMatchIdIn(List.of(502L, 501L))).thenReturn(List.of());

    Optional<RecentDuplicateMatchWarningService.RecentDuplicateMatchWarning> warning =
        service.findWarning(
            new RecentDuplicateMatchWarningService.DetectionRequest(
                816L,
                null,
                List.of("user:10", "user:20"),
                List.of("user:30", "user:40"),
                11,
                9,
                now));

    assertThat(warning).isPresent();
    assertThat(warning.get().matchId()).isEqualTo(501L);
  }

  @Test
  void findWarning_ignoresRecentMatchesOutsideTheCurrentScope() {
    Instant now = Instant.parse("2026-03-20T22:00:00Z");

    LadderSeason season = new LadderSeason();
    setId(season, 812L);

    Match otherSeasonMatch = new Match();
    setId(otherSeasonMatch, 654L);
    otherSeasonMatch.setSeason(season);
    otherSeasonMatch.setCreatedAt(now.minusSeconds(20));
    otherSeasonMatch.setState(MatchState.PROVISIONAL);
    otherSeasonMatch.setScoreA(11);
    otherSeasonMatch.setScoreB(9);

    when(matchRepository.findByCreatedAtInRange(now.minusSeconds(120), now.plusSeconds(1)))
        .thenReturn(List.of(otherSeasonMatch));

    Optional<RecentDuplicateMatchWarningService.RecentDuplicateMatchWarning> warning =
        service.findWarning(
            new RecentDuplicateMatchWarningService.DetectionRequest(
                999L,
                null,
                List.of("user:10", "user:20"),
                List.of("user:30", "user:40"),
                11,
                9,
                now));

    assertThat(warning).isEmpty();
    verifyNoInteractions(matchConfirmationRepository);
  }

  @Test
  void findConfirmedWarningForMatch_excludesCurrentMatchAndUsesConfirmPrompt() {
    Instant now = Instant.parse("2026-03-20T22:00:00Z");
    User reporter = user(10L, "reporter");
    User partner = user(20L, "partner");
    User opponent = user(30L, "opponent");

    LadderSeason season = new LadderSeason();
    setId(season, 813L);

    Match current = new Match();
    setId(current, 700L);
    current.setSeason(season);
    current.setState(MatchState.PROVISIONAL);
    current.setA1(reporter);
    current.setA2(partner);
    current.setB1(opponent);
    current.setScoreA(11);
    current.setScoreB(7);
    current.setA1Guest(false);
    current.setA2Guest(false);
    current.setB1Guest(false);
    current.setB2Guest(true);

    Match confirmedDuplicate = new Match();
    setId(confirmedDuplicate, 701L);
    confirmedDuplicate.setSeason(season);
    confirmedDuplicate.setCreatedAt(now.minusSeconds(20));
    confirmedDuplicate.setState(MatchState.CONFIRMED);
    confirmedDuplicate.setLoggedBy(opponent);
    confirmedDuplicate.setA1(opponent);
    confirmedDuplicate.setB1(reporter);
    confirmedDuplicate.setB2(partner);
    confirmedDuplicate.setScoreA(7);
    confirmedDuplicate.setScoreB(11);
    confirmedDuplicate.setA1Guest(false);
    confirmedDuplicate.setA2Guest(true);
    confirmedDuplicate.setB1Guest(false);
    confirmedDuplicate.setB2Guest(false);

    MatchConfirmation confirmation = new MatchConfirmation();
    confirmation.setMatch(confirmedDuplicate);
    confirmation.setPlayer(partner);
    confirmation.setTeam("B");
    confirmation.setConfirmedAt(now.minusSeconds(8));

    when(matchRepository.findByCreatedAtInRange(now.minusSeconds(120), now.plusSeconds(1)))
        .thenReturn(List.of(current, confirmedDuplicate));
    when(matchConfirmationRepository.findByMatchIdIn(List.of(701L)))
        .thenReturn(List.of(confirmation));

    Optional<RecentDuplicateMatchWarningService.RecentDuplicateMatchWarning> warning =
        service.findConfirmedWarningForMatch(current, now);

    assertThat(warning).isPresent();
    assertThat(warning.get().matchId()).isEqualTo(701L);
    assertThat(warning.get().message()).contains("confirm this one");
    assertThat(warning.get().message()).contains("partner confirmed it");
  }

  @Test
  void findConfirmedWarningForMatch_omitsLoggerFromConfirmedNames() {
    Instant now = Instant.parse("2026-03-20T22:10:00Z");
    User reporter = user(10L, "reporter");
    User partner = user(20L, "partner");
    User opponent = user(30L, "opponent");

    LadderSeason season = new LadderSeason();
    setId(season, 814L);

    Match current = new Match();
    setId(current, 702L);
    current.setSeason(season);
    current.setState(MatchState.PROVISIONAL);
    current.setA1(reporter);
    current.setA2(partner);
    current.setB1(opponent);
    current.setScoreA(11);
    current.setScoreB(7);
    current.setA1Guest(false);
    current.setA2Guest(false);
    current.setB1Guest(false);
    current.setB2Guest(true);

    Match confirmedDuplicate = new Match();
    setId(confirmedDuplicate, 703L);
    confirmedDuplicate.setSeason(season);
    confirmedDuplicate.setCreatedAt(now.minusSeconds(20));
    confirmedDuplicate.setState(MatchState.CONFIRMED);
    confirmedDuplicate.setLoggedBy(opponent);
    confirmedDuplicate.setA1(opponent);
    confirmedDuplicate.setB1(reporter);
    confirmedDuplicate.setB2(partner);
    confirmedDuplicate.setScoreA(7);
    confirmedDuplicate.setScoreB(11);
    confirmedDuplicate.setA1Guest(false);
    confirmedDuplicate.setA2Guest(true);
    confirmedDuplicate.setB1Guest(false);
    confirmedDuplicate.setB2Guest(false);

    MatchConfirmation loggerConfirmation = new MatchConfirmation();
    loggerConfirmation.setMatch(confirmedDuplicate);
    loggerConfirmation.setPlayer(opponent);
    loggerConfirmation.setTeam("A");
    loggerConfirmation.setConfirmedAt(now.minusSeconds(10));

    MatchConfirmation partnerConfirmation = new MatchConfirmation();
    partnerConfirmation.setMatch(confirmedDuplicate);
    partnerConfirmation.setPlayer(partner);
    partnerConfirmation.setTeam("B");
    partnerConfirmation.setConfirmedAt(now.minusSeconds(8));

    when(matchRepository.findByCreatedAtInRange(now.minusSeconds(120), now.plusSeconds(1)))
        .thenReturn(List.of(current, confirmedDuplicate));
    when(matchConfirmationRepository.findByMatchIdIn(List.of(703L)))
        .thenReturn(List.of(loggerConfirmation, partnerConfirmation));

    Optional<RecentDuplicateMatchWarningService.RecentDuplicateMatchWarning> warning =
        service.findConfirmedWarningForMatch(current, now);

    assertThat(warning).isPresent();
    assertThat(warning.get().message())
        .isEqualTo(
            "It looks like opponent already logged this match and partner confirmed it. Are you sure you want to confirm this one?");
  }

  private static User user(Long id, String nickname) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickname);
    user.setEmail(nickname + "@test.local");
    return user;
  }

  private static void setId(Object target, long id) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField("id");
      field.setAccessible(true);
      field.set(target, id);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
