package com.w3llspring.fhpb.web.controller.meetups;

import com.w3llspring.fhpb.web.service.meetups.MeetupsEmailDigestService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/meetups")
@Secured("ROLE_USER")
public class MeetupsEmailPreferenceController {

  private final MeetupsEmailDigestService digests;

  public MeetupsEmailPreferenceController(MeetupsEmailDigestService digests) {
    this.digests = digests;
  }

  public record MeetupsEmailPrefRequest(Boolean optIn) {}

  @PostMapping("/email-opt-in")
  public void emailOptIn(@RequestBody MeetupsEmailPrefRequest req) {
    if (req == null || req.optIn() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    Long userId = AuthenticatedUserSupport.requireCurrentUserId();
    digests.recordOptIn(userId, req.optIn());
  }
}
