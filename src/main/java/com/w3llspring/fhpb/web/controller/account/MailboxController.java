package com.w3llspring.fhpb.web.controller.account;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MailboxController {
  @GetMapping("/mailbox")
  public String mailbox() {
    return "components/mailbox :: content";
  }
}
