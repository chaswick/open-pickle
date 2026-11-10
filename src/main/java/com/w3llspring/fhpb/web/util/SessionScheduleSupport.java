package com.w3llspring.fhpb.web.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class SessionScheduleSupport {
  public static final int START_INTERVAL_MINUTES = 15;

  private SessionScheduleSupport() {}

  public static Instant resolveStart(
      LocalDate date, LocalTime time, ZoneId zone, Instant now, int maxDays) {
    LocalDate today = now.atZone(zone).toLocalDate();
    if (date.isBefore(today) || date.isAfter(today.plusDays(maxDays)))
      throw new IllegalArgumentException("Choose a date within the next " + maxDays + " days.");
    var local = date.atTime(time != null ? time : LocalTime.MIDNIGHT);
    if (local.getMinute() % START_INTERVAL_MINUTES != 0
        || local.getSecond() != 0
        || local.getNano() != 0)
      throw new IllegalArgumentException("Choose a start time in 15-minute increments.");
    if (zone.getRules().getValidOffsets(local).isEmpty())
      throw new IllegalArgumentException(
          "This time falls in a daylight-saving time change. Choose another time.");
    Instant start = local.atZone(zone).toInstant();
    if (!start.isAfter(now))
      throw new IllegalArgumentException("Choose a future date and time for your session.");
    return start;
  }
}
