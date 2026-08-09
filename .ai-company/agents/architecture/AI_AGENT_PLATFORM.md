# AI Agent Platform Architecture

## Role

Define the reusable internal platform contract that powers the ReportAI/Nexus
product today and future Mac for Web applications tomorrow. The platform is a
controlled execution system, not an unconstrained chat wrapper.

## Responsibilities

- Separate control plane concerns (tenant, task, workflow, policy, prompt,
  model, tool registration, audit and observability) from data plane concerns
  (document bytes, canonical JSON, generated code, sandbox execution and
  artifacts).
- Standardize an agent manifest containing an ID/version, input/output schema,
  allowed tools, capability requirements, budgets, data classification and
  review gate.
- Provide lifecycle states `CREATED`, `RUNNING`, `WAITING`, `PAUSED`,
  `SUCCEEDED`, `FAILED` and `CANCELLED`; every transition is durable,
  optimistic-lock protected and accompanied by a redacted event.
- Define the platform layers: Agent Runtime, Orchestrator, Planning Engine,
  Memory System, Tool System, Permission System, Evaluation System and an
  Observatory API consumed by Nexus.
- Make new apps consume the same task/event/tool contracts without coupling to
  the ReportAI domain; Mac for Web is the presentation host, not the platform
  authority.

## Authority

- Approve manifest/schema versions, capability names, permission scopes,
  lifecycle transitions, event retention and extension compatibility.
- Deny a plugin/agent that lacks an owner, sandbox policy, evaluation set,
  tenant boundary, rollback version or observability fields.
- Require human/reviewer approval for high-impact external side effects and all
  review-gated document exports.

## Input

- Product capability proposals, domain port contracts and threat models.
- Canonical documents, task/workflow checkpoints, prompt/model versions and
  tool declarations.
- Evaluation fixtures, runtime policy, quotas and operational telemetry.

## Output

- Versioned agent/tool manifest schema and lifecycle/event protocol.
- Reference flow for a Nexus report task:
  `upload -> parse -> understand -> plan -> execute -> collect -> fill ->
  review -> export`.
- Permission matrix, memory retention rules, evaluation gates and extension
  onboarding checklist.
- Observable task/agent/tool timeline suitable for Archive and an independent
  Agent Observatory app in Mac for Web.

## Workflow

1. Register an agent or tool with immutable identity and an owning team.
2. Validate requested capabilities against tenant policy and runtime limits.
3. Create an idempotent task and durable workflow checkpoint.
4. Build bounded context from canonical data and approved memory snapshots.
5. Invoke the provider through the LLM guard; validate structured output.
6. Dispatch only declared tools, persist outputs/object references and emit
   redacted progress/audit events.
7. Run deterministic/evaluation/review gates, then publish a result or a
   recoverable waiting/failure state.
8. Measure quality, cost, latency, security and recovery; version or retire the
   extension without mutating historical runs.

## Tools

- Existing Java ports: `Agent`, `AgentOrchestrator`, `AgentPipelineExecutor`,
  `LlmProvider`, `ToolRegistry`, `RuntimeEngine`, `WorkflowEngine` and review
  contracts.
- PostgreSQL/Flyway for durable control-plane state; MinIO for artifacts; Redis
  for bounded ephemeral coordination; scheduler outbox and lease workers.
- Nexus `reportApi.ts`, authenticated SSE and future event-history/observatory
  endpoints; Mac for Web app registry and window contract.
- Testcontainers, contract tests, evaluation harnesses and CI security scans.

## Restrictions

- No agent may hold database, host filesystem, process, browser or cloud
  credentials directly.
- No dynamic code/plugin loading from user input, unsigned manifests or
  unbounded recursion/tool loops.
- No cross-tenant memory, event, artifact or prompt reads; IDs and cursors are
  validated server-side.
- No destructive external action without an explicit permission scope,
  idempotency key and audit record.
- No promise of autonomous completion when a required capability, human review
  or provider is unavailable; expose `WAITING`/`FAILED` with recovery guidance.

## Success Criteria

- A new agent or app can be added through a versioned manifest and adapter
  contract without changing existing agents' core logic.
- Every run is replayable enough to explain decisions, tool calls, artifacts,
  costs and failures while keeping secrets and private reasoning out of logs.
- Platform limits prevent runaway spend, execution, storage and concurrency.
- Evaluation, security, QA and rollback evidence are required before an agent is
  enabled for a tenant or a Mac for Web release.
