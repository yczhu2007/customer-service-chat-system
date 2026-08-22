# 客服聊天系统

一个面向小型团队的轻量级客服系统，参考 Zendesk 的工单视图、状态和分类方式，实现用户咨询、客服接待、管理员审计以及消息可靠投递。

项目采用 Spring Boot、WebSocket STOMP、Redis、MySQL 和 Vue 3，提供用户端、客服端、管理员端三个独立工作台。`stomp-test.html` 作为旧版功能验证页面保留，正式界面位于 Vue 3 前端。

## 核心功能

### 用户端

- 注册、登录、退出登录、记住我和恢复码重置密码。
- 发起新咨询，查看排队人数、当前位置和预计等待时间。
- 实时发送文本、图片和文件消息。
- 文件消息展示原始文件名、大小和下载入口，不向用户展示 MIME 字符串。
- 查看历史会话、最后一条消息和未读数量。
- 接收消息送达、已读、编辑和撤回事件。
- 对已结束的不同会话分别提交 1–5 星满意度评价。

### 客服端

- 客服上线、下线和断线恢复。
- 使用固定视图查看处理中、未读、高优先级、未归档和最近关闭的会话。
- 多会话切换、未读计数、历史消息和实时更新。
- 发送文本、图片和文件消息，维护快捷回复。
- 编辑会话标题、优先级、分类和标签。
- 转接会话、结束会话、查看转接记录和用户资料。
- 对已结束会话设置 Zendesk 风格归档状态：`Solved`、`Pending`、`On-hold`、`Other`。

### 管理员端

- 查看在线客服数、排队数、当日会话和消息统计。
- 管理用户、角色、权限和 VIP 客服技能组。
- 按用户、客服、状态、归档状态、评分和起止时间审计会话；时间筛选统一使用 `YYYY-MM-DD HH:mm` 格式。
- 会话审计的业务状态只使用 `ACTIVE` 和 `CLOSED`；归档状态通过独立字段筛选。
- 查看满意度、归档统计、转接记录和消息死信，并重放可恢复的死信消息。

### 消息与会话可靠性

- 每条客户端消息使用 `clientMsgId` 去重。
- 消息异步落库，支持重试、死信和补偿调度。
- 历史消息请求携带请求标识，避免快速切换会话时旧响应覆盖新响应。
- WebSocket 使用一次性 Ticket 连接，并通过业务心跳维护在线状态。
- HTTP 401 会统一清理认证信息、聊天状态和旧 WebSocket 连接。
- 会话结束在数据库事务提交后同步 Redis 并发送通知，降低 MySQL 与 Redis 状态不一致风险。
- 附件只允许系统内部附件地址，不允许外部附件地址携带系统认证信息。

## 界面规范

页面标题、按钮、筛选项、提示和操作反馈使用中文。只有可选择的业务枚举值保留英文：

- 归档状态：`Solved`、`Pending`、`On-hold`、`Other`。
- 分类：`Account`、`Payment`、`Technical`、`After-sales`、`Other`。
- 优先级：`LOW`、`NORMAL`、`HIGH`、`URGENT`。
- 管理员审计会话状态：`ACTIVE`、`CLOSED`。

归档状态、分类和优先级按具体枚举值使用固定颜色。同一个值在会话列表、会话详情和编辑区域中保持相同颜色。

## 技术栈

| 模块 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 4.0.7、Spring WebSocket、Spring Security、Apache Shiro 2.0.2 |
| 数据访问 | MyBatis-Plus 3.5.17、MySQL 8.x |
| 缓存与消息状态 | Redis、Spring Data Redis |
| 前端 | Vue 3、Vite、Vue Router、Pinia、`@stomp/stompjs` |
| 测试 | JUnit、Mockito、Vitest、Vue Test Utils、jsdom |
| 构建 | Maven、npm |

## 项目结构

```text
customer-service-chat-system/
├── application/          # 启动模块、Controller、配置、调度器、静态资源
├── businessModel/        # 会话路由、消息投递、认证和业务 Service
├── commonModel/          # Domain、DTO、Mapper、常量和公共工具
├── third-party-libs/     # 第三方依赖聚合模块
├── frontend/             # Vue 3 用户端、客服端和管理员端
├── sql/                  # 数据库初始化、RBAC 和升级脚本
├── docs/                 # 设计与实施文档
├── pom.xml               # Maven 父工程
└── README.md
```

## 运行环境

启动前需要准备：

- JDK 17 或更高版本。
- Maven 3.9 或使用项目 Maven Wrapper。
- Node.js 与 npm。
- MySQL 8.x。
- Redis 6.x 或更高版本。

