package com.w3llspring.fhpb.web.controller.site;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AppleTouchIconController {

  @GetMapping("/apple-touch-icon-precomposed.png")
  public String appleTouchIconPrecomposed() {
    return "redirect:/apple-touch-icon.png";
  }
}
