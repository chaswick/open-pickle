package com.w3llspring.fhpb.web.service.trophy;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.model.TrophyRarity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class FallbackTrophyLibraryConsistencyTest {

  private static final Path SEED_SCRIPT =
      Path.of("scripts", "db", "seed", "seed_default_trophies.sql");
  private static final Pattern SEED_ROW_PATTERN =
      Pattern.compile(
          "^\\(@season_id, '((?:[^']|'')*)', '((?:[^']|'')*)', '((?:[^']|'')*)', '((?:[^']|'')*)', '([A-Z]+)', ([01]), (NULL|\\d+), '((?:[^']|'')*)', '((?:[^']|'')*)', '((?:[^']|'')*)', (\\d+), ([01]), (@[a-z_]+), NULL, NOW\\(6\\), NOW\\(6\\)\\)(,|;)$");

  @Test
  void fallbackTemplates_matchSeedDefaultTrophyMetadata() throws IOException {
    List<SeedRow> expected = parseSeedRows();
    List<GeneratedTrophy> actual = FallbackTrophyTemplates.createAll("Ignored Season Name");

    assertThat(actual).hasSize(expected.size());

    SoftAssertions.assertSoftly(
        softly -> {
          for (int i = 0; i < expected.size(); i++) {
            SeedRow seedRow = expected.get(i);
            GeneratedTrophy trophy = actual.get(i);

            softly
                .assertThat(trophy.getTitle())
                .as("title at index %s", i)
                .isEqualTo(seedRow.title());
            softly
                .assertThat(trophy.getSummary())
                .as("summary for %s", seedRow.title())
                .isEqualTo(seedRow.summary());
            softly
                .assertThat(trophy.getUnlockCondition())
                .as("unlock condition for %s", seedRow.title())
                .isEqualTo(seedRow.unlockCondition());
            softly
                .assertThat(trophy.getUnlockExpression())
                .as("unlock expression for %s", seedRow.title())
                .isEqualTo(seedRow.unlockExpression());
            softly
                .assertThat(trophy.getRarity())
                .as("rarity for %s", seedRow.title())
                .isEqualTo(seedRow.rarity());
            softly
                .assertThat(trophy.isLimited())
                .as("limited flag for %s", seedRow.title())
                .isEqualTo(seedRow.limited());
            softly
                .assertThat(trophy.getMaxClaims())
                .as("max claims for %s", seedRow.title())
                .isEqualTo(seedRow.maxClaims());
            softly
                .assertThat(trophy.getImageUrl())
                .as("image url for %s", seedRow.title())
                .isEqualTo(seedRow.imageUrl());
            softly
                .assertThat(trophy.getAiProvider())
                .as("AI provider for %s", seedRow.title())
                .isEqualTo("fallback");
          }
        });
  }

  private List<SeedRow> parseSeedRows() throws IOException {
    assertThat(SEED_SCRIPT).exists();

    List<SeedRow> rows = new ArrayList<>();
    for (String rawLine : Files.readAllLines(SEED_SCRIPT)) {
      String line = rawLine.trim();
      if (!line.startsWith("(@season_id,")) {
        continue;
      }
      Matcher matcher = SEED_ROW_PATTERN.matcher(line);
      assertThat(matcher.matches()).as("seed row format: %s", line).isTrue();
      assertThat(matcher.group(12))
          .as("default template flag in seed row: %s", line)
          .isEqualTo("1");
      rows.add(
          new SeedRow(
              unescapeSql(matcher.group(1)),
              unescapeSql(matcher.group(2)),
              unescapeSql(matcher.group(3)),
              unescapeSql(matcher.group(4)),
              TrophyRarity.valueOf(matcher.group(5)),
              "1".equals(matcher.group(6)),
              parseNullableInt(matcher.group(7)),
              imageUrlForArtRef(matcher.group(13))));
    }
    return rows;
  }

  private String unescapeSql(String value) {
    return value.replace("''", "'");
  }

  private Integer parseNullableInt(String value) {
    return "NULL".equals(value) ? null : Integer.valueOf(value);
  }

  private String imageUrlForArtRef(String artRef) {
    return switch (artRef) {
      case "@fallback_art_id" -> "/images/trophy/fallback.png";
      default -> throw new IllegalArgumentException("Unexpected art ref: " + artRef);
    };
  }

  private record SeedRow(
      String title,
      String summary,
      String unlockCondition,
      String unlockExpression,
      TrophyRarity rarity,
      boolean limited,
      Integer maxClaims,
      String imageUrl) {}
}
