package com.w3llspring.fhpb.web.util;

import static org.assertj.core.api.Assertions.*;

import com.w3llspring.fhpb.web.model.LadderConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionInviteTextTest {
  private final Instant now = Instant.parse("2026-11-09T20:00:00Z");
  private final String link = "https://picklebuddies.com/groups/join?inviteCode=TEST&autoJoin=true";

  private LadderConfig session() {
    LadderConfig s = new LadderConfig();
    s.setType(LadderConfig.Type.SESSION);
    return s;
  }

  @Test
  void currentInviteIncludesOwnerBrandAndRawJoinLink() {
    assertThat(SessionInviteText.build(session(), "Dave", "PickleBuddies", link, now))
        .isEqualTo("Dave invites you to a PickleBuddies session now.\n\nClick to join: " + link);
  }

  @Test
  void futureInviteIncludesDateTimeAndStoredTimezone() {
    var s = session();
    s.setSessionScheduledAt(Instant.parse("2026-11-15T23:30:00Z"));
    s.setSessionTimeZone("America/New_York");
    assertThat(SessionInviteText.build(s, "Dave", "PickleBuddies", link, now))
        .isEqualTo(
            "Dave invites you to a PickleBuddies session on Sun, Nov 15, 2026 at 6:30 PM EST.\n\nClick to join: "
                + link);
    assertThat(s.getSessionDateLabel()).contains("6:30 PM EST");
  }

  @Test
  void namedInvitesIncludeTheEventTitle() {
    var s = session();
    s.setTitle("Dave's \"Amateur Bracket\" session");
    assertThat(SessionInviteText.build(s, "Dave", "PickleBuddies", link, now))
        .isEqualTo(
            "Dave invites you to a PickleBuddies session now.\nDave's \"Amateur Bracket\" session\n\nClick to join: "
                + link);
  }

  @Test
  void aScheduledSessionThatHasStartedInvitesPlayersNow() {
    var s = session();
    s.setSessionScheduledAt(now.plusSeconds(3600));
    s.setSessionTimeZone("America/New_York");
    s.setSessionStartedAt(now);
    assertThat(SessionInviteText.build(s, "Dave", "Open-Pickle", link, now))
        .isEqualTo("Dave invites you to an Open-Pickle session now.\n\nClick to join: " + link);
  }
}
