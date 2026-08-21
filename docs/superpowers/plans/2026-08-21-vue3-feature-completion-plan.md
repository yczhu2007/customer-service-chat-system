# Vue 3 Feature Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 补齐 Vue 3 相对于 `stomp-test.html` 的认证、消息操作、工作台统计、审计、转接记录和 WebSocket 管理能力。

**Architecture:** 继续使用 Vue 3、Pinia、现有 REST API 和 STOMP 协议。认证页面按路由拆分，聊天操作集中在 `chat` store 与聊天组件，工作台统计和审计使用现有 API 页面组件；不修改后端接口路径和数据库结构。

**Tech Stack:** Vue 3, Pinia, Vue Router, Vitest, Spring Boot REST/STOMP APIs.

**Spec:** `docs/superpowers/specs/2026-08-21-vue3-feature-completion-design.md`

## Global Constraints

- 不新增 UI 框架。
- 不修改 `stomp-test.html`。
- 不改变现有 REST 路径、STOMP 目的地和数据库结构。
- 每个任务先增加回归测试，再修改实现。
- 页面空数据必须显示业务说明，不显示原始接口路径。

---

### Task 1: 认证与账号页面

**Files:**
- Modify: `frontend/src/api/auth-api.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/RegisterView.vue`
- Create: `frontend/src/views/ForgotPasswordView.vue`
- Create: `frontend/src/views/AccountView.vue`
- Modify: `frontend/src/components/common/AppShell.vue`
- Test: `frontend/src/__tests__/auth-api.spec.js`, `frontend/src/__tests__/router.spec.js`

- [ ] 为注册、忘记密码、重置密码、用户名修改、密码修改和恢复码生成补充 API 测试。
- [ ] 读取后端认证控制器的实际路径和请求字段，补齐 `auth-api.js`。
- [ ] 增加公开认证路由和登录页入口，登录后按角色返回工作台。
- [ ] 实现表单校验、提交状态、错误显示和成功跳转。
- [ ] 在 AppShell 增加账号入口和退出登录入口。
- [ ] 运行认证和路由测试。

### Task 2: 历史消息加载更多

**Files:**
- Modify: `frontend/src/stores/chat.js`
- Modify: `frontend/src/components/chat/MessageList.vue`
- Test: `frontend/src/__tests__/user-workspace.spec.js`, `frontend/src/__tests__/agent-workspace.spec.js`

- [ ] 增加 `historyHasMore`、`historyCursor` 和 `historyLoadingMore` 状态测试。
- [ ] 实现 `loadMoreHistory(sessionId)`，使用历史响应的 `nextCursor`。
- [ ] 保持消息时间顺序和去重逻辑，避免切换会话后串入旧消息。
- [ ] 在消息列表顶部增加“加载更早消息”按钮和加载失败提示。
- [ ] 运行用户、客服消息测试。

### Task 3: 消息选择、编辑、撤回和状态

**Files:**
- Modify: `frontend/src/stores/chat.js`
- Modify: `frontend/src/components/chat/MessageList.vue`
- Modify: `frontend/src/components/chat/MessageComposer.vue`
- Modify: `frontend/src/components/chat/ChatWindow.vue`
- Modify: `frontend/src/components/chat/AgentChatWindow.vue`
- Test: `frontend/src/__tests__/user-workspace.spec.js`, `frontend/src/__tests__/agent-workspace.spec.js`

- [ ] 为自己的文本消息增加选择测试，为他人消息和附件消息增加不可编辑/撤回测试。
- [ ] 实现发送 `/app/chat.message.edit` 和 `/app/chat.message.recall` 的 store 方法。
- [ ] 处理 `MESSAGE_EDITED`、`MESSAGE_RECALLED`、`MESSAGES_READ` 和 ACK 状态更新。
- [ ] 在消息气泡上增加编辑、撤回、已保存、已送达和已读状态。
- [ ] 增加编辑输入状态、提交状态和错误提示。
- [ ] 运行消息交互测试。

### Task 4: WebSocket 连接管理

**Files:**
- Modify: `frontend/src/services/stomp-client.js`
- Modify: `frontend/src/stores/chat.js`
- Modify: `frontend/src/views/UserWorkspaceView.vue`
- Modify: `frontend/src/views/AgentWorkspaceView.vue`
- Create: `frontend/src/components/common/ConnectionStatus.vue`
- Test: `frontend/src/__tests__/user-workspace.spec.js`, `frontend/src/__tests__/agent-workspace.spec.js`

- [ ] 测试连接状态、错误队列和手动重连行为。
- [ ] 为 STOMP 客户端增加 `onDisconnect`、`onError` 和活动时间回调。
- [ ] 订阅 `/user/queue/errors`，统一写入可读错误状态。
- [ ] 实现最多 5 次递增等待自动重连，重复连接先释放旧客户端。
- [ ] 增加连接/断开/重连按钮和状态面板。
- [ ] 运行 WebSocket 相关测试。

### Task 5: 客服概况和转接记录

**Files:**
- Modify: `frontend/src/api/chat-api.js`
- Modify: `frontend/src/views/AgentWorkspaceView.vue`
- Create: `frontend/src/components/agent/AgentOverviewPanel.vue`
- Create: `frontend/src/components/session/TransferLogPanel.vue`
- Modify: `frontend/src/components/chat/AgentChatWindow.vue`
- Test: `frontend/src/__tests__/agent-workspace.spec.js`

- [ ] 增加客服概况和转接记录 API 的测试。
- [ ] 调用现有客服 Dashboard、评分摘要和转接记录接口。
- [ ] 展示排队数、处理会话、今日关闭数、客服负载和满意度摘要。
- [ ] 转接成功后刷新固定视图与转接日志。
- [ ] 在当前会话区域展示来源客服、目标客服、时间和结果。
- [ ] 运行客服工作台测试。

### Task 6: 管理员审计增强

**Files:**
- Modify: `frontend/src/components/admin/SessionAuditPanel.vue`
- Modify: `frontend/src/components/admin/AdminDashboard.vue`
- Create: `frontend/src/components/admin/AuditTransferLog.vue`
- Modify: `frontend/src/api/admin-api.js`
- Test: `frontend/src/__tests__/admin-workspace.spec.js`

- [ ] 增加归档状态筛选和请求参数测试。
- [ ] 增加会话行展开与转接记录加载测试。
- [ ] 补充管理员满意度摘要、客服负载列表和刷新状态。
- [ ] 为死信、VIP、权限和归档统计增加明确空数据和刷新反馈。
- [ ] 运行管理员工作台测试。

### Task 7: 全量验证与启动

**Files:**
- Modify: only files required by failing verification
- Test: all frontend tests and selected backend tests

- [ ] 运行 `npm.cmd test -- --run`，修复新增功能回归。
- [ ] 运行 `npm.cmd run build`。
- [ ] 运行 `mvn.cmd package -DskipTests`。
- [ ] 重启 Spring Boot 服务并确认 8080 监听。
- [ ] 请求 `/frontend/index.html`，确认 HTTP 200。
- [ ] 通过浏览器手动验收登录、消息操作、客服概况、审计和重连流程。
