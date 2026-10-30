package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.RoundRobin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundRobinRepository extends JpaRepository<RoundRobin, Long> {

  @org.springframework.data.jpa.repository.Query(
      """
      select rr.id from RoundRobin rr left join rr.sessionConfig s
      where rr.trophiesProcessed = false
        and (rr.endedAt is not null or s.status = 'ARCHIVED' or s.expiresAt <= :now
          or (exists (select e.id from RoundRobinEntry e where e.roundRobin = rr and e.bye = false)
            and not exists (select e.id from RoundRobinEntry e where e.roundRobin = rr
              and e.bye = false and e.matchId is null)))
        and not exists (select e.id from RoundRobinEntry e, Match m
          where e.roundRobin = rr and e.bye = false and e.matchId = m.id
            and (m.state is null or m.state not in ('CONFIRMED', 'NULLIFIED')))
      order by rr.id
      """)
  java.util.List<Long> findUnprocessedFinishedRuns(
      @org.springframework.data.repository.query.Param("now") java.time.Instant now,
      org.springframework.data.domain.Pageable page);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      """
      update RoundRobin rr set rr.trophiesProcessed = false, rr.trophiesProcessedAt = null
      where rr.id = :id and rr.trophiesProcessed = true
      """)
  int resetTrophyProcessing(@org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.data.jpa.repository.Query(
      "select distinct e.roundRobin.id from RoundRobinEntry e where e.matchId = :id and e.roundRobin.trophiesProcessed = true")
  java.util.List<Long> findProcessedRunIdsForMatch(
      @org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select rr from RoundRobin rr where rr.id = :id")
  java.util.Optional<RoundRobin> findByIdForUpdate(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<RoundRobin> findBySeason(com.w3llspring.fhpb.web.model.LadderSeason season);

  org.springframework.data.domain.Page<RoundRobin> findBySeason(
      com.w3llspring.fhpb.web.model.LadderSeason season,
      org.springframework.data.domain.Pageable pageable);

  java.util.List<RoundRobin> findBySeasonAndSessionConfigIsNull(
      com.w3llspring.fhpb.web.model.LadderSeason season);

  org.springframework.data.domain.Page<RoundRobin> findBySeasonAndSessionConfigIsNull(
      com.w3llspring.fhpb.web.model.LadderSeason season,
      org.springframework.data.domain.Pageable pageable);

  java.util.List<RoundRobin> findBySessionConfig(
      com.w3llspring.fhpb.web.model.LadderConfig sessionConfig);

  org.springframework.data.domain.Page<RoundRobin> findBySessionConfig(
      com.w3llspring.fhpb.web.model.LadderConfig sessionConfig,
      org.springframework.data.domain.Pageable pageable);

  java.util.List<RoundRobin> findBySessionConfigId(Long sessionConfigId);

  @org.springframework.data.jpa.repository.Query(
      "select rr from RoundRobin rr where rr.sessionConfig is null and rr.season.ladderConfig.id = :ladderConfigId")
  java.util.List<RoundRobin> findByLadderConfigId(
      @org.springframework.data.repository.query.Param("ladderConfigId") Long ladderConfigId);
}
