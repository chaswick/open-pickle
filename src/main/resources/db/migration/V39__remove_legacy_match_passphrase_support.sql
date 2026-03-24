DROP TABLE IF EXISTS user_passphrase;

SET @drop_user_passphrase = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'users'
              AND COLUMN_NAME = 'passphrase'
        ),
        'ALTER TABLE users DROP COLUMN passphrase',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_user_passphrase;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_user_passphrase_valid_until = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'users'
              AND COLUMN_NAME = 'passphrase_valid_until'
        ),
        'ALTER TABLE users DROP COLUMN passphrase_valid_until',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_user_passphrase_valid_until;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
