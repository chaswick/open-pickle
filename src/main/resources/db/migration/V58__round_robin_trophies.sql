ALTER TABLE trophy_catalog ADD COLUMN is_round_robin_trophy BIT NOT NULL DEFAULT 0;
ALTER TABLE trophy ADD COLUMN is_round_robin_trophy BIT NOT NULL DEFAULT 0;
ALTER TABLE round_robin
    ADD COLUMN trophies_processed BIT NOT NULL DEFAULT 0,
    ADD COLUMN trophies_processed_at DATETIME(6) NULL;
CREATE INDEX idx_rr_trophy_processing ON round_robin (trophies_processed, id);
CREATE INDEX idx_rr_entry_match_id ON round_robin_entry (match_id);

CREATE TABLE round_robin_trophy_award (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    round_robin_id BIGINT NOT NULL,
    trophy_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    awarded_at DATETIME(6) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    CONSTRAINT uk_rr_trophy_award UNIQUE (round_robin_id, trophy_id, user_id),
    CONSTRAINT fk_rr_trophy_run FOREIGN KEY (round_robin_id) REFERENCES round_robin (id) ON DELETE CASCADE,
    CONSTRAINT fk_rr_trophy_trophy FOREIGN KEY (trophy_id) REFERENCES trophy (id) ON DELETE CASCADE,
    CONSTRAINT fk_rr_trophy_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_rr_trophy_owners ON round_robin_trophy_award (trophy_id, user_id);
