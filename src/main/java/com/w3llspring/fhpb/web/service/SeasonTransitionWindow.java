package com.w3llspring.fhpb.web.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Result of evaluating whether a ladder may perform a season transition (start/end) now. */
public class SeasonTransitionWindow {

  private final boolean allowed;
  private final Instant nextAllowedAt; // null if allowed or unknown
  private final String message; // optional user-friendly explanation

  public SeasonTransitionWindow(boolean allowed, Instant nextAllowedAt, String message) {
    this.allowed = allowed;
    this.nextAllowedAt = nextAllowedAt;
    this.message = message == null ? "" : message;
  }

  public static SeasonTransitionWindow ok() {
    return new SeasonTransitionWindow(true, null, "");
  }

  public static SeasonTransitionWindow blocked(Instant nextAllowedAt, String message) {
    return new SeasonTransitionWindow(false, nextAllowedAt, message);
  }

  public boolean isAllowed() {
    return allowed;
  }

  public Instant getNextAllowedAt() {
    return nextAllowedAt;
  }

  public String getMessage() {
    return message;
  }

  /** Returns 0 if allowed or nextAllowedAt is null/past. */
  public Duration getWaitDuration() {
    if (allowed || nextAllowedAt == null) return Duration.ZERO;
    Instant now = Instant.now();
    return now.isBefore(nextAllowedAt) ? Duration.between(now, nextAllowedAt) : Duration.ZERO;
  }

  @Override
  public String toString() {
    return "SeasonTransitionWindow{"
        + "allowed="
        + allowed
        + ", nextAllowedAt="
        + nextAllowedAt
        + ", message='"
        + message
        + '\''
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SeasonTransitionWindow)) return false;
    SeasonTransitionWindow that = (SeasonTransitionWindow) o;
    return allowed == that.allowed
        && Objects.equals(nextAllowedAt, that.nextAllowedAt)
        && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(allowed, nextAllowedAt, message);
  }
}
