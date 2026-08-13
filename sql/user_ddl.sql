-- 客服聊天系统：用户表
-- 目标数据库：MySQL 8.x
-- 执行前请先选择 application.yml 中配置的 springboot 数据库。
--
-- 说明：
-- 1. 聊天模块使用 U001、A001 这类字符串用户 ID，因此主键使用 VARCHAR(64)。
-- 2. password 保存 PasswordUtil 生成的 PBKDF2 字符串，不保存明文密码。
-- 3. 当前登录与用户管理统一使用 sys_user，不再依赖旧版 t_user。

SET NAMES utf8mb4;

-- 本脚本用于全新安装。CREATE TABLE IF NOT EXISTS 不会升级已经存在的表结构。

CREATE TABLE IF NOT EXISTS sys_user
(
    id          VARCHAR(64)  NOT NULL COMMENT '用户ID，例如U001、A001',
    username    VARCHAR(64)  NOT NULL COMMENT '登录用户名',
    password    VARCHAR(255) NOT NULL COMMENT 'PBKDF2密码哈希',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED'
        COMMENT '用户状态：ENABLED、DISABLED',
    vip_level   TINYINT      NOT NULL DEFAULT 0
        COMMENT 'VIP等级：0为普通用户，1到5为VIP',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '最后修改时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_status (status),
    KEY idx_sys_user_create_time (create_time),

    CONSTRAINT chk_sys_user_status
        CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT chk_sys_user_vip_level
        CHECK (vip_level BETWEEN 0 AND 5)
)
    ENGINE = InnoDB
    DEFAULT CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '系统用户表';
