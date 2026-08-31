# Customer Service Chat

一个面向小型人工客服场景的实时聊天系统。项目围绕“用户咨询 → 排队分配 → 客服接待 → 会话处理 → 评价与工单跟进”构建，并提供管理员维护入口。

## 功能概览

### 用户端

- 注册、登录、发起咨询与查看排队状态。
- 与客服实时聊天，发送图片和普通文件，查看历史会话。
- 查看会话关联工单；确认问题已解决，或申请继续处理。
- 会话结束后提交满意度评价。

### 客服端

- 上下线、接待分配会话，按会话状态处理咨询。
- 搜索聊天记录、使用快捷回复、转接会话。
- 维护会话优先级、分类、标签与归档信息。
- 为本人负责的会话创建和更新轻量工单，并查看工单流转记录。

### 管理中心

- 用户、角色和权限管理，VIP 技能组维护。
- 会话管理、会话元数据查看、聊天消息搜索、归档与评价统计、死信处理。
- 工单信息查看与筛选：可按工单号、标题、问题描述或处理结果搜索；可只查看有关联工单的会话；可定位关联会话。

## 工单说明

工单附属于会话，而不是独立的大型模块：一个会话最多关联一张工单。客服可填写标题、问题描述、处理状态和处理结果；系统会记录创建、状态变更、用户确认解决、申请继续处理等历史，也会展示会话转接带来的负责人变化。

## 技术栈与目录

- 后端：Java 17、Spring Boot、MyBatis-Plus、Apache Shiro、STOMP WebSocket。
- 前端：Vue 3、Vite、Pinia、Vue Router、Element Plus。
- 基础设施：MySQL、Redis、MinIO。

```text
application/       Spring Boot 启动模块、Controller 与配置
businessModel/     业务服务层
commonModel/       实体、DTO、Mapper 与 MyBatis XML
third-party-libs/  第三方集成相关模块
frontend/          Vue 3 前端
sql/               建表、种子与升级脚本
compose.yml        Redis 与 MinIO 的 Docker Compose 配置
```

## 运行环境

- JDK 17
- Maven 3.9 或更高版本
- Node.js 20 或更高版本与 npm
- MySQL 8（或兼容版本）
- Docker Desktop（用于 Redis、MinIO）

项目当前的 `compose.yml` 只管理 Redis 和 MinIO；MySQL 仍按应用配置连接到已有实例。若后续将 MySQL 容器化，建议先导出原数据库，再导入 Docker 命名卷中的 MySQL，避免丢失现有数据。

## 首次安装与配置

### 1. 初始化 Redis 与 MinIO

在项目根目录创建一个本地 `.env` 文件（不要提交到 Git），填入 MinIO 的本地凭据：

```dotenv
MINIO_ACCESS_KEY=your-minio-access-key
MINIO_SECRET_KEY=your-minio-secret-key
```

启动基础设施：

```powershell
docker compose up -d redis minio
docker compose ps
```

MinIO API 地址为 `http://127.0.0.1:9000`，控制台为 `http://127.0.0.1:9001`。

为避免 Windows 保留 6379 端口导致 Redis 无法启动，本项目将主机端口映射为 `16379`：

```powershell
redis-cli -h 127.0.0.1 -p 16379 ping
```

返回 `PONG` 表示 Redis 可用。运行后端时请设置 `REDIS_PORT=16379`；Docker 容器内部仍使用 Redis 标准端口 `6379`。

### 2. 初始化 MySQL

创建数据库后执行基础建表脚本：

```sql
CREATE DATABASE springboot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后在该数据库中执行：

```text
sql/chat_ddl.sql
```

对于已存在的旧数据库，按实际版本按顺序执行 `sql/` 中对应的升级脚本。当前脚本包括用户昵称、消息回复、会话元数据、工单、工单用户反馈和角色种子等升级内容。升级前请先备份数据库；同一升级脚本只应在确认可重复执行后再次运行。

### 3. 配置并启动后端

后端默认从环境变量读取数据库、Redis 和 MinIO 配置。以下是 PowerShell 示例，所有密码均使用你自己的本地值：

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/springboot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME = 'your-mysql-user'
$env:DB_PASSWORD = 'your-mysql-password'
$env:REDIS_PORT = '16379'
$env:MINIO_ENDPOINT = 'http://127.0.0.1:9000'
$env:MINIO_ACCESS_KEY = 'your-minio-access-key'
$env:MINIO_SECRET_KEY = 'your-minio-secret-key'

mvn -pl application -am spring-boot:run
```

也可以在 IDE 中运行 `application` 模块的 Spring Boot 启动类，并在运行配置中设置相同的环境变量。

### 4. 配置并启动前端

```powershell
Set-Location frontend
npm install
npm run dev
```

前端开发服务器启动后的实际访问地址以 Vite 控制台输出为准。生产构建命令：

```powershell
npm run build
```

## 数据库升级

- 全新环境：执行 `sql/chat_ddl.sql`，并根据需要执行 `user_ddl.sql`、`rbac_ddl.sql` 与示例种子脚本。
- 已部署环境：只执行尚未应用的 `*_upgrade.sql`，例如工单基础结构使用 `support_ticket_upgrade.sql`，用户确认/继续处理与工单历史补充使用 `support_ticket_user_feedback_upgrade.sql`。
- 每次升级前执行备份；应用启动成功后再验证相关页面和接口。

## 验证与构建

在项目根目录执行后端测试：

```powershell
mvn test
```

在前端目录执行前端测试和构建：

```powershell
npm test -- --run
npm run build
```

打包后端：

```powershell
Set-Location ..
mvn -pl application -am package
```

## 常见问题

### Redis 容器提示 6379 端口不可用

Windows 可能将 `6379` 划入系统保留端口范围。保持 `compose.yml` 中的 `127.0.0.1:16379:6379` 映射，并为后端设置 `REDIS_PORT=16379`。不要把宿主机端口改回 `6379`，除非已确认该端口可绑定。

### Docker 显示 Redis 已启动，但后端连不上

检查端口映射是否显示为 `127.0.0.1:16379->6379/tcp`，再执行：

```powershell
redis-cli -h 127.0.0.1 -p 16379 ping
```

如果没有返回 `PONG`，使用 `docker compose logs redis` 查看容器日志。

### 附件文件存放在哪里

聊天附件由 MinIO 保存，业务数据库只保留文件元数据和访问关联信息。导出或交付源码时，不会自动包含 MinIO 中的实际附件文件；如需演示数据，请单独备份相应 Bucket。

## 源码交付与安全

请不要提交真实的数据库密码、MinIO 凭据、`.env` 文件、上传附件或数据库备份。推荐从已提交的 Git 版本导出源码包：

```powershell
git archive --format=zip --output=customer-service-chat-system-source.zip HEAD
```

该命令只导出当前提交中的受版本控制源码，不会包含 `node_modules`、`target`、本地配置、数据库数据或 MinIO 附件。

## 项目范围

本项目聚焦人工客服的核心闭环：咨询、排队、接待、会话处理、评价、管理和轻量工单。它适合作为实时 Web 应用、权限控制与前后端协作的实践项目，而不是完整商业客服系统的替代品。
