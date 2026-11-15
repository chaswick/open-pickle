package com.w3llspring.fhpb.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationDiscoveryTest {

  @Test
  void productionMigrationsCanBeResolvedWithoutDuplicateVersions() {
    // Resolve the MySQL migrations without executing SQL against the H2 test database.
    Flyway flyway =
        Flyway.configure()
            .dataSource("jdbc:h2:mem:migration_discovery;DB_CLOSE_DELAY=-1", "sa", "")
            .locations("classpath:db/migration")
            .load();

    var migrations = assertDoesNotThrow(() -> flyway.info().all());
    assertTrue(migrations.length > 0, "Production migrations must be on the test classpath");
  }
}
