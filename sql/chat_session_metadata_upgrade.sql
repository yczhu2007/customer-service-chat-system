-- 客服聊天系统：会话元数据升级脚本
-- 目标数据库：MySQL 8.x
-- 用途：为已存在的 chat_session 表补充会话元数据字段，并新增会话标签表。

SET NAMES utf8mb4;

ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS title VARCHAR(100) NOT NULL DEFAULT '新咨询' COMMENT '会话标题' AFTER end_time,
    ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '优先级：LOW、NORMAL、HIGH、URGENT' AFTER title,
    ADD COLUMN IF NOT EXISTS category VARCHAR(32) NULL COMMENT '会话分类：ACCOUNT、PAYMENT、TECHNICAL、AFTER_SALES、OTHER' AFTER priority,
    ADD COLUMN IF NOT EXISTS metadata_updated_at DATETIME(6) NULL COMMENT '会话元数据更新时间' AFTER category;

UPDATE chat_session
SET title = '新咨询'
WHERE title IS NULL OR title = '';

UPDATE chat_session
SET priority = 'NORMAL'
WHERE priority IS NULL OR priority = '';

CREATE TABLE IF NOT EXISTS chat_session_tag
(
    session_id   VARCHAR(64) NOT NULL COMMENT '会话ID',
    tag          VARCHAR(32) NOT NULL COMMENT '会话标签',
    create_time  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '创建时间',

    PRIMARY KEY (session_id, tag),
    KEY idx_chat_session_tag_tag (tag),

    CONSTRAINT fk_chat_session_tag_session
        FOREIGN KEY (session_id)
            REFERENCES chat_session (id)
            ON UPDATE CASCADE
            ON DELETE CASCADE
)
    ENGINE = InnoDB
    DEFAULT CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '会话标签表';
