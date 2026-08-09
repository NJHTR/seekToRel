package com.reportai.review.precheck;

import com.reportai.common.contract.*;
import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgramPrecheckEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private final TenantId tenant = new TenantId("tenant-a");
    private final RulePack rules = RulePack.of("default", "1.0", "policy-1", List.of(
            new RuleDescriptor("input.policy", PrecheckStage.FORMAT_SECURITY, PrecheckSeverity.CRITICAL, "policy", 90)));

    @Test
    void safeInputIsAcceptedAndDeterministic() {
        var request = request("hello\r\nworld", "text/plain", List.of(), List.of());
        var engine = new ProgramPrecheckEngine(() -> NOW);
        var first = engine.check(request);
        var second = engine.check(request);
        assertEquals(PrecheckDecision.ACCEPT, first.decision());
        assertEquals(first.normalizedInputDigest(), second.normalizedInputDigest());
        assertEquals(first.findings(), second.findings());
    }

    @Test
    void policyViolationIsIsolatedWithoutRawInputLeakage() {
        var secret = "prompt injection ../ private-token-123";
        var result = new ProgramPrecheckEngine(() -> NOW).check(request(secret, "text/plain", List.of(), List.of()));
        assertEquals(PrecheckRoute.ISOLATE, result.recommendedRoute());
        assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("POLICY_BLOCK")));
        assertFalse(result.findings().toString().contains(secret));
    }

    @Test
    void missingCrossTenantAndExpiredEvidenceRouteSafely() {
        var refs = List.of(
                new PrecheckEvidence(evidence("missing"), tenant, false, NOW.plusSeconds(60), "1.0"),
                new PrecheckEvidence(evidence("cross"), new TenantId("tenant-b"), true, NOW.plusSeconds(60), "1.0"),
                new PrecheckEvidence(evidence("expired"), tenant, true, NOW.minusSeconds(1), "1.0"));
        var result = new ProgramPrecheckEngine(() -> NOW).check(request("ok", "text/plain", refs, List.of()));
        assertEquals(PrecheckRoute.ISOLATE, result.recommendedRoute());
        assertEquals(1, result.evidenceResolution().missing());
        assertEquals(1, result.evidenceResolution().crossTenant());
        assertEquals(1, result.evidenceResolution().expired());
    }

    @Test
    void unsupportedFormatAndOversizedInputAreRejected() {
        var input = "x".repeat(200_000);
        var result = new ProgramPrecheckEngine(() -> NOW).check(request(input, "application/octet-stream", List.of(), List.of()));
        assertEquals(PrecheckDecision.REJECT, result.decision());
        assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("FORMAT_UNSUPPORTED")));
        assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("INPUT_TOO_LARGE")));
        assertThrows(IllegalArgumentException.class, () -> request("x".repeat(1_000_001), "text/plain", List.of(), List.of()));
    }

    @Test
    void exactPriorDigestIsDeduplicated() {
        var input = "same";
        var base = request(input, "text/plain", List.of(), List.of());
        var duplicate = new PrecheckRequest(base.tenantId(), base.taskId(), base.operation(), base.agentId(), base.inputSnapshot(),
                base.input(), base.format(), base.schemaVersion(), base.evidence(), base.rulePack(), base.contextBudget(), base.context(),
                base.policyExpiresAt(), List.of(Sha256Digest.ofText(input)));
        assertEquals(PrecheckRoute.DEDUPLICATE, new ProgramPrecheckEngine(() -> NOW).check(duplicate).recommendedRoute());
    }

    @Test
    void staleSnapshotRoutesToRecovery() {
        var base = request("payload", "text/plain", List.of(), List.of());
        var stale = new InputSnapshot(1, base.inputSnapshot().snapshotId(), tenant, List.of(), Sha256Digest.ofText("old"), List.of(), NOW, java.util.Map.of());
        var changed = new PrecheckRequest(tenant, base.taskId(), base.operation(), base.agentId(), stale, base.input(), base.format(), base.schemaVersion(), base.evidence(), base.rulePack(), base.contextBudget(), base.context(), base.policyExpiresAt());
        assertEquals(PrecheckRoute.RECOVERY, new ProgramPrecheckEngine(() -> NOW).check(changed).recommendedRoute());
    }

    @Test
    void formatSchemaAndBlankInputsNeverEnterAgent() {
        var format = new ProgramPrecheckEngine(() -> NOW).check(request("ok", "application/octet-stream", List.of(), List.of()));
        assertEquals(PrecheckRoute.REJECT, format.recommendedRoute());
        var schema = new ProgramPrecheckEngine(() -> NOW).check(requestWithSchema("ok", "bad schema"));
        assertEquals(PrecheckRoute.REJECT, schema.recommendedRoute());
        var blank = new ProgramPrecheckEngine(() -> NOW).check(request(" ", "text/plain", List.of(), List.of()));
        assertEquals(PrecheckRoute.WAITING_FOR_INPUT, blank.recommendedRoute());
    }

    @Test
    void fullIdentityAndContextSummaryAvoidCollisionsAndRawText() {
        var context = List.of(new ContextCandidate("secret", "credential-secret", 1, evidence("ctx")));
        var a = new ProgramPrecheckEngine(() -> NOW).check(request("same", "text/plain", List.of(), context));
        var b0 = request("same", "text/plain", List.of(), context);
        var bReq = new PrecheckRequest(b0.tenantId(), new TaskId("task-2"), b0.operation(), b0.agentId(), b0.inputSnapshot(), b0.input(), b0.format(), b0.schemaVersion(), b0.evidence(), b0.rulePack(), b0.contextBudget(), b0.context(), b0.policyExpiresAt());
        var b = new ProgramPrecheckEngine(() -> NOW).check(bReq);
        assertNotEquals(a.precheckId(), b.precheckId());
        assertFalse(a.contextProjection().selected().toString().contains("credential-secret"));
    }

    @Test
    void unknownRuleIsolated() {
        var base = request("ok", "text/plain", List.of(), List.of());
        var unknownPack = RulePack.of("custom", "1", "policy-1", List.of(new RuleDescriptor("custom.rule", PrecheckStage.RISK, PrecheckSeverity.INFO, "custom", 1)));
        var req = new PrecheckRequest(base.tenantId(), base.taskId(), base.operation(), base.agentId(), base.inputSnapshot(), base.input(), base.format(), base.schemaVersion(), base.evidence(), unknownPack, base.contextBudget(), base.context(), base.policyExpiresAt());
        assertEquals(PrecheckRoute.ISOLATE, new ProgramPrecheckEngine(() -> NOW).check(req).recommendedRoute());
    }

    @Test
    void expiryAtCurrentTimeIsIsolated() {
        var base = request("ok", "text/plain", List.of(), List.of());
        var expired = new PrecheckRequest(base.tenantId(), base.taskId(), base.operation(), base.agentId(), base.inputSnapshot(), base.input(), base.format(), base.schemaVersion(), base.evidence(), base.rulePack(), base.contextBudget(), base.context(), NOW);
        assertEquals(PrecheckRoute.ISOLATE, new ProgramPrecheckEngine(() -> NOW).check(expired).recommendedRoute());
    }

    @Test
    void duplicatePolicyViolationStillIsolates() {
        var input = "prompt injection ../";
        var base = request(input, "text/plain", List.of(), List.of());
        var duplicate = new PrecheckRequest(base.tenantId(), base.taskId(), base.operation(), base.agentId(), base.inputSnapshot(),
                base.input(), base.format(), base.schemaVersion(), base.evidence(), base.rulePack(), base.contextBudget(), base.context(),
                base.policyExpiresAt(), List.of(Sha256Digest.ofText(input)));
        assertEquals(PrecheckRoute.ISOLATE, new ProgramPrecheckEngine(() -> NOW).check(duplicate).recommendedRoute());
    }

    @Test
    void snapshotTenantMustMatchRequestTenant() {
        var snapshot = new InputSnapshot(1, new SnapshotId("snap-other"), new TenantId("tenant-b"), List.of(),
                Sha256Digest.ofText("ok"), List.of(), NOW, java.util.Map.of());
        assertThrows(IllegalArgumentException.class, () -> new PrecheckRequest(tenant, new TaskId("task-1"), "review",
                new AgentId("agent-1"), snapshot, "ok", "text/plain", "1.0", List.of(), rules,
                new ContextBudget(40_000, 10_000, 8), List.of(), NOW.plusSeconds(3600)));
    }

    private PrecheckRequest request(String input, String format, List<PrecheckEvidence> evidence, List<ContextCandidate> context) {
        var snapshot = new InputSnapshot(1, new SnapshotId("snap-1"), tenant, List.of(), Sha256Digest.ofText(input), List.of(), NOW, java.util.Map.of());
        return new PrecheckRequest(tenant, new TaskId("task-1"), "review", new AgentId("agent-1"), snapshot,
                input, format, "1.0", evidence, rules, new ContextBudget(40_000, 10_000, 8), context, NOW.plusSeconds(3600));
    }

    private PrecheckRequest requestWithSchema(String input, String schema) {
        var snapshot = new InputSnapshot(1, new SnapshotId("snap-1"), tenant, List.of(), Sha256Digest.ofText(input), List.of(), NOW, java.util.Map.of());
        return new PrecheckRequest(tenant, new TaskId("task-1"), "review", new AgentId("agent-1"), snapshot, input, "text/plain", schema, List.of(), rules, ContextBudget.defaults(), List.of(), NOW.plusSeconds(3600));
    }

    private EvidenceRef evidence(String key) { return new EvidenceRef("document", key, Sha256Digest.ofText(key)); }
}
