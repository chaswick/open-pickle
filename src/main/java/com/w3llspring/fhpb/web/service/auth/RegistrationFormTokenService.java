package com.w3llspring.fhpb.web.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RegistrationFormTokenService {

  private static final Logger log = LoggerFactory.getLogger(RegistrationFormTokenService.class);
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final long MAX_FUTURE_DRIFT_MS = 60_000L;

  private final byte[] secretBytes;
  private final long maxAgeMs;

  public RegistrationFormTokenService(
      @Value("${fhpb.security.registration.form-token-secret:}") String configuredSecret,
      @Value("${fhpb.security.remember-me.key:}") String rememberMeKey,
      @Value("${fhpb.security.registration.form-token-max-age-minutes:360}") long maxAgeMinutes) {
    this.secretBytes = resolveSecret(configuredSecret, rememberMeKey);
    this.maxAgeMs = Duration.ofMinutes(Math.max(1L, maxAgeMinutes)).toMillis();
  }

  public String issueToken() {
    return issueToken(System.currentTimeMillis());
  }

  public Long resolveIssuedAt(String token) {
    if (!StringUtils.hasText(token)) {
      return null;
    }

    int separator = token.indexOf('.');
    if (separator <= 0 || separator >= token.length() - 1) {
      return null;
    }

    String issuedAtPart = token.substring(0, separator);
    String signaturePart = token.substring(separator + 1);
    if (!constantTimeEquals(signaturePart, sign(issuedAtPart))) {
      return null;
    }

    long issuedAt;
    try {
      issuedAt = Long.parseLong(issuedAtPart);
    } catch (NumberFormatException ex) {
      return null;
    }

    long now = System.currentTimeMillis();
    if (issuedAt <= 0L || issuedAt > now + MAX_FUTURE_DRIFT_MS) {
      return null;
    }
    if (now - issuedAt > maxAgeMs) {
      return null;
    }

    return issuedAt;
  }

  String issueToken(long issuedAtMs) {
    String issuedAt = Long.toString(issuedAtMs);
    return issuedAt + "." + sign(issuedAt);
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
      byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to sign registration form token.", ex);
    }
  }

  private byte[] resolveSecret(String configuredSecret, String rememberMeKey) {
    if (StringUtils.hasText(configuredSecret)) {
      return configuredSecret.trim().getBytes(StandardCharsets.UTF_8);
    }
    if (StringUtils.hasText(rememberMeKey)) {
      return rememberMeKey.trim().getBytes(StandardCharsets.UTF_8);
    }

    byte[] generated = new byte[32];
    new SecureRandom().nextBytes(generated);
    log.warn(
        "Using an ephemeral registration form token secret because no configured secret was provided.");
    return generated;
  }

  private boolean constantTimeEquals(String left, String right) {
    byte[] leftBytes = left.getBytes(StandardCharsets.US_ASCII);
    byte[] rightBytes = right.getBytes(StandardCharsets.US_ASCII);
    if (leftBytes.length != rightBytes.length) {
      return false;
    }

    int diff = 0;
    for (int i = 0; i < leftBytes.length; i++) {
      diff |= leftBytes[i] ^ rightBytes[i];
    }
    return diff == 0;
  }
}
