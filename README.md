# 客服聊天系统

基于 Spring Boot 4 + WebSocket STOMP + Redis + MySQL 的多角色客服实时聊天系统。

## 功能概览

### 用户端

| 功能 | 说明 |
|---|---|
| 注册与登录 | 用户名注册（自动分配 USER 角色）、记住我（7 天 Token）、忘记密码（恢复码） |
| 发起咨询 | 用户连接 WebSocket 后发起咨询，系统自动分配空闲客服或进入排队 |
| 实时消息 | 文本、图片、文件消息，消息去重（clientMsgId）、已读回执、编辑、撤回 |
| 离线消息 | 用户离线期间的消息在重新上线后自动拉取并 ACK |
| 排队状态 | 查看在线客服数、排队人数、我的位置、预估等待时间 |
| 满意度评价 | 对已结束会话提交 1-5 星评价，每人每会话一次 |

### 客服工作台

| 功能 | 说明 |
|---|---|
| 上下线 | 客服上线后可接收新会话，离线时当前会话自动结束 |
| 多会话切换 | 客服同时接待多个会话，通过会话列表切换，支持未读计数 |
| 快捷回复 | 客服维护常用回复，点击快速插入到消息输入框 |
| 消息操作 | 标记已读、编辑、撤回（限 2 分钟内） |
| 会话转接 | 将当前会话转给其他在线客服，Redis 原子迁移 + DB 事务保证一致性 |
| 会话归档 | 对已结束会话设置归档状态（已完成 / 挂起·等客户 / 挂起·等内部 / 其他），参考 Zendesk 状态机 |
| 用户信息侧栏 | 查看当前会话用户的基本信息、VIP 等级、历史会话数 |

### 管理后台

| 功能 | 说明 |
|---|---|
| 用户管理 | CRUD 用户、分配角色、设置 VIP 等级、禁用/启用 |
| 角色权限 | CRUD 角色和权限、分配权限给角色 |
| VIP 技能组 | 管理客服的 VIP 接待技能（DB 持久化 + Redis 缓存） |
| 归档统计 | 查看各归档状态的会话数量（Zendesk 风格卡片布局） |
| 死信管理 | 查看落库失败的消息、手动重放 |

### 基础设施

| 能力 | 说明 |
|---|---|
| 消息可靠投递 | 异步落库 + 重试（指数退避，最多 3 次）+ 死信 + 补偿调度 |
| 心跳与断线 | WebSocket 双向心跳（10 秒）、超时断线、客服断线宽限期（20 秒） |
| 分布式锁 | 基于 Redis 的会话操作锁、客服分配锁、调度器分布式锁（带 watchdog 续期） |
| 对账调度 | 会话状态、消息落库、Redis 过期数据定期清理与补偿 |
| 认证限流 | 登录 / 注册 / 密码重置分别使用独立 Redis 计数桶，默认 5 次/60 秒 |

## 技术栈

| 层 | 技术 |
|---|---|
| 框架 | Spring Boot 4.0.7、Spring WebSocket、Spring Security (Shiro 2.0.2) |
| 数据库 | MySQL 8.x（utf8mb4）、MyBatis-Plus 3.5.17 |
| 缓存 | Redis（Spring Data Redis） |
| 认证 | 无状态 Token（Redis 存储）、PBKDF2WithHmacSHA256 密码哈希 |
| 前端 | 单页 HTML + 原生 JS（STOMP/WebSocket），用于功能验证 |
| 构建 | Maven、Java 17+、Mockito 5.23.0（JDK 26 兼容） |

## 项目结构

```
customer-service-chat-system/
├── application/          # 启动模块：Controller、Config、Scheduler、前端页面
├── businessModel/        # 业务模块：Service 实现、Security
├── commonModel/          # 公共模块：Domain、DTO、Mapper、常量、工具
├── third-party-libs/     # 第三方依赖聚合
├── sql/                  # 数据库脚本
│   ├── user_ddl.sql      # 用户表 + 恢复码表
│   ├── rbac_ddl.sql      # 角色/权限表 + 种子数据
│   └── chat_ddl.sql      # 聊天相关表
└── pom.xml               # 父 POM
```

## 数据库部署

### 全新安装

在目标数据库中依次执行：

```bash
mysql -u root -p springboot < sql/user_ddl.sql
mysql -u root -p springboot < sql/rbac_ddl.sql
mysql -u root -p springboot < sql/chat_ddl.sql
```

执行顺序不可调换：`user_ddl` → `rbac_ddl` → `chat_ddl`（后者依赖前者的表和种子数据）。

### 已有数据库升级

