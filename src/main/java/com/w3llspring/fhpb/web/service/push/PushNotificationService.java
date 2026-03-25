package com.w3llspring.fhpb.web.service.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.w3llspring.fhpb.web.config.BrandingProperties;
import com.w3llspring.fhpb.web.db.UserPushSubscriptionRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserPushSubscription;
import com.w3llspring.fhpb.web.service.meetups.MeetupsEmailDigestRow;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

  private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

  private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/New_York");
  private static final DateTimeFormatter DATE_TIME_FORMAT_PATTERN =
      DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a z", Locale.US);

  private final UserPushSubscriptionRepository subscriptions;
  private final UserRepository userRepo;
  private final ObjectMapper objectMapper;
  private final PushEndpointValidator pushEndpointValidator;

  private final boolean enabled;
  private final String vapidPublicKey;
  private final String vapidPrivateKey;
  private final String vapidSubject;
  private final int ttlSeconds;
  private final String appName;

  private volatile PushService pushService;

  private static void ensureBouncyCastleProvider() {
    if (Security.getProvider("BC") == null) {
      try {
        Security.addProvider(new BouncyCastleProvider());
      } catch (Throwable ignored) {
        // If BC isn't on the classpath (or security manager restrictions), web-push init will fail
        // later.
      }
    }
  }

  public PushNotificationService(
      UserPushSubscriptionRepository subscriptions,
      UserRepository userRepo,
      ObjectMapper objectMapper,
      PushEndpointValidator pushEndpointValidator,
      BrandingProperties brandingProperties,
      @Value("${fhpb.push.enabled:false}") boolean enabled,
      @Value("${fhpb.push.vapid.public-key:}") String vapidPublicKey,
      @Value("${fhpb.push.vapid.private-key:}") String vapidPrivateKey,
      @Value("${fhpb.push.vapid.subject:mailto:support@example.invalid}") String vapidSubject,
      @Value("${fhpb.push.ttl-seconds:3600}") int ttlSeconds) {
    this.subscriptions = subscriptions;
    this.userRepo = userRepo;
    this.objectMapper = objectMapper;
    this.pushEndpointValidator = pushEndpointValidator;
    this.enabled = enabled;
    this.vapidPublicKey = vapidPublicKey == null ? "" : vapidPublicKey.trim();
    this.vapidPrivateKey = vapidPrivateKey == null ? "" : vapidPrivateKey.trim();
    this.vapidSubject =
        vapidSubject == null ? "mailto:support@example.invalid" : vapidSubject.trim();
    this.ttlSeconds = Math.max(60, ttlSeconds);
    this.appName = brandingProperties.getAppName();
  }

  private ZoneId resolveUserZone(Long userId) {
    if (userId == null) return DEFAULT_ZONE;
    try {
      User u = userRepo.findById(userId).orElse(null);
      if (u == null || u.getTimeZone() == null || u.getTimeZone().isBlank()) return DEFAULT_ZONE;
      return ZoneId.of(u.getTimeZone().trim());
    } catch (Exception ignored) {
      return DEFAULT_ZONE;
    }
  }

  private String formatStartsAt(Long userId, Instant startsAt) {
    if (startsAt == null) return null;
    ZoneId zone = resolveUserZone(userId);
    return DATE_TIME_FORMAT_PATTERN.withZone(zone).format(startsAt);
  }

  public boolean isEnabledAndConfigured() {
    return enabled && !vapidPublicKey.isBlank() && !vapidPrivateKey.isBlank();
  }

  public String getVapidPublicKey() {
    return isEnabledAndConfigured() ? vapidPublicKey : "";
  }

  public String requireSubscriptionEndpoint(String endpoint) {
    return pushEndpointValidator.requireAllowedEndpoint(endpoint);
  }

  private PushService getOrInitPushService() {
    if (!isEnabledAndConfigured()) {
      return null;
    }

    // web-push Utils.* asks for KeyFactory/KeyAgreement with provider "BC".
    ensureBouncyCastleProvider();

    PushService existing = pushService;
    if (existing != null) {
      return existing;
    }
    synchronized (this) {
      if (pushService != null) {
        return pushService;
      }
      try {
        PushService ps = new PushService();
        ps.setSubject(vapidSubject);
        ps.setPublicKey(Utils.loadPublicKey(vapidPublicKey));
        ps.setPrivateKey(Utils.loadPrivateKey(vapidPrivateKey));
        pushService = ps;
        return ps;
      } catch (Exception ex) {
        log.warn("[push] VAPID init failed; push disabled", ex);
        return null;
      }
    }
  }

  public void sendNewPlayPlan(Long userId, String ladderTitle, Instant startsAt) {
    if (userId == null) return;

    PushService ps = getOrInitPushService();
    if (ps == null) return;

    List<UserPushSubscription> subs = subscriptions.findByUserId(userId);
    if (subs == null || subs.isEmpty()) return;

    String title = appName;

    String cleanedLadder = ladderTitle == null ? "" : ladderTitle.trim();
    String body;
    if (cleanedLadder.isBlank()) {
      body = "New Play Plan available.";
    } else {
      body = "New Play Plan in " + cleanedLadder + ".";
    }
    if (startsAt != null) {
      String when = formatStartsAt(userId, startsAt);
      body = body + " (" + when + ")";
    }

    PushPayload payload = new PushPayload(title, body, "/play-plans", Instant.now());

    byte[] payloadBytes;
    try {
      payloadBytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
    } catch (Exception ex) {
      log.warn("[push] payload serialize failed userId={}", userId, ex);
      return;
    }

    for (UserPushSubscription sub : subs) {
      if (sub == null) continue;
      if (!isAllowedSubscription(sub)) continue;
      try {
        Notification notification =
            new Notification(
                sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payloadBytes, ttlSeconds);
        var response = ps.send(notification);
        int status =
            response.getStatusLine() != null ? response.getStatusLine().getStatusCode() : 0;
        if (status == 404 || status == 410) {
          subscriptions.deleteById(sub.getId());
        }
      } catch (Exception ex) {
        log.debug("[push] send failed userId={} subId={}", userId, sub.getId(), ex);
      }
    }
  }

  public void sendPlayPlanCanceled(Long userId, String ladderTitle, Instant startsAt) {
    if (userId == null) return;

    PushService ps = getOrInitPushService();
    if (ps == null) return;

    List<UserPushSubscription> subs = subscriptions.findByUserId(userId);
    if (subs == null || subs.isEmpty()) return;

    String title = appName;

    String cleanedLadder = ladderTitle == null ? "" : ladderTitle.trim();
    String body;
    if (cleanedLadder.isBlank()) {
      body = "Play Plan canceled.";
    } else {
      body = "Play Plan canceled in " + cleanedLadder + ".";
    }
    if (startsAt != null) {
      String when = formatStartsAt(userId, startsAt);
      body = body + " (" + when + ")";
    }

    PushPayload payload = new PushPayload(title, body, "/play-plans", Instant.now());

    byte[] payloadBytes;
    try {
      payloadBytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
    } catch (Exception ex) {
      log.warn("[push] payload serialize failed userId={}", userId, ex);
      return;
    }

    for (UserPushSubscription sub : subs) {
      if (sub == null) continue;
      if (!isAllowedSubscription(sub)) continue;
      try {
        Notification notification =
            new Notification(
                sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payloadBytes, ttlSeconds);
        var response = ps.send(notification);
        int status =
            response.getStatusLine() != null ? response.getStatusLine().getStatusCode() : 0;
        if (status == 404 || status == 410) {
          subscriptions.deleteById(sub.getId());
        }
      } catch (Exception ex) {
        log.debug("[push] send failed userId={} subId={}", userId, sub.getId(), ex);
      }
    }
  }

  public void sendMeetupsDigest(Long userId, List<MeetupsEmailDigestRow> rows) {
    if (userId == null) return;

    PushService ps = getOrInitPushService();
    if (ps == null) return;

    List<UserPushSubscription> subs = subscriptions.findByUserId(userId);
    if (subs == null || subs.isEmpty()) return;

    int count = rows == null ? 0 : rows.size();
    String title = appName;
    String body = count <= 1 ? "New Play Plan available." : (count + " new Play Plans available.");

    PushPayload payload = new PushPayload(title, body, "/play-plans", Instant.now());

    byte[] payloadBytes;
    try {
      payloadBytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
    } catch (Exception ex) {
      log.warn("[push] payload serialize failed userId={}", userId, ex);
      return;
    }

    for (UserPushSubscription sub : subs) {
      if (sub == null) continue;
      if (!isAllowedSubscription(sub)) continue;
      try {
        Notification notification =
            new Notification(
                sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payloadBytes, ttlSeconds);
        var response = ps.send(notification);
        int status =
            response.getStatusLine() != null ? response.getStatusLine().getStatusCode() : 0;
        if (status == 404 || status == 410) {
          subscriptions.deleteById(sub.getId());
        }
      } catch (Exception ex) {
        log.debug("[push] send failed userId={} subId={}", userId, sub.getId(), ex);
      }
    }
  }

  public record PushPayload(String title, String body, String url, Instant sentAt) {}

  private boolean isAllowedSubscription(UserPushSubscription sub) {
    if (sub == null || sub.getEndpoint() == null) {
      return false;
    }
    if (pushEndpointValidator.isAllowed(sub.getEndpoint())) {
      return true;
    }
    if (sub.getId() != null) {
      subscriptions.deleteById(sub.getId());
    }
    log.warn(
        "[push] dropped disallowed subscription subId={} userId={}", sub.getId(), sub.getUserId());
    return false;
  }
}
