-- Seeds 20 disposable performance-load accounts plus two ladder setups.
--
-- Shared login password for all 20 users:
--   LoadTest!2026#Openpickle
--
-- Notes:
-- - This script targets the current MySQL dev schema used by the app.
-- - It is idempotent for the same seed prefix.
-- - Terms acceptance is represented by users.acknowledged_terms_at.

SET @seed_prefix := 'perfload';
SET @seed_domain := 'demo.local';
SET @seed_code := 'PERFLOAD';
SET @shared_password_hash := '$2a$10$xMx9t0qkgd6OXvimzLqEXuZiWYr5fwq79k1Dfp26OmZ.hyYBssoS6';
SET @registered_at := UTC_TIMESTAMP(6) - INTERVAL 14 DAY;
SET @terms_at := UTC_TIMESTAMP(6) - INTERVAL 13 DAY;
SET @joined_at_base := UTC_TIMESTAMP(6) - INTERVAL 10 DAY;
SET @left_at_base := UTC_TIMESTAMP(6) - INTERVAL 2 DAY;
SET @time_zone_name := 'America/New_York';
SET @max_owned_ladders := 3;

DROP TEMPORARY TABLE IF EXISTS tmp_perf_users;
CREATE TEMPORARY TABLE tmp_perf_users (
    seq INT PRIMARY KEY,
    email VARCHAR(45) NOT NULL,
    nick_name VARCHAR(24) NOT NULL,
    global_alias VARCHAR(64) NOT NULL,
    alpha_alias VARCHAR(64) NOT NULL,
    beta_alias VARCHAR(64) NOT NULL,
    alpha_role VARCHAR(12) NOT NULL,
    beta_role VARCHAR(12) NOT NULL,
    beta_state VARCHAR(12) NOT NULL
);

