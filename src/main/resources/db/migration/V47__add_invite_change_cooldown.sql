SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ladder_config'
          AND COLUMN_NAME = 'last_invite_change_at'
    ),
    'SELECT 1',
    'ALTER TABLE ladder_config ADD COLUMN last_invite_change_at DATETIME(6) NULL AFTER invite_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
