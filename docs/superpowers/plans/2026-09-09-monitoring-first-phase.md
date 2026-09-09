# 第一阶段系统监控 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 采集五类实时运行指标，并在管理员报表页展示安全、可刷新、不持久化的系统监控快照。

**Architecture:** ChatMonitoringMetrics 维护本地 Timer、Counter 与 WebSocket 连接 Gauge；ChatMonitoringService 从 Redis 读取队列和死信基数并组装快照。控制器仅进行管理员授权和服务委托；前端独立监控组件负责 15 秒刷新生命周期。

**Tech Stack:** Spring Boot Actuator、Micrometer Prometheus、Redis、JUnit 5/Mockito、Vue 3、Vitest。

**Spec:** docs/superpowers/specs/2026-09-09-monitoring-first-phase-design.md

## Global Constraints

- 只实现五项实时指标和管理员监控区块；不创建数据库表、历史数据、告警或额外定时任务。
- /actuator/prometheus 不加入 Shiro 匿名白名单；/chat/admin/monitoring 要求 ADMIN 与 chat:admin:dashboard:view。
- 指标不得带用户、会话或消息 ID 标签；Redis 不可用时快照明确标记不可用，而非显示零值。
- 会话操作锁保持一次 SET NX 非阻塞语义；所有同类路径统一走仓储方法。
- 生产代码前必须先写并运行预期失败的行为测试；依赖和 YAML 属于必要配置例外。

---

### Task 1: 指标基础设施与运行快照

**Files:**

- Modify: application/pom.xml, businessModel/pom.xml, application/src/main/resources/application.yml
- Create: commonModel/src/main/java/com/example/customerservice/dto/SystemMonitoringSnapshotVO.java
- Create: businessModel/src/main/java/com/example/customerservice/monitoring/ChatMonitoringMetrics.java
- Create: businessModel/src/main/java/com/example/customerservice/service/ChatMonitoringService.java
- Test: application/src/test/java/com/example/customerservice/monitoring/ChatMonitoringMetricsTest.java
- Test: application/src/test/java/com/example/customerservice/service/ChatMonitoringServiceTest.java

**Interfaces:**

- ChatMonitoringMetrics exposes recordMessagePersisted(Duration), recordSessionLockAttempt(Duration, boolean), registerConnection(String, Set<String>), unregisterConnection(String), and snapshot helpers.
- ChatMonitoringService.findSnapshot() returns SystemMonitoringSnapshotVO.
- Snapshot includes nullable queueLength/deadLetterBacklog, redisAvailable, nested latency summaries, user/agent connections, and Instant collectedAt.

- [ ] **Step 1: Write the failing tests**

~~~
@Test
void repeatedDisconnectDoesNotMakeUserConnectionsNegative() {
    metrics.registerConnection("ws-1", Set.of("USER"));
    metrics.unregisterConnection("ws-1");
    metrics.unregisterConnection("ws-1");
    assertEquals(0, metrics.userConnections());
}

@Test
void redisFailureMarksOnlyRedisSnapshotFieldsUnavailable() {
    when(redis.sortedSetCardinality(RedisConstants.QUEUE_PENDING))
            .thenThrow(new RedisConnectionFailureException("down"));
    SystemMonitoringSnapshotVO snapshot = service.findSnapshot();
    assertFalse(snapshot.redisAvailable());
    assertNull(snapshot.queueLength());
    assertEquals(1, snapshot.onlineConnections().user());
}
~~~

- [ ] **Step 2: Run the tests and verify RED**

Run: mvn.cmd -pl application -am -Dtest=ChatMonitoringMetricsTest,ChatMonitoringServiceTest test

Expected: compilation fails because the new metrics and snapshot types do not exist.

- [ ] **Step 3: Write minimal implementation and required configuration**

Add micrometer-core to businessModel; add Actuator and Prometheus registry to application. Expose only health,info,prometheus; configure percentile histograms for the two timers. Use a concurrent session-to-role map plus AtomicInteger values so only the first connection and first disconnect change a count. ChatMonitoringService catches Redis runtime failures while reading both ZSet sizes and returns unavailable Redis fields.

