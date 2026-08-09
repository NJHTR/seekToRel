package com.reportai.storage;

import com.reportai.common.tenant.TenantId;

import java.io.InputStream;

/** Opens a previously persisted object only after tenant and content integrity checks are attached. */
public interface ObjectStorageResolver {
    InputStream open(TenantId tenantId, StoredObject reference);

    /**
     * Deletes a tenant-owned object after validating that its key is confined
     * to the tenant namespace. Implementations may reject deletion when the
     * provider does not support a safe tenant-bound operation.
     */
    default void delete(TenantId tenantId, StoredObject reference) {
        throw new UnsupportedOperationException("tenant-bound object deletion is not supported");
    }
}
