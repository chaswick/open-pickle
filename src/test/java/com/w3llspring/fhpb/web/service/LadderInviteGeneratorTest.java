package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LadderInviteGeneratorTest {

  private final LadderInviteGenerator generator = new LadderInviteGenerator();

  @Test
  void generatesUppercaseOpaqueInviteCodes() {
    String invite = generator.generate();

    assertThat(invite).hasSize(20);
    assertThat(invite).matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]+");
  }

  @Test
  void generatedInvitesDoNotCollideInSampleSet() {
    Set<String> invites = new HashSet<>();

    for (int i = 0; i < 2000; i++) {
      invites.add(generator.generate());
    }

    assertThat(invites).hasSize(2000);
  }
}
