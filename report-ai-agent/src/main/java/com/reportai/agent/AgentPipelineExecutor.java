package com.reportai.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Executes exactly one agent per invocation and commits a cursor checkpoint with
 * optimistic locking. A scheduler retry can therefore safely resume from the
 * last committed agent after a process restart.
 */
public final class AgentPipelineExecutor {
    private final AgentOrchestrator orchestrator;
    private final AgentPipelineRepository repository;
    private final AgentPrecheckGuard precheckGuard;

    public AgentPipelineExecutor(AgentOrchestrator orchestrator, AgentPipelineRepository repository) {
        this(orchestrator, repository, new AgentPrecheckGuard());
    }

    public AgentPipelineExecutor(AgentOrchestrator orchestrator, AgentPipelineRepository repository,
                                 AgentPrecheckGuard precheckGuard) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.precheckGuard = Objects.requireNonNull(precheckGuard, "precheckGuard");
    }

    /** Creates a cursor for a new task without executing user code. */
    public AgentPipelineCheckpoint start(String pipelineId, AgentContext context,
                                         List<? extends Agent> agents) {
        Objects.requireNonNull(context, "context");
        List<String> types = validateAgents(agents);
        return repository.create(new AgentPipelineCheckpoint(pipelineId, context.taskId(), context.tenantId(),
                types, 0, AgentPipelineStatus.RUNNING, context.state(), "pipeline created", 0, Instant.now()));
    }

    /**
     * Creates the checkpoint if this is the first delivery, then runs one step.
     * A concurrent creator is treated as a normal recovery race and the durable
     * checkpoint is loaded instead of creating a duplicate pipeline.
     */
    public AgentPipelineResult run(String pipelineId, AgentContext runtimeContext,
                                   List<? extends Agent> agents) {
        Objects.requireNonNull(runtimeContext, "runtimeContext");
        List<String> types = validateAgents(agents);
        AgentPipelineCheckpoint checkpoint = repository.findById(pipelineId).orElse(null);
        if (checkpoint == null) {
            try {
                checkpoint = start(pipelineId, runtimeContext, agents);
            } catch (RuntimeException race) {
                checkpoint = repository.findById(pipelineId).orElseThrow(() -> race);
            }
        }
        checkpoint = migrateLegacyCheckpoint(checkpoint, types);
        validateIdentity(checkpoint, runtimeContext, types);
        if (checkpoint.status() == AgentPipelineStatus.FAILED) {
            // A scheduler redelivery is a legitimate retry boundary. Keep the last
            // committed state/cursor, but make the failed agent runnable again.
            checkpoint = update(checkpoint, AgentPipelineStatus.RUNNING,
                    checkpoint.nextAgentIndex(), checkpoint.state(), "retrying failed agent");
        }
        if (checkpoint.isTerminal()) {
            return new AgentPipelineResult(checkpoint.status(), checkpoint,
                    new AgentResult(checkpoint.status() == AgentPipelineStatus.COMPLETED
                            ? AgentResult.Status.COMPLETED : AgentResult.Status.FAILED,
                            checkpoint.state(), checkpoint.message()));
        }
        if (checkpoint.nextAgentIndex() >= agents.size()) {
            AgentPipelineCheckpoint completed = update(checkpoint, AgentPipelineStatus.COMPLETED,
                    checkpoint.nextAgentIndex(), checkpoint.state(), "agent pipeline completed");
            return new AgentPipelineResult(completed.status(), completed,
                    new AgentResult(AgentResult.Status.COMPLETED, completed.state(), completed.message()));
        }

        int index = checkpoint.nextAgentIndex();
        // The handler may derive a bounded runtime request between agent steps
        // (for example, after Coder produces source code). Keep durable
        // checkpoint values authoritative, while admitting new values from the
        // current execution context before invoking the next agent.
        Map<String, Object> executionState = new java.util.LinkedHashMap<>(checkpoint.state());
        mergeRuntimeState(executionState, runtimeContext);
        AgentContext stepContext = new AgentContext(checkpoint.taskId(), checkpoint.tenantId(), executionState,
                runtimeContext.llmProvider(), runtimeContext.toolRegistry(), runtimeContext.llmOptions(),
                runtimeContext.attachmentResolver(), runtimeContext.authoritativeStateKeys(), runtimeContext.inputSnapshot(), runtimeContext.precheckResult(), runtimeContext.precheckReceipts());
        AgentPrecheckGuard.Validation validation = precheckGuard.validate(stepContext, agents.get(index).type());
        if (!validation.allowed()) {
            Map<String, Object> state = new java.util.LinkedHashMap<>(checkpoint.state());
            state.put("precheckErrorCode", validation.code());
            state.put("precheckRoute", validation.route().name());
            AgentPipelineCheckpoint denied = update(checkpoint, AgentPipelineStatus.FAILED, index, state, validation.code());
            return new AgentPipelineResult(AgentPipelineStatus.FAILED, denied,
                    new AgentResult(AgentResult.Status.FAILED, state, validation.code()));
        }
        Object persistedPrecheckId = checkpoint.state().get("precheckId");
        if (persistedPrecheckId != null && !persistedPrecheckId.equals(stepContext.precheckResult().precheckId())) {
            Map<String, Object> state = new java.util.LinkedHashMap<>(checkpoint.state());
            state.put("precheckErrorCode", "PRECHECK_RECEIPT_MISMATCH");
            state.put("precheckRoute", stepContext.precheckResult().recommendedRoute().name());
            AgentPipelineCheckpoint denied = update(checkpoint, AgentPipelineStatus.FAILED, index, state, "PRECHECK_RECEIPT_MISMATCH");
            return new AgentPipelineResult(AgentPipelineStatus.FAILED, denied,
                    new AgentResult(AgentResult.Status.FAILED, state, "PRECHECK_RECEIPT_MISMATCH"));
        }
        AgentResult result = orchestrator.execute(stepContext, List.of(agents.get(index)));
        if (result == null) {
            result = new AgentResult(AgentResult.Status.FAILED, Map.of(), "agent returned no result");
        }

        // Merge the durable checkpoint first so source document, runtime request,
        // and every previous agent output survive a restart/resume boundary.
        Map<String, Object> state = new java.util.LinkedHashMap<>(checkpoint.state());
        mergeRuntimeState(state, runtimeContext);
        state.put("precheckId", stepContext.precheckResult().precheckId());
        state.put("precheckNormalizedInputDigest", stepContext.precheckResult().normalizedInputDigest().value());
        state.put("precheckRulePackDigest", stepContext.precheckResult().rulePack().digest().value());
        state.putAll(result.outputs());
        List<String> completed = new ArrayList<>();
        completed.addAll(types.subList(0, index));
        if (result.status() == AgentResult.Status.COMPLETED) {
            completed.add(types.get(index));
            state.put("completedAgents", List.copyOf(completed));
            int nextIndex = index + 1;
            AgentPipelineStatus status = nextIndex >= agents.size()
                    ? AgentPipelineStatus.COMPLETED : AgentPipelineStatus.RUNNING;
            String message = nextIndex >= agents.size() ? "agent pipeline completed" : result.message();
            AgentPipelineCheckpoint updated = update(checkpoint, status, nextIndex, state, message);
            return new AgentPipelineResult(status, updated, result);
        }
        if (result.status() == AgentResult.Status.WAITING) {
            state.put("completedAgents", List.copyOf(completed));
            state.put("waitingAgent", types.get(index));
            AgentPipelineCheckpoint updated = update(checkpoint, AgentPipelineStatus.WAITING, index, state,
                    result.message());
            return new AgentPipelineResult(AgentPipelineStatus.WAITING, updated, result);
        }
        state.put("completedAgents", List.copyOf(completed));
        state.put("failedAgent", types.get(index));
        int retryIndex = index;
        if ((ExecutionAgent.TYPE.equals(types.get(index))
                || "artifact-persistence".equals(types.get(index)))
                && Boolean.TRUE.equals(result.outputs().get("rewindToCoder"))) {
            // A deterministic runtime/argument failure is feedback about the
            // generated source. Replaying Execution would run the same broken
            // bytes, so restart at Coder and let it repair the complete source.
            // The bounded diagnostic remains in the checkpoint; credentials and
            // full process output are filtered by ToolBackedAgent.
            int coderIndex = types.indexOf(CoderAgent.TYPE);
            retryIndex = coderIndex >= 0 ? coderIndex : index;
            state.put("runtimeFailureFeedback", Map.of(
                    "toolErrorCode", String.valueOf(result.outputs().getOrDefault("toolErrorCode", "PYTHON_RUNTIME_ERROR")),
                    "stderr", String.valueOf(result.outputs().getOrDefault("toolStderrSummary", "")),
                    "exitCode", String.valueOf(result.outputs().getOrDefault("toolExitCode", ""))));
            state.put("retryFrom", types.get(retryIndex));
            state.put("retryReason", "sandbox execution failed; generated source requires repair");
        } else if ((ExecutionAgent.TYPE.equals(types.get(index))
                || "artifact-persistence".equals(types.get(index)))
                && Boolean.TRUE.equals(result.outputs().get("rewindToInstaller"))) {
            // Execution cleaned its task-scoped volume before returning the
            // failure.  Replaying Execution alone would reference a deleted
            // volume, so the next delivery must repeat Installer first.
            int installerIndex = types.indexOf(InstallerAgent.TYPE);
            retryIndex = installerIndex >= 0 ? installerIndex : Math.max(0, index - 1);
            state.put("retryFrom", types.get(retryIndex));
            state.put("retryReason", "execution failed; dependency volume was cleaned");
        }
        AgentPipelineCheckpoint updated = update(checkpoint, AgentPipelineStatus.FAILED, retryIndex, state,
                result.message());
        return new AgentPipelineResult(AgentPipelineStatus.FAILED, updated, result);
    }

    private AgentPipelineCheckpoint update(AgentPipelineCheckpoint current, AgentPipelineStatus status,
                                           int nextIndex, Map<String, Object> state, String message) {
        return repository.update(new AgentPipelineCheckpoint(current.pipelineId(), current.taskId(),
                current.tenantId(), current.agentTypes(), nextIndex, status, state, message,
                current.version() + 1, Instant.now()), current.version());
    }

    /**
     * Inserts durable preflight and sandbox lifecycle boundaries into older
     * cursors. Existing checkpoints were created with [coder,runtime] (or the
     * intermediate [coder,preflight,runtime] shape); migrate before identity
     * validation so a restart neither reruns Coder nor skips safety gates.
     */
    private AgentPipelineCheckpoint migrateLegacyCheckpoint(AgentPipelineCheckpoint checkpoint,
                                                              List<String> requestedTypes) {
        List<String> legacy = List.of("coder", "runtime");
        List<String> current = List.of("coder", "preflight", "runtime");
        List<String> lifecycle = List.of("coder", "preflight", "installer", "execution", "cleanup");
        List<String> lifecycleWithArtifacts = List.of("coder", "preflight", "installer", "execution", "artifact-persistence", "cleanup");
        if ((requestedTypes.equals(lifecycle) || requestedTypes.equals(lifecycleWithArtifacts))
                && (checkpoint.agentTypes().equals(legacy) || checkpoint.agentTypes().equals(current))) {
            List<String> target = requestedTypes;
            int next = checkpoint.status() == AgentPipelineStatus.COMPLETED
                    ? target.size() : Math.min(target.size(), checkpoint.nextAgentIndex());
            // A legacy runtime cursor points at the first new lifecycle boundary.
            if (checkpoint.agentTypes().equals(legacy)) next = Math.min(1, next);
            Map<String, Object> state = new java.util.LinkedHashMap<>(checkpoint.state());
            state.put("pipelineMigration", "legacy-to-installer-execution-cleanup");
            return repository.update(new AgentPipelineCheckpoint(checkpoint.pipelineId(), checkpoint.taskId(),
                    checkpoint.tenantId(), target, next, checkpoint.status(), state,
                    "legacy checkpoint migrated to installer-execution-cleanup lifecycle",
                    checkpoint.version() + 1, Instant.now()), checkpoint.version());
        }
        if (!checkpoint.agentTypes().equals(legacy) || !requestedTypes.equals(current)) {
            return checkpoint;
        }
        int next = checkpoint.status() == AgentPipelineStatus.COMPLETED
                ? current.size() : Math.min(1, checkpoint.nextAgentIndex());
        Map<String, Object> state = new java.util.LinkedHashMap<>(checkpoint.state());
        state.put("pipelineMigration", "coder-runtime-to-coder-preflight-runtime");
        return repository.update(new AgentPipelineCheckpoint(checkpoint.pipelineId(), checkpoint.taskId(),
                checkpoint.tenantId(), current, next, checkpoint.status(), state,
                "legacy coder-runtime checkpoint migrated", checkpoint.version() + 1, Instant.now()),
                checkpoint.version());
    }

    private static List<String> validateAgents(List<? extends Agent> agents) {
        if (agents == null || agents.isEmpty() || agents.size() > 256) {
            throw new IllegalArgumentException("agents are required and bounded");
        }
        List<String> types = new ArrayList<>(agents.size());
        for (Agent agent : agents) {
            if (agent == null || agent.type() == null || agent.type().isBlank()) {
                throw new IllegalArgumentException("agent definition is invalid");
            }
            types.add(agent.type().trim());
        }
        return List.copyOf(types);
    }

    /**
     * Reconciles the durable JSON checkpoint with values supplied by the current
     * worker. Most values are deliberately checkpoint-authoritative: this keeps
     * a retry from accepting a changed client/runtime request. The canonical
     * document is different. It is a domain object loaded and integrity-checked
     * by the application on every delivery, while JSON persistence necessarily
     * restores it as an untyped map. Prefer that current typed value so agents
     * with a domain contract (notably ReviewerAgent) do not receive a raw map on
     * retry.
     */
    private static void mergeRuntimeState(Map<String, Object> target, AgentContext runtimeContext) {
        if (runtimeContext == null || runtimeContext.state().isEmpty()) {
            return;
        }
        runtimeContext.state().forEach((key, value) -> {
            if (runtimeContext.authoritativeStateKeys().contains(key)) {
                target.put(key, value);
            } else {
                target.putIfAbsent(key, value);
            }
        });
    }

    private static void validateIdentity(AgentPipelineCheckpoint checkpoint, AgentContext context,
                                         List<String> types) {
        if (!checkpoint.taskId().equals(context.taskId())
                || !checkpoint.tenantId().equals(context.tenantId())
                || !checkpoint.agentTypes().equals(types)) {
            throw new IllegalArgumentException("agent pipeline runtime identity does not match checkpoint");
        }
    }
}
