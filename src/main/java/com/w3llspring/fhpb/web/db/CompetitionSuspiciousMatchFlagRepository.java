package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.CompetitionSuspiciousMatchFlag;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompetitionSuspiciousMatchFlagRepository
    extends JpaRepository<CompetitionSuspiciousMatchFlag, Long> {

  void deleteByMatch(Match match);

  @Query(
      "select f from CompetitionSuspiciousMatchFlag f "
          + "where f.season = :season "
          + "order by f.createdAt desc, f.severity desc, f.id desc")
  List<CompetitionSuspiciousMatchFlag> findRecentBySeason(
      @Param("season") LadderSeason season, Pageable pageable);
}
