package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.UserPushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface UserPushSubscriptionRepository extends JpaRepository<UserPushSubscription, Long> {

  Optional<UserPushSubscription> findByEndpoint(String endpoint);

  List<UserPushSubscription> findByUserId(Long userId);

  @Transactional
  long deleteByUserIdAndEndpoint(Long userId, String endpoint);
}