## 数据库初始化

创建数据库后，按顺序执行：

```powershell
mysql -u root -p springboot < sql/user_ddl.sql
mysql -u root -p springboot < sql/rbac_ddl.sql
mysql -u root -p springboot < sql/chat_ddl.sql
```

执行顺序必须保持为：

```text
user_ddl.sql -> rbac_ddl.sql -> chat_ddl.sql
```

已有数据库需要补充会话标题、优先级、分类和标签字段时，再执行：

```powershell
mysql -u root -p springboot < sql/chat_session_metadata_upgrade.sql
```

`rbac_ddl.sql` 和 `chat_ddl.sql` 包含幂等初始化逻辑，可以用于补充缺失的表、权限和种子数据。但 `CREATE TABLE IF NOT EXISTS` 不会自动修改已有字段，升级旧数据库前应先检查实际表结构。

## 配置

常用环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/springboot` | MySQL 连接地址 |
| `DB_USERNAME` | `root` | MySQL 用户名 |
| `DB_PASSWORD` | 空 | MySQL 密码 |
| `REDIS_HOST` | `127.0.0.1` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_DATABASE` | `0` | Redis 数据库编号 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `ADMIN_BOOTSTRAP_PASSWORD` | 空 | 首次创建管理员时使用的密码 |
| `AUTH_TOKEN_TTL_MINUTES` | `30` | 普通 Token 有效期 |
| `AUTH_REMEMBER_TOKEN_TTL_DAYS` | `7` | 记住我 Token 有效期 |
| `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` | 本地地址 | WebSocket 允许的来源 |
| `CHAT_ATTACHMENT_STORAGE_PATH` | `./data/chat-attachments` | 附件存储目录 |
| `CHAT_MESSAGE_RECALL_WINDOW_SECONDS` | `120` | 消息撤回窗口 |
| `CHAT_MESSAGE_EDIT_WINDOW_SECONDS` | `300` | 消息编辑窗口 |
| `CHAT_SESSION_INACTIVITY_TIMEOUT_SECONDS` | `1800` | 无活动会话超时，默认 30 分钟 |
| `CHAT_QUEUE_TIMEOUT_SECONDS` | `300` | 普通用户排队超时 |
| `CHAT_VIP_QUEUE_TIMEOUT_SECONDS` | `120` | VIP 排队超时 |

PowerShell 配置示例：

```powershell
$env:DB_PASSWORD='你的数据库密码'
$env:ADMIN_BOOTSTRAP_PASSWORD='首次管理员密码'
```

## 启动方式

### 方式一：使用 IntelliJ IDEA

1. 用 IDEA 打开项目根目录。
2. 等待 Maven 模块和依赖加载完成。
3. 确认 MySQL、Redis 已启动，数据库脚本已经执行。
4. 配置需要的环境变量。
5. 运行 `CustomerServiceChatApplication.java`。

Maven 在资源生成阶段会构建 Vue 前端并复制到 Spring Boot 静态资源目录。修改前端后，需要重新执行前端构建或触发配置好的 IDEA 启动前任务。

### 方式二：分别启动前后端

启动后端：

```powershell
.\mvnw.cmd -pl application -am spring-boot:run
```

启动 Vue 开发服务器：

```powershell
cd frontend
npm install
npm run dev
```

开发服务器地址为 `http://localhost:5173`，并自动将 `/chat`、`/account`、`/users`、`/roles`、`/permissions` 和 `/ws/chat` 代理到 `http://localhost:8080`。

### 方式三：构建并运行 JAR

```powershell
.\mvnw.cmd -pl application -am clean package
java -jar application/target/customer-service-chat-application-*.jar
```

## 访问地址

后端启动并完成前端构建后：

| 地址 | 用途 |
|---|---|
| `http://localhost:8080/frontend/login` | Vue 登录页 |
| `http://localhost:8080/frontend/user` | 用户工作台 |
| `http://localhost:8080/frontend/agent` | 客服工作台 |
| `http://localhost:8080/frontend/admin` | 管理员工作台 |
| `http://localhost:8080/stomp-test.html` | 旧版功能验证页面 |
| `http://localhost:8080/actuator/health` | 后端健康检查 |

用户登录后会根据完整角色集合进入可用工作台。多角色账号可以在用户端、客服端和管理员端之间切换。

## 前端构建

单独构建 Vue 前端：

```powershell
cd frontend
npm ci
npm run build
```

