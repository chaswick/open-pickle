SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ladder_config'
          AND COLUMN_NAME = 'tournament_mode'
    ),
    'SELECT 1',
    'ALTER TABLE ladder_config ADD COLUMN tournament_mode BIT(1) NOT NULL DEFAULT b''0'' AFTER story_mode_default_enabled'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
