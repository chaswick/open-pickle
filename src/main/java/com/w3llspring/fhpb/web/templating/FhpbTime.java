package com.w3llspring.fhpb.web.templating;

import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component("fhpbTime")
public class FhpbTime {

  private static final ZoneId EASTERN_TIME = ZoneId.of("America/New_York");

  private static final DateTimeFormatter DEFAULT_DATE_TIME_PATTERN =
      DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US);

  private static final DateTimeFormatter DATE_TIME_24H_PATTERN =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US);

  private static final DateTimeFormatter DEFAULT_DATE_PATTERN =
      DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

  private static final DateTimeFormatter ISO_DATE_PATTERN =
      DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);

  public String eastern(Object value) {
    return format(value, DEFAULT_DATE_TIME_PATTERN.withZone(EASTERN_TIME), EASTERN_TIME);
  }

  public String easternDateTime24(Object value) {
    return format(value, DATE_TIME_24H_PATTERN.withZone(EASTERN_TIME), EASTERN_TIME);
  }

  public String display(Object value, Object principal) {
    ZoneId userZone = resolveZone(principal);
    return format(value, DEFAULT_DATE_TIME_PATTERN.withZone(userZone), userZone);
  }

  public String displayDateTime24(Object value, Object principal) {
    ZoneId userZone = resolveZone(principal);
    return format(value, DATE_TIME_24H_PATTERN.withZone(userZone), userZone);
  }

  public String displayDate(Object value, Object principal) {
    ZoneId userZone = resolveZone(principal);
    return format(value, DEFAULT_DATE_PATTERN.withZone(userZone), userZone);
  }

  public String displayIsoDate(Object value, Object principal) {
    ZoneId userZone = resolveZone(principal);
    return format(value, ISO_DATE_PATTERN.withZone(userZone), userZone);
  }

  public String pattern(Object value, Object principal, String pattern) {
    ZoneId userZone = resolveZone(principal);
    if (pattern == null || pattern.isBlank()) {
      return format(value, DEFAULT_DATE_TIME_PATTERN.withZone(userZone), userZone);
    }
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern, Locale.US).withZone(userZone);
    return format(value, fmt, userZone);
  }

  /** Exposes the effective ZoneId (IANA id string) for templates/JS. */
  public String zoneId(Object principal) {
    return resolveZone(principal).getId();
  }

  private static ZoneId resolveZone(Object principal) {
    if (principal instanceof CustomUserDetails cud) {
      return resolveZoneFromUser(AuthenticatedUserSupport.refresh(cud.getUserObject()));
    }
    if (principal instanceof User user) {
      return resolveZoneFromUser(AuthenticatedUserSupport.refresh(user));
    }
    return EASTERN_TIME;
  }

  private static ZoneId resolveZoneFromUser(User user) {
    if (user == null) return EASTERN_TIME;
    String tz = user.getTimeZone();
    if (tz == null || tz.isBlank()) return EASTERN_TIME;
    try {
      return ZoneId.of(tz.trim());
    } catch (Exception ignored) {
      return EASTERN_TIME;
    }
  }

  private static String format(Object value, DateTimeFormatter formatter, ZoneId outputZone) {
    if (value == null) return "";

    if (value instanceof Instant instant) {
      return formatter.format(instant);
    }
    if (value instanceof OffsetDateTime odt) {
      return formatter.format(odt.toInstant());
    }
    if (value instanceof ZonedDateTime zdt) {
      return formatter.format(zdt.toInstant());
    }
    if (value instanceof LocalDateTime ldt) {
      // LocalDateTime is ambiguous; legacy behavior treats it as Eastern.
      Instant legacyInstant = ldt.atZone(EASTERN_TIME).toInstant();
      return formatter.withZone(outputZone).format(legacyInstant);
    }

    // Fallback: avoid surprising errors in templates.
    return value.toString();
  }
}
