package com.w3llspring.fhpb.web.controller.meetups;

import com.w3llspring.fhpb.web.service.meetups.MeetupsEmailDigestService;
import com.w3llspring.fhpb.web.service.meetups.MeetupsEmailLinkSigner;
import com.w3llspring.fhpb.web.service.meetups.MeetupsEmailLinkSigner.UnsubscribeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/meetups")
public class MeetupsEmailUnsubscribeController {

  private static final Logger log =
      LoggerFactory.getLogger(MeetupsEmailUnsubscribeController.class);

  private final MeetupsEmailDigestService digests;
  private final MeetupsEmailLinkSigner linkSigner;

  public MeetupsEmailUnsubscribeController(
      MeetupsEmailDigestService digests, MeetupsEmailLinkSigner linkSigner) {
    this.digests = digests;
    this.linkSigner = linkSigner;
  }

  @GetMapping("/unsubscribe")
  public String unsubscribe(@RequestParam("token") String token, Model model) {
    try {
      UnsubscribeToken ut = linkSigner.verifyUnsubscribe(token);
      model.addAttribute("token", token);
      model.addAttribute("expiresAt", ut.expiresAt());
      return "public/unsubscribe_confirm";
    } catch (Exception e) {
      log.warn("[meetups-email] unsubscribe failed token={}", token, e);
      model.addAttribute("success", false);
      model.addAttribute("error", "Invalid or expired link.");
    }

    return "public/unsubscribe_success";
  }

  @PostMapping("/unsubscribe")
  public String unsubscribePost(@RequestParam("token") String token, Model model) {
    try {
      UnsubscribeToken ut = linkSigner.verifyUnsubscribe(token);
      digests.recordOptIn(ut.userId(), false);
      log.info("[meetups-email] unsubscribed userId={}", ut.userId());
      model.addAttribute("success", true);
    } catch (Exception e) {
      log.warn("[meetups-email] unsubscribe failed token={}", token, e);
      model.addAttribute("success", false);
      model.addAttribute("error", "Invalid or expired link.");
    }

    return "public/unsubscribe_success";
  }
}
