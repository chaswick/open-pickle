ALTER TABLE trophy
    MODIFY COLUMN season_id BIGINT NULL;

ALTER TABLE trophy
    ADD COLUMN badge_selectable_by_all TINYINT(1) NOT NULL DEFAULT 0 AFTER display_order;

CREATE INDEX idx_trophy_badge_selectable_by_all ON trophy (badge_selectable_by_all, display_order, id);
