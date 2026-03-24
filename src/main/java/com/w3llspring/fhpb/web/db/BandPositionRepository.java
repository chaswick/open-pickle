package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.BandPosition;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BandPositionRepository extends JpaRepository<BandPosition, Long> {
  Optional<BandPosition> findBySeasonAndUser(LadderSeason season, User user);

  /** Batch lookup helper used by high traffic dashboards to avoid N+1 queries. */
  List<BandPosition> findBySeasonAndUserIdIn(LadderSeason season, Collection<Long> userIds);

  List<BandPosition> findBySeason(LadderSeason season);

  void deleteBySeason(LadderSeason season);

  void deleteBySeasonAndUserIdNotIn(LadderSeason season, Collection<Long> userIds);
}
