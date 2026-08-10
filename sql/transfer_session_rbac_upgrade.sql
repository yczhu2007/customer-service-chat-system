-- 为已有数据库补充客服转接权限，重复执行安全。

INSERT INTO sys_permission
    (id, permission_code, permission_name, permission_type, request_method, request_path, description, status)
VALUES
    ('P_CHAT_SESSION_TRANSFER', 'chat:session:transfer', '转接聊天会话', 'API', NULL,
     '/app/chat.transfer', '允许客服将自己正在处理的会话转接给其他在线客服', 'ENABLED')
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    request_method = VALUES(request_method),
    request_path = VALUES(request_path),
    description = VALUES(description),
    status = VALUES(status);

INSERT INTO sys_role_permission
    (role_id, permission_id)
VALUES
    ('R_AGENT', 'P_CHAT_SESSION_TRANSFER'),
    ('R_ADMIN', 'P_CHAT_SESSION_TRANSFER')
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id);
