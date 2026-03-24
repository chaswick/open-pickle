package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.RoundRobin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundRobinRepository extends JpaRepository<RoundRobin, Long> {

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
