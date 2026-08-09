package com.reportai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic, bounded orchestration. Agents can only communicate through state and declared ports. */
public final class DefaultAgentOrchestrator implements AgentOrchestrator {
    private final AgentExecutionPolicy policy;
    private final AgentPrecheckGuard precheckGuard;

    public DefaultAgentOrchestrator() {
        this(AgentExecutionPolicy.safeDefaults(), new AgentPrecheckGuard());
    }

    public DefaultAgentOrchestrator(AgentExecutionPolicy policy) {
        this(policy, new AgentPrecheckGuard());
    }

    public DefaultAgentOrchestrator(AgentExecutionPolicy policy, AgentPrecheckGuard precheckGuard) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.precheckGuard = Objects.requireNonNull(precheckGuard, "precheckGuard");
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
            // Keep the task-scoped attachment resolver on the step context.  It
            // holds only an ephemeral reader for validated document assets and
            // must never be replaced by the legacy constructor (which defaults
            // to an empty resolver).  Dropping it here silently turned every
            // multimodal request into a text-only request.
            AgentContext stepContext = new AgentContext(context.taskId(), context.tenantId(), state,
                    context.llmProvider(), context.toolRegistry(), context.llmOptions(),
                    context.attachmentResolver(), context.authoritativeStateKeys(), context.inputSnapshot(), context.precheckResult(), context.precheckReceipts());
            AgentPrecheckGuard.Validation validation = precheckGuard.validate(stepContext, agent.type());
            if (!validation.allowed()) {
                Map<String, Object> denied = new LinkedHashMap<>(state);
                denied.put("precheckErrorCode", validation.code());
                denied.put("precheckRoute", validation.route().name());
                return new AgentResult(AgentResult.Status.FAILED, Map.copyOf(denied), validation.code());
            }
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
