CREATE TABLE trophy_art (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    storage_key CHAR(64) NOT NULL,
    image_url VARCHAR(512),
    image_bytes LONGBLOB,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_trophy_art_storage_key UNIQUE (storage_key)
) ENGINE=InnoDB;

ALTER TABLE trophy
    ADD COLUMN art_id BIGINT NULL AFTER slug;

INSERT INTO trophy_art (storage_key, image_url, image_bytes, created_at, updated_at)
SELECT DISTINCT
    SHA2(CONCAT(COALESCE(SHA2(image_bytes, 256), ''), '|', COALESCE(image_url, '')), 256) AS storage_key,
    image_url,
    image_bytes,
    NOW(6),
    NOW(6)
FROM trophy
WHERE (image_bytes IS NOT NULL AND OCTET_LENGTH(image_bytes) > 0)
   OR (image_url IS NOT NULL AND image_url <> '');

UPDATE trophy t
JOIN trophy_art a
  ON a.storage_key = SHA2(CONCAT(COALESCE(SHA2(t.image_bytes, 256), ''), '|', COALESCE(t.image_url, '')), 256)
SET t.art_id = a.id
WHERE (t.image_bytes IS NOT NULL AND OCTET_LENGTH(t.image_bytes) > 0)
   OR (t.image_url IS NOT NULL AND t.image_url <> '');

ALTER TABLE trophy
    ADD CONSTRAINT fk_trophy_art FOREIGN KEY (art_id)
        REFERENCES trophy_art (id) ON DELETE SET NULL;

CREATE INDEX idx_trophy_art_id ON trophy (art_id);

ALTER TABLE trophy
    DROP COLUMN image_url,
    DROP COLUMN image_bytes;
