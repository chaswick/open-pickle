package com.w3llspring.fhpb.web.service.meetups;

import com.w3llspring.fhpb.web.model.LadderMeetupRsvp;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MeetupsEmailLinkSigner {

  private final String secret;

  public MeetupsEmailLinkSigner(@Value("${fhpb.meetups.email.link-secret:}") String secret) {
    this.secret = secret;
  }

  public String sign(
      Long userId, Long slotId, LadderMeetupRsvp.Status status, Instant expiresAt, String nonce) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("Meetups email link secret not configured");
    }
    String payload = payload(userId, slotId, status, expiresAt, nonce);
    String sig = hmacSha256Base64Url(payload);
    String token = payload + "." + sig;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(token.getBytes(StandardCharsets.UTF_8));
  }

  public ParsedToken verify(String tokenB64) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("Meetups email link secret not configured");
    }

    String token;
    try {
      token = new String(Base64.getUrlDecoder().decode(tokenB64), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid token");
    }

    int dot = token.lastIndexOf('.');
    if (dot <= 0) throw new IllegalArgumentException("Invalid token");

    String payload = token.substring(0, dot);
    String sig = token.substring(dot + 1);
    String expected = hmacSha256Base64Url(payload);
    if (!constantTimeEquals(sig, expected)) {
      throw new IllegalArgumentException("Invalid token");
    }

    String[] parts = payload.split("\\|", -1);
    if (parts.length != 5) throw new IllegalArgumentException("Invalid token");

    Long userId = Long.valueOf(parts[0]);
    Long slotId = Long.valueOf(parts[1]);
    LadderMeetupRsvp.Status status = LadderMeetupRsvp.Status.valueOf(parts[2]);
    Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[3]));
    String nonce = parts[4];

    if (Instant.now().isAfter(expiresAt)) {
      throw new IllegalArgumentException("Token expired");
    }

    return new ParsedToken(userId, slotId, status, expiresAt, nonce);
  }

  private String payload(
      Long userId, Long slotId, LadderMeetupRsvp.Status status, Instant expiresAt, String nonce) {
    if (userId == null || slotId == null || status == null || expiresAt == null || nonce == null) {
      throw new IllegalArgumentException("Missing token fields");
    }
    return userId
        + "|"
        + slotId
        + "|"
        + status.name()
        + "|"
        + expiresAt.getEpochSecond()
        + "|"
        + nonce;
  }

  private String hmacSha256Base64Url(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Unable to sign token", e);
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    if (a.length() != b.length()) return false;
    int r = 0;
    for (int i = 0; i < a.length(); i++) {
      r |= a.charAt(i) ^ b.charAt(i);
    }
    return r == 0;
  }

  public String signUnsubscribe(Long userId, Instant expiresAt, String nonce) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("Meetups email link secret not configured");
    }
    String payload = "UNSUB|" + userId + "|" + expiresAt.getEpochSecond() + "|" + nonce;
    String sig = hmacSha256Base64Url(payload);
    String token = payload + "." + sig;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(token.getBytes(StandardCharsets.UTF_8));
  }

  public UnsubscribeToken verifyUnsubscribe(String tokenB64) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("Meetups email link secret not configured");
    }

    String token;
    try {
      token = new String(Base64.getUrlDecoder().decode(tokenB64), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid token");
    }

    int dot = token.lastIndexOf('.');
    if (dot <= 0) throw new IllegalArgumentException("Invalid token");

    String payload = token.substring(0, dot);
    String sig = token.substring(dot + 1);
    String expected = hmacSha256Base64Url(payload);
    if (!constantTimeEquals(sig, expected)) {
      throw new IllegalArgumentException("Invalid token");
    }

    String[] parts = payload.split("\\|", -1);
    if (parts.length != 4 || !"UNSUB".equals(parts[0]))
      throw new IllegalArgumentException("Invalid token");

    Long userId = Long.valueOf(parts[1]);
    Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[2]));
    String nonce = parts[3];

    if (Instant.now().isAfter(expiresAt)) {
      throw new IllegalArgumentException("Token expired");
    }

    return new UnsubscribeToken(userId, expiresAt, nonce);
  }

  public record UnsubscribeToken(Long userId, Instant expiresAt, String nonce) {}

  public record ParsedToken(
      Long userId, Long slotId, LadderMeetupRsvp.Status status, Instant expiresAt, String nonce) {}
}
