package com.w3llspring.fhpb.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerTrustServiceTest {

  private MatchRepository matchRepository;
  private MatchConfirmationRepository confirmationRepository;
  private PlayerTrustService service;

  @BeforeEach
  void setUp() {
    matchRepository = mock(MatchRepository.class);
    confirmationRepository = mock(MatchConfirmationRepository.class);
    service = new PlayerTrustService(matchRepository, confirmationRepository);
  }

  @Test
  void calculateTrustWeight_ignoresAutoConfirmedLoggerRows() {
    User logger = user(10L, "Logger");

    when(matchRepository.findByLoggedByIdAndSeasonId(10L, 55L)).thenReturn(List.of());
    when(confirmationRepository.findByPlayerIdAndMatchSeasonId(10L, 55L))
        .thenReturn(
            List.of(
                confirmation(
                    match(logger, opponent(20L), logger, MatchState.CONFIRMED, false),
                    logger,
                    "A",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    Instant.now()),
                confirmation(
                    match(logger, opponent(21L), logger, MatchState.CONFIRMED, false),
                    logger,
                    "A",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    Instant.now()),
                confirmation(
                    match(logger, opponent(22L), logger, MatchState.CONFIRMED, false),
                    logger,
                    "A",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    Instant.now()),
                confirmation(
                    match(logger, opponent(23L), logger, MatchState.CONFIRMED, false),
                    logger,
                    "A",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    Instant.now()),
                confirmation(
                    match(logger, opponent(24L), logger, MatchState.CONFIRMED, false),
                    logger,
                    "A",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    Instant.now())));

    double trustWeight = service.calculateTrustWeight(10L, 55L);

    assertEquals(1.0d, trustWeight);
  }

  @Test
  void calculateTrustWeight_penalizesExpiredConfirmationSilence() {
    User player = user(10L, "Player");

    when(matchRepository.findByLoggedByIdAndSeasonId(10L, 55L)).thenReturn(List.of());
    when(confirmationRepository.findByPlayerIdAndMatchSeasonId(10L, 55L))
        .thenReturn(
            List.of(
                confirmation(
                    match(opponent(20L), player, opponent(20L), MatchState.NULLIFIED, false),
                    player,
                    "B",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    null),
                confirmation(
                    match(opponent(21L), player, opponent(21L), MatchState.NULLIFIED, false),
                    player,
                    "B",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    null),
                confirmation(
                    match(opponent(22L), player, opponent(22L), MatchState.NULLIFIED, false),
                    player,
                    "B",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    null),
                confirmation(
                    match(opponent(23L), player, opponent(23L), MatchState.NULLIFIED, false),
                    player,
                    "B",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    null),
                confirmation(
                    match(opponent(24L), player, opponent(24L), MatchState.NULLIFIED, false),
                    player,
                    "B",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    null)));

    double trustWeight = service.calculateTrustWeight(10L, 55L);

    assertEquals(0.0d, trustWeight);
  }

  @Test
  void calculateTrustWeight_ignoresDisputedNullifications() {
    User player = user(10L, "Player");

    when(matchRepository.findByLoggedByIdAndSeasonId(10L, 55L)).thenReturn(List.of());
    when(confirmationRepository.findByPlayerIdAndMatchSeasonId(10L, 55L))
        .thenReturn(
            List.of(
                confirmation(
                    match(opponent(20L), player, opponent(20L), MatchState.NULLIFIED, true),
                    player,
                    "B",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    null),
                confirmation(
                    match(opponent(21L), player, opponent(21L), MatchState.NULLIFIED, true),
                    player,
                    "B",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    null),
                confirmation(
                    match(opponent(22L), player, opponent(22L), MatchState.NULLIFIED, true),
                    player,
                    "B",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    null),
                confirmation(
                    match(opponent(23L), player, opponent(23L), MatchState.NULLIFIED, true),
                    player,
                    "B",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    null),
                confirmation(
                    match(opponent(24L), player, opponent(24L), MatchState.NULLIFIED, true),
                    player,
                    "B",
                    MatchConfirmation.ConfirmationMethod.AUTO,
                    null)));

    double trustWeight = service.calculateTrustWeight(10L, 55L);

    assertEquals(1.0d, trustWeight);
  }

  private Match match(User a1, User b1, User loggedBy, MatchState state, boolean disputed) {
    Match match = new Match();
    match.setA1(a1);
    match.setB1(b1);
    match.setLoggedBy(loggedBy);
    match.setState(state);
    match.setScoreA(11);
    match.setScoreB(7);
    if (disputed) {
      match.setDisputedBy(b1);
      match.setDisputedAt(Instant.now());
      match.setDisputeNote("Did not play");
    }
    return match;
  }

  private MatchConfirmation confirmation(
      Match match,
      User player,
      String team,
      MatchConfirmation.ConfirmationMethod method,
      Instant confirmedAt) {
    MatchConfirmation confirmation = new MatchConfirmation();
    confirmation.setMatch(match);
    confirmation.setPlayer(player);
    confirmation.setTeam(team);
    confirmation.setMethod(method);
    confirmation.setConfirmedAt(confirmedAt);
    return confirmation;
  }

  private User user(Long id, String nickName) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickName);
    user.setEmail(nickName.toLowerCase() + "@test.local");
    return user;
  }

  private User opponent(Long id) {
    return user(id, "Opponent" + id);
  }
}