构建结果位于 `frontend/dist`。Maven 构建会自动执行 `npm ci` 和 `npm run build`，再将产物复制到应用的 `static/frontend` 目录。

## 测试

前端测试与构建：

```powershell
cd frontend
npm test -- --run
npm run build
```

后端测试：

```powershell
.\mvnw.cmd -pl application -am test
```

真实 MySQL/Redis 集成测试默认不运行。准备好独立测试环境后执行：

```powershell
$env:RUN_REAL_INTEGRATION_TESTS='true'
.\mvnw.cmd -pl application -am '-Dtest=RealInfrastructureIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## 主要 HTTP API

### 认证和账号

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/chat/login` | 登录并返回 Token 与角色集合 |
| `POST` | `/chat/logout` | 注销 Token |
| `POST` | `/account/register` | 注册普通用户 |
| `POST` | `/account/forgot-password` | 使用恢复码重置密码 |
| `POST` | `/chat/ws-ticket` | 获取一次性 WebSocket Ticket |

### 会话和消息

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/chat/sessions` | 查询当前账号可见会话 |
| `GET` | `/chat/queue-status` | 查询排队状态 |
| `GET` | `/chat/agent/views` | 查询客服固定视图数量 |
| `GET` | `/chat/agent/views/{viewCode}/sessions` | 查询固定视图会话 |
| `PUT` | `/chat/sessions/{id}/metadata` | 更新标题、优先级、分类和标签 |
| `PUT` | `/chat/sessions/{id}/archive-status` | 更新归档状态 |
| `GET` | `/chat/sessions/{id}/rating` | 查询满意度评价 |
| `POST` | `/chat/sessions/{id}/rating` | 提交满意度评价 |
| `POST` | `/chat/attachments` | 上传会话附件 |
| `GET` | `/chat/attachments/{id}/content` | 下载会话附件 |

### 管理员

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/chat/admin/dashboard` | 运营概览 |
| `GET` | `/chat/admin/sessions` | 会话审计分页查询 |
| `GET` | `/chat/admin/archive-stats` | 归档统计 |
| `GET` | `/chat/admin/ratings/summary` | 满意度统计 |
| `GET` | `/users` | 用户管理列表 |
| `GET` | `/roles` | 角色列表 |
| `GET` | `/permissions` | 权限列表 |

## STOMP 消息目标

| 目标 | 说明 |
|---|---|
| `/app/chat.start` | 用户发起咨询 |
| `/app/chat.send` | 发送文本、图片或文件消息 |
| `/app/chat.history` | 加载历史消息 |
| `/app/chat.read` | 标记消息已读 |
| `/app/chat.end` | 客服结束会话 |
| `/app/chat.transfer` | 客服转接会话 |
| `/app/chat.message.edit` | 编辑消息 |
| `/app/chat.message.recall` | 撤回消息 |
| `/app/chat.heartbeat` | 刷新在线状态 |
| `/app/chat.offline.pull` | 拉取离线消息 |
| `/user/queue/chat` | 接收会话事件和消息 |
| `/user/queue/messages` | 接收已读、编辑和撤回事件 |
| `/user/queue/errors` | 接收 STOMP 错误 |

## 部署说明

- Spring Boot 后端需要可访问的 MySQL、Redis 和持久化附件目录。
- Vue 静态页面可以由 Spring Boot 统一提供，也可以独立部署。
- 仅将 Vue 页面部署到 Vercel 不会自动获得 Spring Boot API；独立前端必须配置可公网访问的后端地址及 WebSocket 地址。
- 多实例后端需要共享附件存储，或将本地附件实现替换为对象存储。
- 生产环境应设置明确的 `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS`，并使用 HTTPS/WSS。

## 常见问题

### 8080 端口被占用

说明已有 Java 进程或其他服务正在监听 8080。先确认正在运行的后端是否就是当前项目，不要重复启动第二个实例。

### 修改 Vue 后页面没有变化

使用 `localhost:8080/frontend/...` 时，浏览器访问的是 Spring Boot 中的构建产物。重新执行 `npm run build` 并让 Maven 复制最新产物，或直接使用 `npm run dev` 访问 5173 开发服务器。

### 页面显示已登录但接口持续返回 401

退出当前账号并重新登录。当前前端会在收到 401 时清理失效 Token、聊天状态和旧 WebSocket 连接。

### Vercel 页面能够打开但 API 返回 405 或连接失败

Vercel 只托管前端静态资源，Spring Boot 后端仍需要部署到独立的公网服务，并在前端配置正确的 HTTP 和 WebSocket 地址。
