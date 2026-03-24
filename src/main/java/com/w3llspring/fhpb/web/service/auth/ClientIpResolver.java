package com.w3llspring.fhpb.web.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

  private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
  private static final String HEADER_X_REAL_IP = "X-Real-IP";

  private final List<CidrRange> trustedProxies;

  public ClientIpResolver(
      @Value("${fhpb.security.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}")
          String trustedProxies) {
    this.trustedProxies = parseTrustedProxies(trustedProxies);
  }

  public String resolve(HttpServletRequest request) {
    if (request == null) {
      return "unknown";
    }

    String remote = normalizeIp(request.getRemoteAddr());
    if (!StringUtils.hasText(remote)) {
      return "unknown";
    }

    if (!isTrustedProxy(remote)) {
      return remote;
    }

    String forwardedFor = firstForwardedIp(request.getHeader(HEADER_X_FORWARDED_FOR));
    if (StringUtils.hasText(forwardedFor)) {
      return forwardedFor;
    }

    String realIp = normalizeIp(request.getHeader(HEADER_X_REAL_IP));
    if (StringUtils.hasText(realIp)) {
      return realIp;
    }

    return remote;
  }

  private boolean isTrustedProxy(String remoteAddr) {
    InetAddress remote = parseAddress(remoteAddr);
    if (remote == null) {
      return false;
    }
    for (CidrRange range : trustedProxies) {
      if (range.contains(remote)) {
        return true;
      }
    }
    return false;
  }

  private String firstForwardedIp(String headerValue) {
    if (!StringUtils.hasText(headerValue)) {
      return null;
    }
    String[] parts = headerValue.split(",");
    if (parts.length == 0) {
      return null;
    }
    return normalizeIp(parts[0]);
  }

  private String normalizeIp(String candidate) {
    if (!StringUtils.hasText(candidate)) {
      return null;
    }
    InetAddress address = parseAddress(candidate.trim());
    return address == null ? null : address.getHostAddress();
  }

  private InetAddress parseAddress(String value) {
    if (!isNumericIpLiteral(value)) {
      return null;
    }
    try {
      return InetAddress.getByName(value);
    } catch (Exception ex) {
      return null;
    }
  }

  private List<CidrRange> parseTrustedProxies(String configured) {
    List<CidrRange> ranges = new ArrayList<>();
    if (!StringUtils.hasText(configured)) {
      return ranges;
    }
    String[] parts = configured.split("[,;\\s]+");
    for (String part : parts) {
      if (!StringUtils.hasText(part)) {
        continue;
      }
      CidrRange range = CidrRange.parse(part.trim());
      if (range != null) {
        ranges.add(range);
      }
    }
    return ranges;
  }

  private boolean isNumericIpLiteral(String value) {
    if (!StringUtils.hasText(value)) {
      return false;
    }
    String candidate = value.trim();
    if (candidate.contains(":")) {
      return candidate.matches("[0-9A-Fa-f:]+");
    }
    if (!candidate.matches("(\\d{1,3}\\.){3}\\d{1,3}")) {
      return false;
    }
    String[] octets = candidate.split("\\.");
    for (String octet : octets) {
      int parsed;
      try {
        parsed = Integer.parseInt(octet);
      } catch (NumberFormatException ex) {
        return false;
      }
      if (parsed < 0 || parsed > 255) {
        return false;
      }
    }
    return true;
  }

  private static final class CidrRange {
    private final byte[] networkBytes;
    private final int prefixLength;

    private CidrRange(byte[] networkBytes, int prefixLength) {
      this.networkBytes = networkBytes;
      this.prefixLength = prefixLength;
    }

    private static CidrRange parse(String value) {
      if (!StringUtils.hasText(value)) {
        return null;
      }
      String trimmed = value.trim();
      int slash = trimmed.indexOf('/');
      try {
        if (slash < 0) {
          InetAddress address = InetAddress.getByName(trimmed);
          return new CidrRange(address.getAddress(), address.getAddress().length * 8);
        }

        String host = trimmed.substring(0, slash).trim();
        String prefix = trimmed.substring(slash + 1).trim();
        InetAddress address = InetAddress.getByName(host);
        int prefixLength = Integer.parseInt(prefix);
        int maxBits = address.getAddress().length * 8;
        if (prefixLength < 0 || prefixLength > maxBits) {
          return null;
        }
        return new CidrRange(address.getAddress(), prefixLength);
      } catch (Exception ex) {
        return null;
      }
    }

    private boolean contains(InetAddress address) {
      byte[] candidate = address.getAddress();
      if (candidate.length != networkBytes.length) {
        return false;
      }

      int fullBytes = prefixLength / 8;
      int remainingBits = prefixLength % 8;

      for (int i = 0; i < fullBytes; i++) {
        if (candidate[i] != networkBytes[i]) {
          return false;
        }
      }

      if (remainingBits == 0) {
        return true;
      }

      int mask = 0xFF << (8 - remainingBits);
      return (candidate[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
    }
  }
}
