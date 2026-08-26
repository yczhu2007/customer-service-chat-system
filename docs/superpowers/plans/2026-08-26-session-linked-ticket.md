# 会话关联轻量工单 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为一条客服会话提供一张可持续跟踪、可实时同步的轻量工单。

**Architecture:** 工单只存放处理生命周期字段，标题、优先级、分类、用户和客服均从 `chat_session` 及用户表读取。MySQL 的唯一约束和乐观锁负责并发；提交后的 STOMP 通知仅告知双方重新请求详情。前端把当前工单状态放入现有 Pinia 聊天 Store，并在两个工作台使用独立的客服编辑面板和用户只读卡片。

**Tech Stack:** Spring Boot 4、MyBatis-Plus、MySQL 8、Spring STOMP、Vue 3、Pinia、Element Plus、Vitest、JUnit 5、Mockito。

**Spec:** `docs/superpowers/specs/2026-08-26-session-linked-ticket-design.md`

## Global Constraints

- 一条会话最多一张工单；不增加管理员工单中心、SLA、外部渠道、独立附件、自动分派、合并或物理删除。
- 工单状态只使用 `OPEN`、`IN_PROGRESS`、`WAITING_USER`、`RESOLVED`；接口和数据库保存英文，前端展示中文。
- 工单编号由数据库主键格式化为 `TK-%08d`；不保存第二个编号字段。
- 访问控制只使用认证后的内部用户 ID；用户和客服的参与关系由 `chat_session` 判断。
- STOMP 复用 `/user/queue/chat`；为兼容现有客户端，事件字段使用项目现有的 `event`，值为 `TICKET_CREATED` 或 `TICKET_UPDATED`，并仅附带 `sessionId`、`ticketNo`、`version`。
- 工单不写 Redis；数据库提交成功后才发送通知，通知异常只记录脱敏日志，不回滚工单。
- 不新增依赖；继续使用 MyBatis-Plus、现有 `Result`、现有异常处理和 Element Plus。

---

### Task 1: 工单表与传输对象

**Files:**
- Create: `sql/support_ticket_upgrade.sql`
- Modify: `sql/chat_ddl.sql`
- Create: `commonModel/src/main/java/com/example/customerservice/domain/SupportTicket.java`
- Create: `commonModel/src/main/java/com/example/customerservice/constant/SupportTicketStatus.java`
- Create: `commonModel/src/main/java/com/example/customerservice/mapper/SupportTicketMapper.java`
- Create: `commonModel/src/main/java/com/example/customerservice/dto/SupportTicketCreateDTO.java`
- Create: `commonModel/src/main/java/com/example/customerservice/dto/SupportTicketUpdateDTO.java`
- Create: `commonModel/src/main/java/com/example/customerservice/dto/SupportTicketVO.java`
- Modify: `application/src/test/java/com/example/customerservice/dto/TransportObjectSerializationTest.java`
- Test: `application/src/test/java/com/example/customerservice/service/SupportTicketServiceTest.java`

**Interfaces:**
- Produces `SupportTicket` with `Long id`, `String sessionId`, `String status`, `String description`, `String resolution`, `Integer version`, `LocalDateTime createdAt`, `updatedAt`, `resolvedAt`.
- Produces `SupportTicketCreateDTO.description` with `@NotBlank` and `@Size(max = 1000)`.
- Produces `SupportTicketUpdateDTO.status`, `description`, `resolution`, `version`; `version` uses `@NotNull @Min(0)` and text fields are capped at 1000 characters.
- Produces `SupportTicketVO.ticketNo`, `sessionId`, `status`, `description`, `resolution`, `version`, timestamps, plus read-only session title/priority/category and agent nickname.

- [ ] **Step 1: Write the failing transport test**

