# MinIO Attachment Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all chat image and file storage from the local attachment directory to a private MinIO bucket, with an explicit, verifiable one-time migration command for existing files.

**Architecture:** Keep the existing `/chat/attachments` HTTP contract and database metadata. Add one MinIO storage component for object operations, keep participant authorization and file validation in `ChatAttachmentServiceImpl`, and add a migration runner that uploads, re-reads, checksums, updates database keys, and only then removes the local directory.

**Tech Stack:** Java 17, Spring Boot 4.0.7, MyBatis-Plus, MinIO Java SDK 9.0.3, Docker Compose, JUnit 5, Mockito, real-service tests gated by `RUN_REAL_INTEGRATION_TESTS=true`.

**Spec:** `docs/superpowers/specs/2026-08-25-minio-attachment-storage-design.md`

## Global Constraints

- MinIO uses a private Bucket; the frontend never receives MinIO credentials or direct object URLs.
- Existing frontend endpoints and attachment message content URLs remain unchanged.
- Normal Spring Boot startup fails when MinIO is unavailable, credentials are invalid, or the Bucket is public.
- Migration runs only when `ATTACHMENT_MIGRATION_ENABLED=true` and exits with a non-zero code on any failed validation or cleanup step.
- Local files are removed only after every object is uploaded, read back, checksum-verified, and database keys are committed.
- Existing file validation rules and the 10 MB attachment limit remain unchanged.
- Ordinary Maven tests do not require Docker; real MinIO tests are opt-in.

---

### Task 1: MinIO dependency, configuration, and Docker service

**Files:**
- Modify: `pom.xml`
- Modify: `application/pom.xml`
- Modify: `businessModel/pom.xml`
- Modify: `application/src/main/resources/application.yml`
- Modify: `application/src/main/resources/application-prod.yml`
- Create: `compose.yml`
- Create: `.env.example`
- Modify: `.gitignore`
- Create: `businessModel/src/main/java/com/example/customerservice/config/MinioAttachmentProperties.java`
- Create: `businessModel/src/main/java/com/example/customerservice/config/MinioAttachmentConfiguration.java`
- Test: `application/src/test/java/com/example/customerservice/config/MinioConfigurationTest.java`

**Interfaces:**
- Produces `MinioAttachmentProperties` values for the storage and initializer components.
- Produces a `MinioClient` bean configured from `app.chat.attachment.minio.*`.

- [ ] **Step 1: Write the failing configuration test**

  Add a Spring test that binds endpoint, access key, secret key, bucket, orphan grace period, migration enabled flag, and legacy path from properties, and asserts the MinIO client bean uses the configured endpoint. The test must use a local test context and must not require a live MinIO server.

- [ ] **Step 2: Run the configuration test to verify it fails**

  Run:

  ```powershell
  mvn -pl application -am -Dtest=MinioConfigurationTest test
  ```

  Expected: compilation or bean-resolution failure because the properties class and MinIO dependency do not yet exist.

- [ ] **Step 3: Add dependency and configuration**

  Add the MinIO version property and `io.minio:minio` dependency to the business module (where the storage component is compiled), while keeping the version in the parent POM. Create `MinioAttachmentProperties` with endpoint, access key, secret key, bucket, orphan grace period, migration enabled, and legacy storage path. Create configuration that validates required credentials and builds `MinioClient.builder().endpoint(...).credentials(...).build()`.

  Add YAML defaults and production environment bindings. Add Compose with a pinned MinIO image, ports `9000` and `9001`, a named volume, root credentials from `.env`, and an API health check. Add `.env.example` and ignore the real `.env`.

- [ ] **Step 4: Run the configuration test to verify it passes**

  Run the same Maven command and expect the test to pass without Docker.

- [ ] **Step 5: Commit the task**

  ```powershell
  git add pom.xml application/pom.xml businessModel/pom.xml application/src/main/resources/application.yml application/src/main/resources/application-prod.yml compose.yml .env.example .gitignore businessModel/src/main/java/com/example/customerservice/config application/src/test/java/com/example/customerservice/config/MinioConfigurationTest.java
  git commit -m "feat: add MinIO configuration and local service"
  ```

### Task 2: Private Bucket initialization and storage component

