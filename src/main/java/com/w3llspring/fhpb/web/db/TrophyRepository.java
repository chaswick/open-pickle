package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Trophy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrophyRepository extends JpaRepository<Trophy, Long> {

  boolean existsBySeason(LadderSeason season);

  long countBySeason(LadderSeason season);

  List<Trophy> findBySeasonOrderByDisplayOrderAscIdAsc(LadderSeason season);

  List<Trophy> findBySeasonAndStoryModeTrackerTrueOrderByDisplayOrderAscIdAsc(LadderSeason season);

  List<Trophy> findByBadgeSelectableByAllTrueOrderByDisplayOrderAscIdAsc();

  Optional<Trophy> findBySlug(String slug);

  @Query("select t from Trophy t left join fetch t.art where t.id = :id")
  Optional<Trophy> findByIdWithArt(@Param("id") Long id);

  @Query("select t from Trophy t left join fetch t.art left join fetch t.badgeArt where t.id = :id")
  Optional<Trophy> findByIdWithArtAndBadgeArt(@Param("id") Long id);

  @Query("select distinct t from Trophy t left join fetch t.season where t.id in :ids")
  List<Trophy> findAllByIdInWithSeason(@Param("ids") Collection<Long> ids);

  @Query("select distinct t.season from Trophy t where t.season is not null")
  List<LadderSeason> findDistinctSeasonsWithTrophies();

  @Query(
      "select distinct t.season from Trophy t where t.season is not null and t.season.state = :state")
  List<LadderSeason> findDistinctSeasonsWithTrophiesByState(
      @Param("state") LadderSeason.State state);
}
