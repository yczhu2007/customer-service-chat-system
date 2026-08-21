# Chat Workspaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved chat-workspace design: role-scoped session queries, session metadata, fixed agent views, and a Vue 3 application with user, agent, and administrator workspaces.

**Architecture:** Keep the Spring Boot/MyBatis/MySQL/Redis/STOMP backend boundaries. Add explicit participant scope and metadata/view services in the existing business and application modules. Add one `frontend/` Vue 3 + Vite application with Vue Router, Pinia, native fetch, and `@stomp/stompjs`; package its build into the Spring Boot application.

**Tech Stack:** Java 17, Spring Boot 4.0.7, MyBatis-Plus 3.5.17, MySQL 8, Redis, STOMP/WebSocket, Vue 3, Vite, Vue Router, Pinia, Vitest, Vue Test Utils, `@stomp/stompjs`.

**Spec:** `docs/superpowers/specs/2026-08-21-chat-session-workspaces-design.md`

## Global Constraints

- `USER` session queries use only `chat_session.user_id = currentUserId`.
- `AGENT` session queries use only `chat_session.agent_id = currentUserId`.
- `ADMIN` uses `/chat/admin/sessions` for cross-user search.
- Session priorities are exactly `LOW`, `NORMAL`, `HIGH`, `URGENT`.
- Session categories are exactly `ACCOUNT`, `PAYMENT`, `TECHNICAL`, `AFTER_SALES`, `OTHER`.
- A session has at most ten normalized lowercase tags.
- Only the assigned agent can update session metadata.
- Fixed agent views are backend-owned; clients cannot submit arbitrary predicates.
- The legacy `stomp-test.html` remains until Vue regression checks pass.
- No configurable view engine, SLA engine, agent-group system, omnichannel input, knowledge base, or general workflow engine is added.
- Real MySQL/Redis verification is reported separately from unit-test and compilation verification.

---

### Task 1: Correct participant session scope

**Files:**
- Modify: `application/src/main/java/com/example/customerservice/controller/ChatController.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/ChatSessionQueryService.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatSessionQueryServiceImpl.java`
- Create or modify: `commonModel/src/main/java/com/example/customerservice/constant/SessionParticipantType.java`
- Modify: `application/src/test/java/com/example/customerservice/controller/ChatControllerTest.java`
- Create or modify: `application/src/test/java/com/example/customerservice/service/ChatSessionQueryServiceImplTest.java`
- Modify: `sql/rbac_ddl.sql`

**Interfaces:**
- Produce `PageResult<ChatSessionListItemVO> findMySessions(String participantId, SessionParticipantType participantType, String statusFilter, String archiveStatusFilter, long pageNo, long pageSize)`.
- Produce permission `chat:session:view-own` assigned to USER and AGENT.

- [ ] **Step 1: Add failing service tests** for USER scope, AGENT scope, no cross-scope records, and invalid participant type.
- [ ] **Step 2: Run the focused tests** with `./mvnw.cmd -pl application -am -Dtest=ChatSessionQueryServiceImplTest test`; confirm the new tests fail against the current `user_id OR agent_id` query.
- [ ] **Step 3: Add `SessionParticipantType` and update the service signature** so the caller must declare USER or AGENT scope.
- [ ] **Step 4: Update the query predicate** to choose exactly one of `user_id` or `agent_id`, require `chat:session:view-own`, allow both roles, and reject ADMIN on this endpoint.
- [ ] **Step 5: Add controller tests** covering USER success, AGENT success, ADMIN rejection, and dual-role AGENT precedence.
- [ ] **Step 6: Run the focused tests** and confirm all scope tests pass.
- [ ] **Step 7: Commit** with `git add` for only the task files and `git commit -m "fix: scope participant session queries"`.

### Task 2: Add session metadata and normalized tags

**Files:**
- Modify: `sql/chat_ddl.sql`
- Create: `sql/chat_session_metadata_upgrade.sql`
- Modify: `commonModel/src/main/java/com/example/customerservice/domain/ChatSession.java`
- Create: `commonModel/src/main/java/com/example/customerservice/domain/ChatSessionTag.java`
- Create: `commonModel/src/main/java/com/example/customerservice/mapper/ChatSessionTagMapper.java`
- Modify: `commonModel/src/main/java/com/example/customerservice/dto/ChatSessionListItemVO.java`
- Create: `commonModel/src/main/java/com/example/customerservice/dto/ChatSessionMetadataVO.java`
- Create: `commonModel/src/main/java/com/example/customerservice/dto/ChatSessionMetadataUpdateDTO.java`
- Modify: `commonModel/src/main/java/com/example/customerservice/constant/ChatConstants.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/ChatSessionQueryService.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatSessionQueryServiceImpl.java`
- Create or modify: `application/src/main/java/com/example/customerservice/controller/ChatController.java`
- Create or modify: `application/src/test/java/com/example/customerservice/service/ChatSessionMetadataTest.java`