**Files:**
- Create: `businessModel/src/main/java/com/example/customerservice/storage/MinioAttachmentStorage.java`
- Create: `businessModel/src/main/java/com/example/customerservice/config/MinioAttachmentInitializer.java`
- Create: `businessModel/src/main/java/com/example/customerservice/exception/AttachmentStorageException.java`
- Test: `application/src/test/java/com/example/customerservice/storage/MinioAttachmentStorageTest.java`
- Test: `application/src/test/java/com/example/customerservice/config/MinioAttachmentInitializerTest.java`

**Interfaces:**
- `MinioAttachmentStorage.put(String objectKey, InputStream input, long size, String contentType)` uploads an object.
- `MinioAttachmentStorage.open(String objectKey)` returns an `InputStream` whose close releases the MinIO response.
- `MinioAttachmentStorage.stat(String objectKey)` returns object size and last-modified metadata.
- `MinioAttachmentStorage.delete(String objectKey)` deletes one object and converts SDK failures to `AttachmentStorageException`.
- `MinioAttachmentStorage.list(String prefix)` returns object metadata pages for cleanup.
- `MinioAttachmentInitializer` checks connectivity, creates the bucket when absent, and rejects a public anonymous policy.

- [ ] **Step 1: Write failing storage and initializer tests**

  Cover upload argument propagation, stream closure, SDK exception conversion, bucket creation when absent, startup failure when the endpoint is unavailable, and startup failure when the bucket has an anonymous public policy. Use a mocked `MinioClient` for ordinary tests.

- [ ] **Step 2: Run the tests and verify the expected failures**

  Run:

  ```powershell
  mvn -pl application -am -Dtest=MinioAttachmentStorageTest,MinioAttachmentInitializerTest test
  ```

  Expected: missing classes and methods.

- [ ] **Step 3: Implement the storage wrapper and initializer**

  Use the MinIO SDK `BucketExistsArgs`, `MakeBucketArgs`, `PutObjectArgs`, `GetObjectArgs`, `StatObjectArgs`, `RemoveObjectArgs`, and `ListObjectsArgs`. Use `try-with-resources` for `GetObjectResponse`. Treat a missing bucket policy as private; reject an explicit anonymous read policy. Do not include credentials, authorization headers, or full object paths in user-facing exceptions.

- [ ] **Step 4: Run the tests and verify they pass**

  Run the same Maven command and expect all storage and initializer tests to pass.

- [ ] **Step 5: Commit the task**

  ```powershell
  git add businessModel/src/main/java/com/example/customerservice/storage businessModel/src/main/java/com/example/customerservice/config/MinioAttachmentInitializer.java businessModel/src/main/java/com/example/customerservice/exception/AttachmentStorageException.java application/src/test/java/com/example/customerservice/storage application/src/test/java/com/example/customerservice/config/MinioAttachmentInitializerTest.java
  git commit -m "feat: add private MinIO attachment storage"
  ```

### Task 3: Switch normal upload and download to MinIO

