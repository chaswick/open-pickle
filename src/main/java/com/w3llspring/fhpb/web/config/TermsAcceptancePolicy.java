package com.w3llspring.fhpb.web.config;

import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TermsAcceptancePolicy {

  private static final ZoneId DEFAULT_LEGAL_ZONE = ZoneId.of("America/New_York");

  private final Instant requiredAcknowledgedAt;

  public TermsAcceptancePolicy(
      @Value("${fhpb.legal.terms-required-at:}") String requiredAcknowledgedAtRaw) {
    this.requiredAcknowledgedAt = parseRequiredAcknowledgedAt(requiredAcknowledgedAtRaw);
  }

  public boolean requiresAcceptance(User user) {
    return requiresAcceptance(user != null ? user.getAcknowledgedTermsAt() : null);
  }

  public boolean requiresAcceptance(Instant acknowledgedAt) {
    if (acknowledgedAt == null) {
      return true;
    }
    return requiredAcknowledgedAt != null && acknowledgedAt.isBefore(requiredAcknowledgedAt);
  }

  public Instant getRequiredAcknowledgedAt() {
    return requiredAcknowledgedAt;
  }

  private Instant parseRequiredAcknowledgedAt(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    String value = raw.trim();

    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ignored) {
      // Try the more human-authored forms below.
    }

    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException ignored) {
      // Try the next supported form.
    }

    try {
      return ZonedDateTime.parse(value).toInstant();
    } catch (DateTimeParseException ignored) {
      // Try date-only next.
    }

    try {
      return LocalDate.parse(value).atStartOfDay(DEFAULT_LEGAL_ZONE).toInstant();
    } catch (DateTimeParseException ignored) {
      throw new IllegalStateException(
          "Invalid fhpb.legal.terms-required-at value '"
              + value
              + "'. Use ISO-8601 instant, offset datetime, zoned datetime, or date.");
    }
  }
}
