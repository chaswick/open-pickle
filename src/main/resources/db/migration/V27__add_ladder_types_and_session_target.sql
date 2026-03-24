SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ladder_config'
          AND COLUMN_NAME = 'type'
    ),
    'SELECT 1',
    'ALTER TABLE ladder_config ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT ''STANDARD'' AFTER status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ladder_config'
          AND COLUMN_NAME = 'target_season_id'
    ),
    'SELECT 1',
    'ALTER TABLE ladder_config ADD COLUMN target_season_id BIGINT NULL AFTER pending_deletion_by_user_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ladder_config'
          AND COLUMN_NAME = 'expires_at'
    ),
    'SELECT 1',
    'ALTER TABLE ladder_config ADD COLUMN expires_at DATETIME(6) NULL AFTER target_season_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ladder_config'
          AND INDEX_NAME = 'idx_ladder_config_type_status'
    ),
    'SELECT 1',
    'CREATE INDEX idx_ladder_config_type_status ON ladder_config (type, status)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ladder_config'
          AND INDEX_NAME = 'idx_ladder_config_expires_at'
    ),
    'SELECT 1',
    'CREATE INDEX idx_ladder_config_expires_at ON ladder_config (expires_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
