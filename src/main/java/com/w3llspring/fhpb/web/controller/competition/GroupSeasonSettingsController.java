package com.w3llspring.fhpb.web.controller.competition;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.competition.GroupSeasonSettingsService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/groups")
public class GroupSeasonSettingsController {

  private final GroupSeasonSettingsService groupSeasonSettingsService;

  public GroupSeasonSettingsController(GroupSeasonSettingsService groupSeasonSettingsService) {
    this.groupSeasonSettingsService = groupSeasonSettingsService;
  }

  @PostMapping("/{ladderId}/config/all")
  public String updateSettings(
      @PathVariable Long ladderId,
      @RequestParam("mode") LadderConfig.Mode mode,
      @RequestParam("securityLevel") LadderSecurity securityLevel,
      @RequestParam(
              value = "allowGuestOnlyPersonalMatches",
              required = false,
              defaultValue = "false")
          boolean allowGuestOnlyPersonalMatches,
      @RequestParam(value = "carryOverPreviousRating", required = false, defaultValue = "false")
          boolean carryOverPreviousRating,
      @RequestParam(value = "storyModeDefaultEnabled", required = false, defaultValue = "false")
          boolean storyModeDefaultEnabled,
      @RequestParam(value = "invitesEnabled") boolean invitesEnabled,
      @RequestParam(value = "count", required = false) Integer count,
      @RequestParam(value = "unit", required = false) LadderConfig.CadenceUnit unit,
      Authentication auth,
      RedirectAttributes redirectAttributes) {

    User currentUser = AuthenticatedUserSupport.currentUser(auth);
    Long requesterUserId = currentUser != null ? currentUser.getId() : null;

    GroupSeasonSettingsService.UpdateOutcome outcome =
        groupSeasonSettingsService.updateSettings(
            ladderId,
            requesterUserId,
            mode,
            securityLevel,
            allowGuestOnlyPersonalMatches,
            carryOverPreviousRating,
            storyModeDefaultEnabled,
            invitesEnabled,
            count,
            unit);

    redirectAttributes.addFlashAttribute("toastMessage", outcome.toastMessage());
    redirectAttributes.addFlashAttribute("toastLevel", outcome.toastLevel());
    return "redirect:" + outcome.redirectPath();
  }
}
