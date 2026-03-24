package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserMatchLogGateService {

  private final UserRepository userRepository;

  public UserMatchLogGateService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public MatchLogGateResult reserveMatchLogging(
      Long userId, Instant now, boolean rateLimitEnabled) {
    if (userId == null) {
      throw new IllegalArgumentException("User id is required.");
    }

    Instant currentTime = now != null ? now : Instant.now();
    User user =
        userRepository
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

    Instant lockoutUntil = user.getPassphraseTimeoutUntil();
    if (lockoutUntil != null && lockoutUntil.isAfter(currentTime)) {
      return MatchLogGateResult.blocked(
          user, "Too many recent blocked attempts. Try again in a few minutes.");
    }

    int consecutiveLogs = user.getConsecutiveMatchLogs();
    Instant lastMatchLogged = user.getLastMatchLoggedAt();
    if (lastMatchLogged == null) {
      consecutiveLogs = 0;
    } else {
      long minutesSinceLastMatch = Duration.between(lastMatchLogged, currentTime).toMinutes();
      if (minutesSinceLastMatch >= 5) {
        consecutiveLogs = 0;
      } else if (rateLimitEnabled && consecutiveLogs >= 3) {
        long waitMinutes = Math.max(1, 5 - minutesSinceLastMatch);
        return MatchLogGateResult.blocked(
            user,
            String.format(
                "Please wait %d more minutes before logging another match.", waitMinutes));
      }
    }

    user.setFailedPassphraseAttempts(0);
    user.setPassphraseTimeoutUntil(null);
    user.setLastMatchLoggedAt(currentTime);
    user.setConsecutiveMatchLogs(consecutiveLogs + 1);
    userRepository.save(user);

    return MatchLogGateResult.allowed(user);
  }

  public record MatchLogGateResult(User user, boolean allowed, String message) {

    public static MatchLogGateResult allowed(User user) {
      return new MatchLogGateResult(user, true, null);
    }

    public static MatchLogGateResult blocked(User user, String message) {
      return new MatchLogGateResult(user, false, message);
    }
  }
}
