package com.reportai.review.workflow;

import com.reportai.common.contract.EvaluationDecision;
import com.reportai.common.contract.EventId;
import com.reportai.common.tenant.TenantId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic application gate for the review aggregate. */
public final class ReviewWorkflowGate {
    private final ReviewCaseRepository repository;
    private final Clock clock;
    private final Duration evidenceMaxAge;

    public ReviewWorkflowGate(ReviewCaseRepository repository) { this(repository, Clock.systemUTC(), Duration.ofHours(24)); }

    public ReviewWorkflowGate(ReviewCaseRepository repository, Clock clock) {
        this(repository, clock, Duration.ofHours(24));
    }

    public ReviewWorkflowGate(ReviewCaseRepository repository, Clock clock, Duration evidenceMaxAge) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.evidenceMaxAge = Objects.requireNonNull(evidenceMaxAge, "evidenceMaxAge");
        if (evidenceMaxAge.isNegative() || evidenceMaxAge.isZero()) throw new IllegalArgumentException("evidenceMaxAge must be positive");
    }

    public ReviewCase create(ReviewCase reviewCase) { return repository.create(reviewCase); }

    public ReviewOutcome handle(ReviewCommand command) {
        Objects.requireNonNull(command, "command");
        ReviewEvent replay = repository.findByIdempotency(command.caseId(), command.tenantId(), command.idempotencyKey()).orElse(null);
        if (replay != null) {
            ReviewCase state = repository.find(command.caseId(), command.tenantId())
                    .orElseThrow(() -> new ReviewWorkflowException("review case not found"));
            if (!replay.commandId().equals(command.commandId()) || replay.action() != command.action()
                    || !Objects.equals(replay.evaluationId(), evaluationId(command))) {
                throw new ReviewConflictException("idempotency key reused with different command");
            }
            return new ReviewOutcome(state, replay, true);
        }
        ReviewCase current = repository.find(command.caseId(), command.tenantId())
                .orElseThrow(() -> new ReviewWorkflowException("review case not found"));
        if (current.version() != command.expectedVersion()) throw new ReviewConflictException("stale review version");
        ReviewEvaluation evaluation = command.evaluation();
        validateEvaluation(current, evaluation, command.action());
        validateParent(current, evaluation, command.action());
        Transition transition = transition(current, command.action(), evaluation, repository.events(current.id(), current.tenantId()));
        ReviewCase next = current.next(transition.state(), evaluation, transition.reentries(), transition.recoveries());
        ReviewEvent event = new ReviewEvent(new EventId(UUID.randomUUID().toString()), command.commandId(),
                command.idempotencyKey(), command.caseId(), command.action(), current.state(), next.state(),
                evaluationId(command), next.version(), Instant.now(clock));
        ReviewCaseRepository.AppendResult appended = repository.append(current, next, event);
        return new ReviewOutcome(appended.state(), appended.event() == null ? event : appended.event(), appended.duplicate());
    }

    public ReviewCase replay(String caseId, TenantId tenantId) {
        ReviewCase state = repository.find(caseId, tenantId).orElseThrow(() -> new ReviewWorkflowException("review case not found"));
        ReviewCase rebuilt = new ReviewCase(state.id(), state.tenantId(), state.taskId(), state.inputSnapshot(), state.precheckId(),
                ReviewState.FIRST_PASS_PENDING, 0, 0, state.maxReentries(), state.maxRecoveryAttempts(), null, 0,
                state.expectedOperation(), state.expectedAgentId());
        for (ReviewEvent event : repository.events(caseId, tenantId)) {
            rebuilt = applyEvent(rebuilt, event);
        }
        if (!rebuilt.equals(state)) throw new ReviewWorkflowException("review event replay diverged");
        return rebuilt;
    }

    private static ReviewCase applyEvent(ReviewCase current, ReviewEvent event) {
        if (current.version() + 1 != event.version() || current.state() != event.from()
                || !current.id().equals(event.caseId())
                || !allowedTransition(event.action(), event.from(), event.to())) {
            throw new ReviewWorkflowException("invalid review event stream");
        }
        int reentries = current.reentryCount() + (event.action() == ReviewAction.REENTER_FIRST_REVIEW
                && event.to() != ReviewState.EXHAUSTED ? 1 : 0);
        int recoveries = current.recoveryAttempts() + (event.action() == ReviewAction.COMPLETE_RECOVERY
                && event.to() == ReviewState.REENTRY_PENDING ? 1 : 0);
        return new ReviewCase(current.id(), current.tenantId(), current.taskId(), current.inputSnapshot(), current.precheckId(),
                event.to(), reentries, recoveries, current.maxReentries(), current.maxRecoveryAttempts(),
                event.evaluationId(), event.version(), current.expectedOperation(), current.expectedAgentId());
    }

    private static boolean allowedTransition(ReviewAction action, ReviewState source, ReviewState target) {
        return switch (action) {
            case SUBMIT_FIRST_REVIEW -> source == ReviewState.FIRST_PASS_PENDING
                    && (target == ReviewState.ACCEPTANCE || target == ReviewState.REJECTION || target == ReviewState.RECOVERY_PENDING);
            case SUBMIT_ACCEPTANCE -> source == ReviewState.ACCEPTANCE
                    && (target == ReviewState.SECOND_REVIEW_PENDING || target == ReviewState.RECOVERY_PENDING);
            case SUBMIT_REJECTION -> source == ReviewState.REJECTION
                    && (target == ReviewState.SECOND_REVIEW_PENDING || target == ReviewState.RECOVERY_PENDING);
            case SUBMIT_SECOND_REVIEW -> source == ReviewState.SECOND_REVIEW_PENDING
                    && (target == ReviewState.APPROVED || target == ReviewState.REJECTED || target == ReviewState.RECOVERY_PENDING);
            case REQUEST_RECOVERY -> source != null && !source.terminal() && source != ReviewState.RECOVERY_PENDING
                    && target == ReviewState.RECOVERY_PENDING;
            case COMPLETE_RECOVERY -> source == ReviewState.RECOVERY_PENDING
                    && (target == ReviewState.REENTRY_PENDING || target == ReviewState.EXHAUSTED);
            case REENTER_FIRST_REVIEW -> source == ReviewState.REENTRY_PENDING
                    && (target == ReviewState.FIRST_PASS_PENDING || target == ReviewState.EXHAUSTED);
            case PRECHECK, ESCALATE -> false;
        };
    }

    private static Transition transition(ReviewCase current, ReviewAction action, ReviewEvaluation evaluation, List<ReviewEvent> history) {
        EvaluationDecision decision = evaluation == null ? null : evaluation.packet().decision();
        return switch (action) {
            case SUBMIT_FIRST_REVIEW -> {
                require(current.state() == ReviewState.FIRST_PASS_PENDING, "first review is not pending");
                yield decision == EvaluationDecision.ACCEPT ? new Transition(ReviewState.ACCEPTANCE, current.reentryCount(), current.recoveryAttempts())
                        : decision == EvaluationDecision.REJECT ? new Transition(ReviewState.REJECTION, current.reentryCount(), current.recoveryAttempts())
                        : new Transition(ReviewState.RECOVERY_PENDING, current.reentryCount(), current.recoveryAttempts());
            }
            case SUBMIT_ACCEPTANCE -> {
                require(current.state() == ReviewState.ACCEPTANCE, "acceptance review is not pending");
                yield decision == EvaluationDecision.ACCEPT ? new Transition(ReviewState.SECOND_REVIEW_PENDING, current.reentryCount(), current.recoveryAttempts())
                        : new Transition(ReviewState.RECOVERY_PENDING, current.reentryCount(), current.recoveryAttempts());
            }
            case SUBMIT_REJECTION -> {
                require(current.state() == ReviewState.REJECTION, "rejection review is not pending");
                yield decision == EvaluationDecision.REJECT ? new Transition(ReviewState.SECOND_REVIEW_PENDING, current.reentryCount(), current.recoveryAttempts())
                        : new Transition(ReviewState.RECOVERY_PENDING, current.reentryCount(), current.recoveryAttempts());
            }
            case SUBMIT_SECOND_REVIEW -> {
                require(current.state() == ReviewState.SECOND_REVIEW_PENDING, "second review is not pending");
                ReviewAction prior = history.isEmpty() ? null : history.get(history.size() - 1).action();
                EvaluationDecision expected = prior == ReviewAction.SUBMIT_ACCEPTANCE ? EvaluationDecision.ACCEPT
                        : prior == ReviewAction.SUBMIT_REJECTION ? EvaluationDecision.REJECT : null;
                yield expected == null || decision != expected ? new Transition(ReviewState.RECOVERY_PENDING, current.reentryCount(), current.recoveryAttempts())
                        : decision == EvaluationDecision.ACCEPT ? new Transition(ReviewState.APPROVED, current.reentryCount(), current.recoveryAttempts())
                        : new Transition(ReviewState.REJECTED, current.reentryCount(), current.recoveryAttempts());
            }
            case REQUEST_RECOVERY -> {
                require(!current.state().terminal() && current.state() != ReviewState.RECOVERY_PENDING, "recovery cannot be requested");
                yield new Transition(ReviewState.RECOVERY_PENDING, current.reentryCount(), current.recoveryAttempts());
            }
            case COMPLETE_RECOVERY -> {
                require(current.state() == ReviewState.RECOVERY_PENDING, "recovery is not pending");
                if (current.recoveryAttempts() >= current.maxRecoveryAttempts()) yield new Transition(ReviewState.EXHAUSTED, current.reentryCount(), current.recoveryAttempts());
                else yield new Transition(ReviewState.REENTRY_PENDING, current.reentryCount(), current.recoveryAttempts() + 1);
            }
            case REENTER_FIRST_REVIEW -> {
                require(current.state() == ReviewState.REENTRY_PENDING, "re-entry is not pending");
                if (current.reentryCount() >= current.maxReentries()) yield new Transition(ReviewState.EXHAUSTED, current.reentryCount(), current.recoveryAttempts());
                // Re-entry always returns to the main first pass. A recovery agent may
                // attach context, but it cannot choose the business outcome.
                yield new Transition(ReviewState.FIRST_PASS_PENDING, current.reentryCount() + 1, current.recoveryAttempts());
            }
            case PRECHECK, ESCALATE -> throw new ReviewWorkflowException("unsupported workflow action: " + action);
        };
    }

    private void validateEvaluation(ReviewCase current, ReviewEvaluation evaluation, ReviewAction action) {
        boolean requires = action == ReviewAction.SUBMIT_FIRST_REVIEW || action == ReviewAction.SUBMIT_ACCEPTANCE
                || action == ReviewAction.SUBMIT_REJECTION || action == ReviewAction.SUBMIT_SECOND_REVIEW
                || (action == ReviewAction.REENTER_FIRST_REVIEW && evaluation != null);
        if (!requires) return;
        if (evaluation == null) throw new ReviewWorkflowException("evaluation is required");
        var packet = evaluation.packet();
        var receipt = evaluation.precheck();
        Instant now = Instant.now(clock);
        if (!receipt.acceptedForAgent() || !receipt.operation().equals(current.expectedOperation())
                || !receipt.agentId().equals(current.expectedAgentId()) || !receipt.precheckId().equals(current.precheckId())
                || !receipt.tenantId().equals(current.tenantId()) || !receipt.taskId().equals(current.taskId())
                || !receipt.inputSnapshot().snapshotId().equals(current.inputSnapshot().snapshotId())
                || !receipt.inputSnapshot().contentDigest().equals(current.inputSnapshot().contentDigest())
                || !packet.inputSnapshot().tenantId().equals(current.tenantId())
                || !packet.inputSnapshot().snapshotId().equals(current.inputSnapshot().snapshotId())
                || !packet.inputSnapshot().contentDigest().equals(current.inputSnapshot().contentDigest())) {
            throw new ReviewWorkflowException("evaluation evidence is stale or not bound to this review");
        }
        if (packet.createdAt().isAfter(now) || packet.createdAt().isBefore(now.minus(evidenceMaxAge))
                || receipt.createdAt().isAfter(now) || receipt.createdAt().isBefore(now.minus(evidenceMaxAge))) {
            throw new ReviewWorkflowException("evaluation evidence is stale");
        }
    }

    private static void validateParent(ReviewCase current, ReviewEvaluation evaluation, ReviewAction action) {
        if (evaluation == null || current.latestEvaluationId() == null) return;
        boolean chained = action == ReviewAction.SUBMIT_ACCEPTANCE || action == ReviewAction.SUBMIT_REJECTION
                || action == ReviewAction.SUBMIT_SECOND_REVIEW;
        if (chained && !current.latestEvaluationId().equals(evaluation.packet().parentEvaluationId())) {
            throw new ReviewWorkflowException("evaluation parent chain is broken");
        }
    }

    private static com.reportai.common.contract.EvaluationId evaluationId(ReviewCommand command) {
        return command.evaluation() == null ? null : command.evaluation().packet().evaluationId();
    }

    private static void require(boolean condition, String message) { if (!condition) throw new ReviewWorkflowException(message); }
    private record Transition(ReviewState state, int reentries, int recoveries) { }
}
