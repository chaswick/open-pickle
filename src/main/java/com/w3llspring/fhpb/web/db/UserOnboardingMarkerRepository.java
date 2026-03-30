package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.UserOnboardingMarker;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOnboardingMarkerRepository extends JpaRepository<UserOnboardingMarker, Long> {

  boolean existsByUserIdAndMarkerKey(Long userId, String markerKey);

  Optional<UserOnboardingMarker> findByUserIdAndMarkerKey(Long userId, String markerKey);
}