**Interfaces:**
- Produce `ChatSessionMetadataVO getSessionMetadata(String actorId, String sessionId)`.
- Produce `ChatSessionMetadataVO updateSessionMetadata(String agentId, String sessionId, ChatSessionMetadataUpdateDTO request)`.
- Produce `GET /chat/sessions/{sessionId}/metadata` and `PUT /chat/sessions/{sessionId}/metadata`.

- [ ] **Step 1: Write failing tests** for defaults, enum validation, title length, tag normalization, duplicate collapse, ten-tag limit, participant read access, assigned-agent update access, and non-owner rejection.
- [ ] **Step 2: Run the focused metadata tests** and confirm they fail because the fields, table, and service methods do not exist.
- [ ] **Step 3: Add `title`, `priority`, `category`, and `metadata_updated_at`** to fresh-install DDL and add the independent upgrade SQL that backfills existing rows with `新咨询` and `NORMAL`.
- [ ] **Step 4: Add `chat_session_tag` DDL, domain, mapper, and constants** with foreign-key cascade and the `(session_id, tag)` primary key.
- [ ] **Step 5: Add metadata DTO/VO fields** to the session list item and enforce exact allowed values and tag normalization in the service boundary.
- [ ] **Step 6: Implement transactional metadata read/update** with full replacement of tags and ownership checks.
- [ ] **Step 7: Add HTTP endpoints** with participant read access, assigned-agent update access, and `chat:session:metadata:update` permission.
- [ ] **Step 8: Run focused unit tests and `git diff --check`**, then commit with `git commit -m "feat: add session metadata and tags"`.

### Task 3: Implement fixed agent views

**Files:**
- Create: `commonModel/src/main/java/com/example/customerservice/constant/AgentSessionView.java`
- Create: `commonModel/src/main/java/com/example/customerservice/dto/AgentSessionViewVO.java`
- Create: `commonModel/src/main/java/com/example/customerservice/dto/AgentSessionViewCountVO.java`
- Modify: `commonModel/src/main/java/com/example/customerservice/mapper/ChatManagementMapper.java`
- Modify: `commonModel/src/main/resources/mapper/ChatManagementMapper.xml`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/ChatManagementQueryService.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatManagementQueryServiceImpl.java`
- Modify: `application/src/main/java/com/example/customerservice/controller/ChatManagementController.java`
- Create or modify: `application/src/test/java/com/example/customerservice/service/AgentSessionViewTest.java`

**Interfaces:**
- Produce `List<AgentSessionViewCountVO> findAgentSessionViews(String agentId)`.
- Produce `PageResult<ChatSessionListItemVO> findAgentViewSessions(String agentId, AgentSessionView view, long pageNo, long pageSize)`.
- Produce `GET /chat/agent/views` and `GET /chat/agent/views/{viewCode}/sessions`.

- [ ] **Step 1: Add failing tests** for all five view codes, view count, pagination bounds, unknown code rejection, and agent-ID binding.
- [ ] **Step 2: Run focused view tests** and confirm they fail before the enum and mapper methods exist.
- [ ] **Step 3: Define the exact views** `MY_ACTIVE`, `MY_UNREAD`, `MY_HIGH_PRIORITY`, `MY_UNARCHIVED`, and `MY_RECENT_CLOSED`.
- [ ] **Step 4: Add set-based mapper queries** for counts and list records, including metadata, last-message summary, archive state, and unread count without per-row queries.
- [ ] **Step 5: Add service validation and controller endpoints** requiring AGENT and `chat:session:view-own`; reject unknown view codes before DB access.
- [ ] **Step 6: Run focused tests and inspect generated SQL** for the authenticated `agent_id` predicate.
- [ ] **Step 7: Commit** with `git commit -m "feat: add fixed agent session views"`.

### Task 4: Scaffold Vue 3 foundation

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.js`
- Create: `frontend/src/main.js`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/router/index.js`
- Create: `frontend/src/stores/auth.js`
- Create: `frontend/src/stores/chat.js`
- Create: `frontend/src/services/http-client.js`
- Create: `frontend/src/services/stomp-client.js`
- Create: `frontend/src/api/auth-api.js`
- Create: `frontend/src/api/chat-api.js`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/UserWorkspaceView.vue`
- Create: `frontend/src/views/AgentWorkspaceView.vue`
- Create: `frontend/src/views/AdminWorkspaceView.vue`
- Create: `frontend/src/components/common/AppShell.vue`
- Create: `frontend/src/components/common/LoadingState.vue`
- Create: `frontend/src/components/common/ErrorState.vue`
- Create: `frontend/src/__tests__/router.spec.js`
- Create: `frontend/src/__tests__/auth-store.spec.js`

