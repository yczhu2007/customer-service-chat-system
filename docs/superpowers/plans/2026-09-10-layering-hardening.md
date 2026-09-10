# Layering Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Remove the nine verified layering, hard-coding, responsibility, and robustness issues without changing user-visible behavior.

**Architecture:** Application entry points delegate to business services. Shared domain values and STOMP destinations use existing constant packages. Attachment transport is converted at the controller boundary, file-content validation becomes a focused helper, and frontend attachment preview/API concerns move out of oversized components.

**Tech Stack:** Spring Boot, MyBatis-Plus, Redis, Vue 3, Pinia, Vitest.

**Spec:** Approved review findings in the current task.

## Global Constraints

- Preserve all HTTP/STOMP routes and response payloads.
- Add no dependency.
- Prefer one shared constant/helper over new abstraction hierarchies.
- Every non-trivial production change starts with a focused failing test.

---

### Task 1: Application entry-point delegation

**Files:**
- Modify: `application/src/main/java/com/example/customerservice/runner/AdminBootstrapRunner.java`
- Modify: `application/src/main/java/com/example/customerservice/runner/VipAgentSkillCacheInitializer.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/ChatAgentOperations.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatAgentService.java`
- Create: `businessModel/src/main/java/com/example/customerservice/service/AdminBootstrapService.java`
- Test: `application/src/test/java/com/example/customerservice/runner/ApplicationRunnerLayeringTest.java`

**Interfaces:**
- Produces: `AdminBootstrapService.initialize(...)` and `ChatAgentOperations.rebuildVipSkillCache()`.

- [x] Write a reflection/source-boundary test proving runners depend only on services.
- [x] Run the focused test and verify it fails on current Mapper/Repository fields.
- [x] Move existing logic unchanged into business services; leave runners as configuration adapters.
- [x] Run the focused test and existing agent service tests.

### Task 2: Shared domain and messaging constants

**Files:**
- Create: `commonModel/src/main/java/com/example/customerservice/constant/AccountStatus.java`
- Create: `commonModel/src/main/java/com/example/customerservice/constant/ChatDestinations.java`
- Modify: affected authentication, RBAC, chat, bootstrap and notification classes.
- Test: `application/src/test/java/com/example/customerservice/constant/SharedConstantsTest.java`

**Interfaces:**
- Produces: `AccountStatus.ENABLED`, `AccountStatus.DISABLED`, and `ChatDestinations.USER_CHAT_QUEUE`.

- [x] Write a test asserting the canonical values.
- [x] Verify it fails because the constants do not exist.
- [x] Add constants and replace duplicated production literals.
- [x] Run the focused constant and chat tests.

### Task 3: Attachment boundary and validation responsibility

**Files:**
- Create: `businessModel/src/main/java/com/example/customerservice/dto/AttachmentUpload.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/dto/AttachmentDownload.java`
- Create: `businessModel/src/main/java/com/example/customerservice/service/impl/AttachmentContentValidator.java`
- Modify: attachment controller/service and their existing tests.

**Interfaces:**
- Consumes: controller-created `AttachmentUpload(filename, content)`.
- Produces: `AttachmentDownload.inline()` so the controller does not inspect message types.

- [x] Change tests to require a web-independent service signature and inline download descriptor.
- [x] Verify compilation/tests fail against the current API.
- [x] Implement the DTO boundary and extract existing validation logic unchanged.
- [x] Run all attachment tests.

### Task 4: Reconciliation named policy values

**Files:**
- Modify: `ChatSessionReconciliationService.java`.
- Test: existing session reconciliation/configuration tests.

**Interfaces:** No external API change.

- [x] Add a source contract test rejecting unexplained reconciliation literals.
- [x] Verify it fails.
- [x] Replace literals with narrowly named private constants.
- [x] Run focused scheduler/service tests.

### Task 5: Frontend API and attachment preview boundaries

**Files:**
- Create: `frontend/src/composables/use-attachment-preview.js`
- Modify: `frontend/src/components/chat/MessageList.vue`
- Modify: `frontend/src/api/admin-api.js`
- Modify: `frontend/src/components/admin/SessionAuditPanel.vue`
- Test: existing workspace tests plus a focused boundary test.

**Interfaces:**
- Produces: a composable owning preview state, blob cleanup and Office/text parsing; API functions for session audit requests.

- [x] Write source-boundary tests for API delegation and preview extraction.
- [x] Verify the tests fail against current component code.
- [x] Move existing behavior without changing UI text or routes.
- [x] Run focused tests and the full frontend suite.

### Task 6: Full verification

- [x] Run `mvnw.cmd -q -pl application -am test` and inspect Surefire totals.
- [x] Run `node node_modules/vitest/vitest.mjs run`.
- [x] Run `npm.cmd run build`.
- [x] Run `git diff --check` and review the final diff against all nine findings.

