SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ladder_config'
          AND COLUMN_NAME = 'story_mode_default_enabled'
    ),
    'SELECT 1',
    'ALTER TABLE ladder_config ADD COLUMN story_mode_default_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER carry_over_previous_rating'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ladder_season'
          AND COLUMN_NAME = 'story_mode_enabled'
    ),
    'SELECT 1',
    'ALTER TABLE ladder_season ADD COLUMN story_mode_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER ended_by_user_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'trophy'
          AND COLUMN_NAME = 'story_mode_tracker'
    ),
    'SELECT 1',
    'ALTER TABLE trophy ADD COLUMN story_mode_tracker TINYINT(1) NOT NULL DEFAULT 0 AFTER is_default_template'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'trophy'
          AND COLUMN_NAME = 'story_mode_key'
    ),
    'SELECT 1',
    'ALTER TABLE trophy ADD COLUMN story_mode_key VARCHAR(64) NULL AFTER story_mode_tracker'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'trophy'
          AND COLUMN_NAME = 'is_repeatable'
    ),
    'SELECT 1',
    'ALTER TABLE trophy ADD COLUMN is_repeatable TINYINT(1) NOT NULL DEFAULT 0 AFTER is_limited'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'trophy'
          AND COLUMN_NAME = 'evaluation_scope'
    ),
    'SELECT 1',
    'ALTER TABLE trophy ADD COLUMN evaluation_scope VARCHAR(24) NOT NULL DEFAULT ''USER'' AFTER is_repeatable'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'trophy'
          AND INDEX_NAME = 'idx_trophy_story_mode_tracker'
    ),
    'SELECT 1',
    'CREATE INDEX idx_trophy_story_mode_tracker ON trophy (season_id, story_mode_tracker, story_mode_key)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'user_trophy'
          AND COLUMN_NAME = 'award_count'
    ),
    'SELECT 1',
    'ALTER TABLE user_trophy ADD COLUMN award_count INT NOT NULL DEFAULT 1 AFTER trophy_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'user_trophy'
          AND COLUMN_NAME = 'first_awarded_at'
    ),
    'SELECT 1',
    'ALTER TABLE user_trophy ADD COLUMN first_awarded_at DATETIME(6) NULL AFTER award_count'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'user_trophy'
          AND COLUMN_NAME = 'last_awarded_at'
    ),
    'SELECT 1',
    'ALTER TABLE user_trophy ADD COLUMN last_awarded_at DATETIME(6) NULL AFTER awarded_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = '';

UPDATE user_trophy
SET first_awarded_at = COALESCE(first_awarded_at, awarded_at),
    last_awarded_at = COALESCE(last_awarded_at, awarded_at),
    award_count = CASE WHEN award_count < 1 THEN 1 ELSE award_count END;

UPDATE user_trophy
SET first_awarded_at = NULL
WHERE first_awarded_at = '0000-00-00 00:00:00';

UPDATE user_trophy
SET last_awarded_at = NULL
WHERE last_awarded_at = '0000-00-00 00:00:00';

UPDATE user_trophy
SET awarded_at = COALESCE(last_awarded_at, first_awarded_at, UTC_TIMESTAMP(6))
WHERE awarded_at = '0000-00-00 00:00:00';

SET SESSION sql_mode = @previous_sql_mode;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'user_trophy'
          AND INDEX_NAME = 'idx_user_trophy_trophy'
    ),
    'SELECT 1',
    'CREATE INDEX idx_user_trophy_trophy ON user_trophy (trophy_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS group_trophy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    season_id BIGINT NOT NULL,
    trophy_id BIGINT NOT NULL,
    award_count INT NOT NULL DEFAULT 1,
    first_awarded_at DATETIME(6) NOT NULL,
    awarded_at DATETIME(6) NOT NULL,
    last_awarded_at DATETIME(6) NOT NULL,
    awarded_reason VARCHAR(512) NULL,
    award_match_ids TEXT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_trophy_season FOREIGN KEY (season_id) REFERENCES ladder_season (id),
    CONSTRAINT fk_group_trophy_trophy FOREIGN KEY (trophy_id) REFERENCES trophy (id),
    CONSTRAINT uk_group_trophy_pair UNIQUE (season_id, trophy_id)
) ENGINE=InnoDB;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'group_trophy'
          AND INDEX_NAME = 'idx_group_trophy_trophy'
    ),
    'SELECT 1',
    'CREATE INDEX idx_group_trophy_trophy ON group_trophy (trophy_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
