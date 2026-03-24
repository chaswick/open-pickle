package com.w3llspring.fhpb.web.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RegistrationAbuseGuard {

  private final int maxPerIpPerHour;
  private final int maxPerIpPerDay;
  private final Duration minSubmitDelay;
  private final ClientIpResolver clientIpResolver;
  private final BoundedSlidingWindowRateLimiter perIpHour;
  private final BoundedSlidingWindowRateLimiter perIpDay;

  @Autowired
  public RegistrationAbuseGuard(
      @Value("${fhpb.security.registration.max-per-ip-per-hour:5}") int maxPerIpPerHour,
      @Value("${fhpb.security.registration.max-per-ip-per-day:20}") int maxPerIpPerDay,
      @Value("${fhpb.security.registration.min-seconds-to-submit:2}") long minSecondsToSubmit,
      @Value("${fhpb.security.registration.max-tracked-keys:10000}") int maxTrackedKeys,
      ClientIpResolver clientIpResolver) {
    this.maxPerIpPerHour = Math.max(1, maxPerIpPerHour);
    this.maxPerIpPerDay = Math.max(1, maxPerIpPerDay);
    this.minSubmitDelay = Duration.ofSeconds(Math.max(0, minSecondsToSubmit));
    this.clientIpResolver = clientIpResolver;
    this.perIpHour = new BoundedSlidingWindowRateLimiter(Duration.ofHours(1), maxTrackedKeys);
    this.perIpDay = new BoundedSlidingWindowRateLimiter(Duration.ofDays(1), maxTrackedKeys);
  }

  public RegistrationAbuseGuard(int maxPerIpPerHour, int maxPerIpPerDay, long minSecondsToSubmit) {
    this(maxPerIpPerHour, maxPerIpPerDay, minSecondsToSubmit, 10_000, new ClientIpResolver(""));
  }

  public RegistrationAbuseGuard(
      int maxPerIpPerHour, int maxPerIpPerDay, long minSecondsToSubmit, int maxTrackedKeys) {
    this(
        maxPerIpPerHour,
        maxPerIpPerDay,
        minSecondsToSubmit,
        maxTrackedKeys,
        new ClientIpResolver(""));
  }

  public String resolveClientIp(HttpServletRequest request) {
    return clientIpResolver.resolve(request);
  }

  public Decision evaluate(String clientIp, String honeypotValue, Long formServedAtEpochMs) {
    String ipKey = normalizeIp(clientIp);

    // Honeypot: if any value supplied, almost certainly a bot.
    if (StringUtils.hasText(honeypotValue)) {
      return Decision.block("honeypot");
    }

    // Time-to-submit: bots often post instantly.
    if (!minSubmitDelay.isZero()) {
      if (formServedAtEpochMs == null) {
        return Decision.block("missing_form_token");
      }
      long now = System.currentTimeMillis();
      long deltaMs = now - formServedAtEpochMs;
      if (deltaMs >= 0 && deltaMs < minSubmitDelay.toMillis()) {
        return Decision.block("too_fast");
      }
    }

    // Rate limit: per IP sliding windows.
    long now = System.currentTimeMillis();
    boolean hourOk = perIpHour.allow(ipKey, now, maxPerIpPerHour);
    if (!hourOk) {
      return Decision.block("rate_limit_hour");
    }

    boolean dayOk = perIpDay.allow(ipKey, now, maxPerIpPerDay);
    if (!dayOk) {
      return Decision.block("rate_limit_day");
    }

    return Decision.allow();
  }

  private String normalizeIp(String ip) {
    if (!StringUtils.hasText(ip)) {
      return "unknown";
    }
    return ip.trim().toLowerCase(java.util.Locale.ROOT);
  }

  int trackedIpHourKeyCount() {
    return perIpHour.trackedKeyCount();
  }

  public record Decision(boolean allowed, String reason) {
    public static Decision allow() {
      return new Decision(true, null);
    }

    public static Decision block(String reason) {
      return new Decision(false, reason);
    }
  }
}
