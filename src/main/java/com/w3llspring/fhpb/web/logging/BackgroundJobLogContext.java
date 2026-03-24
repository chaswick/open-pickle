package com.w3llspring.fhpb.web.logging;

import org.slf4j.MDC;

/**
 * Marks log events produced inside scheduled/startup background work so Logback can route them to a
 * dedicated jobs log file instead of the main application log.
 */
public final class BackgroundJobLogContext implements AutoCloseable {

  public static final String LOG_CHANNEL_KEY = "fhpbLogChannel";
  public static final String JOB_NAME_KEY = "fhpbJobName";
  public static final String JOBS_CHANNEL = "jobs";

  private final String previousChannel;
  private final String previousJobName;

  private BackgroundJobLogContext(String jobName) {
    this.previousChannel = MDC.get(LOG_CHANNEL_KEY);
    this.previousJobName = MDC.get(JOB_NAME_KEY);

    MDC.put(LOG_CHANNEL_KEY, JOBS_CHANNEL);
    if (jobName == null || jobName.isBlank()) {
      MDC.remove(JOB_NAME_KEY);
    } else {
      MDC.put(JOB_NAME_KEY, jobName);
    }
  }

  public static BackgroundJobLogContext open(String jobName) {
    return new BackgroundJobLogContext(jobName);
  }

  @Override
  public void close() {
    restore(LOG_CHANNEL_KEY, previousChannel);
    restore(JOB_NAME_KEY, previousJobName);
  }

  private void restore(String key, String value) {
    if (value == null || value.isBlank()) {
      MDC.remove(key);
    } else {
      MDC.put(key, value);
    }
  }
}
