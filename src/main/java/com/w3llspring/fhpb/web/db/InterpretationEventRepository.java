package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.InterpretationEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterpretationEventRepository extends JpaRepository<InterpretationEvent, Long> {
  Optional<InterpretationEvent> findByEventUuid(String eventUuid);

  List<InterpretationEvent> findByMatchIdOrderByCreatedAtDesc(Long matchId);

  List<InterpretationEvent> findByLadderConfigIdAndTranscriptOrderByCreatedAtDesc(
      Long ladderConfigId, String transcript);

  long countByCurrentUserId(Long currentUserId);

  List<InterpretationEvent> findByCurrentUserIdOrderByCreatedAtAsc(
      Long currentUserId, Pageable pageable);
}
