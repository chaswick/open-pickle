package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.RoundRobin;
import com.w3llspring.fhpb.web.model.RoundRobinEntry;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RoundRobinEntryRepository extends JpaRepository<RoundRobinEntry, Long> {
  List<RoundRobinEntry> findByRoundRobinOrderByRoundNumberAsc(RoundRobin roundRobin);

  // OPTIMIZED: Eagerly fetch all 4 players to avoid N+1 queries when displaying round-robin entries
  @org.springframework.data.jpa.repository.Query(
      "select distinct e from RoundRobinEntry e "
          + "left join fetch e.a1 "
          + "left join fetch e.a2 "
          + "left join fetch e.b1 "
          + "left join fetch e.b2 "
          + "where e.roundRobin = :roundRobin "
          + "order by e.roundNumber asc")
  List<RoundRobinEntry> findByRoundRobinOrderByRoundNumberAscWithUsers(
      @org.springframework.data.repository.query.Param("roundRobin") RoundRobin roundRobin);

  List<RoundRobinEntry> findByRoundRobinAndRoundNumber(RoundRobin roundRobin, int roundNumber);

  // OPTIMIZED: Eagerly fetch all 4 players for a specific round
  @org.springframework.data.jpa.repository.Query(
      "select distinct e from RoundRobinEntry e "
          + "left join fetch e.a1 "
          + "left join fetch e.a2 "
          + "left join fetch e.b1 "
          + "left join fetch e.b2 "
          + "where e.roundRobin = :roundRobin and e.roundNumber = :roundNumber")
  List<RoundRobinEntry> findByRoundRobinAndRoundNumberWithUsers(
      @org.springframework.data.repository.query.Param("roundRobin") RoundRobin roundRobin,
      @org.springframework.data.repository.query.Param("roundNumber") int roundNumber);

  // Lock only the assignment row. Fetch joins with a write lock also lock the
  // season and players on MySQL, conflicting with standings replay's FK locks.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from RoundRobinEntry e where e.id = :id")
  Optional<RoundRobinEntry> lockRowById(
      @org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.transaction.annotation.Transactional
  default Optional<RoundRobinEntry> findByIdWithUsersForUpdate(Long id) {
    return lockRowById(id).flatMap(locked -> findByIdWithUsers(id));
  }

  @org.springframework.data.jpa.repository.Query(
      "select distinct e from RoundRobinEntry e "
          + "left join fetch e.roundRobin rr "
          + "left join fetch rr.season s "
          + "left join fetch s.ladderConfig "
          + "left join fetch rr.sessionConfig "
          + "left join fetch e.a1 "
          + "left join fetch e.a2 "
          + "left join fetch e.b1 "
          + "left join fetch e.b2 "
          + "where e.id = :id")
  Optional<RoundRobinEntry> findByIdWithUsers(
      @org.springframework.data.repository.query.Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select distinct e from RoundRobinEntry e "
          + "left join fetch e.roundRobin rr "
          + "left join fetch rr.season s "
          + "left join fetch s.ladderConfig "
          + "left join fetch rr.sessionConfig "
          + "left join fetch e.a1 "
          + "left join fetch e.a2 "
          + "left join fetch e.b1 "
          + "left join fetch e.b2 "
          + "where e.roundRobin = :roundRobin and e.roundNumber = :roundNumber")
  List<RoundRobinEntry> findByRoundRobinAndRoundNumberWithUsersForUpdate(
      @org.springframework.data.repository.query.Param("roundRobin") RoundRobin roundRobin,
      @org.springframework.data.repository.query.Param("roundNumber") int roundNumber);

  List<RoundRobinEntry> findByMatchId(Long matchId);
}
