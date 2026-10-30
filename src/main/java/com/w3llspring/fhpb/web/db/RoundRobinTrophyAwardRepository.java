package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoundRobinTrophyAwardRepository
    extends JpaRepository<RoundRobinTrophyAward, Long> {
  @Query(
      "select a from RoundRobinTrophyAward a join fetch a.user join fetch a.trophy where a.roundRobin.id = :id order by a.trophy.displayOrder, a.trophy.id, a.user.id")
  List<RoundRobinTrophyAward> findForRun(@Param("id") Long id);

  @Query(
      "select a.user.id as userId, count(a.id) as total, min(a.awardedAt) as firstAwardedAt, max(a.awardedAt) as lastAwardedAt from RoundRobinTrophyAward a where a.trophy = :trophy group by a.user.id")
  List<OwnerSummary> summarizeOwners(@Param("trophy") Trophy trophy);

  interface OwnerSummary {
    Long getUserId();

    long getTotal();

    java.time.Instant getFirstAwardedAt();

    java.time.Instant getLastAwardedAt();
  }
}
