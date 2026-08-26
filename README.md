# Customer Service Chat

一个基于 Spring Boot、Vue 3、MySQL、Redis 和 STOMP 的轻量人工客服聊天项目。系统提供用户咨询、客服接待、会话管理、附件、评价、归档和管理员管理功能；不包含 AI 自动回复或企业级工单平台能力。

## 环境

- JDK 17
- MySQL 8
- Redis
- Node.js 20 或以上
- Docker Desktop（仅附件使用 MinIO 时需要）

## 初始化与启动

1. 创建数据库后，依次执行 `sql/user_ddl.sql`、`sql/rbac_ddl.sql`、`sql/chat_ddl.sql`。
2. 已有数据库需要继续执行：`sql/user_nickname_upgrade.sql`、`sql/chat_session_metadata_upgrade.sql`、`sql/chat_message_reply_upgrade.sql`、`sql/support_ticket_upgrade.sql`。
3. 如需使用附件存储，复制 `.env.example` 为 `.env`，填写 MinIO 配置后执行 `docker compose up -d`。
4. 前端开发：进入 `frontend` 后执行 `npm.cmd install` 和 `npm.cmd run dev`。
5. 整体打包：进入 `frontend` 后执行 `npm.cmd run build:sync`，再在项目根目录执行 `./mvnw.cmd spring-boot:run`。

默认后端地址为 `http://localhost:8080`，打包后的 Vue 前端通过 `/frontend/login` 访问。

## 会话关联工单

每条会话最多关联一张轻量工单。客服可为自己负责的进行中或已结束会话创建工单，并更新状态、问题描述和处理结果；用户和会话负责人可查看，管理员本阶段只读查看。工单状态在前端显示为“待处理、处理中、等待用户、已解决”，后端保存 `OPEN`、`IN_PROGRESS`、`WAITING_USER`、`RESOLVED`。

接口如下：

- `GET /chat/sessions/{sessionId}/ticket`：查看会话工单；未创建时返回空数据。
- `POST /chat/sessions/{sessionId}/ticket`：当前负责客服创建工单。
- `PATCH /chat/tickets/{ticketNo}`：当前负责客服更新工单，需携带版本号。

演示步骤：创建一条已分配的会话；使用负责客服在右侧“关联工单”创建工单；让用户打开同一会话确认只读卡片；客服更新为“处理中”，再填写处理结果后更新为“已解决”；重新打开会话确认工单仍存在。

## 验证

```powershell
./mvnw.cmd "-Dmaven.repo.local=E:\springboot\demo2\customer-service-chat-system\.m2\repository" -o test
Set-Location frontend
npm.cmd test -- --run
npm.cmd run build:sync
```
