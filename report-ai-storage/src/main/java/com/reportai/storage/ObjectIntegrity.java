package com.reportai.storage;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Durable ownership and checksum metadata for a stored source, asset or generated output. */
public record ObjectIntegrity(String id, TenantId tenantId, String ownerType, String ownerId,
                              String role, StoredObject object, Instant createdAt) {
    public ObjectIntegrity {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("integrity id is invalid");
        }
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        ownerType = enumValue(ownerType, "ownerType");
        ownerId = identifier(ownerId, "ownerId");
        role = enumValue(role, "role");
        object = Objects.requireNonNull(object, "object");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String enumValue(String value, String field) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_-]{0,31}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String identifier(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
