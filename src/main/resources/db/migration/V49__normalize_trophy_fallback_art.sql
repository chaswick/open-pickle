SET @fallback_image_url = '/images/trophy_fallback/fallback.png';
SET @legacy_st_patricks_image_url = '/images/trophy_fallback/StPatricksDay_2026_512_c.png';
SET @fallback_storage_key = SHA2(CONCAT('', '|', @fallback_image_url), 256);

INSERT INTO trophy_art (
    storage_key,
    image_url,
    image_bytes,
    created_at,
    updated_at
)
SELECT
    @fallback_storage_key,
    @fallback_image_url,
    NULL,
    NOW(6),
    NOW(6)
WHERE NOT EXISTS (
    SELECT 1
    FROM trophy_art
    WHERE storage_key = @fallback_storage_key
);

SET @fallback_art_id = (
    SELECT id
    FROM trophy_art
    WHERE storage_key = @fallback_storage_key
    ORDER BY id
    LIMIT 1
);

UPDATE trophy t
JOIN trophy_art ta ON ta.id = t.art_id
SET t.art_id = @fallback_art_id
WHERE ta.image_url = @legacy_st_patricks_image_url
  AND @fallback_art_id IS NOT NULL;

UPDATE trophy t
JOIN trophy_art ta ON ta.id = t.badge_art_id
SET t.badge_art_id = @fallback_art_id
WHERE ta.image_url = @legacy_st_patricks_image_url
  AND @fallback_art_id IS NOT NULL;

UPDATE trophy_catalog tc
JOIN trophy_art ta ON ta.id = tc.art_id
SET tc.art_id = @fallback_art_id
WHERE ta.image_url = @legacy_st_patricks_image_url
  AND @fallback_art_id IS NOT NULL;

UPDATE trophy_catalog tc
JOIN trophy_art ta ON ta.id = tc.badge_art_id
SET tc.badge_art_id = @fallback_art_id
WHERE ta.image_url = @legacy_st_patricks_image_url
  AND @fallback_art_id IS NOT NULL;

DELETE ta
FROM trophy_art ta
WHERE ta.image_url = @legacy_st_patricks_image_url
  AND ta.id <> @fallback_art_id;
