package com.w3llspring.fhpb.web.controller.advice;

import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.roundrobin.RoundRobinService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.WebRequest;

/**
 * ControllerAdvice to provide common model attributes for round-robin pages.
 *
 * <p>- If a request contains a ladderId parameter and the model contains a `members` list, this
 * advice will compute a `displayNames` map (userId -> courtname-nickname) using the ladder-specific
 * season context so templates can render names consistently. - If a `roundRobin` object is present
 * in the model, expose its season as `rrSeason`.
 */
@ControllerAdvice(annotations = Controller.class)
public class RoundRobinModelAdvice {

  private final RoundRobinService roundRobinService;

  @Autowired
  public RoundRobinModelAdvice(RoundRobinService roundRobinService) {
    this.roundRobinService = roundRobinService;
  }

  @ModelAttribute
  public void addRoundRobinAttributes(WebRequest request, org.springframework.ui.Model model) {
    // If a roundRobin object exists, expose its season as rrSeason
    Object rrObj = model.asMap().get("roundRobin");
    if (rrObj instanceof com.w3llspring.fhpb.web.model.RoundRobin) {
      com.w3llspring.fhpb.web.model.RoundRobin rr =
          (com.w3llspring.fhpb.web.model.RoundRobin) rrObj;
      model.addAttribute("rrSeason", rr.getSeason());
    }

    // If ladderId param present and members list is in the model, compute displayNames
    String ladderIdParam = request.getParameter("ladderId");
    if (ladderIdParam != null && !model.containsAttribute("displayNames")) {
      Long ladderId = null;
      try {
        ladderId = Long.parseLong(ladderIdParam);
      } catch (Exception ignored) {
      }
      if (ladderId != null) {
        LadderSeason season = roundRobinService.findSeasonForLadder(ladderId);
        Object membersObj = model.asMap().get("members");
        if (membersObj instanceof List) {
          @SuppressWarnings("unchecked")
          List<User> members = (List<User>) membersObj;
          Map<Long, String> displayNames = new HashMap<>();
          for (User u : members) {
            if (u == null || u.getId() == null) continue;
            displayNames.put(u.getId(), roundRobinService.getDisplayNameForUser(u, season));
          }
          model.addAttribute("displayNames", displayNames);
        }
      }
    }
  }
}
