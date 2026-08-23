# Vue Chat Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the eight identified Vue3 chat-flow gaps without changing the existing Spring Boot protocol.

**Architecture:** Keep protocol handling in `frontend/src/stores/chat.js`; make components render and invoke store actions only. Use STOMP events as the source of truth for history, queue state, session assignment and completion; REST remains the initial/fallback data source.

**Tech Stack:** Vue 3, Pinia, Vitest, STOMP.js, Spring Boot static frontend delivery.

**Spec:** User-provided eight-item defect list dated 2026-08-22.

## Global Constraints

- Do not change backend endpoints or database contracts unless a front-end protocol gap is proven.
- Preserve the existing `/app/chat.*` destinations and existing RBAC behavior.
- Write a failing Vitest regression before each production fix.
- Verify the full frontend suite and production build after all tasks.

---

### Task 1: Prevent false unread and cancelled ticket connections

**Files:**
- Modify: `frontend/src/stores/chat.js`
- Test: `frontend/src/__tests__/agent-workspace.spec.js`, `frontend/src/__tests__/user-workspace.spec.js`

- [ ] Add a test proving a current user's persisted message received after switching sessions does not increment `unreadCounts`.
- [ ] Add a test proving `disconnectStomp()` during an unresolved ticket request prevents STOMP client creation.
- [ ] Add a monotonic connection-attempt token; verify it before and after ticket acquisition and invalidate it on disconnect/logout.
- [ ] Treat messages from `auth.userId` as delivery updates, never as incoming unread messages.

### Task 2: Handle queue and assignment events

**Files:**
- Modify: `frontend/src/stores/chat.js`, `frontend/src/views/UserWorkspaceView.vue`
- Test: `frontend/src/__tests__/user-workspace.spec.js`

- [ ] Add failing event tests for `WAITING_FOR_AGENT`, `VIP_CALLBACK_REQUIRED`, `ASSIGNMENT_PROCESSING`, and business `ERROR`.
- [ ] Store a concise queue status notice and refresh queue/session REST state when these events arrive.
- [ ] On `SESSION_CREATED` and `SESSION_RECONNECTED` for a user, reload sessions and select the event session once it is present.
- [ ] Render the queue status notice in the user workspace.

### Task 3: Make history loading response-driven

**Files:**
- Modify: `frontend/src/stores/chat.js`
- Test: `frontend/src/__tests__/user-workspace.spec.js`

- [ ] Add failing tests asserting `messagesLoading` and `historyLoadingMore` remain true after publish.
- [ ] Clear the appropriate loading flag only in `CHAT_HISTORY` handling for the matching active session.
- [ ] Clear the loading flags on publish failure and connection teardown.

### Task 5: Make transfer and close actions truthful

**Files:**
- Modify: `frontend/src/stores/chat.js`, `frontend/src/components/chat/AgentChatWindow.vue`
- Test: `frontend/src/__tests__/agent-workspace.spec.js`

- [ ] Add failing tests for disconnected transfer/end actions returning a visible error and not closing the transfer dialog.
- [ ] Add store actions `transferSession` and `endSession` that validate connection and publish the existing destinations.
- [ ] Keep the transfer dialog open until publish succeeds; show errors in the agent window.

### Task 6: Guard duplicate consultations

**Files:**
- Modify: `frontend/src/stores/chat.js`, `frontend/src/views/UserWorkspaceView.vue`
- Test: `frontend/src/__tests__/user-workspace.spec.js`

- [ ] Add a failing test that a second start request is ignored while an active session or pending request exists.
- [ ] Track `consultationStarting`; clear it on assignment, queue errors and publish failure.
- [ ] Disable the consultation button while an active session exists or a request is pending.

### Task 7: Verify and deliver

**Files:**
- Test: all `frontend/src/__tests__/*.spec.js`

- [ ] Run the focused regression suites after each task.
- [ ] Run `npm test -- --run` and `npm run build`.
- [ ] Run `mvn process-resources` before local browser verification so Spring Boot serves the new static assets.
