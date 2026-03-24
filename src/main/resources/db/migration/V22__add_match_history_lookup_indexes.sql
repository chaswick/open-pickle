-- Support recent-match lookups used by scoring algorithms that inspect player history.
CREATE INDEX idx_matches_a1_created_at
    ON matches (a1_id, created_at);

CREATE INDEX idx_matches_a2_created_at
    ON matches (a2_id, created_at);

CREATE INDEX idx_matches_b1_created_at
    ON matches (b1_id, created_at);

CREATE INDEX idx_matches_b2_created_at
    ON matches (b2_id, created_at);