INSERT INTO tmp_perf_users (
    seq,
    email,
    nick_name,
    global_alias,
    alpha_alias,
    beta_alias,
    alpha_role,
    beta_role,
    beta_state
) VALUES
    (1, CONCAT(@seed_prefix, '01@', @seed_domain), 'PerfUser01', 'Load 01', 'Alpha01', 'Beta01', 'ADMIN',  'ADMIN',  'ACTIVE'),
    (2, CONCAT(@seed_prefix, '02@', @seed_domain), 'PerfUser02', 'Load 02', 'Alpha02', 'Beta02', 'ADMIN',  'ADMIN',  'ACTIVE'),
    (3, CONCAT(@seed_prefix, '03@', @seed_domain), 'PerfUser03', 'Load 03', 'Alpha03', 'Beta03', 'MEMBER', 'MEMBER', 'ACTIVE'),
    (4, CONCAT(@seed_prefix, '04@', @seed_domain), 'PerfUser04', 'Load 04', 'Alpha04', 'Beta04', 'MEMBER', 'MEMBER', 'ACTIVE'),
    (5, CONCAT(@seed_prefix, '05@', @seed_domain), 'PerfUser05', 'Load 05', 'Alpha05', 'Beta05', 'MEMBER', 'MEMBER', 'ACTIVE'),
    (6, CONCAT(@seed_prefix, '06@', @seed_domain), 'PerfUser06', 'Load 06', 'Alpha06', 'Beta06', 'MEMBER', 'MEMBER', 'ACTIVE'),
    (7, CONCAT(@seed_prefix, '07@', @seed_domain), 'PerfUser07', 'Load 07', 'Alpha07', 'Beta07', 'MEMBER', 'MEMBER', 'ACTIVE'),
    (8, CONCAT(@seed_prefix, '08@', @seed_domain), 'PerfUser08', 'Load 08', 'Alpha08', 'Beta08', 'MEMBER', 'MEMBER', 'ACTIVE'),
    (9, CONCAT(@seed_prefix, '09@', @seed_domain), 'PerfUser09', 'Load 09', 'Alpha09', 'Beta09', 'MEMBER', 'MEMBER', 'ACTIVE'),
    (10, CONCAT(@seed_prefix, '10@', @seed_domain), 'PerfUser10', 'Load 10', 'Alpha10', 'Beta10', 'MEMBER', 'MEMBER', 'ACTIVE'),
    (11, CONCAT(@seed_prefix, '11@', @seed_domain), 'PerfUser11', 'Load 11', 'Alpha11', 'Beta11', 'MEMBER', 'MEMBER', 'LEFT'),
    (12, CONCAT(@seed_prefix, '12@', @seed_domain), 'PerfUser12', 'Load 12', 'Alpha12', 'Beta12', 'MEMBER', 'MEMBER', 'LEFT'),
    (13, CONCAT(@seed_prefix, '13@', @seed_domain), 'PerfUser13', 'Load 13', 'Alpha13', 'Beta13', 'MEMBER', 'MEMBER', 'LEFT'),
    (14, CONCAT(@seed_prefix, '14@', @seed_domain), 'PerfUser14', 'Load 14', 'Alpha14', 'Beta14', 'MEMBER', 'MEMBER', 'LEFT'),
    (15, CONCAT(@seed_prefix, '15@', @seed_domain), 'PerfUser15', 'Load 15', 'Alpha15', 'Beta15', 'MEMBER', 'MEMBER', 'LEFT'),
    (16, CONCAT(@seed_prefix, '16@', @seed_domain), 'PerfUser16', 'Load 16', 'Alpha16', 'Beta16', 'MEMBER', 'MEMBER', 'BANNED'),
    (17, CONCAT(@seed_prefix, '17@', @seed_domain), 'PerfUser17', 'Load 17', 'Alpha17', 'Beta17', 'MEMBER', 'MEMBER', 'BANNED'),
    (18, CONCAT(@seed_prefix, '18@', @seed_domain), 'PerfUser18', 'Load 18', 'Alpha18', 'Beta18', 'MEMBER', 'MEMBER', 'BANNED'),
    (19, CONCAT(@seed_prefix, '19@', @seed_domain), 'PerfUser19', 'Load 19', 'Alpha19', 'Beta19', 'MEMBER', 'MEMBER', 'BANNED'),
    (20, CONCAT(@seed_prefix, '20@', @seed_domain), 'PerfUser20', 'Load 20', 'Alpha20', 'Beta20', 'MEMBER', 'MEMBER', 'BANNED');

START TRANSACTION;

INSERT INTO users (
    email,
    nick_name,
    password,
    is_admin,
    max_owned_ladders,
    time_zone,
    registered_at,
    acknowledged_terms_at,
    meetups_email_opt_in,
    meetups_email_pending,
    app_ui_enabled,
    competition_safe_display_name_active
)
SELECT
    t.email,
    t.nick_name,
    @shared_password_hash,
    0,
    @max_owned_ladders,
    @time_zone_name,
    @registered_at,
    @terms_at,
    0,
    0,
    1,
    0
FROM tmp_perf_users t
ON DUPLICATE KEY UPDATE
    nick_name = VALUES(nick_name),
    password = VALUES(password),
    is_admin = VALUES(is_admin),
    max_owned_ladders = VALUES(max_owned_ladders),
    time_zone = VALUES(time_zone),
    acknowledged_terms_at = COALESCE(acknowledged_terms_at, VALUES(acknowledged_terms_at)),
    registered_at = COALESCE(registered_at, VALUES(registered_at)),
    meetups_email_opt_in = VALUES(meetups_email_opt_in),
    meetups_email_pending = VALUES(meetups_email_pending),
    app_ui_enabled = VALUES(app_ui_enabled),
    competition_safe_display_name_active = VALUES(competition_safe_display_name_active);

SET @alpha_owner_id := (
    SELECT id
    FROM users
    WHERE email = CONCAT(@seed_prefix, '01@', @seed_domain)
);
SET @beta_owner_id := (
    SELECT id
    FROM users
    WHERE email = CONCAT(@seed_prefix, '02@', @seed_domain)
);

