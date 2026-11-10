package com.w3llspring.fhpb.web.util;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

class SessionScheduleSupportTest {
  private final ZoneId zone = ZoneId.of("America/New_York");
  private final Instant now = Instant.parse("2026-03-01T17:00:00Z");

  @Test
  void allowsLaterTodayButRejectsPastTimes() {
    assertThat(
            SessionScheduleSupport.resolveStart(
                LocalDate.of(2026, 3, 1), LocalTime.of(18, 30), zone, now, 90))
        .isEqualTo(Instant.parse("2026-03-01T23:30:00Z"));
    assertThatThrownBy(
            () ->
                SessionScheduleSupport.resolveStart(
                    LocalDate.of(2026, 3, 1), LocalTime.of(11, 30), zone, now, 90))
        .hasMessageContaining("future date and time");
  }

  @Test
  void respectsThePlanningWindow() {
    assertThatThrownBy(
            () ->
                SessionScheduleSupport.resolveStart(
                    LocalDate.of(2026, 6, 1), LocalTime.NOON, zone, now, 90))
        .hasMessageContaining("next 90 days");
  }

  @Test
  void rejectsNonexistentTimesAndUsesTheCorrectOffsetAcrossDaylightSaving() {
    assertThatThrownBy(
            () ->
                SessionScheduleSupport.resolveStart(
                    LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), zone, now, 90))
        .hasMessageContaining("daylight-saving");
    assertThat(
            SessionScheduleSupport.resolveStart(
                LocalDate.of(2026, 3, 8), LocalTime.of(3, 30), zone, now, 90))
        .isEqualTo(Instant.parse("2026-03-08T07:30:00Z"));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"18:10", "18:30:01", "18:30:00.001"})
  void rejectsTimesBetweenQuarterHours(String value) {
    assertThatThrownBy(
            () ->
                SessionScheduleSupport.resolveStart(
                    LocalDate.of(2026, 3, 2), LocalTime.parse(value), zone, now, 90))
        .hasMessageContaining("15-minute increments");
  }

  @Test
  void midnightIsAValidExplicitTimeOnAFutureDate() {
    assertThat(
            SessionScheduleSupport.resolveStart(
                LocalDate.of(2026, 3, 2), LocalTime.MIDNIGHT, zone, now, 90))
        .isEqualTo(Instant.parse("2026-03-02T05:00:00Z"));
  }
}
