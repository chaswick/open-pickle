-- Read-only artwork export. Compatible with production before round-robin trophies.
-- Each result row is one JSON object; save the full export_json column as text.
-- Includes referenced image bytes/URLs and trophy metadata, without account data.
--
-- MySQL CLI example (replace YOUR_DB_USER and YOUR_DATABASE):
-- mysql -u YOUR_DB_USER -p --batch --raw --skip-column-names --quick \
--   --default-character-set=utf8mb4 --max-allowed-packet=256M YOUR_DATABASE \
--   < scripts/db/export/export_trophy_art.sql > trophy-art-export.jsonl

SELECT JSON_OBJECT(
    'kind', 'art',
    'id', a.id,
    'image_url', a.image_url,
    'image_base64', REPLACE(TO_BASE64(a.image_bytes), CHAR(10), '')
) AS export_json
FROM trophy_art a
WHERE a.id IN (
    SELECT art_id FROM trophy
    UNION SELECT badge_art_id FROM trophy
    UNION SELECT art_id FROM trophy_catalog
    UNION SELECT badge_art_id FROM trophy_catalog
)
UNION ALL
SELECT JSON_OBJECT(
    'kind', 'catalog',
    'id', c.id,
    'title', c.title,
    'rarity', c.rarity,
    'season_id', c.season_id,
    'art_id', c.art_id,
    'badge_art_id', c.badge_art_id
)
FROM trophy_catalog c
UNION ALL
SELECT JSON_OBJECT(
    'kind', 'trophy',
    'id', t.id,
    'title', t.title,
    'rarity', t.rarity,
    'season_id', t.season_id,
    'art_id', t.art_id,
    'badge_art_id', t.badge_art_id
)
FROM trophy t;
