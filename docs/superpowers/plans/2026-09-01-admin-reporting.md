# 管理端运营报表 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在管理员工作台提供可按日期和日/周粒度查看的会话、客服、首响、时长、满意度和工单运营报表。

**Architecture:** 新增一个管理员报表总览接口，在服务层统一校验时间范围、补齐趋势/状态零值桶，Mapper 分别聚合六类指标。前端新增 `AdminReportPanel`；`ReportChart` 负责 ECharts 生命周期，所有图表共用一次总览请求。

**Tech Stack:** Spring Boot、MyBatis XML、MySQL、Vue 3、Element Plus、ECharts 6、Vitest、JUnit 5、Mockito。

**Spec:** `docs/superpowers/specs/2026-09-01-admin-reporting-design.md`

## Global Constraints

- 仅 `ADMIN` 且拥有 `chat:admin:dashboard:view` 的用户可读取报表；不得新增权限、数据库字段、表或迁移脚本。
- 日期范围默认最近 30 个自然日，`to` 为开区间，最大跨度 366 天；仅支持 `DAY` 与 `WEEK`，周从周一开始。
- 接待量按 `chat_session.agent_id`；首响为第一条未撤回客服消息减会话创建时间；低分是 1–2 星。
- 使用已安装的 `echarts@^6.1.0`，不添加 `vue-echarts`；实例不得放进 Vue 深度响应式数据。
- 所有新增行为先写失败测试并确认失败原因，再写最小实现；每个任务通过自身测试后提交。

---

## File Structure

- Create: `commonModel/src/main/java/com/example/customerservice/dto/AdminReportQueryDTO.java` — 日期和粒度参数。
- Create: `commonModel/src/main/java/com/example/customerservice/dto/AdminReportOverviewVO.java` — 总览响应与嵌套指标记录。
- Modify: `commonModel/src/main/java/com/example/customerservice/mapper/ChatManagementMapper.java` — 六个聚合查询方法。
- Modify: `commonModel/src/main/resources/mapper/ChatManagementMapper.xml` — 指标 SQL。
- Modify: `businessModel/src/main/java/com/example/customerservice/service/ChatManagementQueryService.java` — 报表服务契约。
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatManagementQueryServiceImpl.java` — 默认值、校验、补桶和编排。
- Modify: `application/src/main/java/com/example/customerservice/controller/ChatManagementController.java` — 报表 GET 端点。
- Modify: `application/src/test/java/com/example/customerservice/service/ChatManagementQueryServiceImplTest.java` — 服务测试。
- Modify: `application/src/test/java/com/example/customerservice/controller/ChatManagementControllerTest.java` — 控制器权限测试。
- Modify: `frontend/src/api/admin-api.js` — 总览 API 封装。
- Create: `frontend/src/components/admin/ReportChart.vue` — ECharts 生命周期封装。
- Create: `frontend/src/components/admin/report-options.js` — 图表 option 纯函数。
- Create: `frontend/src/components/admin/AdminReportPanel.vue` — 报表页面。
- Create: `frontend/src/__tests__/report-options.spec.js` — option 纯函数测试。
- Modify: `frontend/src/__tests__/admin-workspace.spec.js` — 页面与页签回归测试。
- Modify: `frontend/src/views/AdminWorkspaceView.vue` — “报表”页签。

## Interfaces

```java
public class AdminReportQueryDTO {
    private LocalDateTime from;
    private LocalDateTime to;
    private String granularity = "DAY";
}