Add `SupportTicketCreateDTO.class`, `SupportTicketUpdateDTO.class`, and `SupportTicketVO.class` to `allTransportTypesImplementSerializable()`. Add a service test assertion that `SupportTicketStatus.RESOLVED.canTransitionTo(SupportTicketStatus.IN_PROGRESS)` is true and `OPEN.canTransitionTo(OPEN)` is false.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw.cmd "-Dmaven.repo.local=E:\springboot\demo2\customer-service-chat-system\.m2\repository" -o -pl application -am test`

Expected: compilation fails because the ticket DTOs and status enum do not exist.

- [ ] **Step 3: Add the smallest data model and SQL**

Create `support_ticket` with `BIGINT AUTO_INCREMENT` primary key, unique `session_id`, `status`, `description`, nullable `resolution` and `resolved_at`, optimistic `version`, and microsecond timestamps. Include a status check constraint and a foreign key to `chat_session` with `ON DELETE RESTRICT`. Add the same `CREATE TABLE IF NOT EXISTS` block to full-install `chat_ddl.sql`.

Implement the status transition method with the exact map below:

```java
public boolean canTransitionTo(SupportTicketStatus target) {
    return switch (this) {
        case OPEN -> target == IN_PROGRESS || target == WAITING_USER || target == RESOLVED;
        case IN_PROGRESS -> target == WAITING_USER || target == RESOLVED;
        case WAITING_USER -> target == IN_PROGRESS || target == RESOLVED;
        case RESOLVED -> target == IN_PROGRESS;
    };
}
```

Define `SupportTicketMapper extends BaseMapper<SupportTicket>` only; conditional updates will use a MyBatis-Plus `LambdaUpdateWrapper`, so no custom SQL mapper method is needed.

- [ ] **Step 4: Run the transport test to verify it passes**

Run: `./mvnw.cmd "-Dmaven.repo.local=E:\springboot\demo2\customer-service-chat-system\.m2\repository" -o -pl application -am test`

Expected: all application tests pass and the new DTOs serialize.

- [ ] **Step 5: Commit the data contract**

```powershell
git add sql/support_ticket_upgrade.sql sql/chat_ddl.sql commonModel/src/main/java/com/example/customerservice application/src/test/java/com/example/customerservice/dto/TransportObjectSerializationTest.java
git commit -m "功能：新增会话关联工单数据模型"
```

### Task 2: 工单服务、权限与并发控制

**Files:**
- Create: `businessModel/src/main/java/com/example/customerservice/service/SupportTicketService.java`
- Test: `application/src/test/java/com/example/customerservice/service/SupportTicketServiceTest.java`

**Interfaces:**
- Consumes `SupportTicketMapper`, `ChatSessionMapper`, `SysUserMapper`, `SimpMessagingTemplate` and `CurrentUser`-derived caller ID/roles.
- Produces `SupportTicketVO findBySessionId(String sessionId, String callerId, Set<String> roleCodes)`; returns `null` for an authorized session with no ticket.
- Produces `SupportTicketVO create(String sessionId, SupportTicketCreateDTO request, String callerId)` and `SupportTicketVO update(String ticketNo, SupportTicketUpdateDTO request, String callerId)`.

- [ ] **Step 1: Write failing service tests**

Create mocked `SupportTicketMapper`, `ChatSessionMapper`, `SysUserMapper`, and `SimpMessagingTemplate`. Add tests for:

```java
assertThrows(AccessDeniedException.class,
        () -> service.create("S1", create("支付失败"), "OTHER_AGENT"));
assertNull(service.findBySessionId("S1", "USER_1", Set.of("USER")));
assertThrows(IllegalArgumentException.class,
        () -> service.update("TK-00000001", update("RESOLVED", "描述", null, 0), "AGENT_1"));
assertThrows(ConcurrentModificationException.class,
        () -> service.update("TK-00000001", update("IN_PROGRESS", "描述", null, 0), "AGENT_1"));
```

Also verify a successful `RESOLVED` update stores `resolvedAt`, and an `IN_PROGRESS` update after `RESOLVED` clears it.

- [ ] **Step 2: Run the service test to verify it fails**

Run: `./mvnw.cmd "-Dmaven.repo.local=E:\springboot\demo2\customer-service-chat-system\.m2\repository" -o -pl application -am "-Dtest=SupportTicketServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: compilation fails because `SupportTicketService` does not exist.

- [ ] **Step 3: Implement the minimal service**

Use `chatSessionMapper.selectById(sessionId)` for all relation checks. Allow read when caller is the session user, session agent, or has `ADMIN`; allow create/update only when caller equals nonblank `session.agentId`. Format and parse ticket numbers only at the service edge:

