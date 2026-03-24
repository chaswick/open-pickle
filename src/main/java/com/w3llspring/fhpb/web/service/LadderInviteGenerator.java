package com.w3llspring.fhpb.web.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class LadderInviteGenerator {

  private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
  private static final int INVITE_LENGTH = 20; // 32^20 ~= 2^100 possible codes

  private final SecureRandom random;

  public LadderInviteGenerator() {
    this(new SecureRandom());
  }

  LadderInviteGenerator(SecureRandom random) {
    this.random = random != null ? random : new SecureRandom();
  }

  public String generate() {
    char[] invite = new char[INVITE_LENGTH];
    for (int i = 0; i < invite.length; i++) {
      invite[i] = ALPHABET[random.nextInt(ALPHABET.length)];
    }
    return new String(invite);
  }
}
