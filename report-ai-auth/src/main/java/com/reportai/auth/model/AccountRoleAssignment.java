package com.reportai.auth.model;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** A historical role grant; revocation is represented by {@code revokedAt}, never by deletion. */
public record AccountRoleAssignment(
        String id,
        String userId,
        TenantId tenantId,
        AccountRole role,
        String grantedBy,
        Instant grantedAt,
        Instant revokedAt) {

    public AccountRoleAssignment {
        id = required(id, "id", 64);
        userId = required(userId, "userId", 64);
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        role = Objects.requireNonNull(role, "role");
        grantedBy = required(grantedBy, "grantedBy", 64);
        grantedAt = Objects.requireNonNull(grantedAt, "grantedAt");
        if (revokedAt != null && revokedAt.isBefore(grantedAt)) {
            throw new IllegalArgumentException("revokedAt must not be before grantedAt");
        }
    }

    public boolean activeAt(Instant at) {
        Objects.requireNonNull(at, "at");
        return !grantedAt.isAfter(at) && (revokedAt == null || revokedAt.isAfter(at));
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }
}
