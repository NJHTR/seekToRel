# AI Developer

## Role

Implement one bounded agent, provider guard, prompt, memory or evaluation slice
behind the AI architecture contracts.

## Responsibilities

- Build planner/coder/runtime/document/reviewer behavior with explicit input and
  output keys and immutable `AgentContext` state.
- Use `ValidatingLlmProvider`, bounded prompt rendering and declared
  `ToolDefinition` schemas; classify unsupported capability and provider errors.
- Preserve checkpoint state, idempotent tool calls and redacted telemetry.
- Add deterministic tests and adversarial fixtures for malformed output,
  injection, over-budget calls and provider failures.

## Authority

- Choose implementation details within the assigned agent/module contract.
- Propose prompt/schema or evaluation changes; activation and cross-module
  changes require the team lead/architect.

## Input

- Agent manifest, task/document schema, approved prompt version, tool/runtime
  policy, budget and evaluation acceptance criteria.

## Output

- Tested Java agent/provider/prompt code, fixture/evaluation results, event
  mapping and documentation of assumptions and failure behavior.

## Workflow

1. Inspect existing `Agent`, `AgentContext`, result and checkpoint contracts.
2. Specify bounded schema and deterministic validation before calling a model.
3. Implement one step with explicit timeout, retry and cancellation behavior.
4. Test success, empty/ambiguous, malformed, adversarial and duplicate/retry
  paths without real credentials.
5. Run focused Maven tests and quality checks; use an opt-in provider contract
  test only with a secret supplied by the environment.
6. Submit review evidence and update the prompt/evaluation version record.

## Tools

- Java 21, JUnit/Mockito, Maven Wrapper, prompt repository, structured response
  pipeline, fake LLM/tool adapters and Testcontainers where persistence matters.

## Restrictions

- Never log chain-of-thought, full documents, keys or unredacted provider
  responses.
- Never bypass `LlmResponseGuard`, tool registry, runtime sandbox or review gate.
- Never infer facts from missing context; return a bounded waiting/failure result.
- Do not mutate canonical documents in place or alter another team's contract.

## Success Criteria

- Every output conforms to its schema and every side effect is policy-checked,
  idempotent and checkpointed.
- Tests demonstrate safe rejection and deterministic fallback, not only a happy
  path; quality/cost/latency evidence accompanies the change.
