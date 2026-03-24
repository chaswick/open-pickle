package com.w3llspring.fhpb.web.service.matchlog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class MatchValidationPickleballScoreTest {

  private MatchValidationService makeService() {
    LadderSeasonRepository seasonRepo = mock(LadderSeasonRepository.class);
    LadderMembershipRepository membershipRepo = mock(LadderMembershipRepository.class);
    return new MatchValidationService(seasonRepo, membershipRepo);
  }

  @Test
  @DisplayName("Tie scores should be invalid")
  void tieScoresInvalid() {
    MatchValidationService svc = makeService();
    assertFalse(svc.isValidPickleballScore(11, 11));
    assertFalse(svc.isValidPickleballScore(10, 10));
  }

  @Test
  @DisplayName("Scores with winner under 11 are invalid")
  void underElevenInvalid() {
    MatchValidationService svc = makeService();
    assertFalse(svc.isValidPickleballScore(10, 8));
    assertFalse(svc.isValidPickleballScore(9, 10));
  }

  @Test
  @DisplayName("Standard 11-point wins are accepted")
  void standardElevenWins() {
    MatchValidationService svc = makeService();
    assertTrue(svc.isValidPickleballScore(11, 9));
    assertTrue(svc.isValidPickleballScore(11, 10));
    assertTrue(svc.isValidPickleballScore(11, 0));
  }

  @Test
  @DisplayName("Alternative higher-score formats are accepted up to the configured cap")
  void extendedFormatsWithinCapAreAccepted() {
    MatchValidationService svc = makeService();
    assertTrue(svc.isValidPickleballScore(12, 10));
    assertTrue(svc.isValidPickleballScore(13, 10));
    assertTrue(svc.isValidPickleballScore(15, 7));
    assertTrue(svc.isValidPickleballScore(14, 12));
    assertTrue(svc.isValidPickleballScore(30, 29));
  }

  @Test
  @DisplayName("Scores above the configured cap are invalid")
  void scoresAboveCapInvalid() {
    MatchValidationService svc = makeService();

    assertFalse(svc.isValidPickleballScore(36, 34));
    assertFalse(svc.isValidPickleballScore(35, 36));
  }

  @Test
  @DisplayName("Negative scores are invalid")
  void negativeScoresInvalid() {
    MatchValidationService svc = makeService();

    assertFalse(svc.isValidPickleballScore(-1, 11));
    assertFalse(svc.isValidPickleballScore(11, -1));
  }
}