```java
private String ticketNo(long id) { return "TK-%08d".formatted(id); }
private long ticketId(String ticketNo) { /* require TK- plus exactly eight digits */ }
```

For create, insert one row in a transaction and translate `DuplicateKeyException` from the unique session constraint into the project conflict exception. For update, reject unknown status and disallowed transitions; require nonblank resolution for `RESOLVED`; then update with:

```java
ticketMapper.update(nextTicket,
    Wrappers.<SupportTicket>lambdaUpdate()
        .eq(SupportTicket::getId, ticket.getId())
        .eq(SupportTicket::getVersion, request.getVersion()));
```

Treat an affected-row count other than one as a conflict with message `工单已被其他操作修改，请刷新后重试`.

Build the VO by joining in memory with the already-loaded `ChatSession` and the assigned agent's nickname. Do not duplicate these session fields in `support_ticket`.

- [ ] **Step 4: Run service tests to verify them pass**

Run: `./mvnw.cmd "-Dmaven.repo.local=E:\springboot\demo2\customer-service-chat-system\.m2\repository" -o -pl application -am "-Dtest=SupportTicketServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: the create, relation, transition, resolution, and version-conflict cases pass.

- [ ] **Step 5: Commit the service**

```powershell
git add businessModel/src/main/java/com/example/customerservice/service/SupportTicketService.java application/src/test/java/com/example/customerservice/service/SupportTicketServiceTest.java
git commit -m "功能：实现工单权限和并发控制"
```

### Task 3: HTTP 接口与提交后实时通知

**Files:**
- Create: `application/src/main/java/com/example/customerservice/controller/SupportTicketController.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/SupportTicketService.java`
- Test: `application/src/test/java/com/example/customerservice/controller/SupportTicketControllerTest.java`
- Test: `application/src/test/java/com/example/customerservice/service/SupportTicketServiceTest.java`

**Interfaces:**
- Produces `GET /chat/sessions/{sessionId}/ticket` → `Result<SupportTicketVO>` with nullable `data`.
- Produces `POST /chat/sessions/{sessionId}/ticket` → `Result<SupportTicketVO>`.
- Produces `PATCH /chat/tickets/{ticketNo}` → `Result<SupportTicketVO>`.
- Emits `/user/queue/chat` body `{ event, sessionId, ticketNo, version }` to the session user and assigned agent only after commit.

- [ ] **Step 1: Write failing HTTP and notification tests**

Use `MockMvc` with mocked `SupportTicketService` and `CurrentUser` to assert the three routes, request validation, and caller ID forwarding. In the service test, activate Spring transaction synchronization, call create/update, assert `SimpMessagingTemplate` has no interactions before `afterCommit()`, then invoke registered synchronization `afterCommit()` and verify two `convertAndSendToUser` calls.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw.cmd "-Dmaven.repo.local=E:\springboot\demo2\customer-service-chat-system\.m2\repository" -o -pl application -am "-Dtest=SupportTicketControllerTest,SupportTicketServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: route resolution fails because the controller and notification callback do not exist.

- [ ] **Step 3: Add the controller and callback**

Put `@RequestMapping("/chat")` on the dedicated controller. Use `@Valid`, `@NotBlank`, `@Size(max = 64)` for path values; use `currentUser.getUserId()` and `currentUser.getRoleCodes()` rather than any request-provided participant ID.

In `SupportTicketService`, copy the existing `TransactionSynchronizationManager` pattern from `ChatMessageManagementService.afterCommit`. The callback catches `RuntimeException`, logs only ticket number and session ID, and sends two small maps through `SimpMessagingTemplate`.

- [ ] **Step 4: Run HTTP and notification tests to verify they pass**

Run: `./mvnw.cmd "-Dmaven.repo.local=E:\springboot\demo2\customer-service-chat-system\.m2\repository" -o -pl application -am "-Dtest=SupportTicketControllerTest,SupportTicketServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: HTTP validation, caller identity forwarding, post-commit ordering, and two-party event delivery pass.

- [ ] **Step 5: Commit the transport layer**

