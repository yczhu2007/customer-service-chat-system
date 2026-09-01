# 鲁棒性基础修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让应用只在显式环境下启动、请求可超时、会话删除可恢复，并限制 MinIO 的本机网络暴露。

**Architecture:** 将开发默认配置放入 `dev` Profile，启动时拒绝未选择 `dev` 或 `prod` 的运行环境。会话删除使用数据库逻辑删除字段，并沿现有管理端查询、接口和页面传递 `deleted` 筛选；前端复用唯一 HTTP 入口实现超时。基础设施仅改 Compose 端口绑定。

**Tech Stack:** Spring Boot 4、MyBatis-Plus、MySQL 8、Vue 3、Vite、Vitest、Docker Compose。

**Spec:** `docs/superpowers/specs/2026-09-01-robustness-foundations-design.md`

## Global Constraints

- 不增加第三方依赖，不引入 Flyway、MySQL 容器或 CI。
- 升级 SQL 必须可重复执行，不能删除既有业务数据。
- 删除会话只能逻辑删除，恢复后必须保留消息、工单、评价和附件。
- 默认 HTTP 超时固定为 15 秒，调用方可覆盖。
- MinIO 两个端口只能绑定 `127.0.0.1`。

---

### Task 1: 显式 Spring Profile

**Files:**
- Create: `application/src/main/resources/application-dev.yml`
- Create: `application/src/main/java/com/example/customerservice/config/RuntimeProfileValidator.java`
- Modify: `application/src/main/resources/application.yml`
- Modify: `application/src/main/resources/application-prod.yml`
- Modify: `application/src/test/java/com/example/customerservice/config/ProductionCredentialValidatorTest.java`
- Modify: `README.md`

**Interfaces:**
- Produces: `RuntimeProfileValidator.run(ApplicationArguments)`，只接受激活的 `dev` 或 `prod` Profile。

- [ ] **Step 1: 写失败测试**

```java
assertThrows(IllegalStateException.class,
        () -> new RuntimeProfileValidator(environmentWithoutProfiles).run(arguments));
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -pl application -Dtest=ProductionCredentialValidatorTest test`
Expected: FAIL，因为 `RuntimeProfileValidator` 尚不存在。

- [ ] **Step 3: 最小实现**

```java
if (Arrays.stream(environment.getActiveProfiles())
        .noneMatch(profile -> "dev".equals(profile) || "prod".equals(profile))) {
    throw new IllegalStateException("必须显式激活 dev 或 prod Profile");
}
```

把本地数据库、Redis、MinIO 和 WebSocket Origin 默认值移入 `application-dev.yml`；公共与生产配置只读取环境变量。

- [ ] **Step 4: 验证通过**

Run: `mvn -pl application -Dtest=ProductionCredentialValidatorTest test`
Expected: PASS。

### Task 2: HTTP 请求超时

**Files:**
- Modify: `frontend/src/services/http-client.js`
- Modify: `frontend/src/__tests__/http-client.spec.js`

**Interfaces:**
- Consumes: `request(path, { timeoutMs, signal, ...fetchOptions })`
- Produces: 默认 15000ms 超时错误 `请求超时，请检查网络或稍后重试`。

- [ ] **Step 1: 写失败测试**

```js
await expect(request('/chat/sessions', { timeoutMs: 1 }))
  .rejects.toThrow('请求超时，请检查网络或稍后重试')
```

- [ ] **Step 2: 运行失败测试**

Run: `npm test -- --run src/__tests__/http-client.spec.js`
Expected: FAIL，因为请求入口尚未创建超时控制器。

- [ ] **Step 3: 最小实现**

```js
const controller = new AbortController()
const timeout = setTimeout(() => controller.abort(), timeoutMs)
const response = await fetch(path, { ...fetchOptions, signal: controller.signal })
clearTimeout(timeout)
```

保留调用方已有 `signal` 的取消语义，并只将本地超时转换为中文错误。

- [ ] **Step 4: 验证通过**

Run: `npm test -- --run src/__tests__/http-client.spec.js`
Expected: PASS。

### Task 3: 会话逻辑删除与恢复

