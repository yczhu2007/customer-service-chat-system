# Chat Session Workspaces Design

**Date:** 2026-08-21

**Status:** Approved in chat

## Goal

Turn the existing function-verification page into a small Vue 3 customer-service application with separate user, agent, and administrator workspaces, while fixing session-query authorization and adding lightweight ticket metadata and fixed agent views.

## Scope

This design covers four changes:

1. Correct session-query authorization and data scope.
2. Replace the single role-mixed test page with one Vue 3 application containing three role-specific workspaces.
3. Add session title, priority, category, and tags.
4. Add fixed agent session views.

The design does not add configurable view rules, SLA policies, agent groups, omnichannel ingestion, a knowledge base, or a general workflow engine.

## Architecture

The backend remains a Spring Boot modular application. The existing controller, service, MyBatis, MySQL, Redis, and STOMP boundaries are retained. Session-list authorization becomes explicit by passing a participant type to the service instead of querying `user_id OR agent_id` for every caller.

A new `frontend/` directory contains one Vue 3 and Vite application. Vue Router exposes role-specific workspaces at `/user`, `/agent`, and `/admin`. Authentication, HTTP access, STOMP connectivity, message rendering, and common layout components are shared.

Implementation proceeds from backend authorization and schema changes to APIs, then to the Vue foundation and role workspaces. The legacy `stomp-test.html` remains available until the Vue replacement passes integration checks.

## Session Authorization and Data Scope

`GET /chat/sessions` remains the participant-facing session-list endpoint.

- A `USER` query is restricted to `chat_session.user_id = currentUserId`.
- An `AGENT` query is restricted to `chat_session.agent_id = currentUserId`.
- An `ADMIN` cannot use this endpoint and continues to use `GET /chat/admin/sessions`.
- If an account has both `AGENT` and `USER`, `AGENT` scope takes precedence.
- The client cannot provide a participant ID or role to widen the query.

The controller resolves an internal `SessionParticipantType` value and passes it with the authenticated user ID to `ChatSessionQueryService`. The service applies one exact predicate for that type. This replaces the current implicit `user_id = participantId OR agent_id = participantId` query.

A new `chat:session:view-own` permission is assigned to the built-in USER and AGENT roles. The endpoint requires that permission in addition to an allowed role.

## Session Metadata

The `chat_session` table gains these columns:

| Column | Type | Rules |
|---|---|---|
| `title` | `VARCHAR(100)` | Not null; default `新咨询` |
| `priority` | `VARCHAR(16)` | Not null; default `NORMAL`; one of `LOW`, `NORMAL`, `HIGH`, `URGENT` |
| `category` | `VARCHAR(32)` | Nullable; one of `ACCOUNT`, `PAYMENT`, `TECHNICAL`, `AFTER_SALES`, `OTHER` |
| `metadata_updated_at` | `DATETIME(6)` | Nullable until the first metadata update |

Existing rows receive the default title and priority during migration. The fresh-install DDL and a separate upgrade script are both updated because `CREATE TABLE IF NOT EXISTS` cannot upgrade an existing table.

Tags use a normalized `chat_session_tag` table:

| Column | Type | Rules |
|---|---|---|
| `session_id` | `VARCHAR(64)` | Foreign key to `chat_session`; cascade on delete |
| `tag` | `VARCHAR(32)` | Normalized lowercase value |
| `create_time` | `DATETIME(6)` | Creation timestamp |

The primary key is `(session_id, tag)`. A session may have at most ten tags. Blank tags are rejected, surrounding whitespace is removed, tags are converted to lowercase with `Locale.ROOT`, and duplicates after normalization are collapsed.

New sessions start with `title = 新咨询`, `priority = NORMAL`, no category, and no tags. In this scope, metadata is edited by the assigned agent rather than collected during queue entry, so queued-user Redis state does not need a new payload format.

## Metadata API

Two endpoints are added:

- `GET /chat/sessions/{sessionId}/metadata`
- `PUT /chat/sessions/{sessionId}/metadata`

The response contains `sessionId`, `title`, `priority`, `category`, `tags`, and `metadataUpdatedAt`.

The update request contains the full replacement state for `title`, `priority`, `category`, and `tags`. Full replacement keeps the operation deterministic and avoids partial tag-update semantics.

Read access is allowed to the session's user, assigned agent, and administrators with session-audit permission. Update access requires the AGENT role, `chat:session:metadata:update`, and `chat_session.agent_id = currentUserId`. Administrators do not edit metadata through this endpoint.

The service updates the session row and replaces tags in one database transaction. Validation errors return the existing standard error envelope. A missing session returns the existing not-found boundary. A non-owner update returns an authorization error without revealing unrelated session details.

## Fixed Agent Views

Fixed views are backend-owned enums; the frontend does not send arbitrary predicates.

| Code | Rule |
|---|---|
| `MY_ACTIVE` | Assigned to the current agent and `status = ACTIVE` |
| `MY_UNREAD` | Assigned to the current agent and unread count greater than zero |
| `MY_HIGH_PRIORITY` | Assigned to the current agent, active, and priority `HIGH` or `URGENT` |
| `MY_UNARCHIVED` | Assigned to the current agent, closed, and `archive_status IS NULL` |
| `MY_RECENT_CLOSED` | Assigned to the current agent and closed in the previous seven days |

