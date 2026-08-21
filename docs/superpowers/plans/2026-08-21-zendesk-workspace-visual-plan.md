# Zendesk Workspace Visual Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Vue 三角色工作台统一为 Zendesk 式客服界面，同时沿用 stomp 测试页色板。

**Architecture:** 通过全局设计令牌建立颜色、字体和控件规则；各工作区只调整布局容器与现有组件样式，不改变任何接口或状态管理。管理员摘要改为表格/统计行，客服端保持三栏操作面。

**Tech Stack:** Vue 3、Pinia、Vite、Vitest、Spring Boot Maven packaging。

**Spec:** `docs/superpowers/specs/2026-08-21-zendesk-workspace-visual-design.md`

## Global Constraints

- 使用 `#f7f8fa`、`#ffffff`、`#e7e9ee`、`#d6dae2`、`#4d6bfe`、`#3a56e0` 作为基础色板。
- 不改 REST、STOMP、路由、权限或数据字段。
- Vue 界面文件不得含 Emoji。
- 不添加第三方 UI 库。

---

### Task 1: 建立全局视觉令牌与登录入口

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/components/common/AppShell.vue`
- Modify: `frontend/src/views/LoginView.vue`
- Test: `frontend/src/__tests__/ui-text.spec.js`

- [ ] **Step 1: 写入失败测试**

在 `ui-text.spec.js` 增加断言，读取 `App.vue` 并验证它包含 `--color-primary: #4d6bfe`。

- [ ] **Step 2: 运行测试验证失败**

Run: `npm.cmd test -- --run src/__tests__/ui-text.spec.js`

Expected: `App.vue` 未定义该令牌而失败。

- [ ] **Step 3: 实现全局令牌和登录卡片**

在 `App.vue` 定义 CSS 变量和控件基线；在 `AppShell.vue` 使用白色应用栏；在 `LoginView.vue` 使用居中的白色登录面板、细边框和蓝色主按钮。

- [ ] **Step 4: 验证并提交**

Run: `npm.cmd test -- --run src/__tests__/ui-text.spec.js`

Expected: PASS。

### Task 2: 改造用户与客服工作台布局

**Files:**
- Modify: `frontend/src/views/UserWorkspaceView.vue`
- Modify: `frontend/src/views/AgentWorkspaceView.vue`
- Modify: `frontend/src/components/chat/ChatWindow.vue`
- Modify: `frontend/src/components/chat/AgentChatWindow.vue`
- Modify: `frontend/src/components/session/AgentSessionList.vue`
- Modify: `frontend/src/components/session/UserSessionList.vue`
- Test: `frontend/src/__tests__/user-workspace.spec.js`
- Test: `frontend/src/__tests__/agent-workspace.spec.js`

- [ ] **Step 1: 写入失败测试**

在两份工作台测试中断言页面仍渲染会话列表与会话主区域，避免布局改动删除业务组件。

- [ ] **Step 2: 运行测试验证失败**

Run: `npm.cmd test -- --run src/__tests__/user-workspace.spec.js src/__tests__/agent-workspace.spec.js`

Expected: 新断言失败。

- [ ] **Step 3: 实现三栏/双栏工作台样式**

使用 `--color-*` 令牌替换组件内分散色值；会话行采用细边框和选中蓝色描边；消息区采用浅灰背景与白色气泡；保留客服右侧上下文栏和底部快捷回复区域。

- [ ] **Step 4: 验证并提交**

Run: `npm.cmd test -- --run src/__tests__/user-workspace.spec.js src/__tests__/agent-workspace.spec.js`

Expected: PASS。

### Task 3: 改造管理员端为表格优先的工作台

**Files:**
- Modify: `frontend/src/views/AdminWorkspaceView.vue`
- Modify: `frontend/src/components/admin/*.vue`
- Test: `frontend/src/__tests__/admin-workspace.spec.js`

- [ ] **Step 1: 写入失败测试**

在管理员测试中断言导航保留所有七个业务入口，防止视觉重构丢失面板。

- [ ] **Step 2: 运行测试验证失败**

Run: `npm.cmd test -- --run src/__tests__/admin-workspace.spec.js`

Expected: 新断言失败。

- [ ] **Step 3: 实现管理员视觉统一**

将统计卡片改为带状态点的紧凑统计行；统一所有表格标题、筛选器、分页、弹窗和危险操作按钮的边框与颜色；不加入 Emoji 或渐变。

- [ ] **Step 4: 验证全部前端与打包**

Run: `npm.cmd test -- --run`

Expected: PASS。

Run: `npm.cmd run build`

Expected: Vite build success。

- [ ] **Step 5: 打包验证**

Run: `mvnw.cmd -pl application -am package -DskipTests`

Expected: JAR 包含 `BOOT-INF/classes/static/frontend/index.html`。
