-- 客服聊天系统：RBAC角色与权限表
-- 目标数据库：MySQL 8.x
-- 执行顺序：
--   1. user_ddl.sql
--   2. rbac_ddl.sql
--
-- 本脚本可重复执行：
-- - 表使用 CREATE TABLE IF NOT EXISTS；
-- - 基础角色、权限和角色权限关系使用幂等插入。

SET NAMES utf8mb4;


-- ============================================================
-- 1. 角色表
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_role
(
    id          VARCHAR(64)  NOT NULL COMMENT '角色ID',
    role_code   VARCHAR(64)  NOT NULL COMMENT '角色编码：USER、AGENT、ADMIN',
    role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称',
    description VARCHAR(255) NULL COMMENT '角色说明',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED'
        COMMENT '角色状态：ENABLED、DISABLED',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '最后修改时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code),
    KEY idx_sys_role_status (status),

    CONSTRAINT chk_sys_role_status
        CHECK (status IN ('ENABLED', 'DISABLED'))
)
    ENGINE = InnoDB
    DEFAULT CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '系统角色表';


-- ============================================================
-- 2. 权限表
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_permission
(
    id              VARCHAR(64)  NOT NULL COMMENT '权限ID',
    permission_code VARCHAR(128) NOT NULL COMMENT '权限编码',
    permission_name VARCHAR(64)  NOT NULL COMMENT '权限名称',
    permission_type VARCHAR(16)  NOT NULL DEFAULT 'API'
        COMMENT '权限类型：API、MENU、BUTTON',
    request_method  VARCHAR(16)  NULL COMMENT 'HTTP方法，非接口权限可为空',
    request_path    VARCHAR(255) NULL COMMENT '接口路径，非接口权限可为空',
    description     VARCHAR(255) NULL COMMENT '权限说明',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED'
        COMMENT '权限状态：ENABLED、DISABLED',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '最后修改时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (permission_code),
    KEY idx_sys_permission_type (permission_type),
    KEY idx_sys_permission_status (status),

    CONSTRAINT chk_sys_permission_type
        CHECK (permission_type IN ('API', 'MENU', 'BUTTON')),
    CONSTRAINT chk_sys_permission_status
        CHECK (status IN ('ENABLED', 'DISABLED'))
)
    ENGINE = InnoDB
    DEFAULT CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '系统权限表';


-- ============================================================
-- 3. 用户角色关联表
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_user_role
(
    user_id     VARCHAR(64) NOT NULL COMMENT '用户ID',
    role_id     VARCHAR(64) NOT NULL COMMENT '角色ID',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '分配时间',

    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role_id (role_id),

    CONSTRAINT fk_sys_user_role_user
        FOREIGN KEY (user_id)
            REFERENCES sys_user (id)
            ON UPDATE CASCADE
            ON DELETE CASCADE,

    CONSTRAINT fk_sys_user_role_role
        FOREIGN KEY (role_id)
            REFERENCES sys_role (id)
            ON UPDATE CASCADE
            ON DELETE CASCADE
)
    ENGINE = InnoDB
    DEFAULT CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '用户角色关联表';


-- ============================================================
-- 4. 角色权限关联表
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_role_permission
(
    role_id       VARCHAR(64) NOT NULL COMMENT '角色ID',
    permission_id VARCHAR(64) NOT NULL COMMENT '权限ID',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '分配时间',

    PRIMARY KEY (role_id, permission_id),
    KEY idx_sys_role_permission_permission_id (permission_id),

    CONSTRAINT fk_sys_role_permission_role
        FOREIGN KEY (role_id)
            REFERENCES sys_role (id)
            ON UPDATE CASCADE
            ON DELETE CASCADE,

    CONSTRAINT fk_sys_role_permission_permission
        FOREIGN KEY (permission_id)
            REFERENCES sys_permission (id)
            ON UPDATE CASCADE
            ON DELETE CASCADE
)
    ENGINE = InnoDB
    DEFAULT CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '角色权限关联表';


