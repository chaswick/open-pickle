package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.PlayLocationAlias;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayLocationAliasRepository extends JpaRepository<PlayLocationAlias, Long> {

  @Query(
      "select a from PlayLocationAlias a "
          + "join fetch a.location l "
          + "where a.user.id = :userId "
          + "order by a.usageCount desc, a.lastUsedAt desc, a.id asc")
  List<PlayLocationAlias> findAllWithLocationByUserId(@Param("userId") Long userId);

  List<PlayLocationAlias> findByLocation_IdAndUser_IdOrderByUsageCountDescLastUsedAtDescIdAsc(
      Long locationId, Long userId);

  Optional<PlayLocationAlias> findByLocation_IdAndUser_IdAndNormalizedName(
      Long locationId, Long userId, String normalizedName);

  @Query(
      "select a from PlayLocationAlias a "
          + "where a.location.id = :locationId "
          + "and a.user.id <> :userId "
          + "order by a.usageCount desc, a.lastUsedAt desc, a.id asc")
  List<PlayLocationAlias> findOtherUsersByLocationId(
      @Param("locationId") Long locationId, @Param("userId") Long userId);
}
