# ReportAI Master Plan

## Mission

Build a production-ready, tenant-aware AI document automation platform whose first product is automated university experiment reports.

## Phase 1 — Platform foundation

- [x] Multi-module Maven skeleton
- [x] PostgreSQL/Flyway baseline schema
- [x] Idempotent task creation and optimistic-lock task aggregate
- [x] Authentication/authorization ports and tenant context boundary
- [x] Secure DOCX parser boundary and Apache POI adapter
- [x] Workflow state machine and persistence ports
- [x] Configuration properties and environment validation
- [x] Centralized error, audit, metrics and rate-limit infrastructure
- [x] Automated Maven CI build/test gate

## Phase 2 — Document ingestion and export

- [x] MinIO object-storage adapter
- [x] Document upload API
- [x] Persist parsed canonical Document JSONB
- [x] DOCX asset extraction, SHA-256 metadata and tenant-confined object references
- [x] Tenant-bound object resolver with streaming size/checksum verification
- [x] Durable object integrity verification status and bounded orphan-metadata reconciliation
- [x] Word builder with paragraphs, tables, code and images
- [x] Export/download authorization with bounded output and checksum verification

## Phase 3 — Task and workflow execution

- [x] Task HTTP API
- [x] Workflow HTTP API
- [x] PostgreSQL workflow-definition and workflow-instance adapters
- [x] Resume/retry/cancel semantics and durable workflow event log
- [x] Scheduler and bounded worker pools
- [x] PostgreSQL scheduler task store with lease/retry conditional updates
- [x] Transactional scheduler outbox with lease-based publication and restart recovery
- [x] Progress event contracts and authenticated bounded SSE adapter

## Phase 4 — Sandboxed runtimes

- [x] Runtime policy model and image allow-list
- [x] Docker execution adapter
- [x] Python execution tool
- [x] Java execution tool
- [x] Artifact collection, output quotas and cleanup
- [x] Runtime security/load tests

## Phase 5 — Tool platform

- [x] Tool SPI and registry hardening
- [x] Chart/image tools
- [ ] OCR/search/browser tools with SSRF controls
- [ ] Tool audit log and retry policy

## Phase 6 — LLM and agent platform

- [x] Provider-neutral chat/tool/vision/embedding ports
- [x] Prompt repository and versioning
- [x] Request budget/response validation
- [x] Bounded agent orchestrator and LLM/tool-backed agents
- [x] Planner, coder, runtime, document and reviewer domain agents
- [x] Versioned PostgreSQL prompt repository with active-version invariant
- [x] Tenant-scoped conversation/message persistence ports and PostgreSQL adapter
- [x] Conversation memory compaction and context-window orchestration with bounded deterministic fallback
- [x] Durable conversation memory snapshots with CAS and tenant-bound through-cursor
- [x] Scheduled memory snapshot compaction with bounded retries and Micrometer metrics
- [x] Structured tool-call validation and budget controls

## Phase 7 — Review, release and operations

- [x] Review rules and completeness scoring
- [x] Review-gated Word release service
- [ ] Observability dashboards and alerts
- [x] Backup/restore and disaster-recovery runbook
- [ ] Security, performance, chaos and end-to-end acceptance evidence (opt-in infrastructure suite is now scaffolded)
- [x] Production release checklist
- [ ] Final production-readiness review and approval
- [x] Executable Spring Boot JAR packaging and Java 21/Maven release-toolchain gate

## Phase 0 - AI software company operating system

- [x] Executive, product and project-management role contracts
- [x] Architecture, engineering, quality, security and operations role contracts
- [x] Development, review, security, Git and release governance
- [x] Portfolio roadmap, status, task board, risk register and project templates
- [ ] Cross-functional review and named owners for the first implementation wave

## Phase 8 - Agent observability and platform expansion

- [ ] Durable task progress-event repository with tenant-safe bounded history API
- [ ] Nexus Agent Observatory timeline, reconnect and recovery UX
- [ ] Application manifest/version/health contract for Mac for Web apps
- [ ] OCR/search/browser tools with SSRF, quota and audit controls

## Frontend baseline

- [x] Vue3 + Vite + TypeScript + NaiveUI task console
- [x] Authenticated upload, document-id task creation, progress streaming and export download
