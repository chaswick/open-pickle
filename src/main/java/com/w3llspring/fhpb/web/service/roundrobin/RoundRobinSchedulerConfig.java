package com.w3llspring.fhpb.web.service.roundrobin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to select the RoundRobinScheduler implementation at runtime. Controlled by property
 * `fhpb.roundrobin.scheduler` with values: - `default`: DefaultRoundRobinScheduler (simple circle
 * rotation, backward compatible) - `fair` or `fairbye`: FairByeRoundRobinScheduler - ensures fair
 * bye distribution, balanced partnerships, and considers seasonal opponent history for optimal
 * balance - `balanced` or `composite`: BalancedCompositeRoundRobinScheduler (legacy) - `balancing`
 * or `balance`: BalancingRoundRobinScheduler (legacy) - `templates` or `template`:
 * BalancedTemplateRoundRobinScheduler (legacy) - `odd` or `heuristic`:
 * OddHeuristicRoundRobinScheduler (legacy)
 */
@Configuration
public class RoundRobinSchedulerConfig {

  private static final Logger log = LoggerFactory.getLogger(RoundRobinSchedulerConfig.class);

  @Value("${fhpb.roundrobin.scheduler:fair}")
  private String schedulerType;

  @Bean
  public RoundRobinScheduler roundRobinScheduler() {
    String s = schedulerType == null ? "default" : schedulerType.trim().toLowerCase();
    if ("templates".equals(s) || "template".equals(s)) {
      log.info(
          "RoundRobinScheduler: using BalancedTemplateRoundRobinScheduler (property: {})",
          schedulerType);
      return new BalancedTemplateRoundRobinScheduler();
    }
    if ("odd".equals(s) || "heuristic".equals(s)) {
      log.info(
          "RoundRobinScheduler: using OddHeuristicRoundRobinScheduler (property: {})",
          schedulerType);
      return new OddHeuristicRoundRobinScheduler();
    }
    if ("balanced".equals(s) || "composite".equals(s)) {
      log.info(
          "RoundRobinScheduler: using BalancedCompositeRoundRobinScheduler (property: {})",
          schedulerType);
      return new BalancedCompositeRoundRobinScheduler();
    }
    if ("balancing".equals(s) || "balance".equals(s)) {
      log.info(
          "RoundRobinScheduler: using BalancingRoundRobinScheduler (property: {})", schedulerType);
      return new BalancingRoundRobinScheduler();
    }
    if ("fair".equals(s) || "fairbye".equals(s)) {
      log.info(
          "RoundRobinScheduler: using FairByeRoundRobinScheduler (property: {})", schedulerType);
      return new FairByeRoundRobinScheduler();
    }
    // Default to DefaultRoundRobinScheduler for backward compatibility
    log.info(
        "RoundRobinScheduler: using DefaultRoundRobinScheduler (default, property: {})",
        schedulerType);
    return new DefaultRoundRobinScheduler();
  }
}