-- ============================================================
-- 5. 基础角色
-- ============================================================

INSERT INTO sys_role
    (id, role_code, role_name, description, status)
VALUES
    ('R_USER', 'USER', '普通用户', '发起客服咨询的普通用户', 'ENABLED'),
    ('R_AGENT', 'AGENT', '客服', '接待普通用户并处理聊天会话', 'ENABLED'),
    ('R_ADMIN', 'ADMIN', '管理员', '管理用户、角色和权限', 'ENABLED')
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    description = VALUES(description),
    status = VALUES(status);


-- ============================================================
-- 6. 基础权限
-- ============================================================

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
        'P_CHAT_USER_ACCESS',
        'chat:user:access',
        '用户接入客服',
        'API',
        NULL,
        '/user/queue/chat',
        '允许普通用户订阅聊天分配队列并发起客服咨询',
        'ENABLED'
    ),
    (
        'P_CHAT_AGENT_ONLINE',
        'chat:agent:online',
        '客服上线',
        'API',
        'POST',
        '/chat/agent/online',
        '允许客服进入可分配状态',
        'ENABLED'
    ),
    (
        'P_CHAT_AGENT_OFFLINE',
        'chat:agent:offline',
        '客服下线',
        'API',
        'POST',
        '/chat/agent/offline',
        '允许客服退出可分配状态',
        'ENABLED'
    ),
    (
        'P_CHAT_AGENT_VIP_SKILL_MANAGE',
        'chat:agent:vip-skill:manage',
        'VIP坐席技能组管理',
        'API',
        NULL,
        '/chat/agents/**/vip-skill',
        '允许管理员配置和查询VIP坐席技能组',
        'ENABLED'
    ),
    (
        'P_CHAT_SESSION_END',
        'chat:session:end',
        '结束聊天会话',
        'API',
        NULL,
        '/app/chat.end',
        '允许客服结束自己正在处理的聊天会话',
        'ENABLED'
    ),
    (
        'P_CHAT_SESSION_TRANSFER',
        'chat:session:transfer',
        '转接聊天会话',
        'API',
        NULL,
        '/app/chat.transfer',
        '允许客服将自己正在处理的会话转接给其他在线客服',
        'ENABLED'
    ),
    (
        'P_USER_MANAGE',
        'user:manage',
        '用户管理',
        'API',
        NULL,
        '/users/**',
        '允许管理用户信息和用户状态',
        'ENABLED'
    ),
    (
        'P_ROLE_MANAGE',
        'role:manage',
        '角色管理',
        'API',
        NULL,
        '/roles/**',
        '允许管理角色以及用户角色关系',
        'ENABLED'
    ),
    (
        'P_PERMISSION_MANAGE',
        'permission:manage',
        '权限管理',
        'API',
        NULL,
        '/permissions/**',
        '允许管理权限以及角色权限关系',
        'ENABLED'
    )
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    permission_type = VALUES(permission_type),
    request_method = VALUES(request_method),
    request_path = VALUES(request_path),
    description = VALUES(description),
    status = VALUES(status);


-- ============================================================
-- 7. 基础角色权限关系
-- ============================================================

-- 普通用户权限
INSERT INTO sys_role_permission
    (role_id, permission_id)
VALUES
    ('R_USER', 'P_CHAT_USER_ACCESS')
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id);


-- 客服权限
INSERT INTO sys_role_permission
    (role_id, permission_id)
VALUES
    ('R_AGENT', 'P_CHAT_AGENT_ONLINE'),
    ('R_AGENT', 'P_CHAT_AGENT_OFFLINE'),
    ('R_AGENT', 'P_CHAT_SESSION_END'),
    ('R_AGENT', 'P_CHAT_SESSION_TRANSFER')
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id);


-- 管理员默认拥有全部基础权限
INSERT INTO sys_role_permission
    (role_id, permission_id)
SELECT
    'R_ADMIN',
    permission.id
FROM sys_permission AS permission
WHERE permission.status = 'ENABLED'
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id);