**Files:**
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatAttachmentServiceImpl.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/service/ChatAttachmentService.java`
- Modify: `application/src/main/java/com/example/customerservice/controller/ChatAttachmentController.java`
- Modify: `commonModel/src/main/java/com/example/customerservice/domain/ChatAttachment.java` only if mapper update support requires it
- Test: `application/src/test/java/com/example/customerservice/service/ChatAttachmentServiceImplTest.java`
- Test: `application/src/test/java/com/example/customerservice/controller/ChatAttachmentControllerTest.java`

**Interfaces:**
- Existing `ChatAttachmentService.upload`, `requireAccessible`, and controller routes remain source-compatible for the frontend.
- `ChatAttachmentService.load` returns a Spring `Resource` backed by a MinIO object stream.

- [ ] **Step 1: Add failing tests for MinIO-backed upload and download**

  Extend the service tests to assert that upload writes an object key of the form `sessions/S1/{id}.jpg`, then stores that key in `storedName`. Add a failure test asserting a database insert error invokes object deletion. Add a download test asserting the controller still returns the same content type, content disposition, and length after reading from the MinIO-backed resource. Preserve the existing security and file-validation tests.

- [ ] **Step 2: Run the focused tests and verify they fail**

  ```powershell
  mvn -pl application -am -Dtest=ChatAttachmentServiceImplTest,ChatAttachmentControllerTest test
  ```

- [ ] **Step 3: Replace local filesystem operations**

  Keep `validateActualType` and participant checks. Replace `Files.copy` with `MinioAttachmentStorage.put`. Register transaction rollback cleanup for the object key. Replace `FileSystemResource` with an `InputStreamResource` or equivalent resource that opens and closes the MinIO response. Keep `contentUrl` as `/chat/attachments/{id}/content`.

- [ ] **Step 4: Run focused tests and verify they pass**

  Run the same command. Confirm that the old local storage path is not used by normal upload or download code.

- [ ] **Step 5: Commit the task**

  ```powershell
  git add businessModel/src/main/java/com/example/customerservice/service/ChatAttachmentService.java businessModel/src/main/java/com/example/customerservice/service/impl/ChatAttachmentServiceImpl.java application/src/main/java/com/example/customerservice/controller/ChatAttachmentController.java application/src/test/java/com/example/customerservice/service/ChatAttachmentServiceImplTest.java application/src/test/java/com/example/customerservice/controller/ChatAttachmentControllerTest.java
  git commit -m "feat: store chat attachments in MinIO"
  ```

### Task 4: Implement one-time historical migration

**Files:**
- Create: `application/src/main/java/com/example/customerservice/migration/ChatAttachmentMigrationRunner.java`
- Create: `businessModel/src/main/java/com/example/customerservice/service/ChatAttachmentMigrationService.java`
- Create: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatAttachmentMigrationServiceImpl.java`
- Modify: `businessModel/src/main/java/com/example/customerservice/mapper/ChatAttachmentMapper.java` if a batch key update is needed
- Modify: `commonModel/src/main/java/com/example/customerservice/mapper/ChatAttachmentMapper.xml` if a batch key update is needed
- Test: `application/src/test/java/com/example/customerservice/migration/ChatAttachmentMigrationServiceTest.java`

**Interfaces:**
- `ChatAttachmentMigrationService.migrate(Path legacyRoot)` returns a report with total, uploaded, reused, verified, database-updated, deleted, and failed counts.
- `ChatAttachmentMigrationRunner` runs only when migration is enabled, prints the report, exits zero on complete success and non-zero on failure.

- [ ] **Step 1: Write failing migration tests**

  Add tests for: all records migrate and the directory is removed; missing local file preserves the directory; checksum mismatch preserves the directory; an unreferenced local file fails the migration; a rerun reuses a matching MinIO object; and a database update failure leaves the directory intact.

- [ ] **Step 2: Run the migration tests and verify they fail**

  ```powershell
  mvn -pl application -am -Dtest=ChatAttachmentMigrationServiceTest test
  ```

- [ ] **Step 3: Implement migration service**

  Read attachment records, derive the legacy local file safely, calculate SHA-256 with a stream, upload to deterministic object keys, read each object back and compare size plus SHA-256, then update every `storedName` in one database transaction. If the database already contains the target object key, derive the legacy filename from attachment ID and original extension so a cleanup retry remains possible. Before deletion, revalidate every database key and object checksum. Atomically rename the legacy directory to a sibling pending-delete directory, then recursively delete that directory; preserve the pending directory and return failure if cleanup fails.

- [ ] **Step 4: Implement migration runner and command behavior**

  Activate the runner only for `ATTACHMENT_MIGRATION_ENABLED=true`. Set the exit code through `SpringApplication.exit`, use `spring.main.web-application-type=none` in the documented command, and ensure normal startup does not run migration. Normal startup must reject non-empty legacy or pending-delete directories.

- [ ] **Step 5: Run migration tests and verify they pass**

  Run the focused command and inspect the report fields for both success and failure fixtures.

- [ ] **Step 6: Commit the task**

  ```powershell
  git add application/src/main/java/com/example/customerservice/migration businessModel/src/main/java/com/example/customerservice/service/ChatAttachmentMigrationService.java businessModel/src/main/java/com/example/customerservice/service/impl/ChatAttachmentMigrationServiceImpl.java businessModel/src/main/java/com/example/customerservice/mapper commonModel/src/main/java/com/example/customerservice/mapper
  git commit -m "feat: add one-time attachment migration"
  ```

