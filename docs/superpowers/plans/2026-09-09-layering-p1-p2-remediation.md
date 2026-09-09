# P1/P2 分层与鲁棒性整改 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除 P1/P2 分层越界与关键批处理鲁棒性问题，保持现有 HTTP、STOMP、Redis 和数据库兼容。

**Architecture:** HTTP/STOMP/Scheduler/Interceptor 只保留协议或触发职责，数据访问和业务规则下沉到既有业务服务及 `ChatRedisRepository`。复用现有服务，不引入新框架或数据库表；集中重复角色、权限和运行参数。

**Tech Stack:** Spring Boot、Spring Messaging、MyBatis-Plus、Redis、JUnit 5、Mockito、Vue 3、Vitest。

**Spec:** `docs/superpowers/specs/2026-09-09-layering-p1-p2-remediation-design.md`

## Global Constraints

- 不改变 HTTP URL、STOMP destination、Redis key 名称或数据库结构。
- Scheduler、Controller、WebSocket interceptor 不直接注入 Mapper、`StringRedisTemplate` 或 `SimpMessagingTemplate`。
- 保留每轮 100 条批处理上限；失败项保留给后续轮次重试。
- 不引入新的第三方依赖。

---

### Task 1: STOMP 应用服务与 typing 输入校验

**Files:**
- Create: `commonModel/src/main/java/com/example/customerservice/dto/TypingRequest.java`
- Create: `businessModel/src/main/java/com/example/customerservice/service/ChatStompApplicationService.java`
- Modify: `application/src/main/java/com/example/customerservice/controller/ChatStompController.java`
- Delete: `application/src/main/java/com/example/customerservice/controller/ChatController.java`
- Test: `application/src/test/java/com/example/customerservice/service/ChatStompApplicationServiceTest.java`
- Modify: `application/src/test/java/com/example/customerservice/controller/ChatControllerTest.java`

**Interfaces:**
- Consumes: `ChatRoutingOperations`、`ChatMessageOperations`、`ChatSessionOperations`、`ChatPresenceOperations`、`IAuthenticationService`。
- Produces: `ChatStompApplicationService.handleTyping(TypingRequest, Principal)`；`ChatStompController` 是唯一 `@MessageMapping` 声明处。

- [ ] **Step 1: 写失败测试**

```java
@Test
void handleTypingRejectsMissingSessionId() {
    assertThrows(IllegalArgumentException.class,
            () -> service.handleTyping(new TypingRequest(null, true), principal));
    verifyNoInteractions(presenceOperations);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\\mvnw.cmd --% -q -pl application -am -Dtest=ChatStompApplicationServiceTest test`

Expected: 编译失败，因为 `TypingRequest` 与 `ChatStompApplicationService` 尚不存在。

- [ ] **Step 3: 最小实现**

```java
public record TypingRequest(
        @NotBlank @Size(max = 64) String sessionId,
        boolean typing
) {}

public void handleTyping(TypingRequest request, Principal principal) {
    requirePrincipal(principal);
    requireValidStompPayload(request, "输入状态不能为空");
    chatPresenceOperations.handleTyping(request.sessionId(), principal.getName(), request.typing());
}
```

将原 `ChatController` 的纯协议编排逻辑迁入该服务，移除所有 `@MessageMapping`、`@SendToUser`、`@Component` 注解；`ChatStompController` 委托该服务并保留所有 STOMP 注解。

- [ ] **Step 4: 运行受影响测试**

Run: `.\\mvnw.cmd --% -q -pl application -am -Dtest=ChatStompApplicationServiceTest,ChatControllerTest test`

Expected: PASS；缺失 `sessionId` 时不调用 presence service。

- [ ] **Step 5: 提交**

```powershell
git add commonModel businessModel application
git commit -m "重构：下沉 STOMP 消息编排"
```

### Task 2: 认证服务收拢 WebSocket 数据访问

