-- Cleans up the disposable performance-load users and the ladder data they
-- generated.
--
-- This removes:
-- - the 20 seeded users with the configured email prefix
-- - the seeded ladders
-- - matches, confirmations, credits, meetups, aliases, trophies, standings,
--   and related rows tied to those users/ladders

SET @seed_prefix := 'perfload';
SET @seed_domain := 'demo.local';
SET @seed_code := 'PERFLOAD';

DROP TEMPORARY TABLE IF EXISTS tmp_perf_users;
DROP TEMPORARY TABLE IF EXISTS tmp_perf_ladders;
DROP TEMPORARY TABLE IF EXISTS tmp_perf_seasons;
DROP TEMPORARY TABLE IF EXISTS tmp_perf_matches;
DROP TEMPORARY TABLE IF EXISTS tmp_perf_slots;
DROP TEMPORARY TABLE IF EXISTS tmp_perf_trophies;

CREATE TEMPORARY TABLE tmp_perf_users (
    id BIGINT PRIMARY KEY
);

INSERT INTO tmp_perf_users (id)
SELECT id
FROM users
WHERE email LIKE CONCAT(@seed_prefix, '%@', @seed_domain);

CREATE TEMPORARY TABLE tmp_perf_ladders (
    id BIGINT PRIMARY KEY
);

INSERT INTO tmp_perf_ladders (id)
SELECT DISTINCT lc.id
FROM ladder_config lc
LEFT JOIN tmp_perf_users tu
    ON tu.id = lc.owner_user_id
WHERE lc.invite_code LIKE CONCAT(@seed_code, '-%')
   OR tu.id IS NOT NULL;

CREATE TEMPORARY TABLE tmp_perf_seasons (
    id BIGINT PRIMARY KEY
);

INSERT INTO tmp_perf_seasons (id)
SELECT ls.id
FROM ladder_season ls
JOIN tmp_perf_ladders tl
    ON tl.id = ls.ladder_config_id;

CREATE TEMPORARY TABLE tmp_perf_matches (
    id BIGINT PRIMARY KEY
);

INSERT INTO tmp_perf_matches (id)
SELECT DISTINCT m.id
FROM matches m
LEFT JOIN tmp_perf_seasons ts
    ON ts.id = m.season_id
LEFT JOIN tmp_perf_users tu_a1
    ON tu_a1.id = m.a1_id
LEFT JOIN tmp_perf_users tu_a2
    ON tu_a2.id = m.a2_id
LEFT JOIN tmp_perf_users tu_b1
    ON tu_b1.id = m.b1_id
LEFT JOIN tmp_perf_users tu_b2
    ON tu_b2.id = m.b2_id
LEFT JOIN tmp_perf_users tu_cosigned
    ON tu_cosigned.id = m.cosigned_by_id
LEFT JOIN tmp_perf_users tu_logged
    ON tu_logged.id = m.logged_by_id
LEFT JOIN tmp_perf_users tu_edited
    ON tu_edited.id = m.edited_by_id
WHERE ts.id IS NOT NULL
   OR tu_a1.id IS NOT NULL
   OR tu_a2.id IS NOT NULL
   OR tu_b1.id IS NOT NULL
   OR tu_b2.id IS NOT NULL
   OR tu_cosigned.id IS NOT NULL
   OR tu_logged.id IS NOT NULL
   OR tu_edited.id IS NOT NULL;

CREATE TEMPORARY TABLE tmp_perf_slots (
    id BIGINT PRIMARY KEY
);

INSERT INTO tmp_perf_slots (id)
SELECT DISTINCT s.id
FROM ladder_meetup_slot s
LEFT JOIN tmp_perf_ladders tl
    ON tl.id = s.ladder_config_id
LEFT JOIN tmp_perf_users tu
    ON tu.id = s.created_by_user_id
WHERE tl.id IS NOT NULL
   OR tu.id IS NOT NULL;

CREATE TEMPORARY TABLE tmp_perf_trophies (
    id BIGINT PRIMARY KEY
);