```powershell
git add application/src/main/java/com/example/customerservice/controller/SupportTicketController.java businessModel/src/main/java/com/example/customerservice/service/SupportTicketService.java application/src/test/java/com/example/customerservice/controller/SupportTicketControllerTest.java application/src/test/java/com/example/customerservice/service/SupportTicketServiceTest.java
git commit -m "功能：提供工单接口和实时通知"
```

### Task 4: 前端接口、Store 和实时刷新

**Files:**
- Modify: `frontend/src/api/chat-api.js`
- Modify: `frontend/src/stores/chat.js`
- Create: `frontend/src/__tests__/session-ticket.spec.js`

**Interfaces:**
- Produces `getSessionTicket(sessionId)`, `createSessionTicket(sessionId, data)`, and `updateSessionTicket(ticketNo, data)`.
- Produces store state `activeTicket`, `ticketLoading`, `ticketError`, `_ticketRequestSequence`.
- Produces store actions `loadSessionTicket(sessionId)`, `createSessionTicket(sessionId, data)`, `updateSessionTicket(ticketNo, data)`.

- [ ] **Step 1: Write failing Store tests**

Mock ticket API requests and assert:

```js
await chat.selectSession('S2')
expect(chat.activeTicket).toBeNull()
// Resolve S1 after S2: stale response must not replace S2 ticket.
expect(chat.activeTicket?.sessionId).not.toBe('S1')
```

Add a STOMP event test: `TICKET_UPDATED` for the active session calls `loadSessionTicket`, while an event for a different session does not change `activeTicket` or message composer state. Add rejection assertions for a 409 conflict message and 403 permission message.

- [ ] **Step 2: Run the frontend test to verify it fails**

Run: `npm.cmd test -- --run src/__tests__/session-ticket.spec.js`

Expected: failure because ticket API functions and Store state do not exist.

- [ ] **Step 3: Add the API and Store behavior**

Implement REST calls with the project `request()` wrapper. At the start of `selectSession`, increment `_ticketRequestSequence`, set `activeTicket` to `null`, and clear `ticketError`; request the ticket after the new `activeSessionId` is set. Only apply a response when both its request sequence and session ID still match the active session.

Handle `TICKET_CREATED` and `TICKET_UPDATED` at the start of `_handleChatEvent`:

```js
if ((event === 'TICKET_CREATED' || event === 'TICKET_UPDATED')
    && body.sessionId === this.activeSessionId) {
  this.loadSessionTicket(body.sessionId)
  return
}
```

Do not reload sessions, history, or user profiles for ticket events.

- [ ] **Step 4: Run the frontend test to verify it passes**

Run: `npm.cmd test -- --run src/__tests__/session-ticket.spec.js`

Expected: stale-response protection, current-session refresh, and HTTP error presentation pass.

- [ ] **Step 5: Commit the ticket state layer**

```powershell
git add frontend/src/api/chat-api.js frontend/src/stores/chat.js frontend/src/__tests__/session-ticket.spec.js
git commit -m "功能：增加工单前端状态和实时刷新"
```

### Task 5: 客服编辑面板与用户只读卡片

**Files:**
- Create: `frontend/src/components/session/SessionTicketPanel.vue`
- Create: `frontend/src/components/session/UserSessionTicketCard.vue`
- Modify: `frontend/src/views/AgentWorkspaceView.vue`
- Modify: `frontend/src/views/UserWorkspaceView.vue`
- Modify: `frontend/src/constants/session-ui.js`
- Modify: `frontend/src/__tests__/session-ticket.spec.js`

**Interfaces:**
- `SessionTicketPanel` uses `chat.activeTicket` and invokes `chat.createSessionTicket` or `chat.updateSessionTicket`.
- `UserSessionTicketCard` accepts no mutating props and contains no update action.
- `ticketStatusLabel(status)` maps `OPEN`、`IN_PROGRESS`、`WAITING_USER`、`RESOLVED` to `待处理`、`处理中`、`等待用户`、`已解决`.

- [ ] **Step 1: Write failing component tests**

Mount the agent panel with no ticket and assert it shows one description input and `创建工单`. Mount it with a ticket and assert the ticket number is read-only, the agent can save status/description/resolution, and `RESOLVED` without resolution shows `处理结果不能为空`.

