package com.reportai.common.tenant;

public final class TenantContext {
    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantId tenantId) {
        CURRENT.set(tenantId);
    }

    public static TenantId require() {
        TenantId tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new IllegalStateException("authenticated tenant context is required");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
