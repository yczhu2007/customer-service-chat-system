# 人工客服聊天系统

一个基于 Spring Boot 和 Vue 3 的人工客服系统，适合课程设计、毕业设计和小型客服场景演示。系统覆盖用户咨询、排队分配、客服接待、实时聊天、附件查看、会话评价、工单跟进和后台管理。

## 主要功能

### 用户端

- 注册、登录、找回密码和维护个人资料
- 发起咨询并查看排队状态
- 与客服实时聊天，查看历史消息和已读状态
- 发送图片及常用文档附件
- 撤回消息、引用回复和查看历史会话
- 查看关联工单，确认解决或申请继续处理
- 会话结束后提交满意度评价

### 客服端

- 上线、下线并接收系统分配的咨询
- 查看当前会话和历史会话
- 搜索聊天记录，使用快捷回复
- 查看用户资料，设置会话分类、优先级和标签
- 转接、结束和归档会话
- 创建、更新会话关联工单
- 查看连接状态和客服重连提示

### 管理端

- 管理用户、角色和权限
- 查看客服状态、会话和聊天记录
- 管理 VIP 客服技能组
- 查看会话、评价和工单统计
- 查看系统运行监控数据
- 处理消息持久化死信

## 附件查看

系统支持图片、PDF、TXT、Word、Excel 和 PowerPoint 等常用附件。

- 图片、PDF 和 TXT 可直接查看。
- DOCX、XLSX、PPTX 使用前端或服务端提供的预览能力。
- 旧版 DOC、XLS、PPT 由后端调用 LibreOffice 转换为 PDF 后查看。
- 预览失败时仍可下载原文件。

若需要预览旧版 Office 文件，请安装 LibreOffice，并保证 `soffice` 已加入系统 `PATH`。也可以通过环境变量指定完整路径：

```powershell
$env:CHAT_ATTACHMENT_PREVIEW_COMMAND = 'C:\Program Files\LibreOffice\program\soffice.com'
```

## 技术栈

- 后端：Java 17、Spring Boot 4、MyBatis-Plus、Apache Shiro
- 实时通信：WebSocket、STOMP
- 前端：Vue 3、Vite、Pinia、Vue Router、Element Plus
- 数据存储：MySQL、Redis、MinIO
- 文档预览：docx-preview、ExcelJS、SheetJS、LibreOffice

## 运行环境

- JDK 17
- Maven 3.9 或更高版本
- Node.js 20 或更高版本
- MySQL 8 或兼容版本
- Docker Desktop（推荐用于运行完整系统）
- LibreOffice（仅预览旧版 Office 文件时需要）

## Docker Desktop 一键启动（推荐）

导师电脑安装 Docker Desktop 后，只需要配置一次 `.env`：

```powershell
Copy-Item .env.example .env
```

然后修改 `.env` 中的数据库、Redis、MinIO 和管理员密码，至少替换所有 `change-this-` 开头的值。启动完整系统：

```powershell
docker compose up -d --build
docker compose ps
```

浏览器访问 `http://localhost:8080/frontend/`。首次启动会自动创建 MySQL 表、MinIO 附件桶和 `admin` 管理员账号；管理员密码使用 `.env` 中的 `ADMIN_BOOTSTRAP_PASSWORD`。

常用操作：

```powershell
# 查看应用日志
docker compose logs -f app

# 停止服务但保留数据
docker compose down

# 停止服务并删除数据库、Redis、MinIO 数据（谨慎使用）
docker compose down --volumes
```

数据保存在 Docker volumes 中。交接已有系统时，除源码和 `.env` 模板外，还需要单独备份并恢复 MySQL 数据和 MinIO 附件。

## 本地开发启动

以下步骤适用于不使用完整应用容器、需要在 IDE 或本机运行源码的开发场景。

### 1. 准备 MySQL

创建数据库：

