package com.w3llspring.fhpb.web.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("fhpb-async-");
    // Background work is best-effort; shutdown should not hang the app or test contexts
    // waiting for the executor to drain indefinitely.
    executor.setWaitForTasksToCompleteOnShutdown(false);
    executor.setAwaitTerminationSeconds(5);
    executor.setAcceptTasksAfterContextClose(false);
    executor.initialize();
    return executor;
  }
}
