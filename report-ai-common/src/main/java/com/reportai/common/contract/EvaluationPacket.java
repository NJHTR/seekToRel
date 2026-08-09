package com.reportai.common.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Versioned proposal returned by a review-capable Agent. It contains concise,
 * evidence-linked facts only; hidden chain-of-thought is deliberately excluded.
 */
public record EvaluationPacket(int schemaVersion, EvaluationId evaluationId,
                               EvaluationDecision decision, double score, double confidence,
                               String summary, List<String> reasons, List<EvidenceRef> evidenceRefs,
                               List<EvaluationRisk> risks, String nextAction,
                               EvaluationId parentEvaluationId, String agentVersion,
                               InputSnapshot inputSnapshot, Instant createdAt) {
    public EvaluationPacket {
        if (schemaVersion <= 0) throw new IllegalArgumentException("evaluation schemaVersion must be positive");
        evaluationId = Objects.requireNonNull(evaluationId, "evaluationId");
        decision = Objects.requireNonNull(decision, "decision");
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            throw new IllegalArgumentException("score must be finite and between 0 and 1");
        }
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be finite and between 0 and 1");
        }
        summary = bounded(summary, "summary", 4096);
        reasons = boundedReasons(reasons);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        if (evidenceRefs.size() > 256) throw new IllegalArgumentException("too many evidence references");
        risks = risks == null ? List.of() : List.copyOf(risks);
        if (risks.size() > 64) throw new IllegalArgumentException("too many evaluation risks");
        nextAction = bounded(nextAction, "nextAction", 512);
        agentVersion = IdentifierRules.requireVersion(agentVersion, "agentVersion");
        inputSnapshot = Objects.requireNonNull(inputSnapshot, "inputSnapshot");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static List<String> boundedReasons(List<String> source) {
        if (source == null || source.isEmpty()) return List.of();
        if (source.size() > 64) throw new IllegalArgumentException("too many evaluation reasons");
        return source.stream().map(value -> bounded(value, "reason", 2048)).toList();
    }

    private static String bounded(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > max
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
