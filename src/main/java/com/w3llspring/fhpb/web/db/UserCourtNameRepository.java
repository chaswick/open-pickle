package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.UserCourtName;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCourtNameRepository extends JpaRepository<UserCourtName, Long> {

  List<UserCourtName> findByUser_Id(Long userId);

  List<UserCourtName> findByUser_IdInAndLadderConfigIsNull(Collection<Long> userIds);

  List<UserCourtName> findByUser_IdInAndLadderConfig_Id(
      Collection<Long> userIds, Long ladderConfigId);

  List<UserCourtName> findByLadderConfig_Id(Long ladderConfigId);

  Optional<UserCourtName> findByIdAndUser_Id(Long id, Long userId);

  Optional<UserCourtName> findByUser_IdAndAliasIgnoreCaseAndLadderConfigIsNull(
      Long userId, String alias);

  Optional<UserCourtName> findByUser_IdAndAliasIgnoreCaseAndLadderConfig_Id(
      Long userId, String alias, Long ladderConfigId);

  long countByUser_IdAndLadderConfigIsNull(Long userId);

  long countByUser_IdAndLadderConfig_Id(Long userId, Long ladderConfigId);
}
