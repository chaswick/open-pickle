CREATE TABLE ladder_rating_change (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    season_id BIGINT NOT NULL,
    match_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    rating_before INT NOT NULL,
    rating_delta INT NOT NULL,
    rating_after INT NOT NULL,
    summary VARCHAR(255) NOT NULL,
    details TEXT,
    CONSTRAINT fk_lrc_season FOREIGN KEY (season_id)
        REFERENCES ladder_season (id) ON DELETE CASCADE,
    CONSTRAINT fk_lrc_match FOREIGN KEY (match_id)
        REFERENCES matches (id) ON DELETE CASCADE,
    CONSTRAINT fk_lrc_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_lrc_match_user UNIQUE (match_id, user_id)
);

CREATE INDEX idx_lrc_season_user_occurred
    ON ladder_rating_change (season_id, user_id, occurred_at DESC);
