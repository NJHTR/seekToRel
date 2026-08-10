package com.reportai.recovery;

import com.reportai.common.contract.InputSnapshot;
import com.reportai.common.error.ErrorDetail;
import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FailureObservation(TenantId tenantId, String taskId, String operation, String agentId,
                                 InputSnapshot sourceSnapshot, ErrorDetail error, int attempt,
                                 Instant observedAt, List<String> evidenceRefs, Map<String, String> diagnostics,
                                 String fingerprint) {
    public FailureObservation {
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        taskId = text(taskId, "taskId", 128);
        operation = text(operation, "operation", 128);
        agentId = text(agentId, "agentId", 128);
        sourceSnapshot = Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        if (!tenantId.equals(sourceSnapshot.tenantId())) throw new IllegalArgumentException("snapshot tenant mismatch");
        error = Objects.requireNonNull(error, "error");
        if (attempt < 1 || attempt > 100) throw new IllegalArgumentException("attempt out of bounds");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        evidenceRefs = boundedList(evidenceRefs, 32, "evidenceRefs");
        diagnostics = boundedMap(diagnostics, 16, 256, "diagnostics");
        fingerprint = text(fingerprint, "fingerprint", 128);
    }

    public static FailureObservation of(TenantId tenantId, String taskId, String operation, String agentId,
                                         InputSnapshot snapshot, ErrorDetail error, int attempt, Instant at) {
        String fp = error.code() + ":" + snapshot.contentDigest().value();
        return new FailureObservation(tenantId, taskId, operation, agentId, snapshot, error, attempt, at,
                List.of(), Map.of(), fp);
    }

    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field); String n = value.strip();
        if (n.isBlank() || n.length() > max || n.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field + " is invalid");
        return n;
    }
    private static <T> List<T> boundedList(List<T> value, int max, String field) {
        List<T> result = value == null ? List.of() : List.copyOf(value);
        if (result.size() > max || result.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException(field + " is invalid");
        return result;
    }
    private static Map<String, String> boundedMap(Map<String, String> value, int max, int valueMax, String field) {
        if (value == null || value.isEmpty()) return Map.of();
        if (value.size() > max) throw new IllegalArgumentException(field + " is too large");
        java.util.Map<String, String> copy = new java.util.TreeMap<>();
        value.forEach((k, v) -> { if (k == null || v == null || k.length() > 64 || v.length() > valueMax || v.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field + " is invalid"); copy.put(k, v); });
        return Map.copyOf(copy);
    }
}