- [ ] **Step 4: Run the tests and verify GREEN**

Run: mvn.cmd -pl application -am -Dtest=ChatMonitoringMetricsTest,ChatMonitoringServiceTest test

Expected: PASS; duplicate disconnect remains zero and Redis failure preserves local metrics.

- [ ] **Step 5: Commit**

~~~
git add application/pom.xml businessModel/pom.xml application/src/main/resources/application.yml commonModel/src/main/java/com/example/customerservice/dto/SystemMonitoringSnapshotVO.java businessModel/src/main/java/com/example/customerservice/monitoring/ChatMonitoringMetrics.java businessModel/src/main/java/com/example/customerservice/service/ChatMonitoringService.java application/src/test/java/com/example/customerservice/monitoring/ChatMonitoringMetricsTest.java application/src/test/java/com/example/customerservice/service/ChatMonitoringServiceTest.java
git commit -m "feat: add chat monitoring metrics"
~~~

### Task 2: 接入真实业务路径与管理员接口

**Files:**

- Modify: businessModel/src/main/java/com/example/customerservice/repository/ChatRedisRepository.java
- Modify: businessModel/src/main/java/com/example/customerservice/service/impl/ChatRoutingSessionSupport.java
- Modify: businessModel/src/main/java/com/example/customerservice/service/impl/MessagePersistServiceImpl.java
- Modify: application/src/main/java/com/example/customerservice/listener/WebSocketEventListener.java
- Modify: application/src/main/java/com/example/customerservice/controller/ChatManagementController.java
- Modify tests: ChatRedisRepositoryTest.java, MessagePersistServiceTest.java, ChatManagementControllerTest.java

**Interfaces:**

- ChatRedisRepository.acquireSessionOperationLock(String) records one duration and a failure for null or Redis exception without changing its prior return/throw contract.
- MessagePersistServiceImpl.markStored(ChatMessage) records pending-to-stored time only when a ZSet timestamp exists.
- ChatManagementController.findMonitoring() returns Result<SystemMonitoringSnapshotVO> after dashboard authorization.

- [ ] **Step 1: Write the failing tests**

~~~
@Test
void failedSessionLockAttemptIncrementsFailureMetric() {
    when(template.opsForValue().setIfAbsent(anyString(), anyString(), anyLong(), any()))
            .thenReturn(false);
    assertNull(repository.acquireSessionOperationLock("S001"));
    assertEquals(1, metrics.sessionLockFailureCount());
}

@Test
void adminMonitoringEndpointDelegatesAfterDashboardAuthorization() {
    Result<SystemMonitoringSnapshotVO> result = controller.findMonitoring();
    assertEquals(snapshot, result.getData());
    verify(currentUser).requireRole("ADMIN");
    verify(currentUser).requirePermission("chat:admin:dashboard:view");
    verify(monitoringService).findSnapshot();
}
~~~

- [ ] **Step 2: Run the tests and verify RED**

Run: mvn.cmd -pl application -am -Dtest=ChatRedisRepositoryTest,MessagePersistServiceTest,ChatManagementControllerTest test

Expected: compilation fails for the missing metrics dependencies and endpoint.

- [ ] **Step 3: Implement narrow integration points**

Wrap the existing repository operation in one System.nanoTime() duration measurement, record a failure for contention or exception, then preserve its old behavior. Inject the shared metrics component into repository, persistence service, and listener. Read WebSocketUserPrincipal.getRoleCodes() to register USER/AGENT sessions; unregister by session ID. Route ChatRoutingSessionSupport acquisition and release through repository methods. Add the thin controller endpoint with the same authorization calls as the report endpoint.

- [ ] **Step 4: Run the tests and verify GREEN**

Run: mvn.cmd -pl application -am -Dtest=ChatRedisRepositoryTest,MessagePersistServiceTest,ChatManagementControllerTest test

Expected: PASS; instrumentation changes no lock or persistence business result.

- [ ] **Step 5: Commit**

