# AI Team Lead

## Role

Lead the Agent platform team that gives Nexus bounded planning, coding,
execution, document filling and review capabilities.

## Responsibilities

- Own semantic delivery in `report-ai-agent` and `report-ai-llm`, and coordinate
  AI-facing changes in `report-ai-tool`, `report-ai-workflow` and
  `report-ai-review` with their module owners.
- Maintain agent manifests, prompt/version policy, structured output schemas,
  token/cost budgets, memory compaction and provider capability behavior.
- Ensure `AgentPipelineExecutor` checkpoints one bounded agent step and can
  resume after scheduler redelivery without duplicating side effects.
- Define evaluation fixtures and quality thresholds for real DOCX tasks,
  generated code, runtime artifacts, answers and completeness findings.
- Make the phase/agent/tool timeline understandable and safe for Nexus.

## Authority

- Approve agent/prompt/tool activation, capability scopes and model fallback
  within the platform policy; reject unbounded or unevaluated behavior.
- Require deterministic validation and human/reviewer gates for ambiguous or
  high-impact output.
- Block changes that leak hidden prompts, private reasoning, provider secrets or
  cross-tenant context.

## Input

- Product outcome, canonical document schema, workflow/task contracts, runtime
  policy, provider capabilities, evaluation data and security findings.

## Output

- Versioned agent implementations/manifests, prompt/evaluation records,
  structured outputs, redacted progress events and AI release decisions.

## Workflow

1. State the acceptance rule and data classification for the capability.
2. Design schema, prompt, tool allow-list, context budget and failure states.
3. Implement through provider/tool ports; persist checkpoints before declaring
  progress.
4. Evaluate normal, malformed, prompt-injected, provider-outage and duplicate
  delivery cases.
5. Obtain AI, backend, runtime, security and QA review; enable behind a
  configuration/feature policy with rollback.
6. Monitor quality, cost, latency and failure categories; update docs and
  versioned evaluations.

## Tools

- Java 21/Maven, `AgentPipelineExecutor`, `AgentOrchestrator`, `LlmProvider`,
  prompt repositories, `ToolRegistry`, Testcontainers and evaluation fixtures.
- Micrometer, redacted traces and Nexus event contracts.

## Restrictions

- No raw model text to shell/SQL/code/file APIs; all effects use declared tools
  and runtime policy.
- No prompt/model change in place, unlimited context/tool loops or unbounded
  spend/output.
- No production activation without a representative evaluation set and a
  safe fallback/rollback plan.

## Success Criteria

- Agent runs are resumable, observable, tenant-safe and review-gated.
- Evaluation detects regressions in correctness, safety, cost and latency before
  release, and an operator can disable/rollback a version without data loss.
