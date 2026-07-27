-- 已有数据库聊天表升级脚本
-- 仅用于已经存在 chat_session、chat_message 表的数据库。
-- 本脚本不会删除表或删除数据，只需执行一次。

SET NAMES utf8mb4;


-- 执行唯一索引前先检查是否存在重复client_msg_id。
-- 查询结果应当为空；如果有结果，需要先处理重复数据。
SELECT
    client_msg_id,
    COUNT(*) AS duplicate_count
FROM chat_message
WHERE client_msg_id IS NOT NULL
GROUP BY client_msg_id
HAVING COUNT(*) > 1;


ALTER TABLE chat_session
    ADD INDEX idx_chat_session_user_status
        (user_id, status),
    ADD INDEX idx_chat_session_agent_status
        (agent_id, status),
    ADD INDEX idx_chat_session_create_time
        (create_time);


ALTER TABLE chat_message
    ADD UNIQUE INDEX uk_chat_message_client_msg_id
        (client_msg_id),
    ADD INDEX idx_chat_message_session_time
        (session_id, create_time),
    ADD INDEX idx_chat_message_sender_time
        (sender_id, create_time);
