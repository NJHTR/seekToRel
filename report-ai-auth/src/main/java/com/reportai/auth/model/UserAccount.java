package com.reportai.auth.model;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Persisted identity used by the first-party local authentication adapter. */
public record UserAccount(
        String id,
        TenantId tenantId,
        String email,
        String passwordHash,
        String displayName,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt) {

    public UserAccount {
        id = required(id, "id", 64);
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        email = required(email, "email", 320).toLowerCase(java.util.Locale.ROOT);
        passwordHash = required(passwordHash, "passwordHash", 255);
        displayName = required(displayName, "displayName", 128);
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public boolean active() {
        return status == AccountStatus.ACTIVE;
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }
}
