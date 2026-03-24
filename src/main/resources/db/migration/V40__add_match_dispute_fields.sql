SET @add_disputed_by_id = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'matches'
              AND COLUMN_NAME = 'disputed_by_id'
        ),
        'SELECT 1',
        'ALTER TABLE matches ADD COLUMN disputed_by_id BIGINT NULL AFTER edited_at'
    )
);
PREPARE stmt FROM @add_disputed_by_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_disputed_at = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'matches'
              AND COLUMN_NAME = 'disputed_at'
        ),
        'SELECT 1',
        'ALTER TABLE matches ADD COLUMN disputed_at DATETIME(6) NULL AFTER disputed_by_id'
    )
);
PREPARE stmt FROM @add_disputed_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_dispute_note = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'matches'
              AND COLUMN_NAME = 'dispute_note'
        ),
        'SELECT 1',
        'ALTER TABLE matches ADD COLUMN dispute_note TEXT NULL AFTER disputed_at'
    )
);
PREPARE stmt FROM @add_dispute_note;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fk_matches_disputed_by = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'matches'
              AND CONSTRAINT_NAME = 'fk_matches_disputed_by'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT 1',
        'ALTER TABLE matches ADD CONSTRAINT fk_matches_disputed_by FOREIGN KEY (disputed_by_id) REFERENCES users(id)'
    )
);
PREPARE stmt FROM @add_fk_matches_disputed_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_idx_matches_state_disputed_at = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'matches'
              AND INDEX_NAME = 'idx_matches_state_disputed_at'
        ),
        'SELECT 1',
        'CREATE INDEX idx_matches_state_disputed_at ON matches (state, disputed_at)'
    )
);
PREPARE stmt FROM @add_idx_matches_state_disputed_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_idx_matches_disputed_by = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'matches'
              AND COLUMN_NAME = 'disputed_by_id'
              AND SEQ_IN_INDEX = 1
        ),
        'SELECT 1',
        'CREATE INDEX idx_matches_disputed_by ON matches (disputed_by_id)'
    )
);
PREPARE stmt FROM @add_idx_matches_disputed_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
