package com.w3llspring.fhpb.web.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Simple appender filter that accepts or rejects log events based on a single MDC key/value pair.
 */
public class MdcValueFilter extends Filter<ILoggingEvent> {

  private String key;
  private String value;
  private FilterReply onMatch = FilterReply.NEUTRAL;
  private FilterReply onMismatch = FilterReply.DENY;

  public void setKey(String key) {
    this.key = key;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public void setOnMatch(FilterReply onMatch) {
    this.onMatch = onMatch;
  }

  public void setOnMismatch(FilterReply onMismatch) {
    this.onMismatch = onMismatch;
  }

  @Override
  public void start() {
    if (key != null && !key.isBlank() && value != null) {
      super.start();
    }
  }

  @Override
  public FilterReply decide(ILoggingEvent event) {
    if (!isStarted() || event == null) {
      return FilterReply.NEUTRAL;
    }
    String actualValue = event.getMDCPropertyMap().get(key);
    return value.equals(actualValue) ? onMatch : onMismatch;
  }
}
