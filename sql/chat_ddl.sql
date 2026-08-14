-- 客服聊天系统：聊天会话与聊天消息表
-- 目标数据库：MySQL 8.x
-- 执行顺序：
--   1. user_ddl.sql
--   2. rbac_ddl.sql
--   3. chat_ddl.sql
--
-- 本脚本用于全新安装，只使用 CREATE TABLE IF NOT EXISTS，不会删除已有数据。
-- CREATE TABLE IF NOT EXISTS 不会修改已存在表的字段精度或排序规则；
-- 旧数据库必须先人工核对表结构，再执行对应 ALTER TABLE，不要依赖本脚本自动升级。

SET NAMES utf8mb4;


-- ============================================================
-- 0. 客服技能表
-- ============================================================

CREATE TABLE IF NOT EXISTS chat_agent_skill
(
    id          VARCHAR(64) NOT NULL COMMENT '技能记录ID',
    agent_id    VARCHAR(64) NOT NULL COMMENT '客服用户ID',
    skill_code  VARCHAR(64) NOT NULL COMMENT '技能编码，例如VIP',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '创建时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_agent_skill (agent_id, skill_code),
    KEY idx_chat_agent_skill_code (skill_code, agent_id),

    CONSTRAINT fk_chat_agent_skill_user
        FOREIGN KEY (agent_id)
            REFERENCES sys_user (id)
            ON UPDATE CASCADE
            ON DELETE CASCADE
)
    ENGINE = InnoDB
    DEFAULT CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '客服技能表';


-- ============================================================
-- 1. 聊天会话表
-- ============================================================

CREATE TABLE IF NOT EXISTS chat_session
(
    id          VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id     VARCHAR(64) NOT NULL COMMENT '普通用户ID',
    agent_id    VARCHAR(64) NULL COMMENT '客服ID',
    status      VARCHAR(16) NOT NULL COMMENT '会话状态：ACTIVE、CLOSED',
    create_time DATETIME(6) NOT NULL COMMENT '会话创建时间',
    end_time    DATETIME(6) NULL COMMENT '会话结束时间',

    PRIMARY KEY (id),
    KEY idx_chat_session_user_status (user_id, status),
    KEY idx_chat_session_agent_status (agent_id, status),
    KEY idx_chat_session_create_time (create_time),

    CONSTRAINT chk_chat_session_status
        CHECK (status IN ('ACTIVE', 'CLOSED'))
)
    ENGINE = InnoDB
    DEFAULT CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '客服聊天会话表';


-- ============================================================
-- 2. 聊天消息表
-- ============================================================

CREATE TABLE IF NOT EXISTS chat_message
(
    id            VARCHAR(64) NOT NULL COMMENT '服务端消息ID',
    session_id    VARCHAR(64) NOT NULL COMMENT '所属会话ID',
    sender_id     VARCHAR(64) NOT NULL COMMENT '发送者ID',
    sender_role   VARCHAR(16) NOT NULL COMMENT '发送者角色：USER、AGENT',
    type          VARCHAR(16) NOT NULL COMMENT '消息类型：TEXT、IMAGE、FILE',
    content       TEXT        NOT NULL COMMENT '消息内容',
    client_msg_id VARCHAR(64) NOT NULL COMMENT '客户端幂等消息ID',
    create_time   DATETIME(6) NOT NULL COMMENT '服务端接收时间',
    edited        TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否编辑过',
    edited_at     DATETIME(6) NULL COMMENT '最后编辑时间',
    original_content TEXT     NULL COMMENT '首次编辑前的原始内容，仅供审计',
    recalled      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否已撤回',
    recalled_at   DATETIME(6) NULL COMMENT '撤回时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_message_client (session_id, sender_id, client_msg_id),
    KEY idx_chat_message_session_time (session_id, create_time, id),
    KEY idx_chat_message_sender_time (sender_id, create_time),

    CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id)
            REFERENCES chat_session (id)
            ON UPDATE CASCADE
            ON DELETE CASCADE,

    CONSTRAINT chk_chat_message_sender_role
        CHECK (sender_role IN ('USER', 'AGENT')),

    CONSTRAINT chk_chat_message_type
        CHECK (type IN ('TEXT', 'IMAGE', 'FILE'))
)
    ENGINE = InnoDB
    DEFAULT CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '客服聊天消息表';


-- ============================================================
-- 3. 聊天消息已读状态表
-- ============================================================

CREATE TABLE IF NOT EXISTS chat_message_read
(
    message_id VARCHAR(64) NOT NULL COMMENT '已读消息ID',
    session_id VARCHAR(64) NOT NULL COMMENT '所属会话ID',
    user_id    VARCHAR(64) NOT NULL COMMENT '执行已读操作的用户ID',
    read_time  DATETIME(6) NOT NULL COMMENT '已读时间',

    PRIMARY KEY (message_id, user_id),
    KEY idx_chat_message_read_session_user (session_id, user_id, read_time),

    CONSTRAINT fk_chat_message_read_message
        FOREIGN KEY (message_id)
            REFERENCES chat_message (id)
            ON UPDATE CASCADE
            ON DELETE CASCADE,

    CONSTRAINT fk_chat_message_read_session
        FOREIGN KEY (session_id)
            REFERENCES chat_session (id)
            ON UPDATE CASCADE
            ON DELETE CASCADE
)
    ENGINE = InnoDB
    DEFAULT CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '聊天消息已读状态表';
