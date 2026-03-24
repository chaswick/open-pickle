ALTER TABLE matches
    ADD COLUMN source_session_config_id BIGINT NULL;

ALTER TABLE matches
    ADD CONSTRAINT fk_matches_source_session_config
        FOREIGN KEY (source_session_config_id) REFERENCES ladder_config (id);

CREATE INDEX idx_matches_source_session_config_played_at
    ON matches (source_session_config_id, played_at);