~~~
git add businessModel/src/main/java/com/example/customerservice/repository/ChatRedisRepository.java businessModel/src/main/java/com/example/customerservice/service/impl/ChatRoutingSessionSupport.java businessModel/src/main/java/com/example/customerservice/service/impl/MessagePersistServiceImpl.java application/src/main/java/com/example/customerservice/listener/WebSocketEventListener.java application/src/main/java/com/example/customerservice/controller/ChatManagementController.java application/src/test/java/com/example/customerservice/repository/ChatRedisRepositoryTest.java application/src/test/java/com/example/customerservice/service/MessagePersistServiceTest.java application/src/test/java/com/example/customerservice/controller/ChatManagementControllerTest.java
git commit -m "feat: expose chat monitoring snapshot"
~~~

### Task 3: 管理员报表监控区块

**Files:**

- Modify: frontend/src/api/admin-api.js, frontend/src/components/admin/AdminReportPanel.vue
- Create: frontend/src/components/admin/SystemMonitoringPanel.vue
- Modify: frontend/src/__tests__/admin-workspace.spec.js

**Interfaces:**

- findSystemMonitoringSnapshot() requests /chat/admin/monitoring.
- SystemMonitoringPanel displays the server snapshot and owns a 15-second refresh timer.

- [ ] **Step 1: Write failing frontend tests**

~~~
it('loads monitoring immediately and refreshes every 15 seconds', async () => {
  vi.useFakeTimers()
  mount(SystemMonitoringPanel)
  await vi.dynamicImportSettled()
  expect(findSystemMonitoringSnapshot).toHaveBeenCalledTimes(1)
  await vi.advanceTimersByTimeAsync(15_000)
  expect(findSystemMonitoringSnapshot).toHaveBeenCalledTimes(2)
})
~~~

Also add a separate test in which a rejected refresh preserves the first successful snapshot and shows inline failure text.

- [ ] **Step 2: Run the test and verify RED**

Run: npx.cmd vitest run src/__tests__/admin-workspace.spec.js

Expected: failure because the monitoring API function and panel do not exist.

- [ ] **Step 3: Implement minimal panel**

Create an independent panel, call the endpoint at mount, and use window.setInterval/clearInterval in onMounted/onBeforeUnmount. Render five simple groups: queue, dead letters, user/agent connections, persistence latency, lock latency. Redis unavailable fields render “暂不可用”. A manual refresh can show loading; background failure keeps data and updates inline status. Insert the panel above date-filtered report content.

- [ ] **Step 4: Run the test and verify GREEN**

Run: npx.cmd vitest run src/__tests__/admin-workspace.spec.js

Expected: PASS; initial load, polling, preserved data after automatic failure, manual refresh, and timer cleanup are covered.

- [ ] **Step 5: Commit**

~~~
git add frontend/src/api/admin-api.js frontend/src/components/admin/AdminReportPanel.vue frontend/src/components/admin/SystemMonitoringPanel.vue frontend/src/__tests__/admin-workspace.spec.js
git commit -m "feat: show system monitoring in admin reports"
~~~

### Task 4: 集成验证与交付检查

**Files:**

- Modify only files from Tasks 1-3 when a verification failure identifies a direct defect.

- [ ] **Step 1: Run backend suite**

Run: mvn.cmd -pl application -am test

Expected: existing and monitoring tests pass.

- [ ] **Step 2: Run frontend suite and production build**

Run: npx.cmd vitest run; npm run build

Expected: Vitest passes and Vite emits frontend/dist without errors.

- [ ] **Step 3: Inspect final safety boundaries**

Run: git diff main...HEAD --check; git status --short; rg -n 'prometheus|/actuator/health|/chat/admin/monitoring' application/src/main businessModel/src/main frontend/src

Expected: no whitespace errors or unintended files; Prometheus has no anonymous Shiro rule; controller delegates data access to services.

- [ ] **Step 4: Commit a correction only if verification found one**

~~~
git add <only files required by the verified defect>
git commit -m "fix: correct monitoring verification issue"
~~~