public record AdminReportOverviewVO(
        List<AdminReportOverviewVO.TimeBucketCountVO> sessionTrend,
        List<AdminReportOverviewVO.AgentReceptionRankVO> agentReceptionRanking,
        Long averageFirstResponseSeconds,
        List<AdminReportOverviewVO.DurationBucketCountVO> sessionDurationDistribution,
        AdminReportOverviewVO.SatisfactionMetricsVO satisfaction,
        List<SupportTicketStatusCountVO> ticketStatusDistribution
) {
    public record TimeBucketCountVO(String bucket, long count) {}
    public record AgentReceptionRankVO(String agentId, String agentName, long sessionCount) {}
    public record DurationBucketCountVO(String bucket, long count) {}
    public record SatisfactionMetricsVO(long ratingCount, Double averageRating, long lowRatingCount, Double lowRatingRate) {}
}
```

```java
AdminReportOverviewVO findAdminReportOverview(AdminReportQueryDTO query);
```

```http
GET /chat/admin/reports/overview?from=2026-08-01T00:00:00&to=2026-09-01T00:00:00&granularity=DAY
```

### Task 1: 报表数据契约与聚合 SQL

**Files:**
- Create: `commonModel/src/main/java/com/example/customerservice/dto/AdminReportQueryDTO.java`
- Create: `commonModel/src/main/java/com/example/customerservice/dto/AdminReportOverviewVO.java`
- Modify: `commonModel/src/main/java/com/example/customerservice/mapper/ChatManagementMapper.java`
- Modify: `commonModel/src/main/resources/mapper/ChatManagementMapper.xml`
- Test: `application/src/test/java/com/example/customerservice/service/ChatManagementQueryServiceImplTest.java`

**Consumes:** `chat_session`、`chat_message`、`chat_session_rating`、`support_ticket` 和 `sys_user`。

**Produces:** 六个 Mapper 聚合方法以及 DTO/VO。

- [ ] **Step 1: 写失败服务测试，声明总览契约**

```java
@Test
void reportOverviewRequestsEveryAggregateForTheNormalizedRange() {
    AdminReportQueryDTO query = query("2026-08-01T00:00", "2026-08-08T00:00", "DAY");
    service.findAdminReportOverview(query);
    verify(managementMapper).findSessionTrend(query.getFrom(), query.getTo(), "DAY");
    verify(managementMapper).findAgentReceptionRanking(query.getFrom(), query.getTo(), 10);
    verify(managementMapper).findAverageFirstResponseSeconds(query.getFrom(), query.getTo());
}
```

- [ ] **Step 2: 运行测试并确认缺少报表类型或方法**

Run: `mvn -am -pl application "-Dtest=ChatManagementQueryServiceImplTest#reportOverviewRequestsEveryAggregateForTheNormalizedRange" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL at compilation because `AdminReportQueryDTO` and `findAdminReportOverview` do not exist.

- [ ] **Step 3: 新增 DTO/VO 和 Mapper 方法**

```java
List<AdminReportOverviewVO.TimeBucketCountVO> findSessionTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("granularity") String granularity);
List<AdminReportOverviewVO.AgentReceptionRankVO> findAgentReceptionRanking(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("limit") int limit);
Long findAverageFirstResponseSeconds(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
List<AdminReportOverviewVO.DurationBucketCountVO> findSessionDurationDistribution(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
AdminReportOverviewVO.SatisfactionMetricsVO findSatisfactionMetrics(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
List<SupportTicketStatusCountVO> findTicketStatusDistribution(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
```

In XML: `DAY` uses `DATE(session.create_time)`; `WEEK` uses `DATE_SUB(DATE(session.create_time), INTERVAL WEEKDAY(session.create_time) DAY)`. First response groups `MIN(message.create_time)` by message session with `sender_role = 'AGENT'` and `recalled = 0`. Duration SQL returns the five fixed labels using `TIMESTAMPDIFF(MINUTE, session.create_time, session.end_time)`. Rating SQL computes count, average, 1–2 star count and rate; ticket SQL groups the four statuses by `support_ticket.created_at`.

- [ ] **Step 4: 运行测试并确认失败推进至服务层**

Run: `mvn -am -pl application "-Dtest=ChatManagementQueryServiceImplTest#reportOverviewRequestsEveryAggregateForTheNormalizedRange" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL because `ChatManagementQueryServiceImpl` has not implemented the report method.

- [ ] **Step 5: 提交数据契约**

```bash
git add commonModel/src/main/java/com/example/customerservice/dto/AdminReportQueryDTO.java commonModel/src/main/java/com/example/customerservice/dto/AdminReportOverviewVO.java commonModel/src/main/java/com/example/customerservice/mapper/ChatManagementMapper.java commonModel/src/main/resources/mapper/ChatManagementMapper.xml
git commit -m "feat: add admin report aggregate mapper"
```

### Task 2: 服务归一化、补桶和管理员接口

**Files:**
- Modify: `businessModel/src/main/java/com/example/customerservice/service/ChatManagementQueryService.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatManagementQueryServiceImpl.java`
- Modify: `application/src/main/java/com/example/customerservice/controller/ChatManagementController.java`
- Modify: `application/src/test/java/com/example/customerservice/service/ChatManagementQueryServiceImplTest.java`
- Modify: `application/src/test/java/com/example/customerservice/controller/ChatManagementControllerTest.java`

**Consumes:** Task 1 的 Mapper 与 VO。

**Produces:** 校验、零值补齐后的总览服务与 `/chat/admin/reports/overview`。

- [ ] **Step 1: 写失败测试，覆盖默认范围、补桶和无样本**

```java
@Test
void reportOverviewFillsDailyGapsAndKeepsMissingMetricsNull() {
    when(managementMapper.findSessionTrend(any(), any(), eq("DAY")))
            .thenReturn(List.of(new AdminReportOverviewVO.TimeBucketCountVO("2026-08-31", 3L)));
    when(managementMapper.findAverageFirstResponseSeconds(any(), any())).thenReturn(null);
    when(managementMapper.findSatisfactionMetrics(any(), any()))
            .thenReturn(new AdminReportOverviewVO.SatisfactionMetricsVO(0, null, 0, null));

    AdminReportOverviewVO result = service.findAdminReportOverview(new AdminReportQueryDTO());

    assertEquals(30, result.sessionTrend().size());
    assertNull(result.averageFirstResponseSeconds());
    assertNull(result.satisfaction().averageRating());
}

