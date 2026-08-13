-- 将消息幂等范围从全局 client_msg_id 调整为会话、发送者和客户端消息编号。
-- 执行前先确认重复检查查询没有结果。
SET NAMES utf8mb4;

SELECT session_id, sender_id, client_msg_id, COUNT(*) AS duplicate_count
FROM chat_message
WHERE client_msg_id IS NOT NULL
GROUP BY session_id, sender_id, client_msg_id
HAVING COUNT(*) > 1;

ALTER TABLE chat_message
    DROP INDEX uk_chat_message_client_msg_id,
    DROP INDEX idx_chat_message_session_time,
    ADD UNIQUE INDEX uk_chat_message_client
        (session_id, sender_id, client_msg_id),
    ADD INDEX idx_chat_message_session_time
        (session_id, create_time, id);
