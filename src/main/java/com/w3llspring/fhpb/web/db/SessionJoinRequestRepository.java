package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.SessionJoinRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SessionJoinRequestRepository extends JpaRepository<SessionJoinRequest, Long> {

  Optional<SessionJoinRequest> findByLadderConfigIdAndRequesterUserId(
      Long ladderConfigId, Long requesterUserId);

  List<SessionJoinRequest> findByLadderConfigIdAndStatusOrderByRequestedAtAsc(
      Long ladderConfigId, SessionJoinRequest.Status status);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from SessionJoinRequest r where r.id = :id")
  Optional<SessionJoinRequest> lockById(Long id);
}
