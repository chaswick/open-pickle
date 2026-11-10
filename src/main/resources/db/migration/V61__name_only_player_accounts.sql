ALTER TABLE users ADD COLUMN name_only_account BIT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN recovery_code_hash VARCHAR(64) NULL;
CREATE UNIQUE INDEX uk_users_recovery_code_hash ON users (recovery_code_hash);
