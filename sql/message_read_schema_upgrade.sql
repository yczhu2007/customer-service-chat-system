-- 已有数据库升级：增加聊天消息已读状态表。
-- 本脚本不会删除或修改原有聊天消息。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message_read
(
    message_id VARCHAR(64) NOT NULL COMMENT '已读消息ID',
    session_id VARCHAR(64) NOT NULL COMMENT '所属会话ID',
    user_id    VARCHAR(64) NOT NULL COMMENT '执行已读操作的用户ID',
    read_time  DATETIME    NOT NULL COMMENT '已读时间',

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
    -- 兼容项目早期已经使用utf8mb4_unicode_ci创建的聊天表，
    -- 外键字符串字段必须与被引用字段保持相同排序规则。
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '聊天消息已读状态表';
