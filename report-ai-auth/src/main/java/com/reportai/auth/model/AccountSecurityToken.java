package com.reportai.auth.model;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * Persisted representation of an email-verification or password-reset token.
 *
 * <p>Only a SHA-256 digest is retained.  The raw credential is returned once to the caller so it
 * can be handed to a delivery adapter; it must never be logged or persisted.</p>
 */
public record AccountSecurityToken(
        String id,
        String userId,
        TenantId tenantId,
        OneTimeTokenPurpose purpose,
        String tokenHash,
        Instant expiresAt,
        Instant consumedAt,
        Instant revokedAt,
        Instant createdAt,
        String requestIp,
        String userAgent) {

    public AccountSecurityToken {
        id = required(id, "id", 64);
        userId = required(userId, "userId", 64);
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        purpose = Objects.requireNonNull(purpose, "purpose");
        tokenHash = required(tokenHash, "tokenHash", 64);
        if (!tokenHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("tokenHash must be a lowercase SHA-256 digest");
        }
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (consumedAt != null && consumedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("consumedAt must not be before createdAt");
        }
        if (revokedAt != null && revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }
        requestIp = requestIp == null ? "" : requestIp.trim();
        userAgent = userAgent == null ? "" : userAgent.trim();
        if (requestIp.length() > 64) {
            throw new IllegalArgumentException("requestIp is too long");
        }
        if (userAgent.length() > 512) {
            throw new IllegalArgumentException("userAgent is too long");
        }
    }

    public boolean usableAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return consumedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }
}
