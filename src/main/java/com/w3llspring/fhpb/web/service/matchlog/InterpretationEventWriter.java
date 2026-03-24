package com.w3llspring.fhpb.web.service.matchlog;

import com.w3llspring.fhpb.web.db.InterpretationEventRepository;
import com.w3llspring.fhpb.web.model.InterpretationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes InterpretationEvent in a new transaction so failures here don't mark the caller
 * transaction rollback-only and don't flush the caller's Hibernate Session after an exception.
 */
@Service
public class InterpretationEventWriter {

  private static final Logger log = LoggerFactory.getLogger(InterpretationEventWriter.class);
  private static final int PRUNE_BATCH_SIZE = 250;

  private final InterpretationEventRepository interpretationEventRepository;
  private final boolean retentionEnabled;
  private final int maxEventsPerUser;

  public InterpretationEventWriter(InterpretationEventRepository interpretationEventRepository) {
    this(interpretationEventRepository, true, 500);
  }

  @Autowired
  public InterpretationEventWriter(
      InterpretationEventRepository interpretationEventRepository,
      @Value("${fhpb.voice.interpretation-events.retention.enabled:true}") boolean retentionEnabled,
      @Value("${fhpb.voice.interpretation-events.retention.max-per-user:500}")
          int maxEventsPerUser) {
    this.interpretationEventRepository = interpretationEventRepository;
    this.retentionEnabled = retentionEnabled;
    this.maxEventsPerUser = Math.max(1, maxEventsPerUser);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void write(InterpretationEvent ev) {
    interpretationEventRepository.save(ev);
    trimUserHistoryIfNeeded(ev != null ? ev.getCurrentUserId() : null);
    if (log.isDebugEnabled()) {
      log.debug("Persisted InterpretationEvent id={} uuid={}", ev.getId(), ev.getEventUuid());
    }
  }

  private void trimUserHistoryIfNeeded(Long currentUserId) {
    if (!retentionEnabled || currentUserId == null) {
      return;
    }
    long total = interpretationEventRepository.countByCurrentUserId(currentUserId);
    long excess = total - maxEventsPerUser;
    if (excess <= 0L) {
      return;
    }

    long remaining = excess;
    while (remaining > 0L) {
      int batchSize = (int) Math.min(remaining, PRUNE_BATCH_SIZE);
      var oldest =
          interpretationEventRepository.findByCurrentUserIdOrderByCreatedAtAsc(
              currentUserId, PageRequest.of(0, batchSize));
      if (oldest.isEmpty()) {
        break;
      }
      interpretationEventRepository.deleteAllInBatch(oldest);
      remaining -= oldest.size();
    }
    if (log.isInfoEnabled()) {
      log.info(
          "Trimmed {} interpretation event(s) for user {} to enforce retention max={}",
          excess - Math.max(0L, remaining),
          currentUserId,
          maxEventsPerUser);
    }
  }
}
