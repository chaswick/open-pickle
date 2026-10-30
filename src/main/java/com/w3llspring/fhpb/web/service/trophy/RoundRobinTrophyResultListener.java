package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.model.*;
import jakarta.persistence.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Covers result mutations from every scoring, confirmation, correction and maintenance path. */
@Component
public class RoundRobinTrophyResultListener {
  private final ApplicationEventPublisher events;

  public RoundRobinTrophyResultListener(ApplicationEventPublisher events) {
    this.events = events;
  }

  @PostPersist
  public void inserted(Object entity) {
    if (entity instanceof RoundRobinEntry entry) entryChanged(entry);
    else if (entity instanceof Match match) match.rememberTrophyResults();
  }

  @PostLoad
  public void loaded(Object entity) {
    if (entity instanceof Match match) match.rememberTrophyResults();
  }

  @PostUpdate
  public void updated(Object entity) {
    if (entity instanceof Match match && match.trophyResultsChanged())
      events.publishEvent(new RoundRobinTrophyResultsChanged(match.getId(), null, null));
    else if (entity instanceof RoundRobinEntry entry) entryChanged(entry);
  }

  @PostRemove
  public void removed(Object entity) {
    if (entity instanceof RoundRobin run) {
      events.publishEvent(new RoundRobinTrophyResultsChanged(null, null, run.getSeason().getId()));
    } else if (entity instanceof Match match) {
      events.publishEvent(new RoundRobinTrophyResultsChanged(match.getId(), null, null));
    } else updated(entity);
  }

  private void entryChanged(RoundRobinEntry entry) {
    if (entry.getRoundRobin() != null)
      events.publishEvent(
          new RoundRobinTrophyResultsChanged(null, entry.getRoundRobin().getId(), null));
  }
}
