-- Restore artwork references captured when this pack was first applied.

-- Rows edited to different artwork afterward are left alone.

-- Existing and generated art rows are retained; no awards or definitions are deleted.

START TRANSACTION;

UPDATE trophy_catalog t JOIN trophy_art_pack_backup b ON b.entity_id=t.id AND b.entity_type='catalog' AND b.pack_version='openpickle-art-v1' SET t.art_id=b.old_art_id,t.badge_art_id=b.old_badge_art_id,t.updated_at=NOW(6) WHERE t.art_id <=> b.new_art_id AND t.badge_art_id <=> b.new_badge_art_id;

UPDATE trophy t JOIN trophy_art_pack_backup b ON b.entity_id=t.id AND b.entity_type='trophy' AND b.pack_version='openpickle-art-v1' SET t.art_id=b.old_art_id,t.badge_art_id=b.old_badge_art_id,t.updated_at=NOW(6) WHERE t.art_id <=> b.new_art_id AND t.badge_art_id <=> b.new_badge_art_id;

COMMIT;
