package com.w3llspring.fhpb.web.controller.site;

import com.w3llspring.fhpb.web.util.ReturnToSanitizer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Controller for static legal pages like Terms of Service and Privacy Policy. These pages are
 * accessible without authentication.
 */
@Controller
public class LegalController {

  @GetMapping("/terms")
  public String termsOfService(
      @RequestParam(value = "returnTo", required = false) String returnTo, Model model) {
    prepareLegalNavigation(model, returnTo, "/privacy");
    return "public/terms";
  }

  @GetMapping("/privacy")
  public String privacyPolicy(
      @RequestParam(value = "returnTo", required = false) String returnTo, Model model) {
    prepareLegalNavigation(model, returnTo, "/terms");
    return "public/privacy";
  }

  private void prepareLegalNavigation(Model model, String returnTo, String alternateDocumentPath) {
    String safeReturnTo = ReturnToSanitizer.sanitize(returnTo);
    model.addAttribute("backHref", StringUtils.hasText(safeReturnTo) ? safeReturnTo : "/");
    model.addAttribute("backLabel", StringUtils.hasText(safeReturnTo) ? "Back" : "Back to Home");
    model.addAttribute(
        "alternateLegalHref", buildAlternateLegalHref(alternateDocumentPath, safeReturnTo));
  }

  private String buildAlternateLegalHref(String path, String returnTo) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
    if (StringUtils.hasText(returnTo)) {
      builder.queryParam("returnTo", returnTo);
    }
    return builder.build().encode().toUriString();
  }
}
