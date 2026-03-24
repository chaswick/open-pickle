package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.User;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MatchLoggingQuotaService {

  private static final Duration WINDOW = Duration.ofDays(7);

  private final MatchRepository matchRepository;
  private final boolean enabled;
  private final int weeklyLimit;

  public MatchLoggingQuotaService(
      MatchRepository matchRepository,
      @Value("${fhpb.match-log.weekly-quota.enabled:true}") boolean enabled,
      @Value("${fhpb.match-log.weekly-quota.max-per-user:100}") int weeklyLimit) {
    this.matchRepository = matchRepository;
    this.enabled = enabled;
    this.weeklyLimit = Math.max(1, weeklyLimit);
  }

  public QuotaStatus evaluate(User user) {
    if (!enabled || user == null || user.getId() == null) {
      return QuotaStatus.allowed(weeklyLimit, 0L);
    }
    Instant windowStart = Instant.now().minus(WINDOW);
    long count =
        matchRepository.countByLoggedBy_IdAndCreatedAtGreaterThanEqual(user.getId(), windowStart);
    if (count >= weeklyLimit) {
      return QuotaStatus.blocked(weeklyLimit, count);
    }
    return QuotaStatus.allowed(weeklyLimit, count);
  }

  public static final class QuotaStatus {
    private final boolean allowed;
    private final int limit;
    private final long currentCount;

    private QuotaStatus(boolean allowed, int limit, long currentCount) {
      this.allowed = allowed;
      this.limit = limit;
      this.currentCount = currentCount;
    }

    public static QuotaStatus allowed(int limit, long currentCount) {
      return new QuotaStatus(true, limit, currentCount);
    }

    public static QuotaStatus blocked(int limit, long currentCount) {
      return new QuotaStatus(false, limit, currentCount);
    }

    public boolean allowed() {
      return allowed;
    }

    public int limit() {
      return limit;
    }

    public long currentCount() {
      return currentCount;
    }
  }
}
