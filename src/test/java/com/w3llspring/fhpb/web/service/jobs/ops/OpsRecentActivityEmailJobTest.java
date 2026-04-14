package com.w3llspring.fhpb.web.service.jobs.ops;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.config.OperatorProperties;
import com.w3llspring.fhpb.web.config.OpsRecentActivityProperties;
import com.w3llspring.fhpb.web.service.email.EmailService;
import com.w3llspring.fhpb.web.service.ops.OpsRecentActivityReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpsRecentActivityEmailJobTest {

  @Mock private OpsRecentActivityReportService reportService;
  @Mock private EmailService emailService;

  @Test
  void sendDailyReportSkipsWhenDisabled() throws Exception {
    OpsRecentActivityProperties properties = new OpsRecentActivityProperties();
    properties.setEnabled(false);

    OperatorProperties operatorProperties = new OperatorProperties();
    operatorProperties.setSupportEmail("ops@example.com");

    OpsRecentActivityEmailJob job =
        new OpsRecentActivityEmailJob(properties, operatorProperties, reportService, emailService);

    job.sendDailyReport();

    verify(reportService, never()).buildEmailReport();
    verify(emailService, never())
        .sendHtml(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void sendDailyReportSkipsWhenSupportEmailIsPlaceholder() throws Exception {
    OpsRecentActivityProperties properties = new OpsRecentActivityProperties();
    properties.setEnabled(true);

    OperatorProperties operatorProperties = new OperatorProperties();
    operatorProperties.setSupportEmail("no-reply@example.invalid");

    OpsRecentActivityEmailJob job =
        new OpsRecentActivityEmailJob(properties, operatorProperties, reportService, emailService);

    job.sendDailyReport();

    verify(reportService, never()).buildEmailReport();
    verify(emailService, never())
        .sendHtml(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void sendDailyReportSendsToConfiguredSupportEmail() throws Exception {
    OpsRecentActivityProperties properties = new OpsRecentActivityProperties();
    properties.setEnabled(true);

    OperatorProperties operatorProperties = new OperatorProperties();
    operatorProperties.setSupportEmail("ops@example.com");

    OpsRecentActivityReportService.EmailReport report =
        new OpsRecentActivityReportService.EmailReport(
            "Ops: Recent activity 2026-04-14", "<html>report</html>", "report");
    when(reportService.buildEmailReport()).thenReturn(report);

    OpsRecentActivityEmailJob job =
        new OpsRecentActivityEmailJob(properties, operatorProperties, reportService, emailService);

    job.sendDailyReport();

    verify(reportService).buildEmailReport();
    verify(emailService)
        .sendHtml("ops@example.com", "Ops: Recent activity 2026-04-14", "<html>report</html>");
  }
}
