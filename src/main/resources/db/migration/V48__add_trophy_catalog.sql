CREATE TABLE trophy_catalog (
    badge_selectable_by_all BIT NOT NULL DEFAULT b'0',
    display_order INT,
    is_default_template BIT NOT NULL DEFAULT b'0',
    is_limited BIT NOT NULL DEFAULT b'0',
    is_repeatable BIT NOT NULL DEFAULT b'0',
    max_claims INT,
    story_mode_tracker BIT NOT NULL DEFAULT b'0',
    art_id BIGINT,
    badge_art_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    season_id BIGINT,
    updated_at DATETIME(6) NOT NULL,
    ai_provider VARCHAR(64),
    story_mode_key VARCHAR(64),
    slug VARCHAR(80) NOT NULL,
    title VARCHAR(96) NOT NULL,
    generation_seed VARCHAR(128),
    summary VARCHAR(140) NOT NULL,
    unlock_condition VARCHAR(512),
    unlock_expression VARCHAR(512),
    prompt TEXT,
    evaluation_scope ENUM ('GROUP','USER') NOT NULL DEFAULT 'USER',
    rarity ENUM ('COMMON','EPIC','LEGENDARY','RARE','UNCOMMON') NOT NULL DEFAULT 'COMMON',
    PRIMARY KEY (id),
    CONSTRAINT uk_trophy_catalog_slug UNIQUE (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE trophy_catalog
    ADD CONSTRAINT fk_trophy_catalog_season FOREIGN KEY (season_id)
        REFERENCES ladder_season (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_trophy_catalog_art FOREIGN KEY (art_id)
        REFERENCES trophy_art (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_trophy_catalog_badge_art FOREIGN KEY (badge_art_id)
        REFERENCES trophy_art (id) ON DELETE SET NULL;

CREATE INDEX idx_trophy_catalog_season ON trophy_catalog (season_id);
CREATE INDEX idx_trophy_catalog_default_template ON trophy_catalog (is_default_template, season_id, display_order, id);

ALTER TABLE trophy
    ADD COLUMN catalog_entry_id BIGINT NULL AFTER season_id;

INSERT INTO trophy_catalog (
    season_id,
    title,
    summary,
    unlock_condition,
    rarity,
    slug,
    art_id,
    badge_art_id,
    ai_provider,
    generation_seed,
    prompt,
    unlock_expression,
    is_limited,
    is_repeatable,
    evaluation_scope,
    is_default_template,
    story_mode_tracker,
    story_mode_key,
    max_claims,
    display_order,
    badge_selectable_by_all,
    created_at,
    updated_at
)
SELECT
    CASE
        WHEN ls.name = 'Default Trophy Templates' THEN NULL
        ELSE t.season_id
    END,
    t.title,
    t.summary,
    t.unlock_condition,
    t.rarity,
    t.slug,
    t.art_id,
    t.badge_art_id,
    t.ai_provider,
    t.generation_seed,
    t.prompt,
    t.unlock_expression,
    t.is_limited,
    t.is_repeatable,
    t.evaluation_scope,
    t.is_default_template,
    t.story_mode_tracker,
    t.story_mode_key,
    t.max_claims,
    t.display_order,
    t.badge_selectable_by_all,
    t.generated_at,
    t.updated_at
FROM trophy t
LEFT JOIN ladder_season ls ON ls.id = t.season_id
WHERE t.is_default_template = 1;

UPDATE trophy t
JOIN trophy_catalog c
  ON c.is_default_template = 1
 AND c.generation_seed IS NOT NULL
 AND c.generation_seed = t.generation_seed
SET t.catalog_entry_id = c.id
WHERE t.catalog_entry_id IS NULL
  AND t.season_id IS NOT NULL
  AND t.is_default_template = 0
  AND t.generation_seed IS NOT NULL;

INSERT INTO trophy_catalog (
    season_id,
    title,
    summary,
    unlock_condition,
    rarity,
    slug,
    art_id,
    badge_art_id,
    ai_provider,
    generation_seed,
    prompt,
    unlock_expression,
    is_limited,
    is_repeatable,
    evaluation_scope,
    is_default_template,
    story_mode_tracker,
    story_mode_key,
    max_claims,
    display_order,
    badge_selectable_by_all,
    created_at,
    updated_at
)
SELECT
    t.season_id,
    t.title,
    t.summary,
    t.unlock_condition,
    t.rarity,
    t.slug,
    t.art_id,
    t.badge_art_id,
    t.ai_provider,
    t.generation_seed,
    t.prompt,
    t.unlock_expression,
    t.is_limited,
    t.is_repeatable,
    t.evaluation_scope,
    b'0',
    t.story_mode_tracker,
    t.story_mode_key,
    t.max_claims,
    t.display_order,
    t.badge_selectable_by_all,
    t.generated_at,
    t.updated_at
FROM trophy t
WHERE t.season_id IS NOT NULL
  AND t.is_default_template = 0
  AND t.catalog_entry_id IS NULL;

UPDATE trophy t
JOIN trophy_catalog c ON c.slug = t.slug
SET t.catalog_entry_id = c.id
WHERE t.catalog_entry_id IS NULL
  AND t.season_id IS NOT NULL;

ALTER TABLE trophy
    ADD CONSTRAINT fk_trophy_catalog_entry FOREIGN KEY (catalog_entry_id)
        REFERENCES trophy_catalog (id) ON DELETE SET NULL;

CREATE INDEX idx_trophy_catalog_entry_id ON trophy (catalog_entry_id);

DELETE t
FROM trophy t
LEFT JOIN user_trophy ut ON ut.trophy_id = t.id
LEFT JOIN group_trophy gt ON gt.trophy_id = t.id
LEFT JOIN users u1 ON u1.badge_slot_1_trophy_id = t.id
LEFT JOIN users u2 ON u2.badge_slot_2_trophy_id = t.id
LEFT JOIN users u3 ON u3.badge_slot_3_trophy_id = t.id
WHERE t.is_default_template = 1
  AND ut.id IS NULL
  AND gt.id IS NULL
  AND u1.id IS NULL
  AND u2.id IS NULL
  AND u3.id IS NULL;
