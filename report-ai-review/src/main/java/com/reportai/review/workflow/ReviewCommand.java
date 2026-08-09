package com.reportai.review.workflow;

import com.reportai.common.contract.CommandId;
import com.reportai.common.tenant.TenantId;

import java.util.Objects;

public record ReviewCommand(CommandId commandId, String idempotencyKey, String caseId, TenantId tenantId,
                            long expectedVersion, ReviewAction action, ReviewEvaluation evaluation) {
    public ReviewCommand {
        commandId = Objects.requireNonNull(commandId, "commandId");
        idempotencyKey = text(idempotencyKey, "idempotencyKey", 128);
        caseId = text(caseId, "caseId", 128);
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        action = Objects.requireNonNull(action, "action");
    }
    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String n = value.strip();
        if (n.isBlank() || n.length() > max || n.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field + " is invalid");
        return n;
    }
}
