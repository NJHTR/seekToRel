package com.reportai.common.tenant;

import java.util.Objects;

public record TenantId(String value) {
    public TenantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }

    public static TenantId of(String value) {
        return new TenantId(Objects.requireNonNull(value, "tenantId"));
    }
}
