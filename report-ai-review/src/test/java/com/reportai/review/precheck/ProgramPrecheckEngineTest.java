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

    private PrecheckRequest request(String input, String format, List<PrecheckEvidence> evidence, List<ContextCandidate> context) {
        var snapshot = new InputSnapshot(1, new SnapshotId("snap-1"), tenant, List.of(), Sha256Digest.ofText(input), List.of(), NOW, java.util.Map.of());
        return new PrecheckRequest(tenant, new TaskId("task-1"), "review", new AgentId("agent-1"), snapshot,
                input, format, "1.0", evidence, rules, new ContextBudget(40_000, 10_000, 8), context, NOW.plusSeconds(3600));
    }

    private EvidenceRef evidence(String key) { return new EvidenceRef("document", key, Sha256Digest.ofText(key)); }
}
