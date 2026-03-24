package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.PlayLocationCheckIn;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayLocationCheckInRepository extends JpaRepository<PlayLocationCheckIn, Long> {

  interface ActiveLocationCount {
    Long getLocationId();

    long getUserCount();
  }

  @Query(
      "select c from PlayLocationCheckIn c "
          + "join fetch c.location l "
          + "where c.user.id = :userId "
          + "and c.expiresAt > :now "
          + "order by c.checkedInAt desc, c.id desc")
  List<PlayLocationCheckIn> findActiveWithLocationByUserId(
      @Param("userId") Long userId, @Param("now") Instant now);

  @Modifying
  @Query(
      "update PlayLocationCheckIn c "
          + "set c.expiresAt = :now, c.endedAt = :now "
          + "where c.user.id = :userId "
          + "and c.expiresAt > :now")
  int expireActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);

  @Query(
      "select c.location.id as locationId, count(distinct c.user.id) as userCount "
          + "from PlayLocationCheckIn c "
          + "where c.location.id in :locationIds "
          + "and c.expiresAt > :now "
          + "group by c.location.id")
  List<ActiveLocationCount> countActiveUsersByLocationIds(
      @Param("locationIds") Collection<Long> locationIds, @Param("now") Instant now);

  @Query(
      "select c.location.id as locationId, count(distinct c.user.id) as userCount "
          + "from PlayLocationCheckIn c "
          + "where c.location.id in :locationIds "
          + "and c.user.id in :userIds "
          + "and c.expiresAt > :now "
          + "group by c.location.id")
  List<ActiveLocationCount> countActiveUsersByLocationIdsAndUserIds(
      @Param("locationIds") Collection<Long> locationIds,
      @Param("userIds") Collection<Long> userIds,
      @Param("now") Instant now);

  Optional<PlayLocationCheckIn> findTopByUser_IdOrderByCheckedInAtDescIdDesc(Long userId);

  long countByUser_IdAndCheckedInAtGreaterThanEqual(Long userId, Instant since);
}
