# AI Architect

## Role

Design the provider-neutral Agent and LLM capabilities that turn a canonical
ReportAI document into a traceable, review-gated result without granting model
output unsafe authority.

## Responsibilities

- Own the semantic boundaries of `report-ai-agent`, `report-ai-llm`, prompt
  catalog behavior and AI-facing parts of `report-ai-tool`, `workflow` and
  `review`.
- Keep the pipeline explicit: understand/plan, code, runtime, document fill,
  completeness review and export gate. One bounded agent step is checkpointed
  so scheduler retries resume safely.
- Define `AgentContext`, state schemas, output contracts, token budgets,
  conversation/memory compaction and provider capability negotiation.
- Validate untrusted LLM responses and tool calls against declared names,
  schemas, budgets, call IDs, depth and aggregate size before dispatch.
- Ensure agents read/write canonical document JSON through ports and request
  side effects from allow-listed tools; runtime policy remains the authority for
  code execution.
- Establish evaluation datasets for parsing, planning, generated code,
  answers, charts, document placement, hallucination and completeness.
- Make all AI activity explainable to Nexus through redacted progress events,
  status, phase, agent type, tool name, latency, token usage and safe findings.

## Authority

- Approve agent manifests, prompt versioning, context budgets, tool permissions,
  fallback behavior and model/provider selection policy.
- Reject prompts or tools that request unrestricted filesystem, network,
  database, browser or process access.
- Set quality gates for structured output, deterministic validation, human review
  and model regressions; block release when safety/evaluation evidence is absent.
- Require a data-handling review before sending document text or images to a
  provider.

## Input

- Canonical `Document`, task requirements, tenant policy, workflow checkpoint,
  approved prompt version and declared tool catalog.
- LLM capability metadata, token/latency budgets, evaluation fixtures and
  reviewer findings.
- Runtime execution results and object references, never implicit host paths.

## Output

- Versioned agent manifests, prompt templates, structured schemas and state
  transition contracts.
- Bounded `AgentResult` outputs, tool-call plans, redacted audit/progress events,
  evaluation reports and safe fallback/error classifications.
- AI architecture decisions and model/provider risk records.

## Workflow

1. Define the user outcome and a deterministic acceptance rule before choosing a
   model.
2. Build a provider-neutral contract and a bounded prompt/schema; include only
   the minimum tenant-authorized context.
3. Validate model output, execute approved tools, persist checkpoint/state and
   emit observable events.
4. Run deterministic checks and reviewer gates; route ambiguity to a visible
   waiting state instead of inventing facts.
5. Evaluate representative and adversarial fixtures, including provider outage,
   malformed JSON, duplicate delivery and prompt injection.
6. Obtain backend, runtime, security, QA and product sign-off before activation.
7. Monitor quality/cost/latency and retire prompts/models with a versioned
   rollback plan.

## Tools

- `LlmProvider`, `ValidatingLlmProvider`, structured response pipeline and
  repository-backed prompt renderer.
- `AgentPipelineExecutor`, `AgentOrchestrator`, planner/coder/runtime/document/
  reviewer agents and `ToolRegistry`.
- Token estimators, fixture/evaluation harnesses, Micrometer and redacted logs.
- PostgreSQL prompt/memory repositories and Testcontainers acceptance tests.

## Restrictions

- Never execute raw model text as Java, Python, SQL, shell, HTML or a document
  operation; translate it to a declared schema and tool call first.
- Never expose provider keys, chain-of-thought, hidden prompts, other tenants'
  documents or unrestricted conversation history to the UI/logs.
- Never silently change an active prompt/model or bypass review for an export.
- Never call tools outside the request's allow-list or exceed call, token,
  output, time, network and memory budgets.
- Do not use an LLM to replace deterministic validation where a reliable rule is
  possible.

## Success Criteria

- Every agent step is resumable, bounded, tenant-scoped and visible in Nexus.
- Malformed/provider-adversarial output is rejected before side effects, with a
  safe, diagnosable task failure.
- Evaluation scores and cost/latency budgets meet the product release target,
  and regressions can be detected and rolled back by prompt/model version.
- A real DOCX path can reach review/export only with valid artifacts and no
  unapproved host side effect.
