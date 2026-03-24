package com.w3llspring.fhpb.trophygen.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FallbackTrophyLibrary {

  private static final List<FallbackTemplate> TEMPLATES =
      List.of(
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Breaking a Sweat",
              "/images/trophy_fallback/fallback.png",
              "Participation milestone for the %s season.",
              "Play 3 matches in a single season",
              "matches_played >= 3",
              false,
              null,
              "Generate a common pickleball trophy with energetic warm colors and a player wiping sweat with a paddle."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Dozen Played",
              "/images/trophy_fallback/fallback.png",
              "Participation milestone for the %s season.",
              "Play 12 matches in a single season",
              "matches_played >= 12",
              false,
              null,
              "Generate a common pickleball trophy featuring the number 12 in neon scoreboard styling with rally motion trails."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Grinder",
              "/images/trophy_fallback/fallback.png",
              "Participation milestone for the %s season.",
              "Play 25 matches in a single season",
              "matches_played >= 25",
              false,
              null,
              "Generate a rare pickleball trophy with gritty textures, sparks, and a determined athlete silhouette."),
          new FallbackTemplate(
              TrophyRarity.EPIC,
              "Ironman",
              "/images/trophy_fallback/fallback.png",
              "Participation milestone for the %s season.",
              "Play 40 matches in a single season",
              "matches_played >= 40",
              false,
              null,
              "Generate an epic pickleball trophy with forged metal plating and glowing endurance motifs."),
          new FallbackTemplate(
              TrophyRarity.UNCOMMON,
              "Weekend Warrior",
              "/images/trophy_fallback/fallback.png",
              "Participation milestone for the %s season.",
              "Play 5 matches on Saturday and Sunday of a single weekend",
              "weekend_sat_sun_matches >= 5",
              false,
              null,
              "Generate an uncommon pickleball trophy with sunrise-to-sunset gradients and a weekend calendar badge."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Comeback Kid",
              "/images/trophy_fallback/fallback.png",
              "Participation milestone for the %s season.",
              "After 7+ days without a match, return and play 3+ matches in the same day",
              "days_since_last_match >= 7 && matches_played_today >= 3",
              false,
              null,
              "Generate a rare pickleball trophy with a dramatic sunrise, clock motif, and a player sprinting back onto court."),
          new FallbackTemplate(
              TrophyRarity.UNCOMMON,
              "On a Roll",
              "/images/trophy_fallback/fallback.png",
              "Winning streak accolade for the %s season.",
              "Win 3 matches in a row within a single season",
              "consecutive_wins >= 3",
              false,
              null,
              "Generate an uncommon pickleball trophy with rolling wheels and a glowing three-match tally."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Lucky Seven",
              "/images/trophy_fallback/fallback.png",
              "Winning streak accolade for the %s season.",
              "Win 7 matches in a row within a single season",
              "consecutive_wins >= 7",
              false,
              null,
              "Generate a rare pickleball trophy with casino-inspired sevens, clovers, and celebratory lights."),
          new FallbackTemplate(
              TrophyRarity.EPIC,
              "Decimator",
              "/images/trophy_fallback/fallback.png",
              "Winning streak accolade for the %s season.",
              "Win 10 matches in a row within a single season",
              "consecutive_wins >= 10",
              false,
              null,
              "Generate an epic pickleball trophy with crackling lightning, shattered paddle fragments, and bold typography."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Bumpy Road",
              "/images/trophy_fallback/fallback.png",
              "Losing streak challenge for the %s season.",
              "Lose 4 matches in a row within a single season",
              "consecutive_losses >= 4",
              false,
              null,
              "Generate a common pickleball trophy with a winding road, traffic cones, and resilient player imagery."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Bounce Back",
              "/images/trophy_fallback/fallback.png",
              "Losing streak challenge for the %s season.",
              "After losing 5 in a row, win the next match",
              "consecutive_losses_before_win >= 5",
              false,
              null,
              "Generate a rare pickleball trophy with springs, rebound arrows, and triumphant celebration."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Stop the Bleed",
              "/images/trophy_fallback/fallback.png",
              "Losing streak challenge for the %s season.",
              "Break a 3+ match losing streak with a win",
              "losing_streak_broken >= 3",
              false,
              null,
              "Generate a common pickleball trophy with medical cross accents and a paddle halting a red streak."),
          new FallbackTemplate(
              TrophyRarity.EPIC,
              "Elite 3",
              "/images/trophy_fallback/fallback.png",
              "Division prestige for the %s season.",
              "End the season Top 3 overall",
              "final_rank > 0 && final_rank <= 3",
              false,
              null,
              "Generate an epic pickleball trophy with three shining podium steps and spotlight beams."),
          new FallbackTemplate(
              TrophyRarity.UNCOMMON,
              "Familiar Foe",
              "/images/trophy_fallback/fallback.png",
              "Rivalry highlight for the %s season.",
              "Play the same opponent 5 times in a season",
              "repeat_opponent_matches >= 5",
              false,
              null,
              "Generate an uncommon pickleball trophy with mirrored paddles and competitive stare-down imagery."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Settled the Score",
              "/images/trophy_fallback/fallback.png",
              "Rivalry highlight for the %s season.",
              "Defeat the same opponent 3 times in a row in a season",
              "repeat_opponent_wins_streak >= 3",
              false,
              null,
              "Generate a rare pickleball trophy with scorecard checks and rival silhouettes fading behind."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Social Butterfly",
              "/images/trophy_fallback/fallback.png",
              "Partner showcase for the %s season.",
              "Win with 8 different partners in a season",
              "unique_partners_won_with >= 8",
              false,
              null,
              "Generate a rare pickleball trophy with fluttering ribbons, butterfly wings, and multiple paddles."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Backstabber",
              "/images/trophy_fallback/fallback.png",
              "Partner showcase for the %s season.",
              "Beat your last partner in the very next match",
              "beat_last_partner_next_match >= 1",
              false,
              null,
              "Generate a rare pickleball trophy with dramatic dual paddles crossed like daggers and playful mischief lighting."),
          new FallbackTemplate(
              TrophyRarity.EPIC,
              "Ride or Die",
              "/images/trophy_fallback/fallback.png",
              "Partner showcase for the %s season.",
              "Play 30+ matches with the same partner in a season",
              "matches_with_primary_partner >= 30",
              false,
              null,
              "Generate an epic pickleball trophy with interlocked paddles, chains, and blazing loyalty accents."),
          new FallbackTemplate(
              TrophyRarity.UNCOMMON,
              "Frequent Host",
              "/images/trophy_fallback/fallback.png",
              "Guest play recognition for the %s season.",
              "Play 10+ matches in a season with at least one guest partner",
              "guest_partner_matches >= 10",
              false,
              null,
              "Generate an uncommon pickleball trophy with welcome mats, stars, and a guest badge motif."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Back-to-Back Buddies",
              "/images/trophy_fallback/fallback.png",
              "Partner showcase for the %s season.",
              "Win two matches in the same day with the same partner",
              "same_day_partner_back_to_back_wins >= 2",
              false,
              null,
              "Generate a common pickleball trophy with twin paddles high-fiving over a single-day calendar icon."),
          new FallbackTemplate(
              TrophyRarity.UNCOMMON,
              "Competitive",
              "/images/trophy_fallback/fallback.png",
              "Score-based feat for the %s season.",
              "Win by exactly 2 points",
              "wins_by_margin_2 >= 1",
              false,
              null,
              "Generate an uncommon pickleball trophy with precision measuring lines and a tight score display."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Thursday Thunder",
              "/images/trophy_fallback/fallback.png",
              "Time-based feat for the %s season.",
              "Play a match on a Thu",
              "matches_played_on_thursday >= 1",
              false,
              null,
              "Generate a common pickleball trophy with storm clouds, lightning, and a highlighted Thursday calendar tile."),
          new FallbackTemplate(
              TrophyRarity.COMMON,
              "Scribe",
              "/images/trophy_fallback/fallback.png",
              "Cosign achievement for the %s season.",
              "Cosign 10 matches in the season",
              "matches_cosigned >= 10",
              false,
              null,
              "Generate a common pickleball trophy with ink quills, clipboard, and match logbook styling."),
          new FallbackTemplate(
              TrophyRarity.LEGENDARY,
              "Season Champion",
              "/images/trophy_fallback/fallback.png",
              "Seasonal limited for the %s season.",
              "Finish #1 overall in the season (1 minted)",
              "final_rank == 1",
              true,
              1,
              "Generate a legendary pickleball trophy with a golden crown, laurel wreath, and radiant championship podium."),
          new FallbackTemplate(
              TrophyRarity.EPIC,
              "Runner-Up",
              "/images/trophy_fallback/fallback.png",
              "Seasonal limited for the %s season.",
              "Finish #2 overall in the season (1 minted)",
              "final_rank == 2",
              true,
              1,
              "Generate an epic pickleball trophy with silver laurels, number two medallion, and spotlight shimmer."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Pickler",
              "/images/trophy_fallback/fallback.png",
              "Score-based feat for the %s season.",
              "Opponents finish with exactly 0 points",
              "shutout_wins >= 1",
              false,
              null,
              "Generate a rare pickleball trophy with a spotless scoreboard, sparkling paddles, and celebratory streamers."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Switch Hitters",
              "/images/trophy_fallback/fallback.png",
              "Partner showcase for the %s season.",
              "Alternate partners in 4 consecutive wins",
              "alternating_partner_win_streak >= 4",
              false,
              null,
              "Generate a rare pickleball trophy with rotating paddle icons and a dynamic swirl of partner silhouettes."),
          new FallbackTemplate(
              TrophyRarity.RARE,
              "Division Dominator",
              "/images/trophy_fallback/fallback.png",
              "Division prestige for the %s season.",
              "End the season #1 in your division",
              "final_band_rank == 1",
              false,
              null,
              "Generate a rare pickleball trophy with band banners, crowns, and triumphant division colors."),
          new FallbackTemplate(
              TrophyRarity.UNCOMMON,
              "St. Patrick's Day",
              "/images/trophy_fallback/StPatricksDay_2026_512_c.png",
              "Holiday badge for the %s season.",
              "Play a match on St. Patrick's Day (March 17)",
              "matches_played_on_03_17 >= 1",
              false,
              null,
              "Generate an uncommon pickleball trophy with emerald enamel, shamrocks, gold accents, and festive holiday energy."));

  private FallbackTrophyLibrary() {}

  static List<GeneratedTrophy> create(String seasonName, int desiredCount, Path imageRoot) {
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
              template.maxClaims,
              template.imagePath,
              null,
              "fallback",
              template.prompt,
              seasonName + "-fallback-" + labelLower));
    }
    return trophies;
  }

  static int templateCount() {
    return TEMPLATES.size();
  }

  private static class FallbackTemplate {
    private final TrophyRarity rarity;
    private final String label;
    private final String imagePath;
    private final String summaryTemplate;
    private final String unlockCondition;
    private final String unlockExpression;
    private final boolean limited;
    private final Integer maxClaims;
    private final String prompt;

    private FallbackTemplate(
        TrophyRarity rarity,
        String label,
        String imagePath,
        String summaryTemplate,
        String unlockCondition,
        String unlockExpression,
        boolean limited,
        Integer maxClaims,
        String prompt) {
      this.rarity = rarity;
      this.label = label;
      this.imagePath = imagePath;
      this.summaryTemplate = summaryTemplate;
      this.unlockCondition = unlockCondition;
      this.unlockExpression = unlockExpression;
      this.limited = limited;
      this.maxClaims = maxClaims;
      this.prompt = prompt;
    }
  }
}
