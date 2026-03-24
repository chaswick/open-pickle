ALTER TABLE round_robin
    ADD COLUMN session_config_id BIGINT;

ALTER TABLE round_robin
    ADD CONSTRAINT fk_round_robin_session_config
        FOREIGN KEY (session_config_id) REFERENCES ladder_config(id);

CREATE INDEX idx_round_robin_session_config_id
    ON round_robin(session_config_id);