**Files:**
- Modify: `businessModel/src/main/java/com/example/customerservice/service/IAuthenticationService.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/AuthenticationServiceImpl.java`
- Modify: `application/src/main/java/com/example/customerservice/config/WebSocketHandshakeInterceptor.java`
- Modify: `application/src/main/java/com/example/customerservice/config/StompAuthChannelInterceptor.java`
- Test: `application/src/test/java/com/example/customerservice/config/WebSocketHandshakeInterceptorTest.java`
- Test: `application/src/test/java/com/example/customerservice/config/StompAuthChannelInterceptorTest.java`

**Interfaces:**
- Consumes: `IAuthenticationService`。
- Produces: `requireEnabledUser(userId)`、`findRoleCodesByUserId(userId)`、`requireChatSubscriptionPermission(userId)`。

- [ ] **Step 1: 写失败测试**

```java
verify(authenticationService).requireEnabledUser("U001");
verify(authenticationService).requireChatSubscriptionPermission("U001");
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\\mvnw.cmd --% -q -pl application -am -Dtest=WebSocketHandshakeInterceptorTest,StompAuthChannelInterceptorTest test`

Expected: 编译失败，因为认证服务尚无这些方法。

- [ ] **Step 3: 最小实现**

```java
public void requireEnabledUser(String userId) {
    SysUser user = userMapper.selectById(userId);
    if (user == null || !"ENABLED".equals(user.getStatus())) {
        throw new IllegalArgumentException("用户无效或已禁用");
    }
}
```

在认证服务内完成角色、权限查询。两个 interceptor 仅保留 token/Principal/destination 解析，并将认证服务异常转换为既有协议异常；删除全部 Mapper 字段和构造参数。

- [ ] **Step 4: 运行受影响测试**

Run: `.\\mvnw.cmd --% -q -pl application -am -Dtest=WebSocketHandshakeInterceptorTest,StompAuthChannelInterceptorTest,AuthenticationServiceImplTest test`

Expected: PASS；interceptor 不再 mock 或注入 Mapper。

- [ ] **Step 5: 提交**

```powershell
git add businessModel application
git commit -m "重构：认证入口委托业务服务"
```

### Task 3: Scheduler 下沉与批处理隔离

**Files:**
- Modify: `businessModel/src/main/java/com/example/customerservice/repository/ChatRedisRepository.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/ChatRoutingOperations.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/ChatPresenceOperations.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/ChatMaintenanceOperations.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/MessagePersistService.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatRoutingSessionService.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatRoutingSessionMaintenanceSupport.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatPresenceService.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/MessagePersistServiceImpl.java`
- Modify: `application/src/main/java/com/example/customerservice/scheduler/QueueTimeoutSweeper.java`
- Modify: `application/src/main/java/com/example/customerservice/scheduler/HeartbeatTimeoutScheduler.java`
- Modify: `application/src/main/java/com/example/customerservice/scheduler/AgentReconnectGraceScheduler.java`
- Modify: `application/src/main/java/com/example/customerservice/scheduler/MessageReconciliationScheduler.java`
- Modify: `application/src/main/java/com/example/customerservice/scheduler/RedisDataRetentionScheduler.java`
- Modify: `application/src/main/java/com/example/customerservice/scheduler/SessionInactivityScheduler.java`
- Test: `application/src/test/java/com/example/customerservice/scheduler/HeartbeatTimeoutSchedulerTest.java`
- Test: `application/src/test/java/com/example/customerservice/scheduler/AgentReconnectGraceSchedulerTest.java`
- Test: `application/src/test/java/com/example/customerservice/service/ChatPresenceServiceTest.java`

