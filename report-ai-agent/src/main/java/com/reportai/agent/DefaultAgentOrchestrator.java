package com.reportai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic, bounded orchestration. Agents can only communicate through state and declared ports. */
public final class DefaultAgentOrchestrator implements AgentOrchestrator {
    private final AgentExecutionPolicy policy;

    public DefaultAgentOrchestrator() {
        this(AgentExecutionPolicy.safeDefaults());
    }

    public DefaultAgentOrchestrator(AgentExecutionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public AgentResult execute(AgentContext context, List<? extends Agent> agents) {
        if (context == null) {
            return new AgentResult(AgentResult.Status.FAILED, Map.of(), "agent context is required");
        }
        if (agents == null || agents.isEmpty()) {
            return new AgentResult(AgentResult.Status.FAILED, Map.of(), "at least one agent is required");
        }
        if (agents.size() > policy.maxSteps()) {
            return new AgentResult(AgentResult.Status.FAILED, Map.of(), "agent step limit exceeded");
        }
        Map<String, Object> state = new LinkedHashMap<>(context.state());
        List<String> completed = new ArrayList<>();
        for (Agent agent : agents) {
            if (agent == null || agent.type() == null || agent.type().isBlank()) {
                return failed(state, "agent definition is invalid");
            }
            AgentContext stepContext = new AgentContext(context.taskId(), context.tenantId(), state,
                    context.llmProvider(), context.toolRegistry());
            AgentResult result;
            try {
                result = agent.execute(stepContext);
            } catch (RuntimeException failure) {
                return failed(state, "agent execution failed: " + agent.type());
            }
            if (result == null) {
                return failed(state, "agent returned no result: " + agent.type());
            }
            if (result.outputs().size() + state.size() > policy.maxStateEntries()) {
                return failed(state, "agent state limit exceeded");
            }
            state.putAll(result.outputs());
            completed.add(agent.type());
            if (result.status() == AgentResult.Status.FAILED) {
                return new AgentResult(AgentResult.Status.FAILED, Map.copyOf(state), bounded(result.message()));
            }
            if (result.status() == AgentResult.Status.WAITING) {
                state.put("completedAgents", List.copyOf(completed));
                return new AgentResult(AgentResult.Status.WAITING, Map.copyOf(state), bounded(result.message()));
            }
        }
        state.put("completedAgents", List.copyOf(completed));
        return new AgentResult(AgentResult.Status.COMPLETED, Map.copyOf(state), "agent pipeline completed");
    }

    private AgentResult failed(Map<String, Object> state, String message) {
        return new AgentResult(AgentResult.Status.FAILED, Map.copyOf(state), bounded(message));
    }

    private String bounded(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= policy.maxOutputMessageLength()
                ? message : message.substring(0, policy.maxOutputMessageLength());
    }
}