Endpoints:

- `GET /chat/agent/views` returns each view's code, display name, and current count.
- `GET /chat/agent/views/{viewCode}/sessions?pageNo=1&pageSize=20` returns the same enriched session-list item shape used by the agent workspace.

Both endpoints require the AGENT role and `chat:session:view-own`. Every query binds the authenticated agent ID. Unknown view codes are rejected before database access. Pagination is bounded to 1 through 100 records per page.

The fixed-view list query returns session metadata, last-message summary, unread count, archive state, and timestamps. Mapper SQL performs set-based joins or subqueries; it must not issue one query per session.

## Vue 3 Frontend

The frontend uses Vue 3 Composition API, Vite, Vue Router, Pinia, native `fetch`, and `@stomp/stompjs`.

Routes:

- `/login` for shared authentication, registration, and password recovery entry points.
- `/user` for the customer workspace.
- `/agent` for the agent workspace.
- `/admin` for the administrator workspace.

The route guard restores the authenticated profile, checks required roles, redirects unauthorized users to their permitted workspace, and redirects anonymous users to `/login`. Role information is never treated as backend authorization; all backend endpoints still enforce RBAC and data scope.

Shared frontend boundaries:

- `api/` contains feature-specific HTTP functions and a common authenticated request wrapper.
- `stores/auth.js` owns the token, profile, roles, login, logout, and restoration flow.
- `stores/chat.js` owns selected session and message state.
- `services/stomp-client.js` owns ticket acquisition, connection lifecycle, subscriptions, heartbeats, reconnects, and disconnect cleanup.
- `components/chat/` renders messages, composer actions, attachments, read state, edits, and recalls.
- `components/session/` renders session rows, metadata controls, archive state, and view navigation.

Workspace responsibilities:

- User workspace: queue state, consultation start, live chat, attachments, own session history, offline-message recovery, and rating.
- Agent workspace: online state, fixed views, active and historical sessions, live chat, metadata editing, transfer, archive operations, quick replies, and customer sidebar.
- Administrator workspace: dashboard, user and role management, VIP skills, audit search, archive statistics, rating summaries, and dead-letter replay.

The current test page is used as a behavioral reference, not copied as one large Vue component. Functions are migrated by responsibility. The old page is removed only after all three workspaces pass the agreed regression checks.

## Development and Packaging

Vite proxies backend HTTP paths and `/ws/chat` to the Spring Boot application during development. WebSocket proxying preserves the one-time ticket query string.

The Maven build invokes a pinned Node and npm toolchain for `npm ci` and `npm run build`, then packages the Vite output into the application JAR's `static` resources. The generated files remain under build output and are not committed. A frontend-only development workflow remains available through `npm run dev`.

No server-side fallback is needed for browser refreshes if Spring Boot maps `/user`, `/agent`, and `/admin` to the Vue `index.html`; that forwarding controller must not intercept REST, actuator, attachment, or WebSocket paths.

## Testing

Backend tests cover:

- USER and AGENT session scopes and ADMIN rejection.
- Dual-role precedence.
- No cross-user or cross-agent session leakage.
- Metadata validation, normalization, tag limit, transaction behavior, and ownership.
- Every fixed-view predicate, count, pagination bound, unknown code, and agent binding.
- Mapper integration against real MySQL through the existing opt-in integration-test path.

Frontend tests use Vitest and Vue Test Utils for:

- Route guards and role redirects.
- Authenticated request behavior and logout on invalid tokens.
- Fixed-view selection and pagination.
- Metadata form validation and update refresh.
- STOMP lifecycle and event-to-store updates with a mocked client.

End-to-end regression checks cover login and one representative workflow for each role. Existing Java tests and the real Redis/MySQL integration suite remain separate verification layers.

## Delivery Sequence

1. Correct participant session scope and add authorization tests.
2. Add the schema migration, domain fields, tag model, and metadata DTOs.
3. Add metadata read and update services and endpoints.
4. Add fixed-view query services and endpoints.
5. Scaffold the Vue application and shared auth, API, routing, and STOMP boundaries.
6. Implement the user workspace.
7. Implement the agent workspace, including fixed views and metadata editing.
8. Implement the administrator workspace.
9. Integrate frontend packaging, run backend/frontend/integration checks, update documentation, and retire the legacy page after parity is demonstrated.

## Acceptance Criteria

- Users and agents can call `GET /chat/sessions` but can only see records in their exact role scope.
- Administrators use only the administrator audit endpoint for cross-user session search.
- Session title, priority, category, and normalized tags persist and appear in participant lists and agent views.
- Only the currently assigned agent can update metadata.
- All five fixed views return correct counts and paginated results without accepting arbitrary query predicates.
- `/user`, `/agent`, and `/admin` expose role-specific Vue workspaces and reject unauthorized navigation.
- Existing chat, attachment, offline message, transfer, archive, rating, RBAC, dashboard, and dead-letter workflows remain available in the appropriate workspace.
- Maven packages the production Vue build into the executable application JAR.
- Unit tests pass; real MySQL/Redis and browser workflows are reported separately and are not inferred from compilation success.
