SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'competition_safe_display_name'
    ),
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN competition_safe_display_name VARCHAR(32) NULL AFTER time_zone'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'competition_safe_display_name_active'
    ),
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN competition_safe_display_name_active BIT NOT NULL DEFAULT b''0'' AFTER competition_safe_display_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'competition_safe_display_name_basis'
    ),
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN competition_safe_display_name_basis VARCHAR(64) NULL AFTER competition_safe_display_name_active'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS competition_display_name_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uc_comp_name_report_reporter_target UNIQUE (reporter_user_id, target_user_id)
);

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'competition_display_name_report'
          AND INDEX_NAME = 'idx_comp_name_report_target'
    ),
    'SELECT 1',
    'CREATE INDEX idx_comp_name_report_target ON competition_display_name_report (target_user_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'competition_display_name_report'
          AND INDEX_NAME = 'idx_comp_name_report_reporter'
    ),
    'SELECT 1',
    'CREATE INDEX idx_comp_name_report_reporter ON competition_display_name_report (reporter_user_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
