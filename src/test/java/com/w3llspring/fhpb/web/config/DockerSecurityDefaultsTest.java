package com.w3llspring.fhpb.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.service.jobs.startup.DevDataSeeder;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class DockerSecurityDefaultsTest {

  @Test
  void dockerDefaultsDisableDemoAccountsAndDestructiveStartupResets() throws Exception {
    Properties properties = PropertiesLoaderUtils.loadProperties(
        new ClassPathResource("application-docker.properties"));

    new ApplicationContextRunner()
        .withInitializer(context -> {
          context.getEnvironment().setActiveProfiles("docker");
          context.getEnvironment().getPropertySources()
              .addFirst(new PropertiesPropertySource("docker-defaults", properties));
        })
        .withUserConfiguration(DevDataSeeder.class)
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(DevDataSeeder.class);
          var environment = context.getEnvironment();
          assertThat(environment.getProperty("fhpb.dev.seed-data.enabled", Boolean.class)).isFalse();
          assertThat(environment.getProperty("fhpb.dev.reset-data-on-startup", Boolean.class)).isFalse();
          assertThat(environment.getProperty("fhpb.dev.reset-schema-on-startup", Boolean.class)).isFalse();
          assertThat(environment.getProperty("spring.flyway.clean-disabled", Boolean.class)).isTrue();
        });
  }
}
