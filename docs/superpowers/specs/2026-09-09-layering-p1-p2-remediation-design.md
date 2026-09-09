# P1/P2 分层与鲁棒性整改设计

## 目标与范围

本次只修复已审查确认的 P1/P2：入口层越过业务层访问数据或基础设施、STOMP 控制器间委托、空输入被转成字符串 `"null"`、定时批处理被单项失败阻塞、业务实体泄漏到附件控制器、工单工作台角色判断重复，以及重复的运行参数与状态常量。

不重写整个服务层，不调整接口 URL、STOMP destination、Redis key 名称或数据库结构；不补监控趋势图。

## 边界与数据流

### STOMP

`ChatStompController` 保留为唯一标注 `@MessageMapping` 的入口。原 `ChatController` 改为 `ChatStompApplicationService`，移至业务模块，删除 Spring 消息路由注解。它仅处理协议 DTO 校验、当前 Principal 到业务调用的转换及响应 DTO 组装；不依赖 Mapper、Redis 或 `SimpMessagingTemplate`。

`typing` 改用 `TypingRequest(sessionId, typing)` DTO，使用 Bean Validation 校验 `sessionId` 非空且长度不超过 64。这样缺失字段被拒绝，不会被 `String.valueOf(null)` 转成 `"null"`。

### 定时任务与 Redis

Scheduler 仅保留 `@Scheduled`、分布式调度锁和一次服务调用。Redis 扫描、Lua 执行、队列超时判断、在线客服读取以及 STOMP 通知下沉为现有业务服务的方法；对 Redis 的访问继续集中到 `ChatRedisRepository`。

每项批处理在业务服务内独立捕获异常并记录完整堆栈；失败项不会阻止本批后续成员处理。失败项不删除，保留给下一轮重试。每轮仍使用现有 100 条上限，避免扩大单次负载。

### WebSocket 身份验证

握手与 STOMP 拦截器只解析 HTTP/STOMP 协议并委托认证服务。认证服务提供：验证已启用用户、查询角色、验证聊天订阅权限。Mapper 不再注入 application 的 interceptor。

### 工单、工作台与附件

将 `X-Workspace-Role` 的解析和“当前用户是否拥有该角色”校验放进复用的 `WorkspaceRoleResolver`。控制器只传入请求头值和允许角色；`ChatSessionController` 与 `SupportTicketController` 共用该规则。

`SupportTicketController` 调用一个 `submitUserFeedback` 服务方法，服务根据动作执行确认解决或要求继续处理。控制器不再决定业务分支。

附件业务服务返回 `ChatAttachmentVO` 和下载描述对象（文件名、Content-Type、长度、Resource）。控制器不再接收 `ChatAttachment` 实体，也不再自行组装可见元数据。

### 常量与生命周期

新增小型 `RoleCodes`、`PermissionCodes` 常量类，仅收拢当前重复使用的角色和权限字面量。现有业务状态统一从其唯一归属常量读取；删除 `RedisConstants` 中重复的会话状态和心跳毫秒常量，毫秒由秒值换算。

调度周期、单批上限等运行参数统一保留在 `application.yml` 的既有 `app.chat` 配置下。Redis 键仍留在 `RedisConstants`。

`ChatRedisRepository`、`DistributedSchedulerLock` 与 `MessagePersistServiceImpl` 自建的 watchdog executor 在 Bean 销毁时关闭；不改为新的线程池框架。

## 验收标准

1. Controller、Scheduler、WebSocket interceptor 不直接注入 Mapper 或 `StringRedisTemplate`；Scheduler 也不直接注入 `SimpMessagingTemplate`。
2. 仅 `ChatStompController` 声明聊天 STOMP destination；无 controller-to-controller 注入。
3. 缺失或 null 的 typing `sessionId` 被拒绝，且不会进入 presence service。
4. 一项心跳、重连宽限或队列超时处理抛异常时，同批后续成员仍会被处理。
5. 附件 controller 不引用 `ChatAttachment` 实体；工单 controller 不按 `CONFIRM` 选择业务方法。
6. 新增或更新的单元测试覆盖上述边界；后端全量测试、前端全量测试与生产构建通过。

## 风险控制

保留现有 URL、消息目的地和 Redis 数据格式以避免前端或线上缓存兼容性问题。Scheduler 下沉前先补边界测试；每项迁移后只运行受影响测试，再运行完整验证。若 watchdog 关闭影响测试上下文，使用 `@PreDestroy` 在 Spring 容器生命周期内关闭，不使用静态全局清理逻辑。
