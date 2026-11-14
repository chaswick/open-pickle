package com.w3llspring.fhpb.web.config;

import com.w3llspring.fhpb.web.service.site.PublicSeoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class SeoModelAttributes {
  private final PublicSeoService seo;

  public SeoModelAttributes(PublicSeoService seo) {
    this.seo = seo;
  }

  @ModelAttribute("publicSeo")
  public PublicSeoService.PageMetadata metadata(HttpServletRequest request) {
    return seo.describe(request);
  }
}
