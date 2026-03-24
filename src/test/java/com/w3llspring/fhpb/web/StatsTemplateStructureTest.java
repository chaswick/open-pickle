package com.w3llspring.fhpb.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StatsTemplateStructureTest {

  @Test
  void statsTemplatePlacesActiveSeasonStatsBetweenPerformanceAndSiteActivity() throws Exception {
    String template = Files.readString(Path.of("src/main/resources/templates/auth/stats.html"));

    int performanceIndex = template.indexOf("Performance Stats");
    int activeSeasonIndex = template.indexOf("Active Seasons Stats");
    int siteActivityIndex = template.indexOf("Site Activity");

    assertThat(performanceIndex).isGreaterThanOrEqualTo(0);
    assertThat(activeSeasonIndex).isGreaterThan(performanceIndex);
    assertThat(siteActivityIndex).isGreaterThan(activeSeasonIndex);
    assertThat(template).contains("card-stack-col");
    assertThat(template).contains("th:with=\"activeStats=${userStats.activeSeasonStats}\"");
    assertThat(template).contains("th:text=\"${activeStats.totalMatches}\"");
    assertThat(template).contains("th:text=\"${activeStats.winRate}\"");
    assertThat(template).contains("th:text=\"${userStats.favoritePartner}\"");
    assertThat(template).contains("th:text=\"${activeStats.favoritePartner}\"");
    assertThat(template).contains("th:text=\"${userStats.mostBeatenOpponent}\"");
    assertThat(template).contains("th:text=\"${activeStats.mostBeatenOpponent}\"");
  }
}
