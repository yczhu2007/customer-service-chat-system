# 工单历史与处理体验改进 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复工单处理体验，并增加状态操作历史和负责人变更展示。

**Architecture:** 工单状态历史是与工单同事务写入的追加记录；负责人变更复用已有会话转接日志。前端继续使用现有 Pinia、REST 和 Element Plus，不新增依赖。

**Tech Stack:** Spring Boot、MyBatis-Plus、MySQL 8、Vue 3、Pinia、JUnit 5、Vitest。

**Spec:** `docs/superpowers/specs/2026-08-28-ticket-history-and-workflow-design.md`

## Global Constraints

- 一会话一工单；不增加 SLA、独立分派、内部评论、附件或独立工单中心。
- 历史只记录创建和实际状态变化；描述修改不记历史。
- 转接记录只读复用，不复制到工单表。
- 每项行为先写失败测试，再写最小实现。

---

### Task 1: 编辑失败和实时草稿保护

**Files:**
- Modify: `frontend/src/services/http-client.js`
- Modify: `frontend/src/components/session/SupportTicketPanel.vue`
- Modify: `frontend/src/stores/chat.js`
- Modify: `frontend/src/__tests__/support-ticket.spec.js`

**Interfaces:** `request()` 抛出的错误有 `status`；面板有 `dirty`、`stale` 状态。

- [x] **Step 1: Write the failing tests**

```js
it('keeps the draft after a non-conflict update failure', async () => {
  chat.updateSupportTicket = vi.fn().mockRejectedValue(Object.assign(new Error('网络错误'), { status: 500 }))
  await wrapper.get('textarea').setValue('未保存内容')
  await wrapper.get('.action').trigger('click')
  expect(wrapper.get('textarea').element.value).toBe('未保存内容')
  expect(chat.loadSupportTicket).not.toHaveBeenCalled()
})
```

- [x] **Step 2: Run the test to verify it fails**

Run: `npm.cmd test -- --run src/__tests__/support-ticket.spec.js`

Expected: 当前 catch 对所有错误刷新工单。

- [x] **Step 3: Write the minimal implementation**

```js
const error = new Error(body?.message || `HTTP ${response.status}`)
error.status = response.status
throw error
```

仅在 `error.status === 409` 时刷新；其他错误保留表单。收到工单事件时，脏表单标记 `stale`，用户点击刷新后才同步。

- [x] **Step 4: Run the test to verify it passes**

Run: `npm.cmd test -- --run src/__tests__/support-ticket.spec.js`

### Task 2: 合法状态选择和工单列表结果

**Files:**
- Modify: `commonModel/src/main/java/com/example/customerservice/dto/ChatSessionListItemVO.java`
- Modify: `commonModel/src/main/resources/mapper/ChatManagementMapper.xml`
- Modify: `frontend/src/components/session/SupportTicketPanel.vue`
- Modify: `frontend/src/components/session/AgentSessionList.vue`
- Modify: `application/src/test/java/com/example/customerservice/service/AgentSessionViewTest.java`
- Modify: `frontend/src/__tests__/agent-workspace.spec.js`

**Interfaces:** 会话列表增加 `ticketNo`；工单视图按未解决优先、更新时间降序；状态选项仅为当前和合法下一状态。

- [x] **Step 1: Write the failing tests**

```java
assertTrue(sql.contains("CONCAT('TK-', LPAD(TICKET.ID, 8, '0')) AS TICKET_NO"));
assertTrue(sql.contains("TICKET.UPDATED_AT DESC"));
```

