package com.reportai.agent;

import com.reportai.common.contract.*;
import com.reportai.common.tenant.TenantId;
import com.reportai.review.precheck.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AgentPrecheckGuardTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void acceptsMatchingReceipt() {
        var fixture = fixture(NOW, PrecheckDecision.ACCEPT, PrecheckRoute.AGENT);
        var result = guard().validate(fixture.context(), "agent-1");
        assertTrue(result.allowed());
        var mapOnly = new AgentContext("task-1", fixture.tenant(), Map.of(), null, null,
                fixture.snapshot(), null, Map.of("agent-1", fixture.result()));
        assertTrue(guard().validate(mapOnly, "agent-1").allowed());
    }

    @Test
    void rejectsMissingTenantTaskAgentDigestDecisionRouteAndStale() {
        var fixture = fixture(NOW, PrecheckDecision.ACCEPT, PrecheckRoute.AGENT);
        assertEquals("PRECHECK_MISSING", guard().validate(new AgentContext("task-1", fixture.tenant(), Map.of(), null, null), "agent-1").code());
        assertEquals("PRECHECK_TENANT_MISMATCH", guard().validate(new AgentContext("task-1", new TenantId("other"), Map.of(), null, null, fixture.snapshot(), fixture.result()), "agent-1").code());
        assertEquals("PRECHECK_TASK_MISMATCH", guard().validate(new AgentContext("task-2", fixture.tenant(), Map.of(), null, null, fixture.snapshot(), fixture.result()), "agent-1").code());
        assertEquals("PRECHECK_AGENT_MISMATCH", guard().validate(fixture.context(), "other-agent").code());
        var wrongSnapshot = new InputSnapshot(1, new SnapshotId("snap-2"), fixture.tenant(), List.of(), fixture.snapshot().contentDigest(), List.of(), NOW, Map.of());
        assertEquals("PRECHECK_INPUT_MISMATCH", guard().validate(new AgentContext("task-1", fixture.tenant(), Map.of(), null, null, wrongSnapshot, fixture.result()), "agent-1").code());
        var rejected = fixture(NOW, PrecheckDecision.REJECT, PrecheckRoute.REJECT);
        assertEquals("PRECHECK_ROUTE_NOT_ALLOWED", guard().validate(rejected.context(), "agent-1").code());
        var stale = fixture(NOW.minusSeconds(3600), PrecheckDecision.ACCEPT, PrecheckRoute.AGENT);
        assertEquals("PRECHECK_STALE", guard().validate(stale.context(), "agent-1").code());
    }

    @Test
    void rejectsExpectedRulePackMismatch() {
        var fixture = fixture(NOW, PrecheckDecision.ACCEPT, PrecheckRoute.AGENT);
        var other = RulePack.of("other", "1", "policy", List.of(new RuleDescriptor("input.policy", PrecheckStage.FORMAT_SECURITY, PrecheckSeverity.INFO, "policy", 1)));
        assertEquals("PRECHECK_RULE_PACK_MISMATCH", new AgentPrecheckGuard(() -> NOW, Duration.ofHours(1), other).validate(fixture.context(), "agent-1").code());
    }

    @Test
    void orchestratorDoesNotInvokeAgentWithoutReceipt() {
        AtomicBoolean invoked = new AtomicBoolean();
        Agent agent = new Agent() { public String type() { return "agent-1"; } public AgentResult execute(AgentContext ignored) { invoked.set(true); return new AgentResult(AgentResult.Status.COMPLETED, Map.of(), "unexpected"); } };
        AgentResult result = new DefaultAgentOrchestrator().execute(new AgentContext("task-1", new TenantId("tenant-1"), Map.of(), null, null), List.of(agent));
        assertEquals(AgentResult.Status.FAILED, result.status());
        assertEquals("PRECHECK_MISSING", result.outputs().get("precheckErrorCode"));
        assertFalse(invoked.get());
        AgentContext nullIdentity = new AgentContext(null, null, Map.of(), null, null);
        assertEquals("PRECHECK_MISSING", guard().validate(nullIdentity, "agent-1").code());
    }

    private AgentPrecheckGuard guard() { return new AgentPrecheckGuard(() -> NOW, Duration.ofHours(1), null); }

    private Fixture fixture(Instant createdAt, PrecheckDecision decision, PrecheckRoute route) {
        TenantId tenant = new TenantId("tenant-1"); String input = "hello";
        InputSnapshot snapshot = new InputSnapshot(1, new SnapshotId("snap-1"), tenant, List.of(), Sha256Digest.ofText(input), List.of(), createdAt, Map.of());
        RulePack rules = RulePack.of("default", "1", "policy", List.of(new RuleDescriptor("input.policy", PrecheckStage.FORMAT_SECURITY, PrecheckSeverity.INFO, "policy", 1)));
        PrecheckResult result = new PrecheckResult(1, "precheck-1", tenant, new TaskId("task-1"), "review", new AgentId("agent-1"), snapshot, snapshot.contentDigest(), snapshot.contentDigest(), rules, List.of(), new EvidenceResolution(0, 0, 0, 0, 0, 0), RiskAssessment.from(0, false), new ContextProjection(List.of(), 0, 0, Sha256Digest.ofText("[]")), route, decision, createdAt);
        return new Fixture(tenant, snapshot, result, new AgentContext("task-1", tenant, Map.of(), null, null, snapshot, result));
    }

    private record Fixture(TenantId tenant, InputSnapshot snapshot, PrecheckResult result, AgentContext context) { }
}
