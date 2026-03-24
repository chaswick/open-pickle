package com.w3llspring.fhpb.web.controller.site;

import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {

  @GetMapping({"/", ""})
  public String root() {
    if (AuthenticatedUserSupport.currentUser() != null) {
      return "redirect:/home";
    }
    return "public/landing";
  }
}
