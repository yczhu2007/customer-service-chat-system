# 已读回执增强 —— 实施交接方案

> 交接给实施 Agent。本文档包含全部必要的代码现状、实施步骤与项目约束，无需再向我追问背景。

## 一、任务目标

客服聊天系统的已读回执链路已部分实现，本任务补齐三个真实缺口：

1. **刷新后已读标记丢失**（核心）：已读状态只通过 WebSocket 事件传递，页面刷新后"已读"标记全部消失，需通过"已读水位线"在加载历史时恢复。
2. **没有已读时间**：事件和查询结果均无 `readAt`，无法展示"对方已于 xx 阅读"。
3. **"未读"状态不可见**：发出但未读的消息无任何标记，用户分不清"对方没读"和"功能坏了"。

## 二、项目背景与硬约束

- 项目：`E:\springboot\demo2\customer-service-chat-system`，Maven 多模块（application / businessModel / commonModel）+ Vue3 前端（frontend/）。
- **分层红线（导师要求，必须遵守）**：控制器只鉴权+委托，禁止直接注入/使用 Mapper、Repository、SimpMessagingTemplate；业务逻辑一律放 businessModel 的 service 层。
- **禁止硬编码**：阈值、TTL、窗口等一律进 `application.yml`（带环境变量回退），业务层用 `Math.max(1L, ...)` 之类的下限保护。
- **Git 提交信息用中文**。
- 后端测试命令（Windows 下必须带这两个参数，否则 fork 崩溃或找不到类）：
  ```
  ./mvnw.cmd test -pl application -am -Dtest='...' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -DargLine="-Djdk.net.URLClassPath.disableClassPathURLCheck=true"
  ```
- 前端测试：`node node_modules/vitest/vitest.mjs run src/__tests__/<file>.spec.js`（工作目录 frontend/）。
- 已有 WebSocket 推送的异常处理模式：事务提交后（afterCommit）推送，推送失败只记日志不抛出。新代码必须沿用。

## 三、代码现状（已核实，可直接依赖）

### 后端（已存在，不要重写）

- `businessModel/.../service/impl/ChatMessageManagementService.java`
  - `markMessagesRead(sessionId, lastReadMessageId, readerId)`：锚点批量已读（`chatMessageReadMapper.markReadThrough`），事务提交后已通过 `messagingTemplate.convertAndSendToUser(counterpartId, "/queue/messages", result)` 推送 `MESSAGES_READ` 事件。
  - `getHistory(...)`：游标分页加载历史，返回 `ChatHistoryPage`。
- `commonModel/.../dto/MessageReadResult.java`：record，字段 `(event, sessionId, readerId, lastReadMessageId, markedCount, unreadCount)`，event 固定 `"MESSAGES_READ"`。
- `commonModel/.../dto/ChatHistoryPage.java`：record，字段 `(records, total, pageSize, nextCursor, hasMore, unreadCount)`。
- `commonModel/.../domain/ChatMessage.java`：**没有任何已读相关字段**（这是缺口 1 的根源）。
- `ChatMessageReadMapper`：已有 `markReadThrough`、`countUnread`。

### 前端（已存在，不要重写）

- `frontend/src/stores/chat.js` `_handleMessageEvent`：已处理 `MESSAGES_READ`，按 `lastReadMessageId` 锚点给消息打 `read`（对方发给我的）/ `readByPeer`（我发的）标记。
- `frontend/src/components/chat/MessageList.vue` 第 431 行附近：`<span v-if="isMine(msg) && msg.readByPeer" class="message-state">已读</span>`。

## 四、实施步骤

### 步骤 1：已读水位线恢复（核心，半天）

**后端**

1. `ChatMessageReadMapper` 新增查询：`findLastReadMessageId(String sessionId, String readerId)`，返回对方在该会话中已读到的最新消息 ID（`chat_message_read` 表中已读记录按消息创建时间倒序取第一条；表结构先读 `commonModel/src/main/resources/mapper/` 下对应 XML 确认字段名再写 SQL）。
2. `ChatHistoryPage` record 增加字段 `counterpartLastReadMessageId`（String）、`counterpartLastReadMessageCreateTime`（LocalDateTime）和 `counterpartLastReadAt`（LocalDateTime，步骤 2 用）。注意这是 record，改构造签名会影响所有 `new ChatHistoryPage(...)` 调用点，全局搜索一并改。
3. `getHistory` 组装结果时：取对方 ID（`readerId` 的对端），调 `findLastReadMessageId` 查出对方水位线，填入 `ChatHistoryPage`。

**前端**

4. `stores/chat.js` 加载历史（调 getHistory 的 action）完成后：遍历返回的消息，按 `counterpartLastReadMessageCreateTime + counterpartLastReadMessageId` 复合水位线判断，且 `senderId === 当前用户` → `readByPeer: true`。`counterpartLastReadAt` 仅用于展示阅读时间，不能用于判断消息先后。

### 步骤 2：已读时间戳（半小时）

1. `MessageReadResult` record 增加 `readAt`（LocalDateTime）；`completed(...)` 工厂方法签名同步扩展，`markMessagesRead` 里传入 `markReadThrough` 使用的同一个 `LocalDateTime.now()` 实例，保证库内时间与事件时间一致。
2. 步骤 1 的水位线查询同时返回水位线消息创建时间和已读时间，`ChatHistoryPage` 中对应字段已预留。
3. 前端 `MessageList.vue` 的"已读" `<span>` 加 `:title` 绑定：`对方已于 ${formatDateTime(msg.peerReadAt)} 阅读`。用原生 title，不做弹层组件。

### 步骤 3："未读"状态渲染（1 小时）

- `MessageList.vue`：只在**当前用户发送的最后一条已持久化消息**上显示状态标签——`readByPeer` 为 true 显示"已读"，否则显示灰色"未读"；其余消息一律不显示（避免历史消息里一堆"未读"造成视觉噪音，对齐微信的克制做法）。
- 样式复用现有 `.message-state`，"未读"用灰色（`var(--color-muted)` 或等价变量）。

### 步骤 4：测试（半天）

- 后端：`ChatMessageServiceTest`（或新建 `ChatMessageManagementServiceTest` 用例）覆盖：水位线查询返回正确锚点、无已读记录时返回 null 而非抛错、`getHistory` 响应包含水位线字段、`readAt` 与落库时间一致。
- 前端：在 `user-workspace.spec.js`（或对应 spec）覆盖：加载历史后水位线之前的我方消息带 `readByPeer`、收到 `MESSAGES_READ` 后最后一条消息状态从"未读"变"已读"、悬停 title 内容。
- 全量回归：相关后端测试 + 前端 `user-workspace.spec.js`、`agent-workspace.spec.js`（如存在）。

## 五、明确不做（防止过度设计）

- 不做逐条消息已读回执表/接口——一对一会话用水位线模型足够。
- 不做离线补推已读事件——水位线水合天然覆盖离线场景。
- 不开放新的 REST 端点——全部数据挂在现有 `getHistory` 响应和 `MESSAGES_READ` 事件上。
- 不改数据库表结构——水位线从 `chat_message_read` 表现有数据查出。
- 不"顺手"重构任何相邻代码（CLAUDE.md 约束：surgical changes）。

## 六、验收标准

1. 发消息 → 对方标记已读 → 我方刷新页面 → "已读"标记仍在。
2. 收到 MESSAGES_READ 事件 → 最后一条我方消息从"未读"实时变"已读"。
3. 悬停"已读"显示对方阅读时间。
4. 历史消息中只有最后一条我方消息显示状态标签。
5. 后端、前端测试全部通过；控制器无任何数据层引用（导师红线）。
