package com.w3llspring.fhpb.web.service.trophy;

import java.util.List;

/** Shared by the catalog migration and award-rule regression tests. */
public final class RoundRobinTrophyTemplates {
  private RoundRobinTrophyTemplates() {}

  public record Definition(
      String slug,
      String title,
      String condition,
      String expression,
      String rarity,
      String artSource) {}

  public static List<Definition> definitions() {
    return List.of(
        new Definition(
            "rr-champion",
            "Round Robin Champion",
            "Finish first by wins, then points for, with at least 4 players.",
            "final_rank == 1 && round_robin_players >= 4 && matches_played >= 1",
            "RARE",
            "Season Champion"),
        new Definition(
            "rr-runner-up",
            "Round Robin Runner-Up",
            "Finish second by wins, then points for, with at least 4 players.",
            "final_rank == 2 && round_robin_players >= 4 && matches_played >= 1",
            "UNCOMMON",
            "Runner-Up"),
        new Definition(
            "rr-wooden-spoon",
            "Wooden Spoon",
            "Finish last in a round robin with at least 3 competing players or teams.",
            "is_final_overall_last == 1 && final_total_competitors >= 3 && round_robin_players >= 4 && matches_played >= 1",
            "COMMON",
            "Last but not Least"),
        new Definition(
            "rr-mix-master",
            "Mix Master",
            "Win with 4 different partners in one rotating-partner round robin.",
            "rotating_partners == 1 && unique_partners_won_with >= 4",
            "RARE",
            "Social Butterfly"),
        new Definition(
            "rr-court-captain",
            "Court Captain",
            "Organize a round robin with at least 4 confirmed games and 4 players.",
            "is_round_robin_host == 1 && round_robin_games >= 4 && round_robin_players >= 4",
            "UNCOMMON",
            "Frequent Host"),
        new Definition(
            "rr-court-time",
            "Court Time",
            "Play 3 confirmed games in one round robin.",
            "matches_played >= 3",
            "COMMON",
            "Breaking a Sweat"),
        new Definition(
            "rr-close-call",
            "Close Call",
            "Win a game by exactly 2 points in a round robin.",
            "wins_by_margin_2 >= 1",
            "COMMON",
            "Competitive"),
        new Definition(
            "rr-winning-pair",
            "Winning Pair",
            "Win twice with the same partner in one round robin.",
            "wins_with_same_partner >= 2",
            "COMMON",
            "Back-to-Back Buddies"),
        new Definition(
            "rr-back-in-it",
            "Back in It",
            "Win after 2 consecutive losses in a round robin.",
            "losing_streak_broken >= 2",
            "UNCOMMON",
            "Stop the Bleed"));
  }
}
