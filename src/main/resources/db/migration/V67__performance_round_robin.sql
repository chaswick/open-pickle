ALTER TABLE round_robin MODIFY COLUMN format VARCHAR(32) NOT NULL DEFAULT 'ROTATING_PARTNERS';
ALTER TABLE round_robin ADD COLUMN prepared_round INT NOT NULL DEFAULT 1;
CREATE TABLE round_robin_seed (
    round_robin_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INT NOT NULL,
    PRIMARY KEY (round_robin_id, user_id),
    CONSTRAINT fk_round_robin_seed_run FOREIGN KEY (round_robin_id) REFERENCES round_robin(id)
);
