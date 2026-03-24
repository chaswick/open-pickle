package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LadderStandingRepository extends JpaRepository<LadderStanding, Long> {

  // Standings table for a season, ordered by rank ascending
  List<LadderStanding> findBySeasonOrderByRankNoAsc(LadderSeason season);

  // OPTIMIZED: Eagerly fetch user to avoid N+1 queries when displaying standings
  @org.springframework.data.jpa.repository.Query(
      "select distinct s from LadderStanding s "
          + "left join fetch s.user "
          + "where s.season = :season "
          + "order by s.rankNo asc")
  List<LadderStanding> findBySeasonOrderByRankNoAscWithUser(
      @org.springframework.data.repository.query.Param("season") LadderSeason season);

  java.util.Optional<LadderStanding> findBySeasonAndUser(
      LadderSeason season, com.w3llspring.fhpb.web.model.User user);

  List<LadderStanding> findByUser(User user);
}
