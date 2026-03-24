-- Quick verification for trophy import
-- Replace :season_id with the actual season id (or hardcode a value).

-- Count trophies for the season
SELECT COUNT(*) AS trophy_count
FROM trophy
WHERE season_id = :season_id;

-- Spot-check recent trophies (title + slug + image presence)
SELECT
    t.id,
    t.title,
    t.slug,
    t.rarity,
    t.status,
    CASE
        WHEN a.image_bytes IS NOT NULL AND OCTET_LENGTH(a.image_bytes) > 0 THEN 'bytes'
        WHEN a.image_url IS NOT NULL AND a.image_url <> '' THEN 'url'
        ELSE 'none'
    END AS image_source
FROM trophy t
LEFT JOIN trophy_art a ON a.id = t.art_id
WHERE t.season_id = :season_id
ORDER BY t.display_order ASC
LIMIT 25;
