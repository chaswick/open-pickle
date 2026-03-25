package com.w3llspring.fhpb.web.service.email;

import com.w3llspring.fhpb.web.config.BrandingProperties;
import com.w3llspring.fhpb.web.config.OperatorProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  private final JavaMailSender mailSender;
  private final String supportEmail;
  private final String appName;

  public EmailService(
      JavaMailSender mailSender, OperatorProperties operatorProperties, BrandingProperties brandingProperties) {
    this.mailSender = mailSender;
    this.supportEmail = operatorProperties.getSupportEmail();
    this.appName = brandingProperties.getAppName();
  }

  public void sendHtml(String to, String subject, String html) throws MessagingException {
    sendHtml(to, subject, html, null);
  }

  public void sendHtml(
      String to, String subject, String html, java.util.Map<String, String> headers)
      throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message);

    // Keep sender consistent and recognizable.
    try {
      helper.setFrom(supportEmail, appName + " <no-reply>");
    } catch (java.io.UnsupportedEncodingException e) {
      helper.setFrom(supportEmail);
    }
    helper.setTo(to);
    helper.setSubject(subject);
    helper.setText(html, true);

    if (headers != null) {
      for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
        message.addHeader(entry.getKey(), entry.getValue());
      }
    }

    mailSender.send(message);
  }
}