**Interfaces:**
- Produces: `ChatRoutingOperations.removeTimedOutWaitingUsers()`、`ChatPresenceOperations.handleExpiredHeartbeatUsers()`、`ChatPresenceOperations.handleExpiredReconnectGracePeriods()`、`ChatMaintenanceOperations.handleInactiveSessions(cutoffMillis)`、`MessagePersistService.retryAndCheckBacklog()`、`MessagePersistService.cleanupExpiredRedisData(nowMillis, batchSize)`。

- [ ] **Step 1: 写失败测试**

```java
doThrow(new RuntimeException("boom"))
        .when(presenceOperations).handleHeartbeatTimeout("U001");
scheduler.scanHeartbeatTimeout();
verify(presenceOperations).handleHeartbeatTimeout("U002");
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\\mvnw.cmd --% -q -pl application -am -Dtest=HeartbeatTimeoutSchedulerTest,AgentReconnectGraceSchedulerTest test`

Expected: FAIL；第一项异常会阻断同批第二项。

- [ ] **Step 3: 最小实现**

```java
@Scheduled(fixedDelayString = "${app.chat.heartbeat.sweep-delay-ms:10000}")
public void scanHeartbeatTimeout() {
    schedulerLock.execute("heartbeat-timeout", presenceOperations::handleExpiredHeartbeatUsers);
}
```

将每个 scheduler 的 Redis 查询、Lua、通知和逐项 try/catch 移入对应业务服务；`ChatRedisRepository` 增加所需的有语义操作，不暴露 `StringRedisTemplate`。`RedisDataRetentionScheduler` 改为仅调用 `cleanupExpiredRedisData(System.currentTimeMillis(), 500)`，`SessionInactivityScheduler` 改为仅计算 cutoff 并调用 `handleInactiveSessions(cutoffMillis)`。每项失败使用 `log.warn(..., exception)` 记录完整堆栈，再继续循环。

- [ ] **Step 4: 运行受影响测试**

Run: `.\\mvnw.cmd --% -q -pl application -am -Dtest=HeartbeatTimeoutSchedulerTest,AgentReconnectGraceSchedulerTest,ChatPresenceServiceTest,ChatRedisRepositoryTest test`

Expected: PASS；scheduler 不直接注入 Redis 或 STOMP 模板。

- [ ] **Step 5: 提交**

```powershell
git add businessModel application
git commit -m "重构：下沉定时任务的数据访问"
```

### Task 4: 工单、工作台角色与附件边界

**Files:**
- Create: `application/src/main/java/com/example/customerservice/security/WorkspaceRoleResolver.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/SupportTicketService.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/ChatAttachmentService.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatAttachmentServiceImpl.java`
- Create: `commonModel/src/main/java/com/example/customerservice/dto/AttachmentDownload.java`
- Modify: `application/src/main/java/com/example/customerservice/controller/SupportTicketController.java`
- Modify: `application/src/main/java/com/example/customerservice/controller/ChatSessionController.java`
- Modify: `application/src/main/java/com/example/customerservice/controller/ChatAttachmentController.java`
- Test: `application/src/test/java/com/example/customerservice/controller/SupportTicketControllerTest.java`
- Test: `application/src/test/java/com/example/customerservice/controller/ChatAttachmentControllerTest.java`
- Test: `application/src/test/java/com/example/customerservice/security/WorkspaceRoleResolverTest.java`

**Interfaces:**
- Produces: `WorkspaceRoleResolver.resolve(String header, Set<String> roles, String... allowed)`；`SupportTicketService.submitUserFeedback(String actorId, String ticketNo, SupportTicketUserFeedbackDTO request)`；`ChatAttachmentService.loadAccessibleDownload(String userId, String id)`。

- [ ] **Step 1: 写失败测试**

```java
verify(supportTicketService).submitUserFeedback("U001", "TK-00000001", request);
verify(attachmentService).loadAccessibleDownload("U001", attachmentId);
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\\mvnw.cmd --% -q -pl application -am -Dtest=SupportTicketControllerTest,ChatAttachmentControllerTest,WorkspaceRoleResolverTest test`

