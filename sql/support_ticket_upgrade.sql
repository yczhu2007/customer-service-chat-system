-- 客服聊天系统：会话关联轻量工单升级脚本
-- 目标数据库：MySQL 8.x
-- 用途：为已存在的 chat_session 表新增一会话一工单记录。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS support_ticket
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '工单主键',
    session_id  VARCHAR(64)  NOT NULL COMMENT '关联会话ID',
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT '工单状态：OPEN、IN_PROGRESS、WAITING_USER、RESOLVED',
    description VARCHAR(1000) NOT NULL COMMENT '问题描述',
    resolution  VARCHAR(1000) NULL COMMENT '处理结果',
    version     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    resolved_at DATETIME(6)  NULL COMMENT '解决时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_support_ticket_session (session_id),
    KEY idx_support_ticket_status_updated (status, updated_at),

    CONSTRAINT fk_support_ticket_session
        FOREIGN KEY (session_id) REFERENCES chat_session (id)
            ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_support_ticket_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_USER', 'RESOLVED'))
)
    ENGINE = InnoDB
    DEFAULT CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '会话关联轻量工单表';
