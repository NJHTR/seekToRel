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

    public AgentPipelineExecutor(AgentOrchestrator orchestrator, AgentPipelineRepository repository) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.repository = Objects.requireNonNull(repository, "repository");
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
        AgentContext stepContext = new AgentContext(checkpoint.taskId(), checkpoint.tenantId(), checkpoint.state(),
                runtimeContext.llmProvider(), runtimeContext.toolRegistry());
        AgentResult result = orchestrator.execute(stepContext, List.of(agents.get(index)));
        if (result == null) {
            result = new AgentResult(AgentResult.Status.FAILED, Map.of(), "agent returned no result");
        }

        // Merge the durable checkpoint first so source document, runtime request,
        // and every previous agent output survive a restart/resume boundary.
        Map<String, Object> state = new java.util.LinkedHashMap<>(checkpoint.state());
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
        AgentPipelineCheckpoint updated = update(checkpoint, AgentPipelineStatus.FAILED, index, state,
                result.message());
        return new AgentPipelineResult(AgentPipelineStatus.FAILED, updated, result);
    }

    private AgentPipelineCheckpoint update(AgentPipelineCheckpoint current, AgentPipelineStatus status,
                                           int nextIndex, Map<String, Object> state, String message) {
        return repository.update(new AgentPipelineCheckpoint(current.pipelineId(), current.taskId(),
                current.tenantId(), current.agentTypes(), nextIndex, status, state, message,
                current.version() + 1, Instant.now()), current.version());
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

    private static void validateIdentity(AgentPipelineCheckpoint checkpoint, AgentContext context,
                                         List<String> types) {
        if (!checkpoint.taskId().equals(context.taskId())
                || !checkpoint.tenantId().equals(context.tenantId())
                || !checkpoint.agentTypes().equals(types)) {
            throw new IllegalArgumentException("agent pipeline runtime identity does not match checkpoint");
        }
    }
}
