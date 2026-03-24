package com.w3llspring.fhpb.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestTimingFilter extends OncePerRequestFilter {

  private static final Logger perfLog = LoggerFactory.getLogger("com.w3llspring.fhpb.perf");
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${fhpb.perf.enabled:true}")
  private boolean enabled;

  /**
   * Comma-separated list of request paths to record. Supports prefix match with '*'. Defaults focus
   * on user-experience endpoints: home (leaderboard-ish) + match submit.
   */
  @Value("${fhpb.perf.paths:/home,/voice-match-log/interpret}")
  private String paths;

  private volatile List<String> parsedPaths;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!enabled) {
      filterChain.doFilter(request, response);
      return;
    }

    String uri = safeUri(request);
    if (!shouldRecord(uri)) {
      filterChain.doFilter(request, response);
      return;
    }

    long startNs = System.nanoTime();
    int status = 0;
    try {
      filterChain.doFilter(request, response);
    } finally {
      try {
        status = response.getStatus();
      } catch (Exception ignored) {
        status = 0;
      }
      long ms = (System.nanoTime() - startNs) / 1_000_000L;
      writeJsonLine(request, uri, status, ms);
    }
  }

  private String safeUri(HttpServletRequest request) {
    try {
      String uri = request.getRequestURI();
      if (uri == null || uri.isBlank()) {
        return "/";
      }
      if (uri.length() > 400) {
        return uri.substring(0, 400);
      }
      return uri;
    } catch (Exception e) {
      return "/";
    }
  }

  private boolean shouldRecord(String uri) {
    List<String> allow = parsedPaths;
    if (allow == null) {
      allow = parsePaths(paths);
      parsedPaths = allow;
    }
    if (allow.isEmpty()) {
      return false;
    }

    String normalized = normalize(uri);
    for (String entry : allow) {
      if (entry.endsWith("*")) {
        String prefix = entry.substring(0, entry.length() - 1);
        if (normalized.startsWith(prefix)) {
          return true;
        }
      } else if (normalized.equals(entry)) {
        return true;
      }
    }
    return false;
  }

  private String normalize(String uri) {
    if (uri == null || uri.isBlank()) return "/";
    if (uri.length() > 1 && uri.endsWith("/")) {
      return uri.substring(0, uri.length() - 1);
    }
    return uri;
  }

  private List<String> parsePaths(String raw) {
    List<String> out = new ArrayList<>();
    if (raw == null || raw.isBlank()) return out;
    for (String part : raw.split(",")) {
      if (part == null) continue;
      String p = part.trim();
      if (p.isEmpty()) continue;
      if (!p.startsWith("/")) p = "/" + p;
      out.add(normalize(p));
    }
    return out;
  }

  private void writeJsonLine(HttpServletRequest request, String uri, int status, long ms) {
    try {
      String method = request.getMethod();
      if (method == null) method = "";
      Map<String, Object> payload =
          Map.of(
              "type", "http_perf",
              "ts", Instant.now().toString(),
              "method", method,
              "uri", uri,
              "status", status,
              "ms", ms);
      perfLog.info(objectMapper.writeValueAsString(payload));
    } catch (Exception e) {
      // best-effort: never break requests because logging failed
    }
  }
}