Expected: 编译失败，因为统一服务入口和下载描述对象尚不存在。

- [ ] **Step 3: 最小实现**

```java
public SupportTicketVO submitUserFeedback(String actorId, String ticketNo,
        SupportTicketUserFeedbackDTO request) {
    return "CONFIRM".equals(request.getAction())
            ? confirmResolution(actorId, ticketNo, request.getVersion())
            : requestFurtherHandling(actorId, ticketNo, request.getVersion());
}

public record AttachmentDownload(String filename, String contentType,
        long size, String messageType, Resource resource) {}
```

控制器改为单次服务委托；`WorkspaceRoleResolver` 统一现有 header 与角色集合校验；附件服务负责将实体映射为 VO 与下载描述对象。

- [ ] **Step 4: 运行受影响测试**

Run: `.\\mvnw.cmd --% -q -pl application -am -Dtest=SupportTicketControllerTest,ChatAttachmentControllerTest,WorkspaceRoleResolverTest,SupportTicketServiceTest,ChatAttachmentServiceTest test`

Expected: PASS；控制器不引用 `ChatAttachment`，不按反馈动作选择服务方法。

- [ ] **Step 5: 提交**

```powershell
git add commonModel businessModel application
git commit -m "重构：收拢工单与附件边界"
```

### Task 5: 重复常量、运行参数与 watchdog 生命周期

**Files:**
- Create: `commonModel/src/main/java/com/example/customerservice/constant/RoleCodes.java`
- Create: `commonModel/src/main/java/com/example/customerservice/constant/PermissionCodes.java`
- Modify: `commonModel/src/main/java/com/example/customerservice/constant/RedisConstants.java`
- Modify: `application/src/main/resources/application.yml`
- Modify: `application/src/main/java/com/example/customerservice/scheduler/DistributedSchedulerLock.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/repository/ChatRedisRepository.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/MessagePersistServiceImpl.java`
- Modify: affected controllers, interceptors and services using repeated roles/permissions.
- Create: `application/src/test/java/com/example/customerservice/config/ChatServiceConfigurationTest.java`

**Interfaces:**
- Produces: 单一角色、权限、心跳超时来源；每个 watchdog owner 的 `@PreDestroy void shutdownWatchdog()`。

- [ ] **Step 1: 写失败测试**

```java
assertThat(applicationYaml).contains("heartbeat-sweep-delay-ms");
assertThat(RedisConstants.HEARTBEAT_TIMEOUT_SECONDS).isEqualTo(90L);
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\\mvnw.cmd --% -q -pl application -am -Dtest=ChatServiceConfigurationTest test`

Expected: FAIL，因为测试尚未覆盖统一配置来源。

- [ ] **Step 3: 最小实现**

```java
@PreDestroy
void shutdownWatchdog() {
    LOCK_WATCHDOG.shutdownNow();
}
```

删除重复的 `SESSION_STATUS_CLOSED` 与 `HEARTBEAT_TIMEOUT_MILLIS`；将 scheduler 固定延迟改为现有或新增的 `app.chat.*.sweep-delay-ms` 属性；仅替换重复使用的角色/权限字面量。

- [ ] **Step 4: 运行受影响测试**

Run: `.\\mvnw.cmd --% -q -pl application -am -Dtest=ChatServiceConfigurationTest,ChatRedisRepositoryTest test`

Expected: PASS；没有重复的会话关闭或心跳毫秒常量。

- [ ] **Step 5: 全量验证并提交**

Run:

```powershell
.\\mvnw.cmd --% -q test -pl application -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-Djdk.net.URLClassPath.disableClassPathURLCheck=true
node node_modules/vitest/vitest.mjs run
npm.cmd run build
git diff --check
```

Expected: 后端、前端测试与构建全部通过。

```powershell
git add commonModel businessModel application frontend docs
git commit -m "重构：完善分层边界与运行可靠性"
```
