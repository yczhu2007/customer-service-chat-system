-- Upgrade existing chat_message tables for quote replies.
ALTER TABLE chat_message
    ADD COLUMN reply_to_message_id VARCHAR(64) NULL COMMENT '被引用的消息ID' AFTER client_msg_id,
    ADD KEY idx_chat_message_reply (reply_to_message_id);
