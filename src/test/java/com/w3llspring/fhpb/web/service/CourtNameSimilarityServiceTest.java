package com.w3llspring.fhpb.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserCourtNameRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserCourtName;
import com.w3llspring.fhpb.web.service.user.CourtNameSimilarityService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Tests for CourtNameSimilarityService batching and phonetic matching behavior. */
@ExtendWith(MockitoExtension.class)
class CourtNameSimilarityServiceTest {

  @Mock private UserCourtNameRepository userCourtNameRepo;

  @Mock private LadderMembershipRepository ladderMembershipRepo;

  @Mock private UserRepository userRepo;

  private CourtNameSimilarityService service;

  @BeforeEach
  void setUp() {
    service = new CourtNameSimilarityService();
    ReflectionTestUtils.setField(service, "userCourtNameRepo", userCourtNameRepo);
    ReflectionTestUtils.setField(service, "ladderMembershipRepo", ladderMembershipRepo);
    ReflectionTestUtils.setField(service, "userRepo", userRepo);
  }

  @Test
  void checkSimilaritiesReturnsEmptyMapWithoutQueryingWhenNoAliasesProvided() {
    User currentUser = user(1L, "viewer");

    Map<Long, CourtNameSimilarityService.SimilarityWarning> warnings =
        service.checkSimilarities(currentUser, List.of());

    assertTrue(warnings.isEmpty());
    verifyNoInteractions(userCourtNameRepo, ladderMembershipRepo, userRepo);
  }

  @Test
  void checkSimilaritiesLoadsComparisonDataOncePerLadder() {
    User currentUser = user(1L, "viewer");
    User otherUser = user(2L, "other");
    LadderConfig ladder = ladder(11L, "Downtown");

    when(ladderMembershipRepo.findByUserIdAndState(1L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership(ladder, 1L)));
    when(ladderMembershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            11L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(membership(ladder, 1L), membership(ladder, 2L)));
    when(userRepo.findAllById(any())).thenReturn(List.of(otherUser));
    when(userCourtNameRepo.findByUser_IdInAndLadderConfigIsNull(anyCollection()))
        .thenReturn(
            List.of(courtName(otherUser, "Kris", null), courtName(otherUser, "Cate", null)));
    when(userCourtNameRepo.findByUser_IdInAndLadderConfig_Id(anyCollection(), anyLong()))
        .thenReturn(List.of());

    Map<Long, CourtNameSimilarityService.SimilarityWarning> warnings =
        service.checkSimilarities(
            currentUser,
            List.of(
                ownCourtName(100L, currentUser, "Chris", null),
                ownCourtName(101L, currentUser, "Kate", null)));

    assertEquals(2, warnings.size());
    assertEquals("Downtown", warnings.get(100L).getLadderTitle());
    assertEquals("Downtown", warnings.get(101L).getLadderTitle());

    verify(ladderMembershipRepo, times(1)).findByUserIdAndState(1L, LadderMembership.State.ACTIVE);
    verify(ladderMembershipRepo, times(1))
        .findByLadderConfigIdAndStateOrderByJoinedAtAsc(11L, LadderMembership.State.ACTIVE);
    verify(userRepo, times(1)).findAllById(any());
    verify(userCourtNameRepo, times(1)).findByUser_IdInAndLadderConfigIsNull(anyCollection());
    verify(userCourtNameRepo, times(1))
        .findByUser_IdInAndLadderConfig_Id(anyCollection(), anyLong());
  }

  private User user(Long id, String nickName) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickName);
    user.setEmail(nickName + "@test.local");
    return user;
  }

  private LadderConfig ladder(Long id, String title) {
    LadderConfig ladder = new LadderConfig();
    ladder.setId(id);
    ladder.setTitle(title);
    return ladder;
  }

  private LadderMembership membership(LadderConfig ladder, Long userId) {
    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(ladder);
    membership.setUserId(userId);
    membership.setState(LadderMembership.State.ACTIVE);
    return membership;
  }

  private UserCourtName courtName(User user, String alias, LadderConfig ladder) {
    UserCourtName record = new UserCourtName();
    record.setUser(user);
    record.setAlias(alias);
    record.setLadderConfig(ladder);
    return record;
  }

  private UserCourtName ownCourtName(Long id, User user, String alias, LadderConfig ladder) {
    UserCourtName record = courtName(user, alias, ladder);
    ReflectionTestUtils.setField(record, "id", id);
    return record;
  }
}
