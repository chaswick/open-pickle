package com.w3llspring.fhpb.web.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops.recent-activity")
public class OpsRecentActivityProperties {

  private boolean enabled = false;
  private String cron = "0 15 6 * * *";
  private String zone = "America/New_York";
  private int lookbackDays = 1;
  private int activeMinutes = 60;
  private int maxRows = 10;
  private int maxErrors = 15;
  private String logsDir = "logs";
  private String subjectPrefix = "Ops: ";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getCron() {
    return cron == null || cron.isBlank() ? "0 15 6 * * *" : cron.trim();
  }

  public void setCron(String cron) {
    this.cron = cron;
  }

  public String getZone() {
    return zone == null || zone.isBlank() ? "America/New_York" : zone.trim();
  }

  public void setZone(String zone) {
    this.zone = zone;
  }

  public ZoneId zoneId() {
    try {
      return ZoneId.of(getZone());
    } catch (Exception ignored) {
      return ZoneId.of("America/New_York");
    }
  }

  public int getLookbackDays() {
    return Math.max(1, lookbackDays);
  }

  public void setLookbackDays(int lookbackDays) {
    this.lookbackDays = lookbackDays;
  }

  public int getActiveMinutes() {
    return Math.max(1, activeMinutes);
  }

  public void setActiveMinutes(int activeMinutes) {
    this.activeMinutes = activeMinutes;
  }

  public int getMaxRows() {
    return Math.max(1, maxRows);
  }

  public void setMaxRows(int maxRows) {
    this.maxRows = maxRows;
  }

  public int getMaxErrors() {
    return Math.max(1, maxErrors);
  }

  public void setMaxErrors(int maxErrors) {
    this.maxErrors = maxErrors;
  }

  public String getLogsDir() {
    return logsDir == null || logsDir.isBlank() ? "logs" : logsDir.trim();
  }

  public void setLogsDir(String logsDir) {
    this.logsDir = logsDir;
  }

  public String getSubjectPrefix() {
    return subjectPrefix == null ? "" : subjectPrefix;
  }

  public void setSubjectPrefix(String subjectPrefix) {
    this.subjectPrefix = subjectPrefix;
  }
}
