package com.w3llspring.fhpb.web.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.web.util.UriComponentsBuilder;

/** Shared URL rules for canonical links, social images, and crawl discovery. */
public final class PublicSiteUrls {
  private PublicSiteUrls() {}

  public static String baseUrl(String configured, String operatorUrl, HttpServletRequest request) {
    String base = normalize(configured);
    if (base.isEmpty()) base = normalize(operatorUrl);
    if (!base.isEmpty()) return base;
    if (request == null || request.getServerName() == null) return "";
    int port = request.getServerPort();
    boolean defaultPort =
        port <= 0
            || ("https".equalsIgnoreCase(request.getScheme()) && port == 443)
            || ("http".equalsIgnoreCase(request.getScheme()) && port == 80);
    return UriComponentsBuilder.newInstance()
        .scheme(request.getScheme())
        .host(request.getServerName())
        .port(defaultPort ? -1 : port)
        .path(request.getContextPath())
        .build()
        .toUriString();
  }

  public static String requestPath(HttpServletRequest request) {
    if (request == null || request.getRequestURI() == null) return "";
    String uri = request.getRequestURI();
    String context = request.getContextPath();
    return context != null && !context.isEmpty() && uri.startsWith(context)
        ? uri.substring(context.length())
        : uri;
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return "";
    try {
      URI uri = URI.create(value.trim());
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null
          || "your-domain.example".equalsIgnoreCase(uri.getHost())
          || uri.getRawUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null) return "";
      return uri.toASCIIString().replaceAll("/+$", "");
    } catch (IllegalArgumentException ex) {
      return "";
    }
  }
}
