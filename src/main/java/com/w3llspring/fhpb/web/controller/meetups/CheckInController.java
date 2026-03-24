package com.w3llspring.fhpb.web.controller.meetups;

import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.PlayLocationService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Secured("ROLE_USER")
public class CheckInController {

  @Value("${fhpb.features.check-in.enabled:true}")
  private boolean checkInEnabled;

  private final PlayLocationService playLocationService;

  public CheckInController(PlayLocationService playLocationService) {
    this.playLocationService = playLocationService;
  }

  @GetMapping("/check-in")
  public String checkInPage(
      @RequestParam(value = "toastMessage", required = false) String toastMessage,
      @RequestParam(value = "autostart", required = false, defaultValue = "false")
          boolean autoStart,
      Model model) {
    if (!checkInEnabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    User currentUser = resolveCurrentUser();
    if (currentUser == null) {
      return "redirect:/login";
    }

    PlayLocationService.CheckInPageView pageView = playLocationService.buildPage(currentUser);
    boolean hasActiveCheckIn = pageView.getActiveCheckIn() != null;

    model.addAttribute("userName", currentUser.getNickName());
    model.addAttribute("showLadderSelection", Boolean.FALSE);
    model.addAttribute("checkInPage", pageView);
    model.addAttribute("checkInExpiryLabel", playLocationService.getExpiryLabel());
    model.addAttribute("checkInNameMaxLength", playLocationService.getLocationNameMaxLength());
    model.addAttribute("autoStartCheckIn", autoStart && !hasActiveCheckIn);
    if (toastMessage != null && !toastMessage.isBlank()) {
      model.addAttribute("toastLevel", "success");
      model.addAttribute("toastAuto", Boolean.TRUE);
      model.addAttribute("toastMessage", toastMessage);
    }
    return "auth/check-in";
  }

  private User resolveCurrentUser() {
    return AuthenticatedUserSupport.currentUser();
  }
}