@Test
void reportOverviewRejectsInvalidRangeAndGranularity() {
    assertThrows(IllegalArgumentException.class, () -> service.findAdminReportOverview(query("2026-09-02T00:00", "2026-09-01T00:00", "MONTH")));
}
```

- [ ] **Step 2: 运行测试并确认失败在默认化/补桶逻辑**

Run: `mvn -am -pl application "-Dtest=ChatManagementQueryServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL because range normalization and report assembly do not exist.

- [ ] **Step 3: 实现总览服务和控制器**

```java
public AdminReportOverviewVO findAdminReportOverview(AdminReportQueryDTO query) {
    NormalizedReportQuery normalized = normalizeReportQuery(query);
    return new AdminReportOverviewVO(
            fillTrendBuckets(managementMapper.findSessionTrend(normalized.from(), normalized.to(), normalized.granularity()), normalized),
            safeList(managementMapper.findAgentReceptionRanking(normalized.from(), normalized.to(), 10)),
            managementMapper.findAverageFirstResponseSeconds(normalized.from(), normalized.to()),
            fillDurationBuckets(managementMapper.findSessionDurationDistribution(normalized.from(), normalized.to())),
            normalizeSatisfaction(managementMapper.findSatisfactionMetrics(normalized.from(), normalized.to())),
            fillTicketStatusBuckets(managementMapper.findTicketStatusDistribution(normalized.from(), normalized.to()))
    );
}

@GetMapping("/admin/reports/overview")
public Result<AdminReportOverviewVO> findAdminReportOverview(@Valid @ModelAttribute AdminReportQueryDTO query) {
    currentUser.requireRole("ADMIN");
    currentUser.requirePermission("chat:admin:dashboard:view");
    return Result.success(managementQueryService.findAdminReportOverview(query));
}
```

Use `LocalDate.now().plusDays(1).atStartOfDay()` as default `to` and `to.minusDays(30)` as `from`; reject spans above 366 days. The service test asserts the 30-bucket size and captures Mapper range arguments rather than asserting a hard-coded current date. Test that the controller calls both role and permission checks before delegation.

- [ ] **Step 4: 运行服务与控制器测试并确认通过**

Run: `mvn -am -pl application "-Dtest=ChatManagementQueryServiceImplTest,ChatManagementControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS.

- [ ] **Step 5: 提交服务与接口**

```bash
git add businessModel/src/main/java/com/example/customerservice/service/ChatManagementQueryService.java businessModel/src/main/java/com/example/customerservice/service/impl/ChatManagementQueryServiceImpl.java application/src/main/java/com/example/customerservice/controller/ChatManagementController.java application/src/test/java/com/example/customerservice/service/ChatManagementQueryServiceImplTest.java application/src/test/java/com/example/customerservice/controller/ChatManagementControllerTest.java
git commit -m "feat: add admin report overview endpoint"
```

### Task 3: ECharts 封装与 option 构造器

**Files:**
- Create: `frontend/src/components/admin/ReportChart.vue`
- Create: `frontend/src/components/admin/report-options.js`
- Create: `frontend/src/__tests__/report-options.spec.js`

**Consumes:** `echarts@^6.1.0`、Task 2 返回的 JSON。

**Produces:** ECharts 生命周期组件与会话趋势、客服排行、会话时长、满意度、工单状态 option 纯函数。

- [ ] **Step 1: 写失败 option 测试**

```js
import { buildSessionTrendOption, buildTicketStatusOption } from '../components/admin/report-options'

it('maps daily buckets to a line chart and preserves zeroes', () => {
  const option = buildSessionTrendOption([{ bucket: '2026-08-31', count: 0 }, { bucket: '2026-09-01', count: 3 }])
  expect(option.xAxis.data).toEqual(['2026-08-31', '2026-09-01'])
  expect(option.series[0]).toMatchObject({ type: 'line', data: [0, 3] })
})

