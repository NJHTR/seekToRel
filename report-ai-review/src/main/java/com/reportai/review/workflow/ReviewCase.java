package com.reportai.review.workflow;

import com.reportai.common.contract.EvaluationId;
import com.reportai.common.contract.InputSnapshot;
import com.reportai.common.contract.TaskId;
import com.reportai.common.tenant.TenantId;
import com.reportai.review.precheck.PrecheckResult;

import java.util.Objects;

/** Immutable aggregate snapshot. Mutations are only produced by ReviewWorkflowGate. */
public record ReviewCase(String id, TenantId tenantId, TaskId taskId, InputSnapshot inputSnapshot,
                         String precheckId, ReviewState state, int reentryCount,
                         int recoveryAttempts, int maxReentries, int maxRecoveryAttempts,
                         EvaluationId latestEvaluationId, long version) {
    public ReviewCase {
        id = text(id, "id", 128);
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        taskId = Objects.requireNonNull(taskId, "taskId");
        inputSnapshot = Objects.requireNonNull(inputSnapshot, "inputSnapshot");
        if (!tenantId.equals(inputSnapshot.tenantId())) throw new IllegalArgumentException("snapshot tenant mismatch");
        precheckId = text(precheckId, "precheckId", 128);
        state = Objects.requireNonNull(state, "state");
        if (reentryCount < 0 || recoveryAttempts < 0 || maxReentries < 0 || maxRecoveryAttempts < 0
                || reentryCount > maxReentries || recoveryAttempts > maxRecoveryAttempts) {
            throw new IllegalArgumentException("invalid review budgets");
        }
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    public static ReviewCase create(String id, PrecheckResult receipt, int maxReentries, int maxRecoveryAttempts) {
        Objects.requireNonNull(receipt, "receipt");
        if (!receipt.acceptedForAgent()) throw new IllegalArgumentException("review requires an accepted precheck");
        if (!receipt.tenantId().equals(receipt.inputSnapshot().tenantId())) throw new IllegalArgumentException("precheck tenant mismatch");
        return new ReviewCase(id, receipt.tenantId(), receipt.taskId(), receipt.inputSnapshot(), receipt.precheckId(),
                ReviewState.FIRST_PASS_PENDING, 0, 0, maxReentries, maxRecoveryAttempts, null, 0);
    }

    ReviewCase next(ReviewState next, ReviewEvaluation evaluation, int reentries, int recoveries) {
        return new ReviewCase(id, tenantId, taskId, inputSnapshot, precheckId, next, reentries, recoveries,
                maxReentries, maxRecoveryAttempts,
                evaluation == null ? latestEvaluationId : evaluation.packet().evaluationId(), version + 1);
    }

    /** Compatibility helper for adapters that build an aggregate successor in one step. */
    public ReviewCase successor(ReviewState next, ReviewEvaluation evaluation, int reentries, int recoveries) {
        return next(next, evaluation, reentries, recoveries);
    }

    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > max || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
