package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.GroupTrophy;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Trophy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupTrophyRepository extends JpaRepository<GroupTrophy, Long> {

  Optional<GroupTrophy> findBySeasonAndTrophy(LadderSeason season, Trophy trophy);

  List<GroupTrophy> findBySeason(LadderSeason season);

  List<GroupTrophy> findBySeasonAndTrophyIn(LadderSeason season, Collection<Trophy> trophies);

  List<GroupTrophy> findByTrophy(Trophy trophy);

  long countByTrophy(Trophy trophy);
}
