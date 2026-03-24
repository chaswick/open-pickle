CREATE TABLE IF NOT EXISTS competition_suspicious_match_flag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    season_id BIGINT NOT NULL,
    match_id BIGINT NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    severity INT NOT NULL,
    summary VARCHAR(255) NOT NULL,
    details TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uc_comp_suspicious_match_reason UNIQUE (match_id, reason_code)
);

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'competition_suspicious_match_flag'
          AND INDEX_NAME = 'idx_comp_suspicious_season_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_comp_suspicious_season_created ON competition_suspicious_match_flag (season_id, created_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'competition_suspicious_match_flag'
          AND INDEX_NAME = 'idx_comp_suspicious_match'
    ),
    'SELECT 1',
    'CREATE INDEX idx_comp_suspicious_match ON competition_suspicious_match_flag (match_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