如数据库已经由本项目脚本建立，可重新执行 `rbac_ddl.sql` 和 `chat_ddl.sql`，以幂等方式补充缺失的权限和新表。`CREATE TABLE IF NOT EXISTS` 不会修改已有字段，旧结构仍需先人工核对。

当前版本新增的表和字段：

| 对象 | 说明 |
|---|---|
| `chat_agent_skill` | 客服 VIP 技能 |
| `chat_attachment` | 聊天附件 |
| `chat_quick_reply` | 快捷回复 |
| `chat_session_rating` | 满意度评价 |
| `chat_session_transfer_log` | 客服会话转接记录 |
| `chat_session` 归档字段 | `archive_status`、`archive_remark`、`archived_by`、`archived_at` |

新增权限（`rbac_ddl.sql` 幂等写入，可重复执行）：

| 权限 | 分配角色 |
|---|---|
| `chat:session:archive` | AGENT |
| `chat:archive:stats` | ADMIN（通过全量 SELECT 自动获得） |
| `chat:session:rate` | USER |
| `chat:agent:dashboard:view` | AGENT |
| `chat:admin:dashboard:view` | ADMIN |
| `chat:rating:stats:view` | AGENT、ADMIN |
| `chat:session:audit:view` | ADMIN |
| `chat:session:transfer-log:view` | AGENT、ADMIN |
| `chat:quick-reply:manage` | AGENT |

## 配置

### 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/springboot` | 数据库连接 |
| `DB_USERNAME` | `root` | 数据库用户名 |
| `DB_PASSWORD` | — | 数据库密码 |
| `REDIS_HOST` | `127.0.0.1` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | — | Redis 密码 |
| `ADMIN_BOOTSTRAP_PASSWORD` | — | 首次启动时自动创建管理员的密码（仅首次需要） |
| `AUTH_TOKEN_TTL_MINUTES` | `30` | 普通 Token 有效期 |
| `AUTH_REMEMBER_TOKEN_TTL_DAYS` | `7` | 记住我 Token 有效期 |
| `AUTH_RECOVERY_CODE_TTL_DAYS` | `90` | 恢复码有效期 |
| `AUTH_REGISTRATION_RATE_LIMIT_MAX_REQUESTS` | `5` | 注册限流次数 |
| `AUTH_TRUST_FORWARDED_HEADERS` | `false` | 是否信任 X-Forwarded-For |
| `CHAT_ATTACHMENT_STORAGE_PATH` | `./data/chat-attachments` | 附件存储路径 |
| `CHAT_MESSAGE_RECALL_WINDOW_SECONDS` | `120` | 消息撤回窗口 |
| `CHAT_MESSAGE_EDIT_WINDOW_SECONDS` | `300` | 消息编辑窗口 |
| `CHAT_SESSION_INACTIVITY_TIMEOUT_SECONDS` | `1800` | 会话无活动超时 |
| `CHAT_QUEUE_TIMEOUT_SECONDS` | `300` | 排队超时 |

### 生产环境

- `spring-boot-starter-actuator` 已引入，`/actuator/health` 允许匿名访问
- 附件存储默认在单机目录，多实例部署应挂载共享存储或替换为对象存储
- 日志文件不提交到 Git（`.gitignore` 排除 `*.log`）

## API 概览

### 认证

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/chat/login` | 登录（返回 Token） | 匿名 |
| POST | `/chat/logout` | 退出登录（吊销 Token） | 已登录 |
| POST | `/account/register` | 用户注册 | 匿名（限流） |
| POST | `/account/forgot-password` | 密码重置（恢复码） | 匿名（限流） |
| POST | `/chat/ws-ticket` | 获取 WebSocket 一次性票据 | 已登录 |

### 会话

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/chat/sessions` | 会话历史列表（支持 `archiveStatus`、`status` 筛选） | 已登录 |
| GET | `/chat/queue-status` | 排队状态 | 已登录 |
| GET | `/chat/sessions/{id}/rating` | 查询会话评价 | 会话参与者 |
| POST | `/chat/sessions/{id}/rating` | 提交满意度评价 | USER + chat:session:rate |
| PUT | `/chat/sessions/{id}/archive-status` | 设置归档状态 | AGENT + chat:session:archive |
| GET | `/chat/sessions/{id}/user-profile` | 用户信息侧栏 | AGENT |
| GET | `/chat/admin/archive-stats` | 归档统计 | ADMIN + chat:archive:stats |
| GET | `/chat/sessions/{id}/transfers` | 会话转接记录 | 参与转接的 AGENT 或 ADMIN |
| GET | `/chat/agent/dashboard` | 客服工作台概览 | AGENT + chat:agent:dashboard:view |
| GET | `/chat/admin/dashboard` | 管理员运营概览 | ADMIN + chat:admin:dashboard:view |
| GET | `/chat/agent/ratings/summary` | 当前客服满意度统计 | AGENT + chat:rating:stats:view |
| GET | `/chat/admin/ratings/summary` | 整体或指定客服满意度统计 | ADMIN + chat:rating:stats:view |
| GET | `/chat/admin/sessions` | 会话质检分页查询 | ADMIN + chat:session:audit:view |

