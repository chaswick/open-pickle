package com.w3llspring.fhpb.web.service.user;

import com.w3llspring.fhpb.web.db.UserCourtNameRepository;
import com.w3llspring.fhpb.web.model.UserCourtName;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultCourtNameService implements CourtNameService {

  private final UserCourtNameRepository userCourtNameRepository;

  public DefaultCourtNameService(UserCourtNameRepository userCourtNameRepository) {
    this.userCourtNameRepository = userCourtNameRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, Set<String>> gatherCourtNamesForUsers(
      Collection<Long> userIds, Long ladderConfigId) {
    LinkedHashMap<Long, Set<String>> namesByUser = new LinkedHashMap<>();
    if (userIds == null || userIds.isEmpty()) {
      return namesByUser;
    }

    for (Long id : userIds) {
      namesByUser.putIfAbsent(id, new LinkedHashSet<>());
    }

    if (ladderConfigId != null) {
      List<UserCourtName> ladderAliases =
          userCourtNameRepository.findByUser_IdInAndLadderConfig_Id(userIds, ladderConfigId);
      for (UserCourtName alias : ladderAliases) {
        namesByUser
            .computeIfAbsent(alias.getUser().getId(), ignored -> new LinkedHashSet<>())
            .add(alias.getAlias());
      }
    }

    List<UserCourtName> globalAliases =
        userCourtNameRepository.findByUser_IdInAndLadderConfigIsNull(userIds);
    for (UserCourtName alias : globalAliases) {
      namesByUser
          .computeIfAbsent(alias.getUser().getId(), ignored -> new LinkedHashSet<>())
          .add(alias.getAlias());
    }

    return namesByUser;
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> gatherCourtNamesForUser(long userId, Long ladderConfigId) {
    return gatherCourtNamesForUsers(List.of(userId), ladderConfigId).getOrDefault(userId, Set.of());
  }
}
