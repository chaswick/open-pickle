ALTER TABLE ladder_config
    ADD COLUMN carry_over_previous_rating TINYINT(1) NOT NULL DEFAULT 0;
