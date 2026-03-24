package com.w3llspring.fhpb.web.service.push;

import com.w3llspring.fhpb.web.util.InputValidation;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PushEndpointValidator {

  private final List<AllowedHostPattern> allowedHosts;

  public PushEndpointValidator(
      @Value(
              "${fhpb.push.allowed-endpoint-hosts:"
                  + "fcm.googleapis.com,"
                  + "push.services.mozilla.com,"
                  + "updates.push.services.mozilla.com,"
                  + "web.push.apple.com,"
                  + "*.push.apple.com}")
          String allowedHosts) {
    this.allowedHosts = parseAllowedHosts(allowedHosts);
  }

  public String requireAllowedEndpoint(String value) {
    String endpoint = InputValidation.requirePushEndpoint(value);
    URI uri;
    try {
      uri = URI.create(endpoint);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Push endpoint is invalid.");
    }

    if (StringUtils.hasText(uri.getUserInfo())) {
      throw new IllegalArgumentException("Push endpoint is invalid.");
    }

    int port = uri.getPort();
    if (port != -1 && port != 443) {
      throw new IllegalArgumentException("Push endpoint is invalid.");
    }

    String host = uri.getHost();
    if (!StringUtils.hasText(host) || !matchesAllowedHost(host)) {
      throw new IllegalArgumentException("Push endpoint host is not allowed.");
    }

    return endpoint;
  }

  public boolean isAllowed(String value) {
    try {
      requireAllowedEndpoint(value);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private boolean matchesAllowedHost(String host) {
    String normalized = host.trim().toLowerCase(Locale.US);
    for (AllowedHostPattern pattern : allowedHosts) {
      if (pattern.matches(normalized)) {
        return true;
      }
    }
    return false;
  }

  private List<AllowedHostPattern> parseAllowedHosts(String configured) {
    List<AllowedHostPattern> parsed = new ArrayList<>();
    if (!StringUtils.hasText(configured)) {
      return parsed;
    }
    String[] parts = configured.split("[,;\\s]+");
    for (String part : parts) {
      if (!StringUtils.hasText(part)) {
        continue;
      }
      AllowedHostPattern pattern = AllowedHostPattern.parse(part.trim());
      if (pattern != null) {
        parsed.add(pattern);
      }
    }
    return parsed;
  }

  private static final class AllowedHostPattern {
    private final String normalized;
    private final boolean wildcard;

    private AllowedHostPattern(String normalized, boolean wildcard) {
      this.normalized = normalized;
      this.wildcard = wildcard;
    }

    private static AllowedHostPattern parse(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
      if (!StringUtils.hasText(normalized)) {
        return null;
      }
      if (normalized.startsWith("*.")) {
        String suffix = normalized.substring(2);
        if (!StringUtils.hasText(suffix)) {
          return null;
        }
        return new AllowedHostPattern(suffix, true);
      }
      return new AllowedHostPattern(normalized, false);
    }

    private boolean matches(String host) {
      if (!wildcard) {
        return normalized.equals(host);
      }
      return host.endsWith("." + normalized);
    }
  }
}
