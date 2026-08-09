package com.reportai.review.workflow;

import com.reportai.common.contract.*;
import com.reportai.common.tenant.TenantId;
import com.reportai.review.precheck.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReviewWorkflowGateTest {
    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");
    private final TenantId tenant = new TenantId("tenant-a");
    private final InputSnapshot snapshot = new InputSnapshot(1, new SnapshotId("snap-1"), tenant, List.of(),
            Sha256Digest.ofText("input"), List.of(), NOW, Map.of());
    private final PrecheckResult receipt = new PrecheckResult(1, "pre-1", tenant, new TaskId("task-1"), "review",
            new AgentId("reviewer"), snapshot, snapshot.contentDigest(), snapshot.contentDigest(),
            RulePack.of("rules", "1.0", "policy", List.of(new RuleDescriptor("format", PrecheckStage.FORMAT_SECURITY, PrecheckSeverity.INFO, "format", 0))),
            List.of(), new EvidenceResolution(0, 0, 0, 0, 0, 0), RiskAssessment.from(0, false),
            new ContextProjection(List.of(), 0, 0, Sha256Digest.ofText("[]")), PrecheckRoute.AGENT, PrecheckDecision.ACCEPT, NOW);

    @Test
    void passReplayAndDuplicateAreDeterministic() {
        InMemoryReviewCaseRepository repository = new InMemoryReviewCaseRepository();
        ReviewWorkflowGate gate = new ReviewWorkflowGate(repository, fixedClock());
        repository.create(ReviewCase.create("case-1", receipt, 2, 2));
        ReviewEvaluation first = evaluation("e-1", EvaluationDecision.ACCEPT, null, NOW);
        ReviewOutcome a = gate.handle(command("c-1", "i-1", 0, ReviewAction.SUBMIT_FIRST_REVIEW, first));
        assertEquals(ReviewState.ACCEPTANCE, a.reviewCase().state());
        ReviewOutcome duplicate = gate.handle(command("c-1", "i-1", 99, ReviewAction.SUBMIT_FIRST_REVIEW, first));
        assertTrue(duplicate.duplicate());
        assertEquals(a.event(), duplicate.event());
    }

    @Test
    void recoveryReentryReturnsToFirstPassAndRequiresFreshChain() {
        InMemoryReviewCaseRepository repository = new InMemoryReviewCaseRepository();
        ReviewWorkflowGate gate = new ReviewWorkflowGate(repository, fixedClock());
        repository.create(ReviewCase.create("case-1", receipt, 1, 1));
        ReviewEvaluation first = evaluation("e-1", EvaluationDecision.REVISE, null, NOW);
        ReviewOutcome pending = gate.handle(command("c-1", "i-1", 0, ReviewAction.SUBMIT_FIRST_REVIEW, first));
        assertEquals(ReviewState.RECOVERY_PENDING, pending.reviewCase().state());
        ReviewOutcome reentry = gate.handle(command("c-2", "i-2", 1, ReviewAction.COMPLETE_RECOVERY, null));
        assertEquals(ReviewState.REENTRY_PENDING, reentry.reviewCase().state());
        ReviewOutcome firstPassAgain = gate.handle(command("c-3", "i-3", 2, ReviewAction.REENTER_FIRST_REVIEW, null));
        assertEquals(ReviewState.FIRST_PASS_PENDING, firstPassAgain.reviewCase().state());
    }

    @Test
    void rejectAndDisagreementReachExpectedTerminalOrRecoveryStates() {
        InMemoryReviewCaseRepository repository = new InMemoryReviewCaseRepository();
        ReviewWorkflowGate gate = new ReviewWorkflowGate(repository, fixedClock());
        repository.create(ReviewCase.create("case-1", receipt, 0, 1));
        ReviewEvaluation first = evaluation("e-1", EvaluationDecision.REJECT, null, NOW);
        gate.handle(command("c-1", "i-1", 0, ReviewAction.SUBMIT_FIRST_REVIEW, first));
        ReviewEvaluation rejection = evaluation("e-2", EvaluationDecision.REJECT, new EvaluationId("e-1"), NOW);
        gate.handle(command("c-2", "i-2", 1, ReviewAction.SUBMIT_REJECTION, rejection));
        ReviewEvaluation disagreement = evaluation("e-3", EvaluationDecision.ACCEPT, new EvaluationId("e-2"), NOW);
        ReviewOutcome recovery = gate.handle(command("c-3", "i-3", 2, ReviewAction.SUBMIT_SECOND_REVIEW, disagreement));
        assertEquals(ReviewState.RECOVERY_PENDING, recovery.reviewCase().state());
    }

    @Test
    void exhaustedRecoveryCannotReenter() {
        InMemoryReviewCaseRepository repository = new InMemoryReviewCaseRepository();
        ReviewWorkflowGate gate = new ReviewWorkflowGate(repository, fixedClock());
        repository.create(ReviewCase.create("case-1", receipt, 0, 0));
        gate.handle(command("c-1", "i-1", 0, ReviewAction.REQUEST_RECOVERY, null));
        ReviewOutcome exhausted = gate.handle(command("c-2", "i-2", 1, ReviewAction.COMPLETE_RECOVERY, null));
        assertEquals(ReviewState.EXHAUSTED, exhausted.reviewCase().state());
        assertThrows(ReviewWorkflowException.class, () -> gate.handle(command("c-3", "i-3", 2, ReviewAction.REENTER_FIRST_REVIEW, null)));
    }

    @Test
    void staleSnapshotAndBrokenParentAreRejected() {
        InMemoryReviewCaseRepository repository = new InMemoryReviewCaseRepository();
        ReviewWorkflowGate gate = new ReviewWorkflowGate(repository, fixedClock());
        repository.create(ReviewCase.create("case-1", receipt, 1, 1));
        ReviewEvaluation first = evaluation("e-1", EvaluationDecision.ACCEPT, null, NOW);
        gate.handle(command("c-1", "i-1", 0, ReviewAction.SUBMIT_FIRST_REVIEW, first));
        ReviewEvaluation broken = evaluation("e-2", EvaluationDecision.ACCEPT, new EvaluationId("wrong"), NOW);
        assertThrows(ReviewWorkflowException.class, () -> gate.handle(command("c-2", "i-2", 1, ReviewAction.SUBMIT_ACCEPTANCE, broken)));
        ReviewEvaluation stale = evaluation("e-3", EvaluationDecision.ACCEPT, new EvaluationId("e-1"), NOW.minusSeconds(90000));
        assertThrows(ReviewWorkflowException.class, () -> gate.handle(command("c-3", "i-3", 1, ReviewAction.SUBMIT_ACCEPTANCE, stale)));
        assertEquals(repository.find("case-1", tenant).orElseThrow(), gate.replay("case-1", tenant));
    }

    @Test
    void receiptFromAnotherAgentIsRejected() {
        InMemoryReviewCaseRepository repository = new InMemoryReviewCaseRepository();
        ReviewWorkflowGate gate = new ReviewWorkflowGate(repository, fixedClock());
        repository.create(ReviewCase.create("case-1", receipt, 1, 1));
        PrecheckResult otherAgent = new PrecheckResult(receipt.schemaVersion(), receipt.precheckId(), receipt.tenantId(),
                receipt.taskId(), receipt.operation(), new AgentId("other-agent"), receipt.inputSnapshot(),
                receipt.originalInputDigest(), receipt.normalizedInputDigest(), receipt.rulePack(), receipt.findings(),
                receipt.evidenceResolution(), receipt.riskAssessment(), receipt.contextProjection(),
                receipt.recommendedRoute(), receipt.decision(), receipt.createdAt());
        EvaluationPacket packet = new EvaluationPacket(1, new EvaluationId("other-eval"), EvaluationDecision.ACCEPT,
                .8, .9, "summary", List.of("reason"), List.of(), List.of(), "next", null, "agent-1.0", snapshot, NOW);
        assertThrows(ReviewWorkflowException.class, () -> gate.handle(command("c-other", "i-other", 0,
                ReviewAction.SUBMIT_FIRST_REVIEW, new ReviewEvaluation(packet, otherAgent))));
    }

    @Test
    void replayRejectsForgedTerminalTransition() {
        InMemoryReviewCaseRepository delegate = new InMemoryReviewCaseRepository();
        ReviewWorkflowGate gate = new ReviewWorkflowGate(delegate, fixedClock());
        delegate.create(ReviewCase.create("case-1", receipt, 1, 1));
        ReviewOutcome accepted = gate.handle(command("c-1", "i-1", 0, ReviewAction.SUBMIT_FIRST_REVIEW,
                evaluation("e-1", EvaluationDecision.ACCEPT, null, NOW)));
        ReviewCaseRepository tampered = new ReviewCaseRepository() {
            @Override public ReviewCase create(ReviewCase value) { return delegate.create(value); }
            @Override public java.util.Optional<ReviewCase> find(String id, TenantId owner) { return delegate.find(id, owner); }
            @Override public AppendResult append(ReviewCase expected, ReviewCase next, ReviewEvent event) { return delegate.append(expected, next, event); }
            @Override public List<ReviewEvent> events(String id, TenantId owner) {
                List<ReviewEvent> source = delegate.events(id, owner);
                ReviewEvent original = source.get(0);
                ReviewEvent forged = new ReviewEvent(original.eventId(), original.commandId(), original.idempotencyKey(),
                        original.caseId(), original.action(), original.from(), ReviewState.APPROVED,
                        original.evaluationId(), original.version(), original.occurredAt());
                return List.of(forged);
            }
        };
        assertEquals(ReviewState.ACCEPTANCE, accepted.reviewCase().state());
        assertThrows(ReviewWorkflowException.class, () -> new ReviewWorkflowGate(tampered, fixedClock())
                .replay("case-1", tenant));
    }

    @Test
    void replayRejectsActionFromAnInvalidSourceState() {
        InMemoryReviewCaseRepository delegate = new InMemoryReviewCaseRepository();
        ReviewWorkflowGate gate = new ReviewWorkflowGate(delegate, fixedClock());
        delegate.create(ReviewCase.create("case-1", receipt, 1, 1));
        ReviewOutcome accepted = gate.handle(command("c-1", "i-1", 0, ReviewAction.SUBMIT_FIRST_REVIEW,
                evaluation("e-1", EvaluationDecision.ACCEPT, null, NOW)));
        ReviewCaseRepository tampered = new ReviewCaseRepository() {
            @Override public ReviewCase create(ReviewCase value) { return delegate.create(value); }
            @Override public java.util.Optional<ReviewCase> find(String id, TenantId owner) { return delegate.find(id, owner); }
            @Override public AppendResult append(ReviewCase expected, ReviewCase next, ReviewEvent event) { return delegate.append(expected, next, event); }
            @Override public List<ReviewEvent> events(String id, TenantId owner) {
                ReviewEvent first = delegate.events(id, owner).get(0);
                ReviewEvent forged = new ReviewEvent(new EventId("forged-event"), new CommandId("forged-command"),
                        "forged-key", id, ReviewAction.COMPLETE_RECOVERY, ReviewState.ACCEPTANCE,
                        ReviewState.REENTRY_PENDING, null, first.version() + 1, NOW);
                return List.of(first, forged);
            }
        };
        assertEquals(ReviewState.ACCEPTANCE, accepted.reviewCase().state());
        assertThrows(ReviewWorkflowException.class, () -> new ReviewWorkflowGate(tampered, fixedClock())
                .replay("case-1", tenant));
    }

    @Test
    void replayRejectsPrematureRecoveryExhaustion() {
        InMemoryReviewCaseRepository delegate = new InMemoryReviewCaseRepository();
        ReviewWorkflowGate gate = new ReviewWorkflowGate(delegate, fixedClock());
        delegate.create(ReviewCase.create("case-1", receipt, 1, 1));
        gate.handle(command("c-1", "i-1", 0, ReviewAction.SUBMIT_FIRST_REVIEW,
                evaluation("e-1", EvaluationDecision.REVISE, null, NOW)));
        ReviewCaseRepository tampered = new ReviewCaseRepository() {
            @Override public ReviewCase create(ReviewCase value) { return delegate.create(value); }
            @Override public java.util.Optional<ReviewCase> find(String id, TenantId owner) { return delegate.find(id, owner); }
            @Override public AppendResult append(ReviewCase expected, ReviewCase next, ReviewEvent event) { return delegate.append(expected, next, event); }
            @Override public List<ReviewEvent> events(String id, TenantId owner) {
                ReviewEvent first = delegate.events(id, owner).get(0);
                ReviewEvent forged = new ReviewEvent(new EventId("forged-event"), new CommandId("forged-command"),
                        "forged-key", id, ReviewAction.COMPLETE_RECOVERY, ReviewState.RECOVERY_PENDING,
                        ReviewState.EXHAUSTED, null, first.version() + 1, NOW);
                return List.of(first, forged);
            }
        };
        assertThrows(ReviewWorkflowException.class, () -> new ReviewWorkflowGate(tampered, fixedClock())
                .replay("case-1", tenant));
    }

    private ReviewCommand command(String id, String key, long version, ReviewAction action, ReviewEvaluation evaluation) {
        return new ReviewCommand(new CommandId(id), key, "case-1", tenant, version, action, evaluation);
    }

    private ReviewEvaluation evaluation(String id, EvaluationDecision decision, EvaluationId parent, Instant createdAt) {
        EvaluationPacket packet = new EvaluationPacket(1, new EvaluationId(id), decision, .8, .9, "summary",
                List.of("reason"), List.of(), List.of(), "next", parent, "agent-1.0", snapshot, createdAt);
        return new ReviewEvaluation(packet, receipt);
    }

    private static Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
}
