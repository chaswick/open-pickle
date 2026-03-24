package com.w3llspring.fhpb.web.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class SeasonNameGeneratorTest {

  private final SeasonNameGenerator generator = new SeasonNameGenerator();

  @Test
  void generatesNonNullNames() {
    LocalDate date = LocalDate.of(2025, 6, 15);
    String name = generator.generate(date);

    assertNotNull(name);
    assertFalse(name.isBlank());
    assertTrue(name.contains("2025"));
  }

  @Test
  void generatesVarietyOfNamesForSamePeriod() {
    LocalDate date = LocalDate.of(2025, 7, 1);
    Set<String> names = new HashSet<>();

    // Generate 20 names for the same period
    for (int i = 0; i < 20; i++) {
      names.add(generator.generate(date));
    }

    // Should get some variety (randomization working)
    assertTrue(names.size() > 1, "Expected variety in generated names, got: " + names);
  }

  @Test
  void generatesWithOrdinal() {
    LocalDate date = LocalDate.of(2025, 3, 15);

    String first = generator.generateWithOrdinal(date, 1);
    String second = generator.generateWithOrdinal(date, 2);
    String third = generator.generateWithOrdinal(date, 3);

    assertFalse(first.contains("I "), "First season should not have ordinal");
    assertTrue(second.contains("II"), "Second season should have Roman numeral II");
    assertTrue(third.contains("III"), "Third season should have Roman numeral III");
  }

  @Test
  void seasonalThemesChangeByMonth() {
    // Just verify that different months can produce different themes
    // (not guaranteed due to randomness, but should work most of the time)
    LocalDate winter = LocalDate.of(2025, 1, 15);
    LocalDate summer = LocalDate.of(2025, 7, 15);
    LocalDate fall = LocalDate.of(2025, 10, 15);

    String winterName = generator.generate(winter);
    String summerName = generator.generate(summer);
    String fallName = generator.generate(fall);

    // All should have year
    assertTrue(winterName.contains("2025"));
    assertTrue(summerName.contains("2025"));
    assertTrue(fallName.contains("2025"));

    // Names should be different (with high probability given the variety)
    Set<String> uniqueNames = Set.of(winterName, summerName, fallName);
    assertTrue(
        uniqueNames.size() >= 2,
        "Expected seasonal variety across winter/summer/fall, got: " + uniqueNames);
  }

  @Test
  void handlesNullDate() {
    String name = generator.generate(null);
    assertEquals("Season", name);
  }

  @Test
  void handlesNullDateWithOrdinal() {
    String name = generator.generateWithOrdinal(null, 2);
    assertEquals("Season", name);
  }
}
