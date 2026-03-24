package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.UserDisplayNameAudit;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDisplayNameAuditRepository extends JpaRepository<UserDisplayNameAudit, Long> {

  List<UserDisplayNameAudit> findByLadderConfigIdOrderByChangedAtDesc(
      Long ladderConfigId, Pageable pageable);
}
