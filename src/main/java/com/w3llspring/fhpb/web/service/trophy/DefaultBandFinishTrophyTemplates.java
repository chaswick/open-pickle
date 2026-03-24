package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyCatalogEntry;
import com.w3llspring.fhpb.web.model.TrophyRarity;
import com.w3llspring.fhpb.web.service.BandDivisionSupport;
import com.w3llspring.fhpb.web.service.BandDivisionSupport.BandDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

final class DefaultBandFinishTrophyTemplates {

  private static final String SUMMARY_TEMPLATE = "Band finish accolade for the %s season.";
  private static final String RETIRED_OPEN_DIVISION_TITLE = "Open Division Finish";
  private static final String RETIRED_OPEN_DIVISION_EXPRESSION = "final_band_index == 1";

  private static final List<TemplateSpec> SPECS =
      BandDivisionSupport.allBandDefinitions().stream()
          .filter(definition -> definition.bandCount() > 1)
          .map(DefaultBandFinishTrophyTemplates::spec)
          .collect(Collectors.toUnmodifiableList());

  private DefaultBandFinishTrophyTemplates() {}

  static List<TemplateSpec> specs() {
    return SPECS;
  }

  static boolean appliesToSeason(Trophy trophy, LadderSeason season, int standingCount) {
    return appliesToSeason(
        trophy != null ? trophy.getTitle() : null,
        trophy != null ? trophy.getUnlockExpression() : null,
        season,
        standingCount);
  }

  static boolean appliesToSeason(
      TrophyCatalogEntry trophy, LadderSeason season, int standingCount) {
    return appliesToSeason(
        trophy != null ? trophy.getTitle() : null,
        trophy != null ? trophy.getUnlockExpression() : null,
        season,
        standingCount);
  }

  private static boolean appliesToSeason(
      String title, String unlockExpression, LadderSeason season, int standingCount) {
    if (isRetiredLegacyTemplate(title, unlockExpression)) {
      return false;
    }
    TemplateSpec spec = matchingSpec(title, unlockExpression);
    if (spec == null) {
      return true;
    }
    if (season == null || season.getState() != LadderSeason.State.ENDED) {
      return false;
    }
    int bandCount = BandDivisionSupport.determineBandCount(Math.max(standingCount, 0));
    return spec.bandCount() == bandCount;
  }

  static boolean isBandFinishTemplate(Trophy trophy) {
    return matchingSpec(trophy) != null;
  }

  static TemplateSpec matchingSpec(Trophy trophy) {
    return trophy == null ? null : matchingSpec(trophy.getTitle(), trophy.getUnlockExpression());
  }

  static TemplateSpec matchingSpec(TrophyCatalogEntry trophy) {
    return trophy == null ? null : matchingSpec(trophy.getTitle(), trophy.getUnlockExpression());
  }

  private static TemplateSpec matchingSpec(String title, String unlockExpression) {
    String normalizedUnlockExpression = normalize(unlockExpression);
    String normalizedTitle = normalize(title);
    for (TemplateSpec spec : SPECS) {
      if (spec.matches(normalizedTitle, normalizedUnlockExpression)) {
        return spec;
      }
    }
    return null;
  }

  private static boolean isRetiredLegacyTemplate(Trophy trophy) {
    return trophy != null
        && isRetiredLegacyTemplate(trophy.getTitle(), trophy.getUnlockExpression());
  }

  private static boolean isRetiredLegacyTemplate(String title, String unlockExpression) {
    return Objects.equals(normalize(title), normalize(RETIRED_OPEN_DIVISION_TITLE))
        && Objects.equals(normalize(unlockExpression), normalize(RETIRED_OPEN_DIVISION_EXPRESSION));
  }

  private static TemplateSpec spec(BandDefinition definition) {
    return new TemplateSpec(
        definition.bandCount(),
        definition.bandIndex(),
        definition.trophyRarity(),
        definition.name() + " Finish",
        definition.name(),
        SUMMARY_TEMPLATE,
        "Finish the season in " + definition.name(),
        "final_band_index == " + definition.bandIndex(),
        definition.trophyPrompt(),
        definition.legacyNames().stream()
            .map(alias -> alias + " Finish")
            .collect(Collectors.toUnmodifiableList()));
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ENGLISH);
  }

  static record TemplateSpec(
      int bandCount,
      int bandIndex,
      TrophyRarity rarity,
      String title,
      String divisionName,
      String summaryTemplate,
      String unlockCondition,
      String unlockExpression,
      String prompt,
      List<String> legacyTitles) {

    boolean matches(String normalizedTitle, String normalizedUnlockExpression) {
      if (!Objects.equals(normalize(unlockExpression), normalizedUnlockExpression)) {
        return false;
      }
      if (Objects.equals(normalize(title), normalizedTitle)) {
        return true;
      }
      return legacyTitles.stream()
          .map(DefaultBandFinishTrophyTemplates::normalize)
          .anyMatch(candidate -> Objects.equals(candidate, normalizedTitle));
    }

    String formattedSummary(String seasonName) {
      return String.format(Locale.ENGLISH, summaryTemplate, seasonName);
    }
  }
}
