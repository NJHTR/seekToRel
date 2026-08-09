package com.reportai.auth.model;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Hashed refresh-token record; the raw token is never persisted. */
public record RefreshTokenRecord(
        String id,
        String userId,
        TenantId tenantId,
        String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        String userAgent) {

    public RefreshTokenRecord {
        id = required(id, "id", 64);
        userId = required(userId, "userId", 64);
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        tokenHash = required(tokenHash, "tokenHash", 64);
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        userAgent = userAgent == null ? "" : userAgent.trim();
        if (userAgent.length() > 512) {
            throw new IllegalArgumentException("userAgent is too long");
        }
    }

    public boolean usableAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return revokedAt == null && expiresAt.isAfter(now);
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }
}
