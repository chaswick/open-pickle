package com.w3llspring.fhpb.web.service.meetups;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MeetupsEmailConfig {

  // Global cooldown; send immediately if allowed.
  private final Duration cooldown;

  // Backstop: maximum digests per user per day.
  private final int maxPerDay;

  // Subject prefix to help inbox routing and recognition.
  private final String subjectPrefix;

  public MeetupsEmailConfig(
      @Value("${fhpb.meetups.email.cooldown-hours:8}") int cooldownHours,
      @Value("${fhpb.meetups.email.max-per-day:2}") int maxPerDay,
      @Value("${fhpb.meetups.email.subject-prefix:Play Plans: }") String subjectPrefix) {
    this.cooldown = Duration.ofHours(Math.max(1, cooldownHours));
    this.maxPerDay = Math.max(1, maxPerDay);
    this.subjectPrefix = subjectPrefix == null ? "Play Plans: " : subjectPrefix;
  }

  public Duration cooldown() {
    return cooldown;
  }

  public int maxPerDay() {
    return maxPerDay;
  }

  public String subjectPrefix() {
    return subjectPrefix;
  }
}
