package com.w3llspring.fhpb.web.service;

import java.time.Instant;

public class InviteChangeCooldownException extends IllegalStateException {

  private final Instant allowedAt;

  public InviteChangeCooldownException(String message, Instant allowedAt) {
    super(message);
    this.allowedAt = allowedAt;
  }

  public Instant getAllowedAt() {
    return allowedAt;
  }
}
