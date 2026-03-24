package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.CompetitionDisplayNameReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompetitionDisplayNameReportRepository
    extends JpaRepository<CompetitionDisplayNameReport, Long> {

  boolean existsByReporterUserIdAndTargetUserId(Long reporterUserId, Long targetUserId);

  long countByTargetUserId(Long targetUserId);

  List<CompetitionDisplayNameReport> findByReporterUserId(Long reporterUserId);

  void deleteByTargetUserId(Long targetUserId);
}
