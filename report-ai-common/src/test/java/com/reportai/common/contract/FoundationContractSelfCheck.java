package com.reportai.common.contract;

import com.reportai.common.error.ErrorDetail;
import com.reportai.common.error.FailureClass;
import com.reportai.common.idempotency.IdempotencyKey;
import com.reportai.common.tenant.TenantId;
import com.reportai.common.time.ClockTimeSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Dependency-free executable contract checks; run with the compiled test class. */
public final class FoundationContractSelfCheck {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    private FoundationContractSelfCheck() {
    }

    public static void main(String[] args) {
        idsAndDigest();
        snapshotAndEvaluation();
        envelopesAndErrors();
        paginationAndCanonicalJson();
        timeIsInjectable();
    }

    private static void idsAndDigest() {
        expectFailure(() -> new TaskId(" task-1 "));
        check(new TaskId("task-1").equals(new TaskId("task-1")), "typed id equality");
        check(Sha256Digest.ofText("abc").value().length() == 64, "sha256 length");
        expectFailure(() -> new Sha256Digest("not-a-digest"));
    }

    private static void snapshotAndEvaluation() {
        TenantId tenant = TenantId.of("tenant-a");
        SnapshotRef ref = new SnapshotRef("document", "obj://doc", "application/json", 3,
                Sha256Digest.ofText("doc"));
        InputSnapshot snapshot = new InputSnapshot(1, new SnapshotId("snap-1"), tenant,
                List.of(ref), Sha256Digest.ofText("snapshot"), List.of(), NOW, Map.of("kind", "document"));
        EvaluationPacket packet = new EvaluationPacket(1, new EvaluationId("eval-1"),
                EvaluationDecision.ACCEPT, .9, .8, "accepted", List.of("rule passed"),
                List.of(new EvidenceRef("document", "obj://doc", ref.digest())), List.of(),
                "continue", null, "agent-v1", snapshot, NOW);
        check(packet.inputSnapshot().equals(snapshot), "snapshot must be retained");
        expectFailure(() -> new EvaluationPacket(1, new EvaluationId("eval-2"),
                EvaluationDecision.ACCEPT, 2, .8, "accepted", List.of(), List.of(), List.of(),
                "continue", null, "agent-v1", snapshot, NOW));
    }

    private static void envelopesAndErrors() {
        TraceMetadata trace = TraceMetadata.root(new TraceId("trace-1"), new CorrelationId("corr-1"));
        CommandEnvelope<String> command = new CommandEnvelope<>(1, new CommandId("cmd-1"),
                "task.create", TenantId.of("tenant-a"), new TaskId("task-1"),
                new IdempotencyKey("idem-key-1"), new CorrelationId("corr-1"), trace, NOW,
                "payload", Sha256Digest.ofText("payload"), Map.of("z", "last", "a", "first"));
        EventEnvelope<String> event = new EventEnvelope<>(1, new EventId("event-1"), "TASK_CREATED",
                command.tenantId(), command.aggregateId(), 1, 0, command.commandId(), command.correlationId(),
                trace, NOW, command.payload(), command.payloadDigest(), Map.of());
        check(event.eventSequence() == 0, "unassigned event sequence");
        ErrorDetail error = new ErrorDetail("PYTHON_INPUT_MISSING", FailureClass.INPUT_MISSING,
                false, "required input is missing", "provide the declared dataset", Map.of());
        check(!error.retryable(), "input errors are not retryable by default");
    }

    private static void paginationAndCanonicalJson() {
        PageCursor cursor = new PageCursor(1, "opaque-token", "tenant-a/task-1");
        Page<String> page = new Page<>(List.of("one"), cursor, true, false);
        check(page.hasMore() && page.nextCursor().equals(cursor), "page cursor contract");
        expectFailure(() -> new Page<>(List.of(), null, true, false));
        String left = CanonicalJson.encode(Map.of("b", 2, "a", 1));
        String right = CanonicalJson.encode(Map.of("a", 1, "b", 2));
        check(left.equals(right) && "{\"a\":1,\"b\":2}".equals(left), "canonical JSON ordering");
        expectFailure(() -> CanonicalJson.encode(Double.NaN));
    }

    private static void timeIsInjectable() {
        check(new ClockTimeSource(Clock.fixed(NOW, ZoneOffset.UTC)).now().equals(NOW),
                "clock adapter must be deterministic");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected validation failure");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
