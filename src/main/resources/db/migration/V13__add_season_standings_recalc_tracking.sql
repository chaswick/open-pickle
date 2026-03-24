ALTER TABLE ladder_season
    ADD COLUMN standings_recalc_in_flight INT NOT NULL DEFAULT 0;

ALTER TABLE ladder_season
    ADD COLUMN standings_recalc_last_started_at TIMESTAMP NULL;

ALTER TABLE ladder_season
    ADD COLUMN standings_recalc_last_finished_at TIMESTAMP NULL;