it('uses an explicit empty state for no ticket distribution', () => {
  expect(buildTicketStatusOption([]).graphic[0].style.text).toBe('暂无数据')
})
```

- [ ] **Step 2: 运行测试并确认 option 模块不存在**

Run: `npm test -- --run src/__tests__/report-options.spec.js`

Expected: FAIL because `report-options.js` has not been created.

- [ ] **Step 3: 实现按需注册和图表生命周期**

```js
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, BarChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
```

`ReportChart` receives `option` and `loading`, initializes in `onMounted`, updates with `chart.setOption(option, { notMerge: true })`, resizes through `ResizeObserver`, and on unmount disconnects and calls `chart.dispose()`. Keep `let chart` local. Every builder includes tooltip, `aria.enabled: true`, and `graphic` text `暂无数据` when empty.

- [ ] **Step 4: 运行 option 测试并确认通过**

Run: `npm test -- --run src/__tests__/report-options.spec.js`

Expected: PASS.

- [ ] **Step 5: 提交图表基础设施**

```bash
git add frontend/src/components/admin/ReportChart.vue frontend/src/components/admin/report-options.js frontend/src/__tests__/report-options.spec.js
git commit -m "feat: add reusable admin report charts"
```

### Task 4: 报表页面和管理工作台接入

**Files:**
- Modify: `frontend/src/api/admin-api.js`
- Create: `frontend/src/components/admin/AdminReportPanel.vue`
- Modify: `frontend/src/views/AdminWorkspaceView.vue`
- Modify: `frontend/src/__tests__/admin-workspace.spec.js`

**Consumes:** Task 2 总览 API 和 Task 3 图表组件/option 构造器。

**Produces:** “报表”侧栏页签、日期/粒度查询、KPI 和五项图表。

- [ ] **Step 1: 写失败前端测试**

```js
it('requests reports with the selected range and granularity', async () => {
  const wrapper = mount(AdminReportPanel)
  await wrapper.get('.report-from').setValue('2026-08-01T00:00')
  await wrapper.get('.report-to').setValue('2026-09-01T00:00')
  await wrapper.get('.report-granularity').setValue('WEEK')
  await wrapper.get('.query-reports').trigger('click')
  expect(findAdminReportOverview).toHaveBeenLastCalledWith({ from: '2026-08-01T00:00', to: '2026-09-01T00:00', granularity: 'WEEK' })
})

it('renders unavailable KPIs instead of fake zeros', async () => {
  findAdminReportOverview.mockResolvedValue({ data: { averageFirstResponseSeconds: null, satisfaction: { averageRating: null, lowRatingRate: null } } })
  const wrapper = mount(AdminReportPanel)
  await vi.dynamicImportSettled()
  expect(wrapper.text()).toContain('暂无样本')
})
```

Also add an `AdminWorkspaceView` test that clicks `报表` and verifies the `AdminReportPanel` stub appears.

- [ ] **Step 2: 运行测试并确认 API、面板和页签不存在**

Run: `npm test -- --run src/__tests__/admin-workspace.spec.js`

Expected: FAIL because report API, panel, and sidebar item are missing.

- [ ] **Step 3: 实现 API、面板与页签**

```js
export function findAdminReportOverview(params = {}) {
  const query = new URLSearchParams(Object.entries(params).filter(([, value]) => value !== '' && value != null))
  return request(`/chat/admin/reports/overview?${query.toString()}`)
}
```

The panel owns only `from`, `to`, `granularity`, `loading`, `error`, and `overview`. On mount request with no dates so the server selects the default range. On explicit query require both date inputs together and `from < to`. Format first response in minutes/seconds, average rating to one decimal, low-rating rate as a one-decimal percentage. Render three KPI cards and five `ReportChart` instances. Register `{ key: 'reports', label: '报表' }` directly after `管理仪表盘`.

- [ ] **Step 4: 运行组件和 option 测试并确认通过**

Run: `npm test -- --run src/__tests__/report-options.spec.js src/__tests__/admin-workspace.spec.js`

Expected: PASS.

- [ ] **Step 5: 提交报表工作台**

```bash
git add frontend/src/api/admin-api.js frontend/src/components/admin/AdminReportPanel.vue frontend/src/components/admin/ReportChart.vue frontend/src/components/admin/report-options.js frontend/src/views/AdminWorkspaceView.vue frontend/src/__tests__/admin-workspace.spec.js frontend/src/__tests__/report-options.spec.js
git commit -m "feat: add admin analytics reports"
```

### Task 5: 全量验证和验收同步

**Files:**
- Modify: only files produced by Tasks 1–4 when verification finds a defect.

**Consumes:** 已完成的后端接口和前端报表页。

**Produces:** 可启动验收的构建产物和验证结果。

- [ ] **Step 1: 运行后端完整测试**

Run: `mvn test`

Expected: PASS; report tests与现有全部后端测试通过。

- [ ] **Step 2: 运行前端完整测试和生产构建**

Run: `npm test -- --run`

Run: `npm run build`

Expected: PASS; Vite 产出 `frontend/dist`。

- [ ] **Step 3: 检查提交边界**

Run: `git status --short`

Expected: 除用户已有未跟踪文件外，无未提交报表实现文件；不提交 `frontend/dist`。

- [ ] **Step 4: 处理验证发现的缺陷**

If a verification command fails, return to the task that owns the failing file, add a focused failing test first, then make the smallest correction and amend that task's commit with its exact file list. If every command passes, create no extra verification commit.
