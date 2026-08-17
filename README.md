# 客服聊天系统

## 数据库部署

数据库脚本位于 `sql` 目录，使用 MySQL 8.x，并统一使用 `utf8mb4`。

### 全新安装

在同一个目标数据库中依次执行：

1. `sql/user_ddl.sql`
2. `sql/rbac_ddl.sql`
3. `sql/chat_ddl.sql`

其中：

- `rbac_ddl.sql` 会创建角色权限表、写入基础角色和权限，并为管理员分配全部启用权限。
- `chat_ddl.sql` 会创建聊天会话、消息、已读状态、客服技能、聊天附件、快捷回复及会话满意度评价表。
- `user_ddl.sql` 会同时创建账号恢复码表；恢复码仅在注册、重置或主动重新生成时展示一次，数据库只保存哈希，默认 90 天有效。

### 已有数据库升级

本项目当前按全新安装方式维护最终表结构，没有保留一次性升级脚本。发布到已有数据库前必须先备份数据并核对表结构。

本次版本至少要求：

- 存在 `chat_agent_skill` 表，用于持久化 VIP 客服技能。
- 存在 `chat_attachment` 和 `chat_quick_reply` 表。
- 存在 `chat_session_rating` 表，用于用户对已结束会话的满意度评价。
- 存在 `chat:message:deadletter:manage` 权限，并已分配给 `ADMIN` 角色。
- 存在 `chat:quick-reply:manage` 权限，并已分配给 `AGENT` 角色。
- 存在 `chat:session:rate` 权限，并已分配给 `USER` 角色。

`rbac_ddl.sql` 中的基础数据使用幂等写入，可以重复执行。`chat_ddl.sql` 使用 `CREATE TABLE IF NOT EXISTS`，只能补建不存在的表，不会修改已有表的字段、精度或排序规则。

聊天附件默认存放在单机目录 `./data/chat-attachments`，可通过 `CHAT_ATTACHMENT_STORAGE_PATH` 修改。该配置适合当前单实例项目；多实例或生产部署应将此路径挂载到共享存储，或将存储实现替换为对象存储，并纳入备份与生命周期管理。

如果旧数据库的 `sys_user.id` 与 `chat_agent_skill.agent_id` 字符集或排序规则不同，必须先统一字段定义，确认外键字段兼容后再创建客服技能表。

升级后执行以下 SQL 检查：

```sql
SHOW TABLES LIKE 'chat_agent_skill';
SHOW TABLES LIKE 'chat_attachment';
SHOW TABLES LIKE 'chat_quick_reply';
SHOW TABLES LIKE 'chat_session_rating';
SHOW TABLES LIKE 'sys_password_recovery';

SELECT COUNT(*) AS permission_count
FROM sys_permission
WHERE permission_code = 'chat:message:deadletter:manage'
  AND status = 'ENABLED';

SELECT COUNT(*) AS admin_permission_count
FROM sys_role_permission relation
JOIN sys_role role_info
  ON role_info.id = relation.role_id
JOIN sys_permission permission
  ON permission.id = relation.permission_id
WHERE role_info.role_code = 'ADMIN'
  AND permission.permission_code = 'chat:message:deadletter:manage';

SELECT COUNT(*) AS agent_quick_reply_permission_count
FROM sys_role_permission relation
JOIN sys_role role_info ON role_info.id = relation.role_id
JOIN sys_permission permission ON permission.id = relation.permission_id
WHERE role_info.role_code = 'AGENT'
  AND permission.permission_code = 'chat:quick-reply:manage';

SELECT COUNT(*) AS user_session_rate_permission_count
FROM sys_role_permission relation
JOIN sys_role role_info ON role_info.id = relation.role_id
JOIN sys_permission permission ON permission.id = relation.permission_id
WHERE role_info.role_code = 'USER'
  AND permission.permission_code = 'chat:session:rate';
```

三个检查结果应分别为：存在 `chat_agent_skill` 表、`permission_count = 1`、`admin_permission_count = 1`。

如果客服技能表缺失，应用启动时会输出 VIP 技能缓存降级告警；如果管理员没有死信管理权限，死信查询和重放接口会返回 `403 Forbidden`。

## 构建与测试

## 反向代理与认证限流

登录、注册和密码重置接口分别使用独立计数桶，默认限制同一客户端 60 秒内最多请求 5 次。
应用部署在 Nginx 等可信反向代理后，可设置
`AUTH_TRUST_FORWARDED_HEADERS=true`，此时会读取 `X-Forwarded-For` 中的第一个客户端地址。
只有在应用端口不直接暴露、请求必须经过可信代理时才能开启，避免客户端伪造请求头绕过限流。

限流计数和过期时间通过 Redis Lua 脚本原子写入，超出限制时返回 `429 Too Many Requests`。

普通登录 Token 默认有效期为 30 分钟；勾选“记住我”后默认有效期为 7 天，可通过
`AUTH_REMEMBER_TOKEN_TTL_DAYS` 调整。退出登录、修改密码或禁用账号都会立即吊销对应 Token。

## 会话查询与评价

- `GET /chat/sessions` — 当前用户查看自己的会话历史列表（自动按用户或客服角色过滤），支持 `status`、`pageNo`、`pageSize` 参数，每条记录包含 `unreadCount`（当前用户在该会话中的未读消息数）。
- `GET /chat/queue-status` — 查询当前排队状态，返回 `onlineAgentCount`（在线客服数）、`queueSize`（排队人数）、`myPosition`（我的位置，不在队列中时为 null）、`estimatedWaitSeconds`（预估等待秒数）。
- `GET /chat/sessions/{sessionId}/rating` — 查询指定会话的满意度评价。
- `POST /chat/sessions/{sessionId}/rating` — 用户对已结束的会话提交满意度评价（1-5 星 + 可选文字），每会话仅一次，需要 `chat:session:rate` 权限。
- `GET /chat/sessions/{sessionId}/user-profile` — 客服查看当前会话中用户的基本信息侧栏（用户名、VIP 等级、历史会话数、上次会话时间）。

普通自动化测试：

```powershell
.\mvnw.cmd test
```

真实 MySQL、Redis 集成测试需要先完成上述数据库初始化，并设置 `DB_PASSWORD`、`ADMIN_BOOTSTRAP_PASSWORD` 和 `RUN_REAL_INTEGRATION_TESTS=true`。

```powershell
$env:RUN_REAL_INTEGRATION_TESTS='true'
.\mvnw.cmd -pl application -am '-Dtest=RealInfrastructureIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```
