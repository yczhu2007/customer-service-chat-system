# 第一阶段系统监控设计

## 目标与范围

本阶段为人工客服聊天系统补充实时运行指标，覆盖以下五类数据：

1. Redis 等待队列长度。
2. 消息从进入待落库队列到 MySQL 成功落库的延迟。
3. 消息持久化死信堆积量。
4. Redis 会话操作锁的一次非阻塞获取耗时及获取失败数。
5. WebSocket 在线连接数，按用户和客服角色分别统计。

本阶段还会在管理员报表页面展示当前监控快照。指标只保留服务进程运行期间的实时累计值；不新增数据库表、不保存历史趋势，也不新增告警机制。

## 架构和权限

- `application` 模块引入 `spring-boot-starter-actuator` 与 `micrometer-registry-prometheus`；`businessModel` 仅引入 `micrometer-core`，使业务层可以采集指标而不依赖 Web 层。
- `application.yml` 只暴露 `health`、`info`、`prometheus` actuator 端点。
- `/actuator/prometheus` 不加入 Shiro 匿名白名单，仍需现有 Token 认证，避免匿名读取运行指标。
- 新增管理员接口 `GET /chat/admin/monitoring`。它要求 `ADMIN` 角色和既有 `chat:admin:dashboard:view` 权限。
- 控制器只进行鉴权和服务委托；指标、Redis 读取与快照组装由业务/基础设施层完成。

## 指标模型

Prometheus 指标不携带用户 ID、会话 ID、消息 ID 等高基数标签。

| 指标类别 | 采集方式 | 展示口径 |
| --- | --- | --- |
| 排队长度 | `QUEUE_PENDING` ZSet 的基数 Gauge | 当前等待用户数 |
| 消息落库延迟 | `markPending` 的入队时间到成功持久化完成的 Timer | 样本数、平均值、P95、最大值 |
| 死信堆积 | `PERSIST_DEAD_LETTER` ZSet 的基数 Gauge | 当前死信数 |
| Redis 会话锁 | `acquireSessionOperationLock` 的 Timer 和成功/失败 Counter | 样本数、平均值、P95、失败次数 |
| WebSocket 在线数 | 会话 ID 去重后维护的 `AtomicInteger` Gauge | 用户连接数、客服连接数 |

消息落库 Timer 仅在已经存在待持久化时间戳且最终成功写入 MySQL 时记录。无样本时，快照中的延迟值为 `null`，不以 `0 ms` 误导管理员。

会话锁保持现有的一次 `SET NX` 非阻塞语义，不引入等待重试。`ChatRoutingSessionSupport` 的同类锁会统一委托 `ChatRedisRepository.acquireSessionOperationLock`，保证会话操作锁不会漏统计。

WebSocket 指标统计的是连接数而非去重用户数。连接与断开均按 STOMP session ID 幂等处理，防止重复断开事件把计数降为负数。仅 `USER` 与 `AGENT` 角色分别计入对应 Gauge。

Redis Gauge 在 Prometheus 抓取时读取 Redis；Redis 不可用时输出 `NaN`。管理端快照遇到 Redis 访问失败时，只将队列和死信项标记为“暂不可用”，不把它们伪装成零值，也不影响本地 Timer、Counter 与在线数的返回。

## 管理端接口与页面

`GET /chat/admin/monitoring` 返回一次实时快照，包含：

- `queueLength`、`deadLetterBacklog` 及 Redis 可用状态；
- 用户与客服 WebSocket 在线连接数；
- 消息落库延迟摘要：样本数、平均毫秒、P95 毫秒、最大毫秒；
- 会话锁摘要：样本数、平均毫秒、P95 毫秒、失败次数；
- `collectedAt` 采集时间。

管理员“报表”页面增加“系统监控”区块。它不受报表日期筛选影响，页面挂载时立即加载，并每 15 秒静默刷新；保留手动刷新按钮。自动刷新失败时保留最后一次成功数据并显示简短状态，不弹出重复通知。组件卸载时清理定时器。

## Prometheus 配置

为两个延迟 Timer 开启 percentile histogram 并计算 P95，使 Prometheus 可采集分布桶，管理员快照也可读取本地 P95。指标自服务启动后累计，重启会重置计时器、计数器和 WebSocket 连接数。

## 验收与测试

1. 已认证请求可以读取 `/actuator/prometheus`，匿名请求不能读取；输出包含五类监控指标。
2. 管理员可读取 `/chat/admin/monitoring`；无管理员权限的请求被拒绝。
3. 管理端初次加载、手动刷新、15 秒自动刷新与组件卸载清理均有前端测试。
4. WebSocket 同一会话重复断开不会使在线数为负。
5. 消息成功持久化、会话锁获取成功和失败均会更新对应指标。
6. Redis 不可用时快照保留其他可采集项目，并明确标识 Redis 项不可用。
7. 运行后端 Maven 测试、前端 Vitest 与生产构建验证。
