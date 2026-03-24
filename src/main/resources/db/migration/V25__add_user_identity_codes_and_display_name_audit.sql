ALTER TABLE users
    ADD COLUMN public_code VARCHAR(17) NULL AFTER nick_name,
    ADD COLUMN last_display_name_change_at DATETIME(6) NULL AFTER last_match_logged_at;

ALTER TABLE users
    ADD CONSTRAINT uk_users_public_code UNIQUE (public_code);

CREATE TABLE user_display_name_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ladder_config_id BIGINT NULL,
    changed_by_user_id BIGINT NOT NULL,
    old_display_name VARCHAR(24) NOT NULL,
    new_display_name VARCHAR(24) NOT NULL,
    changed_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

CREATE INDEX idx_user_display_name_audit_ladder_changed_at
    ON user_display_name_audit (ladder_config_id, changed_at);

CREATE INDEX idx_user_display_name_audit_user_changed_at
    ON user_display_name_audit (user_id, changed_at);