**Interfaces:**
- Produce route guards for `/user`, `/agent`, and `/admin`.
- Produce shared authenticated HTTP and STOMP client interfaces consumed by Tasks 5-7.

- [ ] **Step 1: Create the Vite project files** with Vue 3, Vue Router, Pinia, Vitest, Vue Test Utils, and `@stomp/stompjs` dependencies.
- [ ] **Step 2: Add failing router and auth-store tests** for anonymous redirect, role redirect, token restoration, and logout cleanup.
- [ ] **Step 3: Run `npm ci` and `npm test -- --run`** to confirm the tests fail only on missing implementation, not dependency setup.
- [ ] **Step 4: Implement the shared HTTP client, auth store, router guard, and STOMP service** using the existing login, WebSocket ticket, `/ws/chat`, and `/user/queue/*` contracts.
- [ ] **Step 5: Add minimal route-level workspace shells** and shared loading/error components.
- [ ] **Step 6: Run `npm test -- --run` and `npm run build`**, then commit with `git commit -m "feat: scaffold Vue workspace frontend"`.

### Task 5: Implement the user workspace

**Files:**
- Modify: `frontend/src/views/UserWorkspaceView.vue`
- Modify: `frontend/src/api/chat-api.js`
- Modify: `frontend/src/stores/chat.js`
- Create: `frontend/src/components/chat/ChatWindow.vue`
- Create: `frontend/src/components/chat/MessageList.vue`
- Create: `frontend/src/components/chat/MessageComposer.vue`
- Create: `frontend/src/components/session/UserSessionList.vue`
- Create: `frontend/src/components/session/SessionRatingForm.vue`
- Create: `frontend/src/__tests__/user-workspace.spec.js`

**Interfaces:**
- Consumes the shared auth and STOMP services from Task 4 and metadata-enriched `/chat/sessions` records from Tasks 1-2.
- Produces a working customer flow: queue status, consultation start, live chat, session history, offline pull, attachments, and rating.

- [ ] **Step 1: Add failing component tests** for queue display, consultation start, session selection, message sending, history loading, and one-time rating submission.
- [ ] **Step 2: Implement the user workspace layout** with queue panel, current chat, session history, and rating area.
- [ ] **Step 3: Connect REST actions** for queue status, session history, ratings, and attachments.
- [ ] **Step 4: Connect STOMP actions** for start, send, history, read, ACK, heartbeat, offline pull, edit, and recall.
- [ ] **Step 5: Run `npm test -- --run` and `npm run build`**, then commit with `git commit -m "feat: add Vue user workspace"`.

### Task 6: Implement the agent workspace and fixed views

**Files:**
- Modify: `frontend/src/views/AgentWorkspaceView.vue`
- Modify: `frontend/src/api/chat-api.js`
- Modify: `frontend/src/stores/chat.js`
- Create: `frontend/src/components/session/AgentViewNav.vue`
- Create: `frontend/src/components/session/AgentSessionList.vue`
- Create: `frontend/src/components/session/SessionMetadataEditor.vue`
- Create: `frontend/src/components/session/SessionArchiveActions.vue`
- Create: `frontend/src/components/chat/AgentChatWindow.vue`
- Create: `frontend/src/components/agent/QuickReplyPanel.vue`
- Create: `frontend/src/components/agent/UserProfileSidebar.vue`
- Create: `frontend/src/__tests__/agent-workspace.spec.js`

**Interfaces:**
- Consumes fixed view endpoints from Task 3, metadata endpoints from Task 2, and existing agent online/offline, transfer, archive, rating, quick-reply, and user-profile endpoints.
- Produces a working agent flow with five fixed views and metadata editing.

