package com.w3llspring.fhpb.web.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"dev", "docker"})
public class DevFlywayConfig {

  private static final Logger log = LoggerFactory.getLogger(DevFlywayConfig.class);

  @Bean
  public FlywayMigrationStrategy devFlywayMigrationStrategy(
      @Value("${fhpb.dev.reset-schema-on-startup:false}") boolean resetSchemaOnStartup) {
    return flyway -> migrateDevSchema(flyway, resetSchemaOnStartup);
  }

  private void migrateDevSchema(Flyway flyway, boolean resetSchemaOnStartup) {
    if (flyway == null) {
      return;
    }
    if (resetSchemaOnStartup) {
      log.warn("Dev schema reset is enabled; cleaning and re-applying Flyway migrations.");
      flyway.clean();
    } else {
      log.info("Dev schema reset is disabled; applying Flyway migrations without clean.");
    }
    flyway.migrate();
  }
}