**Files:**
- Create: `sql/chat_session_soft_delete_upgrade.sql`
- Modify: `commonModel/src/main/java/com/example/customerservice/domain/ChatSession.java`
- Modify: `commonModel/src/main/java/com/example/customerservice/mapper/ChatSessionMapper.java`
- Modify: `commonModel/src/main/resources/mapper/ChatSessionMapper.xml`
- Modify: `commonModel/src/main/resources/mapper/ChatManagementMapper.xml`
- Modify: `commonModel/src/main/resources/mapper/SupportTicketMapper.xml`
- Modify: `application/src/main/java/com/example/customerservice/controller/ChatManagementController.java`
- Modify: `application/src/test/java/com/example/customerservice/controller/ChatManagementControllerTest.java`
- Modify: `frontend/src/api/admin-api.js`
- Modify: `frontend/src/components/admin/SessionAuditPanel.vue`
- Modify: `frontend/src/__tests__/admin-workspace.spec.js`
- Modify: `README.md`

**Interfaces:**
- Produces: `DELETE /chat/admin/sessions/{sessionId}` 标记删除；`POST /chat/admin/sessions/{sessionId}/restore` 恢复；`GET /chat/admin/sessions?deleted=true|false` 筛选。

- [ ] **Step 1: 写失败测试**

```java
controller.restoreAdminSession("S001");
verify(sessionMapper).restoreById("S001");
```

```js
expect(restoreAdminSession('S001')).toEqual(
  expect.any(Promise)
)
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -pl application -Dtest=ChatManagementControllerTest test`
Run: `npm test -- --run src/__tests__/admin-workspace.spec.js`
Expected: FAIL，因为恢复接口、删除标记和页面操作尚不存在。

- [ ] **Step 3: 最小实现**

```sql
ALTER TABLE chat_session ADD COLUMN deleted_at DATETIME(6) NULL;
ALTER TABLE chat_session ADD COLUMN deleted_by VARCHAR(64) NULL;
```

将管理端删除改为一次 SQL 更新：`deleted_at = NOW(6), deleted_by = 当前管理员, status = 'CLOSED', end_time = COALESCE(end_time, NOW(6))`；这样删除中的活动会话不会继续参与 WebSocket 路由。所有会话、工单、报表和消息管理查询默认增加 `session.deleted_at IS NULL`，已删除筛选显式使用 `IS NOT NULL`。页面把“永久删除”改为“移入已删除”，并在已删除筛选下显示恢复按钮。

- [ ] **Step 4: 验证通过**

Run: `mvn -pl application -Dtest=ChatManagementControllerTest,ChatSessionQueryServiceImplTest,SupportTicketServiceTest test`
Run: `npm test -- --run src/__tests__/admin-workspace.spec.js`
Expected: PASS。

### Task 4: 限制 MinIO 网络暴露

**Files:**
- Modify: `compose.yml`
- Modify: `README.md`

**Interfaces:**
- Produces: `127.0.0.1:9000:9000` 与 `127.0.0.1:9001:9001`。

- [ ] **Step 1: 写失败检查**

Run: `Select-String -Path compose.yml -Pattern '"9000:9000"|"9001:9001"'`
Expected: MATCH，证明当前端口暴露到全部网卡。

- [ ] **Step 2: 最小实现**

```yaml
ports:
  - "127.0.0.1:9000:9000"
  - "127.0.0.1:9001:9001"
```

- [ ] **Step 3: 验证通过**

Run: `docker compose config`
Expected: PASS，且解析端口仅含 `127.0.0.1`。

### Task 5: 完整回归

**Files:**
- Modify: `docs/superpowers/plans/2026-09-01-robustness-foundations.md`（勾选已完成步骤）

- [ ] **Step 1: 运行后端回归**

Run: `mvn test`
Expected: 全部单元测试通过；真实基础设施测试仍按用户要求保持可选。

- [ ] **Step 2: 运行前端回归与构建**

Run: `npm test -- --run`
Run: `npm run build`
Expected: 测试和生产构建通过。

- [ ] **Step 3: 静态与配置检查**

Run: `git diff --check`
Run: `docker compose config`
Expected: 无空白错误，Compose 可解析。
