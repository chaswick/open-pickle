ALTER TABLE ladder_config
    ADD COLUMN session_scheduled_at DATETIME(6) NULL,
    ADD COLUMN session_time_zone VARCHAR(64) NULL,
    ADD COLUMN session_started_at DATETIME(6) NULL;
