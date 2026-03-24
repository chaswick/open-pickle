package com.w3llspring.fhpb.web.controller.meetups;

import com.w3llspring.fhpb.web.service.meetups.MeetupsEmailDigestService;
import com.w3llspring.fhpb.web.service.meetups.MeetupsEmailDigestService.DebugSendResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/meetups/email")
@Secured("ROLE_USER")
public class MeetupsEmailAdminDebugController {

  private final MeetupsEmailDigestService digests;

  public MeetupsEmailAdminDebugController(MeetupsEmailDigestService digests) {
    this.digests = digests;
  }

  @PostMapping("/send-pending")
  public DebugSendResult sendPending() {
    // Uses same admin gate pattern as other controllers.
    if (!digests.isCurrentUserAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    return digests.debugTrySendPending();
  }
}
