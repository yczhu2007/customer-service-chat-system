# MinIO 附件存储与历史附件迁移设计

## 1. 背景与目标

当前聊天图片和文件保存在应用服务器本地的 `data/chat-attachments/`，数据库表 `chat_attachment` 保存附件元数据。该方式依赖单机文件系统，不利于独立管理、容器化部署和后续扩容。

本次改造目标：

- 使用 MinIO 私有 Bucket 统一保存聊天图片和文件。
- 保持 Vue3 前端及现有上传、下载 URL 不变。
- 所有上传和下载继续经过 Spring Boot 的身份认证与会话参与者权限校验。
- Spring Boot 启动时强制检查 MinIO；连接或安全配置异常时拒绝启动。
- 提供显式触发的一次性迁移工具，将历史本地附件完整迁移到 MinIO。
- 只有全部历史文件上传、回读校验和数据库更新成功后，才统一删除本地附件目录。
- 迁移完成后不再保留本地存储运行模式。

本次不实现前端直传、公开 Bucket、预签名下载、本地与 MinIO 双存储切换，也不在管理员页面增加迁移按钮。

## 2. 已确认决策

- MinIO 由项目提供的 Docker Compose 启动。
- Bucket 必须保持私有。
- 前端继续访问 `/chat/attachments` 和 `/chat/attachments/{id}/content`。
- 历史迁移通过 `ATTACHMENT_MIGRATION_ENABLED=true` 显式触发，正常启动不会自动迁移。
- 迁移成功后统一删除整个本地附件目录；任一环节失败时保留整个目录。
- MinIO 不可连接、凭据错误或 Bucket 不满足安全要求时，Spring Boot 启动失败。
- 使用 Maven Central 中的 `io.minio:minio:9.0.3`，版本在父工程统一管理。

## 3. 总体架构

附件访问链路保持为：

```text
Vue3
  -> Spring Boot 附件接口
  -> 登录身份与会话参与者权限校验
  -> chat_attachment 元数据
  -> MinIO 私有 Bucket
```

后端组件职责：

### 3.1 ChatAttachmentServiceImpl

- 校验上传人是会话用户或当前客服。
- 校验文件非空、大小、扩展名、魔数和 ZIP/Office 压缩包安全。
- 生成附件 ID、对象名和数据库元数据。
- 协调 MinIO 上传与数据库事务。
- 数据库写入或事务提交失败时删除刚上传的对象。
- 下载前校验附件存在且当前用户有权访问。

### 3.2 MinioAttachmentStorage

该组件直接封装 MinIO Java SDK，不增加本地存储实现，也不暴露给 Controller：

- 检查和创建 Bucket。
- 上传对象并设置可信的服务端 MIME 类型。
- 获取对象元数据和输入流。
- 删除单个或批量对象。
- 分页列举 Bucket 对象供孤儿清理使用。
- 将 SDK、网络和服务端异常转换成不泄露凭据、地址或内部堆栈的应用异常。

对象名采用确定性格式：

```text
sessions/{sessionId}/{attachmentId}.{extension}
```

数据库字段 `chat_attachment.stored_name` 保留，含义改为 MinIO object key，无需新增存储类型字段。

### 3.3 MinioAttachmentInitializer

在应用对外提供服务前执行：

- 校验 endpoint、access key、secret key 和 bucket 配置非空且格式合法。
- 连接 MinIO 并检查 Bucket。
- Bucket 不存在时创建。
- Bucket 已存在时检查其匿名访问策略；发现公开访问时拒绝启动，不自动修改既有策略。
- 普通启动发现旧附件目录或迁移待删除目录仍包含文件时拒绝启动，并提示先执行或重试迁移模式。

迁移模式允许旧目录存在，但仍要求 MinIO 可用。

### 3.4 AttachmentMigrationRunner

仅在 `app.chat.attachment.migration.enabled=true` 时创建并执行。迁移完成或失败后返回明确退出码并结束应用，不启动正常聊天服务。推荐命令同时设置 `spring.main.web-application-type=none`，避免占用 8080 端口。

## 4. 正常上传与事务补偿

正常上传顺序：

