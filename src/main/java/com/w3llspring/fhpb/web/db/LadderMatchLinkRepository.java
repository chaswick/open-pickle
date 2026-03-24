package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderMatchLink;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LadderMatchLinkRepository extends JpaRepository<LadderMatchLink, Long> {

  // OPTIMIZED: Eagerly fetch match and players to avoid N+1 queries
  @Query(
      "select distinct l from LadderMatchLink l "
          + "join fetch l.match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.season s "
          + "left join fetch s.ladderConfig "
          + "where l.season = :season "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc")
  List<LadderMatchLink> findTop3BySeasonNewestNonNullified(@Param("season") LadderSeason season);

  // OPTIMIZED: Eagerly fetch match and players to avoid N+1 queries
  @Query(
      "select distinct l from LadderMatchLink l "
          + "join fetch l.match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.season s "
          + "left join fetch s.ladderConfig "
          + "where (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc")
  List<LadderMatchLink> findTop2NewestNonNullified();

  @Query(
      "select l from LadderMatchLink l "
          + "join fetch l.match m "
          + "left join fetch m.season s "
          + "left join fetch s.ladderConfig "
          + "where l.match = :match and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED)")
  Optional<LadderMatchLink> findByMatch(@Param("match") Match match);

  @Query(
      "select l from LadderMatchLink l "
          + "join fetch l.match m "
          + "left join fetch m.season s "
          + "left join fetch s.ladderConfig "
          + "where l.match = :match")
  Optional<LadderMatchLink> findByMatchAnyState(@Param("match") Match match);

  // OPTIMIZED: Use JOIN FETCH to eagerly load Match and User entities in single query (avoids N+1)
  @Query(
      "select distinct l from LadderMatchLink l "
          + "join fetch l.match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.season s "
          + "left join fetch s.ladderConfig "
          + "where l.season = :season and l.match.id in :matchIds "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by l.match.playedAt desc")
  List<LadderMatchLink> findBySeasonAndMatchIds(
      @Param("season") LadderSeason season, @Param("matchIds") List<Long> matchIds);

  @Query(
      "select distinct l from LadderMatchLink l "
          + "join fetch l.match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.season s "
          + "left join fetch s.ladderConfig "
          + "where l.match.id in :matchIds "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by l.match.playedAt desc")
  List<LadderMatchLink> findByMatchIds(@Param("matchIds") List<Long> matchIds);

  // OPTIMIZED: Eagerly fetch match and players to avoid N+1 queries
  @Query(
      "select distinct l from LadderMatchLink l "
          + "join fetch l.match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.season s "
          + "left join fetch s.ladderConfig "
          + "where l.season = :season "
          + "and (m.a1.id = :userId or m.a2.id = :userId or m.b1.id = :userId or m.b2.id = :userId) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc")
  List<LadderMatchLink> findTop3BySeasonAndUserNewestNonNullified(
      @Param("season") LadderSeason season, @Param("userId") Long userId);
}
