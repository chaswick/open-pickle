package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.PlayLocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayLocationRepository extends JpaRepository<PlayLocation, Long> {

  @Query(
      "select l from PlayLocation l "
          + "where l.latitude between :minLat and :maxLat "
          + "and l.longitude between :minLon and :maxLon")
  List<PlayLocation> findWithinBoundingBox(
      @Param("minLat") double minLat,
      @Param("maxLat") double maxLat,
      @Param("minLon") double minLon,
      @Param("maxLon") double maxLon);

  long countByCreatedBy_IdAndCreatedAtGreaterThanEqual(Long userId, java.time.Instant since);
}
