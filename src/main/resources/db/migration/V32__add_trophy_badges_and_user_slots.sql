ALTER TABLE trophy
    ADD COLUMN badge_art_id BIGINT NULL AFTER art_id;

UPDATE trophy
SET badge_art_id = art_id
WHERE badge_art_id IS NULL
  AND art_id IS NOT NULL;

ALTER TABLE trophy
    ADD CONSTRAINT fk_trophy_badge_art FOREIGN KEY (badge_art_id)
        REFERENCES trophy_art (id) ON DELETE SET NULL;

CREATE INDEX idx_trophy_badge_art_id ON trophy (badge_art_id);

ALTER TABLE users
    ADD COLUMN badge_slot_1_trophy_id BIGINT NULL AFTER competition_safe_display_name_basis,
    ADD COLUMN badge_slot_2_trophy_id BIGINT NULL AFTER badge_slot_1_trophy_id,
    ADD COLUMN badge_slot_3_trophy_id BIGINT NULL AFTER badge_slot_2_trophy_id;

ALTER TABLE users
    ADD CONSTRAINT fk_users_badge_slot_1_trophy FOREIGN KEY (badge_slot_1_trophy_id)
        REFERENCES trophy (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_users_badge_slot_2_trophy FOREIGN KEY (badge_slot_2_trophy_id)
        REFERENCES trophy (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_users_badge_slot_3_trophy FOREIGN KEY (badge_slot_3_trophy_id)
        REFERENCES trophy (id) ON DELETE SET NULL;

CREATE INDEX idx_users_badge_slot_1_trophy ON users (badge_slot_1_trophy_id);
CREATE INDEX idx_users_badge_slot_2_trophy ON users (badge_slot_2_trophy_id);
CREATE INDEX idx_users_badge_slot_3_trophy ON users (badge_slot_3_trophy_id);
