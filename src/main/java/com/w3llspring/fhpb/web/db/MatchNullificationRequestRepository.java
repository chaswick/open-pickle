package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchNullificationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MatchNullificationRequestRepository extends JpaRepository<MatchNullificationRequest, Long> {

    @Query("select distinct r from MatchNullificationRequest r " +
            "join fetch r.match m " +
            "left join fetch r.player p " +
            "where m.id in :matchIds " +
            "and r.expiresAt > :now")
    List<MatchNullificationRequest> findActiveByMatchIdIn(@Param("matchIds") Collection<Long> matchIds,
                                                          @Param("now") Instant now);

    @Query("select distinct r from MatchNullificationRequest r " +
            "join fetch r.match m " +
            "left join fetch r.player p " +
            "where m = :match " +
            "and r.expiresAt > :now")
    List<MatchNullificationRequest> findActiveByMatch(@Param("match") Match match,
                                                      @Param("now") Instant now);

    @Query("select distinct r from MatchNullificationRequest r " +
            "join fetch r.match m " +
            "left join fetch r.player p " +
            "where m = :match")
    List<MatchNullificationRequest> findByMatchWithPlayer(@Param("match") Match match);

    int deleteByExpiresAtBefore(Instant cutoff);

    void deleteByMatch(Match match);
}
