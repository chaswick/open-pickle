-- Display names are labels. User IDs, public member codes and email remain unique.
SET @nickname_indexes = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', REPLACE(index_name, '`', '``'), '`') SEPARATOR ', ')
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'users'
          AND non_unique = 0 AND index_name <> 'PRIMARY'
        GROUP BY index_name
        HAVING COUNT(*) = 1 AND MAX(column_name) = 'nick_name'
    ) nickname_unique_indexes
);
SET @nickname_ddl = IF(@nickname_indexes IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE users ', @nickname_indexes));
PREPARE nickname_statement FROM @nickname_ddl;
EXECUTE nickname_statement;
DEALLOCATE PREPARE nickname_statement;

SET @nickname_ddl = IF(EXISTS(
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'users'
      AND index_name = 'idx_users_nick_name'
), 'SELECT 1', 'CREATE INDEX idx_users_nick_name ON users (nick_name)');
PREPARE nickname_statement FROM @nickname_ddl;
EXECUTE nickname_statement;
DEALLOCATE PREPARE nickname_statement;
