CREATE TABLE IF NOT EXISTS match_nullification_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    match_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    team VARCHAR(1) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_match_nullification_request_match
        FOREIGN KEY (match_id) REFERENCES matches(id),
    CONSTRAINT fk_match_nullification_request_player
        FOREIGN KEY (player_id) REFERENCES users(id),
    CONSTRAINT uk_match_nullification_request_match_team
        UNIQUE (match_id, team)
);

CREATE INDEX idx_match_nullification_request_match_expires
    ON match_nullification_request (match_id, expires_at);

CREATE INDEX idx_match_nullification_request_player
    ON match_nullification_request (player_id);