1. 查询会话并校验参与者。
2. 执行现有附件安全校验。
3. 生成附件 ID 和 MinIO object key。
4. 将附件上传到私有 Bucket。
5. 插入 `chat_attachment` 记录。
6. 事务提交后返回原有 `ChatAttachmentVO`，其中 `contentUrl` 仍为后端地址。

MinIO 不参与 MySQL 事务，因此使用补偿机制保持一致性：

- SDK 上传失败：不写数据库。
- 数据库插入失败：立即尝试删除 MinIO 对象，并保留原始异常。
- Spring 事务最终回滚：通过事务同步回调删除 MinIO 对象。
- 补偿删除失败：记录附件 ID、object key 和可追踪错误，但日志不得包含 secret key；后续孤儿清理负责再次删除。

## 5. 正常下载与权限

下载顺序：

1. 根据附件 ID 查询 `chat_attachment`。
2. 查询所属会话，并校验当前用户是用户或客服参与者。
3. 使用 `stored_name` 从 MinIO 获取对象流。
4. 按数据库中的可信 `content_type`、`file_size`、`original_name` 和 `message_type` 生成响应。
5. 图片使用 inline，普通文件使用 attachment；继续发送 `X-Content-Type-Options: nosniff`。
6. 响应完成或客户端中断时关闭 MinIO 输入流。

Bucket 不公开，前端不会获得 MinIO 凭据、内部 endpoint 或对象直链。

## 6. 历史附件一次性迁移

### 6.1 前置检查

- 仅在显式迁移开关为 `true` 时运行。
- 校验本地旧目录为规范化后的预期目录，禁止根目录、工作区根目录和不明确路径。
- 检查 MinIO 连接、私有 Bucket、MySQL 连接和 `chat_attachment` 表。
- 读取全部附件记录，为每条记录计算目标 object key。
- 本地目录存在未被数据库引用的文件时列入报告；它们不进入业务附件迁移，但目录删除前必须明确处理。为避免数据丢失，只要存在这类文件，本轮迁移失败并保留整个目录。

### 6.2 上传和校验

对每条附件记录：

1. 首次迁移以旧 `stored_name` 定位本地文件；数据库已切换但目录删除失败后的重试，则根据附件 ID 和原文件扩展名推导旧文件名。两种情况都必须校验解析后的路径仍位于旧目录内。
2. 校验文件存在、为普通文件，并与数据库 `file_size` 一致。
3. 计算本地文件 SHA-256。
4. 上传到确定性 object key；对象已存在时先检查，内容一致则复用，不一致则覆盖后重新校验。
5. 从 MinIO 回读对象，重新计算 SHA-256，与本地文件比较。
6. 记录附件 ID、旧文件名、目标 object key、大小、校验值和结果。

迁移按固定批次顺序执行，限制并发和内存占用。单个附件当前最大 10 MB，校验仍采用流式计算，不一次性载入全部历史文件。

### 6.3 数据库切换与删除

- 只有所有文件上传和回读校验成功后，才在一个数据库事务中把全部 `stored_name` 更新为目标 object key。
- 数据库事务提交后，再根据数据库记录对 MinIO 对象执行一次完整存在性、大小和 SHA-256 校验。
- 最终校验全部通过后，解析并再次确认本地目录的绝对路径，将目录原子重命名为同级的迁移待删除目录，再递归删除待删除目录。这样删除过程失败时仍有明确、可恢复的剩余目录。
- 删除完成后输出迁移报告。
- 上传、校验或数据库切换任一步骤失败时不移动、不删除本地目录，并返回非零退出码。目录清理失败时保留迁移待删除目录并返回非零退出码，下次迁移运行只需重新校验 MinIO 与数据库后继续清理。
- 已上传对象使用确定性 object key，下一次运行可校验后复用，因此迁移可幂等重试。

若数据库事务失败，MinIO 中已上传对象可以暂时保留；旧数据库记录和本地文件仍然完整，重试迁移时复用这些对象。

## 7. 孤儿对象清理

现有本地目录扫描改为 MinIO 对象扫描：

- 分页列举业务前缀 `sessions/` 下的对象。
- 分批查询数据库中被引用的 `stored_name`。
- 仅删除数据库未引用且最后修改时间超过安全宽限期的对象。
- 默认宽限期 1 小时，可通过配置调整。
- 不扫描或删除其他系统前缀。
- 保留现有分布式调度锁，避免多个应用实例同时执行清理。

