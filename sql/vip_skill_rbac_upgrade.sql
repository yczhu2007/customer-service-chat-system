-- 为已有数据库补充VIP坐席技能组管理权限。
-- 执行一次即可，重复执行安全。

INSERT INTO sys_permission
    (
        id,
        permission_code,
        permission_name,
        permission_type,
        request_method,
        request_path,
        description,
        status
    )
VALUES
    (
        'P_CHAT_AGENT_VIP_SKILL_MANAGE',
        'chat:agent:vip-skill:manage',
        'VIP坐席技能组管理',
        'API',
        NULL,
        '/chat/agents/**/vip-skill',
        '允许管理员配置和查询VIP坐席技能组',
        'ENABLED'
    )
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
    ('R_ADMIN', 'P_CHAT_AGENT_VIP_SKILL_MANAGE')
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id);
