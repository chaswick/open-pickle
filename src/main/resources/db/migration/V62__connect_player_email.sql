ALTER TABLE users ADD COLUMN connect_email_pending VARCHAR(45) NULL;
ALTER TABLE users ADD COLUMN connect_email_token_hash VARCHAR(64) NULL;
ALTER TABLE users ADD COLUMN connect_email_expires_at DATETIME(6) NULL;
CREATE UNIQUE INDEX uk_users_connect_email_token ON users (connect_email_token_hash);
