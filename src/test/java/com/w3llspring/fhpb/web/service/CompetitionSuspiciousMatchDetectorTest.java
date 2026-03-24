package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.CompetitionSuspiciousMatchFlagRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.CompetitionSuspiciousMatchFlag;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.scoring.BalancedV1LadderScoringAlgorithm;
import com.w3llspring.fhpb.web.service.scoring.LadderScoringAlgorithms;
import com.w3llspring.fhpb.web.service.scoring.MarginCurveV1LadderScoringAlgorithm;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitionSuspiciousMatchDetectorTest {

  @Mock private CompetitionSuspiciousMatchFlagRepository flagRepository;

  @Mock private MatchRepository matchRepository;
  @Mock private LadderStandingRepository standingRepository;

  private CompetitionSuspiciousMatchDetector detector;
  private LadderSeason competitionSeason;

  @BeforeEach
  void setUp() {
    detector =
        new CompetitionSuspiciousMatchDetector(
            flagRepository,
            matchRepository,
            standingRepository,
            new LadderScoringAlgorithms(
                List.of(
                    new MarginCurveV1LadderScoringAlgorithm(),
                    new BalancedV1LadderScoringAlgorithm())));

    LadderConfig ladder = new LadderConfig();
    ladder.setId(10L);
    ladder.setType(LadderConfig.Type.COMPETITION);

    competitionSeason = new LadderSeason();
    org.springframework.test.util.ReflectionTestUtils.setField(competitionSeason, "id", 99L);
    competitionSeason.setLadderConfig(ladder);
  }

  @Test
  void reviewConfirmedCompetitionMatch_flagsDeltaMaximizationWhenAWinStacksComponents() {
    User alpha = user(1L, "Alpha");
    User bravo = user(2L, "Bravo");
    User charlie = user(3L, "Charlie");
    User delta = user(4L, "Delta");
    User p1 = user(11L, "P1");
    User p2 = user(12L, "P2");
    User p3 = user(13L, "P3");
    User p4 = user(14L, "P4");
    User o1 = user(21L, "O1");
    User o2 = user(22L, "O2");
    User o3 = user(23L, "O3");
    User o4 = user(24L, "O4");
    User o5 = user(25L, "O5");
    User o6 = user(26L, "O6");
    User o7 = user(27L, "O7");
    User o8 = user(28L, "O8");

    Instant base = Instant.parse("2026-03-10T12:00:00Z");
    List<Match> history = new ArrayList<>();
    history.add(match(100L, alpha, p1, o1, o2, 11, 0, base.minus(4, ChronoUnit.DAYS), alpha));
    history.add(match(101L, alpha, p2, o3, o4, 11, 0, base.minus(3, ChronoUnit.DAYS), alpha));
    history.add(match(102L, alpha, p3, o5, o6, 11, 0, base.minus(2, ChronoUnit.DAYS), alpha));
    history.add(match(103L, alpha, p4, o7, o8, 11, 0, base.minus(1, ChronoUnit.DAYS), alpha));
    Match current = match(104L, alpha, bravo, charlie, delta, 11, 0, base, alpha);
    history.add(current);

    when(matchRepository.findConfirmedForSeasonChrono(competitionSeason)).thenReturn(history);
    when(standingRepository.findBySeasonOrderByRankNoAsc(competitionSeason))
        .thenReturn(standings(alpha, 1, bravo, 6, charlie, 15, delta, 18));

    detector.reviewConfirmedCompetitionMatch(current);

    List<CompetitionSuspiciousMatchFlag> flags = savedFlags();
    assertThat(flags)
        .extracting(CompetitionSuspiciousMatchFlag::getReasonCode)
        .contains(CompetitionSuspiciousMatchFlag.ReasonCode.MAXIMIZED_DELTA);
  }

  @Test
  void reviewConfirmedCompetitionMatch_flagsRapidTurnaroundAndClosedPodPatterns() {
    User alpha = user(1L, "Alpha");
    User bravo = user(2L, "Bravo");
    User charlie = user(3L, "Charlie");
    User delta = user(4L, "Delta");

    Instant base = Instant.parse("2026-03-15T12:00:00Z");
    List<Match> history = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      history.add(
          match(
              200L + i,
              alpha,
              bravo,
              charlie,
              delta,
              11,
              9,
              base.minus(48 - (i * 8), ChronoUnit.MINUTES),
              alpha));
    }
    Match current = match(300L, alpha, bravo, charlie, delta, 11, 9, base, alpha);
    history.add(current);

    when(matchRepository.findConfirmedForSeasonChrono(competitionSeason)).thenReturn(history);
    when(standingRepository.findBySeasonOrderByRankNoAsc(competitionSeason))
        .thenReturn(standings(alpha, 2, bravo, 3, charlie, 12, delta, 14));

    detector.reviewConfirmedCompetitionMatch(current);

    List<CompetitionSuspiciousMatchFlag> flags = savedFlags();
    assertThat(flags)
        .extracting(CompetitionSuspiciousMatchFlag::getReasonCode)
        .contains(
            CompetitionSuspiciousMatchFlag.ReasonCode.RAPID_TURNAROUND,
            CompetitionSuspiciousMatchFlag.ReasonCode.CLOSED_PLAYER_POD);
  }

  @Test
  void reviewConfirmedCompetitionMatch_doesNotFlagRapidTurnaroundForSingleShortGap() {
    User alpha = user(1L, "Alpha");
    User bravo = user(2L, "Bravo");
    User charlie = user(3L, "Charlie");
    User delta = user(4L, "Delta");

    Instant base = Instant.parse("2026-03-16T12:00:00Z");
    List<Match> history = new ArrayList<>();
    history.add(
        match(
            400L, alpha, bravo, charlie, delta, 11, 8, base.minus(90, ChronoUnit.MINUTES), alpha));
    history.add(
        match(
            401L, alpha, bravo, charlie, delta, 11, 8, base.minus(70, ChronoUnit.MINUTES), alpha));
    history.add(
        match(
            402L, alpha, bravo, charlie, delta, 11, 8, base.minus(50, ChronoUnit.MINUTES), alpha));
    Match current =
        match(403L, alpha, bravo, charlie, delta, 11, 8, base.minus(44, ChronoUnit.MINUTES), alpha);
    history.add(current);

    when(matchRepository.findConfirmedForSeasonChrono(competitionSeason)).thenReturn(history);
    when(standingRepository.findBySeasonOrderByRankNoAsc(competitionSeason))
        .thenReturn(standings(alpha, 4, bravo, 8, charlie, 11, delta, 19));

    detector.reviewConfirmedCompetitionMatch(current);

    verify(flagRepository, never()).saveAll(any());
  }

  @Test
  void reviewConfirmedCompetitionMatch_doesNotPersistFlagsWhenSuspiciousPlayersAreMiddleOfPack() {
    User alpha = user(1L, "Alpha");
    User bravo = user(2L, "Bravo");
    User charlie = user(3L, "Charlie");
    User delta = user(4L, "Delta");
    User p1 = user(11L, "P1");
    User p2 = user(12L, "P2");
    User p3 = user(13L, "P3");
    User p4 = user(14L, "P4");
    User o1 = user(21L, "O1");
    User o2 = user(22L, "O2");
    User o3 = user(23L, "O3");
    User o4 = user(24L, "O4");
    User o5 = user(25L, "O5");
    User o6 = user(26L, "O6");
    User o7 = user(27L, "O7");
    User o8 = user(28L, "O8");

    Instant base = Instant.parse("2026-03-17T12:00:00Z");
    List<Match> history = new ArrayList<>();
    history.add(match(500L, alpha, p1, o1, o2, 11, 0, base.minus(4, ChronoUnit.DAYS), alpha));
    history.add(match(501L, alpha, p2, o3, o4, 11, 0, base.minus(3, ChronoUnit.DAYS), alpha));
    history.add(match(502L, alpha, p3, o5, o6, 11, 0, base.minus(2, ChronoUnit.DAYS), alpha));
    history.add(match(503L, alpha, p4, o7, o8, 11, 0, base.minus(1, ChronoUnit.DAYS), alpha));
    Match current = match(504L, alpha, bravo, charlie, delta, 11, 0, base, alpha);
    history.add(current);

    when(standingRepository.findBySeasonOrderByRankNoAsc(competitionSeason))
        .thenReturn(standings(alpha, 18, bravo, 22, charlie, 28, delta, 31));

    detector.reviewConfirmedCompetitionMatch(current);

    verify(flagRepository, never()).saveAll(any());
  }

  @SuppressWarnings("unchecked")
  private List<CompetitionSuspiciousMatchFlag> savedFlags() {
    ArgumentCaptor<Iterable<CompetitionSuspiciousMatchFlag>> captor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(flagRepository).saveAll(captor.capture());
    List<CompetitionSuspiciousMatchFlag> flags = new ArrayList<>();
    captor.getValue().forEach(flags::add);
    return flags;
  }

  private Match match(
      Long id,
      User a1,
      User a2,
      User b1,
      User b2,
      int scoreA,
      int scoreB,
      Instant playedAt,
      User loggedBy) {
    Match match = new Match();
    match.setSeason(competitionSeason);
    match.setA1(a1);
    match.setA2(a2);
    match.setB1(b1);
    match.setB2(b2);
    match.setScoreA(scoreA);
    match.setScoreB(scoreB);
    match.setState(MatchState.CONFIRMED);
    match.setPlayedAt(playedAt);
    match.setLoggedBy(loggedBy);
    org.springframework.test.util.ReflectionTestUtils.setField(match, "id", id);
    return match;
  }

  private List<LadderStanding> standings(Object... entries) {
    List<LadderStanding> standings = new ArrayList<>();
    for (int i = 0; i < entries.length; i += 2) {
      User user = (User) entries[i];
      Integer rank = (Integer) entries[i + 1];
      LadderStanding standing = new LadderStanding();
      standing.setSeason(competitionSeason);
      standing.setUser(user);
      standing.setDisplayName(user.getNickName());
      standing.setRank(rank);
      standings.add(standing);
    }
    return standings;
  }

  private User user(Long id, String nickName) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickName);
    user.setEmail(nickName.toLowerCase() + "@test.local");
    return user;
  }
}
