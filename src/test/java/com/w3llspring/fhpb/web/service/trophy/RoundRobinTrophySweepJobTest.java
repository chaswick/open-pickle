package com.w3llspring.fhpb.web.service.trophy;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.w3llspring.fhpb.web.db.RoundRobinRepository;
import com.w3llspring.fhpb.web.service.jobs.trophy.RoundRobinTrophySweepJob;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundRobinTrophySweepJobTest {
  @Test
  void aFailedRunDoesNotPreventOtherQueuedRunsFromBeingProcessed() {
    var runs = mock(RoundRobinRepository.class);
    var awards = mock(RoundRobinTrophyAwardService.class);
    when(runs.findUnprocessedFinishedRuns(any(), any())).thenReturn(List.of(1L, 2L, 3L));
    when(awards.process(2L)).thenThrow(new IllegalStateException("Temporary failure"));
    new RoundRobinTrophySweepJob(runs, awards, mock(RoundRobinTrophyInvalidation.class)).sweep();
    verify(awards).process(1L);
    verify(awards).process(2L);
    verify(awards).process(3L);
  }

  @Test
  void anOverlappingSweepDoesNotProcessTheSameBatchAgain() {
    var runs = mock(RoundRobinRepository.class);
    var awards = mock(RoundRobinTrophyAwardService.class);
    var job = new RoundRobinTrophySweepJob(runs, awards, mock(RoundRobinTrophyInvalidation.class));
    when(runs.findUnprocessedFinishedRuns(any(), any())).thenReturn(List.of(1L));
    when(awards.process(1L))
        .thenAnswer(
            invocation -> {
              job.sweep();
              return true;
            });
    job.sweep();
    verify(awards, times(1)).process(1L);
  }
}
