package com.w3llspring.fhpb.web.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.jobs.maintenance.StaleUserPurgeJob;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class StaleUserPurgeJobTest {

  @Test
  void dryRunDoesNotDeleteUsers() {
    UserRepository userRepository = mock(UserRepository.class);
    User u1 = user(10L);

    when(userRepository.findStaleZeroFootprintUsersAfterId(
            ArgumentMatchers.anyLong(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(List.of(u1), List.of());

    StaleUserPurgeJob job = new StaleUserPurgeJob(userRepository, true, true, 45, 50, 500, "1,2");

    job.purgeStaleUsers();

    verify(userRepository, never()).delete(ArgumentMatchers.any(User.class));
    verify(userRepository, never()).flush();
  }

  @Test
  void skipsProtectedIdsAndDeletesOthers() {
    UserRepository userRepository = mock(UserRepository.class);
    User protectedUser = user(1L);
    User removableUser = user(42L);

    when(userRepository.findStaleZeroFootprintUsersAfterId(
            ArgumentMatchers.anyLong(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(List.of(protectedUser, removableUser), List.of());

    StaleUserPurgeJob job = new StaleUserPurgeJob(userRepository, true, false, 45, 50, 500, "1,2");

    job.purgeStaleUsers();

    verify(userRepository, times(1)).delete(removableUser);
    verify(userRepository, times(1)).flush();
    verify(userRepository, never()).delete(protectedUser);
  }

  private User user(Long id) {
    User user = new User();
    user.setId(id);
    return user;
  }
}
