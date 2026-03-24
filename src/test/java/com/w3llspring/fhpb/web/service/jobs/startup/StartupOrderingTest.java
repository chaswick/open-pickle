package com.w3llspring.fhpb.web.service.jobs.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

class StartupOrderingTest {

  @Test
  void devSeederRunsBeforeGlobalLadderBootstrap() throws Exception {
    Method seedMethod = DevDataSeeder.class.getDeclaredMethod("seed");
    Method bootstrapMethod = GlobalLadderStartup.class.getDeclaredMethod("onReady");

    Order seedOrder = seedMethod.getAnnotation(Order.class);
    Order bootstrapOrder = bootstrapMethod.getAnnotation(Order.class);

    assertThat(seedOrder).isNotNull();
    assertThat(bootstrapOrder).isNotNull();
    assertThat(seedOrder.value()).isLessThan(bootstrapOrder.value());
  }
}
