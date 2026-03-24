package com.w3llspring.fhpb.web.service.user;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountSettingsService {

  private final UserRepository userRepository;

  public UserAccountSettingsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional
  public User acknowledgeTerms(Long userId, Instant acknowledgedAt) {
    User user = findUserForUpdate(userId);
    Instant effectiveAcknowledgedAt = acknowledgedAt != null ? acknowledgedAt : Instant.now();
    if (!effectiveAcknowledgedAt.equals(user.getAcknowledgedTermsAt())) {
      user.setAcknowledgedTermsAt(effectiveAcknowledgedAt);
      userRepository.save(user);
    }
    return user;
  }

  @Transactional
  public User enableAppUi(Long userId) {
    User user = findUserForUpdate(userId);
    if (!user.isAppUiEnabled()) {
      user.setAppUiEnabled(true);
      userRepository.save(user);
    }
    return user;
  }

  @Transactional
  public User updateTimeZone(Long userId, String timeZone) {
    User user = findUserForUpdate(userId);
    user.setTimeZone(timeZone);
    userRepository.save(user);
    return user;
  }

  @Transactional
  public User updateBadgeSlot1(Long userId, Trophy badgeSlot1Trophy) {
    User user = findUserForUpdate(userId);
    user.setBadgeSlot1Trophy(badgeSlot1Trophy);
    user.setBadgeSlot2Trophy(null);
    user.setBadgeSlot3Trophy(null);
    userRepository.save(user);
    return user;
  }

  @Transactional
  public void touchLastSeen(Long userId, Instant lastSeenAt) {
    if (userId == null) {
      return;
    }
    userRepository.updateLastSeenAtById(userId, lastSeenAt != null ? lastSeenAt : Instant.now());
  }

  private User findUserForUpdate(Long userId) {
    if (userId == null) {
      throw new IllegalArgumentException("User is required.");
    }
    return userRepository
        .findByIdForUpdate(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found."));
  }
}
