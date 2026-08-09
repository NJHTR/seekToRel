package com.reportai.agent;

import com.reportai.common.contract.*;
import com.reportai.common.tenant.TenantId;
import com.reportai.review.precheck.*;
import com.reportai.llm.LlmProvider;
import com.reportai.tool.ToolRegistry;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;

final class AgentPrecheckFixtures {
    static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    private AgentPrecheckFixtures() { }

    static AgentContext context(String taskId, TenantId tenant, String agentId) {
        return context(taskId, tenant, agentId, Map.of(), null, null);
    }

    static AgentContext context(String taskId, TenantId tenant, String agentId, Map<String, Object> state,
                                LlmProvider llmProvider, ToolRegistry toolRegistry) {
        String input = "agent-input";
        InputSnapshot snapshot = new InputSnapshot(1, new SnapshotId("snapshot-1"), tenant, List.of(), Sha256Digest.ofText(input), List.of(), NOW, Map.of());
        RulePack rules = RulePack.of("default", "1", "policy", List.of(new RuleDescriptor("input.policy", PrecheckStage.FORMAT_SECURITY, PrecheckSeverity.INFO, "policy", 1)));
        List<String> types = "pipeline".equals(agentId) ? List.of("first", "second", "planner", "coder", "runtime", "preflight", "installer", "execution", "artifact-persistence", "cleanup", "document", "reviewer") : List.of(agentId);
        Map<String, PrecheckResult> receipts = types.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(type -> type, type -> receipt(taskId, tenant, type, snapshot, rules)));
        return new AgentContext(taskId, tenant, state, llmProvider, toolRegistry, snapshot, receipts.get(types.get(0)), receipts);
    }

    private static PrecheckResult receipt(String taskId, TenantId tenant, String agentId, InputSnapshot snapshot, RulePack rules) {
        return new PrecheckResult(1, "precheck-" + agentId, tenant, new TaskId(taskId), "agent-pipeline", new AgentId(agentId), snapshot, snapshot.contentDigest(), snapshot.contentDigest(), rules, List.of(), new EvidenceResolution(0, 0, 0, 0, 0, 0), RiskAssessment.from(0, false), new ContextProjection(List.of(), 0, 0, Sha256Digest.ofText("[]")), PrecheckRoute.AGENT, PrecheckDecision.ACCEPT, NOW);
    }

    static AgentPrecheckGuard guard() { return new AgentPrecheckGuard(() -> NOW, Duration.ofHours(1), null); }
}
