# 人工客服聊天系统

人工客服聊天系统提供用户咨询、客服接待、实时聊天、附件管理、会话评价、工单跟进和后台管理功能。

## 功能概览

- 用户：注册登录、发起咨询、实时聊天、发送附件、查看工单、提交会话评价。
- 客服：上线接待、查看历史会话、快捷回复、会话转接与归档、工单处理。
- 管理：用户与权限管理、客服状态查看、会话与工单统计、运行监控。
- 附件：支持图片、PDF、文本和常用 Office 文档；旧版 Office 文件在容器内由 LibreOffice 转换预览。

## 快速启动

### 前置条件

安装并启动 Docker Desktop。首次构建需要联网下载镜像和构建依赖。

### 1. 创建配置文件

在项目根目录执行：

```powershell
Copy-Item .env.example .env
```

打开 `.env`，将所有 `change-this-` 开头的密码替换为自己的安全密码。请不要将 `.env` 提交到代码仓库。

| 配置项 | 用途 |
| --- | --- |
| `APP_PORT` | 浏览器访问端口，默认 `8080` |
| `MYSQL_*` | MySQL 数据库名称、业务账号和密码 |
| `REDIS_PASSWORD` | Redis 访问密码 |
| `MINIO_*` | 附件存储账号、密码和桶名称 |
| `ADMIN_BOOTSTRAP_PASSWORD` | 首次创建管理员账号时使用的密码，至少 12 位 |

### 2. 启动完整系统

```powershell
docker compose up -d --build
```

查看服务状态：

```powershell
docker compose ps
```

当 `app`、`mysql`、`redis` 和 `minio` 均显示为运行或健康状态后，访问：

```text
http://localhost:8080/frontend/
```

如果修改了 `APP_PORT`，请将地址中的 `8080` 替换为实际端口。应用端口默认仅绑定到本机地址。

### 3. 首次登录

首次初始化会自动创建管理员账号：

```text
用户名：admin
密码：.env 中的 ADMIN_BOOTSTRAP_PASSWORD
```

管理员账号已存在时，修改 `ADMIN_BOOTSTRAP_PASSWORD` 不会覆盖原密码。

## 日常操作

```powershell
# 查看服务状态
docker compose ps

# 查看应用日志
docker compose logs -f app

# 重启应用服务
docker compose restart app

# 停止服务，保留数据库和附件数据
docker compose down

# 停止服务并删除全部持久化数据
docker compose down --volumes
```

`docker compose down --volumes` 会删除 MySQL、Redis 和 MinIO 中的数据，执行前请确认不再需要其中的内容。

## 数据说明

- 首次启动会自动创建 MySQL 表、Redis 数据目录和 MinIO 附件桶。
- 数据存储在 Docker volumes 中，普通的 `docker compose down` 不会删除数据。
- 迁移到其他电脑或重置环境前，请备份 MySQL 数据和 MinIO 附件数据。
- `sql/user_ddl.sql`、`sql/rbac_ddl.sql`、`sql/chat_ddl.sql` 用于创建全新数据库；已有数据库请先完成备份并核对表结构。

## 常见问题

### 服务没有启动

先查看状态和应用日志：

```powershell
docker compose ps
docker compose logs --tail 200 app
```

确认 Docker Desktop 已启动，并检查 `.env` 中的密码是否已完整填写。

### 浏览器无法访问系统

确认 `APP_PORT` 没有被其他程序占用，并使用以下地址访问：

```text
http://localhost:APP_PORT/frontend/
```

例如默认端口为 `http://localhost:8080/frontend/`。

### 需要重新初始化全部数据

```powershell
docker compose down --volumes
docker compose up -d --build
```

该操作会清空全部持久化数据。

### 附件无法预览

先检查应用和 MinIO 服务是否正常：

```powershell
docker compose ps
docker compose logs --tail 200 app
```

预览失败时仍可下载原始附件。

## 项目结构

```text
application/       后端启动模块和接口入口
businessModel/     业务服务
commonModel/       数据模型、DTO 和数据访问定义
frontend/          Vue 3 前端
sql/               全新数据库初始化脚本
Dockerfile         应用镜像构建文件
compose.yml        应用、MySQL、Redis 和 MinIO 编排配置
.env.example       环境变量模板
```