```sql
CREATE DATABASE springboot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后按以下顺序执行全新安装脚本：

```text
sql/user_ddl.sql
sql/rbac_ddl.sql
sql/chat_ddl.sql
```

这些脚本面向全新数据库，使用 `CREATE TABLE IF NOT EXISTS`，不会自动升级已有表结构。
如果接入已有数据库，请先备份并人工核对表结构，再编写经过验证的 `ALTER TABLE` 变更；不要把全新安装脚本直接当作升级脚本执行。

### 2. 准备 Redis 和 MinIO

本分支的 `compose.yml` 只向宿主机发布应用端口，Redis 和 MinIO 仅在 Docker 内部网络中使用；源码在宿主机运行时，请改用本机或其他开发环境提供的 Redis、MinIO，并据此填写连接地址。完整容器化运行请直接使用上面的 Docker Desktop 方式。

本地运行后端时，环境变量示例：

```powershell
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '6379'
$env:MINIO_ENDPOINT = 'http://127.0.0.1:9000'
```

### 3. 启动后端

开发环境 PowerShell 示例：

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/springboot?serverTimezone=Asia/Shanghai&characterEncoding=UTF-8'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '你的 MySQL 密码'
$env:REDIS_PORT = '6379'
$env:MINIO_ENDPOINT = 'http://127.0.0.1:9000'
$env:MINIO_ACCESS_KEY = 'minioadmin'
$env:MINIO_SECRET_KEY = '与 .env 相同的密码'

.\mvnw.cmd -pl application -am spring-boot:run -Dspring-boot.run.profiles=dev
```

也可以在 IDE 中运行：

```text
com.example.customerservice.CustomerServiceChatApplication
```

IDE 启动时请启用 `dev` Profile，并配置相同的环境变量。

### 4. 启动前端

```powershell
Set-Location frontend
npm install
npm run dev
```

浏览器访问 Vite 控制台显示的地址，默认后端代理目标为 `http://localhost:8080`。

## 使用入口

登录后系统会根据当前账号角色进入对应工作台：

- 用户工作台：`/user`
- 客服工作台：`/agent`
- 管理工作台：`/admin`
- 个人资料：`/account`

一个账号拥有多个角色时，可以在系统允许的工作台之间切换。

## 基本使用流程

1. 用户注册并登录，进入用户工作台发起咨询。
2. 客服登录并上线，系统按排队情况分配会话。
3. 双方通过 WebSocket 实时聊天并发送附件。
4. 客服结束会话，按需要创建或更新关联工单。
5. 用户确认处理结果或申请继续处理，并提交会话评价。
6. 管理员在后台查看会话、工单、评价和运行状态。

## 测试与打包

后端测试：

```powershell
.\mvnw.cmd -pl application -am test
```

前端测试和构建：

```powershell
Set-Location frontend
npm test -- --run
npm run build
```

后端打包：

```powershell
Set-Location ..
.\mvnw.cmd -pl application -am package
```

## 常见问题

### 后端无法连接 Redis

使用完整容器化方式时，应用会通过 Compose 服务名 `redis` 连接 Redis；请先检查：

```powershell
docker compose ps
```

源码在宿主机运行时，请确认外部 Redis 地址和端口与 `REDIS_HOST`、`REDIS_PORT` 配置一致。

### 附件上传成功但无法预览

先确认 MinIO 正常运行、后端使用的账号密码与 `.env` 一致。旧版 DOC、XLS、PPT 还需要 LibreOffice。预览转换可能需要等待几秒，失败后可以下载原文件。

### 无法进入客服或管理工作台

工作台受角色和权限控制。请使用管理员账号为用户分配对应角色和权限，然后重新登录。

### 前端页面无法调用后端

开发环境默认代理到 `http://localhost:8080`。如果后端使用其他地址，可在启动前设置：

```powershell
$env:VITE_BACKEND_URL = 'http://127.0.0.1:8080'
npm run dev
```

## 项目目录

```text
application/       后端启动模块和接口入口
businessModel/     业务服务
commonModel/       数据模型、DTO 和数据访问定义
frontend/          Vue 3 前端
sql/               数据库脚本
compose.yml        应用、MySQL、Redis 和 MinIO 容器编排
```