### 附件

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/chat/attachments` | 上传附件（multipart） | 会话参与者 |
| GET | `/chat/attachments/{id}/content` | 下载附件 | 会话参与者 |

### 快捷回复

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/chat/quick-replies` | 查询我的快捷回复 | AGENT |
| POST | `/chat/quick-replies` | 创建快捷回复 | AGENT + chat:quick-reply:manage |
| PUT | `/chat/quick-replies/{id}` | 修改快捷回复 | AGENT（限本人） |
| DELETE | `/chat/quick-replies/{id}` | 删除快捷回复 | AGENT（限本人） |

### 用户管理

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/users` | 用户列表（分页） | ADMIN + user:manage |
| POST | `/users` | 创建用户 | ADMIN + user:manage |
| PUT | `/users/{id}` | 修改用户 | ADMIN + user:manage |
| PUT | `/users/{id}/password` | 修改用户密码 | ADMIN + user:manage |
| DELETE | `/users/{id}` | 删除用户 | ADMIN + user:manage |

### STOMP WebSocket 消息

| 目标 | 说明 |
|---|---|
| `/app/chat.start` | 用户发起咨询 |
| `/app/chat.send` | 发送消息（TEXT / IMAGE / FILE） |
| `/app/chat.end` | 客服结束会话 |
| `/app/chat.transfer` | 客服转接会话 |
| `/app/chat.history` | 加载历史消息 |
| `/app/chat.read` | 标记消息已读 |
| `/app/chat.message.edit` | 编辑消息 |
| `/app/chat.message.recall` | 撤回消息 |
| `/app/chat.ack` | 消息 ACK |
| `/app/chat.heartbeat` | 心跳 |
| `/app/chat.offline.pull` | 拉取离线消息 |
| `/user/queue/chat` | 接收会话事件和消息（订阅） |
| `/user/queue/messages` | 接收已读/编辑/撤回事件（订阅） |
| `/user/queue/errors` | 接收 STOMP 错误（订阅） |

## 测试

```powershell
# 单元测试
.\mvnw.cmd test

# 真实 MySQL/Redis 集成测试（需先完成数据库初始化）
$env:RUN_REAL_INTEGRATION_TESTS='true'
.\mvnw.cmd -pl application -am '-Dtest=RealInfrastructureIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

集成测试需要的环境变量：`DB_PASSWORD`、`ADMIN_BOOTSTRAP_PASSWORD`、`RUN_REAL_INTEGRATION_TESTS=true`。

## Vue 前端

### 开发模式

```bash
cd frontend
npm install
npm run dev    # http://localhost:5173（自动代理 /chat、/ws/chat 等到 localhost:8080）
```

启动后端：
```powershell
.\mvnw.cmd -pl application -am spring-boot:run
```

### 生产打包

Maven 在 `generate-resources` 阶段自动执行 `npm ci` + `npm run build`，并将 `frontend/dist` 复制到 JAR 的 `static/frontend/` 目录。

```powershell
.\mvnw.cmd -pl application -am clean package -DskipTests
java -jar application/target/*.jar
```

### 路由

| 路径 | 说明 |
|---|---|
| `/user` | 用户工作台（排队、聊天、评价） |
| `/agent` | 客服工作台（五视图、元数据、归档、转接、快捷回复） |
| `/admin` | 管理员工作台（仪表盘、用户/角色管理、会话审计、归档统计、死信） |
| `/login` | 登录页 |
| `/stomp-test.html` | 旧功能验证页（保留至 Vue 回归测试通过） |

### 前端技术栈

Vue 3 + Vite + Vue Router + Pinia + `@stomp/stompjs`

## 前端测试页面

`/stomp-test.html` 是一个单页应用，用于功能验证：

- 登录 / 注册 / 忘记密码
- WebSocket 连接与心跳
- 实时消息收发（含图片/文件预览）
- 客服上下线、快捷回复
- 会话列表（未读计数、归档筛选、归档状态徽标）
- 排队状态展示
- 满意度评价（星级展示 + 提交）
- 客服工作台（在线人数、排队人数、活跃会话、今日完成量、满意度统计）
- 会话转接记录时间线
- 管理员功能（运营仪表盘、满意度统计、会话质检、VIP 设置、归档统计、死信管理）
