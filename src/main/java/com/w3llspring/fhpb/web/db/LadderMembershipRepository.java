package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LadderMembershipRepository extends JpaRepository<LadderMembership, Long> {
  Optional<LadderMembership> findByLadderConfigIdAndUserId(Long configId, Long userId);

  List<LadderMembership> findByUserIdAndState(Long userId, LadderMembership.State state);

  boolean existsByUserId(Long userId);

  List<LadderMembership> findByLadderConfigIdAndStateOrderByJoinedAtAsc(
      Long ladderConfigId, LadderMembership.State state);

  long countByLadderConfigIdAndRoleAndState(
      Long configId, LadderMembership.Role role, LadderMembership.State state);

  @Query(
      "select distinct peer.userId from LadderMembership mine, LadderMembership peer "
          + "where mine.userId = :userId "
          + "and mine.state = :activeState "
          + "and peer.state = :activeState "
          + "and mine.ladderConfig = peer.ladderConfig "
          + "and peer.userId <> :userId "
          + "and mine.ladderConfig.type <> :competitionType "
          + "and mine.ladderConfig.type <> :sessionType")
  List<Long> findDistinctPeerUserIdsForPrivateGroups(
      @Param("userId") Long userId,
      @Param("activeState") LadderMembership.State activeState,
      @Param("competitionType") LadderConfig.Type competitionType,
      @Param("sessionType") LadderConfig.Type sessionType);
}
