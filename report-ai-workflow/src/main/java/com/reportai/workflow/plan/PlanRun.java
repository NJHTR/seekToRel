package com.reportai.workflow.plan;

import com.reportai.common.contract.CanonicalJson;
import com.reportai.common.contract.Sha256Digest;
import com.reportai.common.contract.TaskId;
import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Restart-safe snapshot of one execution of an immutable plan version. */
public record PlanRun(String runId, TenantId tenantId, TaskId taskId, String planId,
                      long planVersion, Sha256Digest planDigest, PlanRunState state,
                      Map<String, PlanNodeState> nodeStates, long version,
                      Instant createdAt, Instant updatedAt) {
    public PlanRun {
        runId = PlanRules.id(runId, "runId");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        taskId = Objects.requireNonNull(taskId, "taskId");
        planId = PlanRules.id(planId, "planId");
        if (planVersion <= 0) throw new IllegalArgumentException("planVersion must be positive");
        planDigest = Objects.requireNonNull(planDigest, "planDigest");
        state = Objects.requireNonNull(state, "state");
        nodeStates = boundedNodeStates(nodeStates);
        if (version < 0) throw new IllegalArgumentException("run version must not be negative");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt precedes createdAt");
    }

    public PlanRun(String runId, TenantId tenantId, String taskId, String planId,
                   long planVersion, String planDigest, PlanRunState state,
                   Map<String, PlanNodeState> nodeStates, long version,
                   Instant createdAt, Instant updatedAt) {
        this(runId, tenantId, new TaskId(taskId), planId, planVersion,
                new Sha256Digest(planDigest), state, nodeStates, version, createdAt, updatedAt);
    }

    public static PlanRun initial(String runId, TenantId tenantId, TaskId taskId,
                                  PlanDefinition definition, Instant now) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(tenantId, "tenantId");
        if (!tenantId.equals(definition.tenantId())) {
            throw new IllegalArgumentException("run tenant does not match plan tenant");
        }
        PlanGraphValidator.assertValid(definition);
        Map<String, PlanNodeState> states = new TreeMap<>();
        definition.nodes().forEach(node -> states.put(node.nodeId(), PlanNodeState.PENDING));
        return new PlanRun(runId, tenantId, taskId, definition.planId(), definition.version(),
                definition.digest(), PlanRunState.PENDING, states, 0, now, now);
    }

    public static PlanRun initial(String runId, TenantId tenantId, String taskId,
                                  PlanDefinition definition, Instant now) {
        return initial(runId, tenantId, new TaskId(taskId), definition, now);
    }

    public PlanRun withState(PlanRunState nextState, Instant at) {
        return new PlanRun(runId, tenantId, taskId, planId, planVersion, planDigest,
                nextState, nodeStates, version + 1, createdAt, Objects.requireNonNull(at, "at"));
    }

    public PlanRun withNodeState(String nodeId, PlanNodeState nextState, Instant at) {
        PlanRules.id(nodeId, "nodeId");
        Objects.requireNonNull(nextState, "nextState");
        if (!nodeStates.containsKey(nodeId)) throw new IllegalArgumentException("unknown node: " + nodeId);
        Map<String, PlanNodeState> copy = new TreeMap<>(nodeStates);
        copy.put(nodeId, nextState);
        return new PlanRun(runId, tenantId, taskId, planId, planVersion, planDigest,
                state, copy, version + 1, createdAt, Objects.requireNonNull(at, "at"));
    }

    public boolean sameIdentity(PlanRun other) {
        return other != null && runId.equals(other.runId) && tenantId.equals(other.tenantId)
                && taskId.equals(other.taskId) && planId.equals(other.planId)
                && planVersion == other.planVersion && planDigest.equals(other.planDigest);
    }

    public String nodeStateDigest() {
        Map<String, String> values = new TreeMap<>();
        nodeStates.forEach((node, nodeState) -> values.put(node, nodeState.name()));
        return Sha256Digest.ofText(CanonicalJson.encode(values)).value();
    }

    private static Map<String, PlanNodeState> boundedNodeStates(Map<String, PlanNodeState> source) {
        Objects.requireNonNull(source, "nodeStates");
        if (source.isEmpty() || source.size() > PlanRules.MAX_NODES) {
            throw new IllegalArgumentException("nodeStates is out of bounds");
        }
        TreeMap<String, PlanNodeState> copy = new TreeMap<>();
        source.forEach((nodeId, nodeState) -> {
            copy.put(PlanRules.id(nodeId, "nodeId"), Objects.requireNonNull(nodeState, "nodeState"));
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(copy));
    }
}
