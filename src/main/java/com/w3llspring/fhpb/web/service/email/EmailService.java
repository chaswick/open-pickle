package com.w3llspring.fhpb.web.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  private final JavaMailSender mailSender;

  @Value("${support.email}")
  private String supportEmail;

  public EmailService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
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
      helper.setFrom(supportEmail, "Open-Pickle <no-reply>");
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
