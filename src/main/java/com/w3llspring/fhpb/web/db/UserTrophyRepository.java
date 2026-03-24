package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserTrophy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTrophyRepository extends JpaRepository<UserTrophy, Long> {

  boolean existsByUserAndTrophy(User user, Trophy trophy);

  Optional<UserTrophy> findByUserAndTrophy(User user, Trophy trophy);

  List<UserTrophy> findByUserAndTrophyIn(User user, Collection<Trophy> trophies);

  List<UserTrophy> findByTrophy(Trophy trophy);

  long countByTrophy(Trophy trophy);

  boolean existsByUserAndTrophySeasonId(User user, Long seasonId);

  @Query(
      "select ut.trophy.id as trophyId, count(distinct ut.user.id) as owners "
          + "from UserTrophy ut where ut.trophy in :trophies group by ut.trophy.id")
  List<TrophyOwnerCount> countOwnersByTrophyIn(@Param("trophies") Collection<Trophy> trophies);

  @Query(
      "select distinct ut.trophy.season from UserTrophy ut where ut.user = :user and ut.trophy.season is not null order by ut.trophy.season.startDate desc")
  List<LadderSeason> findDistinctSeasonsByUser(@Param("user") User user);

  @Query(
      """
            select ut
            from UserTrophy ut
            join fetch ut.trophy t
            left join fetch t.season s
            left join fetch s.ladderConfig lc
            where ut.user.id = :userId
            order by s.startDate desc, t.displayOrder asc, t.id asc
            """)
  List<UserTrophy> findByUserIdWithTrophyAndSeasonOrderBySeasonStartDateDesc(
      @Param("userId") Long userId);

  interface TrophyOwnerCount {
    Long getTrophyId();

    long getOwners();
  }
}
