package com.w3llspring.fhpb.web.controller.account;

import com.w3llspring.fhpb.web.db.UserPushSubscriptionRepository;
import com.w3llspring.fhpb.web.model.UserPushSubscription;
import com.w3llspring.fhpb.web.service.push.PushEndpointValidator;
import com.w3llspring.fhpb.web.service.push.PushNotificationService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import com.w3llspring.fhpb.web.util.InputValidation;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/push")
@Secured("ROLE_USER")
public class PushController {

  private static final Logger log = LoggerFactory.getLogger(PushController.class);

  private final UserPushSubscriptionRepository subscriptions;
  private final PushNotificationService push;
  private final PushEndpointValidator pushEndpointValidator;

  public PushController(
      UserPushSubscriptionRepository subscriptions,
      PushNotificationService push,
      PushEndpointValidator pushEndpointValidator) {
    this.subscriptions = subscriptions;
    this.push = push;
    this.pushEndpointValidator = pushEndpointValidator;
  }

  public record VapidPublicKeyResponse(String publicKey) {}

  public record SubscribeRequest(String endpoint, String p256dh, String auth, String userAgent) {}

  public record UnsubscribeRequest(String endpoint) {}

  @GetMapping("/vapid-public-key")
  public VapidPublicKeyResponse vapidPublicKey() {
    if (!push.isEnabledAndConfigured()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return new VapidPublicKeyResponse(push.getVapidPublicKey());
  }

  @PostMapping("/subscribe")
  public void subscribe(@RequestBody SubscribeRequest req) {
    if (req == null
        || req.endpoint() == null
        || req.endpoint().isBlank()
        || req.p256dh() == null
        || req.p256dh().isBlank()
        || req.auth() == null
        || req.auth().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    Long userId = AuthenticatedUserSupport.requireCurrentUserId();

    final String endpoint;
    final String p256dh;
    final String authKey;
    final String userAgent;
    try {
      endpoint = pushEndpointValidator.requireAllowedEndpoint(req.endpoint());
      p256dh = InputValidation.requirePushKey(req.p256dh(), "p256dh");
      authKey = InputValidation.requirePushKey(req.auth(), "auth");
      userAgent = InputValidation.normalizeOptionalUserAgent(req.userAgent());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    UserPushSubscription sub =
        subscriptions.findByEndpoint(endpoint).orElseGet(UserPushSubscription::new);
    if (sub.getId() != null && !Objects.equals(sub.getUserId(), userId)) {
      log.info(
          "Reassigning an existing push subscription record from userId={} to userId={}",
          sub.getUserId(),
          userId);
    }
    Instant now = Instant.now();
    if (sub.getCreatedAt() == null) {
      sub.setCreatedAt(now);
    }
    sub.setUpdatedAt(now);
    sub.setUserId(userId);
    sub.setEndpoint(endpoint);
    sub.setP256dh(p256dh);
    sub.setAuth(authKey);
    sub.setUserAgent(userAgent);
    subscriptions.save(sub);
  }

  @PostMapping("/unsubscribe")
  public void unsubscribe(@RequestBody UnsubscribeRequest req) {
    if (req == null || req.endpoint() == null || req.endpoint().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    Long userId = AuthenticatedUserSupport.requireCurrentUserId();

    final String endpoint;
    try {
      endpoint = pushEndpointValidator.requireAllowedEndpoint(req.endpoint());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    subscriptions.deleteByUserIdAndEndpoint(userId, endpoint);
  }
}
