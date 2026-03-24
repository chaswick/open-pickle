package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.TrophyCatalogEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrophyCatalogEntryRepository extends JpaRepository<TrophyCatalogEntry, Long> {

  boolean existsByDefaultTemplateTrue();

  List<TrophyCatalogEntry> findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc();

  List<TrophyCatalogEntry> findByDefaultTemplateTrueAndSeasonOrderByDisplayOrderAscIdAsc(
      LadderSeason season);

  Optional<TrophyCatalogEntry> findBySlug(String slug);

  @Query("select c from TrophyCatalogEntry c left join fetch c.art where c.id = :id")
  Optional<TrophyCatalogEntry> findByIdWithArt(@Param("id") Long id);

  @Query(
      "select c from TrophyCatalogEntry c left join fetch c.art left join fetch c.badgeArt where c.id = :id")
  Optional<TrophyCatalogEntry> findByIdWithArtAndBadgeArt(@Param("id") Long id);
}