```js
expect(wrapper.text()).toContain('TK-00000125 · 处理中')
expect(statusOptions).not.toContain('OPEN')
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl application -am "-Dtest=AgentSessionViewTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Run: `npm.cmd test -- --run src/__tests__/agent-workspace.spec.js src/__tests__/support-ticket.spec.js`

- [x] **Step 3: Write the minimal implementation**

使用 `LOCATE(#{ticketKeyword}, field) > 0` 替换 `LIKE`；返回 `ticket_no`；工单视图使用：

```sql
ORDER BY CASE WHEN ticket.status = 'RESOLVED' THEN 1 ELSE 0 END,
         ticket.updated_at DESC, session.id DESC
```

前端显示编号，状态选择只显示当前状态和 `quickStatuses`。

- [x] **Step 4: Run the tests to verify they pass**

Run: 与 Step 2 相同命令。

### Task 3: 工单状态历史和查询接口

**Files:**
- Modify: `sql/chat_ddl.sql`
- Modify: `sql/support_ticket_upgrade.sql`
- Create: `commonModel/src/main/java/com/example/customerservice/domain/SupportTicketStatusHistory.java`
- Create: `commonModel/src/main/java/com/example/customerservice/dto/SupportTicketStatusHistoryVO.java`
- Create: `commonModel/src/main/java/com/example/customerservice/mapper/SupportTicketStatusHistoryMapper.java`
- Create: `commonModel/src/main/resources/mapper/SupportTicketStatusHistoryMapper.xml`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/SupportTicketService.java`
- Modify: `application/src/main/java/com/example/customerservice/controller/SupportTicketController.java`
- Modify: `application/src/test/java/com/example/customerservice/service/SupportTicketServiceTest.java`
- Modify: `application/src/test/java/com/example/customerservice/controller/SupportTicketControllerTest.java`

**Interfaces:** `findHistoryBySessionId(callerId, administrator, sessionId)` 和 `GET /chat/sessions/{sessionId}/ticket/history`。

- [x] **Step 1: Write the failing tests**

```java
verify(historyMapper).insert(argThat(history ->
  "OPEN".equals(history.getFromStatus()) && "IN_PROGRESS".equals(history.getToStatus())));
assertTrue(service.findHistoryBySessionId("U001", false, "S001").size() >= 1);
```

同时验证创建写入 `null -> OPEN`，仅描述修改不写历史，无关用户不能查询。

- [x] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl application -am "-Dtest=SupportTicketServiceTest,SupportTicketControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

- [x] **Step 3: Write the minimal implementation**

```sql
CREATE TABLE IF NOT EXISTS support_ticket_status_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ticket_id BIGINT NOT NULL,
  operator_id VARCHAR(64) NOT NULL,
  from_status VARCHAR(20) NULL,
  to_status VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_ticket_status_history_time (ticket_id, created_at, id),
  CONSTRAINT fk_ticket_status_history_ticket FOREIGN KEY (ticket_id)
    REFERENCES support_ticket (id) ON DELETE CASCADE
);
```

创建时写 `null -> OPEN`；仅状态变化后写历史；查询联表返回操作人昵称，最多 50 条，按时间升序。

- [x] **Step 4: Run the tests to verify they pass**

Run: 与 Step 2 相同命令。

### Task 4: 面板时间线和负责人变更

**Files:**
- Modify: `frontend/src/api/chat-api.js`
- Modify: `frontend/src/stores/chat.js`
- Modify: `frontend/src/components/session/SupportTicketPanel.vue`
- Modify: `frontend/src/views/AgentWorkspaceView.vue`
- Modify: `frontend/src/__tests__/support-ticket.spec.js`

**Interfaces:** `getSupportTicketHistory(sessionId)`；Store 增加当前工单历史；工单面板展示状态历史和既有 `transferLogs`。

- [x] **Step 1: Write the failing test**

```js
chat.activeSupportTicketHistory = [{ fromStatus: 'OPEN', toStatus: 'IN_PROGRESS', operatorNickname: '客服一' }]
chat.transferLogs = [{ sourceAgentNickname: '客服一', targetAgentNickname: '客服二' }]
expect(wrapper.text()).toContain('待处理 → 处理中')
expect(wrapper.text()).toContain('客服一 → 客服二')
```

- [x] **Step 2: Run the test to verify it fails**

Run: `npm.cmd test -- --run src/__tests__/support-ticket.spec.js`

- [x] **Step 3: Write the minimal implementation**

会话切换及工单事件后加载历史；存在工单时展示“操作历史”和“负责人变更”。客服工作台在已有工单时隐藏单独的转接记录面板，避免重复。

- [x] **Step 4: Run the test to verify it passes**

Run: `npm.cmd test -- --run src/__tests__/support-ticket.spec.js`

### Task 5: 真实数据库约束与交付验证

**Files:**
- Modify: `application/src/test/java/com/example/customerservice/integration/RealInfrastructureIntegrationTest.java`
- Modify: `README.md`

- [ ] **Step 1: Write the optional real-MySQL test**

```java
assertEquals(1, jdbcTemplate.update(
  "UPDATE support_ticket SET version = version + 1 WHERE id = ? AND version = ?", ticketId, 0));
assertEquals(0, jdbcTemplate.update(
  "UPDATE support_ticket SET version = version + 1 WHERE id = ? AND version = ?", ticketId, 0));
```

测试数据使用唯一前缀并在 finally 删除。

- [ ] **Step 2: Run final verification**

Run: `mvn test`

Run: `npm.cmd test -- --run`

Run: `npm.cmd run build:sync`

Run: `mvn -pl application -am -DskipTests package`

Run: `git diff --check`
