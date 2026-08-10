-- 消息编辑、撤回功能数据库升级脚本。
-- 仅用于已经存在 chat_message 表的数据库；全新数据库直接执行 chat_ddl.sql。

ALTER TABLE chat_message
    ADD COLUMN edited TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否编辑过' AFTER create_time,
    ADD COLUMN edited_at DATETIME NULL COMMENT '最后编辑时间' AFTER edited,
    ADD COLUMN original_content TEXT NULL COMMENT '首次编辑前的原始内容，仅供审计' AFTER edited_at,
    ADD COLUMN recalled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已撤回' AFTER original_content,
    ADD COLUMN recalled_at DATETIME NULL COMMENT '撤回时间' AFTER recalled;
