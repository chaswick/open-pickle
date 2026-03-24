-- Production seed script for default trophy catalog entries
-- Usage: mysql -u openpickle -p openpickle < scripts/db/seed/seed_default_trophies.sql
-- Or paste the contents into your MySQL client
--
-- By default these entries are global and can apply to any season.
-- To restrict them to a specific season, set @season_id to that ladder_season.id.

SET @season_id = NULL; -- Keep NULL for global defaults, or set to a specific season ID

SET @fallback_image_url = '/images/trophy/fallback.png';
SET @fallback_art_key = SHA2(CONCAT('', '|', @fallback_image_url), 256);

INSERT INTO trophy_art (
  storage_key,
  image_url,
  image_bytes,
  created_at,
  updated_at
) VALUES
(@fallback_art_key, @fallback_image_url, NULL, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

SET @fallback_art_id = (SELECT id FROM trophy_art WHERE storage_key = @fallback_art_key);

INSERT INTO trophy_catalog (
  season_id,
  title,
  summary,
  unlock_condition,
  unlock_expression,
  rarity,
  is_limited,
  max_claims,
  ai_provider,
  generation_seed,
  slug,
  display_order,
  is_default_template,
  art_id,
  prompt,
  created_at,
  updated_at
) VALUES
(@season_id, 'Breaking a Sweat', 'Participation milestone for the season.', 'Play 3 matches in a single season', 'matches_played >= 3', 'COMMON', 0, NULL, 'fallback', 'default-template-breaking-a-sweat-0', 'default-template-breaking-a-sweat-0', 0, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Dozen Played', 'Participation milestone for the season.', 'Play 12 matches in a single season', 'matches_played >= 12', 'COMMON', 0, NULL, 'fallback', 'default-template-dozen-played-1', 'default-template-dozen-played-1', 1, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Grinder', 'Participation milestone for the season.', 'Play 25 matches in a single season', 'matches_played >= 25', 'RARE', 0, NULL, 'fallback', 'default-template-grinder-2', 'default-template-grinder-2', 2, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Ironman', 'Participation milestone for the season.', 'Play 40 matches in a single season', 'matches_played >= 40', 'EPIC', 0, NULL, 'fallback', 'default-template-ironman-3', 'default-template-ironman-3', 3, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Weekend Warrior', 'Participation milestone for the season.', 'Play 5 matches on Saturday and Sunday of a single weekend', 'weekend_sat_sun_matches >= 5', 'UNCOMMON', 0, NULL, 'fallback', 'default-template-weekend-warrior-4', 'default-template-weekend-warrior-4', 4, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Comeback Kid', 'Participation milestone for the season.', 'After 7+ days without a match, return and play 3+ matches in the same day', 'days_since_last_match >= 7 && matches_played_today >= 3', 'RARE', 0, NULL, 'fallback', 'default-template-comeback-kid-5', 'default-template-comeback-kid-5', 5, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'On a Roll', 'Winning streak accolade for the season.', 'Win 3 matches in a row within a single season', 'consecutive_wins >= 3', 'UNCOMMON', 0, NULL, 'fallback', 'default-template-on-a-roll-6', 'default-template-on-a-roll-6', 6, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Lucky Seven', 'Winning streak accolade for the season.', 'Win 7 matches in a row within a single season', 'consecutive_wins >= 7', 'RARE', 0, NULL, 'fallback', 'default-template-lucky-seven-7', 'default-template-lucky-seven-7', 7, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Decimator', 'Winning streak accolade for the season.', 'Win 10 matches in a row within a single season', 'consecutive_wins >= 10', 'EPIC', 0, NULL, 'fallback', 'default-template-decimator-8', 'default-template-decimator-8', 8, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Bumpy Road', 'Losing streak challenge for the season.', 'Lose 4 matches in a row within a single season', 'consecutive_losses >= 4', 'COMMON', 0, NULL, 'fallback', 'default-template-bumpy-road-9', 'default-template-bumpy-road-9', 9, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Bounce Back', 'Losing streak challenge for the season.', 'After losing 5 in a row, win the next match', 'consecutive_losses_before_win >= 5', 'RARE', 0, NULL, 'fallback', 'default-template-bounce-back-10', 'default-template-bounce-back-10', 10, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Stop the Bleed', 'Losing streak challenge for the season.', 'Break a 3+ match losing streak with a win', 'losing_streak_broken >= 3', 'COMMON', 0, NULL, 'fallback', 'default-template-stop-the-bleed-11', 'default-template-stop-the-bleed-11', 11, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Elite 3', 'Division prestige for the season.', 'End the season Top 3 overall', 'final_rank > 0 && final_rank <= 3', 'EPIC', 0, NULL, 'fallback', 'default-template-elite-3-12', 'default-template-elite-3-12', 12, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Familiar Foe', 'Rivalry highlight for the season.', 'Play the same opponent 5 times in a season', 'repeat_opponent_matches >= 5', 'UNCOMMON', 0, NULL, 'fallback', 'default-template-familiar-foe-13', 'default-template-familiar-foe-13', 13, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Settled the Score', 'Rivalry highlight for the season.', 'Defeat the same opponent 3 times in a row in a season', 'repeat_opponent_wins_streak >= 3', 'RARE', 0, NULL, 'fallback', 'default-template-settled-the-score-14', 'default-template-settled-the-score-14', 14, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Social Butterfly', 'Partner showcase for the season.', 'Win with 8 different partners in a season', 'unique_partners_won_with >= 8', 'RARE', 0, NULL, 'fallback', 'default-template-social-butterfly-15', 'default-template-social-butterfly-15', 15, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Backstabber', 'Partner showcase for the season.', 'Beat your last partner in the very next match', 'beat_last_partner_next_match >= 1', 'RARE', 0, NULL, 'fallback', 'default-template-backstabber-16', 'default-template-backstabber-16', 16, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Ride or Die', 'Partner showcase for the season.', 'Play 30+ matches with the same partner in a season', 'matches_with_primary_partner >= 30', 'EPIC', 0, NULL, 'fallback', 'default-template-ride-or-die-17', 'default-template-ride-or-die-17', 17, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Frequent Host', 'Guest play recognition for the season.', 'Play 10+ matches in a season with at least one guest partner', 'guest_partner_matches >= 10', 'UNCOMMON', 0, NULL, 'fallback', 'default-template-frequent-host-18', 'default-template-frequent-host-18', 18, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Back-to-Back Buddies', 'Partner showcase for the season.', 'Win two matches in the same day with the same partner', 'same_day_partner_back_to_back_wins >= 2', 'COMMON', 0, NULL, 'fallback', 'default-template-back-to-back-buddies-19', 'default-template-back-to-back-buddies-19', 19, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Competitive', 'Score-based feat for the season.', 'Win by exactly 2 points', 'wins_by_margin_2 >= 1', 'UNCOMMON', 0, NULL, 'fallback', 'default-template-competitive-20', 'default-template-competitive-20', 20, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Thursday Thunder', 'Time-based feat for the season.', 'Play a match on a Thursday', 'matches_played_on_thursday >= 1', 'COMMON', 0, NULL, 'fallback', 'default-template-thursday-thunder-21', 'default-template-thursday-thunder-21', 21, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Scribe', 'Cosign achievement for the season.', 'Cosign 10 matches in the season', 'matches_cosigned >= 10', 'COMMON', 0, NULL, 'fallback', 'default-template-scribe-22', 'default-template-scribe-22', 22, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Season Champion', 'Seasonal limited for the season.', 'Finish #1 overall in the season', 'final_rank == 1', 'LEGENDARY', 1, 1, 'fallback', 'default-template-season-champion-23', 'default-template-season-champion-23', 23, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Runner-Up', 'Seasonal limited for the season.', 'Finish #2 overall in the season', 'final_rank == 2', 'EPIC', 1, 1, 'fallback', 'default-template-runner-up-24', 'default-template-runner-up-24', 24, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Pickler', 'Score-based feat for the season.', 'Opponents finish with exactly 0 points', 'shutout_wins >= 1', 'RARE', 0, NULL, 'fallback', 'default-template-pickler-25', 'default-template-pickler-25', 25, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Switch Hitters', 'Partner showcase for the season.', 'Alternate partners in 4 consecutive wins', 'alternating_partner_win_streak >= 4', 'RARE', 0, NULL, 'fallback', 'default-template-switch-hitters-26', 'default-template-switch-hitters-26', 26, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Division Dominator', 'Division prestige for the season.', 'End the season #1 in your division', 'final_band_rank == 1', 'RARE', 0, NULL, 'fallback', 'default-template-division-dominator-27', 'default-template-division-dominator-27', 27, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'St. Patrick''s Day', 'Holiday badge for the season.', 'Play a match on St. Patrick''s Day (March 17)', 'matches_played_on_03_17 >= 1', 'RARE', 0, NULL, 'fallback', 'default-template-st-patrick-s-day-28', 'default-template-st-patrick-s-day-28', 28, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Court Sovereign', 'User-specific standings crown for the season.', 'Finish #1 among the players you faced this season (min 6-player group)', 'final_played_group_rank == 1 && final_played_group_size >= 6', 'LEGENDARY', 0, NULL, 'fallback', 'default-template-court-sovereign-29', 'default-template-court-sovereign-29', 29, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Podium Finish', 'User-specific standings finish for the season.', 'Finish #2 or #3 among the players you faced this season (min 6-player group)', 'final_played_group_rank >= 2 && final_played_group_rank <= 3 && final_played_group_size >= 6', 'EPIC', 0, NULL, 'fallback', 'default-template-podium-finish-30', 'default-template-podium-finish-30', 30, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Last but not Least', 'Season finale oddity for the season.', 'Finish last overall in the season', 'is_final_overall_last == 1', 'LEGENDARY', 0, NULL, 'fallback', 'default-template-last-but-not-least-31', 'default-template-last-but-not-least-31', 31, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'You got Pickled', 'User-specific standings oddity for the season.', 'Finish last among the players you faced this season (min 6-player group)', 'final_played_group_size >= 6 && is_final_played_group_last == 1', 'RARE', 0, NULL, 'fallback', 'default-template-you-got-pickled-32', 'default-template-you-got-pickled-32', 32, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Upper Division Finish', 'Band finish accolade for the season.', 'Finish the season in Upper Division', 'final_band_index == 1', 'UNCOMMON', 0, NULL, 'fallback', 'default-template-upper-division-finish-33', 'default-template-upper-division-finish-33', 33, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Lower Division Finish', 'Band finish accolade for the season.', 'Finish the season in Lower Division', 'final_band_index == 2', 'COMMON', 0, NULL, 'fallback', 'default-template-lower-division-finish-34', 'default-template-lower-division-finish-34', 34, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Premier Division Finish', 'Band finish accolade for the season.', 'Finish the season in Premier Division', 'final_band_index == 1', 'RARE', 0, NULL, 'fallback', 'default-template-premier-division-finish-35', 'default-template-premier-division-finish-35', 35, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Select Division Finish', 'Band finish accolade for the season.', 'Finish the season in Select Division', 'final_band_index == 2', 'UNCOMMON', 0, NULL, 'fallback', 'default-template-select-division-finish-36', 'default-template-select-division-finish-36', 36, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Club Division Finish', 'Band finish accolade for the season.', 'Finish the season in Club Division', 'final_band_index == 3', 'COMMON', 0, NULL, 'fallback', 'default-template-club-division-finish-37', 'default-template-club-division-finish-37', 37, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Champion Division Finish', 'Band finish accolade for the season.', 'Finish the season in Champion Division', 'final_band_index == 1', 'EPIC', 0, NULL, 'fallback', 'default-template-champion-division-finish-38', 'default-template-champion-division-finish-38', 38, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Master Division Finish', 'Band finish accolade for the season.', 'Finish the season in Master Division', 'final_band_index == 2', 'RARE', 0, NULL, 'fallback', 'default-template-master-division-finish-39', 'default-template-master-division-finish-39', 39, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Contender Division Finish', 'Band finish accolade for the season.', 'Finish the season in Contender Division', 'final_band_index == 3', 'UNCOMMON', 0, NULL, 'fallback', 'default-template-contender-division-finish-40', 'default-template-contender-division-finish-40', 40, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Challenger Division Finish', 'Band finish accolade for the season.', 'Finish the season in Challenger Division', 'final_band_index == 4', 'COMMON', 0, NULL, 'fallback', 'default-template-challenger-division-finish-41', 'default-template-challenger-division-finish-41', 41, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Diamond Division Finish', 'Band finish accolade for the season.', 'Finish the season in Diamond Division', 'final_band_index == 1', 'EPIC', 0, NULL, 'fallback', 'default-template-diamond-division-finish-42', 'default-template-diamond-division-finish-42', 42, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Platinum Division Finish', 'Band finish accolade for the season.', 'Finish the season in Platinum Division', 'final_band_index == 2', 'RARE', 0, NULL, 'fallback', 'default-template-platinum-division-finish-43', 'default-template-platinum-division-finish-43', 43, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Gold Division Finish', 'Band finish accolade for the season.', 'Finish the season in Gold Division', 'final_band_index == 3', 'UNCOMMON', 0, NULL, 'fallback', 'default-template-gold-division-finish-44', 'default-template-gold-division-finish-44', 44, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Silver Division Finish', 'Band finish accolade for the season.', 'Finish the season in Silver Division', 'final_band_index == 4', 'UNCOMMON', 0, NULL, 'fallback', 'default-template-silver-division-finish-45', 'default-template-silver-division-finish-45', 45, 1, @fallback_art_id, NULL, NOW(6), NOW(6)),
(@season_id, 'Bronze Division Finish', 'Band finish accolade for the season.', 'Finish the season in Bronze Division', 'final_band_index == 5', 'COMMON', 0, NULL, 'fallback', 'default-template-bronze-division-finish-46', 'default-template-bronze-division-finish-46', 46, 1, @fallback_art_id, NULL, NOW(6), NOW(6));

SELECT COUNT(*) AS total_catalog_entries,
       SUM(CASE WHEN is_default_template = 1 THEN 1 ELSE 0 END) AS default_templates
FROM trophy_catalog
WHERE season_id <=> @season_id;
