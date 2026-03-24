package com.w3llspring.fhpb.trophygen.core;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TrophyGenerator {

  private final TrophyGeneratorConfig config;
  private final PromptLibrary promptLibrary;
  private final OpenAiImageClient imageClient;
  private final Path fallbackImageRoot;

  public TrophyGenerator(
      TrophyGeneratorConfig config, PromptLibrary promptLibrary, Path fallbackImageRoot) {
    this.config = config;
    this.promptLibrary = promptLibrary;
    this.fallbackImageRoot = fallbackImageRoot;
    this.imageClient = new OpenAiImageClient(config);
  }

  public List<GeneratedTrophy> generateSeasonTrophies(TrophyGenerationRequest request)
      throws Exception {
    int desired = resolveDesiredCount(request);
    if (config.isEnabled() || config.isDebugPrompts()) {
      return generateAiTrophies(request, desired);
    }
    int fallbackCount = Math.max(desired, FallbackTrophyLibrary.templateCount());
    String seasonName = resolveSeasonName(request);
    return FallbackTrophyLibrary.create(seasonName, fallbackCount, fallbackImageRoot);
  }

  public String generatePromptPreview(
      String seasonName, LocalDate seasonStart, TrophyRarity rarity) {
    if (rarity == null) {
      throw new IllegalArgumentException("Rarity is required for prompt preview.");
    }
    String resolvedSeasonName =
        (seasonName == null || seasonName.isBlank()) ? "Season" : seasonName;
    int slot = ThreadLocalRandom.current().nextInt(10_000);
    return buildPrompt(resolvedSeasonName, rarity, seasonStart, slot);
  }

  private List<GeneratedTrophy> generateAiTrophies(TrophyGenerationRequest request, int desired)
      throws Exception {
    List<GeneratedTrophy> trophies = new ArrayList<>(desired);
    String seasonName = resolveSeasonName(request);
    LocalDate startDate = request != null ? request.getSeasonStart() : null;

    for (int i = 0; i < desired; i++) {
      TrophyRarity rarity = pickRarity(i);
      String title = buildTitle(rarity);
      String summary = "Badge for the " + seasonName + " season.";
      UnlockRule unlockRule = unlockRuleFor(rarity);
      String prompt = buildPrompt(seasonName, rarity, startDate, i);

      byte[] imageBytes = null;
      String provider = config.isEnabled() ? "openai" : "openai-debug";
      if (config.isEnabled()) {
        imageBytes = imageClient.generateImage(prompt);
      }

      GeneratedTrophy trophy =
          new GeneratedTrophy(
              title,
              summary,
              unlockRule.unlockCondition,
              unlockRule.unlockExpression,
              rarity,
              rarity == TrophyRarity.LEGENDARY,
              rarity == TrophyRarity.LEGENDARY ? 1 : null,
              null,
              imageBytes,
              provider,
              prompt,
              UUID.randomUUID().toString());
      trophies.add(trophy);
    }
    return trophies;
  }

  private int resolveDesiredCount(TrophyGenerationRequest request) {
    int fromRequest = request != null ? request.getDesiredCount() : 0;
    int base = fromRequest > 0 ? fromRequest : config.getDesiredCount();
    return Math.max(1, base);
  }

  private String resolveSeasonName(TrophyGenerationRequest request) {
    String seasonName = request != null ? request.getSeasonName() : null;
    return (seasonName == null || seasonName.isBlank()) ? "Season" : seasonName;
  }

  private TrophyRarity pickRarity(int index) {
    switch (index) {
      case 0:
        return TrophyRarity.COMMON;
      case 1:
        return TrophyRarity.RARE;
      default:
        return TrophyRarity.LEGENDARY;
    }
  }

  private String buildTitle(TrophyRarity rarity) {
    switch (rarity) {
      case COMMON:
        return "Momentum Builder";
      case RARE:
        return "Shutout Streak";
      case LEGENDARY:
        return "Division Conqueror";
      case UNCOMMON:
        return "Matchup Explorer";
      case EPIC:
        return "Clutch Comeback";
      default:
        return capitalize(rarity.name().toLowerCase(Locale.ENGLISH).replace('_', ' '));
    }
  }

  private String buildPrompt(
      String seasonName, TrophyRarity rarity, LocalDate startDate, int slot) {
    String palette = paletteFor(seasonName, rarity, slot);
    String lighting = pickRandom(promptLibrary.getLighting(), rarity, "Balanced lighting.");
    String material = pickRandom(promptLibrary.getMaterial(), rarity, "Render as polished emblem.");
    String border = pickRandom(promptLibrary.getBorder(), rarity, "Include themed frame.");
    String flourish = pickRandom(promptLibrary.getFlourish(), rarity, "Add coastal sport icons.");
    String depthMotion =
        pickRandom(promptLibrary.getDepth(), rarity, "Balanced depth presentation.");
    String motif =
        pickRandom(
            promptLibrary.getMotif(),
            rarity,
            "Incorporate iconography that celebrates competitive pickleball achievements.");
    String localeBackdrop = backdropForLocale(startDate);

    String template = config.getPromptTemplate();
    String prompt =
        template
            .replace("{season}", seasonName)
            .replace("{rarity}", rarity.name().toLowerCase(Locale.ENGLISH))
            .replace("{palette}", palette);

    return prompt
        + " "
        + lighting
        + " "
        + material
        + " "
        + border
        + " "
        + localeBackdrop
        + " "
        + flourish
        + " "
        + motif
        + " "
        + depthMotion
        + " Photorealistic elements discouraged. Render as vector-style game badge.";
  }

  private String paletteFor(String seasonName, TrophyRarity rarity, int slot) {
    Map<TrophyRarity, List<String>> library = promptLibrary.getPalette();
    List<String> palettes = library != null ? library.get(rarity) : null;
    if (palettes == null || palettes.isEmpty()) {
      palettes = List.of("coastal palette");
    }
    int seed = (seasonName != null ? seasonName.hashCode() : 0) + rarity.ordinal() * 37 + slot * 17;
    int index = Math.floorMod(seed, palettes.size());
    return palettes.get(index);
  }

  private String backdropForLocale(LocalDate startDate) {
    String mood;
    if (startDate != null) {
      int month = startDate.getMonthValue();
      if (month >= 3 && month <= 5) {
        mood = "spring sunrise over Tampa Bay marinas";
      } else if (month >= 6 && month <= 8) {
        mood = "vibrant summer sunset along coastal trails";
      } else if (month >= 9 && month <= 11) {
        mood = "fiery autumn sky above coastal palms";
      } else {
        mood = "cool winter dusk over Hillsborough River lights";
      }
    } else {
      mood = "dawn on the pickleball courts";
    }
    return "Backdrop should feature " + mood + ".";
  }

  private UnlockRule unlockRuleFor(TrophyRarity rarity) {
    String fallbackCondition = "Complete a highlight achievement.";
    String fallbackExpression = "true";

    Map<TrophyRarity, List<String>> conditionLibrary = promptLibrary.getUnlockCondition();
    Map<TrophyRarity, List<String>> expressionLibrary = promptLibrary.getUnlockExpression();

    List<String> conditions = conditionLibrary != null ? conditionLibrary.get(rarity) : null;
    List<String> expressions = expressionLibrary != null ? expressionLibrary.get(rarity) : null;

    if (conditions == null
        || conditions.isEmpty()
        || expressions == null
        || expressions.isEmpty()) {
      String condition = pickRandom(conditionLibrary, rarity, fallbackCondition);
      String expression = pickRandom(expressionLibrary, rarity, fallbackExpression);
      return new UnlockRule(condition, expression);
    }

    int pairCount = Math.min(conditions.size(), expressions.size());
    if (pairCount <= 0) {
      return new UnlockRule(fallbackCondition, fallbackExpression);
    }

    int index = ThreadLocalRandom.current().nextInt(pairCount);
    return new UnlockRule(conditions.get(index), expressions.get(index));
  }

  private String pickRandom(
      Map<TrophyRarity, List<String>> library, TrophyRarity rarity, String fallback) {
    if (library == null) {
      return fallback;
    }
    List<String> options = library.get(rarity);
    if (options == null || options.isEmpty()) {
      return fallback;
    }
    int index = ThreadLocalRandom.current().nextInt(options.size());
    return options.get(index);
  }

  private String capitalize(String input) {
    if (input == null || input.isBlank()) {
      return input;
    }
    return Character.toUpperCase(input.charAt(0)) + input.substring(1);
  }

  private static class UnlockRule {
    private final String unlockCondition;
    private final String unlockExpression;

    private UnlockRule(String unlockCondition, String unlockExpression) {
      this.unlockCondition = unlockCondition;
      this.unlockExpression = unlockExpression;
    }
  }
}
