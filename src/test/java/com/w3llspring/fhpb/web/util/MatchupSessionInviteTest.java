package com.w3llspring.fhpb.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.model.LadderConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MatchupSessionInviteTest {
  @Test
  void peopleCanJoinADayAfterPreparationAndLaterDuringPlay() {
    Instant now = Instant.parse("2026-10-28T18:00:00Z");
    LadderConfig session = new LadderConfig();
    session.setType(LadderConfig.Type.SESSION);
    session.setSessionPlayMode(LadderConfig.SessionPlayMode.MATCHUPS);
    session.setInviteCode("GROVE-SHOT-15");
    session.setLastInviteChangeAt(now.minusSeconds(24 * 3600));
    session.setExpiresAt(now.plusSeconds(24 * 3600));
    assertThat(SessionInviteCodeSupport.isCurrentlyActive(session, 1800, now)).isTrue();
    session.setExpiresAt(now.minusSeconds(1));
    assertThat(SessionInviteCodeSupport.isCurrentlyActive(session, 1800, now)).isFalse();
    session.setExpiresAt(now.plusSeconds(3600));
    session.setStatus(LadderConfig.Status.ARCHIVED);
    assertThat(SessionInviteCodeSupport.isCurrentlyActive(session, 1800, now)).isFalse();
  }
}
