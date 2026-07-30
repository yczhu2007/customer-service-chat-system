-- 已存在数据库升级脚本：为用户表增加VIP等级。
-- 新建数据库直接执行user_ddl.sql即可，不需要再执行本文件。

SET @vip_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'vip_level'
);

SET @add_vip_column_sql = IF(
        @vip_column_exists = 0,
        'ALTER TABLE sys_user ADD COLUMN vip_level TINYINT NOT NULL DEFAULT 0 COMMENT ''VIP等级：0为普通用户，1到5为VIP'' AFTER status',
        'SELECT ''sys_user.vip_level already exists'''
    );

PREPARE add_vip_column_statement FROM @add_vip_column_sql;
EXECUTE add_vip_column_statement;
DEALLOCATE PREPARE add_vip_column_statement;

SET @vip_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND CONSTRAINT_NAME = 'chk_sys_user_vip_level'
);

SET @add_vip_check_sql = IF(
        @vip_check_exists = 0,
        'ALTER TABLE sys_user ADD CONSTRAINT chk_sys_user_vip_level CHECK (vip_level BETWEEN 0 AND 5)',
        'SELECT ''chk_sys_user_vip_level already exists'''
    );

PREPARE add_vip_check_statement FROM @add_vip_check_sql;
EXECUTE add_vip_check_statement;
DEALLOCATE PREPARE add_vip_check_statement;
