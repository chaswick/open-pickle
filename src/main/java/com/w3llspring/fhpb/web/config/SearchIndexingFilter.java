package com.w3llspring.fhpb.web.config;

import com.w3llspring.fhpb.web.service.site.PublicSeoService;
import com.w3llspring.fhpb.web.util.PublicSiteUrls;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SearchIndexingFilter extends OncePerRequestFilter {
  private static final List<String> ASSET_PREFIXES =
      List.of("/css/", "/js/", "/images/", "/fonts/", "/webjars/");
  private static final List<String> DISCOVERY_PATHS =
      List.of("/robots.txt", "/sitemap.xml", "/site.webmanifest", "/sw.js");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = PublicSiteUrls.requestPath(request);
    boolean asset =
        ASSET_PREFIXES.stream().anyMatch(path::startsWith)
            || path.startsWith("/favicon")
            || path.startsWith("/android-chrome-")
            || path.startsWith("/apple-touch-icon")
            || path.startsWith("/mstile-")
            || path.equals("/safari-pinned-tab.svg")
            || path.equals("/browserconfig.xml");
    if (!PublicSeoService.INDEXABLE_PATHS.contains(path)
        && !DISCOVERY_PATHS.contains(path)
        && !asset) {
      response.setHeader("X-Robots-Tag", "noindex, nofollow");
    }
    chain.doFilter(request, response);
  }
}