## 8. 配置与 Docker Compose

Spring Boot 配置：

```yaml
app:
  chat:
    attachment:
      minio:
        endpoint: ${MINIO_ENDPOINT:http://127.0.0.1:9000}
        access-key: ${MINIO_ACCESS_KEY}
        secret-key: ${MINIO_SECRET_KEY}
        bucket: ${MINIO_BUCKET:chat-attachments}
      orphan-grace-period-seconds: ${CHAT_ATTACHMENT_ORPHAN_GRACE_PERIOD_SECONDS:3600}
      migration:
        enabled: ${ATTACHMENT_MIGRATION_ENABLED:false}
        legacy-storage-path: ${CHAT_ATTACHMENT_LEGACY_STORAGE_PATH:./data/chat-attachments}
```

项目提供：

- 根目录 `compose.yml`，包含固定版本的 MinIO 镜像、API/Console 端口、持久化 Volume 和健康检查。
- `.env.example`，包含仅用于本地演示的示例变量说明。
- `.env` 加入 `.gitignore`，真实凭据不提交 Git。
- MinIO API 默认映射 `9000`，Console 默认映射 `9001`。

生产环境不得使用示例凭据，且 endpoint 应使用受信任网络或 HTTPS。

## 9. 错误处理与日志

- 启动失败信息指出连接失败、凭据错误、Bucket 缺失/创建失败或公开策略，但不输出 secret key。
- 上传失败统一返回用户可理解的“附件存储暂不可用”或现有校验错误。
- 下载对象不存在返回附件不存在，不暴露 Bucket 名和 object key。
- 迁移报告允许记录附件 ID、原文件名、object key 和失败阶段，不记录认证凭据。
- 迁移失败退出码非零，成功退出码为零。

## 10. 测试策略

### 10.1 单元测试

- 保留并适配现有扩展名、魔数、ZIP/Office 校验测试。
- 上传成功写入正确 object key 和可信 MIME 类型。
- MinIO 上传失败时不写数据库。
- 数据库失败或事务回滚时触发对象删除补偿。
- 下载前执行会话参与者权限校验。
- SDK 异常经过脱敏转换。
- 孤儿清理跳过数据库引用对象、系统前缀和宽限期内对象。

### 10.2 迁移测试

- 全部文件成功时更新数据库并删除本地目录。
- 文件缺失、大小不符、SHA-256 不符、存在未引用文件或数据库更新失败时保留整个目录。
- 中断后重试可复用内容一致的对象。
- 重复执行不会重复产生对象或重复减少数据。
- 非法旧目录路径被拒绝。

### 10.3 真实集成测试

沿用项目的环境变量门控策略：只有 `RUN_REAL_INTEGRATION_TESTS=true` 时，才连接 Docker Compose 中的真实 MySQL、Redis 和 MinIO。覆盖：

- Bucket 初始化与私有访问。
- 真实文件上传、stat、流式下载和删除。
- HTTP 上传、授权下载和越权拒绝。
- 一次完整历史迁移及迁移后下载。
- MinIO 停止时应用启动失败。

默认 Maven 测试不要求 Docker，保证日常开发可快速运行。

## 11. 验收流程

1. 根据 `.env.example` 创建本地 `.env`。
2. 使用 Docker Compose 启动 MinIO并等待健康检查通过。
3. 在 MinIO Console 中确认私有 Bucket 已创建。
4. 使用迁移开关和非 Web 模式执行一次性迁移命令。
5. 确认迁移成功报告、数据库 object key、本地附件目录已删除。
6. 正常启动 Spring Boot。
7. 用户发送图片和普通文件，客服可预览或下载。
8. 客服发送附件，用户可预览或下载。
9. 非会话参与者请求附件内容时被拒绝。
10. 在 MinIO Console 中确认对象位于 `sessions/{sessionId}/` 前缀下。
11. 停止 MinIO 后重新启动 Spring Boot，验证启动被明确阻止。

## 12. 实施边界

本次实施只修改附件存储与迁移链路，不改变消息协议、会话模型、前端视觉、附件大小上限、允许扩展名或现有 RBAC 规则。迁移删除本地目录属于显式迁移命令的一部分，正常应用启动和定时清理均不得删除该目录。