INSERT INTO tmp_perf_trophies (id)
SELECT t.id
FROM trophy t
JOIN tmp_perf_seasons ts
    ON ts.id = t.season_id;

START TRANSACTION;

DELETE FROM interpretation_event
WHERE ladder_config_id IN (SELECT id FROM tmp_perf_ladders)
   OR season_id IN (SELECT id FROM tmp_perf_seasons)
   OR match_id IN (SELECT id FROM tmp_perf_matches)
   OR current_user_id IN (SELECT id FROM tmp_perf_users);

DELETE FROM name_corrections
WHERE ladder_config_id IN (SELECT id FROM tmp_perf_ladders)
   OR user_id IN (SELECT id FROM tmp_perf_users)
   OR reporter_user_id IN (SELECT id FROM tmp_perf_users);

DELETE FROM ladder_meetup_rsvp
WHERE slot_id IN (SELECT id FROM tmp_perf_slots)
   OR user_id IN (SELECT id FROM tmp_perf_users);

DELETE FROM ladder_meetup_slot
WHERE id IN (SELECT id FROM tmp_perf_slots);

DELETE FROM user_push_subscriptions
WHERE user_id IN (SELECT id FROM tmp_perf_users);

DELETE FROM match_confirmation
WHERE match_id IN (SELECT id FROM tmp_perf_matches)
   OR player_id IN (SELECT id FROM tmp_perf_users);

DELETE FROM user_credit
WHERE user_id IN (SELECT id FROM tmp_perf_users)
   OR match_id IN (SELECT id FROM tmp_perf_matches);

DELETE FROM user_trophy
WHERE user_id IN (SELECT id FROM tmp_perf_users)
   OR trophy_id IN (SELECT id FROM tmp_perf_trophies);

DELETE FROM trophy
WHERE id IN (SELECT id FROM tmp_perf_trophies);

DELETE FROM ladder_match_link
WHERE season_id IN (SELECT id FROM tmp_perf_seasons)
   OR match_id IN (SELECT id FROM tmp_perf_matches);

DELETE FROM matches
WHERE id IN (SELECT id FROM tmp_perf_matches);

DELETE FROM band_positions
WHERE season_id IN (SELECT id FROM tmp_perf_seasons)
   OR user_id IN (SELECT id FROM tmp_perf_users);

DELETE FROM ladder_standing
WHERE season_id IN (SELECT id FROM tmp_perf_seasons)
   OR user_id IN (SELECT id FROM tmp_perf_users);

DELETE FROM user_court_name
WHERE user_id IN (SELECT id FROM tmp_perf_users)
   OR ladder_config_id IN (SELECT id FROM tmp_perf_ladders);

DELETE FROM ladder_membership
WHERE ladder_config_id IN (SELECT id FROM tmp_perf_ladders)
   OR user_id IN (SELECT id FROM tmp_perf_users);

UPDATE ladder_season
SET started_by_user_id = NULL
WHERE started_by_user_id IN (SELECT id FROM tmp_perf_users)
  AND id NOT IN (SELECT id FROM tmp_perf_seasons);

UPDATE ladder_season
SET ended_by_user_id = NULL
WHERE ended_by_user_id IN (SELECT id FROM tmp_perf_users)
  AND id NOT IN (SELECT id FROM tmp_perf_seasons);

UPDATE ladder_config
SET pending_deletion_by_user_id = NULL
WHERE pending_deletion_by_user_id IN (SELECT id FROM tmp_perf_users)
  AND id NOT IN (SELECT id FROM tmp_perf_ladders);

DELETE FROM ladder_season
WHERE id IN (SELECT id FROM tmp_perf_seasons);

DELETE FROM ladder_config
WHERE id IN (SELECT id FROM tmp_perf_ladders);

DELETE FROM users
WHERE id IN (SELECT id FROM tmp_perf_users);

COMMIT;

SELECT COUNT(*) AS perf_users_remaining
FROM users
WHERE email LIKE CONCAT(@seed_prefix, '%@', @seed_domain);

SELECT COUNT(*) AS perf_ladders_remaining
FROM ladder_config
WHERE invite_code LIKE CONCAT(@seed_code, '-%');