INSERT INTO ladder_config (
    title,
    owner_user_id,
    invite_code,
    status,
    type,
    created_at,
    updated_at,
    pending_deletion,
    mode,
    rolling_every_count,
    rolling_every_unit,
    max_transitions_per_day,
    last_season_created_at,
    security_level,
    allow_guest_only_personal_matches,
    carry_over_previous_rating,
    story_mode_default_enabled,
    tournament_mode,
    scoring_algorithm,
    version
)
SELECT
    'Performance Load Ladder Alpha',
    @alpha_owner_id,
    CONCAT(@seed_code, '-ALPHA'),
    'ACTIVE',
    'STANDARD',
    UTC_TIMESTAMP(6) - INTERVAL 10 DAY,
    UTC_TIMESTAMP(6) - INTERVAL 1 DAY,
    0,
    'ROLLING',
    6,
    'WEEKS',
    3,
    UTC_TIMESTAMP(6) - INTERVAL 7 DAY,
    'SELF_CONFIRM',
    1,
    0,
    0,
    0,
    'MARGIN_CURVE_V1',
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM ladder_config
    WHERE invite_code = CONCAT(@seed_code, '-ALPHA')
);

INSERT INTO ladder_config (
    title,
    owner_user_id,
    invite_code,
    status,
    type,
    created_at,
    updated_at,
    pending_deletion,
    mode,
    rolling_every_count,
    rolling_every_unit,
    max_transitions_per_day,
    last_season_created_at,
    security_level,
    allow_guest_only_personal_matches,
    carry_over_previous_rating,
    story_mode_default_enabled,
    tournament_mode,
    scoring_algorithm,
    version
)
SELECT
    'Performance Load Ladder Beta',
    @beta_owner_id,
    CONCAT(@seed_code, '-BETA'),
    'ACTIVE',
    'STANDARD',
    UTC_TIMESTAMP(6) - INTERVAL 9 DAY,
    UTC_TIMESTAMP(6) - INTERVAL 1 DAY,
    0,
    'MANUAL',
    4,
    'WEEKS',
    3,
    UTC_TIMESTAMP(6) - INTERVAL 8 DAY,
    'STANDARD',
    0,
    1,
    0,
    0,
    'MARGIN_CURVE_V1',
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM ladder_config
    WHERE invite_code = CONCAT(@seed_code, '-BETA')
);

SET @alpha_ladder_id := (
    SELECT id
    FROM ladder_config
    WHERE invite_code = CONCAT(@seed_code, '-ALPHA')
);
SET @beta_ladder_id := (
    SELECT id
    FROM ladder_config
    WHERE invite_code = CONCAT(@seed_code, '-BETA')
);

INSERT INTO ladder_season (
    ladder_config_id,
    name,
    start_date,
    end_date,
    state,
    started_at,
    started_by_user_id,
    story_mode_enabled,
    standings_recalc_in_flight,
    version
)
SELECT
    @alpha_ladder_id,
    'Performance Load Alpha Season',
    CURRENT_DATE - INTERVAL 7 DAY,
    CURRENT_DATE + INTERVAL 35 DAY,
    'ACTIVE',
    UTC_TIMESTAMP(6) - INTERVAL 7 DAY,
    @alpha_owner_id,
    0,
    0,
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM ladder_season
    WHERE ladder_config_id = @alpha_ladder_id
      AND name = 'Performance Load Alpha Season'
);

INSERT INTO ladder_season (
    ladder_config_id,
    name,
    start_date,
    end_date,
    state,
    started_at,
    started_by_user_id,
    story_mode_enabled,
    standings_recalc_in_flight,
    version
)
SELECT
    @beta_ladder_id,
    'Performance Load Beta Season',
    CURRENT_DATE - INTERVAL 14 DAY,
    CURRENT_DATE + INTERVAL 14 DAY,
    'ACTIVE',
    UTC_TIMESTAMP(6) - INTERVAL 14 DAY,
    @beta_owner_id,
    0,
    0,
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM ladder_season
    WHERE ladder_config_id = @beta_ladder_id
      AND name = 'Performance Load Beta Season'
);

