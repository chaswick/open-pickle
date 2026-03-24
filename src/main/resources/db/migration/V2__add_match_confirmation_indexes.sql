-- Optimize match history lookups (created_at window + season filter)
CREATE INDEX idx_matches_created_at_season
    ON matches (created_at, season_id);

-- Speed up per-logger scans when gathering pending confirmations
CREATE INDEX idx_matches_logged_by_season
    ON matches (logged_by_id, season_id);

-- Reduce rescans when loading pending confirmations for a player
CREATE INDEX idx_match_confirmation_player_match
    ON match_confirmation (player_id, match_id);
