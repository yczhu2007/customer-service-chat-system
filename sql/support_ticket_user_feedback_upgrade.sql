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
