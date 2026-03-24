package com.w3llspring.fhpb.web.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MatchLoggingQuotaServiceTest {

  @Test
  void allowsWhenUnderWeeklyLimit() {
    MatchRepository repo = mock(MatchRepository.class);
    MatchLoggingQuotaService service = new MatchLoggingQuotaService(repo, true, 100);
    User user = new User();
    ReflectionTestUtils.setField(user, "id", 42L);
    when(repo.countByLoggedBy_IdAndCreatedAtGreaterThanEqual(eq(42L), any())).thenReturn(99L);

    var status = service.evaluate(user);

    assertTrue(status.allowed());
  }

  @Test
  void blocksWhenAtOrAboveWeeklyLimit() {
    MatchRepository repo = mock(MatchRepository.class);
    MatchLoggingQuotaService service = new MatchLoggingQuotaService(repo, true, 100);
    User user = new User();
    ReflectionTestUtils.setField(user, "id", 42L);
    when(repo.countByLoggedBy_IdAndCreatedAtGreaterThanEqual(eq(42L), any())).thenReturn(100L);

    var status = service.evaluate(user);

    assertFalse(status.allowed());
  }
}
