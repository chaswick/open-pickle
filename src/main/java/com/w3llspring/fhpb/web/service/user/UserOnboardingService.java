package com.w3llspring.fhpb.web.service.user;

import com.w3llspring.fhpb.web.db.UserOnboardingMarkerRepository;
import com.w3llspring.fhpb.web.model.UserOnboardingMarker;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserOnboardingService {

  public static final String HOME_TOUR_V1 = "home_tour_v1";

  private final UserOnboardingMarkerRepository userOnboardingMarkerRepository;

  public UserOnboardingService(UserOnboardingMarkerRepository userOnboardingMarkerRepository) {
    this.userOnboardingMarkerRepository = userOnboardingMarkerRepository;
  }

  @Transactional(readOnly = true)
  public boolean hasCompleted(Long userId, String markerKey) {
    return userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
        requireUserId(userId), normalizeMarkerKey(markerKey));
  }

  @Transactional(readOnly = true)
  public boolean shouldShow(Long userId, String markerKey) {
    return !hasCompleted(userId, markerKey);
  }

  @Transactional
  public void markCompleted(Long userId, String markerKey) {
    Long requiredUserId = requireUserId(userId);
    String requiredMarkerKey = normalizeMarkerKey(markerKey);
    if (userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(requiredUserId, requiredMarkerKey)) {
      return;
    }

    UserOnboardingMarker marker = new UserOnboardingMarker();
    marker.setUserId(requiredUserId);
    marker.setMarkerKey(requiredMarkerKey);
    marker.setCompletedAt(Instant.now());
    try {
      userOnboardingMarkerRepository.saveAndFlush(marker);
    } catch (DataIntegrityViolationException ex) {
      if (!userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
          requiredUserId, requiredMarkerKey)) {
        throw ex;
      }
    }
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new IllegalArgumentException("User is required.");
    }
    return userId;
  }

  private String normalizeMarkerKey(String markerKey) {
    if (markerKey == null || markerKey.isBlank()) {
      throw new IllegalArgumentException("Marker key is required.");
    }
    return markerKey.trim();
  }
}
