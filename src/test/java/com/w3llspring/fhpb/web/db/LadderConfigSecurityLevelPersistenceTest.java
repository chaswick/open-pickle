package com.w3llspring.fhpb.web.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
class LadderConfigSecurityLevelPersistenceTest {

  @Autowired private LadderConfigRepository configs;

  @Test
  void persistsAllSecurityLevels() {
    for (LadderSecurity level : LadderSecurity.values()) {
      LadderConfig cfg = new LadderConfig();
      cfg.setTitle("Test " + level);
      cfg.setOwnerUserId(1L);
      cfg.setInviteCode("INV-" + level);
      cfg.setSecurityLevel(level);

      LadderConfig saved = configs.saveAndFlush(cfg);
      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getSecurityLevel()).isEqualTo(level);
    }
  }
}
