package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.model.TrophyRarity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BandDivisionSupport {

  private static final Map<Integer, List<BandDefinition>> BAND_DEFINITIONS = buildDefinitions();

  private BandDivisionSupport() {}

  public static int determineBandCount(int totalPlayers) {
    if (totalPlayers <= 4) {
      return 1;
    }
    if (totalPlayers <= 8) {
      return 2;
    }
    if (totalPlayers <= 12) {
      return 3;
    }
    if (totalPlayers <= 18) {
      return 4;
    }
    return 5;
  }

  public static Map<Integer, String> resolveBandNames(int bandCount) {
    Map<Integer, String> names = new LinkedHashMap<>();
    if (bandCount <= 0) {
      return names;
    }

    List<BandDefinition> definitions = BAND_DEFINITIONS.get(bandCount);
    if (definitions != null) {
      for (BandDefinition definition : definitions) {
        names.put(definition.bandIndex(), definition.name());
      }
      return names;
    }

    for (int i = 1; i <= bandCount; i++) {
      names.put(i, "Division " + i);
    }
    return names;
  }

  public static List<BandDefinition> resolveBandDefinitions(int bandCount) {
    List<BandDefinition> definitions = BAND_DEFINITIONS.get(bandCount);
    return definitions != null ? definitions : List.of();
  }

  public static List<BandDefinition> allBandDefinitions() {
    return BAND_DEFINITIONS.values().stream().flatMap(List::stream).toList();
  }

  public static Optional<BandDefinition> findBandDefinition(int bandCount, int bandIndex) {
    return resolveBandDefinitions(bandCount).stream()
        .filter(definition -> definition.bandIndex() == bandIndex)
        .findFirst();
  }

  private static Map<Integer, List<BandDefinition>> buildDefinitions() {
    Map<Integer, List<BandDefinition>> definitions = new LinkedHashMap<>();
    definitions.put(1, List.of(definition(1, 1, "Open Division", null, null)));
    definitions.put(
        2,
        List.of(
            definition(
                2,
                1,
                "Upper Division",
                TrophyRarity.UNCOMMON,
                "Generate an uncommon pickleball trophy with elevated podium steps, bright trim, and a bold upper-flight banner.",
                "Summit Division"),
            definition(
                2,
                2,
                "Lower Division",
                TrophyRarity.COMMON,
                "Generate a common pickleball trophy with grounded bronze trim, sturdy framing, and a resilient lower-flight badge.",
                "Basecamp Division")));
    definitions.put(
        3,
        List.of(
            definition(
                3,
                1,
                "Premier Division",
                TrophyRarity.RARE,
                "Generate a rare pickleball trophy with polished trim, confident spotlighting, and a premier division crest.",
                "Gold Division"),
            definition(
                3,
                2,
                "Select Division",
                TrophyRarity.UNCOMMON,
                "Generate an uncommon pickleball trophy with sharp detailing, cool highlights, and a select division shield.",
                "Silver Division"),
            definition(
                3,
                3,
                "Club Division",
                TrophyRarity.COMMON,
                "Generate a common pickleball trophy with welcoming club energy, durable framing, and a sturdy division badge.",
                "Bronze Division")));
    definitions.put(
        4,
        List.of(
            definition(
                4,
                1,
                "Champion Division",
                TrophyRarity.EPIC,
                "Generate an epic pickleball trophy with championship spotlights, polished trim, and a bold champion crest.",
                "Platinum Division"),
            definition(
                4,
                2,
                "Master Division",
                TrophyRarity.RARE,
                "Generate a rare pickleball trophy with master-level polish, confident lighting, and a distinguished division emblem.",
                "Premier Division",
                "Gold Division"),
            definition(
                4,
                3,
                "Contender Division",
                TrophyRarity.UNCOMMON,
                "Generate an uncommon pickleball trophy with rising momentum, battle-ready trim, and a contender badge.",
                "Select Division",
                "Silver Division"),
            definition(
                4,
                4,
                "Challenger Division",
                TrophyRarity.COMMON,
                "Generate a common pickleball trophy with upward-path motifs, sturdy trim, and a challenger division seal.",
                "Club Division",
                "Bronze Division")));
    definitions.put(
        5,
        List.of(
            definition(
                5,
                1,
                "Diamond Division",
                TrophyRarity.EPIC,
                "Generate an epic pickleball trophy with diamond facets, prismatic light, and a crown-like division crest.",
                "Crown Division"),
            definition(
                5,
                2,
                "Platinum Division",
                TrophyRarity.RARE,
                "Generate a rare pickleball trophy with platinum facets, luminous highlights, and an elite division emblem.",
                "Elite Division"),
            definition(
                5,
                3,
                "Gold Division",
                TrophyRarity.UNCOMMON,
                "Generate an uncommon pickleball trophy with gold plating, sunburst highlights, and a polished division shield.",
                "Prime Division"),
            definition(
                5,
                4,
                "Silver Division",
                TrophyRarity.UNCOMMON,
                "Generate an uncommon pickleball trophy with silver filigree, cool gleam, and a balanced division crest.",
                "Contender Division"),
            definition(
                5,
                5,
                "Bronze Division",
                TrophyRarity.COMMON,
                "Generate a common pickleball trophy with warm bronze metal, textured trim, and a sturdy division seal.",
                "Challenger Division")));
    return definitions;
  }

  private static BandDefinition definition(
      int bandCount,
      int bandIndex,
      String name,
      TrophyRarity trophyRarity,
      String trophyPrompt,
      String... legacyNames) {
    return new BandDefinition(
        bandCount, bandIndex, name, trophyRarity, trophyPrompt, List.of(legacyNames));
  }

  public record BandDefinition(
      int bandCount,
      int bandIndex,
      String name,
      TrophyRarity trophyRarity,
      String trophyPrompt,
      List<String> legacyNames) {

    public BandDefinition {
      legacyNames = legacyNames == null ? List.of() : List.copyOf(legacyNames);
    }

    public boolean matchesCurrentOrLegacyName(String candidate) {
      if (Objects.equals(name, candidate)) {
        return true;
      }
      return legacyNames.stream().anyMatch(alias -> Objects.equals(alias, candidate));
    }
  }
}
