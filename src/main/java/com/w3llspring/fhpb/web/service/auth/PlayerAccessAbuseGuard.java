package com.w3llspring.fhpb.web.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PlayerAccessAbuseGuard {
  private final ClientIpResolver ips;
  private final int joinsPerHour;
  private final BoundedSlidingWindowRateLimiter previews =
      new BoundedSlidingWindowRateLimiter(Duration.ofHours(1), 10000);
  private final BoundedSlidingWindowRateLimiter joins =
      new BoundedSlidingWindowRateLimiter(Duration.ofHours(1), 10000);
  private final BoundedSlidingWindowRateLimiter dailyJoins =
      new BoundedSlidingWindowRateLimiter(Duration.ofDays(1), 10000);
  private final BoundedSlidingWindowRateLimiter recoveries =
      new BoundedSlidingWindowRateLimiter(Duration.ofHours(1), 10000);
  private final BoundedSlidingWindowRateLimiter emailConnections =
      new BoundedSlidingWindowRateLimiter(Duration.ofHours(1), 10000);

  public PlayerAccessAbuseGuard(
      ClientIpResolver ips,
      @Value("${fhpb.security.guest-access.max-per-ip-per-hour:64}") int joinsPerHour) {
    this.ips = ips;
    this.joinsPerHour = Math.max(1, joinsPerHour);
  }

  public boolean allowPreview(HttpServletRequest request) {
    return previews.allow(ips.resolve(request), System.currentTimeMillis(), 240);
  }

  public boolean allowJoin(HttpServletRequest request) {
    String ip = ips.resolve(request);
    long now = System.currentTimeMillis();
    return joins.allow(ip, now, joinsPerHour)
        && dailyJoins.allow(ip, now, Math.max(256, joinsPerHour));
  }

  public boolean allowRecovery(HttpServletRequest request) {
    return recoveries.allow(ips.resolve(request), System.currentTimeMillis(), 10);
  }

  public boolean allowEmailConnection(HttpServletRequest request, Long userId, String email) {
    long now = System.currentTimeMillis();
    return emailConnections.allow("ip:" + ips.resolve(request), now, 10)
        && emailConnections.allow("user:" + userId, now, 3)
        && emailConnections.allow("email:" + email, now, 3);
  }
}
