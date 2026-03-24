package com.w3llspring.fhpb.web.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ForgotPasswordAbuseGuard {

  private final int maxPerIpPerHour;
  private final int maxPerEmailPerHour;
  private final ClientIpResolver clientIpResolver;
  private final BoundedSlidingWindowRateLimiter perIpHour;
  private final BoundedSlidingWindowRateLimiter perEmailHour;

  @Autowired
  public ForgotPasswordAbuseGuard(
      @Value("${fhpb.security.forgot-password.max-per-ip-per-hour:10}") int maxPerIpPerHour,
      @Value("${fhpb.security.forgot-password.max-per-email-per-hour:3}") int maxPerEmailPerHour,
      @Value("${fhpb.security.forgot-password.max-tracked-keys:10000}") int maxTrackedKeys,
      ClientIpResolver clientIpResolver) {
    this.maxPerIpPerHour = Math.max(1, maxPerIpPerHour);
    this.maxPerEmailPerHour = Math.max(1, maxPerEmailPerHour);
    this.clientIpResolver = clientIpResolver;
    this.perIpHour = new BoundedSlidingWindowRateLimiter(Duration.ofHours(1), maxTrackedKeys);
    this.perEmailHour = new BoundedSlidingWindowRateLimiter(Duration.ofHours(1), maxTrackedKeys);
  }

  public ForgotPasswordAbuseGuard(int maxPerIpPerHour, int maxPerEmailPerHour) {
    this(maxPerIpPerHour, maxPerEmailPerHour, 10_000, new ClientIpResolver(""));
  }

  public ForgotPasswordAbuseGuard(int maxPerIpPerHour, int maxPerEmailPerHour, int maxTrackedKeys) {
    this(maxPerIpPerHour, maxPerEmailPerHour, maxTrackedKeys, new ClientIpResolver(""));
  }

  public Decision evaluate(HttpServletRequest request, String email) {
    long now = System.currentTimeMillis();
    String clientIp = normalize(resolveClientIp(request));
    boolean ipOk = perIpHour.allow(clientIp, now, maxPerIpPerHour);
    if (!ipOk) {
      return Decision.block("rate_limit_ip_hour");
    }

    String normalizedEmail = normalize(email);
    if (!StringUtils.hasText(normalizedEmail)) {
      return Decision.allow();
    }

    boolean emailOk = perEmailHour.allow(normalizedEmail, now, maxPerEmailPerHour);
    if (!emailOk) {
      return Decision.block("rate_limit_email_hour");
    }

    return Decision.allow();
  }

  public String resolveClientIp(HttpServletRequest request) {
    return clientIpResolver.resolve(request);
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toLowerCase(java.util.Locale.ROOT);
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
