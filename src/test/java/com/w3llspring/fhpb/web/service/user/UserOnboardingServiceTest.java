package com.w3llspring.fhpb.web.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.UserOnboardingMarkerRepository;
import com.w3llspring.fhpb.web.model.UserOnboardingMarker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserOnboardingServiceTest {

  @Mock private UserOnboardingMarkerRepository userOnboardingMarkerRepository;

  private UserOnboardingService userOnboardingService;

  @BeforeEach
  void setUp() {
    userOnboardingService = new UserOnboardingService(userOnboardingMarkerRepository);
  }

  @Test
  void shouldShowWhenMarkerHasNotBeenCompleted() {
    when(userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
            123L, UserOnboardingService.HOME_TOUR_V1))
        .thenReturn(false);

    boolean show = userOnboardingService.shouldShow(123L, UserOnboardingService.HOME_TOUR_V1);

    assertThat(show).isTrue();
  }

  @Test
  void shouldNotShowWhenMarkerAlreadyCompleted() {
    when(userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
            123L, UserOnboardingService.HOME_TOUR_V1))
        .thenReturn(true);

    boolean show = userOnboardingService.shouldShow(123L, UserOnboardingService.HOME_TOUR_V1);

    assertThat(show).isFalse();
  }

  @Test
  void markCompletedCreatesMarkerWhenMissing() {
    when(userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
            123L, UserOnboardingService.HOME_TOUR_V1))
        .thenReturn(false);
    when(userOnboardingMarkerRepository.saveAndFlush(any(UserOnboardingMarker.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userOnboardingService.markCompleted(123L, UserOnboardingService.HOME_TOUR_V1);

    verify(userOnboardingMarkerRepository)
        .saveAndFlush(
            argThat(
                marker ->
                    marker != null
                        && marker.getUserId().equals(123L)
                        && UserOnboardingService.HOME_TOUR_V1.equals(marker.getMarkerKey())
                        && marker.getCompletedAt() != null));
  }

  @Test
  void markCompletedIsIdempotentWhenMarkerAlreadyExists() {
    when(userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
            123L, UserOnboardingService.HOME_TOUR_V1))
        .thenReturn(true);

    userOnboardingService.markCompleted(123L, UserOnboardingService.HOME_TOUR_V1);

    verify(userOnboardingMarkerRepository, never()).saveAndFlush(any(UserOnboardingMarker.class));
  }

  @Test
  void hasCompletedTrimsMarkerKey() {
    when(userOnboardingMarkerRepository.existsByUserIdAndMarkerKey(
            123L, UserOnboardingService.HOME_TOUR_V1))
        .thenReturn(true);

    boolean completed = userOnboardingService.hasCompleted(123L, "  home_tour_v1  ");

    assertThat(completed).isTrue();
    verify(userOnboardingMarkerRepository)
        .existsByUserIdAndMarkerKey(eq(123L), eq(UserOnboardingService.HOME_TOUR_V1));
  }
}
