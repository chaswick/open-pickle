ALTER TABLE trophy
    ADD COLUMN is_default_template TINYINT(1) NOT NULL DEFAULT 0 AFTER is_limited;
