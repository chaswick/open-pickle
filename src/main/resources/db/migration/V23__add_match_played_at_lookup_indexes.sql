-- Support match chronology queries that now use played_at instead of created_at.
CREATE INDEX idx_matches_season_played
    ON matches (season_id, played_at);

CREATE INDEX idx_matches_a1_played_at
    ON matches (a1_id, played_at);

CREATE INDEX idx_matches_a2_played_at
    ON matches (a2_id, played_at);

CREATE INDEX idx_matches_b1_played_at
    ON matches (b1_id, played_at);

CREATE INDEX idx_matches_b2_played_at
    ON matches (b2_id, played_at);
