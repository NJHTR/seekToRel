package com.reportai.auth.model;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Immutable audit record; it intentionally contains no password or raw-token material. */
public record AccountAuditEntry(
        String id,
        TenantId tenantId,
        String actorUserId,
        String targetUserId,
        AccountAuditAction action,
        AccountRole role,
        String reason,
        String requestId,
        String sourceIp,
        String userAgent,
        Instant occurredAt) {

    public AccountAuditEntry {
        id = required(id, "id", 64);
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        actorUserId = optionalIdentifier(actorUserId, 64);
        targetUserId = required(targetUserId, "targetUserId", 64);
        action = Objects.requireNonNull(action, "action");
        role = roleRequired(action, role);
        reason = optional(reason, 512);
        requestId = optional(requestId, 128);
        sourceIp = optional(sourceIp, 64);
        userAgent = optional(userAgent, 512);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static AccountRole roleRequired(AccountAuditAction action, AccountRole role) {
        boolean roleAction = action == AccountAuditAction.ROLE_GRANTED
                || action == AccountAuditAction.ROLE_REVOKED;
        if (roleAction && role == null) {
            throw new IllegalArgumentException("role is required for role audit actions");
        }
        if (!roleAction && role != null) {
            throw new IllegalArgumentException("role is only valid for role audit actions");
        }
        return role;
    }

    private static String required(String value, String name, int maxLength) {
        String normalized = optional(value, maxLength);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("value is too long");
        }
        return normalized;
    }

    private static String optionalIdentifier(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, "identifier", maxLength);
    }
}
