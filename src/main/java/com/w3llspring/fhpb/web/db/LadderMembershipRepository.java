package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LadderMembershipRepository extends JpaRepository<LadderMembership, Long> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select m from LadderMembership m where m.ladderConfig.id = :configId and m.userId = :userId")
  Optional<LadderMembership> lockMember(Long configId, Long userId);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select m from LadderMembership m where m.ladderConfig.id = :configId and m.state = 'ACTIVE' order by m.joinedAt, m.id")
  List<LadderMembership> lockActiveMembers(Long configId);

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
