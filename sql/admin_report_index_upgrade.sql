-- 已部署数据库的管理员报表索引升级脚本（MySQL 8.x）
SET NAMES utf8mb4;

SET @ticket_created_status_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'support_ticket'
      AND INDEX_NAME = 'idx_support_ticket_created_status'
);
SET @ticket_created_status_index_sql = IF(
    @ticket_created_status_index_exists = 0,
    'ALTER TABLE support_ticket ADD KEY idx_support_ticket_created_status (created_at, status)',
    'SELECT 1'
);
PREPARE ticket_created_status_index_statement FROM @ticket_created_status_index_sql;
EXECUTE ticket_created_status_index_statement;
DEALLOCATE PREPARE ticket_created_status_index_statement;

SET @rating_create_time_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chat_session_rating'
      AND INDEX_NAME = 'idx_chat_session_rating_create_time'
);
SET @rating_create_time_index_sql = IF(
    @rating_create_time_index_exists = 0,
    'ALTER TABLE chat_session_rating ADD KEY idx_chat_session_rating_create_time (create_time)',
    'SELECT 1'
);
PREPARE rating_create_time_index_statement FROM @rating_create_time_index_sql;
EXECUTE rating_create_time_index_statement;
DEALLOCATE PREPARE rating_create_time_index_statement;
