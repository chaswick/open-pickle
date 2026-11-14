ALTER TABLE ladder_config
    ADD COLUMN session_member_limit INT NOT NULL DEFAULT 32;

CREATE INDEX idx_session_waitlist_order
    ON session_join_request (ladder_config_id, status, resolved_at, id);