- [ ] **Step 1: Add failing component tests** for view selection, counts, pagination, metadata validation, update refresh, archive action, transfer action, and quick reply insertion.
- [ ] **Step 2: Implement the fixed-view navigation and session list** with selected-session state and unread badges.
- [ ] **Step 3: Implement metadata editing** for title, priority, category, and up to ten tags; submit the full replacement request.
- [ ] **Step 4: Integrate the existing agent chat operations** and render customer profile, transfer logs, archive controls, and quick replies.
- [ ] **Step 5: Run `npm test -- --run` and `npm run build`**, then commit with `git commit -m "feat: add Vue agent workspace"`.

### Task 7: Implement the administrator workspace

**Files:**
- Modify: `frontend/src/views/AdminWorkspaceView.vue`
- Modify: `frontend/src/api/admin-api.js`
- Create: `frontend/src/components/admin/AdminDashboard.vue`
- Create: `frontend/src/components/admin/UserManagementPanel.vue`
- Create: `frontend/src/components/admin/RoleManagementPanel.vue`
- Create: `frontend/src/components/admin/SessionAuditPanel.vue`
- Create: `frontend/src/components/admin/ArchiveStatsPanel.vue`
- Create: `frontend/src/components/admin/DeadLetterPanel.vue`
- Create: `frontend/src/__tests__/admin-workspace.spec.js`

**Interfaces:**
- Consumes existing administrator REST endpoints and the shared auth/API foundation from Task 4.
- Produces the administrator replacement for the admin panels currently embedded in `stomp-test.html`.

- [ ] **Step 1: Add failing tests** for admin route protection, dashboard loading, session audit filters, archive statistics, and dead-letter replay confirmation.
- [ ] **Step 2: Implement the admin dashboard and session audit panels** using the current query parameters and response envelopes.
- [ ] **Step 3: Implement user, role, VIP-skill, archive-statistics, and dead-letter panels** with explicit loading/error states.
- [ ] **Step 4: Run `npm test -- --run` and `npm run build`**, then commit with `git commit -m "feat: add Vue administrator workspace"`.

### Task 8: Integrate packaging, documentation, and regression checks

**Files:**
- Modify: `application/pom.xml`
- Modify: `pom.xml` if shared frontend build properties are required
- Modify: `frontend/vite.config.js`
- Create or modify: `application/src/main/java/com/example/customerservice/config/FrontendForwardingController.java`
- Modify: `README.md`
- Modify: `application/src/test/java/com/example/customerservice/integration/RealInfrastructureIntegrationTest.java` if endpoint coverage belongs there
- Create: `frontend/e2e/role-workflows.spec.js` or document the manual browser regression flow if no browser runner is available

**Interfaces:**
- Consumes the complete frontend from Tasks 4-7 and all backend endpoints from Tasks 1-3.
- Produces a Spring Boot JAR containing the production Vue build and documented development/test commands.

- [ ] **Step 1: Add the frontend packaging configuration** with pinned Node/npm execution, `npm ci`, `npm run build`, generated-resource copying, and static resource inclusion.
- [ ] **Step 2: Add SPA forwarding** for `/user`, `/agent`, and `/admin` without intercepting REST, actuator, attachment, or `/ws/chat` paths.
- [ ] **Step 3: Add backend endpoint regression tests** for the four new endpoint groups and run the complete Java test suite.
- [ ] **Step 4: Run `npm ci`, `npm test -- --run`, `npm run build`, and the Maven package command** in a writable Maven-cache environment; record exit codes and test counts.
- [ ] **Step 5: Run the opt-in MySQL/Redis integration suite** when credentials and services are available; otherwise report it as unverified.
- [ ] **Step 6: Update README** with Vue development, production packaging, migration order, routes, and the legacy test-page status.
- [ ] **Step 7: Run `git diff --check`, inspect `git status`, and commit** with `git commit -m "feat: package Vue chat workspaces"`.

## Final Review Checklist

- [ ] Re-read the approved spec and confirm every section maps to at least one task.
- [ ] Confirm no task accepts arbitrary SQL predicates or client-supplied actor IDs.
- [ ] Confirm all DTO, enum, mapper, endpoint, and frontend store names match across tasks.
- [ ] Confirm the tag migration is safe for existing databases.
- [ ] Confirm the Vue build is included in the executable JAR.
- [ ] Confirm full Java, frontend, and available infrastructure verification results are reported separately.
