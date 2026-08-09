package com.reportai.storage;

import com.reportai.common.tenant.TenantId;

import java.io.InputStream;

/** Opens task-owned execution artifacts through a tenant-confined boundary. */
public interface ArtifactStorageResolver {
    InputStream open(TenantId tenantId, StoredObject reference);

    default void delete(TenantId tenantId, StoredObject reference) {
        throw new UnsupportedOperationException("tenant-bound artifact deletion is not supported");
    }
}
