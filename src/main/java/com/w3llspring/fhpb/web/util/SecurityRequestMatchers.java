package com.w3llspring.fhpb.web.util;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class SecurityRequestMatchers {

  private static final PathPatternRequestMatcher.Builder PATH_MATCHERS =
      PathPatternRequestMatcher.withDefaults();

  private SecurityRequestMatchers() {}

  public static RequestMatcher path(String pattern) {
    return PATH_MATCHERS.matcher(pattern);
  }

  public static RequestMatcher path(HttpMethod method, String pattern) {
    return PATH_MATCHERS.matcher(method, pattern);
  }
}
