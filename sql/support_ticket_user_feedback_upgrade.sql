-- 已部署数据库的工单用户确认升级脚本（MySQL 8.x）
SET NAMES utf8mb4;

SET @user_confirmed_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'support_ticket'
      AND COLUMN_NAME = 'user_confirmed_at'
);
SET @user_confirmed_at_sql = IF(
    @user_confirmed_at_exists = 0,
    'ALTER TABLE support_ticket ADD COLUMN user_confirmed_at DATETIME(6) NULL COMMENT ''用户确认解决时间'' AFTER resolved_at',
    'SELECT 1'
);
PREPARE user_confirmed_at_statement FROM @user_confirmed_at_sql;
EXECUTE user_confirmed_at_statement;
DEALLOCATE PREPARE user_confirmed_at_statement;

SET @action_type_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'support_ticket_status_history'
      AND COLUMN_NAME = 'action_type'
);
SET @action_type_sql = IF(
    @action_type_exists = 0,
    'ALTER TABLE support_ticket_status_history ADD COLUMN action_type VARCHAR(24) NOT NULL DEFAULT ''STATUS_CHANGED'' COMMENT ''操作类型：CREATED、STATUS_CHANGED、USER_CONFIRMED、REOPENED'' AFTER operator_id',
    'SELECT 1'
);
PREPARE action_type_statement FROM @action_type_sql;
EXECUTE action_type_statement;
DEALLOCATE PREPARE action_type_statement;
