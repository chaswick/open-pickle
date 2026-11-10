package com.w3llspring.fhpb.web.util;

import com.w3llspring.fhpb.web.model.LadderConfig;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class SessionInviteText {
  private SessionInviteText() {}

  public static String build(LadderConfig session, String owner, String appName, String link) {
    return build(session, owner, appName, link, Instant.now());
  }

  static String build(
      LadderConfig session, String owner, String appName, String link, Instant now) {
    if (link == null || link.isBlank()) return null;
    String when = "now";
    if (session.isUpcomingSession(now)) {
      when =
          "on "
              + session
                  .getSessionScheduledAt()
                  .atZone(ZoneId.of(session.getSessionTimeZone()))
                  .format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a z", Locale.US));
    }
    String article = "AEIOUaeiou".indexOf(appName.charAt(0)) >= 0 ? "an" : "a";
    String title = session.getTitle();
    String event =
        title != null && !title.isBlank() && !title.equalsIgnoreCase(owner + "'s Session")
            ? "\n" + title
            : "";
    return owner
        + " invites you to "
        + article
        + " "
        + appName
        + " session "
        + when
        + "."
        + event
        + "\n\nClick to join: "
        + link;
  }
}