Mount the user card with the same ticket and assert Chinese status, title, priority, category, assigned-agent nickname, description, resolution, and updated time are visible; assert neither `创建工单` nor `保存` exists.

- [ ] **Step 2: Run the frontend test to verify it fails**

Run: `npm.cmd test -- --run src/__tests__/session-ticket.spec.js`

Expected: component imports fail because the two ticket components do not exist.

- [ ] **Step 3: Implement the two small views**

Place `SessionTicketPanel` directly below `SessionMetadataEditor` in the agent right sidebar. It displays only `该会话暂未创建工单` and a creation form when no ticket exists; after creation it displays `TK-` number, timestamps and the edit form. Disable all fields unless `activeSession.agentId === auth.userId`.

Place `UserSessionTicketCard` below `ChatWindow` and above `SessionRatingForm` in the user workspace. With no ticket it displays only `该会话暂未创建工单`; it must never render an input, select, or save button. Reuse existing priority/category mappings and Element Plus alert, input, select, button, and tag styling; do not add a UI library.

- [ ] **Step 4: Run component tests to verify they pass**

Run: `npm.cmd test -- --run src/__tests__/session-ticket.spec.js`

Expected: the agent creation/edit paths and user read-only view pass.

- [ ] **Step 5: Commit the visible ticket feature**

```powershell
git add frontend/src/components/session/SessionTicketPanel.vue frontend/src/components/session/UserSessionTicketCard.vue frontend/src/views/AgentWorkspaceView.vue frontend/src/views/UserWorkspaceView.vue frontend/src/constants/session-ui.js frontend/src/__tests__/session-ticket.spec.js
git commit -m "功能：增加客服和用户工单视图"
```

### Task 6: 全量回归、安装说明和演示验证

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-26-session-linked-ticket-design.md` only if implementation reveals a necessary protocol correction
- Test: `application/src/test/java/com/example/customerservice/service/SupportTicketServiceTest.java`
- Test: `frontend/src/__tests__/session-ticket.spec.js`

**Interfaces:**
- Consumes all prior task interfaces.
- Produces documented SQL execution order and six-step manual demonstration path.

- [ ] **Step 1: Add the final missing regression cases**

Add a duplicate-create test that simulates `DuplicateKeyException`, a closed-session create success test, and a read-permission matrix test for session user, assigned agent, admin, unrelated user, and unrelated agent. Add frontend tests that a 409 shows `工单已被其他操作修改，请刷新后重试` and a 403 keeps the existing ticket visible while showing the error.

- [ ] **Step 2: Run the targeted regression tests**

Run:

```powershell
./mvnw.cmd "-Dmaven.repo.local=E:\springboot\demo2\customer-service-chat-system\.m2\repository" -o test
```

```powershell
npm.cmd test -- --run
```

Expected: all backend and frontend tests pass with the new ticket cases included.

- [ ] **Step 3: Update operational documentation**

In `README.md`, add `sql/support_ticket_upgrade.sql` after the existing chat-session metadata migration, list the three REST endpoints, explain fixed one-ticket-per-session behavior, and provide the documented six-step demo without exposing internal IDs or SQL details to end users.

- [ ] **Step 4: Build production frontend and package backend**

Run:

```powershell
npm.cmd run build:sync
./mvnw.cmd "-Dmaven.repo.local=E:\springboot\demo2\customer-service-chat-system\.m2\repository" -o -DskipTests package
git diff --check
```

Expected: frontend static files are synchronized, Spring Boot JAR is packaged, and diff check reports no whitespace errors.

- [ ] **Step 5: Perform the manual two-party demonstration**

Use an assigned user and agent session. Create a ticket as the assigned agent, confirm the user sees the same `TK-` number, update to `处理中`, resolve with a result, close the chat, reopen it, and confirm ticket persistence. Verify a nonparticipant receives 403 for the GET endpoint and a duplicate create returns 409.

- [ ] **Step 6: Commit final documentation and verification changes**

```powershell
git add README.md docs/superpowers/specs/2026-08-26-session-linked-ticket-design.md application/src/test/java/com/example/customerservice/service/SupportTicketServiceTest.java frontend/src/__tests__/session-ticket.spec.js
git commit -m "文档：补充会话关联工单说明"
```
