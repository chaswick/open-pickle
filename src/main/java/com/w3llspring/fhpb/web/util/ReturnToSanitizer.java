package com.w3llspring.fhpb.web.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.util.StringUtils;

public final class ReturnToSanitizer {

  private ReturnToSanitizer() {}

  public static String sanitize(String returnTo) {
    if (!StringUtils.hasText(returnTo)) {
      return null;
    }
    if (returnTo.contains("\n")
        || returnTo.contains("\r")
        || returnTo.contains("\\")
        || !returnTo.startsWith("/")
        || returnTo.startsWith("//")) {
      return null;
    }

    String path = returnTo;
    try {
      URI uri = URI.create(returnTo);
      if (StringUtils.hasText(uri.getScheme())
          || StringUtils.hasText(uri.getRawAuthority())
          || StringUtils.hasText(uri.getRawFragment())
          || !StringUtils.hasText(uri.getRawPath())
          || !uri.getRawPath().startsWith("/")
          || uri.getRawPath().startsWith("//")) {
        return null;
      }
      path = uri.getRawPath();
    } catch (Exception ex) {
      return null;
    }

    String pathLower = path.toLowerCase(Locale.ROOT);
    if (pathLower.equals("/error")
        || pathLower.startsWith("/error/")
        || pathLower.startsWith("/.well-known/")
        || pathLower.startsWith("/css/")
        || pathLower.startsWith("/js/")
        || pathLower.startsWith("/images/")
        || pathLower.startsWith("/webjars/")
        || pathLower.equals("/favicon.ico")
        || pathLower.equals("/site.webmanifest")
        || pathLower.equals("/sw.js")) {
      return null;
    }

    return returnTo;
  }

  public static String toAppRelativePath(SavedRequest savedRequest, String contextPath) {
    if (savedRequest == null || !StringUtils.hasText(savedRequest.getRedirectUrl())) {
      return null;
    }
    try {
      URI uri = URI.create(savedRequest.getRedirectUrl());
      String rawPath = uri.getRawPath();
      String rawQuery = uri.getRawQuery();
      if (!StringUtils.hasText(rawPath)) {
        return null;
      }
      String pathAndQuery =
          rawPath + (rawQuery != null && !rawQuery.isBlank() ? "?" + rawQuery : "");
      if (StringUtils.hasText(contextPath) && pathAndQuery.startsWith(contextPath + "/")) {
        return pathAndQuery.substring(contextPath.length());
      }
      return pathAndQuery;
    } catch (Exception e) {
      return null;
    }
  }

  public static String toAppRelativePath(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    String requestUri = request.getRequestURI();
    if (!StringUtils.hasText(requestUri)) {
      return null;
    }
    String contextPath = request.getContextPath();
    String path = requestUri;
    if (StringUtils.hasText(contextPath) && requestUri.startsWith(contextPath)) {
      path = requestUri.substring(contextPath.length());
    }
    if (!StringUtils.hasText(path)) {
      path = "/";
    }
    String query = request.getQueryString();
    return path + (StringUtils.hasText(query) ? "?" + query : "");
  }

  public static String sanitizeSavedRequest(SavedRequest savedRequest, String contextPath) {
    return sanitize(toAppRelativePath(savedRequest, contextPath));
  }
}
