package com.w3llspring.fhpb.web.service.jobs.ops;

import com.w3llspring.fhpb.web.config.OperatorProperties;
import com.w3llspring.fhpb.web.config.OpsRecentActivityProperties;
import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.service.email.EmailService;
import com.w3llspring.fhpb.web.service.ops.OpsRecentActivityReportService;
import jakarta.mail.MessagingException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OpsRecentActivityEmailJob {

  private static final Logger log = LoggerFactory.getLogger(OpsRecentActivityEmailJob.class);

  private final OpsRecentActivityProperties properties;
  private final OperatorProperties operatorProperties;
  private final OpsRecentActivityReportService reportService;
  private final EmailService emailService;

  public OpsRecentActivityEmailJob(
      OpsRecentActivityProperties properties,
      OperatorProperties operatorProperties,
      OpsRecentActivityReportService reportService,
      EmailService emailService) {
    this.properties = properties;
    this.operatorProperties = operatorProperties;
    this.reportService = reportService;
    this.emailService = emailService;
  }

  @Scheduled(
      cron = "${ops.recent-activity.cron:0 15 6 * * *}",
      zone = "${ops.recent-activity.zone:America/New_York}")
  public void sendDailyReport() {
    try (BackgroundJobLogContext ignored =
        BackgroundJobLogContext.open("ops-recent-activity-email")) {
      if (!properties.isEnabled()) {
        return;
      }
      if (!hasDeliverableSupportEmail()) {
        log.warn("Ops recent-activity email skipped because support email is not configured.");
        return;
      }

      OpsRecentActivityReportService.EmailReport report = reportService.buildEmailReport();
      send(operatorProperties.getSupportEmail(), report);
      log.info("Ops recent-activity email sent to {}", operatorProperties.getSupportEmail());
    } catch (Exception ex) {
      log.error("Ops recent-activity email failed: {}", ex.getMessage(), ex);
    }
  }

  private void send(String to, OpsRecentActivityReportService.EmailReport report)
      throws MessagingException {
    emailService.sendHtml(to, report.subject(), report.html());
  }

  private boolean hasDeliverableSupportEmail() {
    String supportEmail = operatorProperties.getSupportEmail();
    if (supportEmail == null || supportEmail.isBlank()) {
      return false;
    }
    String normalized = supportEmail.trim().toLowerCase(Locale.ROOT);
    return !normalized.endsWith(".invalid");
  }
}
