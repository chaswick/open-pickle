SET @has_season_column := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'band_positions'
      AND COLUMN_NAME = 'season_id'
);

SET @add_season_column_sql := IF(
    @has_season_column = 0,
    'ALTER TABLE band_positions ADD COLUMN season_id BIGINT NULL',
    'SELECT 1'
);
PREPARE add_season_column_stmt FROM @add_season_column_sql;
EXECUTE add_season_column_stmt;
DEALLOCATE PREPARE add_season_column_stmt;

SET @has_idx_band_positions_user := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'band_positions'
      AND index_name = 'idx_band_positions_user'
);

SET @add_idx_user_sql := IF(
    @has_idx_band_positions_user = 0,
    'CREATE INDEX idx_band_positions_user ON band_positions(user_id)',
    'SELECT 1'
);
PREPARE add_idx_user_stmt FROM @add_idx_user_sql;
EXECUTE add_idx_user_stmt;
DEALLOCATE PREPARE add_idx_user_stmt;

SET @band_positions_user_fk := (
    SELECT DISTINCT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'band_positions'
      AND COLUMN_NAME = 'user_id'
      AND REFERENCED_TABLE_NAME = 'users'
    LIMIT 1
);

SET @drop_user_fk_sql := IF(
    @band_positions_user_fk IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE band_positions DROP FOREIGN KEY `', @band_positions_user_fk, '`')
);
PREPARE drop_user_fk_stmt FROM @drop_user_fk_sql;
EXECUTE drop_user_fk_stmt;
DEALLOCATE PREPARE drop_user_fk_stmt;

SET @band_positions_user_unique := (
    SELECT ux.index_name
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'band_positions'
          AND non_unique = 0
          AND index_name <> 'PRIMARY'
        GROUP BY index_name
        HAVING COUNT(*) = 1
           AND SUM(CASE WHEN column_name = 'user_id' THEN 1 ELSE 0 END) = 1
    ) ux
    LIMIT 1
);

SET @drop_user_unique_sql := IF(
    @band_positions_user_unique IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE band_positions DROP INDEX `', @band_positions_user_unique, '`')
);
PREPARE drop_user_unique_stmt FROM @drop_user_unique_sql;
EXECUTE drop_user_unique_stmt;
DEALLOCATE PREPARE drop_user_unique_stmt;

DELETE FROM band_positions;

INSERT INTO band_positions (season_id, user_id, band_index, position_in_band, daily_momentum)
SELECT
    ls_by_user.season_id,
    ls_by_user.user_id,
    LEAST(cfg.band_count, FLOOR((ls_by_user.rank_no - 1) / cfg.band_size) + 1) AS band_index,
    MOD(ls_by_user.rank_no - 1, cfg.band_size) + 1 AS position_in_band,
    0 AS daily_momentum
FROM (
    SELECT season_id, user_id, MIN(rank_no) AS rank_no
    FROM ladder_standing
    WHERE user_id IS NOT NULL
    GROUP BY season_id, user_id
) ls_by_user
JOIN (
    SELECT
        sized.season_id,
        sized.season_size,
        CASE
            WHEN sized.season_size <= 4 THEN 1
            WHEN sized.season_size <= 8 THEN 2
            WHEN sized.season_size <= 12 THEN 3
            WHEN sized.season_size <= 18 THEN 4
            ELSE 5
        END AS band_count,
        CEIL(
            sized.season_size / CASE
                WHEN sized.season_size <= 4 THEN 1
                WHEN sized.season_size <= 8 THEN 2
                WHEN sized.season_size <= 12 THEN 3
                WHEN sized.season_size <= 18 THEN 4
                ELSE 5
            END
        ) AS band_size
    FROM (
        SELECT season_id, COUNT(DISTINCT user_id) AS season_size
        FROM ladder_standing
        WHERE user_id IS NOT NULL
        GROUP BY season_id
    ) sized
) cfg ON cfg.season_id = ls_by_user.season_id;

SET @has_user_fk := (
    SELECT COUNT(*)
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'band_positions'
      AND COLUMN_NAME = 'user_id'
      AND REFERENCED_TABLE_NAME = 'users'
);

SET @add_user_fk_sql := IF(
    @has_user_fk = 0,
    'ALTER TABLE band_positions ADD CONSTRAINT fk_band_positions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE add_user_fk_stmt FROM @add_user_fk_sql;
EXECUTE add_user_fk_stmt;
DEALLOCATE PREPARE add_user_fk_stmt;

SET @season_null_count := (
    SELECT COUNT(*) FROM band_positions WHERE season_id IS NULL
);

SET @season_nullable := (
    SELECT CASE WHEN IS_NULLABLE = 'YES' THEN 1 ELSE 0 END
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'band_positions'
      AND COLUMN_NAME = 'season_id'
    LIMIT 1
);

SET @set_not_null_sql := IF(
    @season_null_count = 0 AND @season_nullable = 1,
    'ALTER TABLE band_positions MODIFY COLUMN season_id BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE set_not_null_stmt FROM @set_not_null_sql;
EXECUTE set_not_null_stmt;
DEALLOCATE PREPARE set_not_null_stmt;

SET @has_season_user_unique := (
    SELECT COUNT(*)
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'band_positions'
          AND non_unique = 0
          AND index_name <> 'PRIMARY'
        GROUP BY index_name
        HAVING COUNT(*) = 2
           AND SUM(CASE WHEN column_name = 'season_id' THEN 1 ELSE 0 END) = 1
           AND SUM(CASE WHEN column_name = 'user_id' THEN 1 ELSE 0 END) = 1
    ) x
);

SET @add_season_user_unique_sql := IF(
    @has_season_user_unique = 0,
    'ALTER TABLE band_positions ADD CONSTRAINT uk_band_positions_season_user UNIQUE (season_id, user_id)',
    'SELECT 1'
);
PREPARE add_season_user_unique_stmt FROM @add_season_user_unique_sql;
EXECUTE add_season_user_unique_stmt;
DEALLOCATE PREPARE add_season_user_unique_stmt;

SET @has_idx_user_after := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'band_positions'
      AND index_name = 'idx_band_positions_user'
);

SET @add_idx_user_after_sql := IF(
    @has_idx_user_after = 0,
    'CREATE INDEX idx_band_positions_user ON band_positions(user_id)',
    'SELECT 1'
);
PREPARE add_idx_user_after_stmt FROM @add_idx_user_after_sql;
EXECUTE add_idx_user_after_stmt;
DEALLOCATE PREPARE add_idx_user_after_stmt;

SET @has_season_fk := (
    SELECT COUNT(*)
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'band_positions'
      AND COLUMN_NAME = 'season_id'
      AND REFERENCED_TABLE_NAME = 'ladder_season'
);

SET @add_season_fk_sql := IF(
    @has_season_fk = 0,
    'ALTER TABLE band_positions ADD CONSTRAINT fk_band_positions_season FOREIGN KEY (season_id) REFERENCES ladder_season (id) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE add_season_fk_stmt FROM @add_season_fk_sql;
EXECUTE add_season_fk_stmt;
DEALLOCATE PREPARE add_season_fk_stmt;
