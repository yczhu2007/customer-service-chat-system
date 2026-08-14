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
- `chat_ddl.sql` 会创建聊天会话、消息、已读状态及客服技能表。

### 已有数据库升级

本项目当前按全新安装方式维护最终表结构，没有保留一次性升级脚本。发布到已有数据库前必须先备份数据并核对表结构。

本次版本至少要求：

- 存在 `chat_agent_skill` 表，用于持久化 VIP 客服技能。
- 存在 `chat:message:deadletter:manage` 权限，并已分配给 `ADMIN` 角色。

`rbac_ddl.sql` 中的基础数据使用幂等写入，可以重复执行。`chat_ddl.sql` 使用 `CREATE TABLE IF NOT EXISTS`，只能补建不存在的表，不会修改已有表的字段、精度或排序规则。

如果旧数据库的 `sys_user.id` 与 `chat_agent_skill.agent_id` 字符集或排序规则不同，必须先统一字段定义，确认外键字段兼容后再创建客服技能表。

升级后执行以下 SQL 检查：

```sql
SHOW TABLES LIKE 'chat_agent_skill';

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
```

三个检查结果应分别为：存在 `chat_agent_skill` 表、`permission_count = 1`、`admin_permission_count = 1`。

如果客服技能表缺失，应用启动时会输出 VIP 技能缓存降级告警；如果管理员没有死信管理权限，死信查询和重放接口会返回 `403 Forbidden`。

## 构建与测试

普通自动化测试：

```powershell
.\mvnw.cmd test
```

真实 MySQL、Redis 集成测试需要先完成上述数据库初始化，并设置 `DB_PASSWORD`、`ADMIN_BOOTSTRAP_PASSWORD` 和 `RUN_REAL_INTEGRATION_TESTS=true`。

```powershell
$env:RUN_REAL_INTEGRATION_TESTS='true'
.\mvnw.cmd -pl application -am '-Dtest=RealInfrastructureIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```
