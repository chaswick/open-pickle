package com.w3llspring.fhpb.web.controller.site;

import com.w3llspring.fhpb.web.service.site.PublicSeoService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class SearchDiscoveryController {
  private final PublicSeoService seo;

  public SearchDiscoveryController(PublicSeoService seo) {
    this.seo = seo;
  }

  @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> robots(HttpServletRequest request) {
    // HTML pages remain crawlable so crawlers can read their noindex directives.
    String body =
        "User-agent: *\nAllow: /\nDisallow: /api/\nDisallow: /experimental/\nDisallow: /h2-console/\n\nSitemap: "
            + seo.baseUrl(request)
            + "/sitemap.xml\n";
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(body);
  }

  @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> sitemap(HttpServletRequest request) {
    StringBuilder xml =
        new StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    String base = seo.baseUrl(request);
    for (String path : PublicSeoService.INDEXABLE_PATHS) {
      xml.append("  <url><loc>")
          .append(HtmlUtils.htmlEscape(base + path, "UTF-8"))
          .append("</loc></url>\n");
    }
    xml.append("</urlset>\n");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(xml.toString());
  }
}
