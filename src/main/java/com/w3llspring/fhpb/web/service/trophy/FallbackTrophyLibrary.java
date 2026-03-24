package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.model.TrophyRarity;
import com.w3llspring.fhpb.web.service.BandDivisionSupport;
import com.w3llspring.fhpb.web.service.BandDivisionSupport.BandDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FallbackTrophyLibrary {

  private static final List<FallbackTemplate> STATIC_TEMPLATES =
      List.of(
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Breaking a Sweat",
              "/images/trophy/fallback.png",
              "Participation milestone for the season.",
              "Play 3 matches in a single season",
              "matches_played >= 3",
              false,
              null,
              "Generate a common pickleball trophy with energetic warm colors and a player wiping sweat with a paddle."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Dozen Played",
              "/images/trophy/fallback.png",
              "Participation milestone for the season.",
              "Play 12 matches in a single season",
              "matches_played >= 12",
              false,
              null,
              "Generate a common pickleball trophy featuring the number 12 in neon scoreboard styling with rally motion trails."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Grinder",
              "/images/trophy/fallback.png",
              "Participation milestone for the season.",
              "Play 25 matches in a single season",
              "matches_played >= 25",
              false,
              null,
              "Generate a rare pickleball trophy with gritty textures, sparks, and a determined athlete silhouette."),
          new FallbackTemplate(
              TrophyRarity.EPIC,
              "Ironman",
              "/images/trophy/fallback.png",
              "Participation milestone for the season.",
              "Play 40 matches in a single season",
              "matches_played >= 40",
              false,
              null,
              "Generate an epic pickleball trophy with forged metal plating and glowing endurance motifs."),
          new FallbackTemplate(
              TrophyRarity.UNCOMMON,
              "Weekend Warrior",
              "/images/trophy/fallback.png",
              "Participation milestone for the season.",
              "Play 5 matches on Saturday and Sunday of a single weekend",
              "weekend_sat_sun_matches >= 5",
              false,
              null,
              "Generate an uncommon pickleball trophy with sunrise-to-sunset gradients and a weekend calendar badge."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Comeback Kid",
              "/images/trophy/fallback.png",
              "Participation milestone for the season.",
              "After 7+ days without a match, return and play 3+ matches in the same day",
              "days_since_last_match >= 7 && matches_played_today >= 3",
              false,
              null,
              "Generate a rare pickleball trophy with a dramatic sunrise, clock motif, and a player sprinting back onto court."),
          new FallbackTemplate(
              TrophyRarity.UNCOMMON,
              "On a Roll",
              "/images/trophy/fallback.png",
              "Winning streak accolade for the season.",
              "Win 3 matches in a row within a single season",
              "consecutive_wins >= 3",
              false,
              null,
              "Generate an uncommon pickleball trophy with rolling wheels and a glowing three-match tally."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Lucky Seven",
              "/images/trophy/fallback.png",
              "Winning streak accolade for the season.",
              "Win 7 matches in a row within a single season",
              "consecutive_wins >= 7",
              false,
              null,
              "Generate a rare pickleball trophy with casino-inspired sevens, clovers, and celebratory lights."),
          new FallbackTemplate(
              TrophyRarity.EPIC,
              "Decimator",
              "/images/trophy/fallback.png",
              "Winning streak accolade for the season.",
              "Win 10 matches in a row within a single season",
              "consecutive_wins >= 10",
              false,
              null,
              "Generate an epic pickleball trophy with crackling lightning, shattered paddle fragments, and bold typography."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Bumpy Road",
              "/images/trophy/fallback.png",
              "Losing streak challenge for the season.",
              "Lose 4 matches in a row within a single season",
              "consecutive_losses >= 4",
              false,
              null,
              "Generate a common pickleball trophy with a winding road, traffic cones, and resilient player imagery."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Bounce Back",
              "/images/trophy/fallback.png",
              "Losing streak challenge for the season.",
              "After losing 5 in a row, win the next match",
              "consecutive_losses_before_win >= 5",
              false,
              null,
              "Generate a rare pickleball trophy with springs, rebound arrows, and triumphant celebration."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Stop the Bleed",
              "/images/trophy/fallback.png",
              "Losing streak challenge for the season.",
              "Break a 3+ match losing streak with a win",
              "losing_streak_broken >= 3",
              false,
              null,
              "Generate a common pickleball trophy with medical cross accents and a paddle halting a red streak."),
          new FallbackTemplate(
              TrophyRarity.EPIC,
              "Elite 3",
              "/images/trophy/fallback.png",
              "Division prestige for the season.",
              "End the season Top 3 overall",
              "final_rank > 0 && final_rank <= 3",
              false,
              null,
              "Generate an epic pickleball trophy with three shining podium steps and spotlight beams."),
          new FallbackTemplate(
              TrophyRarity.UNCOMMON,
              "Familiar Foe",
              "/images/trophy/fallback.png",
              "Rivalry highlight for the season.",
              "Play the same opponent 5 times in a season",
              "repeat_opponent_matches >= 5",
              false,
              null,
              "Generate an uncommon pickleball trophy with mirrored paddles and competitive stare-down imagery."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Settled the Score",
              "/images/trophy/fallback.png",
              "Rivalry highlight for the season.",
              "Defeat the same opponent 3 times in a row in a season",
              "repeat_opponent_wins_streak >= 3",
              false,
              null,
              "Generate a rare pickleball trophy with scorecard checks and rival silhouettes fading behind."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Social Butterfly",
              "/images/trophy/fallback.png",
              "Partner showcase for the season.",
              "Win with 8 different partners in a season",
              "unique_partners_won_with >= 8",
              false,
              null,
              "Generate a rare pickleball trophy with fluttering ribbons, butterfly wings, and multiple paddles."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Backstabber",
              "/images/trophy/fallback.png",
              "Partner showcase for the season.",
              "Beat your last partner in the very next match",
              "beat_last_partner_next_match >= 1",
              false,
              null,
              "Generate a rare pickleball trophy with dramatic dual paddles crossed like daggers and playful mischief lighting."),
          new FallbackTemplate(
              TrophyRarity.EPIC,
              "Ride or Die",
              "/images/trophy/fallback.png",
              "Partner showcase for the season.",
              "Play 30+ matches with the same partner in a season",
              "matches_with_primary_partner >= 30",
              false,
              null,
              "Generate an epic pickleball trophy with interlocked paddles, chains, and blazing loyalty accents."),
          new FallbackTemplate(
              TrophyRarity.UNCOMMON,
              "Frequent Host",
              "/images/trophy/fallback.png",
              "Guest play recognition for the season.",
              "Play 10+ matches in a season with at least one guest partner",
              "guest_partner_matches >= 10",
              false,
              null,
              "Generate an uncommon pickleball trophy with welcome mats, stars, and a guest badge motif."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Back-to-Back Buddies",
              "/images/trophy/fallback.png",
              "Partner showcase for the season.",
              "Win two matches in the same day with the same partner",
              "same_day_partner_back_to_back_wins >= 2",
              false,
              null,
              "Generate a common pickleball trophy with twin paddles high-fiving over a single-day calendar icon."),
          new FallbackTemplate(
              TrophyRarity.UNCOMMON,
              "Competitive",
              "/images/trophy/fallback.png",
              "Score-based feat for the season.",
              "Win by exactly 2 points",
              "wins_by_margin_2 >= 1",
              false,
              null,
              "Generate an uncommon pickleball trophy with precision measuring lines and a tight score display."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Thursday Thunder",
              "/images/trophy/fallback.png",
              "Time-based feat for the season.",
              "Play a match on a Thursday",
              "matches_played_on_thursday >= 1",
              false,
              null,
              "Generate a common pickleball trophy with storm clouds, lightning, and a highlighted Thursday calendar tile."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Scribe",
              "/images/trophy/fallback.png",
              "Cosign achievement for the season.",
              "Cosign 10 matches in the season",
              "matches_cosigned >= 10",
              false,
              null,
              "Generate a common pickleball trophy with ink quills, clipboard, and match logbook styling."),
          new FallbackTemplate(
              TrophyRarity.LEGENDARY,
              "Season Champion",
              "/images/trophy/fallback.png",
              "Seasonal limited for the season.",
              "Finish #1 overall in the season",
              "final_rank == 1",
              true,
              1,
              "Generate a legendary pickleball trophy with a golden crown, laurel wreath, and radiant championship podium."),
          new FallbackTemplate(
              TrophyRarity.EPIC,
              "Runner-Up",
              "/images/trophy/fallback.png",
              "Seasonal limited for the season.",
              "Finish #2 overall in the season",
              "final_rank == 2",
              true,
              1,
              "Generate an epic pickleball trophy with silver laurels, number two medallion, and spotlight shimmer."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Pickler",
              "/images/trophy/fallback.png",
              "Score-based feat for the season.",
              "Opponents finish with exactly 0 points",
              "shutout_wins >= 1",
              false,
              null,
              "Generate a rare pickleball trophy with a spotless scoreboard, sparkling paddles, and celebratory streamers."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Switch Hitters",
              "/images/trophy/fallback.png",
              "Partner showcase for the season.",
              "Alternate partners in 4 consecutive wins",
              "alternating_partner_win_streak >= 4",
              false,
              null,
              "Generate a rare pickleball trophy with rotating paddle icons and a dynamic swirl of partner silhouettes."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Division Dominator",
              "/images/trophy/fallback.png",
              "Division prestige for the season.",
              "End the season #1 in your division",
              "final_band_rank == 1",
              false,
              null,
              "Generate a rare pickleball trophy with band banners, crowns, and triumphant division colors."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "St. Patrick's Day",
              "/images/trophy/fallback.png",
              "Holiday badge for the season.",
              "Play a match on St. Patrick's Day (March 17)",
              "matches_played_on_03_17 >= 1",
              false,
              null,
              "Generate an uncommon pickleball trophy with emerald enamel, shamrocks, gold accents, and festive holiday energy."),
          new FallbackTemplate(
              TrophyRarity.LEGENDARY,
              "Court Sovereign",
              "/images/trophy/fallback.png",
              "User-specific standings crown for the season.",
              "Finish #1 among the players you faced this season (min 6-player group)",
              "final_played_group_rank == 1 && final_played_group_size >= 6",
              false,
              null,
              "Generate an epic pickleball trophy with a regal court crown, a single top podium step, and local-rival supremacy energy."),
          new FallbackTemplate(
              TrophyRarity.EPIC,
              "Podium Finish",
              "/images/trophy/fallback.png",
              "User-specific standings finish for the season.",
              "Finish #2 or #3 among the players you faced this season (min 6-player group)",
              "final_played_group_rank >= 2 && final_played_group_rank <= 3 && final_played_group_size >= 6",
              false,
              null,
              "Generate a rare pickleball trophy with a neighborhood podium, bronze and silver accents, and friendly local bragging-rights energy."),
          new FallbackTemplate(
              TrophyRarity.LEGENDARY,
              "Last but not Least",
              "/images/trophy/fallback.png",
              "Season finale oddity for the season.",
              "Finish last overall in the season",
              "is_final_overall_last == 1",
              false,
              null,
              "Generate a common pickleball trophy with a polished wooden spoon, a playful ribbon, and a warmhearted consolation ceremony vibe."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "You got Pickled",
              "/images/trophy/fallback.png",
              "User-specific standings oddity for the season.",
              "Finish last among the players you faced this season (min 6-player group)",
              "final_played_group_size >= 6 && is_final_played_group_last == 1",
              false,
              null,
              "Generate a common pickleball trophy with a pickle jar, playful embarrassment, and chaotic local-rival energy."));

  private static final List<FallbackTemplate> TEMPLATES = buildTemplates();

  private FallbackTrophyLibrary() {}

  static List<FallbackTemplate> templates() {
    return TEMPLATES;
  }

  static List<GeneratedTrophy> create(String seasonName, int desiredCount) {
    if (desiredCount <= 0) {
      return List.of();
    }
    int limit = Math.min(desiredCount, TEMPLATES.size());
    List<GeneratedTrophy> trophies = new ArrayList<>(limit);
    for (int i = 0; i < limit; i++) {
      FallbackTemplate template = TEMPLATES.get(i);
      String labelLower = template.label.toLowerCase(Locale.ENGLISH).replace(" ", "-");
      String title = template.label;
      String summary = String.format(Locale.ENGLISH, template.summaryTemplate, seasonName);
      trophies.add(
          new GeneratedTrophy(
              title,
              summary,
              template.unlockCondition,
              template.unlockExpression,
              template.rarity,
              template.limited,
              false,
              template.maxClaims,
              template.imageUrl,
              "fallback",
              template.prompt,
              seasonName + "-fallback-" + labelLower));
    }
    return trophies;
  }

  static int templateCount() {
    return TEMPLATES.size();
  }

  private static List<FallbackTemplate> buildTemplates() {
    List<FallbackTemplate> templates = new ArrayList<>(STATIC_TEMPLATES.size() + 14);
    templates.addAll(STATIC_TEMPLATES);
    templates.addAll(bandFinishTemplates());
    return List.copyOf(templates);
  }

  private static List<FallbackTemplate> bandFinishTemplates() {
    return BandDivisionSupport.allBandDefinitions().stream()
        .filter(definition -> definition.bandCount() > 1)
        .map(FallbackTrophyLibrary::bandFinishTemplate)
        .toList();
  }

  private static FallbackTemplate bandFinishTemplate(BandDefinition definition) {
    return new FallbackTemplate(
        definition.trophyRarity(),
        definition.name() + " Finish",
        "/images/trophy/fallback.png",
        "Band finish accolade for the season.",
        "Finish the season in " + definition.name(),
        "final_band_index == " + definition.bandIndex(),
        false,
        null,
        definition.trophyPrompt());
  }

  static class FallbackTemplate {
    private final TrophyRarity rarity;
    private final String label;
    private final String imageUrl;
    private final String summaryTemplate;
    private final String unlockCondition;
    private final String unlockExpression;
    private final boolean limited;
    private final Integer maxClaims;
    private final String prompt;

    private FallbackTemplate(
        TrophyRarity rarity,
        String label,
        String imageUrl,
        String summaryTemplate,
        String unlockCondition,
        String unlockExpression,
        boolean limited,
        Integer maxClaims,
        String prompt) {
      this.rarity = rarity;
      this.label = label;
      this.imageUrl = imageUrl;
      this.summaryTemplate = summaryTemplate;
      this.unlockCondition = unlockCondition;
      this.unlockExpression = unlockExpression;
      this.limited = limited;
      this.maxClaims = maxClaims;
      this.prompt = prompt;
    }

    TrophyRarity getRarity() {
      return rarity;
    }

    String getLabel() {
      return label;
    }

    String getImageUrl() {
      return imageUrl;
    }

    String getSummaryTemplate() {
      return summaryTemplate;
    }

    String getUnlockCondition() {
      return unlockCondition;
    }

    String getUnlockExpression() {
      return unlockExpression;
    }

    boolean isLimited() {
      return limited;
    }

    Integer getMaxClaims() {
      return maxClaims;
    }

    String getPrompt() {
      return prompt;
    }
  }
}
