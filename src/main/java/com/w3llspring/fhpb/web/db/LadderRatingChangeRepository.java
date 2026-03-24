package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderRatingChange;
import com.w3llspring.fhpb.web.model.LadderSeason;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LadderRatingChangeRepository extends JpaRepository<LadderRatingChange, Long> {

  void deleteBySeason(LadderSeason season);

  @Query(
      "select rc from LadderRatingChange rc "
          + "where rc.season = :season and rc.user.id = :userId "
          + "order by rc.occurredAt desc, rc.id desc")
  List<LadderRatingChange> findRecentBySeasonAndUser(
      @Param("season") LadderSeason season, @Param("userId") Long userId, Pageable pageable);
}