INSERT INTO ladder_membership (
    ladder_config_id,
    user_id,
    role,
    state,
    joined_at,
    left_at
)
SELECT
    @alpha_ladder_id,
    u.id,
    t.alpha_role,
    'ACTIVE',
    TIMESTAMPADD(MINUTE, t.seq, @joined_at_base),
    NULL
FROM tmp_perf_users t
JOIN users u
    ON u.email = t.email
ON DUPLICATE KEY UPDATE
    role = VALUES(role),
    state = VALUES(state),
    joined_at = VALUES(joined_at),
    left_at = VALUES(left_at);

INSERT INTO ladder_membership (
    ladder_config_id,
    user_id,
    role,
    state,
    joined_at,
    left_at
)
SELECT
    @beta_ladder_id,
    u.id,
    t.beta_role,
    t.beta_state,
    TIMESTAMPADD(MINUTE, t.seq, @joined_at_base),
    CASE
        WHEN t.beta_state = 'ACTIVE' THEN NULL
        ELSE TIMESTAMPADD(HOUR, t.seq, @left_at_base)
    END
FROM tmp_perf_users t
JOIN users u
    ON u.email = t.email
ON DUPLICATE KEY UPDATE
    role = VALUES(role),
    state = VALUES(state),
    joined_at = VALUES(joined_at),
    left_at = VALUES(left_at);

INSERT INTO user_court_name (
    user_id,
    ladder_config_id,
    alias
)
SELECT
    u.id,
    NULL,
    t.global_alias
FROM tmp_perf_users t
JOIN users u
    ON u.email = t.email
WHERE NOT EXISTS (
    SELECT 1
    FROM user_court_name x
    WHERE x.user_id = u.id
      AND x.ladder_config_id IS NULL
      AND x.alias = t.global_alias
);

INSERT INTO user_court_name (
    user_id,
    ladder_config_id,
    alias
)
SELECT
    u.id,
    @alpha_ladder_id,
    t.alpha_alias
FROM tmp_perf_users t
JOIN users u
    ON u.email = t.email
WHERE NOT EXISTS (
    SELECT 1
    FROM user_court_name x
    WHERE x.user_id = u.id
      AND x.ladder_config_id = @alpha_ladder_id
      AND x.alias = t.alpha_alias
);

INSERT INTO user_court_name (
    user_id,
    ladder_config_id,
    alias
)
SELECT
    u.id,
    @beta_ladder_id,
    t.beta_alias
FROM tmp_perf_users t
JOIN users u
    ON u.email = t.email
WHERE NOT EXISTS (
    SELECT 1
    FROM user_court_name x
    WHERE x.user_id = u.id
      AND x.ladder_config_id = @beta_ladder_id
      AND x.alias = t.beta_alias
);

COMMIT;

SELECT COUNT(*) AS perf_users_seeded
FROM users
WHERE email LIKE CONCAT(@seed_prefix, '%@', @seed_domain);

SELECT
    lc.title,
    lc.invite_code,
    COUNT(lm.id) AS memberships,
    SUM(CASE WHEN lm.state = 'ACTIVE' THEN 1 ELSE 0 END) AS active_members,
    SUM(CASE WHEN lm.state = 'LEFT' THEN 1 ELSE 0 END) AS left_members,
    SUM(CASE WHEN lm.state = 'BANNED' THEN 1 ELSE 0 END) AS banned_members
FROM ladder_config lc
LEFT JOIN ladder_membership lm
    ON lm.ladder_config_id = lc.id
WHERE lc.invite_code IN (CONCAT(@seed_code, '-ALPHA'), CONCAT(@seed_code, '-BETA'))
GROUP BY lc.id, lc.title, lc.invite_code
ORDER BY lc.title;