### Task 5: Replace local orphan cleanup and add real MinIO integration coverage

**Files:**
- Modify: `businessModel/src/main/java/com/example/customerservice/service/impl/ChatAttachmentServiceImpl.java`
- Modify: `application/src/main/java/com/example/customerservice/scheduler/ChatAttachmentCleanupScheduler.java` only for renamed report wording
- Test: `application/src/test/java/com/example/customerservice/service/ChatAttachmentServiceImplTest.java`
- Create: `application/src/test/java/com/example/customerservice/integration/MinioAttachmentIntegrationTest.java`
- Modify: `README.md`

**Interfaces:**
- Existing scheduler calls `cleanupOrphanFiles`; the implementation lists MinIO objects rather than local files.
- Real integration test runs only when `RUN_REAL_INTEGRATION_TESTS=true` and uses `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, and `MINIO_BUCKET`.

- [ ] **Step 1: Write failing cleanup and integration tests**

  Add unit fixtures for referenced objects, old unreferenced objects, young unreferenced objects, and system-prefix objects. Add an opt-in integration test for real upload, stat, download, delete, and private anonymous access rejection.

- [ ] **Step 2: Run unit tests to verify the new cleanup expectations fail**

  ```powershell
  mvn -pl application -am -Dtest=ChatAttachmentServiceImplTest test
  ```

- [ ] **Step 3: Replace cleanup implementation**

  Page through MinIO objects under `sessions/`, compare object keys to database references in batches, and delete only unreferenced objects older than the configured grace period. Keep the distributed scheduler lock and update log messages from “文件” to “对象”.

- [ ] **Step 4: Run unit tests and verify they pass**

  Run the focused test command again.

- [ ] **Step 5: Run real MinIO integration tests**

  Start the Compose service, then run:

  ```powershell
  $env:RUN_REAL_INTEGRATION_TESTS='true'
  mvn -pl application -am -Dtest=MinioAttachmentIntegrationTest test
  ```

  Expected: real upload, private access, stream download, stat, and delete all pass. If Docker or credentials are unavailable, report the environment block rather than claiming integration coverage.

- [ ] **Step 6: Update README and commit the task**

  Document Compose startup, `.env` creation, migration command, normal Spring Boot command, MinIO Console URL, required environment variables, failure behavior, and real integration test switch.

  ```powershell
  git add businessModel/src/main/java/com/example/customerservice/service/impl/ChatAttachmentServiceImpl.java application/src/main/java/com/example/customerservice/scheduler/ChatAttachmentCleanupScheduler.java application/src/test/java/com/example/customerservice/service/ChatAttachmentServiceImplTest.java application/src/test/java/com/example/customerservice/integration/MinioAttachmentIntegrationTest.java README.md
  git commit -m "test: verify MinIO attachment lifecycle"
  ```

### Task 6: Full verification and handoff

**Files:**
- Verify: all files changed by Tasks 1–5

- [ ] **Step 1: Run source and diff checks**

  ```powershell
  git diff --check
  Get-ChildItem -Path application,businessModel,commonModel -Recurse -File | Select-String -Pattern 'chat-attachments|FileSystemResource|Files.copy|Files.list'
  ```

  Expected: no normal runtime upload/download path remains on the local filesystem; any remaining legacy path usage is limited to migration checks and documentation.

- [ ] **Step 2: Run all ordinary backend tests**

  ```powershell
  mvn test
  ```

  Record the exact pass/fail/skip counts. If Maven cache or external service access blocks the run, report that limitation separately.

- [ ] **Step 3: Run frontend tests and build**

  ```powershell
  npm.cmd test -- --run
  npm.cmd run build
  ```

  Confirm the existing frontend bundle is still synchronized to `application/target/classes/static/frontend` and no frontend API contract changed.

- [ ] **Step 4: Run the end-to-end local acceptance flow**

  Start Compose, run migration against a controlled fixture or real existing directory, start Spring Boot, upload/download an image and file as both user and agent, verify unauthorized access, stop MinIO, and verify backend startup fails clearly.

- [ ] **Step 5: Review final status and report**

  ```powershell
  git status --short
  git log -6 --oneline
  ```

  Report changed files, migration command, ordinary test evidence, real-service test evidence, and any environment checks that could not be completed.
