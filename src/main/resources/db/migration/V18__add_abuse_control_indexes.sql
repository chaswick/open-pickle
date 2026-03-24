-- Support abuse-control queries introduced for weekly match quotas and
-- per-user voice interpretation retention pruning.
CREATE INDEX idx_matches_logged_by_created_at
    ON matches (logged_by_id, created_at);

CREATE INDEX idx_interpretation_event_user_created_at
    ON interpretation_event (current_user_id, created_at);
