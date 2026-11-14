package com.w3llspring.fhpb.web.service.site;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.w3llspring.fhpb.web.config.BrandingProperties;
import com.w3llspring.fhpb.web.config.OperatorProperties;
import com.w3llspring.fhpb.web.util.PublicSiteUrls;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PublicSeoService {
  public static final List<String> INDEXABLE_PATHS = List.of("/", "/terms", "/privacy");
  private final BrandingProperties branding;
  private final OperatorProperties operator;
  private final ObjectMapper json;
  private final String publicBaseUrl;

  public PublicSeoService(
      BrandingProperties branding,
      OperatorProperties operator,
      ObjectMapper json,
      @Value("${fhpb.public.base-url:}") String publicBaseUrl) {
    this.branding = branding;
    this.operator = operator;
    this.json = json;
    this.publicBaseUrl = publicBaseUrl;
  }

  public record PageMetadata(
      boolean indexable,
      String title,
      String description,
      String canonicalUrl,
      String structuredData) {}

  public String baseUrl(HttpServletRequest request) {
    return PublicSiteUrls.baseUrl(publicBaseUrl, operator.getPublicUrl(), request);
  }

  public PageMetadata describe(HttpServletRequest request) {
    String path = PublicSiteUrls.requestPath(request);
    if (!INDEXABLE_PATHS.contains(path)) return new PageMetadata(false, "", "", "", null);
    String title =
        switch (path) {
          case "/terms" -> "Terms of service";
          case "/privacy" -> "Privacy policy";
          default -> branding.getLandingSearchTitle();
        };
    String description =
        switch (path) {
          case "/terms" ->
              "Read the terms for using "
                  + branding.getAppName()
                  + " to organize pickleball sessions, record matches, and track standings.";
          case "/privacy" ->
              "Learn how "
                  + branding.getAppName()
                  + " handles account information, pickleball session activity, and privacy choices.";
          default -> branding.getLandingMetaDescription();
        };
    String canonical = baseUrl(request) + path;
    return new PageMetadata(
        true,
        title + " | " + branding.getAppName(),
        description,
        canonical,
        "/".equals(path) ? websiteData(canonical, description) : null);
  }

  private String websiteData(String canonical, String description) {
    try {
      return json.writeValueAsString(
              Map.of(
                  "@context",
                  "https://schema.org",
                  "@type",
                  "WebSite",
                  "name",
                  branding.getAppName(),
                  "url",
                  canonical,
                  "description",
                  description))
          .replace("<", "\\u003c")
          .replace(">", "\\u003e")
          .replace("&", "\\u0026")
          .replace("\u2028", "\\u2028")
          .replace("\u2029", "\\u2029");
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Unable to render website metadata", ex);
    }
  }
}
