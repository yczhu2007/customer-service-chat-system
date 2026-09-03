# Chat Boundary Refactor Design

## Goal

在不改变任何 HTTP 路径、STOMP 目的地、响应结构或 Vue 组件调用方式的前提下，缩小 `ChatController` 和 `useChatStore()` 的职责范围，使连接、会话和客服工作台逻辑能够独立阅读和测试。

## Constraints

- 不修改 `application/src/main/resources/static/stomp-test.html`、其 Shiro 放行规则或手工回归清单。
- 保持 `/chat/**` REST 路径、`/app/chat.*` STOMP 目的地、`/user/queue/*` 订阅地址不变。
- 保持组件继续从 `useChatStore()` 读取既有 state、getter 和 action；不要求修改 Vue 组件调用方。
- 不新增依赖、数据库字段、REST 接口或 WebSocket 协议字段。
- 可靠性修复与结构拆分分开提交和验证。

## Backend Boundary

`ChatController` 的职责按传输入口拆分，保留同一个 `/chat` 路径前缀：

- `ChatAuthenticationController`：登录、登出和 WebSocket 票据。
- `ChatQueueAgentController`：取消排队、客服上下线、VIP 技能组和死信管理。
- `ChatSessionController`：当前用户会话、评分、资料、归档、队列状态等 HTTP 会话操作。
- `ChatStompController`：咨询开始、输入状态、发消息、结束/转接、历史、离线消息、ACK、已读、撤回和心跳。

每个控制器只注入所在入口实际使用的服务。STOMP 角色和权限校验保留在 `ChatStompController` 私有方法中，避免为了单一调用方引入共享抽象。现有控制器测试按入口迁移，断言仍以现有可观察行为为准。

## Frontend Boundary

`frontend/src/stores/chat.js` 保持为兼容门面：保留现有 store 名称、state、getter 和 action 名称。内部按下列模块拆分：

- `stores/chat-connection.js`：取得票据、建立/断开 STOMP、重连计时器和心跳；通过回调通知门面更新 store 状态与处理消息帧。
- `stores/chat-session-actions.js`：会话列表、选择、历史消息、排队和消息读取相关 action。
- `stores/chat-agent-actions.js`：客服固定视图、工单、资料、转接记录和客服概况 action。

模块不直接创建 Pinia store，也不导入 `useChatStore()`，由门面在 action 调用时提供现有 `this` 上下文。消息帧分发和跨域状态协调继续保留在门面，防止循环依赖和组件 API 变化。

## Error Handling and Verification

- 连接模块继续使用统一 `request()`，保留 15 秒超时、401 清理和显式断开时取消请求的行为。
- 控制器拆分后，每个 HTTP/STOMP 路由仍执行相同的当前用户、角色和权限检查。
- 为拆分后的控制器入口和连接模块保留或迁移现有单测；新增测试只覆盖模块边界和兼容行为。
- 每个拆分步骤分别运行对应后端/前端测试，最后运行 `mvnw.cmd test`、`npm.cmd test -- --run` 和 `npm.cmd run build:sync`。

## Non-Goals

- 不删除旧测试页，也不处理其令牌泄露风险。
- 不更改消息落库、Redis 数据结构、会话分配算法或页面视觉设计。
- 不在本次中拆分 `ChatMessageDeliveryService`、`RoleServiceImpl` 等其他大型服务。
