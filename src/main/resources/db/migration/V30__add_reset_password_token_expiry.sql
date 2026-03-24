ALTER TABLE users
    ADD COLUMN reset_password_token_expires_at DATETIME(6) NULL;
