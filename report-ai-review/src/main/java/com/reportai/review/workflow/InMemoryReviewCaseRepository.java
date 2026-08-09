package com.reportai.review.workflow;

import com.reportai.common.tenant.TenantId;

import java.util.*;

/** Deterministic repository used by tests and local composition; the interface is CAS-shaped for durable adapters. */
public final class InMemoryReviewCaseRepository implements ReviewCaseRepository {
    private final Map<String, ReviewCase> states = new HashMap<>();
    private final Map<String, List<ReviewEvent>> eventLog = new HashMap<>();
    private final Map<String, ReviewEvent> byIdempotency = new HashMap<>();

    @Override
    public synchronized ReviewCase create(ReviewCase reviewCase) {
        Objects.requireNonNull(reviewCase, "reviewCase");
        if (states.putIfAbsent(key(reviewCase.tenantId(), reviewCase.id()), reviewCase) != null) {
            throw new ReviewConflictException("review case already exists");
        }
        eventLog.put(key(reviewCase.tenantId(), reviewCase.id()), new ArrayList<>());
        return reviewCase;
    }

    @Override
    public synchronized Optional<ReviewCase> find(String caseId, TenantId tenantId) {
        return Optional.ofNullable(states.get(key(tenantId, caseId)));
    }

    @Override
    public synchronized AppendResult append(ReviewCase expected, ReviewCase next, ReviewEvent event) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(event, "event");
        String aggregateKey = key(expected.tenantId(), expected.id());
        ReviewEvent prior = byIdempotency.get(idempotencyKey(expected.tenantId(), expected.id(), event.idempotencyKey()));
        if (prior != null) {
            if (!sameCommand(prior, event)) throw new ReviewConflictException("idempotency key reused with different command");
            return new AppendResult(states.get(aggregateKey), prior, true);
        }
        ReviewCase current = states.get(aggregateKey);
        if (current == null) throw new ReviewWorkflowException("review case not found");
        if (current.version() != expected.version()) throw new ReviewConflictException("stale review version");
        if (next.version() != expected.version() + 1 || event.version() != next.version()) {
            throw new ReviewConflictException("review version sequence is invalid");
        }
        states.put(aggregateKey, next);
        eventLog.computeIfAbsent(aggregateKey, ignored -> new ArrayList<>()).add(event);
        byIdempotency.put(idempotencyKey(expected.tenantId(), expected.id(), event.idempotencyKey()), event);
        return new AppendResult(next, event, false);
    }

    @Override
    public synchronized List<ReviewEvent> events(String caseId, TenantId tenantId) {
        return List.copyOf(eventLog.getOrDefault(key(tenantId, caseId), List.of()));
    }

    @Override
    public synchronized Optional<ReviewEvent> findByIdempotency(String caseId, TenantId tenantId, String idempotencyKey) {
        return Optional.ofNullable(byIdempotency.get(idempotencyKey(tenantId, caseId, idempotencyKey)));
    }

    private static boolean sameCommand(ReviewEvent a, ReviewEvent b) {
        return a.commandId().equals(b.commandId()) && a.action() == b.action()
                && Objects.equals(a.evaluationId(), b.evaluationId()) && a.from() == b.from() && a.to() == b.to();
    }

    private static String key(TenantId tenant, String id) { return tenant.value() + "\u0000" + id; }
    private static String idempotencyKey(TenantId tenant, String id, String key) { return tenant.value() + "\u0000" + id + "\u0000" + key; }
}
